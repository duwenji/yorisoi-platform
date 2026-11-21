package io.github.duwenji.yorisoi.kit.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telling a ride from the speed (platform ADR-RIDING). */
class RidingDetectorTest {
    /** Metres north of a point, as a latitude. */
    private fun lat(northM: Double) = 35.0 + northM / 111_195.0

    /** Fixes every [everyS] seconds at [kmh], from [fromS] for [forS] seconds, starting [startM] north. */
    private fun run(fromS: Int, forS: Int, kmh: Double, startM: Double = 0.0, everyS: Int = 10, accuracyM: Float = 5f) =
        (0..forS step everyS).map { s -> RidingFix((fromS + s) * 1000L, lat(startM + kmh / 3.6 * s), 139.0, accuracyM) }

    private fun changes(detector: RidingDetector, fixes: List<RidingFix>) = fixes.mapNotNull(detector::update)

    /**
     * The walk of 2026-11-21 without its positions (seconds, metres from the previous fix, accuracy),
     * laid out along a line. The walker rode a bus from about 20 to 68 minutes.
     */
    private fun walkOf20261121(): List<RidingFix> {
        var northM = 0.0
        return javaClass.getResource("/riding-walk-2026-11-21.csv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (s, step, accuracy) = line.split(",")
                northM += step.toDouble()
                RidingFix(s.toLong() * 1000, lat(northM), 139.0, accuracy.toFloat())
            }
    }

    @Test
    fun `the bus of 2026-11-21 is told, and the walk around it is not`() {
        val found = changes(RidingDetector(enterKmh = 12.0), walkOf20261121()).map { it.riding to it.timeMs / 60_000.0 }
        // On at about 22 min, off for the four-minute stop at 51-55 min (a change of buses), off at about 71 min.
        assertEquals(listOf(true, false, true, false), found.map { it.first })
        val minutes = found.map { it.second }
        assertTrue("on at ${minutes[0]}", minutes[0] in 20.0..23.0)
        assertTrue("off at ${minutes[1]}", minutes[1] in 54.0..57.0)
        assertTrue("on again at ${minutes[2]}", minutes[2] in 56.0..60.0)
        assertTrue("off at ${minutes[3]}", minutes[3] in 68.0..73.0)
    }

    @Test
    fun `walking, jogging and stops are not a ride`() {
        val d = RidingDetector(enterKmh = 12.0)
        val fixes = run(0, 600, 5.0) + run(610, 300, 0.0, startM = 5.0 / 3.6 * 600) + run(920, 600, 9.0, startM = 5.0 / 3.6 * 600)
        assertTrue(changes(d, fixes).isEmpty())
        assertFalse(d.riding)
    }

    @Test
    fun `a ride starts only after the speed has stayed up for 30 seconds`() {
        val d = RidingDetector(enterKmh = 12.0)
        changes(d, run(0, 120, 5.0))
        val onBus = run(130, 120, 30.0, startM = 5.0 / 3.6 * 120)
        val change = changes(d, onBus).single()
        assertTrue(change.riding)
        // The window has to fill with the bus first, then 30 seconds of it.
        assertTrue("on at ${change.timeMs}", change.timeMs in 170_000L..230_000L)
    }

    @Test
    fun `stops at a bus stop or a light don't end the ride`() {
        val d = RidingDetector(enterKmh = 12.0)
        var m = 0.0
        val fixes = buildList {
            var s = 0
            repeat(5) {
                addAll(run(s, 180, 30.0, startM = m)); m += 30.0 / 3.6 * 180; s += 190
                addAll(run(s, 90, 0.0, startM = m)); s += 100
            }
        }
        changes(d, fixes)
        assertTrue(d.riding)
    }

    @Test
    fun `a ride ends after two minutes below half the speed`() {
        val d = RidingDetector(enterKmh = 12.0)
        changes(d, run(0, 300, 30.0))
        val walk = run(310, 300, 4.0, startM = 30.0 / 3.6 * 300)
        val change = changes(d, walk).single()
        assertFalse(change.riding)
        assertTrue("off at ${change.timeMs}", change.timeMs in 400_000L..500_000L)
    }

    @Test
    fun `a jumpy fix or poor fixes don't start a ride`() {
        val d = RidingDetector(enterKmh = 12.0)
        val fixes = run(0, 300, 4.0) +
            RidingFix(310_000, lat(400.0 + 600.0), 139.0, 30f) +
            run(320, 300, 4.0, startM = 400.0) +
            run(630, 300, 40.0, startM = 800.0, accuracyM = 200f)
        assertTrue(changes(d, fixes).isEmpty())
    }

    @Test
    fun `while no speed can be told, the state stays`() {
        val d = RidingDetector(enterKmh = 12.0)
        changes(d, run(0, 300, 30.0))
        assertTrue(d.riding)
        // A tunnel: one fix ten minutes later, at walking speed over those ten minutes would be slow,
        // but a single slow window doesn't end the ride.
        assertNull(d.update(RidingFix(900_000, lat(30.0 / 3.6 * 300 + 200), 139.0, 5f)))
        assertTrue(d.riding)
    }

    @Test
    fun `CycleGuide's threshold keeps a fast downhill a bicycle ride`() {
        val d = RidingDetector(enterKmh = 40.0)
        val fixes = run(0, 300, 18.0) + run(310, 90, 35.0, startM = 18.0 / 3.6 * 300)
        assertTrue(changes(d, fixes).isEmpty())
        val train = run(410, 240, 80.0, startM = 18.0 / 3.6 * 300 + 35.0 / 3.6 * 90)
        assertTrue(changes(d, train).single().riding)
    }

    @Test
    fun `reset forgets a ride`() {
        val d = RidingDetector(enterKmh = 12.0)
        changes(d, run(0, 300, 30.0))
        d.reset()
        assertFalse(d.riding)
    }
}
