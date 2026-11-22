package io.github.duwenji.yorisoi.core.text

/**
 * The checks on text a provider wrote into a package (slots, materials, collections; API-CHANNEL-PACKAGE
 * F-2): no URL and no Markdown heading. Like [ContactCheck], full-width forms are brought to ASCII width
 * first, so 「ｈｔｔｐｓ：／／」 is a URL here too (ADR-AGENT A-52).
 */
object WrittenText {
    /** A URL's start: 「http://」「https://」「www.」, in any case. Matched against [ContactCheck.normalize]d text. */
    val URL: Regex = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    /** A line starting with 「#」, so it can't be taken for the app's own headings. */
    val HEADING: Regex = Regex("(?m)^\\s*#")

    /** The first URL in [text] (as written, full-width forms included), or null. */
    fun findUrl(text: String): String? {
        val m = URL.find(ContactCheck.normalize(text)) ?: return null
        return text.substring(m.range.first, minOf(text.length, m.range.last + 1))
    }

    /** Whether [text] has a line that is a heading. */
    fun hasHeading(text: String): Boolean = HEADING.containsMatchIn(text)
}
