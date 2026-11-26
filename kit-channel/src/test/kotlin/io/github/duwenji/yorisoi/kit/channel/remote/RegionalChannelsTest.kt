package io.github.duwenji.yorisoi.kit.channel.remote

import io.github.duwenji.yorisoi.core.format.FileRef
import io.github.duwenji.yorisoi.core.format.ListedChannel
import io.github.duwenji.yorisoi.core.format.PackageRef
import io.github.duwenji.yorisoi.core.format.material.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** REQ-REGIONAL-CHANNELS F-1・F-2・F-8・F-10, D-3・D-6・D-8. */
class RegionalChannelsTest {
    // 浅草寺の本堂のあたり.
    private val lat = 35.7148
    private val lon = 139.7967
    private val here = Regions.geohash(lat, lon, 8)

    private fun listed(id: String, name: String, regions: List<String>) = ListedChannel(
        id = id, version = 1, publisher = "sg1test", publisherName = "提供者", publisherChange = null,
        name = name, summary = "", description = null, lang = listOf("ja"), tags = emptyList(), regions = regions,
        icon = FileRef("https://example.com/i.png", "0"), pkg = PackageRef("https://example.com/p.zip", "0", 1, 1),
        approvedAt = Instant.EPOCH, media = emptyList(), resourcesSize = 0, activities = listOf("walk"),
        genres = emptyList(), requires = emptyList(), minAppVersions = emptyMap(),
    )

    private fun provider(id: String, vararg views: ListedView, enabled: Boolean = true, valid: Boolean = true) = ProviderView(
        record = ProviderRecord(id = id, url = "https://example.com", enabled = enabled), name = id, listValid = valid,
        expiresAt = null, channels = views.toList(), gone = emptyList(),
    )

    private fun view(c: ListedChannel, status: ListedStatus = ListedStatus.AVAILABLE) = ListedView(
        c, status, if (status == ListedStatus.AVAILABLE) null else InstalledChannel("p", c.id, 1, "sg1test", "0"),
    )

    private val asakusa = listed("asakusa-guide", "浅草界隈ガイド", listOf(here.take(5)))
    private val tokyo = listed("tokyo-walk", "東京さんぽ", listOf(here.take(3)))
    private val kamakura = listed("kamakura", "鎌倉", listOf("xn6h"))

    @Test
    fun `channels whose region the place is in, the narrowest first, and which may be told about`() {
        val near = RegionalChannels.near(lat, lon, listOf(provider("p", view(tokyo), view(kamakura), view(asakusa, ListedStatus.INSTALLED))))
        assertEquals(listOf("asakusa-guide", "tokyo-walk"), near.map { it.channel.id })
        assertTrue(near[0].notifies)
        assertTrue(near[0].installed)
        // A region of three letters leads the choice but isn't told about (D-6).
        assertFalse(near[1].notifies)
        assertFalse(near[1].installed)
    }

    @Test
    fun `channels that can't be used, lists switched off or expired, and channels without regions are left out`() {
        val noRegion = listed("anywhere", "どこでも", emptyList())
        assertTrue(RegionalChannels.near(lat, lon, listOf(provider("p", view(asakusa, ListedStatus.NEEDS_APP_UPDATE), view(noRegion)))).isEmpty())
        assertTrue(RegionalChannels.near(lat, lon, listOf(provider("p", view(asakusa), enabled = false))).isEmpty())
        assertTrue(RegionalChannels.near(lat, lon, listOf(provider("p", view(asakusa), valid = false))).isEmpty())
    }

    private class MemoryStore : RegionalNoticeStore {
        val days = mutableMapOf<String, String>()
        override fun lastNoticed(key: String) = days[key]
        override fun noticed(key: String, day: String) { days[key] = day }
    }

    @Test
    fun `a channel is told about once a day, not while in use, with how many others match`() {
        val second = listed("asakusa-food", "浅草のたべもの", listOf(here.take(6)))
        val near = RegionalChannels.near(lat, lon, listOf(provider("p", view(asakusa), view(second), view(tokyo))))
        val notices = RegionalNotices(MemoryStore())
        val first = notices.due(near, inUse = null, today = "2026-11-26")!!
        // The narrowest region's channel, and the other one that may be told about; the three-letter one doesn't count.
        assertEquals("asakusa-food", first.channel.channel.id)
        assertEquals(1, first.others)
        assertEquals("この地域には「浅草のたべもの」のチャンネルがあります。ほかに1つあります。チャンネルの選択から使えます。", first.text)
        notices.told(first, "2026-11-26")
        assertEquals("asakusa-guide", notices.due(near, null, "2026-11-26")!!.channel.channel.id)
        notices.told(notices.due(near, null, "2026-11-26")!!, "2026-11-26")
        assertNull(notices.due(near, null, "2026-11-26"))
        // The next day, again.
        assertEquals("asakusa-food", notices.due(near, null, "2026-11-27")!!.channel.channel.id)
        // Not the channel in use, named with its version as stations are.
        val only = RegionalChannels.near(lat, lon, listOf(provider("p", view(asakusa))))
        assertNull(notices.due(only, inUse = "p/asakusa-guide@3", today = "2026-11-28"))
    }
}
