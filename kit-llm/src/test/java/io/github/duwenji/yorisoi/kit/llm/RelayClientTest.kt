package io.github.duwenji.yorisoi.kit.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** The relay as platform API-AI-RELAY describes it, from the app's side. */
class RelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    private val now = Instant.parse("2026-10-20T03:00:00Z")

    private class MemoryStore : RelaySessionStore {
        var token: Pair<String, Instant>? = null
        override fun installId() = "0b6f3c1e-8a2d-4f0b-9a57-3c2e1d4b5a69"
        override fun session() = token
        override fun saveSession(token: String, expiresAt: Instant) {
            this.token = token to expiresAt
        }
        override fun clearSession() {
            token = null
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemoryStore()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client(integrity: IntegrityTokenProvider? = null, plans: PlanPurchases? = null) =
        RelayClient(server.url("/api/").toString(), RelayApp("io.github.duwenji.yorisoi.sanpoguide", 4), store, integrity, now = { now }, plans = plans)

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(body)
    private fun problem(status: Int, code: String, extra: String = "") =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/problem+json")
            .setBody("""{"type":"about:blank","title":"x","status":$status,"code":"$code"$extra}""")

    private val quotaJson = """{"limit":30,"remaining":29,"resetsAt":"2026-10-20T15:00:00Z"}"""
    private fun challenge(nonce: String = "n1") = ok("""{"nonce":"$nonce","expiresAt":"2026-10-20T03:05:00Z"}""")
    private fun session(token: String = "t1") = ok("""{"token":"$token","expiresAt":"2026-10-21T03:00:00Z","quota":$quotaJson}""")
    private fun generated(text: String = "こんにちは") = ok("""{"text":"$text","quota":$quotaJson,"usage":{"inputTokens":10,"outputTokens":5}}""")

    @Test
    fun `opens a session, then generates with the bearer token and the kind`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(generated())
        val c = client()

        assertEquals("こんにちは", c.generate("s", "u", Purpose.GUIDE))

        assertEquals("/api/v1/session/challenge", server.takeRequest().path)
        val open = server.takeRequest()
        assertEquals("/api/v1/session", open.path)
        val body = JSONObject(open.body.readUtf8())
        assertEquals("n1", body.getString("nonce"))
        assertEquals("io.github.duwenji.yorisoi.sanpoguide", body.getJSONObject("app").getString("package"))
        assertTrue("no Integrity token on the development relay", !body.has("integrityToken"))
        val gen = server.takeRequest()
        assertEquals("Bearer t1", gen.getHeader("Authorization"))
        assertEquals("guide", JSONObject(gen.body.readUtf8()).getString("kind"))
        assertEquals(RelayQuota(30, 29, Instant.parse("2026-10-20T15:00:00Z")), c.quota.value)
    }

    @Test
    fun `waits for the relay as long as the relay waits for the model`() {
        assertTrue(RelayClient.defaultHttp.readTimeoutMillis >= 28_000)
    }

    @Test
    fun `keeps the session for the next call`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(generated())
        server.enqueue(generated("2"))
        val c = client()
        c.generate("s", "u")
        assertEquals("2", c.generate("s", "u"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `binds the Integrity token to the install and the nonce`() = runBlocking {
        server.enqueue(challenge("abc"))
        server.enqueue(session())
        server.enqueue(generated())
        var asked: String? = null
        client(integrity = { hash -> asked = hash; "integrity-token" }).generate("s", "u")

        server.takeRequest()
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("integrity-token", body.getString("integrityToken"))
        assertEquals(RelayClient.requestHash("0b6f3c1e-8a2d-4f0b-9a57-3c2e1d4b5a69", "abc"), asked)
        // base64url of SHA-256, no padding: 43 characters.
        assertEquals(43, asked!!.length)
    }

    @Test
    fun `opens a new session once when the relay no longer knows the token`() = runBlocking {
        store.token = "old" to Instant.parse("2026-10-21T00:00:00Z")
        server.enqueue(problem(401, "token_invalid"))
        server.enqueue(challenge())
        server.enqueue(session("t2"))
        server.enqueue(generated())
        assertEquals("こんにちは", client().generate("s", "u"))
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        server.takeRequest()
        server.takeRequest()
        assertEquals("Bearer t2", server.takeRequest().getHeader("Authorization"))
        assertEquals("t2", store.token?.first)
    }

    @Test
    fun `renews a token about to expire before using it`() = runBlocking {
        store.token = "old" to now.plusSeconds(60)
        server.enqueue(challenge())
        server.enqueue(session("t2"))
        server.enqueue(generated())
        client().generate("s", "u")
        assertEquals("/api/v1/session/challenge", server.takeRequest().path)
    }

    @Test
    fun `reports the daily limit with the quota`() = runBlocking {
        store.token = "t" to Instant.parse("2026-10-21T00:00:00Z")
        server.enqueue(problem(429, "daily_limit_reached", ""","quota":{"limit":30,"remaining":0,"resetsAt":"2026-10-20T15:00:00Z"}"""))
        val c = client()
        try {
            c.generate("s", "u")
            fail()
        } catch (e: RelayException) {
            assertEquals("daily_limit_reached", e.code)
            assertEquals("今日の運営の AI の回数（30 回）を使い切りました", e.message)
            assertTrue(c.quota.value!!.exhausted(now))
        }
    }

    @Test
    fun `names the failures the app shows`() = runBlocking {
        store.token = "t" to Instant.parse("2026-10-21T00:00:00Z")
        server.enqueue(problem(503, "relay_paused"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>"))
        val c = client()
        for (expected in listOf("relay_paused", "too_many_requests", "upstream_error")) {
            try {
                c.generate("s", "u")
                fail()
            } catch (e: RelayException) {
                assertEquals(expected, e.code)
                assertNull(e.quota)
            }
        }
    }

    @Test
    fun `asks for the quota without generating`() = runBlocking {
        store.token = "t" to Instant.parse("2026-10-21T00:00:00Z")
        server.enqueue(ok(quotaJson))
        assertEquals(29, client().refreshQuota().remaining)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/v1/quota", req.path)
    }

    @Test
    fun `says when the relay can't be reached`() = runBlocking {
        server.shutdown()
        try {
            client().generate("s", "u")
            fail()
        } catch (e: RelayException) {
            assertEquals("network", e.code)
        }
    }

    private val plusQuota = """{"limit":200,"remaining":200,"resetsAt":"2026-10-20T15:00:00Z","plan":"plus"}"""
    private fun planned() = ok("""{"plan":{"name":"plus","expiresAt":"2026-11-20T03:00:00Z"},"quota":$plusQuota}""")

    @Test
    fun `sends a held たっぷりプラン purchase with each new session (API-AI-RELAY R-18)`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(planned())
        server.enqueue(ok("""{"text":"やあ","quota":{"limit":200,"remaining":199,"resetsAt":"2026-10-20T15:00:00Z","plan":"plus"}}"""))
        val c = client(plans = { PlanPurchase("pt-1", "plus") })

        assertEquals("やあ", c.generate("s", "u", Purpose.CHAT))

        server.takeRequest()
        server.takeRequest()
        val plan = server.takeRequest()
        assertEquals("/api/v1/plan", plan.path)
        assertEquals("Bearer t1", plan.getHeader("Authorization"))
        val body = JSONObject(plan.body.readUtf8())
        assertEquals("pt-1", body.getString("purchaseToken"))
        assertEquals("plus", body.getString("productId"))
        assertEquals("/api/v1/generate", server.takeRequest().path)
        assertEquals(RelayQuota(200, 199, Instant.parse("2026-10-20T15:00:00Z"), RelayPlan.PLUS), c.quota.value)
    }

    @Test
    fun `stays free when the relay can't check the purchase, and doesn't send it again for 10 minutes`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(problem(502, "upstream_error"))
        server.enqueue(generated())
        val c = client(plans = { PlanPurchase("pt-1", "plus") })

        assertEquals("こんにちは", c.generate("s", "u", Purpose.GUIDE))

        assertEquals(4, server.requestCount)
        assertEquals(RelayPlan.FREE, c.quota.value?.plan)
    }

    @Test
    fun `sends nothing without a purchase, and reads an unknown plan as free`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(ok("""{"text":"x","quota":{"limit":30,"remaining":29,"resetsAt":"2026-10-20T15:00:00Z","plan":"gold"}}"""))
        val c = client(plans = { null })
        c.generate("s", "u", Purpose.GUIDE)
        assertEquals(3, server.requestCount)
        assertEquals(RelayPlan.FREE, c.quota.value?.plan)
    }

    @Test
    fun `sends a new purchase and tells when it isn't active`() = runBlocking {
        server.enqueue(challenge())
        server.enqueue(session())
        server.enqueue(planned())
        val c = client()
        assertEquals(RelayPlan.PLUS, c.sendPlan(PlanPurchase("pt-2", "plus")).plan)

        server.enqueue(problem(403, "plan_not_active"))
        try {
            c.sendPlan(PlanPurchase("pt-3", "plus"))
            fail("expected plan_not_active")
        } catch (e: RelayException) {
            assertEquals("plan_not_active", e.code)
            assertEquals("たっぷりプランが有効になっていません", e.message)
        }
    }
}
