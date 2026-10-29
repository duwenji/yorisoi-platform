package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.format.SpotKind
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Overpass answers kept on the device, one file per search: where it searched, which shops it
 * asked for and when, in the file's name; the answer as it came, inside. OpenStreetMap changes
 * slowly, so an answer from nearby saves asking the busy public servers again.
 */
class SpotCache(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = MAX_BYTES,
) {
    /** A kept answer: the search's centre, when it was fetched, and the answer. */
    class Entry internal constructor(val center: LatLon, val fetchedAt: Long, private val file: File) {
        fun body(): String? = try {
            file.readText()
        } catch (e: IOException) {
            null
        }
    }

    /**
     * The nearest answer within [withinM] of the point, for these [shops] and with traffic signals
     * or not as [signals] says and the amenities [facilities], no older than [maxAgeMs].
     */
    @Synchronized
    fun find(
        lat: Double,
        lon: Double,
        shops: Set<SpotKind>,
        withinM: Double,
        maxAgeMs: Long,
        signals: Boolean = false,
        facilities: Set<FacilityKind> = FacilityKind.WALK,
    ): Entry? {
        val here = LatLon(lat, lon)
        val key = key(shops, signals, facilities)
        val oldest = now() - maxAgeMs
        return entries()
            .filter { it.shops == key && it.entry.fetchedAt >= oldest }
            .map { it.entry to RouteGeometry.distanceM(here, it.entry.center) }
            .filter { (_, distance) -> distance <= withinM }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /** Keeps an answer, replacing older ones for the same search within [replaceWithinM], then trims. */
    @Synchronized
    fun put(
        lat: Double,
        lon: Double,
        shops: Set<SpotKind>,
        body: String,
        replaceWithinM: Double,
        signals: Boolean = false,
        facilities: Set<FacilityKind> = FacilityKind.WALK,
    ) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val here = LatLon(lat, lon)
        val key = key(shops, signals, facilities)
        val fetchedAt = now()
        val name = String.format(Locale.ROOT, "%d_%.5f_%.5f_%s%s", fetchedAt, lat, lon, key, SUFFIX)
        val tmp = File(dir, "$name.tmp")
        try {
            tmp.writeText(body)
            if (!tmp.renameTo(File(dir, name))) {
                tmp.delete()
                return
            }
        } catch (e: IOException) {
            tmp.delete()
            return
        }
        entries()
            .filter { it.shops == key && it.file.name != name && RouteGeometry.distanceM(here, it.entry.center) <= replaceWithinM }
            .forEach { it.file.delete() }
        trim()
    }

    /** The operator's spot tiles ([SpotTiles]) kept beside the answers, and cleared with them. */
    internal val tiles = SpotTileStore(File(dir, "tiles"), now)

    /** Forgets every kept answer and tile (they tell, roughly, where the user has walked). */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Drops answers past [MAX_AGE_MS], then the oldest until all fit in [maxBytes]. */
    private fun trim() {
        val oldest = now() - MAX_AGE_MS
        val kept = entries().sortedByDescending { it.entry.fetchedAt }.toMutableList()
        kept.filter { it.entry.fetchedAt < oldest }.forEach { it.file.delete(); kept.remove(it) }
        var total = kept.sumOf { it.file.length() }
        while (total > maxBytes && kept.isNotEmpty()) {
            val last = kept.removeAt(kept.lastIndex)
            total -= last.file.length()
            last.file.delete()
        }
    }

    private class Stored(val entry: Entry, val shops: String, val file: File)

    private fun entries(): List<Stored> = dir.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty().mapNotNull { f ->
        // Shop kinds have underscores too (FOOD_SHOP); they come last.
        val parts = f.name.removeSuffix(SUFFIX).split('_', limit = 4)
        if (parts.size != 4) return@mapNotNull null
        val fetchedAt = parts[0].toLongOrNull() ?: return@mapNotNull null
        val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        Stored(Entry(LatLon(lat, lon), fetchedAt, f), parts[3], f)
    }

    /** A walker's amenities add nothing, so the answers kept before other kinds could be asked for still match. */
    private fun key(shops: Set<SpotKind>, signals: Boolean, facilities: Set<FacilityKind>): String =
        (if (shops.isEmpty()) "none" else shops.map { it.name }.sorted().joinToString("-")) +
            (if (signals) "+signals" else "") +
            (if (facilities == FacilityKind.WALK) "" else "+" + facilities.map { it.name }.sorted().joinToString("-"))

    companion object {
        /** Answers this new are used as they are, without asking Overpass. */
        const val FRESH_MS = 7L * 24 * 60 * 60 * 1000

        /** Older answers are shown while asking again, or when asking fails; none are kept longer. */
        const val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000

        /** About 200 answers of a busy town. */
        const val MAX_BYTES = 10L * 1024 * 1024

        private const val SUFFIX = ".json"
    }
}
