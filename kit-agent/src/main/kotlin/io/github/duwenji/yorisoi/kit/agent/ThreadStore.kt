package io.github.duwenji.yorisoi.kit.agent

import io.github.duwenji.yorisoi.core.agent.Origin
import io.github.duwenji.yorisoi.core.agent.Result
import io.github.duwenji.yorisoi.core.agent.ResultRole
import io.github.duwenji.yorisoi.core.agent.SpotBundle
import io.github.duwenji.yorisoi.core.agent.Step
import io.github.duwenji.yorisoi.core.agent.TalkThread
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where a preparation's threads are kept (platform ADR-AGENT A-34 ③): by the channel and its version and the
 * municipality and the day, or for a spot's preparation by the channel and its version and the spot.
 */
data class PrepKey(val channel: String, val channelVersion: Int, val municipality: String?, val date: LocalDate, val spot: String? = null) {
    internal val text: String get() = listOf(channel, channelVersion, municipality.orEmpty(), if (spot == null) date.toString() else "", spot.orEmpty()).joinToString("|")
}

/** What a preparation left: the threads, or a spot's bundle. */
data class Kept(val threads: List<TalkThread>, val spot: SpotBundle?)

/**
 * The threads kept on the device (A-34): a day's threads until the day ends (in Japan), a spot's for 7 days.
 * Only the threads and their results are kept, never the preparer's conversation (A-38・A-48).
 */
class ThreadStore(private val dir: File, private val zone: ZoneId = ZoneId.of("Asia/Tokyo"), private val now: () -> Instant = Instant::now) {
    init {
        dir.mkdirs()
    }

    private fun file(key: PrepKey) = File(dir, MessageDigest.getInstance("SHA-256").digest(key.text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32) + ".json")

    @Synchronized
    fun get(key: PrepKey): Kept? {
        val f = file(key)
        if (!f.exists()) return null
        val o = runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
        if (Instant.ofEpochMilli(o.getLong("until")).isBefore(now())) { f.delete(); return null }
        return Kept(threads(o.getJSONArray("threads")), o.optJSONObject("spot")?.let { s -> SpotBundle(s.getString("spot"), results(s.getJSONArray("results"))) })
    }

    @Synchronized
    fun put(key: PrepKey, kept: Kept) {
        val until = if (key.spot != null) now().plusSeconds(7 * 24 * 3600) else key.date.plusDays(1).atStartOfDay(zone).toInstant()
        val o = JSONObject().put("until", until.toEpochMilli())
            .put("threads", JSONArray(kept.threads.map { t ->
                JSONObject().put("path", JSONArray(t.path)).put("steps", JSONArray(t.steps.map { s ->
                    JSONObject().put("scene", s.scene).put("topic", s.topic).put("bridge", s.bridge).put("results", results(s.results))
                }))
            }))
        kept.spot?.let { o.put("spot", JSONObject().put("spot", it.spot).put("results", results(it.results))) }
        file(key).writeText(o.toString(), Charsets.UTF_8)
    }

    /** The settings'「キャッシュを消す」 and clearing the history (A-48). */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun results(rs: List<Result>) = JSONArray(rs.map { r ->
        JSONObject().put("id", r.id).put("tool", r.tool).put("text", r.text).put("role", r.role.name).apply {
            r.origin?.let { o -> put("origin", JSONObject().put("name", o.name).put("kind", o.kind).put("title", o.title).put("url", o.url).put("date", o.date)) }
        }
    })

    private fun results(a: JSONArray): List<Result> = (0 until a.length()).map { i ->
        val r = a.getJSONObject(i)
        Result(r.getString("id"), r.getString("tool"), r.getString("text"), r.optJSONObject("origin")?.let { o ->
            Origin(o.getString("name"), o.getString("kind"), o.optString("title").ifEmpty { null }, o.optString("url").ifEmpty { null }, o.optString("date").ifEmpty { null })
        }, ResultRole.valueOf(r.optString("role", ResultRole.FACT.name)))
    }

    private fun threads(a: JSONArray): List<TalkThread> = (0 until a.length()).map { i ->
        val t = a.getJSONObject(i)
        val steps = t.getJSONArray("steps")
        TalkThread(t.getJSONArray("path").let { p -> (0 until p.length()).map(p::getString) }, (0 until steps.length()).map { j ->
            val s = steps.getJSONObject(j)
            Step(s.getString("scene"), s.getString("topic"), results(s.getJSONArray("results")), s.getString("bridge"))
        })
    }
}
