package io.github.duwenji.yorisoi.core.text

/**
 * Whether a reply is in the app's language (REQ-LANGUAGES R-1, F-13): the AI reads Japanese instructions and
 * materials, and sometimes answers in Japanese anyway. Names kept in their own script (浅草寺) are allowed;
 * a reply mostly in another script is not.
 */
object LanguageCheck {
    /** Whether [text] is mostly in another language than [language]. A Japanese reply is never counted off. */
    fun isOff(text: String, language: AppLanguage): Boolean {
        var letters = 0
        var kana = 0
        var han = 0
        var hangul = 0
        for (c in text) {
            if (!Character.isLetter(c)) continue
            letters++
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.HANGUL -> hangul++
                else -> Unit
            }
        }
        if (letters < MIN_LETTERS) return false
        return when (language) {
            AppLanguage.JA -> false
            AppLanguage.EN -> (kana + han + hangul) > letters * EN_MAX_OTHER
            AppLanguage.ZH_HANS -> kana + hangul > letters * ZH_MAX_OTHER
            AppLanguage.KO -> hangul < letters * KO_MIN_HANGUL
        }
    }

    /** Fewer letters than this are too few to tell (a name alone, 「1：」). */
    private const val MIN_LETTERS = 8
    private const val EN_MAX_OTHER = 0.3
    private const val ZH_MAX_OTHER = 0.1
    private const val KO_MIN_HANGUL = 0.5
}
