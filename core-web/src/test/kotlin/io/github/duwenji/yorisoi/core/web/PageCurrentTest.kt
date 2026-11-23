package io.github.duwenji.yorisoi.core.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** AGENT A-18: a page older than 30 days still serves when its text is about now. */
class PageCurrentTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test
    fun `a period that includes today, in the forms municipal pages write`() {
        // 福井市立郷土歴史博物館's page, last updated on 10月1日.
        assertTrue(PageCurrent.of("開催日時 / 令和8年10月10日（土）～11月23日（月・祝）開館時間 ：9時～17時", today))
        assertTrue(PageCurrent.of("期間：2026年11月1日から2026年12月25日まで", today))
        assertTrue(PageCurrent.of("１１月２０日～１２月５日", today))
        assertTrue("a period across the new year", PageCurrent.of("12月20日～1月10日", LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `a day soon, but not a past one nor one far ahead`() {
        assertTrue(PageCurrent.of("12月6日（日）に収穫祭を開きます", today))
        assertFalse("past", PageCurrent.of("10月10日（土）に開催しました", today))
        assertFalse("ended before today", PageCurrent.of("10月10日～11月22日", today))
        assertFalse("too far ahead", PageCurrent.of("2027年2月14日に開催", today))
        assertFalse(PageCurrent.of("市の歴史を紹介します。", today))
    }
}
