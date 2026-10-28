package io.github.duwenji.yorisoi.kit.geo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A frequent way a trip went along, and how many times that makes. */
data class WayPass(val way: FrequentWay, val passes: Int, val new: Boolean)

/**
 * The frequent ways, kept in [file] on this device only (REQ-FREQUENT-WAYS F-2, D-4), with the
 * ways the user deleted after they were found (D-7: not found again). [finder] holds the app's
 * values (a walker's ways are shorter than a rider's).
 */
class FrequentWayStore(
    private val file: File,
    private val finder: FrequentWayFinder,
    private val now: () -> Long = System::currentTimeMillis,
    private val onError: (Exception) -> Unit = {},
) {
    private var refused: List<List<LatLon>> = emptyList()

    private val _ways = MutableStateFlow(read())

    /** Every way, the one gone along last first. */
    val ways: StateFlow<List<FrequentWay>> = _ways.asStateFlow()

    /**
     * After a trip (F-3, F-4): counts the ways it went along, forgets the found ways not used for
     * [UNUSED_MS] and not saved, and, when [find], finds new ways among the [earlier] trips of the
     * last [WINDOW_MS]. A new way is named by [name], else numbered. Returns the ways gone along.
     */
    @Synchronized
    fun afterTrip(trip: Trip, earlier: List<Trip>, find: Boolean, name: (List<LatLon>) -> String?): List<WayPass> {
        val time = now()
        val kept = _ways.value.filter { it.saved || it.origin == WayOrigin.USER || it.lastPassedAt >= time - UNUSED_MS }
        val passed = mutableListOf<WayPass>()
        val ways = kept.map { way ->
            if (finder.passes(way, trip)) {
                way.copy(passes = way.passes + 1, lastPassedAt = trip.endedAt).also { passed += WayPass(it, it.passes, new = false) }
            } else {
                way
            }
        }.toMutableList()
        if (find) {
            val recent = earlier.filter { it.endedAt >= time - WINDOW_MS }
            var number = ways.size
            finder.find(trip, recent, ways.map { it.points }, refused).forEach { points ->
                number++
                val passes = 1 + recent.count { finder.passes(stub(points), it) }
                val way = FrequentWay(
                    id = "w${trip.endedAt}-$number", name = name(points) ?: "よく通る道 $number",
                    points = points, lengthM = FrequentWayFinder.lengthM(points), origin = WayOrigin.FOUND,
                    saved = false, passes = passes, lastPassedAt = trip.endedAt, createdAt = time,
                )
                ways += way
                passed += WayPass(way, passes, new = true)
            }
        }
        update(trimmed(ways))
        return passed.filter { p -> _ways.value.any { it.id == p.way.id } }
    }

    /** A way the user made from a trip (F-16), gone along when that trip ended. */
    @Synchronized
    fun add(name: String, points: List<LatLon>, passedAt: Long): FrequentWay {
        val time = now()
        val way = FrequentWay(
            id = "u$time", name = name, points = points, lengthM = FrequentWayFinder.lengthM(points),
            origin = WayOrigin.USER, saved = true, passes = 1, lastPassedAt = passedAt, createdAt = time,
        )
        update(trimmed(_ways.value + way))
        return way
    }

    @Synchronized
    fun rename(id: String, name: String) = update(_ways.value.map { if (it.id == id) it.copy(name = name) else it })

    /** Keeps a found way for good (D-7). */
    @Synchronized
    fun save(id: String) = update(_ways.value.map { if (it.id == id) it.copy(saved = true) else it })

    /** Deletes a way; a found one isn't found again (D-7). */
    @Synchronized
    fun delete(id: String) {
        val way = _ways.value.find { it.id == id } ?: return
        if (way.origin == WayOrigin.FOUND) refused = refused + listOf(way.points)
        update(_ways.value - way)
    }

    /** With the history cleared (F-2): forgets the found ways not saved, and the deleted ones. */
    @Synchronized
    fun clearFound() {
        refused = emptyList()
        update(_ways.value.filter { it.saved || it.origin == WayOrigin.USER })
    }

    /** At most [MAX_WAYS]: the found ways not saved go first, the one gone along longest ago first (N-6). */
    private fun trimmed(ways: List<FrequentWay>): List<FrequentWay> {
        val over = ways.size - MAX_WAYS
        if (over <= 0) return ways
        val dropped = ways.filter { !it.saved && it.origin == WayOrigin.FOUND }.sortedBy { it.lastPassedAt }.take(over).toSet()
        return ways - dropped
    }

    private fun update(ways: List<FrequentWay>) {
        val sorted = ways.sortedByDescending { it.lastPassedAt }
        try {
            write(sorted)
        } catch (e: IOException) {
            onError(e)
        }
        _ways.value = sorted
    }

    private fun read(): List<FrequentWay> {
        if (!file.exists()) return emptyList()
        return try {
            val json = JSONObject(file.readText())
            refused = json.optJSONArray("refused")?.let { a -> List(a.length()) { points(a.getJSONArray(it)) } }.orEmpty()
            val ways = json.getJSONArray("ways")
            List(ways.length()) { fromJson(ways.getJSONObject(it)) }
        } catch (e: IOException) {
            onError(e)
            emptyList()
        } catch (e: JSONException) {
            onError(e)
            emptyList()
        }
    }

    /** Through a temporary file, so the ways are never half written. */
    private fun write(ways: List<FrequentWay>) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("ways", JSONArray().apply { ways.forEach { put(toJson(it)) } })
            .put("refused", JSONArray().apply { refused.forEach { put(toJson(it)) } })
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not replace $file")
        }
    }

    private fun stub(points: List<LatLon>) = FrequentWay("", "", points, 0.0, WayOrigin.FOUND, false, 0, 0, 0)

    companion object {
        /** The trips a new way is found among (F-3). */
        const val WINDOW_MS = 60L * 24 * 60 * 60 * 1000

        /** A found way not saved goes when it isn't gone along for this long (D-7). */
        const val UNUSED_MS = 60L * 24 * 60 * 60 * 1000

        /** N-6. */
        const val MAX_WAYS = 50

        private fun toJson(points: List<LatLon>) = JSONArray().apply { points.forEach { put(JSONArray().put(it.lat).put(it.lon)) } }

        private fun points(a: JSONArray) = List(a.length()) { i -> a.getJSONArray(i).let { LatLon(it.getDouble(0), it.getDouble(1)) } }

        private fun toJson(way: FrequentWay) = JSONObject()
            .put("id", way.id).put("name", way.name).put("points", toJson(way.points)).put("lengthM", way.lengthM)
            .put("origin", way.origin.name).put("saved", way.saved).put("passes", way.passes)
            .put("lastPassedAt", way.lastPassedAt).put("createdAt", way.createdAt)

        private fun fromJson(o: JSONObject) = FrequentWay(
            id = o.getString("id"), name = o.getString("name"), points = points(o.getJSONArray("points")),
            lengthM = o.getDouble("lengthM"), origin = WayOrigin.valueOf(o.getString("origin")),
            saved = o.getBoolean("saved"), passes = o.getInt("passes"),
            lastPassedAt = o.getLong("lastPassedAt"), createdAt = o.getLong("createdAt"),
        )
    }
}
