package io.github.duwenji.yorisoi.core.text

/**
 * Contact details a reply must not read out (AI-ROLES H-6 ③): a URL, an email address or a phone number.
 * Text is first brought to ASCII width, so full-width forms are caught too.
 */
object ContactCheck {
    private val URL = Regex("https?://|www\\.", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+")
    // Japanese numbers start with 0: 03-1234-5678, 0120-123-456, 090 1234 5678, (0467)22-1234, 0312345678.
    // Korean ones too (010-1234-5678, 02-123-4567).
    private val PHONE = Regex("(?<![0-9])[(]?0[0-9]{1,4}[)]?[-\\s]?[0-9]{1,4}[-\\s]?[0-9]{3,4}(?![0-9])")

    /**
     * The other forms a reply in another language may write (REQ-LANGUAGES F-13): an international number
     * (+81 3-1234-5678), a Chinese mobile (138 1234 5678, 13812345678) or service number (400-123-4567), and a
     * North American one ((555) 123-4567, 555-123-4567). Years and prices don't take these shapes.
     */
    private val PHONE_ABROAD = listOf(
        Regex("(?<![0-9A-Za-z])\\+[1-9][0-9]{0,2}[-\\s.]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{2,4}[-\\s.]?[0-9]{3,4}(?![0-9])"),
        Regex("(?<![0-9])1[3-9][0-9][-\\s]?[0-9]{4}[-\\s]?[0-9]{4}(?![0-9])"),
        Regex("(?<![0-9])[48]00[-\\s][0-9]{3}[-\\s][0-9]{4}(?![0-9])"),
        Regex("(?<![0-9])(?:[(][2-9][0-9]{2}[)]\\s?|[2-9][0-9]{2}[-.])[0-9]{3}[-.][0-9]{4}(?![0-9])"),
    )

    /** What in [text] makes it unusable (「a URL」「an email address」「a phone number」), or null when it may be used. */
    fun violation(text: String): String? {
        val plain = normalize(text)
        return when {
            URL.containsMatchIn(plain) -> "a URL"
            EMAIL.containsMatchIn(plain) -> "an email address"
            PHONE.containsMatchIn(plain) || PHONE_ABROAD.any { it.containsMatchIn(plain) } -> "a phone number"
            else -> null
        }
    }

    /** Full-width digits, letters and signs as their ASCII forms, and every dash as '-'. */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '！'..'～' -> c - 0xFEE0
                    '‐', '‑', '‒', '–', '—', '―', 'ー', '−' -> '-'
                    '　' -> ' '
                    else -> c
                },
            )
        }
    }
}
