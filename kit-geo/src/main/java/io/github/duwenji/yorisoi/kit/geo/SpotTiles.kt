package io.github.duwenji.yorisoi.kit.geo

import android.util.Log
import io.github.duwenji.yorisoi.core.format.SpotKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Spots, amenities, shops and traffic signals from the tiles the operator makes each week from
 * OpenStreetMap (API-SPOT-TILES), instead of asking the busy public Overpass servers. Given to
 * [SpotRepository], which asks Overpass only when a tile can't be had. [baseUrl] is where the tiles
 * are served; [userAgent] names the app and its version, and is all that goes with a request
 * besides the tile's name.
 */
class SpotTiles(
    baseUrl: String,
    private val userAgent: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val base = baseUrl.trimEnd('/')

    /** The tiles read last, so that a search every 300m doesn't read them from the disk again. */
    private val memory = object : LinkedHashMap<String, Tile>(MEMORY_TILES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Tile>) = size > MEMORY_TILES
    }

    /** Tiles the server has none of (404, outside Japan); not asked about again while the app runs. */
    private val outOfRange = mutableSetOf<String>()

    /** What the tiles have around a point; [asked] when any tile was asked for just now (200 or 304). */
    internal class Found(val result: NearbyResult, val asked: Boolean)

    /**
     * The answer around the point, cut to the distances an Overpass search uses (API-SPOT-TILES
     * AP-1〜AP-3); null when any of the tiles it needs can't be had (AP-7). Kept tiles are used
     * while they are newer than [freshMs], and up to [SpotCache.MAX_AGE_MS] when asking fails.
     * Without a [store] the tiles are only kept in memory.
     */
    internal suspend fun nearby(
        lat: Double,
        lon: Double,
        shops: Set<SpotKind>,
        signals: Boolean,
        facilities: Set<FacilityKind>,
        store: SpotTileStore?,
        freshMs: Long,
        forceRefresh: Boolean = false,
    ): Found? {
        val got = Geohash.covering(lat, lon, OverpassClient.RADIUS_M.toDouble(), PRECISION).flatMap { hash ->
            layers(shops).map { layer -> tile(layer, hash, store, freshMs, forceRefresh).takeIf { it.tile != null } ?: return null }
        }
        val here = LatLon(lat, lon)
        val found = NearbyCollector()
        for (tile in got.mapNotNull { it.tile }) {
            tile.spots.filter { distanceM(it, here) <= OverpassClient.RADIUS_M }.forEach(found::spot)
            tile.shops.filter { OverpassClient.shopKindOf(it.tags) in shops && distanceM(it, here) <= OverpassClient.SHOP_RADIUS_M }.forEach(found::spot)
            tile.facilities
                .filter { it.kind in facilities }
                .filter { RouteGeometry.distanceM(here, LatLon(it.lat, it.lon)) <= if (it.kind.near) OverpassClient.CLOSE_RADIUS_M else OverpassClient.RADIUS_M }
                .forEach(found::facility)
            if (signals) {
                for (i in 0 until tile.signals.size - 1 step 2) {
                    val at = LatLon(tile.signals[i], tile.signals[i + 1])
                    if (RouteGeometry.distanceM(here, at) <= OverpassClient.RADIUS_M) found.signal(at)
                }
            }
        }
        return Found(found.result(), got.any { it.asked })
    }

    /**
     * Fetches the tiles around [points] that aren't kept fresh, at most [max] of them, and keeps
     * them in [store]. Returns how many it asked for, and whether it gave up (a tile couldn't be had).
     */
    internal suspend fun prefetch(
        points: List<LatLon>,
        shops: Set<SpotKind>,
        store: SpotTileStore,
        freshMs: Long,
        max: Int,
    ): Pair<Int, Boolean> {
        var asked = 0
        val hashes = points.flatMap { Geohash.covering(it.lat, it.lon, OverpassClient.RADIUS_M.toDouble(), PRECISION) }.distinct()
        for (hash in hashes) {
            for (layer in layers(shops)) {
                if (asked >= max) return asked to false
                val got = tile(layer, hash, store, freshMs, forceRefresh = false)
                if (got.asked) asked++
                if (got.failed) return asked to true
            }
        }
        return asked to false
    }

    private fun layers(shops: Set<SpotKind>) = if (shops.isEmpty()) listOf(BASE) else listOf(BASE, SHOPS)

    /** A spot's distance: to its outline when it has one (as Overpass's `around` finds it), else to its point. */
    private fun distanceM(spot: Poi, from: LatLon): Double =
        spot.shape?.distanceM(from.lat, from.lon) ?: RouteGeometry.distanceM(from, LatLon(spot.lat, spot.lon))

    /** A tile read, or null; [asked] when the server answered just now, [failed] when it couldn't be asked. */
    private class Got(val tile: Tile?, val asked: Boolean, val failed: Boolean = false)

    private suspend fun tile(layer: String, hash: String, store: SpotTileStore?, freshMs: Long, forceRefresh: Boolean): Got {
        val key = "$layer/$hash"
        if (synchronized(memory) { key in outOfRange }) return Got(null, asked = false)
        val kept = synchronized(memory) { memory[key] } ?: store?.read(layer, hash)?.let { k ->
            read(layer, hash, k.body, k.etag, k.fetchedAt).also { if (it == null) store.delete(layer, hash) }
        }?.also(::remember)
        if (kept != null && !forceRefresh && now() - kept.fetchedAt <= freshMs) return Got(kept, asked = false)
        val usable = kept?.takeIf { now() - it.fetchedAt <= SpotCache.MAX_AGE_MS }
        val answer = try {
            fetch(layer, hash, kept?.etag)
        } catch (e: IOException) {
            Log.w(TAG, "${e.javaClass.simpleName} $key: ${e.message}")
            return Got(usable, asked = false, failed = true)
        }
        return when (answer) {
            is Answer.Body -> {
                val tile = read(layer, hash, answer.text, answer.etag, now()) ?: return Got(usable, asked = false, failed = true)
                store?.write(layer, hash, answer.text, answer.etag)
                remember(tile)
                Got(tile, asked = true)
            }
            Answer.NotModified -> {
                val tile = kept?.copy(fetchedAt = now()) ?: return Got(null, asked = false, failed = true)
                store?.touch(layer, hash)
                remember(tile)
                Got(tile, asked = true)
            }
            Answer.OutOfRange -> {
                synchronized(memory) {
                    memory.remove(key)
                    outOfRange += key
                }
                store?.delete(layer, hash)
                Got(null, asked = true)
            }
        }
    }

    private fun remember(tile: Tile) = synchronized(memory) { memory["${tile.layer}/${tile.hash}"] = tile }

    private sealed interface Answer {
        class Body(val text: String, val etag: String?) : Answer
        data object NotModified : Answer
        data object OutOfRange : Answer
    }

    private suspend fun fetch(layer: String, hash: String, etag: String?): Answer = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/v1/$layer/$hash.json")
            .header("User-Agent", userAgent)
            .apply { etag?.let { header("If-None-Match", it) } }
            .build()
        http.newCall(request).execute().use { res ->
            when {
                res.code == 304 -> Answer.NotModified
                res.code == 404 -> Answer.OutOfRange
                res.isSuccessful -> Answer.Body(res.body!!.string(), res.header("ETag"))
                else -> throw IOException("Spot tile $layer/$hash: HTTP ${res.code}")
            }
        }
    }

    /** One layer of one tile, as read from its JSON. */
    internal data class Tile(
        val layer: String,
        val hash: String,
        val etag: String?,
        val fetchedAt: Long,
        val spots: List<Poi>,
        val shops: List<Poi>,
        val facilities: List<Facility>,
        val signals: DoubleArray,
    )

    /** The tile, or null when it isn't one this version reads (API-SPOT-TILES AP-8). */
    private fun read(layer: String, hash: String, body: String, etag: String?, fetchedAt: Long): Tile? = try {
        parse(body, layer, hash).copy(etag = etag, fetchedAt = fetchedAt)
    } catch (e: JSONException) {
        Log.w(TAG, "Unreadable tile $layer/$hash: ${e.message}")
        null
    }

    internal companion object {
        private const val TAG = "SpotTiles"
        const val BASE = "base"
        const val SHOPS = "shops"
        const val PRECISION = 5
        private const val FORMAT = "yorisoi-spot-tile"
        private const val VERSION = 1

        /** The four tiles of a search, of both layers. */
        private const val MEMORY_TILES = 8

        fun parse(json: String, layer: String, hash: String): Tile {
            val o = JSONObject(json)
            if (o.optString("format") != FORMAT || o.optInt("version") != VERSION || o.optString("layer") != layer || o.optString("tile") != hash) {
                throw JSONException("not a $FORMAT v$VERSION $layer/$hash")
            }
            val facilities = o.optJSONArray("facilities")?.objects().orEmpty().mapNotNull { f ->
                // A kind this version doesn't know is left out.
                val kind = FacilityKind.entries.firstOrNull { it.name == f.getString("kind") } ?: return@mapNotNull null
                val name = if (f.has("name")) f.getString("name") else null
                Facility(f.getString("id"), kind, f.getDouble("lat"), f.getDouble("lon"), OverpassClient.facilityName(kind, name, f.optBoolean("publicTransport")))
            }
            val signals = o.optJSONArray("signals")?.let { a -> DoubleArray(a.length()) { a.getDouble(it) } } ?: DoubleArray(0)
            return Tile(
                layer, hash, null, 0L,
                o.optJSONArray("spots")?.objects().orEmpty().mapNotNull(::poiOf),
                o.optJSONArray("shops")?.objects().orEmpty().mapNotNull(::poiOf),
                facilities, signals,
            )
        }

        private fun poiOf(e: JSONObject): Poi? {
            val tagsJson = e.getJSONObject("tags")
            val tags = tagsJson.keys().asSequence().associateWith { tagsJson.getString(it) }
            val name = tags["name:ja"] ?: tags["name"] ?: return null
            val shape = e.optJSONObject("shape")?.let { s ->
                val lines = s.getJSONArray("lines").let { a -> (0 until a.length()).map { a.getJSONArray(it) } }
                    .map { line -> DoubleArray(line.length()) { line.getDouble(it) } }
                    .filter { it.size >= 4 }
                if (lines.isEmpty()) null else Shape(lines, s.getBoolean("area"))
            }
            return Poi(e.getString("id"), name, e.getDouble("lat"), e.getDouble("lon"), OverpassClient.categoryOf(tags), tags, shape)
        }

        private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    }
}

