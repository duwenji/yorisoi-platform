package io.github.duwenji.yorisoi.kit.local

import io.github.duwenji.yorisoi.core.format.material.BuiltInProducers
import io.github.duwenji.yorisoi.core.format.material.MaterialType
import io.github.duwenji.yorisoi.core.format.material.Prelude
import io.github.duwenji.yorisoi.core.format.material.ProducerDescriptor
import io.github.duwenji.yorisoi.kit.channel.material.Content
import io.github.duwenji.yorisoi.kit.channel.material.Credit
import io.github.duwenji.yorisoi.kit.channel.material.Material
import io.github.duwenji.yorisoi.kit.channel.material.MaterialRequest
import io.github.duwenji.yorisoi.kit.channel.material.MaterialResult
import io.github.duwenji.yorisoi.kit.channel.material.Producer
import io.github.duwenji.yorisoi.kit.channel.material.Subject

/**
 * People's own words as material (API-004 P-8): [text] is what is read out, the cue's prelude and
 * where the words come from first; [reading] is the studio or note itself, for the screen (likes,
 * reports, blocks) and a studio's audio, played after the words.
 */
data class LocalReading(val reading: Reading, val text: String) : Content

/** `builtin:studio`: the studios around the cue's place, or the phone's (ADR-MATERIALS D-44〜D-46). */
class StudioProducer(feed: () -> LocalFeed?) : LocalProducer(BuiltInProducers.STUDIO, feed) {
    override suspend fun next(feed: LocalFeed, request: MaterialRequest, near: LocalFeed.Near?): Reading? =
        feed.nextStudio(request.params.categories?.map { it.json }?.toSet(), near)
}

/** `builtin:note`: the notes around the cue's place, or the phone's. */
class NoteProducer(feed: () -> LocalFeed?) : LocalProducer(BuiltInProducers.NOTE, feed) {
    override suspend fun next(feed: LocalFeed, request: MaterialRequest, near: LocalFeed.Near?): Reading? = feed.nextNote(near)
}

/**
 * A producer of material fetched by area from the walk's or ride's [LocalFeed] (API-004
 * `fetch=area`). Outside a walk or ride there is no feed and nothing to give.
 */
abstract class LocalProducer(override val descriptor: ProducerDescriptor, private val feed: () -> LocalFeed?) : Producer {
    protected abstract suspend fun next(feed: LocalFeed, request: MaterialRequest, near: LocalFeed.Near?): Reading?

    override suspend fun produce(request: MaterialRequest): MaterialResult {
        val feed = feed() ?: return MaterialResult.Unavailable(MaterialResult.Reason.NOT_PREPARED, "no walk or ride")
        val near = when (val s = request.subject) {
            is Subject.Spot -> LocalFeed.Near(s.lat, s.lon)
            is Subject.Point -> LocalFeed.Near(s.lat, s.lon)
            else -> null
        }
        val reading = next(feed, request, near) ?: return MaterialResult.None
        val credit = (reading as? Reading.OfStudio)?.let { Credit(it.studio.providerName, null) }
        val text = (request.params.prelude ?: Prelude.NONE).spoken + reading.spoken
        return MaterialResult.Ready(Material(MaterialType.TEXT, LocalReading(reading, text), descriptor.id, credit))
    }
}
