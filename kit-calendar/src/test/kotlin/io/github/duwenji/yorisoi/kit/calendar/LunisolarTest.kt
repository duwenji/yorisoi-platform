package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LunisolarTest {
    @Test
    fun `十五夜 of 2016-2027 is the 15th of the 8th month (国立天文台の暦要項の朔と秋分から)`() {
        val meigetsu = listOf("2016-09-15", "2017-10-04", "2018-09-24", "2019-09-13", "2020-10-01", "2021-09-21",
            "2022-09-10", "2023-09-29", "2024-09-17", "2025-10-06", "2026-09-25", "2027-09-15").map(LocalDate::parse)
        for (d in meigetsu) {
            assertEquals("$d", LunarDate(8, 15, false), Lunisolar.of(d))
            assertEquals(d, Lunisolar.dateOf(d.year, 8, 15))
        }
    }

    @Test
    fun `a month without 中気 is the leap one of the month before (2025 閏6月, 2023 閏2月)`() {
        assertEquals(LunarDate(6, 8, true), Lunisolar.of(LocalDate.of(2025, 8, 1)))
        assertEquals(true, Lunisolar.of(LocalDate.of(2023, 4, 1)).leap)
        assertEquals(2, Lunisolar.of(LocalDate.of(2023, 4, 1)).month)
    }

    @Test
    fun `15 November 2026 is the 7th of the 10th month, 仏滅 (朔 9 November, 小雪 22 November)`() {
        assertEquals(LunarDate(10, 7, false), Lunisolar.of(LocalDate.of(2026, 11, 15)))
        assertEquals(Rokuyou.BUTSUMETSU, Rokuyou.of(LocalDate.of(2026, 11, 15)))
        // The 1st of a month is 先勝 in the 1st and 7th months, and so on in turn.
        assertEquals(Rokuyou.SENSHOU, Rokuyou.of(Lunisolar.dateOf(2026, 1, 1)!!))
        assertEquals(Rokuyou.TOMOBIKI, Rokuyou.of(Lunisolar.dateOf(2026, 2, 1)!!))
    }

    @Test
    fun `the sexagenary day - 1 January 2024 was 甲子, 1 January 2000 戊午`() {
        assertEquals(0, Kanshi.index(LocalDate.of(2024, 1, 1)))
        assertEquals(54, Kanshi.index(LocalDate.of(2000, 1, 1)))
        assertEquals(4, Kanshi.stem(LocalDate.of(2000, 1, 1)))
        assertEquals(6, Kanshi.branch(LocalDate.of(2000, 1, 1)))
    }
}