/**
 * Spot tiles kept on the device, in [SpotCache]'s folder so that clearing it clears them too:
 * each layer of each tile as it came, beside when it was fetched and its ETag.
 */
internal class SpotTileStore(
    private val dir: File,
    private val now: () -> Long,
    private val maxBytes: Long = MAX_BYTES,
) {
    class Kept(val body: String, val etag: String?, val fetchedAt: Long)

    @Synchronized
    fun read(layer: String, hash: String): Kept? = try {
        val meta = meta(layer, hash).takeIf(File::isFile)?.readLines() ?: return null
        val fetchedAt = meta.getOrNull(0)?.toLongOrNull() ?: return null
        Kept(body(layer, hash).readText(), meta.getOrNull(1)?.ifEmpty { null }, fetchedAt)
    } catch (e: IOException) {
        null
    }

    @Synchronized
    fun write(layer: String, hash: String, body: String, etag: String?) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        if (!replace(body(layer, hash), body) || !replace(meta(layer, hash), "${now()}\n${etag.orEmpty()}")) {
            delete(layer, hash)
            return
        }
        trim()
    }

    /** The kept tile was asked about and hasn't changed (304). */
    @Synchronized
    fun touch(layer: String, hash: String) {
        val meta = meta(layer, hash)
        val etag = try { meta.readLines().getOrNull(1) } catch (e: IOException) { null } ?: return
        replace(meta, "${now()}\n$etag")
    }

    @Synchronized
    fun delete(layer: String, hash: String) {
        body(layer, hash).delete()
        meta(layer, hash).delete()
    }

    /** Drops tiles past [SpotCache.MAX_AGE_MS], then the oldest until all fit in [maxBytes]. */
    private fun trim() {
        val kept = dir.listFiles { f -> f.name.endsWith(META) }.orEmpty().mapNotNull { m ->
            val fetchedAt = try { m.readLines().firstOrNull()?.toLongOrNull() } catch (e: IOException) { null } ?: return@mapNotNull null
            Triple(m, File(dir, m.name.removeSuffix(META) + BODY), fetchedAt)
        }.sortedByDescending { it.third }.toMutableList()
        val oldest = now() - SpotCache.MAX_AGE_MS
        kept.filter { it.third < oldest }.forEach { it.first.delete(); it.second.delete(); kept.remove(it) }
        var total = kept.sumOf { it.second.length() }
        while (total > maxBytes && kept.isNotEmpty()) {
            val last = kept.removeAt(kept.lastIndex)
            total -= last.second.length()
            last.first.delete()
            last.second.delete()
        }
    }

    private fun replace(file: File, text: String): Boolean {
        val tmp = File(file.path + ".tmp")
        return try {
            tmp.writeText(text)
            (tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))).also { if (!it) tmp.delete() }
        } catch (e: IOException) {
            tmp.delete()
            false
        }
    }

    private fun body(layer: String, hash: String) = File(dir, "${layer}_$hash$BODY")
    private fun meta(layer: String, hash: String) = File(dir, "${layer}_$hash$META")

    companion object {
        /** About 60 tiles of a busy town. */
        const val MAX_BYTES = 30L * 1024 * 1024

        private const val BODY = ".json"
        private const val META = ".meta"
    }
}
