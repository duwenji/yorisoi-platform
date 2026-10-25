package io.github.duwenji.yorisoi.kit.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The AI however it is reached: set up or not, within the day's limit, replies cleaned (ADR-AI-ROLES H-6, H-9). */
class AiGatewayTest {
    private val server = MockWebServer().apply { start() }
    private val usage = AiUsage(object : UsageStore {
        var saved: Pair<String, Int>? = null
        override fun read() = saved
        override fun write(day: String, count: Int) {
            saved = day to count
        }
    })
    private var connection = AiConnection(AiRoute.OWN_KEY, Provider.CUSTOM, "key", "model", server.url("/v1").toString(), dailyLimit = 2)
    private val calls = mutableListOf<AiCall>()
    private val gateway = AiGateway({ connection }, usage, relay = null).apply { onCall = { calls += it } }

    @After
    fun stop() = server.shutdown()

    private fun reply(text: String) = server.enqueue(
        MockResponse().setBody(JSONObject().put("choices", listOf(JSONObject().put("message", JSONObject().put("content", text)))).toString()),
    )

    private fun unavailable(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("no call should have been made")
        } catch (e: AiUnavailableException) {
            // As meant.
        }
    }

    @Test
    fun `a reply comes back without the stage directions, and the call is recorded`() = runBlocking {
        reply("（明るい声で）いい風ですね。")
        assertEquals("いい風ですね。", gateway.generate(Purpose.CHAT, "system", "user"))
        assertEquals(1, calls.size)
        assertEquals("（明るい声で）いい風ですね。", calls.single().reply)
        assertEquals("model", calls.single().model)
    }

    @Test
    fun `a reply with contact details is recorded as such, the caller decides`() = runBlocking {
        reply("詳しくは https://example.com で")
        gateway.generate(Purpose.GUIDE, "system", "user")
        assertEquals("a URL", calls.single().violation)
        assertEquals("guide", calls.single().kind)
    }

    @Test
    fun `the user's key is called up to the day's limit`() {
        reply("一")
        reply("二")
        runBlocking {
            gateway.generate(Purpose.CHAT, "s", "u")
            gateway.generate(Purpose.CHAT, "s", "u")
        }
        assertTrue(gateway.limitReached())
        unavailable { gateway.generate(Purpose.CHAT, "s", "u") }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing is called without an AI set up`() {
        connection = connection.copy(apiKey = "")
        assertFalse(gateway.isConfigured)
        unavailable { gateway.generate(Purpose.CHAT, "s", "u") }
        // The operator's AI needs a relay in the build.
        connection = AiConnection(AiRoute.OPERATOR)
        assertFalse(gateway.isConfigured)
        unavailable { gateway.generate(Purpose.CHAT, "s", "u") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the operator's AI is named as such`() {
        assertEquals(AiConnection.OPERATOR_AI_NAME, AiConnection(AiRoute.OPERATOR).name)
        assertEquals("deepseek-chat", AiConnection(AiRoute.OWN_KEY, Provider.DEEPSEEK).name)
    }
}
