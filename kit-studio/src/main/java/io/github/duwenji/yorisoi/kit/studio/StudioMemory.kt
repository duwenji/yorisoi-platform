package io.github.duwenji.yorisoi.kit.studio

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * What the device keeps of the studio, in one file that isn't backed up: the notes already read
 * (7 days), the placements read and on which day, reactions not sent yet, and the text of the note
 * each block was made from (the studio never says whom a block is of, API-STUDIO).
 */
class StudioMemory(private val file: File) {
    private val heardNotes = mutableMapOf<String, Long>()
    private val heardPlacements = mutableMapOf<String, String>()
    private val pendingHeard = linkedSetOf<Ref>()
    private val pendingOpened = linkedSetOf<Ref>()
    private val pendingLiked = linkedSetOf<String>()
    private val liked = mutableSetOf<String>()
    private val blockLabels = mutableMapOf<String, String>()

    init {
        load()
    }

    @Synchronized
    fun noteHeard(id: String): Boolean = id in heardNotes

    @Synchronized
    fun placementHeardOn(id: String, day: String): Boolean = heardPlacements[id] == day

    /** A note read now: never again, and counted as heard when the walk ends. */
    @Synchronized
    fun markNoteHeard(id: String, now: Instant) {
        heardNotes[id] = now.toEpochMilli()
        pendingHeard += Ref.note(id)
        save(now)
    }

    /** A placement read on [day] (in Japan): not again that day. */
    @Synchronized
    fun markPlacementHeard(id: String, day: String, now: Instant) {
        heardPlacements[id] = day
        pendingHeard += Ref.placement(id)
        save(now)
    }

    @Synchronized
    fun markOpened(ref: Ref, now: Instant) {
        pendingOpened += ref
        save(now)
    }

    @Synchronized
    fun isLiked(noteId: String): Boolean = noteId in liked

    /** A like counts once per device (API-STUDIO); this keeps the button from sending it twice. */
    @Synchronized
    fun like(noteId: String, now: Instant) {
        if (!liked.add(noteId)) return
        pendingLiked += noteId
        save(now)
    }

    /** Reactions waiting to be sent, at most [max] (API-STUDIO: 200 a request). */
    @Synchronized
    fun pending(max: Int = 200): Pending {
        val heard = pendingHeard.take(max)
        val opened = pendingOpened.take(max - heard.size)
        val liked = pendingLiked.take(max - heard.size - opened.size)
        return Pending(heard, opened, liked)
    }

    /** The reactions of [sent] reached the studio. */
    @Synchronized
    fun sent(sent: Pending, now: Instant) {
        pendingHeard -= sent.heard.toSet()
        pendingOpened -= sent.opened.toSet()
        pendingLiked -= sent.liked.toSet()
        save(now)
    }

    @Synchronized
    fun blockLabel(blockId: String): String? = blockLabels[blockId]

    @Synchronized
    fun rememberBlock(blockId: String, noteText: String, now: Instant) {
        blockLabels[blockId] = noteText
        save(now)
    }

    @Synchronized
    fun forgetBlock(blockId: String, now: Instant) {
        blockLabels.remove(blockId)
        save(now)
    }

    data class Pending(val heard: List<Ref>, val opened: List<Ref>, val liked: List<String>) {
        val isEmpty get() = heard.isEmpty() && opened.isEmpty() && liked.isEmpty()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            o.optJSONObject("heardNotes")?.let { m -> m.keys().forEach { heardNotes[it] = m.getLong(it) } }
            o.optJSONObject("heardPlacements")?.let { m -> m.keys().forEach { heardPlacements[it] = m.getString(it) } }
            o.optJSONArray("pendingHeard")?.objects()?.forEach { pendingHeard += Ref(it.getString("type"), it.getString("id")) }
            o.optJSONArray("pendingOpened")?.objects()?.forEach { pendingOpened += Ref(it.getString("type"), it.getString("id")) }
            o.optJSONArray("pendingLiked")?.let { a -> (0 until a.length()).forEach { pendingLiked += a.getString(it) } }
            o.optJSONArray("liked")?.let { a -> (0 until a.length()).forEach { liked += a.getString(it) } }
            o.optJSONObject("blockLabels")?.let { m -> m.keys().forEach { blockLabels[it] = m.getString(it) } }
        } catch (e: JSONException) {
            // A damaged file starts over: at worst a note is read once more.
        } catch (e: IOException) {
        }
    }

    private fun save(now: Instant) {
        // Notes are read once; after a week they are gone from the studio anyway (API-STUDIO).
        val keepFrom = now.toEpochMilli() - KEEP_MS
        heardNotes.values.removeAll { it < keepFrom }
        val today = StudioFeed.dayOf(now)
        heardPlacements.values.removeAll { it < StudioFeed.dayOf(now.minusMillis(KEEP_MS)) || it > today }
        val refs = { set: Set<Ref> -> JSONArray(set.map { JSONObject().put("type", it.type).put("id", it.id) }) }
        val o = JSONObject()
            .put("heardNotes", JSONObject(heardNotes as Map<*, *>))
            .put("heardPlacements", JSONObject(heardPlacements as Map<*, *>))
            .put("pendingHeard", refs(pendingHeard))
            .put("pendingOpened", refs(pendingOpened))
            .put("pendingLiked", JSONArray(pendingLiked.toList()))
            .put("liked", JSONArray(liked.toList()))
            .put("blockLabels", JSONObject(blockLabels as Map<*, *>))
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            // Kept in memory for this run; the next save tries again.
        }
    }

    private companion object {
        const val KEEP_MS = 8L * 24 * 3600 * 1000
    }
}
