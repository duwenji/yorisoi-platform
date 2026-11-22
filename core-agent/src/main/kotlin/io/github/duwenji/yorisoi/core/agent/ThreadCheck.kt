package io.github.duwenji.yorisoi.core.agent

import org.json.JSONArray
import org.json.JSONObject

/** One step of a thread: its topic, the results it points at, and the words that lead into it. */
data class Step(val scene: String, val topic: String, val results: List<Result>, val bridge: String)

/** A thread of talk (ADR-AGENT A-3): two to four steps along a path of scenes. */
data class TalkThread(val path: List<String>, val steps: List<Step>)

/** A spot's preparation: one bundle of results (A-3). */
data class SpotBundle(val spot: String, val results: List<Result>)

/** What was taken from a submission, and what was left out and why (for the developer's log, A-49). */
data class Checked(val threads: List<TalkThread>, val spot: SpotBundle?, val dropped: List<String>)

/**
 * The check of a submission (ADR-AGENT A-29). The preparer writes no fact: a step points at results by number,
 * and the speaker is given the results' own text. This takes out a number not in this preparation, a result
 * whose tool doesn't serve the step's scene, and a number a thread already used; empties a bridge longer than
 * [MAX_BRIDGE] characters or with a digit; cuts a topic to [MAX_TOPIC] characters. A step left with no result
 * goes, and a thread left with fewer than two steps goes. A path must be scenes joined by 「>」, only scenes the
 * channel lets the preparer use (A-7).
 */
object ThreadCheck {
    const val MAX_THREADS = 3
    const val MAX_STEPS = 4
    const val MAX_TOPIC = 20
    const val MAX_BRIDGE = 30

    /** The tools whose results serve each scene. */
    val SCENE_TOOLS: Map<String, Set<String>> = mapOf(
        "calendar" to setOf("calendar"),
        "season" to setOf("season_obs", "weather", "calendar"),
        "livelihood" to setOf("read_page", "materials"),
        "events" to setOf("local_events", "read_page"),
        "spot" to setOf("nearby_spots", "materials", "read_page"),
    )

    private val PATH = Regex("^(calendar|season|livelihood|events)(>(calendar|season|livelihood|events)){1,3}$")
    private val DIGIT = Regex("[0-9０-９]")

    /**
     * Checks [input] (the arguments of `submit_threads`, already checked against its schema) against [book].
     * @param scenes the scenes this preparation may use (A-7)
     */
    fun check(input: JSONObject, book: ResultBook, scenes: Set<String>): Checked {
        val dropped = ArrayList<String>()
        val threads = ArrayList<TalkThread>()
        val arr = input.optJSONArray("threads") ?: JSONArray()
        for (i in 0 until minOf(arr.length(), MAX_THREADS)) {
            val t = arr.getJSONObject(i)
            val pathText = t.optString("path")
            val path = pathText.split(">")
            if (!PATH.matches(pathText) || path.any { it !in scenes }) { dropped += "thread $i: path $pathText"; continue }
            val used = HashSet<String>()
            val steps = ArrayList<Step>()
            val raw = t.optJSONArray("steps") ?: JSONArray()
            for (j in 0 until minOf(raw.length(), MAX_STEPS, path.size)) {
                val s = raw.getJSONObject(j)
                val scene = path[j]
                val results = ArrayList<Result>()
                val refs = s.optJSONArray("refs") ?: JSONArray()
                for (k in 0 until refs.length()) {
                    val id = refs.optString(k)
                    val r = book[id]
                    when {
                        r == null -> dropped += "thread $i step $j: $id is not a result"
                        r.role != ResultRole.FACT -> dropped += "thread $i step $j: $id is not a fact"
                        r.tool !in SCENE_TOOLS.getValue(scene) -> dropped += "thread $i step $j: $id (${r.tool}) does not serve $scene"
                        id in used -> dropped += "thread $i step $j: $id was used before"
                        else -> { results += r; used += id }
                    }
                }
                if (results.isEmpty()) { dropped += "thread $i step $j: no result left"; continue }
                val bridge = s.optString("bridge").trim()
                val okBridge = if (bridge.codePointCount(0, bridge.length) > MAX_BRIDGE || DIGIT.containsMatchIn(bridge)) {
                    dropped += "thread $i step $j: bridge emptied"; ""
                } else bridge
                steps += Step(scene, ResultBook.cut(s.optString("topic").trim(), MAX_TOPIC), results, okBridge)
            }
            if (steps.size < 2) { dropped += "thread $i: fewer than two steps"; continue }
            threads += TalkThread(steps.map { it.scene }, steps)
        }
        val spot = input.optJSONObject("spot")?.let { s ->
            val results = (0 until (s.optJSONArray("refs")?.length() ?: 0)).mapNotNull { k ->
                val id = s.getJSONArray("refs").optString(k)
                book[id]?.takeIf { it.role == ResultRole.FACT && it.tool in SCENE_TOOLS.getValue("spot") } ?: run { dropped += "spot: $id left out"; null }
            }.distinctBy { it.id }
            if (results.isEmpty()) null else SpotBundle(s.optString("spot"), results)
        }
        return Checked(threads, spot, dropped)
    }
}
