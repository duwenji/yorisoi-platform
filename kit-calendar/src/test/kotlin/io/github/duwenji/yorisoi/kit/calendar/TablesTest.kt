package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TablesTest {
    @Test
    fun `72 pentads, 立春 first, each with its reading and meaning`() {
        assertEquals(72, Tables.KOU.size)
        assertEquals("東風解凍", Tables.KOU[0].name)
        assertEquals("山茶始開", Tables.KOU[54].name)
        assertEquals("地始凍", Tables.KOU[55].name)
        assertEquals("鶏始乳", Tables.KOU[71].name)
        Tables.KOU.forEach { assertTrue(it.name, it.reading.isNotBlank() && it.note.isNotBlank()) }
    }

    @Test
    fun `designated days - who set them and where that was checked, two a month or more (AC-3, D-5)`() {
        Tables.KINENBI.forEach {
            assertTrue(it.name, it.designatedBy.isNotBlank() && it.note.isNotBlank() && it.source.startsWith("https://"))
        }
        for (m in 1..12) assertTrue("month $m", Tables.KINENBI.count { it.month == m } >= 2)
    }

    @Test
    fun `regional days say where and from what source (AC-3, D-6)`() {
        Tables.REGIONAL.forEach { assertTrue(it.name, it.regions.isNotBlank() && it.source.startsWith("https://")) }
        val r = Tables.regional(2026).associateBy { it.key }
        assertEquals(LocalDate.of(2026, 7, 13), r.getValue("bon-july").start)
        assertEquals(LocalDate.of(2026, 8, 16), r.getValue("bon-august").end)
        assertEquals(10, Lunisolar.of(r.getValue("inoko").start).month)
        assertEquals(11, Kanshi.branch(r.getValue("inoko").start))
    }

    @Test
    fun `十五夜 and 十三夜, the festivals of the year`() {
        val lunar = Tables.lunar(2026).associate { it.key to it.start }
        assertEquals(LocalDate.of(2026, 9, 25), lunar.getValue("jugoya"))
        assertEquals(LunarDate(9, 13, false), Lunisolar.of(lunar.getValue("jusanya")))
        assertEquals(LocalDate.of(2026, 11, 15), Tables.sekku(2026).single { it.key == "shichigosan" }.start)
    }
}
