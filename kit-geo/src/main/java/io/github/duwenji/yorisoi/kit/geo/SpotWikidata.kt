package io.github.duwenji.yorisoi.kit.geo

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the operator's spot tiles say of a spot from its Wikidata item (platform REQ-WIKIDATA,
 * API-SPOT-TILES「Wikidata の値」): Japanese values only, each null or empty when the item has none.
 * Map data like the OSM tags, not a reviewed ground for the AI (REQ-WIKIDATA D-3).
 *
 * [inception] is written as OSM's `start_date` is (`1063`, `1924-03`, `C12`, `1920年代`).
 * [image] is a Wikimedia Commons file name without `File:`.
 */
data class SpotWikidata(
    val id: String,
    val description: String? = null,
    val reading: String? = null,
    val kinds: List<String> = emptyList(),
    val inception: String? = null,
    val heritage: List<String> = emptyList(),
    val architects: List<String> = emptyList(),
    val founders: List<String> = emptyList(),
    val jawiki: String? = null,
    val image: String? = null,
    val website: String? = null,
) {
    /** The same object as the tile's, for an app that keeps spots for later (REQ-WIKIDATA F-15). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        description?.let { put("description", it) }
        reading?.let { put("reading", it) }
        list("kinds", kinds)
        inception?.let { put("inception", it) }
        list("heritage", heritage)
        list("architects", architects)
        list("founders", founders)
        jawiki?.let { put("jawiki", it) }
        image?.let { put("image", it) }
        website?.let { put("website", it) }
    }

    private fun JSONObject.list(key: String, values: List<String>) {
        if (values.isNotEmpty()) put(key, JSONArray(values))
    }

    companion object {
        private val ID = Regex("Q[1-9][0-9]*")

        /** The tile's `wikidata` (or [toJson]'s); null when it has no well-formed `id`. Unknown keys are ignored. */
        fun fromJson(o: JSONObject?): SpotWikidata? {
            o ?: return null
            val id = o.optString("id").takeIf { ID.matches(it) } ?: return null
            fun text(key: String) = o.optString(key).trim().ifEmpty { null }.takeIf { o.has(key) }
            fun texts(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } } }.orEmpty()
            return SpotWikidata(
                id = id,
                description = text("description"),
                reading = text("reading"),
                kinds = texts("kinds"),
                inception = text("inception"),
                heritage = texts("heritage"),
                architects = texts("architects"),
                founders = texts("founders"),
                jawiki = text("jawiki"),
                image = text("image"),
                website = text("website")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            )
        }
    }
}
