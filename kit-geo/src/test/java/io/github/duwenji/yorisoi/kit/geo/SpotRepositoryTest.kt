package io.github.duwenji.yorisoi.kit.geo

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Searching with kept answers, and waiting after a failure (kit-geo README「スポット検索のキャッシュ」). */
class SpotRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000_000_000L

    /** Whether the fake Overpass servers answer, what with, and how many times they were asked. */
    private var up = true
    private var answer = "鶴岡八幡宮"
    private var asked = 0

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        asked++
        val builder = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
        if (up) {
            val body = """{"elements":[{"type":"node","id":1,"lat":35.0,"lon":139.0,"tags":{"name":"$answer","historic":"yes"}}]}"""
            builder.code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        } else {
            builder.code(504).message("Gateway Timeout").body("".toResponseBody(null)).build()
        }
    }.build()

    private val cache by lazy { SpotCache(folder.newFolder("spots"), now = { clock }) }
    private val spots by lazy { newRepository() }

    private fun newRepository() = SpotRepository(OverpassClient(http), cache = cache, now = { clock })

    private fun names(of: SpotRepository = spots) = of.spots.value.map { it.name }

    private fun searchFails(lat: Double, lon: Double) = runBlocking {
        try {
            spots.search(lat, lon)
            fail("the search should have failed")
        } catch (e: IOException) {
            // Every server gave up, as they were meant to.
        }
    }

    @Test
    fun `a kept answer from nearby is used without asking`() = runBlocking {
        spots.search(35.0, 139.0)
        assertEquals(SpotSource.NETWORK, spots.source)
        // Another walk, the next day, from about the same place.
        clock += 24 * 60 * 60 * 1000
        asked = 0
        val again = newRepository()
        again.search(35.001, 139.0)
        assertEquals(0, asked)
        assertEquals(SpotSource.CACHE, again.source)
        assertEquals(listOf("鶴岡八幡宮"), names(again))
    }

    @Test
    fun `an old answer is shown, then the new one`() = runBlocking {
        spots.search(35.0, 139.0)
        clock += SpotCache.FRESH_MS + 1
        answer = "鎌倉宮"
        val again = newRepository()
        again.search(35.0, 139.0)
        assertEquals(SpotSource.NETWORK, again.source)
        assertEquals(2, again.updates)
        assertEquals(listOf("鎌倉宮"), names(again))
    }

    @Test
    fun `when every server fails, the nearest kept answer is shown`() {
        runBlocking { spots.search(35.0, 139.0) }
        up = false
        // About 450m away: too far to use as it is, near enough to show.
        searchFails(35.004, 139.0)
        assertEquals(SpotSource.FALLBACK, spots.source)
        assertEquals(listOf("鶴岡八幡宮"), names())
    }

    @Test
    fun `after a failure the next search waits, longer each time`() {
        up = false
        searchFails(35.0, 139.0)
        assertEquals(3, asked)

        // The next locations, right after: Overpass is given a rest.
        runBlocking { spots.search(35.0001, 139.0) }
        assertEquals(3, asked)

        clock += SpotRepository.RETRY_DELAYS_MS[0]
        searchFails(35.0002, 139.0)
        assertEquals(6, asked)

        clock += SpotRepository.RETRY_DELAYS_MS[0]
        runBlocking { spots.search(35.0003, 139.0) }
        assertEquals(6, asked)

        clock += SpotRepository.RETRY_DELAYS_MS[1] - SpotRepository.RETRY_DELAYS_MS[0]
        up = true
        runBlocking { spots.search(35.0004, 139.0) }
        assertEquals(7, asked)
        assertEquals(SpotSource.NETWORK, spots.source)
    }

    @Test
    fun `a refresh asks even with a kept answer, and even while waiting`() = runBlocking {
        spots.search(35.0, 139.0)
        asked = 0
        spots.search(35.0, 139.0, forceRefresh = true)
        assertEquals(1, asked)

        up = false
        searchFails(36.0, 139.0)
        up = true
        spots.search(36.0, 139.0, forceRefresh = true)
        assertEquals(SpotSource.NETWORK, spots.source)
    }

    @Test
    fun `a shorter freshness asks again sooner`() = runBlocking {
        spots.search(35.0, 139.0)
        clock += 2 * 24 * 60 * 60 * 1000
        asked = 0
        val oneDay = SpotRepository(OverpassClient(http), cache = cache, now = { clock }, freshMs = { 24 * 60 * 60 * 1000L })
        oneDay.search(35.0, 139.0)
        assertEquals(1, asked)
        assertEquals(SpotSource.NETWORK, oneDay.source)
    }

    @Test
    fun `with the cache turned off, answers are neither used nor kept`() {
        runBlocking { spots.search(35.0, 139.0) }
        asked = 0
        val off = SpotRepository(OverpassClient(http), cache = cache, now = { clock }, useCache = { false })
        runBlocking { off.search(35.0, 139.0) }
        assertEquals(1, asked)
        runBlocking { off.search(36.0, 139.0) }
        assertNull(cache.find(36.0, 139.0, emptySet(), 300.0, SpotCache.MAX_AGE_MS))

        up = false
        try {
            runBlocking { off.search(35.004, 139.0) }
            fail("the search should have failed")
        } catch (e: IOException) {
            // Nor is the kept answer from 450m away shown instead.
        }
        assertEquals(SpotSource.NETWORK, off.source)
    }

    @Test
    fun `without a cache it searches as before`() = runBlocking {
        val plain = SpotRepository(OverpassClient(http), now = { clock })
        plain.search(35.0, 139.0)
        plain.search(35.001, 139.0)
        assertEquals(1, asked)
        plain.search(35.004, 139.0)
        assertEquals(2, asked)
        assertTrue(plain.spots.value.isNotEmpty())
    }
}
