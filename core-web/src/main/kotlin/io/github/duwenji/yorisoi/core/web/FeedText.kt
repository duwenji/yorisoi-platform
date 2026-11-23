package io.github.duwenji.yorisoi.core.web

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** One item of a feed: its title (cut and checked; empty when it failed), its link and its date. */
data class FeedItem(val title: String, val url: String, val date: LocalDate?)

/**
 * The items of an RSS 2.0, RSS 1.0 (RDF) or Atom feed (AGENT A-21): a source's 新着 list, with dates.
 * The parser refuses DTDs and external entities, since the feed comes from a site nobody reviewed.
 */
object FeedText {
    const val MAX_ITEMS = 50

    fun read(xml: ByteArray): List<FeedItem> {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = runCatching { f.newDocumentBuilder().parse(ByteArrayInputStream(xml)) }.getOrNull() ?: return emptyList()
        val items = ArrayList<FeedItem>()
        for (name in listOf("item", "entry")) {
            val nodes = doc.getElementsByTagNameNS("*", name)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                val title = child(e, "title") ?: ""
                val link = child(e, "link")?.takeIf { it.startsWith("http") }
                    ?: firstElement(e, "link")?.getAttribute("href")?.takeIf { it.startsWith("http") }
                    ?: (e.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about").takeIf { it.startsWith("http") })
                    ?: continue
                val date = listOf("pubDate", "date", "updated", "published").firstNotNullOfOrNull { child(e, it)?.let(::date) }
                items += FeedItem(PageText.checked(title, PageText.MAX_TITLE), link.trim(), date)
                if (items.size >= MAX_ITEMS) return items
            }
        }
        return items
    }

    private fun firstElement(e: Element, local: String): Element? {
        val n = e.getElementsByTagNameNS("*", local)
        return if (n.length > 0) n.item(0) as Element else null
    }

    private fun child(e: Element, local: String): String? = firstElement(e, local)?.textContent?.trim()?.ifEmpty { null }

    private fun date(s: String): LocalDate? =
        runCatching { OffsetDateTime.parse(s).toLocalDate() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}
