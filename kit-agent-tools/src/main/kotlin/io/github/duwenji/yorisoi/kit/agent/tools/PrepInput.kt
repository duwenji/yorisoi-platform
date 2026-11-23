package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.kit.calendar.KoyomiFact
import io.github.duwenji.yorisoi.kit.calendar.KoyomiKind
import io.github.duwenji.yorisoi.kit.calendar.SeasonObsFile
import java.time.LocalDate

/**
 * What the channel lets the preparer use (platform AGENT A-6・A-7), already read from `channel.json`:
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
 * One preparation's facts, as the app has them for the talk (platform AGENT A-4・A-12): nothing here is a
 * coordinate (A-14). [sources] are the ledger's rows and the channel's own for this place and these scenes.
 */
data class PrepInput(
    val date: LocalDate,
    /** The prefecture's number (「18」), for the season's observations. */
    val prefecture: String,
    val koyomi: List<KoyomiFact> = emptyList(),
    val weather: PrepWeather? = null,
    val seasonObs: SeasonObsFile? = null,
    val events: List<PrepEvent> = emptyList(),
    val spots: List<PrepSpot> = emptyList(),
    val materials: List<MaterialText> = emptyList(),
    val sources: List<Source> = emptyList(),
    /** A spot's preparation (A-3): pages without a date, or older than 30 days, may serve it (A-18). */
    val forSpot: Boolean = false,
)

/** The weather now, as the talk has it (kit-geo `Weather` in the apps). */
data class PrepWeather(val description: String, val temperatureC: Double, val sunsetAt: Long? = null)

/** A local event near (kit-geo `NearbyLocalEvent` in the apps), with whoever published it. */
data class PrepEvent(val name: String, val venue: String, val startDate: LocalDate, val endDate: LocalDate, val publisher: String)

/**
 * A spot around (kit-geo `NearbyHighlight` in the apps): its distance and compass point from here, never a
 * coordinate (A-14). [side] is "ahead" for one ahead on the way.
 */
data class PrepSpot(val id: String, val name: String, val category: String, val distanceM: Int, val compass: String, val side: String? = null)
