package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** Every moment computed for 2016–2027 is among 暦要項's, on the same date (REQ-KOYOMI AC-1, D-2). */
class RekiyouTest {
    private fun table(name: String): Map<Int, List<LocalDateTime>> =
        javaClass.getResourceAsStream("/rekiyou/$name")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val v = line.split('\t').map(String::toInt)
                runCatching { LocalDateTime.of(v[0], v[1], v[2], v[3], v[4]) }.getOrNull()
            }
            .groupBy { it.year }

    private fun assertIn(what: String, at: Instant, theirs: List<LocalDateTime>) {
        val ours = at.atOffset(Astro.JST).toLocalDateTime()
        assertTrue(
            "$what at $ours is not in 暦要項 (same date, within 2 minutes)",
            theirs.any { it.toLocalDate() == ours.toLocalDate() && Duration.between(it, ours).abs().toMinutes() <= 2 },
        )
    }

    @Test
    fun `the 24 terms of 2016-2027`() {
        val theirs = table("sekki.tsv")
        for (y in 2016..2027) SolarTerms.of(y).forEach { (term, at) -> assertIn("${term.label} $y", at, theirs.getValue(y)) }
    }

    @Test
    fun `the seasonal days reckoned by the Sun - 土用, 入梅, 半夏生 - of 2016-2027`() {
        val theirs = table("sekki.tsv")
        for (y in 2016..2027) {
            for ((lon, what) in listOf(297.0 to "冬の土用", 27.0 to "春の土用", 117.0 to "夏の土用", 207.0 to "秋の土用", 80.0 to "入梅", 100.0 to "半夏生")) {
                assertIn("$what $y", Astro.sunAt(lon, y), theirs.getValue(y))
            }
        }
    }

    @Test
    fun `the moon's quarters of 2016-2027`() {
        val theirs = table("phases.tsv")
        for (y in 2016..2027) {
            val quarters = Astro.moonQuarters(Astro.startOf(y), Astro.startOf(y + 1))
            assertTrue("$y: ${quarters.size} quarters", quarters.size in 48..51)
            quarters.forEach { (q, at) -> assertIn("quarter $q of $y", at, theirs.getValue(y)) }
        }
    }
}
