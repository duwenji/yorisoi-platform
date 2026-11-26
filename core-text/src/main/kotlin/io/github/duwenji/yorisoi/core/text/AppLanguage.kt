package io.github.duwenji.yorisoi.core.text

import kotlin.math.roundToInt

/**
 * The languages an app speaks and shows (REQ-LANGUAGES D-1): the screens, the voice, the AI's narration and the
 * fixed lines all follow the one chosen ("the app's language").
 *
 * @param tag the BCP 47 value, as written in packages and settings
 * @param charsPerSecond about how many characters the voice reads in a second (D-6), to turn the
 *   seconds a line may take into a length in this language
 */
enum class AppLanguage(val tag: String, val charsPerSecond: Int) {
    JA("ja", 5),
    EN("en", 14),
    ZH_HANS("zh-Hans", 4),
    KO("ko", 6),
    ;

    /** The language's own name, as the setting lists it (「English」「简体中文」). */
    val selfName: String get() = when (this) {
        JA -> "日本語"
        EN -> "English"
        ZH_HANS -> "简体中文"
        KO -> "한국어"
    }

    /** About as many characters as a line of [seconds] takes to read out (D-6). */
    fun chars(seconds: Int): Int = seconds * charsPerSecond

    /**
     * A length given in Japanese characters (as the prompts and the fixed limits are written), as a length in this
     * language that takes about as long to read out (D-6).
     */
    fun fromJapanese(japaneseChars: Int): Int = (japaneseChars.toDouble() * charsPerSecond / JA.charsPerSecond).roundToInt()

    /**
     * What to add at the end of the AI's fixed instructions so it answers in this language (D-5, F-9・F-12, R-1),
     * or null for Japanese, which the instructions are written in. Written in Japanese, as the instructions are.
     */
    val instruction: String? get() = when (this) {
        JA -> null
        EN -> instructionFor("英語", "ローマ字で書き（例: Sensō-ji）")
        ZH_HANS -> instructionFor("中国語（簡体字）", "漢字で書き（例: 浅草寺）")
        KO -> instructionFor("韓国語", "日本語の読みをハングルで書き（例: 센소지）")
    }

    companion object {
        /** The language used when none of the device's languages is one of these (D-3). */
        val FALLBACK: AppLanguage = EN

        /** The language a BCP 47 tag (or a Java locale's `ja_JP` form) is in, or null when it is none of these. Any Chinese is [ZH_HANS] (F-1). */
        fun of(tag: String?): AppLanguage? {
            val language = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
            return when (language) {
                "ja" -> JA
                "en" -> EN
                "zh" -> ZH_HANS
                "ko" -> KO
                else -> null
            }
        }

        /** The first of the device's languages, in the user's order, that is one of these; else [FALLBACK] (F-1). */
        fun resolve(deviceTags: List<String>): AppLanguage = deviceTags.firstNotNullOfOrNull { of(it) } ?: FALLBACK

        private fun instructionFor(language: String, names: String) =
            "答えはすべて${language}で話す。指示・資料・地図の情報が日本語でも、話す言葉は${language}だけにする。" +
                "スポット・寺社・催しなどの固有名詞は元の日本語の名前を残して${names}、初めて出すときは短く意味を添える。" +
                "長さは、指示にある字数を日本語で読み上げたときと同じくらいの時間で読み終える長さにする。" +
                "「1：」「続き：」のような印は、指示どおりの形のまま使う。"
    }
}
