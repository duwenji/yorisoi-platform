package io.github.duwenji.yorisoi.kit.channel.material

import io.github.duwenji.yorisoi.core.format.ListedCollection
import io.github.duwenji.yorisoi.core.format.Trigger
import io.github.duwenji.yorisoi.core.format.material.MaterialType
import io.github.duwenji.yorisoi.core.format.material.Resources
import io.github.duwenji.yorisoi.kit.channel.Station

/**
 * What a channel holds about a spot, for the walk's record: the cues that would fire on it
 * (by its id or name, on any spot of its kind, or a point it lies in) and the resources about it,
 * whether the channel's own, a collection's it names, or a collection's its selectors draw from.
 * Only looks; whether a cue may fire again (`repeat`) is [CueFinder]'s.
 */
data class SpotMaterials(val cues: List<SpotCue>, val resources: List<SpotResource>) {
    data class SpotCue(val id: String, val trigger: String)

    /** [source]: `self`, `collection` or `selector`; [alias] as the channel names it (null for its own). */
    data class SpotResource(
        val source: String,
        val alias: String?,
        val collection: ListedCollection?,
        val id: String,
        val type: MaterialType,
    )

    companion object {
        /** What [station] holds about [spot], or null when nothing. */
        fun of(station: Station, spot: Subject.Spot): SpotMaterials? {
            val materials = station.materials ?: return null
            val cues = materials.media.cues.mapNotNull { cue ->
                val trigger = when (val t = cue.trigger) {
                    is Trigger.Spot -> "spot".takeIf { t.osm == spot.id || (t.name != null && t.name == spot.name) }
                    is Trigger.AnySpot -> "any".takeIf { t.categories.any { it.category == spot.category } }
                    is Trigger.Point -> "point".takeIf { SubjectMatch.distance(t.lat, t.lon, spot.lat, spot.lon) <= t.radiusM }
                    is Trigger.Event -> null
                }
                trigger?.let { SpotCue(cue.id, it) }
            }
            fun about(resources: Resources, source: String, alias: String?, collection: ListedCollection?) =
                resources.items.filter { r -> r.about?.let { SubjectMatch.matches(it, spot) } == true }
                    .map { SpotResource(source, alias, collection, it.id, it.type) }
            val resources = about(materials.media.resources, "self", null, null) +
                materials.media.collections.flatMap { use ->
                    materials.collection(use.alias)?.let { about(it.pkg.resources, "collection", use.alias, it.listed) }.orEmpty()
                } +
                materials.selected.flatMap { (alias, collections) ->
                    collections.flatMap { about(it.pkg.resources, "selector", alias, it.listed) }
                }
            return if (cues.isEmpty() && resources.isEmpty()) null else SpotMaterials(cues, resources)
        }
    }
}
