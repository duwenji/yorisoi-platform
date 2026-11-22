package io.github.duwenji.yorisoi.kit.geo

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** A municipality: its 全国地方公共団体コード and its name with the prefecture (「福井県福井市」). */
data class Municipality(val code: String, val name: String) {
    /** The prefecture's number (「18」). */
    val prefecture: String get() = code.take(2)
}

/**
 * Which municipality a point is in, on the device (platform ADR-AGENT A-14): the nearest of the representative
 * points of the 大字・町丁目 (位置参照情報) the operator delivers per prefecture
 * (docs/contracts/api/municipality-points.md). No outside geocoding service is asked, and the point never
 * leaves the device; only the municipality's name goes into the preparer's input. A point with no
 * representative point within [maxKm] (at sea) has none.
 */
class Municipalities private constructor(private val cells: Map<Long, List<Point>>, private val maxKm: Double) {
    private class Point(val lat: Double, val lon: Double, val m: Municipality)

    /** The municipality of ([lat], [lon]), or null. */
    fun at(lat: Double, lon: Double): Municipality? {
        var best: Point? = null
        var bestKm = Double.MAX_VALUE
        val c = cell(lat, lon)
        for (dy in -RING..RING) for (dx in -RING..RING) {
            for (p in cells[key(c.first + dy, c.second + dx)].orEmpty()) {
                val km = km(lat, lon, p.lat, p.lon)
                if (km < bestKm) { bestKm = km; best = p }
            }
        }
        return best?.takeIf { bestKm <= maxKm }?.m
    }

    companion object {
        /** About 2 km a cell; 5 km is within two cells around. */
        private const val CELL_DEG = 0.02
        private const val RING = 3

        private fun cell(lat: Double, lon: Double) = floor(lat / CELL_DEG).toLong() to floor(lon / CELL_DEG).toLong()
        private fun key(y: Long, x: Long) = y * 100_000 + x

        private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dy = (lat2 - lat1) * 111.0
            val dx = (lon2 - lon1) * 111.0 * cos(Math.toRadians((lat1 + lat2) / 2))
            return sqrt(dx * dx + dy * dy)
        }

        /**
         * Reads the delivered points: one line `code,name,lat,lon` each, with a header line; lines that don't
         * read are left out. Several prefectures' files may be joined.
         */
        fun parse(csv: String, maxKm: Double = 5.0): Municipalities {
            val cells = HashMap<Long, MutableList<Point>>()
            val names = HashMap<String, Municipality>()
            for (line in csv.lineSequence().drop(1)) {
                val f = line.split(',')
                if (f.size < 4) continue
                val code = f[0].trim().takeIf { Regex("\\d{6}").matches(it) } ?: continue
                val lat = f[2].trim().toDoubleOrNull() ?: continue
                val lon = f[3].trim().toDoubleOrNull() ?: continue
                val m = names.getOrPut(code) { Municipality(code, f[1].trim()) }
                val c = cell(lat, lon)
                cells.getOrPut(key(c.first, c.second)) { ArrayList() } += Point(lat, lon, m)
            }
            return Municipalities(cells, maxKm)
        }
    }
}
