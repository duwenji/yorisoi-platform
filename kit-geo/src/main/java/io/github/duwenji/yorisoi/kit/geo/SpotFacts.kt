package io.github.duwenji.yorisoi.kit.geo

import java.net.URI
import java.net.URLEncoder

/** One line of what is known about a spot: [label] beside [value]. */
data class SpotFact(val label: String, val value: String)

/** A page about the spot to open. */
data class SpotLink(val label: String, val url: String)

/**
 * What a spot's OpenStreetMap tags say, in the user's words, for its card and sheet: there before
 * any guide is written, and without the AI. Values are rewritten to read naturally (`Tu-Su
 * 09:00-16:30` → 「火〜日 9:00〜16:30」, `C13` → 「13世紀」); a value that can't be is shown as
 * it is, and one a Japanese reader can't read (an English description) is left out.
 *
 * - [reading]: the name's reading, for under the name
 * - [summary]: a few words for a one-line card (religion, period or founding, heritage)
 * - [details]: the lines for a sheet, most telling first
 * - [links]: Wikipedia and the official site
 */
data class SpotFacts(
    val reading: String?,
    val summary: List<String>,
    val details: List<SpotFact>,
    val links: List<SpotLink>,
) {
    val isEmpty: Boolean get() = reading == null && details.isEmpty() && links.isEmpty()

    companion object {
        /** The tags [of] reads; an app that keeps spots for later can keep only these. */
        val KEYS = setOf(
            "name:ja-Hira", "name:ja_kana", "name:ja-Kana", "description:ja", "description",
            "religion", "denomination", "start_date", "historic:period", "heritage", "heritage:type",
            "heritage:operator", "artist_name", "architect", "opening_hours", "fee", "charge",
            "inscription", "wikipedia", "website", "contact:website",
        )

        fun of(tags: Map<String, String>): SpotFacts {
            val religion = religion(tags)
            val period = tags["historic:period"]?.let { PERIODS[it] }
            val start = tags["start_date"]?.let(::startDate)
            val heritage = heritage(tags)
            val details = buildList {
                description(tags)?.let { add(SpotFact("説明", it)) }
                start?.let { add(SpotFact("創建・設置", it)) }
                period?.let { add(SpotFact("時代", it)) }
                religion?.let { add(SpotFact("宗教", it)) }
                heritage?.let { add(SpotFact("文化財", it)) }
                tags["artist_name"]?.let { add(SpotFact("作者", list(it))) }
                tags["architect"]?.let { add(SpotFact("設計", list(it))) }
                tags["opening_hours"]?.let { add(SpotFact("開いている時間", openingHours(it))) }
                fee(tags)?.let { add(SpotFact("料金", it)) }
                tags["inscription"]?.let { add(SpotFact("碑文", it.trim())) }
            }
            return SpotFacts(
                reading = tags["name:ja-Hira"] ?: tags["name:ja_kana"] ?: tags["name:ja-Kana"],
                summary = listOfNotNull(religion, period ?: start?.let { "創建 $it" }, heritage),
                details = details,
                links = listOfNotNull(wikipedia(tags["wikipedia"]), website(tags["website"] ?: tags["contact:website"])),
            )
        }

        private fun hasJapanese(s: String) = s.any { it in '぀'..'ヿ' || it in '一'..'鿿' }

        /** Several values in one tag (`佐野昭;鈴木長吉`). */
        private fun list(value: String) = value.split(';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("・")

        private fun description(tags: Map<String, String>): String? =
            (tags["description:ja"] ?: tags["description"])?.trim()?.takeIf(::hasJapanese)

        private fun religion(tags: Map<String, String>): String? {
            val religion = RELIGIONS[tags["religion"]] ?: return null
            val denomination = DENOMINATIONS[tags["denomination"]]?.takeIf { it != religion } ?: return religion
            return "$religion（$denomination）"
        }

        /** `1252` → 1252年, `1924-03` → 1924年3月, `C13` → 13世紀, `~1200` → 1200年ごろ; an era name (`ja:明徳四年`) as it is. */
        internal fun startDate(value: String): String {
            val v = value.trim().removePrefix("ja:")
            Regex("""(\d{3,4})(?:-(\d{2}))?(?:-(\d{2}))?""").matchEntire(v)?.let { m ->
                val (y, mo, d) = m.destructured
                return "${y.toInt()}年" + (mo.takeIf { it.isNotEmpty() }?.let { "${it.toInt()}月" } ?: "") +
                    (d.takeIf { it.isNotEmpty() }?.let { "${it.toInt()}日" } ?: "")
            }
            Regex("""C(\d{1,2})""").matchEntire(v)?.let { return "${it.groupValues[1]}世紀" }
            Regex("""(?:~|ca\.\s*)(\d{3,4})""").matchEntire(v)?.let { return "${it.groupValues[1].toInt()}年ごろ" }
            return v
        }

        /** The kind of designation when given (国宝, 重要文化財...), else what the level says. */
        private fun heritage(tags: Map<String, String>): String? {
            val level = tags["heritage"] ?: return null
            tags["heritage:type"]?.let { return list(it) }
            return when (level.trim()) {
                "1" -> "世界遺産"
                "2" -> "国の指定"
                else -> "指定あり"
            }
        }

        /** `fee=no` → 無料; `charge=500 JPY` or `400¥;150¥` → 500円, 400円・150円. */
        private fun fee(tags: Map<String, String>): String? {
            val charge = tags["charge"]?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("・") { c ->
                Regex("""(\d[\d,]*)\s*(?:JPY|¥|円|yen)""", RegexOption.IGNORE_CASE).matchEntire(c)?.let { "${it.groupValues[1]}円" }
                    ?: Regex("""(?:¥|JPY)\s*(\d[\d,]*)""", RegexOption.IGNORE_CASE).matchEntire(c)?.let { "${it.groupValues[1]}円" }
                    ?: c
            }
            return when {
                !charge.isNullOrEmpty() -> "有料（$charge）"
                tags["fee"] == "yes" -> "有料"
                tags["fee"] == "no" -> "無料"
                else -> null
            }
        }

        /**
         * The common forms of OSM's `opening_hours` (days, then times or `off`, rules split by `;`;
         * `24/7`); anything else is shown as it is.
         */
        internal fun openingHours(value: String): String {
            val rules = value.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            val read = rules.map { rule -> readRule(rule) ?: return value.trim() }
            return read.joinToString("、")
        }

        private fun readRule(rule: String): String? {
            if (rule == "24/7") return "24時間"
            val parts = rule.split(Regex("""\s+"""), limit = 2)
            val (days, times) = if (parts.size == 2 && DAYS_SPEC.matches(parts[0])) {
                readDays(parts[0]) to parts[1]
            } else if (DAYS_SPEC.matches(rule)) {
                return null
            } else {
                null to rule
            }
            val hours = when (times.lowercase()) {
                "off", "closed" -> "休み"
                else -> times.split(',').map { span ->
                    val m = TIME_SPAN.matchEntire(span.trim()) ?: return null
                    "${clock(m.groupValues[1])}〜${clock(m.groupValues[2])}"
                }.joinToString("、")
            }
            return listOfNotNull(days, hours).joinToString(" ")
        }

        private fun readDays(spec: String): String =
            if (spec == "Mo-Su") "毎日" else spec.split(',').joinToString("・") { range -> range.split('-').joinToString("〜") { DAY_NAMES.getValue(it) } }

        private fun clock(hhmm: String): String = hhmm.split(':').let { (h, m) -> "${h.toInt()}:$m" }

        /** `ja:鶴岡八幡宮` opens that article; a title with no language is taken as Japanese. */
        private fun wikipedia(tag: String?): SpotLink? {
            if (tag.isNullOrBlank()) return null
            val (lang, title) = tag.split(":", limit = 2).takeIf { it.size == 2 && it[0].length in 2..3 }
                ?.let { it[0] to it[1].trim() } ?: ("ja" to tag.trim())
            val page = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "_")
            return SpotLink("Wikipedia「$title」", "https://$lang.wikipedia.org/wiki/$page")
        }

        /** Shown by its host; only http(s), so nothing else is opened from map data. */
        private fun website(tag: String?): SpotLink? {
            val url = tag?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
            val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: return null
            return SpotLink("公式サイト（$host）", url)
        }

        private val DAY_NAMES = mapOf(
            "Mo" to "月", "Tu" to "火", "We" to "水", "Th" to "木", "Fr" to "金", "Sa" to "土", "Su" to "日", "PH" to "祝日",
        )
        private val DAYS_SPEC = Regex("""(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)(?:[-,](?:Mo|Tu|We|Th|Fr|Sa|Su|PH))*""")
        private val TIME_SPAN = Regex("""(\d{1,2}:\d{2})-(\d{1,2}:\d{2})""")

        private val RELIGIONS = mapOf(
            "shinto" to "神道", "buddhist" to "仏教", "christian" to "キリスト教",
            "konkokyo" to "金光教", "tenrikyo" to "天理教",
        )
        private val DENOMINATIONS = mapOf(
            "jodo_shinshu" to "浄土真宗", "jodo" to "浄土宗", "jodo_shu" to "浄土宗", "nichiren" to "日蓮宗",
            "shingon" to "真言宗", "shingon_shu" to "真言宗", "tendai" to "天台宗", "tiantai" to "天台宗",
            "zen" to "禅宗", "soto" to "曹洞宗", "rinzai" to "臨済宗", "obaku" to "黄檗宗", "ji" to "時宗",
            "inari" to "稲荷", "hachiman" to "八幡",
            "catholic" to "カトリック", "roman_catholic" to "カトリック", "protestant" to "プロテスタント",
            "anglican" to "聖公会", "orthodox" to "正教会",
        )
        private val PERIODS = mapOf(
            "jomon" to "縄文時代", "yayoi" to "弥生時代", "kofun" to "古墳時代", "asuka" to "飛鳥時代",
            "nara" to "奈良時代", "heian" to "平安時代", "kamakura" to "鎌倉時代", "muromachi" to "室町時代",
            "sengoku" to "戦国時代", "azuchi-momoyama" to "安土桃山時代", "edo" to "江戸時代",
            "meiji" to "明治時代", "taisho" to "大正時代", "showa" to "昭和時代",
        )
    }
}
