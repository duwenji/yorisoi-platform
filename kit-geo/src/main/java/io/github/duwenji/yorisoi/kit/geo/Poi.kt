package io.github.duwenji.yorisoi.kit.geo

import android.location.Location

data class Poi(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val tags: Map<String, String>,
    /** Outline for spots mapped as areas or lines; null for points. [lat]/[lon] is then its center. */
    val shape: Shape? = null,
    /** Its Wikidata item's values, from the operator's tiles (platform REQ-WIKIDATA); null otherwise. */
    val wikidata: SpotWikidata? = null,
    /** Whether it came from the operator's tiles (not Overpass): then Wikidata isn't asked about it (REQ-WIKIDATA F-19). */
    val fromTile: Boolean = false,
) {
    /**
     * Rough "worth talking about" score (platform REQ-WIKIDATA F-20): a Japanese Wikipedia article
     * 2 (a link to Wikipedia or Wikidata in other languages only 1), a national designation as
     * heritage 1, a major kind 1.
     */
    val notability: Int
        get() = documented() + (if (designated()) 1 else 0) + (if (category in MAJOR_CATEGORIES) 1 else 0)

    private fun documented(): Int = when {
        tags["wikipedia"]?.let { it.startsWith("ja:") || ':' !in it } == true || wikidata?.jawiki != null -> 2
        "wikipedia" in tags || "wikidata" in tags -> 1
        else -> 0
    }

    private fun designated(): Boolean =
        tags["heritage"]?.trim() in setOf("1", "2") || wikidata?.heritage.orEmpty().any { it in NATIONAL_HERITAGE }

    /** Distance in meters to the spot's edge (0 when inside an area), or to its point. */
    fun distanceFrom(location: Location): Float {
        shape?.let { return it.distanceM(location.latitude, location.longitude).toFloat() }
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, lat, lon, result)
        return result[0]
    }

    /** Distance in meters to the spot's edge or point, as Overpass's `around` finds it. */
    internal fun distanceM(from: LatLon): Double =
        shape?.distanceM(from.lat, from.lon) ?: RouteGeometry.distanceM(from, LatLon(lat, lon))

    /** Whether a distance from [distanceFrom] means the user is inside this spot's area. */
    fun isInside(distanceM: Number): Boolean = shape?.isArea == true && distanceM.toDouble() == 0.0

    private companion object {
        val MAJOR_CATEGORIES = setOf("神社", "寺院", "教会", "博物館", "名所", "展望スポット", "庭園")
        val NATIONAL_HERITAGE = setOf(
            "国宝", "重要文化財", "史跡", "特別史跡", "名勝", "特別名勝", "天然記念物", "特別天然記念物", "世界遺産",
        )
    }
}
