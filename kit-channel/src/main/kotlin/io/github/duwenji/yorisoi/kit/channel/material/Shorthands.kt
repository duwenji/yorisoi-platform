package io.github.duwenji.yorisoi.kit.channel.material

import io.github.duwenji.yorisoi.core.format.Cue
import io.github.duwenji.yorisoi.core.format.CueEvent
import io.github.duwenji.yorisoi.core.format.PresentAs
import io.github.duwenji.yorisoi.core.format.Presentation
import io.github.duwenji.yorisoi.core.format.Reference
import io.github.duwenji.yorisoi.core.format.Repeat
import io.github.duwenji.yorisoi.core.format.StationMedia
import io.github.duwenji.yorisoi.core.format.Trigger
import io.github.duwenji.yorisoi.core.format.material.BuiltInProducers

/**
 * The shorthand cues of built-in producers (API-MATERIALS「省略形の展開」, ADR-MATERIALS D-48): a channel
 * that neither names one of [BuiltInProducers.withShorthand] in its cues nor turns it off in
 * `shorthands` shows it between the companion's lines, as an `idle` cue at the end of its own.
 */
object Shorthands {
    const val CUE_PREFIX = "shorthand-"

    fun expand(media: StationMedia): StationMedia {
        val named = media.cues.flatMap { c -> c.present.flatMap { p -> p.items.flatten() } }
            .filterIsInstance<Reference.BuiltIn>().map { it.id }.toSet()
        val added = BuiltInProducers.withShorthand
            .filter { it.id !in named && it.id !in media.shorthandsOff }
            .map { idle(it.id) }
        return if (added.isEmpty()) media else media.copy(cues = media.cues + added)
    }

    private fun idle(producer: String) = Cue(
        id = CUE_PREFIX + producer.removePrefix(BuiltInProducers.PREFIX),
        trigger = Trigger.Event(CueEvent.IDLE),
        narration = null,
        repeat = Repeat.ONCE_PER_WALK,
        present = listOf(Presentation(PresentAs.SPEAK, listOf(listOf(Reference.BuiltIn(producer))))),
    )
}
