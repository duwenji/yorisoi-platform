package io.github.duwenji.yorisoi.kit.web

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * The hints made from a page (ADR-AGENT A-34 ②), by the page's URL and a hash of the text they were made from,
 * so a page that changed gets new hints. Kept as long as the page itself ([PageFetcher.LIST_AGE] for a source's
 * own list page, [PageFetcher.PAGE_AGE] for a page followed from it).
 */
class HintCache(private val store: FileStore, private val now: () -> Instant = Instant::now) {
    private fun key(url: String, text: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)
        return "hints|$url|$hash"
    }

    /** The hints kept for [url] made from [text], or null when there are none or they are too old. */
    fun get(url: String, text: String, list: Boolean): List<String>? {
        val e = store.get(key(url, text)) ?: return null
        val age = if (list) PageFetcher.LIST_AGE else PageFetcher.PAGE_AGE
        if (!Instant.ofEpochMilli(e.facts.getLong("at")).plus(age).isAfter(now())) return null
        val a = JSONArray(String(e.bytes, Charsets.UTF_8))
        return (0 until a.length()).map(a::getString)
    }

    fun put(url: String, text: String, hints: List<String>) =
        store.put(key(url, text), JSONArray(hints).toString().toByteArray(Charsets.UTF_8), JSONObject().put("at", now().toEpochMilli()))

    /** For a check of how long it keeps them. */
    internal fun age(list: Boolean): Duration = if (list) PageFetcher.LIST_AGE else PageFetcher.PAGE_AGE
}
