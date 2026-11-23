package io.github.duwenji.yorisoi.core.format

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `agent` of `channel.json` (platform AGENT A-6・A-8・A-9). */
class ChannelAgentTest {
    private class MemoryFiles(val files: Map<String, ByteArray>) : StationFiles {
        override fun list() = files.keys.toList()
        override fun read(path: String) = files[path]
    }

    private fun files(agent: String?) = MemoryFiles(mapOf("channel.json" to JSONObject(
        """
        {
          "format": 1, "id": "standard", "version": 1, "name": "標準", "summary": "その場で調べて話します", "lang": "ja",
          "activities": ["walk"], "greeting": "歩きましょう。",
          "talk": { "level": "normal", "events": { "spot": true, "revisit": true, "milestone": true, "rest": true, "start": true, "finish": true } },
          "spots": { "prefer": [], "skip": [] }, "guide": { "length": "normal" }, "mood": { "tone": true, "sound": "auto" }
          ${agent?.let { ", \"agent\": $it" } ?: ""}
        }
        """.trimIndent(),
    ).toString().toByteArray()))

    private fun read(agent: String?, strictness: Strictness = Strictness.REVIEW): ChannelAgent {
        val r = StationValidator.check(files(agent), StationOrigin.BUILT_IN, strictness = strictness)
        assertTrue("expected Ok, got $r", r is StationCheck.Ok)
        return (r as StationCheck.Ok).station.manifest.agent
    }

    private fun rejected(agent: String): String {
        val r = StationValidator.check(files(agent), StationOrigin.BUILT_IN)
        assertTrue("expected a rejection, got $r", r is StationCheck.Rejected)
        return (r as StationCheck.Rejected).let { assertEquals(RejectCode.BAD_VALUE, it.code); it.detail }
    }

    @Test
    fun `left out, the preparer isn't used, and written it is read whole`() {
        assertEquals(ChannelAgent.OFF, read(null))
        val a = read(
            """{ "mode": "plan", "web": "on", "scenes": ["calendar", "season", "livelihood", "events"],
                 "paths": ["calendar>season", "calendar>livelihood>events"],
                 "sources": [{ "name": "鯖江市の観光", "urls": ["https://sabae-kanko.example/news/"], "scenes": ["events"] }],
                 "skip": ["association", "matome.example.com", "https://www.city.example.lg.jp/nyusatsu/"],
                 "topics": ["眼鏡", "越前漆器"] }""",
        )
        assertTrue(a.plan && a.web)
        assertEquals(setOf(AgentScene.CALENDAR, AgentScene.SEASON, AgentScene.LIVELIHOOD, AgentScene.EVENTS), a.scenes)
        assertEquals(listOf(listOf(AgentScene.CALENDAR, AgentScene.SEASON), listOf(AgentScene.CALENDAR, AgentScene.LIVELIHOOD, AgentScene.EVENTS)), a.paths)
        assertEquals(AgentSource("鯖江市の観光", listOf("https://sabae-kanko.example/news/"), setOf(AgentScene.EVENTS)), a.sources.single())
        assertEquals(setOf(SourceKindName.ASSOCIATION), a.skipKinds)
        assertEquals(listOf("matome.example.com"), a.skipDomains)
        assertEquals(listOf("https://www.city.example.lg.jp/nyusatsu/"), a.skipPrefixes)
        assertEquals(listOf("眼鏡", "越前漆器"), a.topics)
        // web and scenes left out: on, and every scene.
        val plain = read("""{ "mode": "plan" }""")
        assertTrue(plain.web)
        assertEquals(AgentScene.entries.toSet(), plain.scenes)
    }

    @Test
    fun `the review turns away what isn't allowed`() {
        assertTrue(rejected("""{ "mode": "auto" }""").contains("mode"))
        assertTrue(rejected("""{ "mode": "plan", "scenes": ["weather"] }""").contains("weather"))
        assertTrue(rejected("""{ "mode": "plan", "paths": ["calendar>spot"] }""").contains("spot"))
        assertTrue(rejected("""{ "mode": "plan", "paths": ["calendar"] }""").contains("2 to 4"))
        assertTrue(rejected("""{ "mode": "plan", "scenes": ["calendar"], "paths": ["calendar>events"] }""").contains("not in scenes"))
        assertTrue(rejected("""{ "mode": "plan", "sources": [{ "name": "x", "urls": ["http://a.example/"], "scenes": ["events"] }] }""").contains("urls"))
        assertTrue(rejected("""{ "mode": "plan", "sources": [{ "name": "x", "urls": ["https://a.example/"], "scenes": [] }] }""").contains("empty"))
        assertTrue(rejected("""{ "mode": "plan", "skip": ["blogs"] }""").contains("blogs"))
        assertTrue(rejected("""{ "mode": "plan", "topics": ["https://a.example/"] }""").contains("URL"))
        assertTrue(rejected("""{ "mode": "plan", "topics": ["{{name}}"] }""").contains("{{"))
    }

    @Test
    fun `an app leaves out what it doesn't know, and nothing loosens`() {
        assertEquals(false, read("""{ "mode": "auto" }""", Strictness.APP).plan)
        assertEquals(false, read("""{ "mode": "plan", "web": "partial" }""", Strictness.APP).web)
        val a = read("""{ "mode": "plan", "scenes": ["calendar", "weather"], "paths": ["calendar>weather", "calendar>calendar"], "skip": ["blogs"] }""", Strictness.APP)
        assertEquals(setOf(AgentScene.CALENDAR), a.scenes)
        assertEquals(listOf(listOf(AgentScene.CALENDAR, AgentScene.CALENDAR)), a.paths)
        assertTrue("an unknown kind in skip: the ledger isn't used", a.noLedger)
    }
}
