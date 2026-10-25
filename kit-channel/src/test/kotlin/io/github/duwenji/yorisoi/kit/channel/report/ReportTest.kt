package io.github.duwenji.yorisoi.kit.channel.report

import io.github.duwenji.yorisoi.kit.channel.Station
import io.github.duwenji.yorisoi.kit.channel.StationSource
import io.github.duwenji.yorisoi.kit.channel.remote.TestChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reports go to whoever answers for the channel, with the line but never the place (platform ADR-004 H-11). */
class ReportTest {
    private val contact = "app@example.com"
    private val report = Report("TestApp", contact)
    private val standard = TestChannels.load().first { it.id == "standard" }

    @Test
    fun `a built-in channel's problems go to the app, a listed one's to the list's operator`() {
        assertEquals(contact, report.target(standard).contact)
        val listed = TestChannels.load().first { it.id == "quiet" }
        // Built-in packages stand in for a listed channel; only its source matters here.
        val fromList = Station(
            TestChannels.standard(), TestChannels.standard(), "p/x@1", id = "p/x",
            source = StationSource("p", "鎌倉のチャンネル", "鎌倉歴史散歩の会", contact = "channels@example.com"),
        )
        assertEquals("channels@example.com", report.target(fromList).contact)
        assertEquals("鎌倉のチャンネル", report.target(fromList).listName)
        // A list without a contact falls back to the app's.
        val noContact = Station(
            TestChannels.standard(), TestChannels.standard(), "p/x@1", id = "p/x",
            source = StationSource("p", "リスト", "提供者"),
        )
        assertEquals(contact, report.target(noContact).contact)
        assertEquals(listed.name, report.target(listed).channelName)
    }

    @Test
    fun `the draft names the channel and the line, and says it leaves the place out`() {
        val target = ReportTarget("標準", "standard@1", null, contact)
        val body = report.body(target, 1_760_600_000_000L to "この先の柵を越えると近道です。", "1.0.0")
        assertTrue(body.contains("チャンネル: 標準（standard@1）"))
        assertTrue(body.contains("発言: この先の柵を越えると近道です。"))
        assertTrue(body.contains("発言の時刻: 2025-10-16"))
        assertTrue(body.contains("位置は含めていません"))
        assertTrue(body.contains("アプリ: TestApp 1.0.0"))
        assertEquals("AI の発言について（標準）", report.subject(target, 0L to "x"))
        assertEquals("チャンネルについて（標準）", report.subject(target, null))
        assertFalse(report.body(target, null, "1.0.0").contains("発言"))
    }
}
