package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** A day of the old lunisolar calendar: its month (1–12), day (1–30), and whether the month is a leap one. */
data class LunarDate(val month: Int, val day: Int, val leap: Boolean)

/**
 * The old lunisolar calendar (REQ-KOYOMI F-5): a month starts on the day (Japan's) of a new moon and
 * is named by the 中気 it holds (雨水 the 1st, 春分 the 2nd, … 大寒 the 12th); a month without one is
 * the leap month of the one before. Not for the year 2033 and after, whose months this rule can't name
 * (the "2033 problem"); see REQ-KOYOMI R-6.
 */
object Lunisolar {
    private val CHUKI_MONTH = mapOf(330 to 1, 0 to 2, 30 to 3, 60 to 4, 90 to 5, 120 to 6, 150 to 7, 180 to 8, 210 to 9, 240 to 10, 270 to 11, 300 to 12)
    private val newMoons = ConcurrentHashMap<Int, List<LocalDate>>()

    fun of(date: LocalDate): LunarDate {
        val starts = (date.year - 1..date.year + 1).flatMap(::newMoonsOf)
        val k = starts.indexOfLast { !it.isAfter(date) }
        val (month, leap) = monthOf(starts, k)
        return LunarDate(month, ChronoUnit.DAYS.between(starts[k], date).toInt() + 1, leap)
    }

    /**
     * The first day of [year] (Gregorian) that is the [day] of the [month] (not a leap one), or null.
     * Found from the months' first days, not day by day. A lunar date can fall twice in a Gregorian
     * year (months 11 and 12); the earlier is given.
     */
    fun dateOf(year: Int, month: Int, day: Int): LocalDate? {
        val starts = (year - 1..year + 1).flatMap(::newMoonsOf)
        for (k in 1 until starts.size - 1) {
            val (m, leap) = monthOf(starts, k)
            if (leap || m != month) continue
            val d = starts[k].plusDays(day - 1L)
            if (d.year == year && d.isBefore(starts[k + 1])) return d
        }
        return null
    }

    private fun newMoonsOf(year: Int): List<LocalDate> = newMoons.getOrPut(year) {
        Astro.newMoons(Astro.startOf(year), Astro.startOf(year + 1)).map(Astro::dateOf)
    }

    private fun monthOf(starts: List<LocalDate>, k: Int): Pair<Int, Boolean> {
        chukiIn(starts[k], starts[k + 1])?.let { return it to false }
        val before = chukiIn(starts[k - 1], starts[k]) ?: error("Two months without 中気 in a row from ${starts[k - 1]}")
        return before to true
    }

    /** The month named by the first 中気 whose day falls in [from, until). */
    private fun chukiIn(from: LocalDate, until: LocalDate): Int? =
        (from.year..until.year).flatMap(SolarTerms::of)
            .filter { it.first.isChuki }
            .firstOrNull { val d = Astro.dateOf(it.second); !d.isBefore(from) && d.isBefore(until) }
            ?.let { CHUKI_MONTH.getValue(it.first.longitude) }
}

/** The sexagenary count of days (干支). 0 is 甲子; [stem] 0 is 甲 (4 戊), [branch] 0 is 子 (11 亥). */
internal object Kanshi {
    /** The Julian day number of [date] (at its noon). */
    private fun jdn(date: LocalDate): Long = date.toEpochDay() + 2440588

    fun index(date: LocalDate): Int = Math.floorMod(jdn(date) - 11, 60L).toInt()
    fun stem(date: LocalDate): Int = index(date) % 10
    fun branch(date: LocalDate): Int = index(date) % 12
}
