package io.github.duwenji.yorisoi.core.web

import org.jsoup.nodes.Document
import java.time.LocalDate

/**
 * The date a page states for itself (AGENT A-18: pages without one serve only the `spot` scene):
 * its metadata (`article:published_time`, `article:modified_time`, `dc.date`, `date`, `<time datetime>`),
 * or else a 「更新日」「掲載日」「公開日」「作成日」 in its text, in the Western or the 令和 calendar.
 * The latest of them is taken.
 */
internal object PageDate {
    private val META = listOf(
        "meta[property=article:modified_time]", "meta[property=article:published_time]",
        "meta[name=dc.date]", "meta[name=DC.date]", "meta[name=date]", "meta[itemprop=dateModified]", "meta[itemprop=datePublished]",
    )
    private val ISO = Regex("(20\\d{2})-(\\d{1,2})-(\\d{1,2})")
    private val LABEL = Regex("(?:更新日|掲載日|公開日|作成日|最終更新|更新)[^0-9０-９令]{0,6}")
    private val WESTERN = Regex("(20\\d{2})\\s*[年/.\\-]\\s*(\\d{1,2})\\s*[月/.\\-]\\s*(\\d{1,2})")
    private val REIWA = Regex("令和\\s*(元|\\d{1,2})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日")

    fun of(doc: Document): LocalDate? {
        val found = ArrayList<LocalDate>()
        for (q in META) doc.select(q).forEach { e -> ISO.find(e.attr("content"))?.let { date(it)?.let(found::add) } }
        doc.select("time[datetime]").forEach { e -> ISO.find(e.attr("datetime"))?.let { date(it)?.let(found::add) } }
        if (found.isEmpty()) {
            val text = narrow(doc.text())
            for (m in LABEL.findAll(text)) {
                val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 30))
                (WESTERN.find(after)?.let(::date) ?: REIWA.find(after)?.let(::reiwa))?.let(found::add)
            }
        }
        return found.maxOrNull()
    }

    private fun date(m: MatchResult): LocalDate? = runCatching {
        LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }.getOrNull()

    private fun reiwa(m: MatchResult): LocalDate? = runCatching {
        val y = m.groupValues[1].let { if (it == "元") 1 else it.toInt() }
        LocalDate.of(2018 + y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }.getOrNull()

    /** Full-width digits as ASCII. */
    private fun narrow(s: String): String = buildString(s.length) { for (c in s) append(if (c in '０'..'９') c - 0xFEE0 else c) }
}
