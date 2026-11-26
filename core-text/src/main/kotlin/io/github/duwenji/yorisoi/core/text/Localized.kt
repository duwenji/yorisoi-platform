package io.github.duwenji.yorisoi.core.text

/**
 * The app's language for text the platform's parts make outside a screen's resources (REQ-LANGUAGES D-4・F-4・F-11):
 * messages of errors, labels, fixed lines. The app sets [current] when it starts and when the setting changes,
 * before it recreates its screens.
 */
object Languages {
    @Volatile
    var current: AppLanguage = AppLanguage.JA
}

/**
 * The text in the app's language ([Languages.current]). All four are required, so a missing translation stops
 * the build (N-3). [ja] stays the text the AI and the records read where one is needed.
 */
fun localized(ja: String, en: String, zh: String, ko: String, language: AppLanguage = Languages.current): String =
    when (language) {
        AppLanguage.JA -> ja
        AppLanguage.EN -> en
        AppLanguage.ZH_HANS -> zh
        AppLanguage.KO -> ko
    }

/** A text in the four languages, read in the app's language each time it is used (labels kept in enums and lists). */
class Words(val ja: String, val en: String, val zh: String, val ko: String) {
    /** The text in the app's language ([Languages.current]). */
    val text: String get() = localized(ja, en, zh, ko)

    /** The text in [language]. */
    fun of(language: AppLanguage): String = localized(ja, en, zh, ko, language)

    override fun toString(): String = text
}
