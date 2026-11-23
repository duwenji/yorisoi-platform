package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.format.StationCheck
import io.github.duwenji.yorisoi.core.format.StationFiles
import io.github.duwenji.yorisoi.core.format.StationOrigin
import io.github.duwenji.yorisoi.core.format.StationValidator
import io.github.duwenji.yorisoi.core.format.Strictness
import io.github.duwenji.yorisoi.core.format.TalkLevel
import io.github.duwenji.yorisoi.core.web.Ledger
import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.core.web.SourceKind
import io.github.duwenji.yorisoi.core.web.SourceRead
import io.github.duwenji.yorisoi.kit.calendar.KoyomiKind
import io.github.duwenji.yorisoi.kit.channel.Station
import io.github.duwenji.yorisoi.kit.channel.StationOverrides
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The channel's `agent` narrowed the same way in both apps and the validator (platform ADR-AGENT A-6・A-7・A-8・A-51). */
class ResearchPolicyTest {
    private class MemoryFiles(val files: Map<String, ByteArray>) : StationFiles {
        override fun list() = files.keys.toList()
        override fun read(path: String) = files[path]
    }

    private fun station(
        agent: String?, events: String = "\"spot\": true, \"calendar\": true, \"localEvent\": true", extra: String = "", strictness: Strictness = Strictness.REVIEW,
    ): Station {
        val json = JSONObject(
            """
            {
              "format": 1, "id": "standard", "version": 1, "name": "標準", "summary": "その場で調べて話します", "lang": "ja",
              "activities": ["walk"], "greeting": "歩きましょう。",
              "talk": { "level": "normal", "events": { $events, "revisit": true, "milestone": true, "rest": true, "start": true, "finish": true } },
              "spots": { "prefer": ["shrine"], "skip": ["artwork"] }, "guide": { "length": "normal" }, "mood": { "tone": true, "sound": "auto" }
              $extra
              ${agent?.let { ", \"agent\": $it" } ?: ""}
            }
            """.trimIndent(),
        )
        val r = StationValidator.check(MemoryFiles(mapOf("channel.json" to json.toString().toByteArray())), StationOrigin.BUILT_IN, strictness = strictness)
        val pkg = (r as? StationCheck.Ok)?.station ?: error("$r")
        return Station(pkg, pkg, "builtin:standard@1")
    }

    private val plan = """{ "mode": "plan", "scenes": ["calendar", "season", "livelihood", "events", "spot"],
        "paths": ["calendar>season", "calendar>livelihood>events"],
        "sources": [{ "name": "福井市の観光", "urls": ["https://fuku-iro.example/news/"], "scenes": ["events", "spot"] }],
        "skip": ["association", "matome.example.com"], "topics": ["越前水仙"] }"""

    @Test
    fun `the channel's other items and the user's settings narrow what the preparer may do`() {
        val p = ResearchPolicy.of(station(plan, extra = ""","calendar": { "prefer": ["kou"], "skip": ["rokuyou"] }"""), researchOn = true, webOn = true)!!
        assertEquals(setOf("calendar", "season", "livelihood", "events"), p.scenes)
        assertEquals(listOf("calendar>season", "calendar>livelihood>events"), p.paths)
        assertTrue(p.spot)
        assertEquals(setOf(KoyomiKind.KOU), p.policy.calendarPrefer); assertEquals(setOf(KoyomiKind.ROKUYOU), p.policy.calendarSkip)
        assertEquals("the categories as the spots name them", setOf("アート"), p.policy.spotsSkip)
        assertEquals(listOf("越前水仙"), p.topics); assertEquals(listOf("matome.example.com"), p.skip); assertEquals(setOf("association"), p.skipKinds)
        assertEquals(false, ResearchPolicy.of(station(plan), researchOn = true, webOn = false)!!.policy.web)

        val noCalendar = ResearchPolicy.of(station(plan, events = "\"spot\": true, \"calendar\": false, \"localEvent\": true"), researchOn = true, webOn = true)!!
        assertEquals(setOf("season", "livelihood", "events"), noCalendar.scenes)
        assertEquals("a path through a scene left out goes", emptyList<String>(), noCalendar.paths)

        assertNull(ResearchPolicy.of(station(plan), researchOn = false, webOn = true))
        assertNull(ResearchPolicy.of(station(null), researchOn = true, webOn = true))
        assertNull(ResearchPolicy.of(station(plan).withOverrides(StationOverrides(talkLevel = TalkLevel.QUIET)), researchOn = true, webOn = true))
    }

    @Test
    fun `the pages to read are the ledger's for the place, but the skipped kinds, then the channel's own`() {
        val s = station(plan)
        val p = ResearchPolicy.of(s, researchOn = true, webOn = true)!!
        fun row(id: String, kind: SourceKind, code: String?, scenes: Set<String>, readable: Boolean = true) =
            Source(id, id, kind, code, listOf(SourceRead("https://$id.example/", feed = false)), scenes, readable)
        val ledger = Ledger(1, "18", listOf(
            row("fukui-city", SourceKind.MUNICIPALITY, "182010", setOf("livelihood", "events")),
            row("sabae-city", SourceKind.MUNICIPALITY, "182079", setOf("events")),
            row("fukui-pref", SourceKind.PREFECTURE, null, setOf("season")),
            row("kanko-renmei", SourceKind.ASSOCIATION, null, setOf("events")),
            row("closed", SourceKind.MUNICIPALITY, "182010", setOf("events"), readable = false),
        ))
        val threads = p.sources(s, listOf(ledger), "182010")
        assertEquals(listOf("fukui-city", "fukui-pref", "channel-0"), threads.map { it.id })
        assertEquals(setOf("events"), threads.last().scenes)
        assertEquals(SourceKind.CHANNEL, threads.last().kind)
        assertEquals(listOf("channel-0"), p.sources(s, listOf(ledger), "182010", setOf("spot")).map { it.id })

        // A kind this core-format doesn't know: the app leaves the ledger out (A-8).
        val noLedger = ResearchPolicy.of(station(plan.replace("\"association\"", "\"blogs\""), strictness = Strictness.APP), researchOn = true, webOn = true)!!
        assertEquals(listOf("channel-0"), noLedger.sources(s, listOf(ledger), "182010").map { it.id })
    }
}
