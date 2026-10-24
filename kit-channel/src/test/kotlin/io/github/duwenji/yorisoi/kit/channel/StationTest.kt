package io.github.duwenji.yorisoi.kit.channel

import io.github.duwenji.yorisoi.core.format.GuideLength
import io.github.duwenji.yorisoi.core.format.Slot
import io.github.duwenji.yorisoi.core.format.TalkLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationTest {
    private object TestChannels : ResourceChannels("test-channels", "standard", listOf("standard", "quiet"))

    private val stations = TestChannels.load()
    private val standard = stations.first { it.id == "standard" }
    private val quiet = stations.first { it.id == "quiet" }

    @Test
    fun `built-in channels are read from the resources in the app's order, keyed by id and version`() {
        assertEquals(listOf("standard", "quiet"), stations.map { it.id })
        assertEquals(listOf("builtin:standard@1", "builtin:quiet@2"), stations.map { it.key })
        assertTrue(stations.all { it.isBuiltIn })
    }

    @Test
    fun `a slot the channel leaves empty gets the standard channel's text`() {
        assertEquals("あなたは町の案内人です。", quiet.slot(Slot.GUIDE_PERSONA))
        assertNull(quiet.slot(Slot.GUIDE_FOCUS))
    }

    @Test
    fun `the user's changes keep only what differs from the channel's own values`() {
        assertEquals(TalkLevel.QUIET, quiet.talkLevel)
        val changed = quiet.withOverrides(StationOverrides(talkLevel = TalkLevel.CHATTY, guideLength = GuideLength.SHORT))
        assertEquals(TalkLevel.CHATTY, changed.talkLevel)
        assertEquals(TalkLevel.QUIET, changed.defaultTalkLevel)
        assertEquals(StationOverrides(talkLevel = TalkLevel.CHATTY), changed.overrides)
    }

    @Test
    fun `the chosen channel comes with its changes, and a channel that is gone falls back to the standard one`() {
        val choice = MutableStateFlow(ChannelChoice("quiet", mapOf("quiet" to StationOverrides(talkLevel = TalkLevel.NORMAL))))
        val repo = StationRepository(stations, "standard", choice, CoroutineScope(Dispatchers.Unconfined))
        assertEquals("quiet", repo.current.value.id)
        assertEquals(TalkLevel.NORMAL, repo.current.value.talkLevel)
        choice.value = ChannelChoice("retired")
        assertEquals(standard.id, repo.current.value.id)
    }

    @Test
    fun `the channels used in order read as one line, a newer version counting as the same channel`() {
        val repo = StationRepository(stations, "standard", MutableStateFlow(ChannelChoice("standard")), CoroutineScope(Dispatchers.Unconfined))
        assertEquals("標準 → しずかに", repo.namesOf(listOf("builtin:standard@1", "builtin:quiet@1")))
        assertEquals("しずかに", repo.namesOf(listOf("builtin:quiet@1", "builtin:quiet@2")))
        assertEquals("標準 → （今はないチャンネル）", repo.namesOf(listOf("builtin:standard@1", "builtin:retired@1")))
    }
}
