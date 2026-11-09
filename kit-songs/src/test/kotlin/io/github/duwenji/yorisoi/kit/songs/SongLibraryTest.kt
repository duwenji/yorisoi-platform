package io.github.duwenji.yorisoi.kit.songs

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import kotlin.random.Random

/** The songs kept on the device (platform API-SONGS「アプリの取り方」, エラー表). */
class SongLibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()

    /** 10 November: autumn, winter next. */
    private var clock = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 12, 0) }.timeInMillis

    private val sounds = mapOf(
        "momiji" to "紅葉の音".toByteArray(),
        "akatombo" to "赤とんぼの音".toByteArray(),
        "yuki" to "雪の音".toByteArray(),
        "haru" to "春の音".toByteArray(),
        "furusato" to "故郷の音".toByteArray(),
    )
    private val seasons = mapOf("momiji" to "autumn", "akatombo" to "autumn", "yuki" to "winter", "haru" to "spring", "furusato" to "all")
    private var catalogEtag = "\"v1\""

    private fun song(id: String, times: List<String> = emptyList(), sha: String? = null) = JSONObject(
        mapOf(
            "id" to id, "title" to id, "seasons" to JSONArray(listOf(seasons.getValue(id))), "timesOfDay" to JSONArray(times),
            "weather" to JSONArray(), "durationSec" to 40, "file" to "$id-0001.m4a", "bytes" to sounds.getValue(id).size,
            "sha256" to (sha ?: SongLibrary.sha256(sounds.getValue(id))),
            "rights" to JSONObject(
                mapOf(
                    "lyricist" to JSONObject(mapOf("name" to "高野辰之", "died" to 1947)),
                    "composer" to JSONObject(mapOf("name" to "不詳（文部省唱歌）", "died" to JSONObject.NULL)),
                    "score" to "尋常小学唱歌", "notesFrom" to JSONObject(mapOf("title" to "紅葉 - Wikipedia", "license" to "CC BY-SA 4.0")),
                ),
            ),
        ),
    )

    private var catalogJson = catalog(song("momiji", listOf("evening")), song("akatombo"), song("yuki"), song("haru"), song("furusato"))

    private fun catalog(vararg songs: JSONObject) = JSONObject(
        mapOf("format" to "yorisoi-songs", "version" to 1, "updatedAt" to "2026-11-10T00:00:00Z", "credit" to "演奏: yorisoi 運営", "songs" to JSONArray(songs.toList())),
    ).toString()

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path.orEmpty()
                return when {
                    path == "/songs/v1/catalog.json" ->
                        if (request.getHeader("If-None-Match") == catalogEtag) MockResponse().setResponseCode(304)
                        else MockResponse().setBody(catalogJson).setHeader("ETag", catalogEtag)
                    path.startsWith("/songs/v1/audio/") -> {
                        val id = path.removePrefix("/songs/v1/audio/").substringBefore("-0001")
                        sounds[id]?.let { MockResponse().setBody(Buffer().write(it)) } ?: MockResponse().setResponseCode(404)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun library(random: Random = Random(1)) =
        SongLibrary(folder.root.resolve("songs"), server.url("/").toString(), "Test/1.0", now = { clock }, random = random)

    @Test
    fun `the catalog is fetched once a day, with its ETag after the first time (AS-1)`() = runBlocking {
        val lib = library()
        assertTrue(lib.refresh())
        assertEquals(5, lib.catalog!!.songs.size)
        lib.refresh()
        assertEquals(1, requests.size)
        clock += SongLibrary.DAY_MS
        lib.refresh()
        assertEquals("\"v1\"", requests.last().getHeader("If-None-Match"))
        assertEquals("Test/1.0", requests.last().getHeader("User-Agent"))
        // Kept across a new start of the app.
        assertEquals(5, library().catalog!!.songs.size)
    }

    @Test
    fun `this season's, the next's and all seasons' sounds are fetched ahead, checked by their hash (AS-2)`() = runBlocking {
        catalogJson = catalog(song("momiji"), song("akatombo", sha = "0".repeat(64)), song("yuki"), song("haru"), song("furusato"))
        val lib = library()
        lib.refresh()
        assertEquals(3, lib.prefetch())
        val byId = lib.catalog!!.songs.associateBy { it.id }
        assertNotNull(lib.fileOf(byId.getValue("momiji")))
        assertNotNull(lib.fileOf(byId.getValue("yuki")))
        assertNotNull(lib.fileOf(byId.getValue("furusato")))
        // Spring is two seasons away; the hash of 赤とんぼ doesn't match.
        assertNull(lib.fileOf(byId.getValue("haru")))
        assertNull(lib.fileOf(byId.getValue("akatombo")))
        // Kept ones aren't fetched again.
        requests.clear()
        assertEquals(0, lib.prefetch())
        assertTrue(requests.none { it.path!!.contains("/audio/momiji") })
    }

    @Test
    fun `a song is picked from the kept ones of the season, fitting the time of day first, not played lately (AS-4)`() = runBlocking {
        val lib = library()
        lib.refresh()
        lib.prefetch()
        assertEquals("momiji", lib.pick("evening", null, emptySet())!!.id)
        // Not 紅葉 again: 赤とんぼ (this season's) before 故郷 (all seasons').
        assertEquals("akatombo", lib.pick("evening", null, setOf("momiji"))!!.id)
        assertEquals("furusato", lib.pick("evening", null, setOf("momiji", "akatombo"))!!.id)
        // Winter's 雪 is kept, but it isn't winter yet.
        assertNull(lib.pick(null, null, setOf("momiji", "akatombo", "furusato")))
    }

    @Test
    fun `the songs played on the last walk and this one aren't picked`() = runBlocking {
        val lib = library()
        lib.refresh()
        lib.prefetch()
        lib.startWalk()
        lib.played(lib.catalog!!.songs.first { it.id == "momiji" })
        lib.startWalk()
        lib.played(lib.catalog!!.songs.first { it.id == "akatombo" })
        assertEquals(setOf("momiji", "akatombo"), lib.recentlyPlayed())
        lib.startWalk()
        assertEquals(setOf("akatombo"), lib.recentlyPlayed())
    }

    @Test
    fun `sounds the catalog no longer names are removed`() = runBlocking {
        val lib = library()
        lib.refresh()
        lib.prefetch()
        catalogJson = catalog(song("akatombo"), song("yuki"), song("furusato"))
        catalogEtag = "\"v2\""
        lib.refresh(force = true)
        assertFalse(folder.root.resolve("songs/audio/momiji-0001.m4a").exists())
        assertTrue(folder.root.resolve("songs/audio/yuki-0001.m4a").exists())
    }

    @Test
    fun `a catalog of another format, or failing to ask, leaves the kept one`() = runBlocking {
        val lib = library()
        lib.refresh()
        catalogJson = "{\"format\":\"yorisoi-songs\",\"version\":2,\"songs\":[]}"
        catalogEtag = "\"v3\""
        assertFalse(lib.refresh(force = true))
        assertEquals(5, lib.catalog!!.songs.size)
        server.shutdown()
        assertFalse(lib.refresh(force = true))
        assertEquals(5, lib.catalog!!.songs.size)
    }

    @Test
    fun `songs with wrong fields are left out, unknown values dropped`() {
        val bad = song("momiji").apply { put("bytes", 2_000_000) }
        val odd = song("yuki").apply { put("seasons", JSONArray(listOf("winter", "monsoon"))) }
        val parsed = SongCatalog.parse(catalog(bad, odd, song("furusato")))!!
        assertEquals(listOf("yuki", "furusato"), parsed.songs.map { it.id })
        assertEquals(setOf("winter"), parsed.songs.first().seasons)
        assertEquals("紅葉 - Wikipedia（CC BY-SA 4.0）", parsed.songs.first().notesFrom)
        assertNull(parsed.songs.last().composer.died)
    }

    @Test
    fun `seasons by the month`() {
        assertEquals("winter", SongSeason.of(12))
        assertEquals("winter", SongSeason.of(2))
        assertEquals("spring", SongSeason.of(3))
        assertEquals("summer", SongSeason.of(8))
        assertEquals("autumn", SongSeason.of(11))
        assertEquals("spring", SongSeason.next("winter"))
        assertEquals("winter", SongSeason.next("autumn"))
    }
}
