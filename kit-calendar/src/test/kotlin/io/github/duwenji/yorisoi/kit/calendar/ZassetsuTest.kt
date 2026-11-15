package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ZassetsuTest {
    private fun starts(year: Int) = Zassetsu.of(year).associate { it.key to it.start }

    /** 暦要項 2025–2027, 雑節 (the days given; 土用 is when it begins). */
    @Test
    fun `the seasonal days of 2025-2027 are 暦要項's (AC-1)`() {
        val rekiyou = mapOf(
            2025 to listOf("doyou-winter" to "01-17", "setsubun" to "02-02", "higan-spring" to "03-17", "doyou-spring" to "04-17", "hachijuhachiya" to "05-01",
                "nyubai" to "06-11", "hangesho" to "07-01", "doyou-summer" to "07-19", "nihyakutoka" to "08-31", "higan-autumn" to "09-20", "doyou-autumn" to "10-20"),
            2026 to listOf("doyou-winter" to "01-17", "setsubun" to "02-03", "higan-spring" to "03-17", "doyou-spring" to "04-17", "hachijuhachiya" to "05-02",
                "nyubai" to "06-11", "hangesho" to "07-02", "doyou-summer" to "07-20", "nihyakutoka" to "09-01", "higan-autumn" to "09-20", "doyou-autumn" to "10-20"),
            2027 to listOf("doyou-winter" to "01-17", "setsubun" to "02-03", "higan-spring" to "03-18", "doyou-spring" to "04-17", "hachijuhachiya" to "05-02",
                "nyubai" to "06-11", "hangesho" to "07-02", "doyou-summer" to "07-20", "nihyakutoka" to "09-01", "higan-autumn" to "09-20", "doyou-autumn" to "10-21"),
        )
        for ((y, days) in rekiyou) {
            val ours = starts(y)
            for ((key, md) in days) assertEquals("$key $y", LocalDate.parse("$y-$md"), ours.getValue(key))
        }
    }

    @Test
    fun `彼岸 is the 7 days around the equinox, and 土用 ends the day before the next 立`() {
        val e = Zassetsu.of(2026).associateBy { it.key }
        assertEquals(LocalDate.of(2026, 3, 23), e.getValue("higan-spring").end)
        assertEquals(LocalDate.of(2026, 8, 6), e.getValue("doyou-summer").end)
        assertEquals(LocalDate.of(2026, 2, 3), e.getValue("doyou-winter").end)
    }

    @Test
    fun `社日 is the 戊 day nearest the equinox, and 二百二十日 ten days after 二百十日`() {
        for (y in 2016..2027) {
            val e = Zassetsu.of(y).associateBy { it.key }
            for ((key, term) in listOf("shanichi-spring" to SolarTerm.SHUNBUN, "shanichi-autumn" to SolarTerm.SHUUBUN)) {
                val day = e.getValue(key).start
                assertEquals("$key $y", 4, Kanshi.stem(day))
                assertTrue("$key $y", Math.abs(ChronoUnit.DAYS.between(SolarTerms.dateOf(term, y), day)) <= 5)
            }
            assertEquals(e.getValue("nihyakutoka").start.plusDays(10), e.getValue("nihyakuhatsuka").start)
        }
    }
}
