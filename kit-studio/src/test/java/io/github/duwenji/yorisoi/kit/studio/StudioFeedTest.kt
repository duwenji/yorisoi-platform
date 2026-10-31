package io.github.duwenji.yorisoi.kit.studio

import io.github.duwenji.yorisoi.kit.geo.Geohash
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

class StudioFeedTest {
    @get:Rule val tmp = TemporaryFolder()

    // Yanaka, Tokyo.
    private val lat = 35.7275
    private val lon = 139.7671
    private val here7 = Geohash.encode(lat, lon, 7)
    private val here6 = Geohash.encode(lat, lon, 6)
    private var now = Instant.parse("2026-10-31T03:00:00Z") // 12:00 in Japan
    private var settings = StudioSettings(listenNotes = true, listenPlacements = true, explained = true, termsVersion = 1)
    private lateinit var api: FakeApi
    private lateinit var memory: StudioMemory
    private lateinit var feed: StudioFeed

    private class FakeApi : StudioApi {
        var list = CellList(emptyList(), emptyList(), Instant.EPOCH, 300)
        val asked = mutableListOf<Pair<List<String>, Pair<Boolean, Boolean>>>()
        val sent = mutableListOf<Triple<List<Ref>, List<Ref>, List<String>>>()
        var fail: StudioException? = null

        override suspend fun cells(cells: List<String>, notes: Boolean, placements: Boolean): CellList {
            asked += cells to (notes to placements)
            fail?.let { throw it }
            return list
        }

        override suspend fun reactions(heard: List<Ref>, opened: List<Ref>, liked: List<String>) {
            fail?.let { throw it }
            sent += Triple(heard, opened, liked)
        }
    }

    @Before
    fun setUp() {
        api = FakeApi()
        memory = StudioMemory(File(tmp.root, "studio.json"))
        feed = newFeed()
    }

    private fun newFeed(clips: ClipFetcher? = null) = StudioFeed(api, memory, clips, { settings }, { now })

    private fun note(id: String, cell: String = here7, mine: Boolean = false, spot: String? = null) =
        Note(id, "一言$id", cell, spot, now.plus(Duration.ofHours(24)), mine)

    private fun placement(id: String, cells: List<String> = listOf(here6), hours: Hours? = null, audio: PlacementAudio? = null, endsIn: Duration = Duration.ofDays(2)) =
        Placement(id, "谷中銀座商店会", "お知らせ$id", audio, cells, now.minus(Duration.ofHours(1)), now.plus(endsIn), hours)

    private fun pass(minutes: Long) {
        now = now.plus(Duration.ofMinutes(minutes))
    }

    @Test
    fun `asks for the nine cells around, and only the kinds the user listens to`() = runBlocking {
        settings = settings.copy(listenPlacements = false)
        feed.onLocation(lat, lon)
        val (cells, kinds) = api.asked.single()
        assertEquals(9, cells.size)
        assertTrue(here6 in cells)
        assertEquals(true to false, kinds)
    }

    @Test
    fun `asks again only when the cells change or the studio's wait has passed`() = runBlocking {
        feed.onLocation(lat, lon)
        feed.onLocation(lat, lon)
        assertEquals(1, api.asked.size)
        pass(5)
        feed.onLocation(lat, lon)
        assertEquals(2, api.asked.size)
        feed.onLocation(lat + 0.02, lon)
        assertEquals(3, api.asked.size)
    }

    @Test
    fun `asks for nothing when the user listens to neither`() = runBlocking {
        settings = settings.copy(listenNotes = false, listenPlacements = false)
        feed.onLocation(lat, lon)
        assertTrue(api.asked.isEmpty())
        assertNull(feed.next())
    }

    @Test
    fun `reads a placement first, then notes, each once, and not my own`() = runBlocking {
        api.list = CellList(listOf(note("N1"), note("N2", mine = true), note("N3", spot = "夕やけだんだん")), listOf(placement("P1")), now, 300)
        feed.onLocation(lat, lon)
        val first = feed.next() as Reading.OfPlacement
        assertEquals("谷中銀座商店会からのお知らせです。お知らせP1", first.spoken)
        assertEquals("この辺りを通った人の一言です。一言N1", feed.next()!!.spoken)
        assertEquals("夕やけだんだんのそばで、この辺りを通った人の一言です。一言N3", feed.next()!!.spoken)
        assertNull(feed.next())
        assertEquals(3, feed.heard.value.size)
    }

    @Test
    fun `reads notes in the cells around, not farther`() = runBlocking {
        val next7 = StudioFeed.around(lat, lon, 7).first { it != here7 }
        val far7 = Geohash.encode(lat + 0.01, lon, 7)
        api.list = CellList(listOf(note("FAR", cell = far7), note("NEXT", cell = next7)), emptyList(), now, 300)
        feed.onLocation(lat, lon)
        assertEquals("NEXT", (feed.next() as Reading.OfNote).note.id)
        assertNull(feed.next())
    }

