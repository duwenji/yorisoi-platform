package io.github.duwenji.yorisoi.kit.geo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Which local events are near and held now (REQ-LOCAL-EVENTS F-24, F-27, F-30). */
class LocalEventsTest {
    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), LocalEvents.ZONE).toInstant().toEpochMilli()

    private val here = LatLon(35.0, 139.0)

    private fun event(
        id: String,
        lat: Double = 35.002,
        lon: Double = 139.0,
        start: String = "2026-11-21",
        end: String = start,
        startTime: String? = null,
        endTime: String? = null,
        area: String? = null,
    ) = LocalEvent(
        id, "催し $id", LocalDate.parse(start), LocalDate.parse(end), startTime?.let(LocalTime::parse), endTime?.let(LocalTime::parse),
        "会場", lat, lon, if (area == null) EventPrecision.POINT else EventPrecision.AREA, area, null, null, null,
        EventSource("東京都江東区", "https://example.jp/dataset/1", "CC-BY-4.0"),
    )

    @Test
    fun `an event is held on its days, until its end time`() {
        val e = event("1", start = "2026-11-21", end = "2026-11-23", startTime = "10:00", endTime = "16:00")
        assertFalse(e.isOn(at("2026-11-20T12:00")))
        assertTrue(e.isOn(at("2026-11-21T08:00"))) // not started yet, but held today
        assertTrue(e.isOn(at("2026-11-22T15:59")))
        assertFalse(e.isOn(at("2026-11-23T16:00")))
        assertFalse(e.isOn(at("2026-11-24T09:00")))
        // Japan's day, whatever the device's zone: 2026-11-20T23:30Z is the 21st in Japan.
        assertTrue(event("2").isOn(java.time.Instant.parse("2026-11-20T23:30:00Z").toEpochMilli()))
        // An end time before the start (into the night) is not read as the end.
        assertTrue(event("3", startTime = "18:00", endTime = "02:00").isOn(at("2026-11-21T21:00")))
    }

    @Test
    fun `nearby events are those held today within 1km, nearest first, leaving out those talked of`() {
        val events = listOf(
            event("far", lat = 35.0095), // about 1.05km north
            event("north", lat = 35.004), // 450m north
            event("east", lat = 35.0, lon = 139.002), // 180m east
            event("tomorrow", start = "2026-11-22"),
            event("talked"),
            event("town", lat = 34.995, area = "江東区平野"), // 550m south
        )
        val now = at("2026-11-21T10:00")
        val got = LocalEvents.nearby(events, here, now, headingDeg = 0.0, exclude = setOf("talked"))
        assertEquals(listOf("east", "north", "town"), got.map { it.event.id })
        val east = got[0]
        assertEquals(200, east.distanceM)
        assertEquals("東", east.compass)
        assertEquals("右手", east.side)
        // Somewhere in a town: no direction (F-30).
        assertNull(got[2].compass)
        assertNull(got[2].side)
    }

    @Test
    fun `an event ahead within 300m is told of before, only at its venue`() {
        val now = at("2026-11-21T10:00")
        val events = listOf(
            event("behind", lat = 34.999),
            event("ahead-far", lat = 35.004),
            event("ahead", lat = 35.002),
            event("town-ahead", lat = 35.001, area = "江東区平野"),
        )
        assertEquals("ahead", LocalEvents.ahead(events, here, headingDeg = 0.0, nowMs = now)?.event?.id)
        assertNull(LocalEvents.ahead(events, here, headingDeg = 0.0, nowMs = now, exclude = setOf("ahead")))
        assertEquals("behind", LocalEvents.ahead(events, here, headingDeg = 180.0, nowMs = now)?.event?.id)
    }

    @Test
    fun `an event is read from the tile, and left out when a value it must have is missing`() {
        val json = """{"id":"131083:KK1","name":"木場公園の朝市","startDate":"2026-11-21","endDate":"2026-11-21","startTime":"09:00",
            "venue":"木場公園","lat":35.67,"lon":139.8,"precision":"point","summary":"毎月の朝市","url":"https://example.jp/e/1",
            "source":{"publisher":"東京都江東区","dataset":"https://example.jp/dataset/1","license":"CC-BY-4.0"},"somethingNew":1}"""
        val e = LocalEvent.fromJson(JSONObject(json))!!
        assertEquals("木場公園の朝市", e.name)
        assertEquals(LocalTime.of(9, 0), e.startTime)
        assertNull(e.endTime)
        assertEquals(EventPrecision.POINT, e.precision)
        assertEquals("東京都江東区", e.source.publisher)
        // An area without the town's name, an unknown precision, a date that can't be read.
        assertNull(LocalEvent.fromJson(JSONObject(json.replace("\"point\"", "\"area\""))))
        assertNull(LocalEvent.fromJson(JSONObject(json.replace("\"point\"", "\"somewhere\""))))
        assertNull(LocalEvent.fromJson(JSONObject(json.replace("\"startDate\":\"2026-11-21\"", "\"startDate\":\"11月21日\""))))
        assertEquals("江東区平野", LocalEvent.fromJson(JSONObject(json.replace("\"point\"", "\"area\",\"area\":\"江東区平野\"")))?.area)
    }
}
