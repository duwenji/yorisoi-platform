package io.github.duwenji.yorisoi.kit.voice

import io.github.duwenji.yorisoi.core.text.Words

/** Background sounds, synthesized on the phone (see [Voices]). Which one plays is the app's choice. */
enum class Soundscape(private val words: Words) {
    RAIN(Words("雨音", "Rain", "雨声", "빗소리")),
    WAVES(Words("波の音", "Waves", "海浪声", "파도 소리")),
    WIND(Words("風の音", "Wind", "风声", "바람 소리")),
    BIRDS(Words("鳥の声", "Birds", "鸟鸣", "새소리")),
    INSECTS(Words("虫の音", "Insects", "虫鸣", "벌레 소리")),
    TEMPLE(Words("遠くの鐘", "A distant bell", "远处的钟声", "먼 종소리")),
    ;

    /** The sound's name, in the app's language. */
    val label: String get() = words.text
}