    @Test
    fun `keeps to ten notes and two placements an hour`() = runBlocking {
        api.list = CellList((1..12).map { note("N$it") }, (1..3).map { placement("P$it") }, now, 300)
        feed.onLocation(lat, lon)
        val read = generateSequence { runBlocking { feed.next() } }.toList()
        assertEquals(2, read.count { it is Reading.OfPlacement })
        assertEquals(10, read.count { it is Reading.OfNote })
        pass(61)
        feed.onLocation(lat, lon)
        assertTrue(feed.next() is Reading.OfPlacement)
    }

    @Test
    fun `reads a placement once a day, within its hours, and not in other cells`() = runBlocking {
        val other6 = Geohash.encode(lat + 0.02, lon, 6)
        api.list = CellList(emptyList(), listOf(placement("P1"), placement("EVE", hours = Hours("18:00", "21:00")), placement("AWAY", cells = listOf(other6))), now, 300)
        feed.onLocation(lat, lon)
        assertEquals("P1", (feed.next() as Reading.OfPlacement).placement.id)
        assertNull(feed.next())
        pass(7 * 60) // 19:00 in Japan
        feed.onLocation(lat, lon)
        assertEquals("EVE", (feed.next() as Reading.OfPlacement).placement.id)
        pass(16 * 60) // the next day, 11:00
        feed.onLocation(lat, lon)
        assertEquals("P1", (feed.next() as Reading.OfPlacement).placement.id)
    }

    @Test
    fun `remembers what was read across walks`() = runBlocking {
        api.list = CellList(listOf(note("N1")), emptyList(), now, 300)
        feed.onLocation(lat, lon)
        feed.next()
        val later = StudioFeed(api, StudioMemory(File(tmp.root, "studio.json")), null, { settings }, { now })
        later.onLocation(lat, lon)
        assertNull(later.next())
    }

    @Test
    fun `sends reactions at the end, and keeps them when it can't`() = runBlocking {
        api.list = CellList(listOf(note("N1")), listOf(placement("P1")), now, 300)
        feed.onLocation(lat, lon)
        val p = feed.next()!!
        val n = feed.next() as Reading.OfNote
        feed.opened(p)
        feed.like(n.note)
        feed.like(n.note)
        api.fail = StudioException("network", "x")
        feed.finish()
        assertTrue(api.sent.isEmpty())
        api.fail = null
        newFeed().start()
        val (heard, opened, liked) = api.sent.single()
        assertEquals(listOf(Ref.placement("P1"), Ref.note("N1")), heard)
        assertEquals(listOf(Ref.placement("P1")), opened)
        assertEquals(listOf("N1"), liked)
        newFeed().start()
        assertEquals(1, api.sent.size)
    }

    @Test
    fun `fetches a placement's audio with the list and hands it out`() = runBlocking {
        val clip = File(tmp.root, "ab.m4a").apply { writeText("x") }
        val audio = PlacementAudio("https://a/p.m4a", 24.0, 196000, "audio/mp4", "ab")
        api.list = CellList(emptyList(), listOf(placement("P1", audio = audio)), now, 300)
        val fetched = mutableListOf<String>()
        val withClips = newFeed { a -> fetched += a.url; clip }
        withClips.onLocation(lat, lon)
        assertEquals(listOf("https://a/p.m4a"), fetched)
        assertEquals(clip, (withClips.next() as Reading.OfPlacement).audio)
    }

    @Test
    fun `goes on with the last list when the studio fails, and drops it when paused`() = runBlocking {
        api.list = CellList(listOf(note("N1"), note("N2")), emptyList(), now, 300)
        feed.onLocation(lat, lon)
        feed.next()
        pass(6)
        api.fail = StudioException("too_many_requests", "x")
        feed.onLocation(lat, lon)
        assertEquals("N2", (feed.next() as Reading.OfNote).note.id)
        api.list = CellList(listOf(note("N3")), emptyList(), now, 300)
        pass(6)
        api.fail = StudioException("studio_paused", "x")
        feed.onLocation(lat, lon)
        assertNull(feed.next())
    }

    @Test
    fun `knows the hours and the day in Japan`() {
        assertEquals("2026-11-01", StudioFeed.dayOf(Instant.parse("2026-10-31T15:30:00Z")))
        assertTrue(StudioFeed.withinHours(Hours("10:00", "18:00"), Instant.parse("2026-10-31T01:00:00Z")))
        assertTrue(!StudioFeed.withinHours(Hours("10:00", "18:00"), Instant.parse("2026-10-31T09:00:00Z")))
        assertTrue(StudioFeed.withinHours(null, Instant.parse("2026-10-31T20:00:00Z")))
    }
}
