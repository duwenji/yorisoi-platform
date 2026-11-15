package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KoyomiTest {
    private val koyomi = Koyomi()
    private fun on(y: Int, m: Int, d: Int) = koyomi.on(LocalDate.of(y, m, d))
    private fun List<KoyomiFact>.of(kind: KoyomiKind) = filter { it.kind == kind }

    @Test
    fun `15 November 2026 - 立冬 9th day, 地始凍, 七五三 today, 仏滅 (AC-2)`() {
        val facts = on(2026, 11, 15)
        val sekki = facts.of(KoyomiKind.SEKKI).single()
        assertEquals("立冬", sekki.name)
        assertEquals(Relation.DayOf(9), sekki.relation)
        assertEquals("sekki:rittou:2026-11-07", sekki.id)
        assertEquals("地始凍", facts.of(KoyomiKind.KOU).single().name)
        assertEquals(Relation.Today, facts.of(KoyomiKind.SEKKU).single { it.name == "七五三" }.relation)
        assertEquals("仏滅", facts.of(KoyomiKind.ROKUYOU).single().name)
        assertEquals(Certainty.TRADITION, facts.of(KoyomiKind.ROKUYOU).single().certainty)
    }

    @Test
    fun `小雪 is 1 day ahead on the 21st and today on the 22nd (AC-2)`() {
        assertEquals(Relation.DaysAhead(1), on(2026, 11, 21).of(KoyomiKind.SEKKI).single { it.name == "小雪" }.relation)
        val today = on(2026, 11, 22).of(KoyomiKind.SEKKI)
        assertEquals(listOf("小雪"), today.map { it.name })
        assertEquals(Relation.Today, today.single().relation)
    }

    @Test
    fun `the first days of a year are counted from the last 冬至`() {
        val sekki = on(2027, 1, 1).of(KoyomiKind.SEKKI).single()
        assertEquals("冬至", sekki.name)
        assertEquals(Relation.DayOf(11), sekki.relation)
    }

    @Test
    fun `days ahead reach into the next year - 元日 is 2 days ahead on 30 December`() {
        val holiday = on(2026, 12, 30).of(KoyomiKind.HOLIDAY).single { it.name == "元日" }
        assertEquals(Relation.DaysAhead(2), holiday.relation)
        assertEquals(LocalDate.of(2027, 1, 1), holiday.date)
    }

    @Test
    fun `a period counts its days, a past one-day thing is yesterday, a designated day only today`() {
        val higan = on(2026, 3, 19).of(KoyomiKind.ZASSETSU).single { it.name == "春の彼岸" }
        assertEquals(Relation.DayOf(3), higan.relation)
        assertEquals(Relation.Yesterday, on(2026, 11, 16).of(KoyomiKind.SEKKU).single { it.name == "七五三" }.relation)
        assertTrue(on(2026, 9, 2).of(KoyomiKind.KINENBI).isEmpty())
        val bousai = on(2026, 9, 1).of(KoyomiKind.KINENBI).single()
        assertEquals("内閣（閣議了解）", bousai.designatedBy)
        assertEquals(Certainty.DESIGNATED, bousai.certainty)
    }

    @Test
    fun `the moon only a day either side - full moon 24 November 2026`() {
        assertEquals(Relation.Today, on(2026, 11, 24).of(KoyomiKind.MOON).single().relation)
        assertEquals(Relation.DaysAhead(1), on(2026, 11, 23).of(KoyomiKind.MOON).single().relation)
        assertTrue(on(2026, 11, 21).of(KoyomiKind.MOON).isEmpty())
    }

    @Test
    fun `regional days say where (D-6)`() {
        val bon = on(2026, 8, 14).of(KoyomiKind.REGIONAL).single { it.name == "お盆（8 月）" }
        assertEquals(Relation.DayOf(2), bon.relation)
        assertEquals("全国の多くの地域", bon.regions)
    }

    @Test
    fun `a day of a year not worked out yet comes quickly (N-3)`() {
        // 2031 is used by no other test, so its terms, pentads, new moons and tables are all cold here.
        val started = System.nanoTime()
        Koyomi().on(LocalDate.of(2031, 6, 1))
        val ms = (System.nanoTime() - started) / 1_000_000
        // On a desktop JVM; a phone is several times slower, and N-3 asks 50 ms of it.
        assertTrue("$ms ms", ms < 40)
    }

    @Test
    fun `a day is worked out once (F-8)`() {
        assertSame(koyomi.on(LocalDate.of(2026, 11, 15)), koyomi.on(LocalDate.of(2026, 11, 15)))
    }
}
