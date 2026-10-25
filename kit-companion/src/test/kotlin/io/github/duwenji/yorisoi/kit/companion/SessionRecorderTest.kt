package io.github.duwenji.yorisoi.kit.companion

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The developer's record of a session, kept on the device. */
class SessionRecorderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L
    private val dir by lazy { File(tmp.root, "diagnostics") }
    private val diagnostics by lazy { SessionRecorder(dir, startType = "walk_start", endType = "walk_end", clock = { now }) }

    private fun types(events: JSONArray) = (0 until events.length()).map { events.getJSONObject(it).getString("type") }

    @Test
    fun `a session's events are read back in order, with their time and fields`() {
        diagnostics.start(7L, listOf("station" to "builtin:standard@1"))
        now = 2_000L
        diagnostics.event("said", "text" to "こんにちは", "spot" to null)
        diagnostics.finish(7L)

        val events = diagnostics.read(7L)
        assertEquals(listOf("walk_start", "said", "walk_end"), types(events))
        assertEquals("builtin:standard@1", events.getJSONObject(0).getString("station"))
        val said = events.getJSONObject(1)
        assertEquals(2_000L, said.getLong("t"))
        assertEquals("こんにちは", said.getString("text"))
        assertTrue(said.isNull("spot"))
    }

    @Test
    fun `nothing is written while no session is recorded`() {
        diagnostics.event("said", "text" to "誰も聞いていない")
        assertFalse(diagnostics.recording)
        assertEquals(0, diagnostics.read(7L).length())
    }

    @Test
    fun `finishing an earlier session leaves the later one recording`() {
        diagnostics.start(1L, emptyList())
        diagnostics.start(2L, emptyList())
        diagnostics.finish(1L)
        diagnostics.event("location")

        assertTrue(diagnostics.recording)
        assertEquals(listOf("walk_start", "location"), types(diagnostics.read(2L)))
    }

    @Test
    fun `a line cut short is skipped, not the whole session`() {
        diagnostics.start(3L, emptyList())
        diagnostics.finish(3L)
        diagnostics.read(3L) // waits for the writes
        File(dir, "3.jsonl").appendText("{\"t\":5,\"type\":\"sai")

        assertEquals(listOf("walk_start", "walk_end"), types(diagnostics.read(3L)))
    }

    @Test
    fun `only the latest sessions are kept, newest first`() {
        (1L..SessionRecorder.MAX_SESSIONS + 2L).forEach { id ->
            diagnostics.start(id, emptyList())
            diagnostics.finish(id)
        }
        diagnostics.read(1L) // waits for the writes

        assertEquals(SessionRecorder.MAX_SESSIONS, dir.listFiles()!!.size)
        assertEquals(SessionRecorder.MAX_SESSIONS + 2L, diagnostics.ids().first())
        assertEquals(0, diagnostics.read(2L).length())
        assertEquals(2, diagnostics.read(SessionRecorder.MAX_SESSIONS + 2L).length())
    }

    @Test
    fun `clear removes every record`() {
        diagnostics.start(4L, emptyList())
        diagnostics.finish(4L)
        diagnostics.clear()
        assertEquals(0, diagnostics.read(4L).length())
    }
}
