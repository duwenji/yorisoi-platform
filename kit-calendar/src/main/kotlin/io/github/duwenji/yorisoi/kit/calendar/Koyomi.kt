package io.github.duwenji.yorisoi.kit.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** How a fact stands to the day asked about (REQ-KOYOMI F-3). */
sealed interface Relation {
    /** It is today, or begins today. */
    data object Today : Relation

    /** The [day]-th day (2 or more) of something that began on an earlier day. */
    data class DayOf(val day: Int) : Relation

    /** It comes in [days] days (1–3). */
    data class DaysAhead(val days: Int) : Relation

    /** It was yesterday (a one-day thing). */
    data object Yesterday : Relation
}

/**
 * One thing the calendar has to say of a day (REQ-KOYOMI F-2): [note] is the operator's line on it
 * (D-3); [designatedBy] who set a designated day, [regions] where a regional day is kept.
 */
data class KoyomiFact(
    val id: String,
    val kind: KoyomiKind,
    val name: String,
    val reading: String?,
    val date: LocalDate,
    val relation: Relation,
    val note: String,
    val designatedBy: String? = null,
    val regions: String? = null,
) {
    val certainty: Certainty get() = kind.certainty
}

/**
 * The calendar's facts for a day (REQ-KOYOMI F-1, F-4): the solar term and the pentad now (and the
 * next term 1–3 days ahead); the seasonal days, festivals, holidays and regional days of today, 1–3
 * days ahead and yesterday (or of a period that has begun); the moon's quarters a day either side;
 * today's designated day and rokuyou. Japan's dates; no network. One day is kept (F-8).
 */
class Koyomi {
    @Volatile
    private var kept: Pair<LocalDate, List<KoyomiFact>>? = null

    fun on(date: LocalDate): List<KoyomiFact> {
        kept?.let { (day, facts) -> if (day == date) return facts }
        return facts(date).also { kept = date to it }
    }

    private fun facts(d: LocalDate): List<KoyomiFact> {
        val years = d.year - 1..d.year + 1
        val out = mutableListOf<KoyomiFact>()
        fun add(events: List<Event>, ahead: Boolean, yesterday: Boolean, filter: (Relation) -> Boolean = { true }) {
            for (e in events) relation(e, d, ahead, yesterday)?.takeIf(filter)?.let { out += fact(e, it) }
        }
        // ① The solar term now, and the next one 1–3 days ahead.
        val terms = years.flatMap(SolarTerms::of)
        add(terms.zipWithNext { (t, at), (_, next) ->
            Event(KoyomiKind.SEKKI, t.key, t.label, Astro.dateOf(at), Astro.dateOf(next).minusDays(1), t.note, t.reading)
        }, ahead = true, yesterday = false)
        // ② The pentad now. A year's list starts at 小寒's first pentad (285°), the 67th from 立春's.
        val pentads = years.flatMap(::pentadsOf)
        add(pentads.withIndex().zipWithNext { (i, at), (_, next) ->
            val k = Math.floorMod(i - 6, 72)
            val kou = Tables.KOU[k]
            Event(KoyomiKind.KOU, "${k + 1}", kou.name, Astro.dateOf(at), Astro.dateOf(next).minusDays(1), kou.note, kou.reading)
        }, ahead = false, yesterday = false)
        // ③ Seasonal days, festivals, holidays, regional days.
        add(years.flatMap(Zassetsu::of), ahead = true, yesterday = true)
        add(years.flatMap(Tables::sekku), ahead = true, yesterday = true)
        add(years.flatMap(Tables::lunar), ahead = true, yesterday = true)
        add(years.filter { it >= 2016 }.flatMap(Holidays::of), ahead = true, yesterday = true)
        add(years.flatMap(Tables::regional), ahead = true, yesterday = true)
        // ④ The moon's quarters a day either side.
        val quarters = Astro.moonQuarters(d.minusDays(10).atStartOfDay().toInstant(Astro.JST), d.plusDays(10).atStartOfDay().toInstant(Astro.JST))
        add(quarters.map { (q, at) -> MOON[q].let { (key, name, note) -> Event(KoyomiKind.MOON, key, name, Astro.dateOf(at), note = note) } },
            ahead = true, yesterday = true) { it !is Relation.DaysAhead || it.days == 1 }
        // ⑤ Today's designated day, ⑥ today's rokuyou.
        add(Tables.kinenbi(d.year), ahead = false, yesterday = false) { it == Relation.Today }
        val r = Rokuyou.of(d)
        out += fact(Event(KoyomiKind.ROKUYOU, r.key, r.label, d, note = r.note, reading = r.reading), Relation.Today)
        return out
    }

    /** The 72 pentads' beginnings in [year], from 小寒's first (285°) on; kept per year. */
    private fun pentadsOf(year: Int): List<Instant> = PENTADS.getOrPut(year) { Astro.sunSteps(285.0, 5.0, 72, Astro.startOf(year)) }

    private fun fact(e: Event, r: Relation) =
        KoyomiFact(e.id, e.kind, e.name, e.reading, e.start, r, e.note, e.designatedBy, e.regions)

    private fun relation(e: Event, d: LocalDate, ahead: Boolean, yesterday: Boolean): Relation? {
        val toStart = ChronoUnit.DAYS.between(d, e.start)
        return when {
            toStart == 0L -> Relation.Today
            toStart < 0 && !d.isAfter(e.end) -> Relation.DayOf((1 - toStart).toInt())
            ahead && toStart in 1..3 -> Relation.DaysAhead(toStart.toInt())
            yesterday && e.start == e.end && toStart == -1L -> Relation.Yesterday
            else -> null
        }
    }

    private companion object {
        val PENTADS = ConcurrentHashMap<Int, List<Instant>>()

        val MOON = listOf(
            Triple("new", "新月", "月が太陽と同じ方向にあって、見えない"),
            Triple("first-quarter", "上弦の月", "右半分が光る半月。夕方の南の空に見える"),
            Triple("full", "満月", "夜通し見える丸い月"),
            Triple("last-quarter", "下弦の月", "左半分が光る半月。明け方の南の空に見える"),
        )
    }
}
