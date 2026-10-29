package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.format.SpotKind
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spots from the operator's tiles, and Overpass when they can't be had (API-SPOT-TILES). */
class SpotTilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000_000_000L

    /** The tile the search's centre lies in; the others around it are empty. */
    private val here = Geohash.encode(35.0, 139.0, 5)

    /** How the fake tile server answers: 200, 404 or 500; the requests it had; the ETag of every tile. */
    private var tileStatus = 200
    private var tileVersion = 1
    private var etag = "\"v1\""
    private val tileRequests = mutableListOf<String>()
    private val ifNoneMatch = mutableListOf<String?>()
    private var overpassAsked = 0

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        if (request.url.host == "overpass-api.de" || "overpass" in request.url.toString()) {
            overpassAsked++
            val body = """{"elements":[{"type":"node","id":9,"lat":35.0,"lon":139.0,"tags":{"name":"Overpass の史跡","historic":"yes"}}]}"""
            return@addInterceptor builder.code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }
        val path = request.url.encodedPath // /v1/base/xxxxx.json
        tileRequests += path
        ifNoneMatch += request.header("If-None-Match")
        val (layer, file) = path.removePrefix("/v1/").split('/')
        val hash = file.removeSuffix(".json")
        when {
            tileStatus == 404 -> builder.code(404).message("Not Found").body("".toResponseBody(null)).build()
            tileStatus != 200 -> builder.code(500).message("Error").body("".toResponseBody(null)).build()
            request.header("If-None-Match") == etag -> builder.code(304).message("Not Modified").body("".toResponseBody(null)).build()
            else -> builder.code(200).message("OK").header("ETag", etag)
                .body(tile(layer, hash).toResponseBody("application/json".toMediaType())).build()
        }
    }.build()

    private fun tile(layer: String, hash: String): String {
        val head = """"format":"yorisoi-spot-tile","version":$tileVersion,"layer":"$layer","tile":"$hash","osmTimestamp":"2026-10-28T00:00:00Z","generatedAt":"2026-10-29T00:00:00Z","license":"ODbL-1.0","attribution":"© OpenStreetMap contributors""""
        val mine = hash == here
        return if (layer == "base") {
            val spots = if (!mine) "" else listOf(
                """{"id":"node/1","lat":35.003,"lon":139.0,"tags":{"name":"近くの史跡","historic":"yes"}}""",
                // About 900m north: too far.
                """{"id":"node/2","lat":35.008,"lon":139.0,"tags":{"name":"遠くの寺","amenity":"place_of_worship","religion":"buddhist"}}""",
                // A park whose centre is 900m away, its edge 400m away: found, as Overpass finds it.
                """{"id":"way/3","lat":35.0,"lon":139.0099,"tags":{"name":"大きな公園","leisure":"park"},"shape":{"area":true,"lines":[[35.001,139.0044,35.001,139.0154,34.999,139.0154,34.999,139.0044,35.001,139.0044]]}}""",
            ).joinToString(",")
            val facilities = if (!mine) "" else listOf(
                """{"id":"node/10","kind":"TOILETS","lat":35.003,"lon":139.0}""",
                // A bench 330m away: only close benches count.
                """{"id":"node/11","kind":"BENCH","lat":35.003,"lon":139.0}""",
                """{"id":"node/12","kind":"BICYCLE_PARKING","lat":35.001,"lon":139.0}""",
                """{"id":"node/13","kind":"SHELTER","lat":35.001,"lon":139.0,"publicTransport":true}""",
                """{"id":"node/14","kind":"SOMETHING_NEW","lat":35.001,"lon":139.0}""",
            ).joinToString(",")
            val signals = if (mine) "35.001,139.0,35.02,139.0" else ""
            """{$head,"spots":[$spots],"facilities":[$facilities],"signals":[$signals]}"""
        } else {
            val shops = if (!mine) "" else listOf(
                """{"id":"node/20","lat":35.002,"lon":139.0,"tags":{"name":"喫茶","amenity":"cafe"}}""",
                """{"id":"node/21","lat":35.002,"lon":139.0,"tags":{"name":"古書店","shop":"books"}}""",
                // A cafe 550m away: shops are searched closer.
                """{"id":"node/22","lat":35.005,"lon":139.0,"tags":{"name":"遠くの喫茶","amenity":"cafe"}}""",
            ).joinToString(",")
            """{$head,"shops":[$shops]}"""
        }
    }

    private var shops: Set<SpotKind> = emptySet()
    private var useCache = true
    private val cache by lazy { SpotCache(folder.newFolder("spots"), now = { clock }) }

    private fun repository(signals: Boolean = false, facilities: Set<FacilityKind> = FacilityKind.WALK) = SpotRepository(
        OverpassClient(http), shops = { shops }, cache = cache, now = { clock }, useCache = { useCache },
        signals = signals, facilityKinds = facilities,
        tiles = SpotTiles("https://tiles.example/", "test/1", http, now = { clock }),
    )

    private fun names(r: SpotRepository) = r.spots.value.map { it.name }.sorted()

    @Test
    fun `spots come from the tiles, cut to a search's distances, without asking Overpass`() = runBlocking {
        val spots = repository()
        spots.search(35.0, 139.0)
        assertEquals(0, overpassAsked)
        assertEquals(SpotSource.TILES, spots.source)
        assertEquals(listOf("大きな公園", "近くの史跡"), names(spots))
        assertEquals("史跡", spots.spots.value.first { it.name == "近くの史跡" }.category)
        // A walker's amenities: the toilet and the bus stop roof; not the far bench, not bicycle parking, not an unknown kind.
        assertEquals(listOf(FacilityKind.TOILETS, FacilityKind.SHELTER), spots.facilities.value.map { it.kind })
        assertEquals("バス停", spots.facilities.value.first { it.kind == FacilityKind.SHELTER }.name)
        assertTrue(spots.trafficSignals.value.isEmpty())
        // The base layer only, of each tile around.
        assertTrue(tileRequests.all { it.startsWith("/v1/base/") })
    }

    @Test
    fun `shops are fetched only for a channel that searches them, and only its kinds`() = runBlocking {
        shops = setOf(SpotKind.CAFE)
        val spots = repository()
        spots.search(35.0, 139.0)
        assertTrue(tileRequests.any { it.startsWith("/v1/shops/$here") })
        assertEquals(listOf("喫茶", "大きな公園", "近くの史跡"), names(spots))
        assertEquals("カフェ", spots.spots.value.first { it.name == "喫茶" }.category)
    }

    @Test
    fun `a cyclist gets its amenities and the signals near by`() = runBlocking {
        val spots = repository(signals = true, facilities = setOf(FacilityKind.BICYCLE_PARKING))
        spots.search(35.0, 139.0)
        assertEquals(listOf(FacilityKind.BICYCLE_PARKING), spots.facilities.value.map { it.kind })
        assertEquals(listOf(LatLon(35.001, 139.0)), spots.trafficSignals.value)
    }

    @Test
    fun `kept tiles are used without asking, then asked about with their ETag`() = runBlocking {
        repository().search(35.0, 139.0)
        val asked = tileRequests.size

        // The next day, another walk: the kept tiles are new enough.
        clock += 24 * 60 * 60 * 1000
        val next = repository()
        next.search(35.0, 139.0)
        assertEquals(asked, tileRequests.size)
        assertEquals(SpotSource.KEPT_TILES, next.source)

        // A week later they are asked about again, and haven't changed.
        clock += SpotCache.FRESH_MS
        val later = repository()
        later.search(35.0, 139.0)
        assertEquals(SpotSource.TILES, later.source)
        assertTrue(ifNoneMatch.drop(asked).all { it == etag })
        assertEquals(listOf("大きな公園", "近くの史跡"), names(later))
        assertEquals(0, overpassAsked)
    }

    @Test
    fun `outside the tiles' range Overpass is asked, and the tiles not again`() = runBlocking {
        tileStatus = 404
        val spots = repository()
        spots.search(35.0, 139.0)
        assertEquals(1, overpassAsked)
        assertEquals(SpotSource.NETWORK, spots.source)
        assertEquals(listOf("Overpass の史跡"), names(spots))
        val asked = tileRequests.size
        spots.search(35.01, 139.0)
        assertEquals(asked, tileRequests.size)
    }

    @Test
    fun `when the tiles can't be had, old kept ones are used, else Overpass`() = runBlocking {
        tileStatus = 500
        val first = repository()
        first.search(35.0, 139.0)
        assertEquals(SpotSource.NETWORK, first.source)

        tileStatus = 200
        repository().search(35.0, 139.0)
        clock += SpotCache.FRESH_MS + 1
        tileStatus = 500
        overpassAsked = 0
        val later = repository()
        later.search(35.0, 139.0)
        assertEquals(0, overpassAsked)
        assertEquals(SpotSource.KEPT_TILES, later.source)
    }

    @Test
    fun `a tile of another version is not read`() = runBlocking {
        tileVersion = 2
        val spots = repository()
        spots.search(35.0, 139.0)
        assertEquals(SpotSource.NETWORK, spots.source)
        assertTrue(cache.tiles.read("base", here) == null)
    }

    @Test
    fun `with the cache turned off, tiles are not kept on the device`() = runBlocking {
        useCache = false
        repository().search(35.0, 139.0)
        assertNull(cache.tiles.read("base", here))
    }

    @Test
    fun `clearing the cache clears the tiles`() = runBlocking {
        repository().search(35.0, 139.0)
        assertTrue(cache.tiles.read("base", here) != null)
        cache.clear()
        assertNull(cache.tiles.read("base", here))
    }

    @Test
    fun `a way is fetched ahead as tiles, without Overpass`() = runBlocking {
        // About 10km north.
        val way = (0..100).map { LatLon(35.0 + it * 0.0009, 139.0) }
        val fetched = repository().prefetch(listOf(way), pauseMs = 0)
        assertTrue(fetched in 3..10)
        assertEquals(fetched, tileRequests.size)
        assertEquals(0, overpassAsked)
        // Done again, nothing is fetched: all are kept and fresh.
        assertEquals(0, repository().prefetch(listOf(way), pauseMs = 0))
    }

    @Test
    fun `a way is searched on Overpass when the tiles can't be had`() = runBlocking {
        tileStatus = 500
        val way = listOf(LatLon(35.0, 139.0), LatLon(35.005, 139.0))
        repository().prefetch(listOf(way), pauseMs = 0)
        assertTrue(overpassAsked >= 1)
    }
}
