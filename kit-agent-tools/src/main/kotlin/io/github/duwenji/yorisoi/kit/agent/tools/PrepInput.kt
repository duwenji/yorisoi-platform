package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.kit.calendar.KoyomiFact
import io.github.duwenji.yorisoi.kit.calendar.KoyomiKind
import io.github.duwenji.yorisoi.kit.calendar.SeasonObsFile
import io.github.duwenji.yorisoi.kit.geo.NearbyHighlight
import io.github.duwenji.yorisoi.kit.geo.NearbyLocalEvent
import io.github.duwenji.yorisoi.kit.geo.weather.Weather
import java.time.LocalDate

/**
 * What the channel lets the preparer use (platform ADR-AGENT A-6・A-7), already read from `channel.json`:
 * the scenes that pass the channel's own items, and the items the tools apply.
 */
data class AgentPolicy(
    /** `agent.scenes` without those the channel's `talk.events` stopped (A-7). */
    val scenes: Set<String>,
    val calendarPrefer: Set<KoyomiKind> = emptySet(),
    val calendarSkip: Set<KoyomiKind> = emptySet(),
    val spotsPrefer: Set<String> = emptySet(),
    val spotsSkip: Set<String> = emptySet(),
    /** Whether the channel uses reference texts (REQ-SPOT-REFERENCES D-7). */
    val references: Boolean = true,
    /** `agent.web`: whether pages are read at all. */
    val web: Boolean = true,
)

/** A material's text for the tool `materials`: the app turns the channel's materials into these. */
data class MaterialText(
    /** The spot it is about (its id), or null for one about a topic. */
    val spotId: String?,
    val topic: String?,
    val text: String,
    val author: String?,
    /** A reference text (REQ-SPOT-REFERENCES), left out when the channel doesn't use them. */
    val reference: Boolean,
)

/**
 * One preparation's facts, as the app has them for the talk (platform ADR-AGENT A-4・A-12): nothing here is a
 * coordinate (A-14). [sources] are the ledger's rows and the channel's own for this place and these scenes.
 */
data class PrepInput(
    val date: LocalDate,
    /** The prefecture's number (「18」), for the season's observations. */
    val prefecture: String,
    val koyomi: List<KoyomiFact> = emptyList(),
    val weather: Weather? = null,
    val seasonObs: SeasonObsFile? = null,
    val events: List<NearbyLocalEvent> = emptyList(),
    val spots: List<NearbyHighlight> = emptyList(),
    val materials: List<MaterialText> = emptyList(),
    val sources: List<Source> = emptyList(),
    /** A spot's preparation (A-3): pages without a date, or older than 30 days, may serve it (A-18). */
    val forSpot: Boolean = false,
)
