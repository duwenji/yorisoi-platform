package io.github.duwenji.yorisoi.kit.geo

import kotlin.math.PI
import kotlin.math.cos

/** Geohashes, which name the operator's spot tiles (API-SPOT-TILES). Pure logic. */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val M_PER_DEG = 111_320.0

    /** The geohash of [precision] characters the point lies in. */
    fun encode(lat: Double, lon: Double, precision: Int): String {
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        val hash = StringBuilder(precision)
        var bits = 0
        var ch = 0
        var even = true
        while (hash.length < precision) {
            if (even) {
                val mid = (lonLo + lonHi) / 2
                if (lon >= mid) { ch = ch * 2 + 1; lonLo = mid } else { ch *= 2; lonHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { ch = ch * 2 + 1; latLo = mid } else { ch *= 2; latHi = mid }
            }
            even = !even
            if (++bits == 5) {
                hash.append(BASE32[ch])
                bits = 0
                ch = 0
            }
        }
        return hash.toString()
    }

    /**
     * The geohashes of [precision] characters that a circle of [radiusM] around the point may reach:
     * those of the corners of the square around it. Enough while a geohash is wider than the circle
     * (5 characters, about 4km, and a search's 600m).
     */
    fun covering(lat: Double, lon: Double, radiusM: Double, precision: Int): List<String> {
        val dLat = radiusM / M_PER_DEG
        val dLon = radiusM / (M_PER_DEG * cos(lat * PI / 180))
        return listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
            .map { (sLat, sLon) -> encode(lat + sLat * dLat, lon + sLon * dLon, precision) }
            .distinct()
    }
}
