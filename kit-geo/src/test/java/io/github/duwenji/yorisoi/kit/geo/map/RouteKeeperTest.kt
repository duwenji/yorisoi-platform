package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.kit.geo.Facility
import io.github.duwenji.yorisoi.kit.geo.FacilityKind
import io.github.duwenji.yorisoi.kit.geo.LatLon
import io.github.duwenji.yorisoi.kit.geo.RouteGeometry
import io.github.duwenji.yorisoi.kit.geo.WalkRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RouteKeeperTest {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val here = MutableStateFlow<LatLon?>(LatLon(35.0, 139.0))
    private val asked = mutableListOf<Pair<LatLon, LatLon>>()
    private var fail = false
    private val keeper = RouteKeeper(scope, here, lookup = { from, to ->
        synchronized(asked) { asked += from to to }
        if (fail) throw IOException("down")
        WalkRoute(listOf(from, RouteGeometry.destination(from, 0.0, 10.0), to), RouteGeometry.distanceM(from, to), onPaths = true)
    })

    // About 200 m north of the start.
    private val toilet = RouteTarget.of(Facility("node/1", FacilityKind.TOILETS, 35.0018, 139.0, null))
    private val spot = RouteTarget("node/2", "八幡宮", 35.0, 139.002)

    @After
    fun stop() = job.cancel()

    private fun waitFor(what: (RouteToSpot?) -> Boolean): RouteToSpot? =
        runBlocking { withTimeout(2_000) { keeper.route.first(what) } }

    @Test
    fun `a facility's name is its kind when it has none`() {
        assertEquals("トイレ", toilet.name)
        assertEquals(FacilityKind.TOILETS, toilet.facility)
        assertEquals("トイレ（浅草寺）", RouteTarget.of(Facility("node/3", FacilityKind.TOILETS, 0.0, 0.0, "浅草寺")).name)
        assertNull(RouteTarget.of(io.github.duwenji.yorisoi.kit.geo.Poi("node/2", "八幡宮", 35.0, 139.002, "神社", emptyMap())).facility)
    }

    @Test
    fun `the way is looked up when the target is picked`() {
        keeper.show(toilet)
        val route = waitFor { it?.loading == false }!!
        assertEquals(toilet, route.target)
        assertTrue(route.route.onPaths)
        assertEquals(1, asked.size)
    }

    @Test
    fun `a spot the app points to doesn't replace a facility picked`() {
        keeper.show(toilet)
        keeper.suggest(spot)
        assertEquals(toilet, keeper.target.value)
        assertEquals(toilet, waitFor { it?.loading == false }!!.target)
    }

    @Test
    fun `a spot the app points to replaces another spot, and a pick replaces a facility`() {
        keeper.suggest(spot)
        val other = spot.copy(id = "node/4")
        keeper.suggest(other)
        assertEquals(other, keeper.target.value)
        keeper.show(toilet)
        keeper.show(spot)
        assertEquals(spot, keeper.target.value)
    }

    @Test
    fun `reaching the facility takes its way away`() {
        keeper.show(toilet)
        waitFor { it?.loading == false }
        here.value = LatLon(35.0017, 139.0) // about 11 m short
        waitFor { it == null }
        assertNull(keeper.target.value)
    }

    @Test
    fun `a facility picked close by stays until cleared`() {
        here.value = LatLon(35.0017, 139.0)
        keeper.show(toilet)
        waitFor { it?.loading == false }
        here.value = LatLon(35.00171, 139.0)
        runBlocking { delay(200) }
        assertEquals(toilet, keeper.target.value)
        keeper.clear()
        waitFor { it == null }
    }

    @Test
    fun `walking along the way doesn't ask again, leaving it does`() {
        keeper.show(spot)
        waitFor { it?.loading == false }
        here.value = LatLon(35.00005, 139.0) // about 6 m along the first leg
        runBlocking { delay(200) }
        assertEquals(1, asked.size)
        here.value = LatLon(34.999, 139.0) // about 110 m off
        runBlocking { withTimeout(2_000) { while (synchronized(asked) { asked.size } < 2) delay(10) } }
    }

    @Test
    fun `a way that can't be found is a straight line`() {
        fail = true
        keeper.show(toilet)
        val route = waitFor { it?.loading == false }!!
        assertFalse(route.route.onPaths)
        assertEquals(2, route.route.points.size)
    }
}
