package io.github.duwenji.yorisoi.kit.channel

import io.github.duwenji.yorisoi.core.format.material.CollectionPackage
import io.github.duwenji.yorisoi.core.format.material.Resource
import io.github.duwenji.yorisoi.core.format.material.ResourceLocation
import io.github.duwenji.yorisoi.core.format.CollectionStatus
import io.github.duwenji.yorisoi.core.format.ListedCollection
import io.github.duwenji.yorisoi.core.format.StationFiles
import io.github.duwenji.yorisoi.core.format.StationMedia

/** A collection on the device that a selector may draw from, as its provider's list names it. */
class SelectedCollection(val listed: ListedCollection, val pkg: CollectionPackage, val files: StationFiles)

/**
 * What a third party's channel can show on this device: its own resources, the
 * collections it declares as resolved against its provider's list, and the outside resources
 * fetched ahead. Anything missing (not fetched yet, revoked, a later app stage) reads as null,
 * and the cue moves on to its next candidate (API-004 I-7).
 *
 * @param collections by alias, as `CollectionReferences.resolve` gave them.
 * @param collectionFiles the files of each usable collection, by alias.
 * @param stored outside resources fetched ahead, by SHA-256.
 * @param selected the collections on the device each selector may draw from, best first, by its alias.
 * @param lang the channel's language, which selectors ask for unless they name another.
 */
class ChannelMaterials(
    val media: StationMedia,
    val collections: Map<String, CollectionStatus>,
    private val own: StationFiles,
    private val collectionFiles: Map<String, StationFiles>,
    private val stored: (sha256: String) -> ByteArray?,
    val selected: Map<String, List<SelectedCollection>> = emptyMap(),
    val lang: String = "ja",
) {
    /** A usable collection by its alias in this channel, or null. */
    fun collection(alias: String): CollectionStatus.Usable? = collections[alias] as? CollectionStatus.Usable

    /** The text of a text resource: the channel's own when [alias] is null, else the collection's. */
    fun text(resource: Resource, alias: String?): String? =
        (if (alias == null) media.resources.texts else collection(alias)?.pkg?.resources?.texts)?.get(resource.id)

    /** The bytes of an image (or later audio and video): from its package, or as fetched ahead. */
    fun bytes(resource: Resource, alias: String?): ByteArray? = when (val at = resource.location) {
        is ResourceLocation.File -> (if (alias == null) own else collectionFiles[alias])?.read(at.path)
        is ResourceLocation.External -> stored(at.sha256)
    }

    /** The bytes of a resource of a collection a selector draws from. */
    fun bytes(c: SelectedCollection, resource: Resource): ByteArray? = when (val at = resource.location) {
        is ResourceLocation.File -> c.files.read(at.path)
        is ResourceLocation.External -> stored(at.sha256)
    }
}
