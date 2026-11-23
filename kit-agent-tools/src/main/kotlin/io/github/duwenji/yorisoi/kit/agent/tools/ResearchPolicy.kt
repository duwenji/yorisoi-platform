package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.format.AgentScene
import io.github.duwenji.yorisoi.core.format.TalkEventKind
import io.github.duwenji.yorisoi.core.format.TalkLevel
import io.github.duwenji.yorisoi.core.web.Ledger
import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.core.web.SourceKind
import io.github.duwenji.yorisoi.core.web.SourceRead
import io.github.duwenji.yorisoi.kit.calendar.KoyomiKind
import io.github.duwenji.yorisoi.kit.channel.Station

/**
 * What the preparer may do on a channel (platform ADR-AGENT A-6・A-7・A-46): the channel's `agent` narrowed by its
 * other items and the user's settings. Both apps and the console's validator narrow it here, the same way (A-51・A-54). Null when nothing is prepared: the channel's `mode` is off, it talks
 * quietly, it doesn't chat (S-1 prepares the chat's threads only), or the user turned research off.
 */
data class ResearchPolicy(
    val policy: AgentPolicy,
    /** The channel's paths that use only the usable scenes, as `calendar>season`. */
    val paths: List<String>,
    val topics: List<String>,
    /** The channel's own skips (domains and URL prefixes), added to the operator's exclusion list. */
    val skip: List<String>,
    /** Whether the ledger's sources are used (an unknown kind in `agent.skip` says no, A-8). */
    val ledger: Boolean,
    val skipKinds: Set<String>,
    /** Whether the spots ahead are prepared for their narration (A-7: `spot` in `agent.scenes`, and the channel talks of spots). */
    val spot: Boolean = false,
) {
    val scenes: Set<String> get() = policy.scenes

    /**
     * The pages the preparer may read for [scenes] in the municipality [code]: the ledgers' rows for the place, but the
     * kinds the channel skips (none when it leaves the ledger out, A-8), then the channel's own sources for these scenes.
     */
    fun sources(station: Station, ledgers: List<Ledger>, code: String?, scenes: Set<String> = this.scenes): List<Source> {
        val fromLedger = if (ledger) ledgers.flatMap { it.forPlace(code, scenes) }.filter { it.kind.id !in skipKinds } else emptyList()
        return fromLedger + station.manifest.agent.sources.filter { s -> s.scenes.any { it.json in scenes } }.mapIndexed { i, s ->
            Source("channel-$i", s.name, SourceKind.CHANNEL, null, s.urls.map { SourceRead(it, feed = false) }, s.scenes.map { it.json }.filter { it in scenes }.toSet())
        }
    }

    companion object {
        fun of(station: Station, researchOn: Boolean, webOn: Boolean): ResearchPolicy? {
            val agent = station.manifest.agent
            if (!researchOn || !agent.plan || station.talkLevel == TalkLevel.QUIET || !station.talksOn(TalkEventKind.CHAT)) return null
            val usable = agent.scenes.filter { scene ->
                when (scene) {
                    AgentScene.CALENDAR -> station.talksOn(TalkEventKind.CALENDAR)
                    AgentScene.EVENTS -> station.talksOn(TalkEventKind.LOCAL_EVENT)
                    // Spots are prepared with the preview (S-2), not in the chat's threads.
                    AgentScene.SPOT -> false
                    AgentScene.SEASON, AgentScene.LIVELIHOOD -> true
                }
            }.map { it.json }.toSet()
            val spot = AgentScene.SPOT in agent.scenes && station.talksOn(TalkEventKind.SPOT)
            if (usable.isEmpty() && !spot) return null
            val kinds = KoyomiKind.entries.associateBy { it.json }
            return ResearchPolicy(
                policy = AgentPolicy(
                    scenes = usable,
                    calendarPrefer = station.calendarPrefer.mapNotNull { kinds[it.json] }.toSet(),
                    calendarSkip = station.calendarSkip.mapNotNull { kinds[it.json] }.toSet(),
                    spotsPrefer = station.preferredCategories,
                    spotsSkip = station.skippedCategories,
                    web = agent.web && webOn,
                ),
                paths = agent.paths.filter { p -> p.all { it.json in usable } }.map { p -> p.joinToString(">") { it.json } },
                topics = agent.topics,
                skip = agent.skipDomains + agent.skipPrefixes,
                ledger = !agent.noLedger,
                skipKinds = agent.skipKinds.map { it.json }.toSet(),
                spot = spot,
            )
        }
    }
}
