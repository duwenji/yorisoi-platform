package io.github.duwenji.yorisoi.kit.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The channel the user chose, and what they changed in each built-in channel, by [Station.id]. */
data class ChannelChoice(val stationId: String, val overrides: Map<String, StationOverrides> = emptyMap())

/**
 * The available channels (the built-in ones, then third parties' usable now), and the one in use
 * with the user's changes applied.
 *
 * @param standardId the built-in channel used when the chosen one is gone
 */
class StationRepository(
    builtIn: List<Station>,
    standardId: String,
    choice: StateFlow<ChannelChoice>,
    scope: CoroutineScope,
    thirdParty: StateFlow<List<Station>> = MutableStateFlow(emptyList()),
) {
    val standard: Station = builtIn.first { it.id == standardId }

    val list: StateFlow<List<Station>> = combine(MutableStateFlow(builtIn), thirdParty) { b, t -> b + t }
        .stateIn(scope, SharingStarted.Eagerly, builtIn + thirdParty.value)

    val all: List<Station> get() = list.value

    val current: StateFlow<Station> = combine(choice, list) { c, l -> select(c, l) }
        .stateIn(scope, SharingStarted.Eagerly, select(choice.value, list.value))

    /** The chosen channel, or the standard one if it's gone (removed in an update, withdrawn, or its list expired). */
    fun select(choice: ChannelChoice, from: List<Station> = all): Station {
        val station = from.firstOrNull { it.id == choice.stationId } ?: standard
        return station.withOverrides(choice.overrides[station.id] ?: StationOverrides())
    }

    fun byId(id: String): Station = all.firstOrNull { it.id == id } ?: standard

    /**
     * The channels behind [keys] (in the order they were used, e.g. a walk's), as one line:
     * `標準 → 歴史探訪`. A channel the app no longer has shows as such.
     */
    fun namesOf(keys: List<String>): String {
        return keys.map { key -> all.firstOrNull { sameChannel(it.key, key) }?.name ?: "（今はないチャンネル）" }
            .fold(emptyList<String>()) { acc, name -> if (acc.lastOrNull() == name) acc else acc + name }
            .joinToString(" → ")
    }

    // A newer version of the same channel still counts as that channel.
    private fun sameChannel(a: String, b: String) = a.substringBefore('@') == b.substringBefore('@')
}
