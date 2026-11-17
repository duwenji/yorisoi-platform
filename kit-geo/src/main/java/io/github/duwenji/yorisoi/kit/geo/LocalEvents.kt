package io.github.duwenji.yorisoi.kit.geo

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Where an event's position came from (API-SPOT-TILES, the events layer). */
enum class EventPrecision {
    /** The venue itself. */
    POINT,

    /** Somewhere in a town ([LocalEvent.area]); its position is the town's. */
    AREA,
}

/** Whose open data an event comes from: the municipality, the dataset's page, and its licence. */
data class EventSource(val publisher: String, val dataset: String, val license: String)

/**
 * A local event from a municipality's open data, as the operator's tiles have it
 * (REQ-LOCAL-EVENTS F-18). Dates and times are Japan's.
 */
data class LocalEvent(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val venue: String,
    val lat: Double,
    val lon: Double,
    val precision: EventPrecision,
    /** The town's name, when [precision] is [EventPrecision.AREA]. */
    val area: String?,
    val summary: String?,
    val conditions: String?,
    val url: String?,
    val source: EventSource,
) {
    /**
     * Whether it is held at [nowMs]: today is one of its days and, if it has an end time, that
     * time hasn't come yet (REQ-LOCAL-EVENTS F-24). A start time later today still counts.
     */
    fun isOn(nowMs: Long): Boolean {
        val at = Instant.ofEpochMilli(nowMs).atZone(LocalEvents.ZONE)
        val day = at.toLocalDate()
        if (day < startDate || day > endDate) return false
        val end = endTime?.takeIf { startTime == null || it > startTime } ?: return true
        return at.toLocalTime() < end
    }

    internal companion object {
        /** The event, or null when a value it must have is missing or can't be read (API-SPOT-TILES AP-8). */
        fun fromJson(o: JSONObject): LocalEvent? {
            return try {
                read(o)
            } catch (e: org.json.JSONException) {
                null
            } catch (e: DateTimeParseException) {
                null
            }
        }

        private fun read(o: JSONObject): LocalEvent? {
            val precision = when (o.getString("precision")) {
                "point" -> EventPrecision.POINT
                "area" -> EventPrecision.AREA
                else -> return null
            }
            val area = o.text("area")
            if (precision == EventPrecision.AREA && area == null) return null
            val s = o.getJSONObject("source")
            return LocalEvent(
                id = o.getString("id"),
                name = o.getString("name"),
                startDate = LocalDate.parse(o.getString("startDate")),
                endDate = LocalDate.parse(o.getString("endDate")),
                startTime = o.text("startTime")?.let(LocalTime::parse),
                endTime = o.text("endTime")?.let(LocalTime::parse),
                venue = o.getString("venue"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                precision = precision,
                area = area,
                summary = o.text("summary"),
                conditions = o.text("conditions"),
                url = o.text("url"),
                source = EventSource(s.getString("publisher"), s.getString("dataset"), s.getString("license")),
            ).takeIf { it.endDate >= it.startDate }
        }

        private fun JSONObject.text(key: String): String? = if (has(key)) getString(key).ifEmpty { null } else null
    }
}

/**
 * A local event near the user, with where it is from them. [distanceM] is rounded to 50m. For an
 * event at its venue ([EventPrecision.POINT]), [compass] is one of the eight directions and [side]
 * is where it is from the way the user goes (null when that isn't known); for one somewhere in a
 * town both are null, as the AI says only the town (REQ-LOCAL-EVENTS F-30).
 */
data class NearbyLocalEvent(val event: LocalEvent, val distanceM: Int, val compass: String?, val side: String?)

/** Picks the local events to talk of (REQ-LOCAL-EVENTS F-24, F-27). */
object LocalEvents {
    /** The dates and times of the events are Japan's. */
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    /** How far a nearby event may be (F-24). */
    const val RADIUS_M = 1000.0

    /** How far ahead an event's venue may be to tell of it before the user gets there (F-27). */
    const val AHEAD_M = 300.0

    /**
     * The events of [events] held at [nowMs] within [radiusM] of [here], leaving out [exclude]
     * (ids: those talked about on this walk or ride), nearest first. [headingDeg] is the way the
     * user goes (degrees clockwise from north), if known.
     */
    fun nearby(
        events: List<LocalEvent>,
        here: LatLon,
        nowMs: Long,
        headingDeg: Double? = null,
        exclude: Set<String> = emptySet(),
        radiusM: Double = RADIUS_M,
    ): List<NearbyLocalEvent> = events.asSequence()
        .filter { it.id !in exclude && it.isOn(nowMs) }
        .map { it to RouteGeometry.distanceM(here, LatLon(it.lat, it.lon)) }
        .filter { (_, d) -> d <= radiusM }
        .sortedBy { (_, d) -> d }
        .map { (e, d) ->
            if (e.precision == EventPrecision.AREA) {
                NearbyLocalEvent(e, NearbyHighlights.rounded(d), null, null)
            } else {
                val bearing = RouteGeometry.bearingDeg(here, LatLon(e.lat, e.lon))
                NearbyLocalEvent(e, NearbyHighlights.rounded(d), NearbyHighlights.compass(bearing), headingDeg?.let { relativeDirection(it.toFloat(), bearing.toFloat()) })
            }
        }
        .toList()

    /**
     * The nearest event held at [nowMs] whose venue lies ahead of the user ([relativeDirection]
     * 前方) within [withinM], to tell of before the user gets there (F-27); only an event at its
     * venue ([EventPrecision.POINT], F-30). Null when there is none.
     */
    fun ahead(
        events: List<LocalEvent>,
        here: LatLon,
        headingDeg: Double,
        nowMs: Long,
        exclude: Set<String> = emptySet(),
        withinM: Double = AHEAD_M,
    ): NearbyLocalEvent? = nearby(events.filter { it.precision == EventPrecision.POINT }, here, nowMs, headingDeg, exclude, withinM)
        .firstOrNull { it.side == "前方" }
}
