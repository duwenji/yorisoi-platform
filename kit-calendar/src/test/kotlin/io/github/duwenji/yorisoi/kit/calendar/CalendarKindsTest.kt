package io.github.duwenji.yorisoi.kit.calendar

import io.github.duwenji.yorisoi.core.format.CalendarKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarKindsTest {
    @Test
    fun `the channel format's calendar kinds are spelled as the calendar's (REQ-KOYOMI F-21)`() {
        assertEquals(KoyomiKind.entries.map { it.json }, CalendarKind.entries.map { it.json })
    }
}
