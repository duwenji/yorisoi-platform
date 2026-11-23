package io.github.duwenji.yorisoi.core.web

import io.github.duwenji.yorisoi.core.text.ContactCheck
import io.github.duwenji.yorisoi.core.text.InstructionCheck
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate

/** A link on a page: its title (cut and checked; empty when it failed the check) and its absolute URL. */
data class PageLink(val title: String, val url: String)

/** What a page gives the hint maker (ADR-AGENT A-18): its title, its date when it states one, its text, and its links. */
data class Page(val title: String, val published: LocalDate?, val text: String, val links: List<PageLink>)

/**
 * The text and links of an HTML page (ADR-AGENT A-18). The page's own text is taken from its main part
 * (without scripts, menus, headers and footers), split into sentences, and every sentence that fails the
 * machine check (contact details, instruction-like sentences; core-text) is dropped. At most [MAX_TEXT]
 * characters are kept. Link titles are cut to [MAX_TITLE] characters and emptied when they fail the check.
 */
object PageText {
    const val MAX_TEXT = 2_000
    const val MAX_TITLE = 40
    private const val CARD_DEPTH = 4
    private val GENERIC = listOf("詳細", "詳しく", "もっと見る", "続きを読む", "続きを見る", "一覧を見る", "こちら", "more", "More", "MORE", "Read more")
    const val MAX_LINKS = 150

    private val SENTENCE_END = Regex("(?<=[。！？!?])|\n+")

    fun read(html: String, baseUrl: String): Page {
        val doc = Jsoup.parse(html, baseUrl)
        val title = checked(doc.title().trim(), MAX_TITLE)
        val published = PageDate.of(doc)
        val anchors = doc.select("a[href]")
        // A text many links share (「詳細を見る」 on every card) names nothing: such a link takes its card's title.
        val shared = anchors.groupingBy { it.text().trim() }.eachCount().filterValues { it >= 3 }.keys
        val links = anchors.asSequence()
            .map { it.absUrl("href").substringBefore('#') to linkTitle(it, shared) }
            .filter { (u, _) -> u.startsWith("http://") || u.startsWith("https://") }
            .distinctBy { it.first }
            .map { (u, t) -> PageLink(checked(t, MAX_TITLE), u) }
            .take(MAX_LINKS)
            .toList()
        return Page(title, published, mainText(doc), links)
    }

    /**
     * What a link is about: its own text, or, when that is a generic one ([GENERIC]) or one many links share, the
     * title or heading of the card it is in (up to [CARD_DEPTH] levels up). The link's own text when there is none.
     */
    private fun linkTitle(a: Element, shared: Set<String>): String {
        val own = a.text().trim()
        if (own.isNotEmpty() && own !in shared && GENERIC.none { own.startsWith(it) }) return own
        var box: Element? = a.parent()
        repeat(CARD_DEPTH) {
            val card = box ?: return own
            card.selectFirst("h1, h2, h3, h4, h5, h6, .title, [class*=title], [class*=ttl], dt, strong")
                ?.text()?.trim()?.takeIf { it.isNotEmpty() && it != own && it !in shared }?.let { return it }
            box = card.parent()
        }
        return own
    }

    private fun mainText(doc: Document): String {
        doc.select("script, style, noscript, nav, header, footer, aside, form, iframe, template").remove()
        val main = doc.selectFirst("main, article, [role=main], #main, #contents, #content, .main") ?: doc.body() ?: return ""
        // Block elements end a sentence even when the page didn't write 「。」.
        main.select("p, li, h1, h2, h3, h4, h5, h6, dt, dd, tr, br, div").forEach { it.appendText("\n") }
        val sentences = main.wholeText().split(SENTENCE_END).map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val kept = StringBuilder()
        for (s in sentences) {
            if (ContactCheck.violation(s) != null || InstructionCheck.looksLikeInstruction(s)) continue
            if (kept.length + s.length + 1 > MAX_TEXT) break
            if (kept.isNotEmpty()) kept.append('\n')
            kept.append(s)
        }
        return kept.toString()
    }

    /** [text] cut to [max] characters, or empty when it fails the machine check. */
    internal fun checked(text: String, max: Int): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        if (ContactCheck.violation(t) != null || InstructionCheck.looksLikeInstruction(t)) return ""
        return if (t.codePointCount(0, t.length) <= max) t else t.substring(0, t.offsetByCodePoints(0, max))
    }
}
