package io.github.duwenji.yorisoi.kit.geo

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** Local events from the operator's tiles (API-SPOT-TILES AP-9〜AP-11, REQ-LOCAL-EVENTS F-23). */
class EventTilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = at("2026-11-21T10:00")

    private fun at(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), LocalEvents.ZONE).toInstant().toEpochMilli()

    /** The tile the point lies in has events; the others around it have none (404). */
    private val here = Geohash.encode(35.0, 139.0, 5)

    private var down = false
    private var until = "2026-12-18"
    private val etag get() = "\"$until\""
    private val requests = mutableListOf<String>()
    private var overpassAsked = 0

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        if ("overpass" in request.url.toString()) {
            overpassAsked++
            return@addInterceptor builder.code(500).message("Error").body("".toResponseBody(null)).build()
        }
        val path = request.url.encodedPath
        requests += path
        val hash = path.substringAfterLast('/').removeSuffix(".json")
        when {
            down -> builder.code(500).message("Error").body("".toResponseBody(null)).build()
            !path.startsWith("/v1/events/") || hash != here -> builder.code(404).message("Not Found").body("".toResponseBody(null)).build()
            request.header("If-None-Match") == etag -> builder.code(304).message("Not Modified").body("".toResponseBody(null)).build()
            else -> builder.code(200).message("OK").header("ETag", etag).body(tile(hash).toResponseBody("application/json".toMediaType())).build()
        }
    }.build()

    private fun tile(hash: String): String {
        val source = """"source":{"publisher":"東京都江東区","dataset":"https://example.jp/dataset/1","license":"CC-BY-4.0"}"""
        return """{"format":"yorisoi-spot-tile","version":1,"layer":"events","tile":"$hash","osmTimestamp":"2026-11-17T20:21:03Z",
            "generatedAt":"2026-11-18T03:40:12Z","license":"ODbL-1.0","attribution":"© OpenStreetMap contributors",
            "eventsFrom":"2026-11-18","eventsUntil":"$until","events":[
            {"id":"131083:1","name":"朝市","startDate":"2026-11-21","endDate":"2026-11-21","venue":"公園","lat":35.002,"lon":139.0,"precision":"point",$source},
            {"id":"131083:2","name":"作品展","startDate":"2026-11-28","endDate":"2026-11-29","venue":"会館","lat":35.003,"lon":139.0,"precision":"point",$source},
            {"id":"131083:3","name":"読めない催し","startDate":"2026-11-21","endDate":"2026-11-21","venue":"会館","lat":35.003,"lon":139.0,"precision":"area",$source}
            ]}"""
    }

    private var useCache = true
    private val spotsDir by lazy { folder.newFolder("spots") }
    private val cache by lazy { SpotCache(spotsDir, now = { clock }) }

    private fun repository(withTiles: Boolean = true) = SpotRepository(
        OverpassClient(http), cache = cache, now = { clock }, useCache = { useCache },
        tiles = if (withTiles) SpotTiles("https://tiles.example/", "test/1", http, now = { clock }) else null,
    )

    private val point = LatLon(35.0, 139.0)

    @Test
    fun `the events come from the tiles around, a tile with none is no error, and Overpass is not asked`() = runBlocking {
        val events = repository().localEvents(point)
        assertEquals(listOf("131083:1", "131083:2"), events.map { it.id })
        assertTrue(requests.isNotEmpty() && requests.all { it.startsWith("/v1/events/") })
        assertEquals(0, overpassAsked)
        // Today's, within 1km.
        assertEquals(listOf("朝市"), LocalEvents.nearby(events, point, clock).map { it.event.name })
    }

    @Test
    fun `kept tiles are used without asking, and a tile with none is asked about again later`() = runBlocking {
        val spots = repository()
        spots.localEvents(point)
        val asked = requests.size
        spots.localEvents(point)
        assertEquals(asked, requests.size)

        // The next day, another walk: the kept tile with events is read from the device.
        clock += 24 * 60 * 60 * 1000
        val next = repository()
        assertEquals(2, next.localEvents(point).size)
        val tilesWithNone = asked - 1
        assertEquals(asked + tilesWithNone, requests.size)
    }

    @Test
    fun `a tile past its eventsUntil is not used, and is asked about again`() = runBlocking {
        until = "2026-11-20"
        val spots = repository()
        assertTrue(spots.localEvents(point).isEmpty())
        val asked = requests.size
        // Not asked again within the hour.
        assertTrue(spots.localEvents(point).isEmpty())
        assertEquals(asked, requests.size)
        // The next week's batch has made it again.
        until = "2026-12-20"
        clock += 2 * 60 * 60 * 1000
        assertEquals(2, spots.localEvents(point).size)
    }

    @Test
    fun `when the tiles can't be had there are no events`() = runBlocking {
        down = true
        assertTrue(repository().localEvents(point).isEmpty())
        assertEquals(0, overpassAsked)
    }

    @Test
    fun `with the cache turned off the tiles are not kept, and without tiles there are no events`() = runBlocking {
        useCache = false
        repository().localEvents(point)
        assertTrue(spotsDir.walkTopDown().none { it.isFile && it.name.startsWith("events_") })
        assertTrue(repository(withTiles = false).localEvents(point).isEmpty())
        // No location yet.
        assertTrue(repository().localEvents().isEmpty())
    }
}
