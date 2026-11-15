package io.github.duwenji.yorisoi.kit.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * The national holidays of a year by the law (国民の祝日に関する法律) as it has stood since 2016, with
 * the years it was set aside (2019's enthronement, 2020's and 2021's moves for the Olympics). Each
 * note is the law's own words of what the day is for (第 2 条), or what makes the day off (第 3 条).
 */
internal object Holidays {
    private class Law(val name: String, val reading: String, val note: String)

    private val LAW = mapOf(
        "ganjitsu" to Law("元日", "がんじつ", "年のはじめを祝う。"),
        "seijin" to Law("成人の日", "せいじんのひ", "おとなになつたことを自覚し、みずから生き抜こうとする青年を祝いはげます。"),
        "kenkoku" to Law("建国記念の日", "けんこくきねんのひ", "建国をしのび、国を愛する心を養う。"),
        "tennou" to Law("天皇誕生日", "てんのうたんじょうび", "天皇の誕生日を祝う。"),
        "shunbun" to Law("春分の日", "しゅんぶんのひ", "自然をたたえ、生物をいつくしむ。"),
        "showa" to Law("昭和の日", "しょうわのひ", "激動の日々を経て、復興を遂げた昭和の時代を顧み、国の将来に思いをいたす。"),
        "kenpou" to Law("憲法記念日", "けんぽうきねんび", "日本国憲法の施行を記念し、国の成長を期する。"),
        "midori" to Law("みどりの日", "みどりのひ", "自然に親しむとともにその恩恵に感謝し、豊かな心をはぐくむ。"),
        "kodomo" to Law("こどもの日", "こどものひ", "こどもの人格を重んじ、こどもの幸福をはかるとともに、母に感謝する。"),
        "umi" to Law("海の日", "うみのひ", "海の恩恵に感謝するとともに、海洋国日本の繁栄を願う。"),
        "yama" to Law("山の日", "やまのひ", "山に親しむ機会を得て、山の恩恵に感謝する。"),
        "keirou" to Law("敬老の日", "けいろうのひ", "多年にわたり社会につくしてきた老人を敬愛し、長寿を祝う。"),
        "shuubun" to Law("秋分の日", "しゅうぶんのひ", "祖先をうやまい、なくなつた人々をしのぶ。"),
        "taiiku" to Law("体育の日", "たいいくのひ", "スポーツにしたしみ、健康な心身をつちかう。"),
        "sports" to Law("スポーツの日", "スポーツのひ", "スポーツを楽しみ、他者を尊重する精神を培うとともに、健康で活力ある社会の実現を願う。"),
        "bunka" to Law("文化の日", "ぶんかのひ", "自由と平和を愛し、文化をすすめる。"),
        "kinrou" to Law("勤労感謝の日", "きんろうかんしゃのひ", "勤労をたつとび、生産を祝い、国民たがいに感謝しあう。"),
        "sokui" to Law("天皇の即位の日", "てんのうのそくいのひ", "天皇の即位の日を休日とする法律（平成 30 年法律第 99 号）による休日。"),
        "sokuirei" to Law("即位礼正殿の儀の行われる日", "そくいれいせいでんのぎのおこなわれるひ", "即位礼正殿の儀の行われる日を休日とする法律（平成 30 年法律第 99 号）による休日。"),
        "furikae" to Law("振替休日", "ふりかえきゅうじつ", "国民の祝日が日曜日に当たったときの、その後のいちばん近い祝日でない日（祝日法第 3 条第 2 項）。"),
        "kokumin" to Law("国民の休日", "こくみんのきゅうじつ", "前の日と後の日が国民の祝日である日（祝日法第 3 条第 3 項）。"),
    )

    /** The days moved for the Olympics: 海の日, スポーツの日 and 山の日 (東京オリンピック特措法). */
    private val MOVED = mapOf(
        2020 to mapOf("umi" to LocalDate.of(2020, 7, 23), "sports" to LocalDate.of(2020, 7, 24), "yama" to LocalDate.of(2020, 8, 10)),
        2021 to mapOf("umi" to LocalDate.of(2021, 7, 22), "sports" to LocalDate.of(2021, 7, 23), "yama" to LocalDate.of(2021, 8, 8)),
    )

    private val years = ConcurrentHashMap<Int, List<Event>>()

    fun of(year: Int): List<Event> = years.getOrPut(year) {
        require(year >= 2016) { "Holidays are kept from 2016 on: $year" }
        val moved = MOVED[year].orEmpty()
        fun monday(month: Int, n: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val days = sortedMapOf<LocalDate, String>()
        fun add(key: String, day: LocalDate) { days[moved[key] ?: day] = key }
        add("ganjitsu", LocalDate.of(year, 1, 1))
        add("seijin", monday(1, 2))
        add("kenkoku", LocalDate.of(year, 2, 11))
        if (year <= 2018) add("tennou", LocalDate.of(year, 12, 23))
        if (year >= 2020) add("tennou", LocalDate.of(year, 2, 23))
        add("shunbun", SolarTerms.dateOf(SolarTerm.SHUNBUN, year))
        add("showa", LocalDate.of(year, 4, 29))
        add("kenpou", LocalDate.of(year, 5, 3))
        add("midori", LocalDate.of(year, 5, 4))
        add("kodomo", LocalDate.of(year, 5, 5))
        add("umi", monday(7, 3))
        add("yama", LocalDate.of(year, 8, 11))
        add("keirou", monday(9, 3))
        add("shuubun", SolarTerms.dateOf(SolarTerm.SHUUBUN, year))
        add(if (year >= 2020) "sports" else "taiiku", monday(10, 2))
        add("bunka", LocalDate.of(year, 11, 3))
        add("kinrou", LocalDate.of(year, 11, 23))
        if (year == 2019) {
            add("sokui", LocalDate.of(2019, 5, 1))
            add("sokuirei", LocalDate.of(2019, 10, 22))
        }
        // 振替休日: a holiday on a Sunday makes the next day that isn't one a day off (第 3 条第 2 項).
        for (day in days.keys.toList().filter { it.dayOfWeek == DayOfWeek.SUNDAY }) {
            var next = day.plusDays(1)
            while (next in days) next = next.plusDays(1)
            days[next] = "furikae"
        }
        // 国民の休日: a day between two national holidays, not one itself (第 3 条第 3 項). 振替休日 isn't one.
        for (day in days.keys.toList()) {
            val between = day.plusDays(1)
            val after = days[between.plusDays(1)]
            if (between !in days && after != null && after != "furikae" && days[day] != "furikae") {
                days[between] = "kokumin"
            }
        }
        days.map { (day, key) -> LAW.getValue(key).let { Event(KoyomiKind.HOLIDAY, key, it.name, day, note = it.note, reading = it.reading) } }
    }
}
