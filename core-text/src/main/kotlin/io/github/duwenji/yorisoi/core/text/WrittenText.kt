package io.github.duwenji.yorisoi.core.text

/**
 * The checks on text a provider wrote into a package (slots, materials, collections; API-CHANNEL-PACKAGE
 * F-2): no URL and no Markdown heading. The text is matched as written; full-width forms are not brought
 * to ASCII width, unlike [ContactCheck].
 */
object WrittenText {
    /** A URL's start: 「http://」「https://」「www.」, in any case. */
    val URL: Regex = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)

    /** A line starting with 「#」, so it can't be taken for the app's own headings. */
    val HEADING: Regex = Regex("(?m)^\\s*#")

    /** The first URL in [text], or null. */
    fun findUrl(text: String): MatchResult? = URL.find(text)

    /** Whether [text] has a line that is a heading. */
    fun hasHeading(text: String): Boolean = HEADING.containsMatchIn(text)
}
