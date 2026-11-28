package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.text.Words
import android.location.Location

/** Not the private ones. */
private const val OPEN = """["access"!~"^(private|no)$"]"""

/**
 * Amenities shown on the map and mentioned when needed; not spots to talk about. An app searches
 * the kinds it needs ([WALK] by default); [filters] are the Overpass filters each kind is searched
 * with, and [near] kinds (everywhere along a street) only close by.
 */
enum class FacilityKind(
    private val words: Words,
    internal val filters: List<String>,
    internal val near: Boolean = false,
    private val matches: (Map<String, String>) -> Boolean,
) {
    TOILETS(Words("トイレ", "toilets", "厕所", "화장실"), listOf("""["amenity"="toilets"]$OPEN"""), matches = { it["amenity"] == "toilets" }),
    DRINKING_WATER(Words("水飲み場", "drinking water", "饮水处", "음수대"), listOf("""["amenity"="drinking_water"]$OPEN"""), matches = { it["amenity"] == "drinking_water" }),
    VENDING_MACHINE(
        Words("自動販売機", "vending machine", "自动售货机", "자동판매기"),
        // Machines that sell nothing a walker needs (tickets, parking, cigarettes, ...).
        listOf("""["amenity"="vending_machine"]["vending"!~"ticket|parking|cigarette|excrement|condom|newspaper|fuel|photo"]"""),
        near = true,
        matches = { it["amenity"] == "vending_machine" },
    ),
    SHELTER(Words("休憩所", "rest area", "休息处", "쉼터"), listOf("""["amenity"="shelter"]$OPEN"""), matches = { it["amenity"] == "shelter" }),
    BENCH(Words("ベンチ", "bench", "长椅", "벤치"), listOf("""["amenity"="bench"]"""), near = true, matches = { it["amenity"] == "bench" }),
    BICYCLE_PARKING(Words("駐輪場", "bicycle parking", "自行车停车场", "자전거 주차장"), listOf("""["amenity"="bicycle_parking"]$OPEN"""), matches = { it["amenity"] == "bicycle_parking" }),
    CONVENIENCE_STORE(Words("コンビニ", "convenience store", "便利店", "편의점"), listOf("""["shop"="convenience"]"""), matches = { it["shop"] == "convenience" }),
    BICYCLE_SHOP(Words("自転車店", "bicycle shop", "自行车店", "자전거 가게"), listOf("""["shop"="bicycle"]"""), matches = { it["shop"] == "bicycle" }),
    BICYCLE_PUMP(
        Words("空気入れ", "air pump", "打气筒", "공기 주입기"),
        listOf("""["amenity"="bicycle_repair_station"]["service:bicycle:pump"="yes"]""", """["amenity"="compressed_air"]$OPEN"""),
        matches = { it["amenity"] == "compressed_air" || (it["amenity"] == "bicycle_repair_station" && it["service:bicycle:pump"] == "yes") },
    );

    /** The kind's name, in the app's language (said in fixed lines and shown on the map). */
    val label: String get() = words.text

    /** The kind's name in Japanese, for the AI's instructions. */
    val labelJa: String get() = words.ja

    companion object {
        /** What a walker looks for; the kinds searched unless an app asks for others. */
        val WALK: Set<FacilityKind> = setOf(TOILETS, DRINKING_WATER, VENDING_MACHINE, SHELTER, BENCH)

        /** The kind an OpenStreetMap element with these [tags] is, if any. */
        fun of(tags: Map<String, String>): FacilityKind? = entries.firstOrNull { it.matches(tags) }
    }
}

data class Facility(val id: String, val kind: FacilityKind, val lat: Double, val lon: Double, val name: String?) {
    /** The kind, with the name if it has one: 「トイレ（浅草寺）」. Shown on the map. */
    val title: String get() = name?.let { "${kind.label}（$it）" } ?: kind.label

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
fun relativeDirection(heading: Float, bearing: Float): String = relativeSide(heading, bearing).ja

/** Where [bearing] lies from [heading], in the four languages, for the fixed lines (REQ-LANGUAGES F-11). */
fun relativeSide(heading: Float, bearing: Float): Words {
    val diff = ((bearing - heading) % 360 + 540) % 360 - 180 // -180..180, positive = right
    return when {
        diff in -45f..45f -> Words("前方", "ahead", "前方", "앞쪽")
        diff in 45f..135f -> Words("右手", "on the right", "右边", "오른쪽")
        diff in -135f..-45f -> Words("左手", "on the left", "左边", "왼쪽")
        else -> Words("後ろ", "behind", "后面", "뒤쪽")
    }
}
