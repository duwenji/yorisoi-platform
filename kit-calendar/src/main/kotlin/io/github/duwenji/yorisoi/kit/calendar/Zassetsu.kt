package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/** The seasonal days (雑節) of a year, from the solar terms and the Sun's longitude (REQ-KOYOMI「暦の種類」). */
internal object Zassetsu {
    private val years = ConcurrentHashMap<Int, List<Event>>()

    fun of(year: Int): List<Event> = years.getOrPut(year) {
        fun term(t: SolarTerm) = SolarTerms.dateOf(t, year)
        fun sun(lon: Double) = Astro.dateOf(Astro.sunAt(lon, year))
        fun day(key: String, name: String, reading: String, on: LocalDate, note: String, end: LocalDate = on) =
            Event(KoyomiKind.ZASSETSU, key, name, on, end, note, reading)
        val risshun = term(SolarTerm.RISSHUN)
        val shunbun = term(SolarTerm.SHUNBUN)
        val shuubun = term(SolarTerm.SHUUBUN)
        val doyou = "立春・立夏・立秋・立冬の前のおよそ 18 日間。季節の変わり目とされる"
        listOf(
            day("doyou-winter", "冬の土用", "ふゆのどよう", sun(297.0), doyou, risshun.minusDays(1)),
            day("setsubun", "節分", "せつぶん", risshun.minusDays(1), "立春の前の日。豆をまいて邪気を払う習わしがある"),
            day("higan-spring", "春の彼岸", "はるのひがん", shunbun.minusDays(3), "春分の日を中日とする 7 日間。墓参りをする習わしがある", shunbun.plusDays(3)),
            day("shanichi-spring", "社日", "しゃにち", nearestTsuchinoe(shunbun), "春分・秋分にいちばん近い戊（つちのえ）の日。土地の神をまつる日とされる"),
            day("doyou-spring", "春の土用", "はるのどよう", sun(27.0), doyou, term(SolarTerm.RIKKA).minusDays(1)),
            day("hachijuhachiya", "八十八夜", "はちじゅうはちや", risshun.plusDays(87), "立春から数えて 88 日目。茶摘みや種まきの目安とされてきた"),
            day("nyubai", "入梅", "にゅうばい", sun(80.0), "暦の上で梅雨に入る日"),
            day("hangesho", "半夏生", "はんげしょう", sun(100.0), "田植えを終える目安とされてきた日"),
            day("doyou-summer", "夏の土用", "なつのどよう", sun(117.0), "$doyou。夏の土用の丑の日にうなぎを食べる習わしがある", term(SolarTerm.RISSHUU).minusDays(1)),
            day("nihyakutoka", "二百十日", "にひゃくとおか", risshun.plusDays(209), "立春から数えて 210 日目。台風の来やすい日として、農家が気をつけてきた"),
            day("nihyakuhatsuka", "二百二十日", "にひゃくはつか", risshun.plusDays(219), "立春から数えて 220 日目。二百十日と並んで、台風に気をつける日とされてきた"),
            day("higan-autumn", "秋の彼岸", "あきのひがん", shuubun.minusDays(3), "秋分の日を中日とする 7 日間。墓参りをする習わしがある", shuubun.plusDays(3)),
            day("shanichi-autumn", "社日", "しゃにち", nearestTsuchinoe(shuubun), "春分・秋分にいちばん近い戊（つちのえ）の日。土地の神をまつる日とされる"),
            day("doyou-autumn", "秋の土用", "あきのどよう", sun(207.0), doyou, term(SolarTerm.RITTOU).minusDays(1)),
        )
    }

    /** The 戊 day nearest [day]; of two as near, the one before (REQ-KOYOMI「暦の種類」zassetsu). */
    private fun nearestTsuchinoe(day: LocalDate): LocalDate =
        (-5L..5L).map { day.plusDays(it) }.filter { Kanshi.stem(it) == 4 }
            .minWith(compareBy<LocalDate> { Math.abs(ChronoUnit.DAYS.between(day, it)) }.thenBy { it })
}
