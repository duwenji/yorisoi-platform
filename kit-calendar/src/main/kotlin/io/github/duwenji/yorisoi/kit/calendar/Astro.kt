package io.github.duwenji.yorisoi.kit.calendar

import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.searchSunLongitude
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The astronomy the calendar stands on, by Astronomy Engine (REQ-KOYOMI D-1): when the Sun's
 * apparent ecliptic longitude reaches a value, and the Moon's quarters (the difference between the
 * Moon's and the Sun's ecliptic longitudes: 0 new, 90 first quarter, 180 full, 270 last quarter).
 */
internal object Astro {
    /** Japan's time; the calendar's days are counted in it. */
    val JST: ZoneOffset = ZoneOffset.ofHours(9)

    private fun time(at: Instant) = Time.fromMillisecondsSince1970(at.toEpochMilli())
    private fun instant(t: Time): Instant = Instant.ofEpochMilli(t.toMillisecondsSince1970())

    fun startOf(year: Int): Instant = LocalDate.of(year, 1, 1).atStartOfDay().toInstant(JST)

    fun dateOf(at: Instant): LocalDate = at.atOffset(JST).toLocalDate()

    /** The moments the Sun reaches [firstLon], then each [step] degrees on, [count] of them, from [from] on. */
    fun sunSteps(firstLon: Double, step: Double, count: Int, from: Instant): List<Instant> {
        val out = ArrayList<Instant>(count)
        var after = from
        repeat(count) { k ->
            val lon = (firstLon + step * k) % 360.0
            val found = searchSunLongitude(lon, time(after), step * 1.3 + 3.0)
                ?: error("The Sun doesn't reach $lon° soon after $after")
            out += instant(found)
            after = instant(found).plusSeconds(3600)
        }
        return out
    }

    /** The moment in [year] (Japan's) the Sun reaches [lon]. */
    fun sunAt(lon: Double, year: Int): Instant {
        var after = startOf(year)
        repeat(13) {
            searchSunLongitude(lon, time(after), 30.0)?.let { return instant(it) }
            after = after.plus(Duration.ofDays(30))
        }
        error("The Sun doesn't reach $lon° in $year")
    }

    /** The new moons from [from] until [until]: a quarter of the work of [moonQuarters]. */
    fun newMoons(from: Instant, until: Instant): List<Instant> {
        val out = mutableListOf<Instant>()
        var after = from
        while (true) {
            val at = instant(searchMoonPhase(0.0, time(after), 40.0) ?: error("No new moon within 40 days of $after"))
            if (!at.isBefore(until)) return out
            out += at
            after = at.plus(Duration.ofDays(20))
        }
    }

    /** The Moon's quarters (0 new, 1 first, 2 full, 3 last) from [from] until [until]. */
    fun moonQuarters(from: Instant, until: Instant): List<Pair<Int, Instant>> {
        val out = mutableListOf<Pair<Int, Instant>>()
        var mq = searchMoonQuarter(time(from))
        while (instant(mq.time).isBefore(until)) {
            out += mq.quarter to instant(mq.time)
            mq = nextMoonQuarter(mq)
        }
        return out
    }
}
