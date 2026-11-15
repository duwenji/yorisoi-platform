package io.github.duwenji.yorisoi.kit.calendar

import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** The 24 solar terms in the order of a year, 小寒 first; [note] is the line told with it (REQ-KOYOMI D-3). */
enum class SolarTerm(val key: String, val label: String, val reading: String, val longitude: Int, val note: String) {
    SHOUKAN("shoukan", "小寒", "しょうかん", 285, "寒の入り。ここから節分までが「寒の内」で、寒さが厳しくなるころ"),
    DAIKAN("daikan", "大寒", "だいかん", 300, "一年でいちばん寒さが厳しいころとされる"),
    RISSHUN("risshun", "立春", "りっしゅん", 315, "暦の上で春が始まる日。八十八夜や二百十日は、この日から数える"),
    USUI("usui", "雨水", "うすい", 330, "雪が雨に変わり、氷が解けて水になるころとされる"),
    KEICHITSU("keichitsu", "啓蟄", "けいちつ", 345, "冬ごもりの虫が土の中から出てくるころとされる"),
    SHUNBUN("shunbun", "春分", "しゅんぶん", 0, "昼と夜の長さがほぼ同じになる日。春の彼岸の中日"),
    SEIMEI("seimei", "清明", "せいめい", 15, "万物がすがすがしく明るく美しいころとされる"),
    KOKUU("kokuu", "穀雨", "こくう", 30, "穀物を潤す春の雨が降るころとされる"),
    RIKKA("rikka", "立夏", "りっか", 45, "暦の上で夏が始まる日"),
    SHOUMAN("shouman", "小満", "しょうまん", 60, "草木が茂り、天地に生気が満ち始めるころとされる"),
    BOUSHU("boushu", "芒種", "ぼうしゅ", 75, "稲や麦など、穂の出る穀物の種をまくころとされる"),
    GESHI("geshi", "夏至", "げし", 90, "一年で昼がいちばん長い日"),
    SHOUSHO("shousho", "小暑", "しょうしょ", 105, "本格的な暑さが始まるころ。暑中見舞いはこのころから"),
    TAISHO("taisho", "大暑", "たいしょ", 120, "一年でいちばん暑さが厳しいころとされる"),
    RISSHUU("risshuu", "立秋", "りっしゅう", 135, "暦の上で秋が始まる日。これより後の暑さは残暑と呼ぶ"),
    SHOSHO("shosho", "処暑", "しょしょ", 150, "暑さがおさまり始めるころとされる"),
    HAKURO("hakuro", "白露", "はくろ", 165, "草花に朝露がつき始めるころとされる"),
    SHUUBUN("shuubun", "秋分", "しゅうぶん", 180, "昼と夜の長さがほぼ同じになる日。秋の彼岸の中日"),
    KANRO("kanro", "寒露", "かんろ", 195, "草木に冷たい露が降りるころとされる"),
    SOUKOU("soukou", "霜降", "そうこう", 210, "朝晩に霜が降り始めるころとされる"),
    RITTOU("rittou", "立冬", "りっとう", 225, "暦の上で冬が始まる日"),
    SHOUSETSU("shousetsu", "小雪", "しょうせつ", 240, "わずかに雪が降り始めるころとされる"),
    TAISETSU("taisetsu", "大雪", "たいせつ", 255, "雪が本格的に降り始めるころとされる"),
    TOUJI("touji", "冬至", "とうじ", 270, "一年で昼がいちばん短い日。柚子湯に入り、かぼちゃを食べる習わしがある"),
    ;

    /** The 12 terms on a multiple of 30°: a lunar month is named by the one it holds (F-5). */
    val isChuki: Boolean get() = longitude % 30 == 0
}

object SolarTerms {
    private val years = ConcurrentHashMap<Int, List<Pair<SolarTerm, Instant>>>()

    /** The 24 terms of [year] at their moments, 小寒 in January to 冬至 in December. */
    fun of(year: Int): List<Pair<SolarTerm, Instant>> = years.getOrPut(year) {
        SolarTerm.entries.zip(Astro.sunSteps(285.0, 15.0, 24, Astro.startOf(year)))
    }

    fun dateOf(term: SolarTerm, year: Int): LocalDate = Astro.dateOf(of(year).first { it.first == term }.second)
}
