package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class SolarTermsTest {
    /** year, longitude, month, day, hour, minute: 暦要項 (国立天文台) 2025–2027, 二十四節気. */
    private val rekiyou = """
        2025 285 1 5 11 33|2025 300 1 20 5 0|2025 315 2 3 23 10|2025 330 2 18 19 7|2025 345 3 5 17 7|2025 0 3 20 18 1
        2025 15 4 4 21 49|2025 30 4 20 4 56|2025 45 5 5 14 57|2025 60 5 21 3 55|2025 75 6 5 18 57|2025 90 6 21 11 42
        2025 105 7 7 5 5|2025 120 7 22 22 29|2025 135 8 7 14 52|2025 150 8 23 5 34|2025 165 9 7 17 52|2025 180 9 23 3 19
        2025 195 10 8 9 41|2025 210 10 23 12 51|2025 225 11 7 13 4|2025 240 11 22 10 36|2025 255 12 7 6 5|2025 270 12 22 0 3
        2026 285 1 5 17 23|2026 300 1 20 10 45|2026 315 2 4 5 2|2026 330 2 19 0 52|2026 345 3 5 22 59|2026 0 3 20 23 46
        2026 15 4 5 3 40|2026 30 4 20 10 39|2026 45 5 5 20 49|2026 60 5 21 9 37|2026 75 6 6 0 48|2026 90 6 21 17 25
        2026 105 7 7 10 57|2026 120 7 23 4 13|2026 135 8 7 20 43|2026 150 8 23 11 19|2026 165 9 7 23 41|2026 180 9 23 9 5
        2026 195 10 8 15 29|2026 210 10 23 18 38|2026 225 11 7 18 52|2026 240 11 22 16 23|2026 255 12 7 11 53|2026 270 12 22 5 50
        2027 285 1 5 23 10|2027 300 1 20 16 30|2027 315 2 4 10 46|2027 330 2 19 6 33|2027 345 3 6 4 40|2027 0 3 21 5 25
        2027 15 4 5 9 17|2027 30 4 20 16 18|2027 45 5 6 2 25|2027 60 5 21 15 18|2027 75 6 6 6 26|2027 90 6 21 23 11
        2027 105 7 7 16 37|2027 120 7 23 10 5|2027 135 8 8 2 27|2027 150 8 23 17 14|2027 165 9 8 5 28|2027 180 9 23 15 2
        2027 195 10 8 21 17|2027 210 10 24 0 33|2027 225 11 8 0 39|2027 240 11 22 22 16|2027 255 12 7 17 38|2027 270 12 22 11 42
    """.trimIndent().split('\n', '|').map { it.trim().split(' ').map(String::toInt) }

    @Test
    fun `the 24 terms of 2025-2027 fall on 暦要項's dates, within 2 minutes of its moments (AC-1)`() {
        for (v in rekiyou) {
            val (y, lon, m, d, h) = v
            val mi = v[5]
            val (term, at) = SolarTerms.of(y).single { it.first.longitude == lon }
            val ours = at.atOffset(Astro.JST).toLocalDateTime()
            val theirs = LocalDateTime.of(y, m, d, h, mi)
            assertEquals("${term.label} $y", theirs.toLocalDate(), ours.toLocalDate())
            assertTrue("${term.label} $y: $ours vs $theirs", Duration.between(theirs, ours).abs().toMinutes() <= 2)
        }
    }

    @Test
    fun `a year's terms go from 小寒 to 冬至, 中気 every other one`() {
        val terms = SolarTerms.of(2026).map { it.first }
        assertEquals(SolarTerm.entries, terms)
        assertEquals(listOf("大寒", "雨水", "春分", "穀雨", "小満", "夏至", "大暑", "処暑", "秋分", "霜降", "小雪", "冬至"), terms.filter { it.isChuki }.map { it.label })
        assertEquals(LocalDate.of(2026, 11, 7), SolarTerms.dateOf(SolarTerm.RITTOU, 2026))
    }
}
