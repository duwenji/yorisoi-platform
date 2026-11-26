package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.text.AppLanguage
import io.github.duwenji.yorisoi.core.text.ContactCheck
import io.github.duwenji.yorisoi.core.text.LanguageCheck
import io.github.duwenji.yorisoi.core.text.SpokenText

/**
 * Replies the app doesn't read out (AI-ROLES H-6 ③): a reply with a URL, an email address or a phone
 * number, one written as notes (headings, list items) rather than speech, or one mostly in another language
 * than the app's (REQ-LANGUAGES R-1) is not used, and a fixed line stands in. Stage directions, Markdown marks
 * and notes on how the reply was written are taken out before anything else, since they'd be read out.
 * The checks themselves live in core-text, which every path shares (AGENT A-52).
 */
object OutputCheck {
    /** [text] as it may be read out: without stage directions, Markdown marks and notes on the reply. */
    fun spoken(text: String): String = SpokenText.spoken(text)

    /** [text] without the lines that are notes on the reply rather than speech; the rest as it was. */
    fun withoutMetaNotes(text: String): String = SpokenText.withoutMetaNotes(text)

    /** [text] without the stage directions in it. */
    fun withoutStageDirections(text: String): String = SpokenText.withoutStageDirections(text)

    /** [text] without Markdown's bold, italic and heading marks. */
    fun withoutMarkdown(text: String): String = SpokenText.withoutMarkdown(text)

    /** What in [text] makes it unusable when it is to be spoken in [language], or null when it may be used. */
    fun violation(text: String, language: AppLanguage = AppLanguage.JA): String? =
        ContactCheck.violation(text)
            ?: NOTES.takeIf { SpokenText.isNotes(text) }
            ?: OTHER_LANGUAGE.takeIf { LanguageCheck.isOff(text, language) }

    /** What [violation] says of a reply written as notes rather than speech. */
    const val NOTES = "notes, not speech"

    /** What [violation] says of a reply mostly in another language than the app's. */
    const val OTHER_LANGUAGE = "not in the app's language"
}
