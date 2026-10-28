package io.github.duwenji.yorisoi.kit.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Finding the frequent ways and keeping them (REQ-FREQUENT-WAYS F-3, F-4, D-5, D-7). */
class FrequentWayTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val start = LatLon(35.70, 139.80)
    private val day = 24 * 60 * 60 * 1000L
    private var clock = 1_800_000_000_000L

    /** A trip east from [fromM] to [toM] metres past [start], [sideM] north of the line (another GPS fix). */
    private fun trip(fromM: Double, toM: Double, sideM: Double = 0.0, endedAt: Long = clock): Trip {
        val origin = RouteGeometry.destination(start, 0.0, sideM)
        return Trip(endedAt, (0..((toM - fromM) / 10).toInt()).map { RouteGeometry.destination(origin, 90.0, fromM + it * 10.0) })
    }

    private val finder = FrequentWayFinder(minLengthM = 500.0)

    @Test
    fun `a way gone along three times is found, without its ends`() {
        val ways = finder.find(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0, sideM = 10.0), trip(0.0, 2000.0, sideM = -15.0)), emptyList(), emptyList())
        assertEquals(1, ways.size)
        val way = ways[0]
        // 200m cut at both ends (D-5).
        assertEquals(1600.0, FrequentWayFinder.lengthM(way), 30.0)
        assertTrue(RouteGeometry.distanceM(start, way.first()) >= 190.0)
    }

    @Test
    fun `twice is not enough, nor a stretch too short`() {
        assertTrue(finder.find(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0)), emptyList(), emptyList()).isEmpty())
        // Only 100m in common past the cut ends: shorter than 500m.
        val ways = finder.find(trip(0.0, 2000.0), listOf(trip(1500.0, 3000.0), trip(1500.0, 3000.0)), emptyList(), emptyList())
        assertTrue(ways.isEmpty())
    }

    @Test
    fun `ways farther apart than 30m are not the same`() {
        assertTrue(finder.find(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0, sideM = 60.0), trip(0.0, 2000.0, sideM = 60.0)), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `a way known or refused is not found again`() {
        val earlier = listOf(trip(0.0, 2000.0), trip(0.0, 2000.0))
        val found = finder.find(trip(0.0, 2000.0), earlier, emptyList(), emptyList()).single()
        assertTrue(finder.find(trip(0.0, 2000.0), earlier, listOf(found), emptyList()).isEmpty())
        assertTrue(finder.find(trip(0.0, 2000.0), earlier, emptyList(), listOf(found)).isEmpty())
    }

    @Test
    fun `a trip goes along a way when it covers most of it`() {
        val found = finder.find(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0), trip(0.0, 2000.0)), emptyList(), emptyList()).single()
        val way = FrequentWay("w", "", found, 0.0, WayOrigin.FOUND, false, 3, 0, 0)
        assertTrue(finder.passes(way, trip(0.0, 2000.0, sideM = 20.0)))
        assertFalse(finder.passes(way, trip(0.0, 1000.0)))
    }

    @Test
    fun `a part of a trip is the points between two distances along it`() {
        val line = (0..20).map { RouteGeometry.destination(start, 90.0, it * 100.0) }
        assertEquals(2000.0, FrequentWayFinder.distances(line).last(), 1.0)
        val part = FrequentWayFinder.section(line, 150.0, 1850.0)
        assertEquals(17, part.size)
        assertEquals(line[2], part.first())
    }

    @Test
    fun `a way is named after the spot nearest its middle`() {
        val way = trip(0.0, 1000.0).points
        val middle = RouteGeometry.destination(start, 90.0, 500.0)
        val spots = listOf(
            Poi("1", "東和親水公園", start.lat, start.lon, "公園", emptyMap()),
            Poi("2", "曳舟川親水公園", middle.lat + 0.0003, middle.lon, "公園", emptyMap()),
            Poi("3", "遠くの寺", middle.lat + 0.01, middle.lon, "寺院", emptyMap()),
        )
        assertEquals("曳舟川親水公園のあたりの道", FrequentWayFinder.nameFrom(way, spots))
        assertNull(FrequentWayFinder.nameFrom(way, spots.drop(2)))
    }

    private fun store() = FrequentWayStore(folder.root.resolve("ways.json"), finder, now = { clock })

    @Test
    fun `the store finds, counts and keeps the ways`() {
        val store = store()
        val earlier = mutableListOf<Trip>()
        repeat(2) {
            assertTrue(store.afterTrip(trip(0.0, 2000.0), earlier.toList(), find = true) { null }.isEmpty())
            earlier += trip(0.0, 2000.0)
            clock += day
        }
        val third = store.afterTrip(trip(0.0, 2000.0), earlier, find = true) { "川沿いの道" }.single()
        assertTrue(third.new)
        assertEquals(3, third.passes)
        assertEquals("川沿いの道", third.way.name)

        clock += day
        val fourth = store.afterTrip(trip(0.0, 2000.0), earlier, find = true) { null }.single()
        assertFalse(fourth.new)
        assertEquals(4, fourth.passes)
        assertEquals(clock, store.ways.value.single().lastPassedAt)

        // Read again from the file.
        assertEquals(store.ways.value, store().ways.value)
    }

    @Test
    fun `finding can be turned off`() {
        val store = store()
        assertTrue(store.afterTrip(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0), trip(0.0, 2000.0)), find = false) { null }.isEmpty())
        assertTrue(store.ways.value.isEmpty())
    }

    @Test
    fun `a deleted way is not found again, a saved one is not forgotten`() {
        val store = store()
        val earlier = listOf(trip(0.0, 2000.0), trip(0.0, 2000.0))
        val way = store.afterTrip(trip(0.0, 2000.0), earlier, find = true) { null }.single().way
        store.delete(way.id)
        assertTrue(store.afterTrip(trip(0.0, 2000.0), earlier, find = true) { null }.isEmpty())
        assertTrue(store().ways.value.isEmpty())

        // Not refused any more once the history is cleared.
        store.clearFound()
        val again = store.afterTrip(trip(0.0, 2000.0), earlier, find = true) { null }.single().way
        store.save(again.id)
        val other = store.add("通勤の道", trip(3000.0, 4000.0).points, passedAt = clock)
        clock += FrequentWayStore.UNUSED_MS + day
        store.afterTrip(trip(5000.0, 5100.0), emptyList(), find = true) { null }
        assertEquals(setOf(again.id, other.id), store.ways.value.map { it.id }.toSet())
        store.clearFound()
        assertEquals(2, store.ways.value.size)
    }

    @Test
    fun `a found way not saved goes after 60 days unused`() {
        val store = store()
        store.afterTrip(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0), trip(0.0, 2000.0)), find = true) { null }
        clock += FrequentWayStore.UNUSED_MS + day
        store.afterTrip(trip(5000.0, 5100.0), emptyList(), find = true) { null }
        assertTrue(store.ways.value.isEmpty())
    }

    @Test
    fun `the history cleared forgets the found ways`() {
        val store = store()
        store.afterTrip(trip(0.0, 2000.0), listOf(trip(0.0, 2000.0), trip(0.0, 2000.0)), find = true) { null }
        store.clearFound()
        assertTrue(store.ways.value.isEmpty())
    }
}
