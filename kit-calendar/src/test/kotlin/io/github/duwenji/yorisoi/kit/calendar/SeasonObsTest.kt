package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeasonObsTest {
    private val file = SeasonObsFile.parse(
        """{"season":"2027","generatedAt":"2026-11-22T03:00:00Z",
          "attribution":"出典：気象庁ホームページ（防災情報 XML「季節観測」「生物季節観測」）を加工して作成",
          "observations":[
            {"kind":"first-snow","date":"2026-11-22","place":"福井地方気象台","prefecture":"18","municipality":"182010","fromNormal":-11,"fromLastYear":-11,"source":"https://www.data.jma.go.jp/developer/xml/data/20261121212302_0_VGSK50_180000.xml"},
            {"kind":"first-snow","date":"2026-11-22","place":"福島地方気象台","prefecture":"07","municipality":"072010","fromNormal":3,"fromLastYear":3},
            {"kind":"phenology","name":"かえでの紅葉日","date":"2026-11-20","place":"奈良地方気象台","prefecture":"29","fromNormal":-1,"fromLastYear":6},
            {"kind":"phenology","name":"いちょうの黄葉日","date":"2026-11-18","place":"京都地方気象台","prefecture":"26","fromNormal":0,"fromLastYear":null},
            {"kind":"snow-cap","date":"2026-11-20","place":"白山","prefecture":"17"},
            {"kind":"first-frost","date":"2026-10-30","place":"金沢地方気象台","prefecture":"17","fromNormal":-2,"fromLastYear":1},
            {"kind":"first-thunder","date":"2026-11-21","place":"福井地方気象台","prefecture":"18"}
          ]}""",
    )

    @Test
    fun `the file is read and unknown kinds are left out`() {
        assertEquals("2027", file.season)
        assertEquals(6, file.observations.size)
    }

    @Test
    fun `the prefecture and its neighbors in the last 14 days, its own first`() {
        val got = file.around("18", LocalDate.of(2026, 11, 22))
        assertEquals(listOf("福井地方気象台", "白山", "京都地方気象台"), got.map { it.place })
        assertTrue(got.none { it.prefecture == "07" || it.prefecture == "29" })
        assertTrue("older than 14 days", got.none { it.kind == ObsKind.FIRST_FROST })
    }

    @Test
    fun `facts keep the values as given`() {
        val obs = file.observations
        assertEquals("福井地方気象台で 11月22日に初雪を観測、平年より 11 日早い、昨年より 11 日早い", obs[0].fact())
        assertEquals("福島地方気象台で 11月22日に初雪を観測、平年より 3 日遅い、昨年より 3 日遅い", obs[1].fact())
        assertEquals("京都地方気象台で 11月18日にいちょうの黄葉日を観測、平年並み", obs[3].fact())
        assertEquals("11月20日に白山の初冠雪を確認", obs[4].fact())
        assertTrue(obs.all { it.fact().length <= 60 })
        // With the day, how it stands to it: the speaker took yesterday's first snow for today's.
        assertEquals("福井地方気象台で 昨日 11月22日に初雪を観測、平年より 11 日早い、昨年より 11 日早い", obs[0].fact(java.time.LocalDate.of(2026, 11, 23)))
        assertEquals("京都地方気象台で 5日前の 11月18日にいちょうの黄葉日を観測、平年並み", obs[3].fact(java.time.LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun `borders are symmetric and every prefecture but Okinawa has one`() {
        for (n in 1..47) {
            val c = n.toString().padStart(2, '0')
            for (o in Prefectures.neighborsOf(c)) assertTrue("$c-$o", c in Prefectures.neighborsOf(o))
            if (c != "47") assertTrue(c, Prefectures.neighborsOf(c).isNotEmpty())
        }
        assertEquals(setOf("17", "21", "25", "26"), Prefectures.neighborsOf("18"))
        assertTrue(Prefectures.neighborsOf("47").isEmpty())
        assertEquals("18", Prefectures.ofMunicipality("182010"))
    }
}
