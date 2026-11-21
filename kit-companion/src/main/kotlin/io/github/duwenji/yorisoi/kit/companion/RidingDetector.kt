package io.github.duwenji.yorisoi.kit.companion

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A location the app got, as far as telling a ride goes. */
data class RidingFix(val timeMs: Long, val lat: Double, val lon: Double, val accuracyM: Float)

/** That the user got on a vehicle (bus, train, car) or off it, at [timeMs] and [speedKmh] over the window. */
data class RidingChange(val riding: Boolean, val timeMs: Long, val speedKmh: Double)

/**
 * Tells from the speed whether the user is riding a vehicle (platform ADR-RIDING R-1・R-2・R-10).
 * The speed is how far the user got in the last [windowMs] (from the newest fix at least that old),
 * so one jumpy fix doesn't count; fixes worse than [maxAccuracyM] are left out.
 *
 * On: the speed stays above [enterKmh] for [enterHoldMs]. Off: it stays below [exitKmh] for
 * [exitHoldMs], so stops at a bus stop, a station or a light don't end the ride. While no speed can be
 * told (no fix old enough, as in a tunnel), the state stays as it is (ADR-RIDING R-b).
 * The app gives the thresholds: SanpoGuide walks, CycleGuide rides a bicycle.
 */
class RidingDetector(
    private val enterKmh: Double,
    private val exitKmh: Double = enterKmh / 2,
    private val windowMs: Long = 60_000,
    private val enterHoldMs: Long = 30_000,
    private val exitHoldMs: Long = 120_000,
    private val maxAccuracyM: Float = 50f,
) {
    init {
        require(exitKmh < enterKmh) { "exitKmh ($exitKmh) must be below enterKmh ($enterKmh)" }
    }

    /** Whether the user is riding now. */
    var riding: Boolean = false
        private set

    private val fixes = ArrayDeque<RidingFix>()
    private var fastSince: Long? = null
    private var slowSince: Long? = null

    /** Takes [fix] and returns the change it makes, or null. */
    fun update(fix: RidingFix): RidingChange? {
        if (fix.accuracyM > maxAccuracyM) return null
        if (fixes.isNotEmpty() && fix.timeMs <= fixes.last().timeMs) return null
        val base = fixes.lastOrNull { it.timeMs <= fix.timeMs - windowMs }
        fixes.addLast(fix)
        // Keep the base for the next window and nothing older.
        while (fixes.size > 1 && fixes[1].timeMs <= fix.timeMs - windowMs) fixes.removeFirst()
        base ?: return null
        val speed = distanceM(base, fix) / (fix.timeMs - base.timeMs) * 3600.0
        return if (!riding) {
            if (speed > enterKmh) {
                val since = fastSince ?: fix.timeMs.also { fastSince = it }
                if (fix.timeMs - since >= enterHoldMs) change(true, fix, speed) else null
            } else {
                fastSince = null
                null
            }
        } else {
            if (speed < exitKmh) {
                val since = slowSince ?: fix.timeMs.also { slowSince = it }
                if (fix.timeMs - since >= exitHoldMs) change(false, fix, speed) else null
            } else {
                slowSince = null
                null
            }
        }
    }

    /** Forgets everything, as at the start of a walk or ride. */
    fun reset() {
        riding = false
        fixes.clear()
        fastSince = null
        slowSince = null
    }

    private fun change(riding: Boolean, fix: RidingFix, speed: Double): RidingChange {
        this.riding = riding
        fastSince = null
        slowSince = null
        return RidingChange(riding, fix.timeMs, speed)
    }

    private fun distanceM(a: RidingFix, b: RidingFix): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
