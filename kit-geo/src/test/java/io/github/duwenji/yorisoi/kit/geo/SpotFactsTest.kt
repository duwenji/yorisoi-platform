package io.github.duwenji.yorisoi.kit.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotFactsTest {
    @Test
    fun `the Great Buddha's tags read as a card and a sheet`() {
        val facts = SpotFacts.of(
            mapOf(
                "name" to "鎌倉大仏", "name:ja-Hira" to "かまくらだいぶつ", "name:en" to "Great Buddha of Kamakura",
                "description" to "Cast bronze statue of Buddha, 11.3m in height without the pedestal.",
                "start_date" to "1252", "historic:period" to "kamakura", "heritage" to "2", "heritage:type" to "国宝",
                "fee" to "yes", "charge" to "50 JPY", "wikipedia" to "ja:鎌倉大仏",
            ),
        )
        assertEquals("かまくらだいぶつ", facts.reading)
        assertEquals(listOf("鎌倉時代", "国宝"), facts.summary)
        assertEquals(
            listOf(
                SpotFact("創建・設置", "1252年"),
                SpotFact("時代", "鎌倉時代"),
                SpotFact("文化財", "国宝"),
                SpotFact("料金", "有料（50円）"),
            ),
            facts.details,
        )
        assertEquals(
            listOf(SpotLink("Wikipedia「鎌倉大仏」", "https://ja.wikipedia.org/wiki/%E9%8E%8C%E5%80%89%E5%A4%A7%E4%BB%8F")),
            facts.links,
        )
    }

    @Test
    fun `a temple names its school, and an unknown school is left out`() {
        val temple = SpotFacts.of(mapOf("religion" to "buddhist", "denomination" to "jodo_shu", "website" to "https://www.example.jp/"))
        assertEquals(listOf("仏教（浄土宗）"), temple.summary)
        assertEquals(listOf(SpotLink("公式サイト（example.jp）", "https://www.example.jp/")), temple.links)
        assertEquals(listOf("神道"), SpotFacts.of(mapOf("religion" to "shinto", "denomination" to "KCN")).summary)
        assertEquals(listOf("神道", "創建 1063年"), SpotFacts.of(mapOf("religion" to "shinto", "start_date" to "1063")).summary)
    }

    @Test
    fun `opening hours read in Japanese, or as they are when they can't`() {
        assertEquals("火〜日 9:00〜16:30", SpotFacts.openingHours("Tu-Su 09:00-16:30"))
        assertEquals("9:00〜17:30", SpotFacts.openingHours("09:00-17:30"))
        assertEquals("24時間", SpotFacts.openingHours("24/7"))
        assertEquals("毎日 6:00〜20:00", SpotFacts.openingHours("Mo-Su 06:00-20:00"))
        assertEquals("月〜金 9:00〜12:00、13:00〜17:00、土・日・祝日 休み", SpotFacts.openingHours("Mo-Fr 09:00-12:00,13:00-17:00; Sa,Su,PH off"))
        assertEquals("Mo-Fr 09:00-17:00 \"予約制\"", SpotFacts.openingHours("Mo-Fr 09:00-17:00 \"予約制\""))
        assertEquals("sunrise-sunset", SpotFacts.openingHours("sunrise-sunset"))
    }

    @Test
    fun `start dates read as years, centuries or the era name given`() {
        assertEquals("1924年3月", SpotFacts.startDate("1924-03"))
        assertEquals("1949年6月1日", SpotFacts.startDate("1949-06-01"))
        assertEquals("13世紀", SpotFacts.startDate("C13"))
        assertEquals("1200年ごろ", SpotFacts.startDate("~1200"))
        assertEquals("弘長二年十一月廿日", SpotFacts.startDate("ja:弘長二年十一月廿日"))
    }

    @Test
    fun `heritage says the kind of designation, or what its level means`() {
        assertEquals("国登録有形文化財・鎌倉市景観重要建築物等", SpotFacts.of(mapOf("heritage" to "3; 7", "heritage:type" to "国登録有形文化財; 鎌倉市景観重要建築物等")).summary.single())
        assertEquals("世界遺産", SpotFacts.of(mapOf("heritage" to "1")).summary.single())
        assertEquals("指定あり", SpotFacts.of(mapOf("heritage" to "7")).summary.single())
    }

    @Test
    fun `charges in other forms, and free entry`() {
        assertEquals(SpotFact("料金", "有料（400円・150円）"), SpotFacts.of(mapOf("charge" to "400¥;150¥")).details.single())
        assertEquals(SpotFact("料金", "無料"), SpotFacts.of(mapOf("fee" to "no")).details.single())
    }

    @Test
    fun `a Japanese description is shown, an English one is not`() {
        assertEquals(SpotFact("説明", "都旧跡"), SpotFacts.of(mapOf("description" to "都旧跡")).details.single())
        assertEquals(
            SpotFact("説明", "金属製キリンの彫刻"),
            SpotFacts.of(mapOf("description" to "A metal kirin.", "description:ja" to "金属製キリンの彫刻")).details.single(),
        )
        assertTrue(SpotFacts.of(mapOf("description" to "Built in 1717, this ornate vermilion gate...")).isEmpty)
    }

    @Test
    fun `nothing but http pages is opened from map data, and plain spots have nothing`() {
        assertTrue(SpotFacts.of(mapOf("website" to "javascript:alert(1)")).links.isEmpty())
        val bench = SpotFacts.of(mapOf("amenity" to "bench", "operator" to "鎌倉市"))
        assertTrue(bench.isEmpty)
        assertNull(bench.reading)
    }
}
