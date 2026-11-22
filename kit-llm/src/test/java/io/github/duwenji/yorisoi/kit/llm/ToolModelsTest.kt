package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.agent.AgentTools
import io.github.duwenji.yorisoi.core.agent.Answer
import io.github.duwenji.yorisoi.core.agent.ToolUse
import io.github.duwenji.yorisoi.core.agent.Turn
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The preparer's turns in each route's form of function calling (platform ADR-AGENT A-10・A-45). */
class ToolModelsTest {
    private lateinit var server: MockWebServer
    private val tools = AgentTools.load()
    private val turns = listOf(
        Turn.Input("日付: 2026-11-22。市区町村: 福井県福井市。"),
        Turn.Asked(listOf(ToolUse("t1", "read_page", JSONObject("""{"ids":["P1"]}""")))),
        Turn.Answers(mutableListOf(Answer("t1", "R3 11月下旬から越前水仙の出荷が始まる", emptyList()))),
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(body)

    @Test
    fun `the relay gets the names and the version, and gives back only tools`() = runBlocking {
        val store = object : RelaySessionStore {
            override fun installId() = "0b6f3c1e-8a2d-4f0b-9a57-3c2e1d4b5a69"
            override fun session() = "t" to Instant.parse("2026-12-01T00:00:00Z")
            override fun saveSession(token: String, expiresAt: Instant) = Unit
            override fun clearSession() = Unit
        }
        val relay = RelayClient(server.url("/api/").toString(), RelayApp("io.github.duwenji.yorisoi.sanpoguide", 4), store, now = { Instant.parse("2026-11-22T03:00:00Z") })
        server.enqueue(ok("""{"toolUses":[{"id":"u1","name":"submit_threads","input":{"threads":[]}}],"quota":{"limit":100,"remaining":97,"resetsAt":"2026-11-22T15:00:00Z"}}"""))
        val reply = relay.agent("s", turns, listOf(tools.submit), mustUse = true, toolsVersion = tools.version)
        val req = server.takeRequest()
        assertEquals("/api/v1/agent", req.path)
        assertEquals("Bearer t", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals(1, body.getInt("toolsVersion"))
        assertEquals("submit_threads", body.getJSONArray("tools").getString(0))
        assertTrue(body.getBoolean("mustUse"))
        val m = body.getJSONArray("messages")
        assertEquals("assistant", m.getJSONObject(1).getString("role"))
        assertEquals("t1", m.getJSONObject(2).getJSONArray("results").getJSONObject(0).getString("id"))
        assertEquals("submit_threads", reply.uses.single().name)
        assertEquals("", reply.text)
        assertEquals(97, relay.quota.value?.remaining)
    }

    @Test
    fun `Claude gets tools, tool_use and tool_result, and the tool by name on the last turn`() = runBlocking {
        server.enqueue(ok("""{"content":[{"type":"text","text":"<words>"},{"type":"tool_use","id":"tu","name":"submit_threads","input":{"threads":[]}}],"stop_reason":"tool_use"}"""))
        val reply = ClaudeToolModel("k", "claude-haiku-4-5", server.url("/").toString()).call("s", turns, listOf(tools.submit), true)
        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("k", req.getHeader("x-api-key"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals(JSONObject("""{"type":"tool","name":"submit_threads"}""").toString(), body.getJSONObject("tool_choice").toString())
        assertEquals("tool_use", body.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("type"))
        assertEquals("tool_result", body.getJSONArray("messages").getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("type"))
        assertEquals("tu", reply.uses.single().id)
        assertEquals("", reply.text)
    }

    @Test
    fun `an OpenAI-compatible service gets tool_calls and tool messages, and required on the last turn`() = runBlocking {
        server.enqueue(ok("""{"choices":[{"message":{"role":"assistant","content":"x","tool_calls":[{"id":"c1","type":"function","function":{"name":"read_page","arguments":"{\"ids\":[\"P1\"]}"}}]}}]}"""))
        val reply = OpenAiToolModel(server.url("/v1/").toString(), "k", "deepseek-chat").call("s", turns, tools.lookups, false)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("auto", body.getString("tool_choice"))
        val m = body.getJSONArray("messages")
        assertEquals("system", m.getJSONObject(0).getString("role"))
        assertEquals("read_page", m.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("tool", m.getJSONObject(3).getString("role"))
        assertEquals("P1", reply.uses.single().input.getJSONArray("ids").getString(0))

        server.enqueue(ok("""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[]}}]}"""))
        OpenAiToolModel(server.url("/v1/").toString(), "k", "deepseek-chat").call("s", turns, listOf(tools.submit), true)
        assertEquals("required", JSONObject(server.takeRequest().body.readUtf8()).getString("tool_choice"))
    }

    @Test
    fun `the preparer on the user's key uses its own model and counts each call`() = runBlocking {
        var kept: Pair<String, Int>? = null
        val usage = AiUsage(object : UsageStore {
            override fun read() = kept
            override fun write(day: String, count: Int) { kept = day to count }
        })
        val gateway = AiGateway({ AiConnection(AiRoute.OWN_KEY, Provider.CUSTOM, "k", "talk-model", server.url("/v1/").toString(), 1, agentModel = "agent-model") }, usage, null)
        val seen = ArrayList<AiCall>()
        gateway.onCall = { seen += it }
        server.enqueue(ok("""{"choices":[{"message":{"tool_calls":[{"id":"c1","type":"function","function":{"name":"submit_threads","arguments":"{\"threads\":[]}"}}]}}]}"""))
        val model = gateway.preparer(tools)!!
        model.call("s", turns.take(1), listOf(tools.submit), true)
        assertEquals("agent-model", JSONObject(server.takeRequest().body.readUtf8()).getString("model"))
        assertEquals("agent", seen.single().kind)
        assertTrue(runCatching { model.call("s", turns.take(1), listOf(tools.submit), true) }.exceptionOrNull() is AiUnavailableException)
    }
}
