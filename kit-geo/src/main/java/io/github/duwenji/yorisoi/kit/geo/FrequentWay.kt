package io.github.duwenji.yorisoi.kit.geo

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/** How a frequent way came to be: found in the history, or made by the user. */
enum class WayOrigin { FOUND, USER }

/**
 * A way walked or ridden again and again (REQ-FREQUENT-WAYS F-1): found in the history, or made by
 * the user from a trip. A found way the user hasn't [saved] goes when it isn't used for a while.
 */
data class FrequentWay(
    val id: String,
    val name: String,
    val points: List<LatLon>,
    val lengthM: Double,
    val origin: WayOrigin,
    val saved: Boolean,
    /** How many times it was gone along, the trips that made it included. */
    val passes: Int,
    val lastPassedAt: Long,
    val createdAt: Long,
)

/** One walk or ride as the finder reads it: when it ended and the way gone. */
data class Trip(val endedAt: Long, val points: List<LatLon>)

/**
 * Finds the frequent ways in trips (REQ-FREQUENT-WAYS F-3, F-4). Trips are cut by [trimM] at both
 * ends (D-5: no home nor workplace) and spread to a point every [stepM]; two points within [nearM]
 * are on the same way. A way is a stretch of the newest trip at least [minLengthM] long, every
 * point of it near [times] trips or more, the newest included, missing at most [gapPoints] points
 * in a row (GPS jumps). A trip goes along a way when [passShare] of the way is near it.
 */
class FrequentWayFinder(
    private val minLengthM: Double,
    private val times: Int = 3,
    private val nearM: Double = 30.0,
    private val trimM: Double = 200.0,
    private val stepM: Double = 25.0,
    private val passShare: Double = 0.7,
    private val gapPoints: Int = 3,
) {
    /** The trip's points, cut at both ends and a point every [stepM]. */
    fun spread(trip: Trip): List<LatLon> {
        val points = resample(trip.points, stepM)
        val cut = ceil(trimM / stepM).toInt()
        return if (points.size <= 2 * cut) emptyList() else points.subList(cut, points.size - cut)
    }

    /** Whether [trip] went along [way]: [passShare] of the way within [nearM] of it. */
    fun passes(way: FrequentWay, trip: Trip): Boolean {
        val along = Grid(nearM).apply { addAll(0, spread(trip)) }
        val points = resample(way.points, stepM)
        if (points.isEmpty()) return false
        return points.count { along.near(it).isNotEmpty() } >= passShare * points.size
    }

    /**
     * The new ways in [newest], from the [earlier] trips: each a stretch of the newest trip. Stretches
     * mostly on a [known] way, or on a way the user [refused] (deleted), are left out.
     */
    fun find(newest: Trip, earlier: List<Trip>, known: List<List<LatLon>>, refused: List<List<LatLon>>): List<List<LatLon>> {
        val line = spread(newest)
        if (line.isEmpty()) return emptyList()
        val grid = Grid(nearM)
        earlier.forEachIndexed { i, trip -> grid.addAll(i, spread(trip)) }
        val often = line.map { grid.near(it).size + 1 >= times }

        val stretches = mutableListOf<List<LatLon>>()
        var start = -1
        var missed = 0
        for (i in 0..line.size) {
            val ok = i < line.size && often[i]
            if (ok) {
                if (start < 0) start = i
                missed = 0
            } else if (start >= 0) {
                missed++
                if (missed > gapPoints || i == line.size) {
                    val end = i - missed + 1
                    if ((end - start - 1) * stepM >= minLengthM) stretches += line.subList(start, end)
                    start = -1
                    missed = 0
                }
            }
        }
        val taken = Grid(nearM).apply { (known + refused).forEachIndexed { i, way -> addAll(i, resample(way, stepM)) } }
        return stretches.filter { stretch -> stretch.count { taken.near(it).isNotEmpty() } < 0.5 * stretch.size }
    }

    /** Points in cells of [cellM], to find those near a point without going through them all. */
    private class Grid(private val cellM: Double) {
        private val cells = HashMap<Long, MutableList<Pair<Int, LatLon>>>()

        fun addAll(id: Int, points: List<LatLon>) = points.forEach { p -> cells.getOrPut(key(p)) { mutableListOf() } += id to p }

        /** The ids of the lines with a point within [cellM] of [p]. */
        fun near(p: LatLon): Set<Int> {
            val (row, col) = cell(p)
            // Cells are [cellM] high and narrower away from the equator: look as many cells aside as it takes.
            val aside = ceil(1.0 / cos(Math.toRadians(p.lat))).toInt()
            val ids = HashSet<Int>()
            for (r in row - 1..row + 1) for (c in col - aside..col + aside) {
                cells[pack(r, c)]?.forEach { (id, q) -> if (id !in ids && RouteGeometry.distanceM(p, q) <= cellM) ids += id }
            }
            return ids
        }

        private fun cell(p: LatLon): Pair<Long, Long> {
            val d = cellM / METERS_PER_DEGREE
            return floor(p.lat / d).toLong() to floor(p.lon / d).toLong()
        }

        private fun key(p: LatLon) = cell(p).let { (r, c) -> pack(r, c) }

        private fun pack(row: Long, col: Long) = (row shl 32) xor (col and 0xffffffffL)
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_195.0

        /** A point every [stepM] along the line through [points]. */
        fun resample(points: List<LatLon>, stepM: Double): List<LatLon> {
            if (points.size < 2) return points.toList()
            val out = mutableListOf(points[0])
            var carried = 0.0
            for ((a, b) in points.zipWithNext()) {
                val d = RouteGeometry.distanceM(a, b)
                if (d <= 0.0) continue
                var t = stepM - carried
                while (t <= d) {
                    out += LatLon(a.lat + (b.lat - a.lat) * t / d, a.lon + (b.lon - a.lon) * t / d)
                    t += stepM
                }
                carried = d - (t - stepM)
            }
            return out
        }

        /** How far along [points] each point is. */
        fun distances(points: List<LatLon>): List<Double> {
            var total = 0.0
            return points.mapIndexed { i, p ->
                if (i > 0) total += RouteGeometry.distanceM(points[i - 1], p)
                total
            }
        }

        /** The part of [points] from [fromM] to [toM] along it (REQ-FREQUENT-WAYS F-16). */
        fun section(points: List<LatLon>, fromM: Double, toM: Double): List<LatLon> {
            val along = distances(points)
            return points.filterIndexed { i, _ -> along[i] in fromM..toM }
        }

        /** The length of the line through [points]. */
        fun lengthM(points: List<LatLon>): Double = points.zipWithNext().sumOf { (a, b) -> RouteGeometry.distanceM(a, b) }

        /**
         * A name for a way found (REQ-FREQUENT-WAYS F-5): after the spot nearest its middle among
         * [spots] within [withinM] of it ("曳舟川親水公園のあたりの道"); null when none is near.
         */
        fun nameFrom(points: List<LatLon>, spots: List<Poi>, withinM: Double = 100.0): String? {
            if (points.isEmpty()) return null
            val middle = resample(points, 10.0).let { it[it.size / 2] }
            return spots
                .filter { it.name.isNotBlank() && RouteGeometry.distanceToLineM(LatLon(it.lat, it.lon), points) <= withinM }
                .minByOrNull { RouteGeometry.distanceM(middle, LatLon(it.lat, it.lon)) }
                ?.let { "${it.name}のあたりの道" }
        }
    }
}
