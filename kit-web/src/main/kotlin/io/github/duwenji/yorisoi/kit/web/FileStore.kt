package io.github.duwenji.yorisoi.kit.web

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * A small store on the device: a value (bytes) and its facts (JSON) by key, in [dir]. It keeps itself under
 * [maxBytes] by deleting what was used longest ago (ADR-AGENT A-34: the caches together up to 20 MB). Clearing
 * it is the settings'「キャッシュを消す」.
 */
class FileStore(private val dir: File, private val maxBytes: Long) {
    init {
        dir.mkdirs()
    }

    data class Entry(val bytes: ByteArray, val facts: JSONObject)

    private fun name(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(40)

    private fun files(key: String) = File(dir, name(key) + ".bin") to File(dir, name(key) + ".json")

    @Synchronized
    fun get(key: String): Entry? {
        val (bin, json) = files(key)
        if (!bin.exists() || !json.exists()) return null
        return runCatching {
            val e = Entry(bin.readBytes(), JSONObject(json.readText(Charsets.UTF_8)))
            val now = System.currentTimeMillis()
            bin.setLastModified(now); json.setLastModified(now)
            e
        }.getOrNull()
    }

    @Synchronized
    fun put(key: String, bytes: ByteArray, facts: JSONObject) {
        val (bin, json) = files(key)
        bin.writeBytes(bytes)
        json.writeText(facts.toString(), Charsets.UTF_8)
        trim()
    }

    /** Rewrites only the facts (a revalidated entry's new times). */
    @Synchronized
    fun touch(key: String, facts: JSONObject) {
        val (bin, json) = files(key)
        if (bin.exists()) json.writeText(facts.toString(), Charsets.UTF_8)
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** What the store holds, in bytes. */
    fun size(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    private fun trim() {
        var total = size()
        if (total <= maxBytes) return
        val bins = dir.listFiles { f -> f.name.endsWith(".bin") }?.sortedBy { it.lastModified() } ?: return
        for (bin in bins) {
            if (total <= maxBytes) break
            val json = File(dir, bin.name.removeSuffix(".bin") + ".json")
            total -= bin.length() + json.length()
            bin.delete(); json.delete()
        }
    }
}
