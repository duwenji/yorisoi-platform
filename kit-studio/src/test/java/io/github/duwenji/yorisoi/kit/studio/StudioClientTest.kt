package io.github.duwenji.yorisoi.kit.studio

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StudioClientTest {
    private lateinit var server: MockWebServer
    private var tokens = mutableListOf("t1", "t2")
    private var forgotten = 0
    private lateinit var client: StudioClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = mutableListOf("t1", "t2")
        forgotten = 0
        client = StudioClient(server.url("/api/").toString(), object : StudioTokens {
            override suspend fun token() = tokens.first()
            override fun forget() {
                forgotten++
                tokens.removeAt(0)
            }
        })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `lists the cells it asks for, with the relay's token`() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"notes":[{"id":"N1","text":"夕日がきれい","cell":"xn77h2b","spotName":"夕やけだんだん","expiresAt":"2026-11-01T03:00:00Z","mine":false}],
                   "placements":[{"id":"P1","providerName":"谷中銀座商店会","text":"豆まき","audio":{"url":"https://a/p.m4a","durationSec":24,"bytes":196000,"contentType":"audio/mp4","sha256":"ab"},
                   "cells":["xn77h2"],"startsAt":"2026-10-31T00:00:00Z","endsAt":"2026-11-02T00:00:00Z","hours":{"from":"10:00","to":"18:00"}}],
                   "fetchedAt":"2026-10-31T05:00:00Z","refetchAfterSec":300}""",
            ),
        )
        val list = client.cells(listOf("xn77h2", "xn77h3"), notes = true, placements = false)
        val request = server.takeRequest()
        assertEquals("/api/v1/studio/cells?cells=xn77h2%2Cxn77h3&kinds=note", request.path)
        assertEquals("Bearer t1", request.getHeader("Authorization"))
        assertEquals(Note("N1", "夕日がきれい", "xn77h2b", "夕やけだんだん", Instant.parse("2026-11-01T03:00:00Z"), false), list.notes.single())
        assertEquals(Hours("10:00", "18:00"), list.placements.single().hours)
        assertEquals(24.0, list.placements.single().audio!!.durationSec, 0.0)
    }

    @Test
    fun `opens a new session once when the token is unknown`() = runBlocking {
        server.enqueue(json(401, """{"code":"token_invalid"}"""))
        server.enqueue(json(201, """{"id":"N1","expiresAt":"2026-11-01T03:00:00Z","remainingToday":9}"""))
        val placed = client.placeNote("夕日", "xn77h2b", null, NoteTtl.DAY, 1)
        assertEquals(9, placed.remainingToday)
        assertEquals(1, forgotten)
        server.takeRequest()
        val retry = server.takeRequest()
        assertEquals("Bearer t2", retry.getHeader("Authorization"))
        val body = JSONObject(retry.body.readUtf8())
        assertEquals("24h", body.getString("ttl"))
        assertFalse(body.has("spotName"))
    }

    @Test
    fun `reports the studio's code, with until and resetsAt`() = runBlocking {
        server.enqueue(json(403, """{"code":"author_banned","until":"2026-12-01T00:00:00Z"}"""))
        try {
            client.placeNote("x", "xn77h2b", null, NoteTtl.HOUR, 1)
            fail()
        } catch (e: StudioException) {
            assertEquals("author_banned", e.code)
            assertEquals(Instant.parse("2026-12-01T00:00:00Z"), e.until)
            assertEquals("この端末からは一言を置けません", e.message)
        }
        // WAF's 429 has no problem body.
        server.enqueue(MockResponse().setResponseCode(429).setBody("Too Many Requests"))
        try {
            client.cells(listOf("xn77h2"), notes = true, placements = true)
            fail()
        } catch (e: StudioException) {
            assertEquals("too_many_requests", e.code)
        }
    }

    @Test
    fun `sends reactions and reads my notes with their approximate counts`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))
        client.reactions(listOf(Ref.note("N1"), Ref.placement("P1")), listOf(Ref.placement("P1")), listOf("N1"))
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("placement", sent.getJSONArray("heard").getJSONObject(1).getString("type"))
        assertEquals("N1", sent.getJSONArray("liked").getString(0))

        server.enqueue(
            json(200, """{"notes":[{"id":"N1","text":"x","cell":"xn77h2b","expiresAt":"2026-11-01T03:00:00Z","state":"active","heard":{"atLeast":3},"likes":null}],"remainingToday":7}"""),
        )
        val (mine, left) = client.myNotes()
        assertEquals(Approx(3), mine.single().heard)
        assertNull(mine.single().likes)
        assertEquals(7, left)
    }

    @Test
    fun `reads the terms without a token`() = runBlocking {
        server.enqueue(json(200, """{"version":1,"url":"https://example.org/terms","updatedAt":""}"""))
        assertEquals(Terms(1, "https://example.org/terms"), client.terms())
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `cuts spoken notes at 60 characters`() {
        assertEquals(60, NoteRecognizer.fit("あ".repeat(80)).length)
        assertTrue(NoteRecognizer.fit("短い").length == 2)
    }
}
