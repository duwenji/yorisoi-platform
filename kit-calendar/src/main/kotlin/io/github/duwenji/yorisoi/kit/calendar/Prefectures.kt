package io.github.duwenji.yorisoi.kit.calendar

/**
 * The 47 prefectures by their numbers (01 北海道 … 47 沖縄) and which border which (ADR-AGENT A-12:
 * `season_obs` gives the prefecture one is in and the ones next to it). Next to means a land border, or a
 * crossing one may travel by road or rail: 青函トンネル, 東京湾アクアライン, 明石海峡・大鳴門橋, 瀬戸大橋,
 * しまなみ海道, 関門. 沖縄 has none.
 */
object Prefectures {
    private val BORDERS = mapOf(
        1 to listOf(2), 2 to listOf(3, 5), 3 to listOf(4, 5), 4 to listOf(5, 6, 7), 5 to listOf(6), 6 to listOf(7, 15),
        7 to listOf(8, 9, 10, 15), 8 to listOf(9, 11, 12), 9 to listOf(10, 11), 10 to listOf(11, 15, 20),
        11 to listOf(12, 13, 19, 20), 12 to listOf(13, 14), 13 to listOf(14, 19), 14 to listOf(19, 22),
        15 to listOf(16, 20), 16 to listOf(17, 20, 21), 17 to listOf(18, 21), 18 to listOf(21, 25, 26),
        19 to listOf(20, 22), 20 to listOf(21, 22, 23), 21 to listOf(23, 24, 25), 22 to listOf(23), 23 to listOf(24),
        24 to listOf(25, 26, 29, 30), 25 to listOf(26), 26 to listOf(27, 28, 29), 27 to listOf(28, 29, 30),
        28 to listOf(31, 33, 36), 29 to listOf(30), 31 to listOf(32, 33, 34), 32 to listOf(34, 35),
        33 to listOf(34, 37), 34 to listOf(35, 38), 35 to listOf(40), 36 to listOf(37, 38, 39), 37 to listOf(38),
        38 to listOf(39), 40 to listOf(41, 43, 44), 41 to listOf(42), 43 to listOf(44, 45, 46), 44 to listOf(45),
        45 to listOf(46),
    )

    private val neighbors: Map<String, Set<String>> by lazy {
        val m = HashMap<String, MutableSet<String>>()
        for (n in 1..47) m[code(n)] = HashSet()
        for ((a, bs) in BORDERS) for (b in bs) { m.getValue(code(a)) += code(b); m.getValue(code(b)) += code(a) }
        m
    }

    private fun code(n: Int) = n.toString().padStart(2, '0')

    /** The prefectures next to [code] (「18」 for 福井), without it; empty for an unknown code. */
    fun neighborsOf(code: String): Set<String> = neighbors[code] ?: emptySet()

    /** The prefecture of a 全国地方公共団体コード（6 digits）: its first two. */
    fun ofMunicipality(municipality: String): String = municipality.take(2)
}
