package io.github.duwenji.yorisoi.core.web

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Whether a page's text is about now (ADR-AGENT A-18): it names a period that includes [today]
 * (「10月10日（土）～11月23日（月・祝）」) or a day from today up to [SOON_DAYS] days ahead. An event's page is often written
 * before it opens and never updated, so its own date says little; such a page serves the day's threads although
 * it is older than 30 days. Dates without a year take the year that puts them nearest [today]; the second date of a
 * period takes the first's year (or the next, when it would come before it).
 */
object PageCurrent {
    const val SOON_DAYS = 14L
    private const val RANGE_GAP = 24
    private val DATE = Regex("(?:(20\\d{2})\\s*年|令和\\s*(元|\\d{1,2})\\s*年|(20\\d{2})\\s*[/.])?\\s*(\\d{1,2})\\s*[月/.]\\s*(\\d{1,2})\\s*日?")
    private val RANGE = Regex("[～〜~ー－\\-]|から|まで")

    fun of(text: String, today: LocalDate): Boolean {
        val t = buildString(text.length) { for (c in text) append(if (c in '０'..'９') c - 0xFEE0 else c) }
        val found = DATE.findAll(t).mapNotNull { m -> dateOf(m, today)?.let { it to m.range } }.toList()
        if (found.any { (d, _) -> !d.isBefore(today) && ChronoUnit.DAYS.between(today, d) <= SOON_DAYS }) return true
        return found.zipWithNext().any { (a, b) ->
            val between = t.substring(a.second.last + 1, b.second.first)
            between.length <= RANGE_GAP && RANGE.containsMatchIn(between) && !a.first.isAfter(today) && !endOf(a.first, b, today).isBefore(today)
        }
    }

    /** A period's end: written without a year, it is in the start's year, or the next. */
    private fun endOf(start: LocalDate, end: Pair<LocalDate, IntRange>, today: LocalDate): LocalDate {
        val e = end.first
        return if (e.isBefore(start) && e.year == start.year) e.plusYears(1) else e
    }

    private fun dateOf(m: MatchResult, today: LocalDate): LocalDate? = runCatching {
        val month = m.groupValues[4].toInt()
        val day = m.groupValues[5].toInt()
        val year = m.groupValues[1].toIntOrNull() ?: m.groupValues[3].toIntOrNull()
            ?: m.groupValues[2].takeIf { it.isNotEmpty() }?.let { 2018 + (if (it == "元") 1 else it.toInt()) }
        if (year != null) return@runCatching LocalDate.of(year, month, day)
        listOf(today.year - 1, today.year, today.year + 1).map { LocalDate.of(it, month, day) }
            .minBy { kotlin.math.abs(ChronoUnit.DAYS.between(today, it)) }
    }.getOrNull()
}
