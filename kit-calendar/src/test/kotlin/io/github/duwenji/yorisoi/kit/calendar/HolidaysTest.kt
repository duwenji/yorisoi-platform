package io.github.duwenji.yorisoi.kit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {
    private val cabinetOffice: Map<LocalDate, String> =
        javaClass.getResourceAsStream("/rekiyou/holidays.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }.associate { line -> line.split('\t').let { LocalDate.parse(it[0]) to it[1].trim() } }

    @Test
    fun `the holidays of 2016-2027 are the Cabinet Office's (AC-1)`() {
        for (y in 2016..2027) {
            val ours = Holidays.of(y).associate { it.start to it.name }
            val theirs = cabinetOffice.filterKeys { it.year == y }
            assertEquals("$y", theirs.keys.sorted(), ours.keys.sorted())
            // Names: the list calls 振替休日 and 国民の休日 both 「休日」.
            for ((day, name) in theirs) {
                val mine = ours.getValue(day)
                if (name.startsWith("休日")) assertTrue("$day $mine", mine in setOf("振替休日", "国民の休日", "天皇の即位の日", "即位礼正殿の儀の行われる日"))
                else assertEquals("$day", name.substringBefore('（'), mine)
            }
        }
    }

    @Test
    fun `every holiday says what it is for`() {
        Holidays.of(2026).forEach { assertTrue(it.name, it.note.isNotBlank()) }
    }
}
