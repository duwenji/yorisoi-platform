package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

/**
 * The six days of the old calendar's notes (六曜), in the turn of (month + day) mod 6 (REQ-KOYOMI D-4).
 * The notes say what was said of each, as said; none of them says to do or avoid anything.
 */
enum class Rokuyou(val key: String, val label: String, val reading: String, val note: String) {
    TAIAN("taian", "大安", "たいあん", "六曜でいちばん吉とされてきた日"),
    SHAKKOU("shakkou", "赤口", "しゃっこう", "正午ごろだけが吉で、ほかは凶とされてきた日"),
    SENSHOU("senshou", "先勝", "せんしょう", "「先んずれば勝つ」とされ、午前が吉と言われてきた日"),
    TOMOBIKI("tomobiki", "友引", "ともびき", "もとは「共引」で、勝負がつかない日とされてきた"),
    SENBU("senbu", "先負", "せんぶ", "「先んずれば負ける」とされ、午後が吉と言われてきた日"),
    BUTSUMETSU("butsumetsu", "仏滅", "ぶつめつ", "六曜でいちばん凶とされてきた日。名前に「仏」とあるが、仏教とは関係がない"),
    ;

    companion object {
        fun of(date: LocalDate): Rokuyou = Lunisolar.of(date).let { entries[(it.month + it.day) % 6] }
    }
}
