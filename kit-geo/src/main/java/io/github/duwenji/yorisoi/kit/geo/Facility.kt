package io.github.duwenji.yorisoi.kit.geo

import android.location.Location

/** Not the private ones. */
private const val OPEN = """["access"!~"^(private|no)$"]"""

/**
 * Amenities shown on the map and mentioned when needed; not spots to talk about. An app searches
 * the kinds it needs ([WALK] by default); [filters] are the Overpass filters each kind is searched
 * with, and [near] kinds (everywhere along a street) only close by.
 */
enum class FacilityKind(
    val label: String,
    internal val filters: List<String>,
    internal val near: Boolean = false,
    private val matches: (Map<String, String>) -> Boolean,
) {
    TOILETS("トイレ", listOf("""["amenity"="toilets"]$OPEN"""), matches = { it["amenity"] == "toilets" }),
    DRINKING_WATER("水飲み場", listOf("""["amenity"="drinking_water"]$OPEN"""), matches = { it["amenity"] == "drinking_water" }),
    VENDING_MACHINE(
        "自動販売機",
        // Machines that sell nothing a walker needs (tickets, parking, cigarettes, ...).
        listOf("""["amenity"="vending_machine"]["vending"!~"ticket|parking|cigarette|excrement|condom|newspaper|fuel|photo"]"""),
        near = true,
        matches = { it["amenity"] == "vending_machine" },
    ),
    SHELTER("休憩所", listOf("""["amenity"="shelter"]$OPEN"""), matches = { it["amenity"] == "shelter" }),
    BENCH("ベンチ", listOf("""["amenity"="bench"]"""), near = true, matches = { it["amenity"] == "bench" }),
    BICYCLE_PARKING("駐輪場", listOf("""["amenity"="bicycle_parking"]$OPEN"""), matches = { it["amenity"] == "bicycle_parking" }),
    CONVENIENCE_STORE("コンビニ", listOf("""["shop"="convenience"]"""), matches = { it["shop"] == "convenience" }),
    BICYCLE_SHOP("自転車店", listOf("""["shop"="bicycle"]"""), matches = { it["shop"] == "bicycle" }),
    BICYCLE_PUMP(
        "空気入れ",
        listOf("""["amenity"="bicycle_repair_station"]["service:bicycle:pump"="yes"]""", """["amenity"="compressed_air"]$OPEN"""),
        matches = { it["amenity"] == "compressed_air" || (it["amenity"] == "bicycle_repair_station" && it["service:bicycle:pump"] == "yes") },
    );

    companion object {
        /** What a walker looks for; the kinds searched unless an app asks for others. */
        val WALK: Set<FacilityKind> = setOf(TOILETS, DRINKING_WATER, VENDING_MACHINE, SHELTER, BENCH)

        /** The kind an OpenStreetMap element with these [tags] is, if any. */
        fun of(tags: Map<String, String>): FacilityKind? = entries.firstOrNull { it.matches(tags) }
    }
}

data class Facility(val id: String, val kind: FacilityKind, val lat: Double, val lon: Double, val name: String?) {
    fun distanceFrom(location: Location): Float = distanceFrom(location.latitude, location.longitude)

    fun distanceFrom(fromLat: Double, fromLon: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, lat, lon, result)
        return result[0]
    }
}

/**
 * Where [bearing] (to a place) lies relative to the way the user goes, [heading], both in degrees
 * clockwise from north: "前方", "右手", "左手" or "後ろ".
 */
fun relativeDirection(heading: Float, bearing: Float): String {
    val diff = ((bearing - heading) % 360 + 540) % 360 - 180 // -180..180, positive = right
    return when {
        diff in -45f..45f -> "前方"
        diff in 45f..135f -> "右手"
        diff in -135f..-45f -> "左手"
        else -> "後ろ"
    }
}
