package io.github.duwenji.yorisoi.kit.songs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The season's songs kept on the device (platform API-SONGS「アプリの取り方」): the catalog, fetched
 * again once a day, and the sounds of this season and the next, fetched ahead on an unmetered network.
 * Never fetches by itself: the app calls [refresh] and [prefetch] when not on a walk or ride (AS-1).
 *
 * [baseUrl] is the delivery's root (the spot tiles'); [userAgent] names the app and its version, and is
 * all that goes with a request besides what is asked for (N-6). [dir] is the app's own folder for them.
 */
class SongLibrary(
    private val dir: File,
    baseUrl: String,
    private val userAgent: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val base = baseUrl.trimEnd('/') + "/songs/v1/"
    private val lock = Mutex()
    private val audio = File(dir, "audio")

    /** The catalog kept, or null before one was fetched. */
    @Volatile
    var catalog: SongCatalog? = readCatalog()
        private set

    /**
     * Asks for the catalog again, if a day has passed since last time (or [force]): with its ETag, so an
     * unchanged one isn't sent. Removes the sounds the catalog no longer names. True when it could ask.
     */
    suspend fun refresh(force: Boolean = false): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val meta = readMeta()
            if (!force && now() - meta.checkedAt < DAY_MS) return@withContext true
            val request = Request.Builder().url(base + "catalog.json").header("User-Agent", userAgent)
                .apply { meta.etag?.let { header("If-None-Match", it) } }.build()
            try {
                http.newCall(request).execute().use { res ->
                    when {
                        res.code == 304 -> writeMeta(meta.etag, now())
                        res.isSuccessful -> {
                            val text = res.body?.string() ?: return@withContext false
                            val parsed = SongCatalog.parse(text) ?: return@withContext false
                            dir.mkdirs()
                            replace(File(dir, CATALOG), text)
                            writeMeta(res.header("ETag"), now())
                            catalog = parsed
                            val named = parsed.songs.map { it.file }.toSet()
                            audio.listFiles()?.filter { it.name !in named }?.forEach { it.delete() }
                        }
                        else -> return@withContext false
                    }
                }
                true
            } catch (e: IOException) {
                false
            }
        }
    }

    /**
     * Fetches the sounds of this season, the next and all seasons that aren't kept yet (AS-2), keeping
     * only those whose hash matches, then trims to [MAX_TOTAL_BYTES] (AS-3). The app calls it on an
     * unmetered network only. Returns how many it fetched.
     */
    suspend fun prefetch(): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val songs = catalog?.songs ?: return@withContext 0
            val season = SongSeason.now(now())
            val wanted = setOf(season, SongSeason.next(season), "all")
            var fetched = 0
            for (song in songs.filter { it.seasons.any(wanted::contains) && fileOf(it) == null }) {
                if (fetch(song)) fetched++
            }
            trim(wanted)
            fetched
        }
    }

    /** The sound of [song] on the device, if it is there and whole. */
    fun fileOf(song: Song): File? = File(audio, song.file).takeIf { it.isFile && it.length() == song.bytes }

    /**
     * A song to play now (AS-4): one whose sound is kept, of the season now (or all seasons), not in
     * [exclude] (played on this walk or the one before). Those that also fit [timeOfDay] and [weather]
     * (the catalog's words) come first; among the best, one at random. Null when there is none.
     */
    fun pick(timeOfDay: String?, weather: String?, exclude: Set<String>): Song? {
        val season = SongSeason.now(now())
        val ready = catalog?.songs.orEmpty()
            .filter { (season in it.seasons || "all" in it.seasons) && it.id !in exclude && fileOf(it) != null }
        if (ready.isEmpty()) return null
        fun fit(s: Song) = (if (timeOfDay != null && timeOfDay in s.timesOfDay) 1 else 0) + (if (weather != null && weather in s.weather) 1 else 0) +
            // A song of this season over one of all seasons.
            (if (season in s.seasons) 1 else 0)
        val best = ready.maxOf(::fit)
        return ready.filter { fit(it) == best }.random(random)
    }

    // ---- the songs played on the last walks (AS-4) ----

    /** The ids played on this walk or ride and the one before, not to be played again yet. */
    fun recentlyPlayed(): Set<String> = readPlayed().let { (it["last"].orEmpty() + it["now"].orEmpty()).toSet() }

    /** A walk or ride starts: what was played on the one before is kept, and this one's list starts empty. */
    fun startWalk() {
        val played = readPlayed()
        writePlayed(mapOf("last" to played["now"].orEmpty(), "now" to emptyList()))
    }

    fun played(song: Song) {
        val played = readPlayed()
        writePlayed(played + ("now" to (played["now"].orEmpty() + song.id)))
    }

    // ---- the files ----

    private fun fetch(song: Song): Boolean = try {
        val request = Request.Builder().url(base + "audio/" + song.file).header("User-Agent", userAgent).build()
        val bytes = http.newCall(request).execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
        if (bytes == null || bytes.size.toLong() != song.bytes || sha256(bytes) != song.sha256) {
            false
        } else {
            audio.mkdirs()
            val tmp = File(audio, song.file + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(File(audio, song.file)).also { if (!it) tmp.delete() }
        }
    } catch (e: IOException) {
        false
    }

    /** Within [MAX_TOTAL_BYTES]: the sounds of other seasons go first, then the largest. */
    private fun trim(wanted: Set<String>) {
        val songs = catalog?.songs.orEmpty().associateBy { it.file }
        val files = audio.listFiles { f -> f.name.endsWith(".m4a") }.orEmpty()
            .sortedWith(compareBy<File> { f -> if (songs[f.name]?.seasons?.any(wanted::contains) == true) 1 else 0 }.thenByDescending { it.length() })
            .toMutableList()
        var total = files.sumOf { it.length() }
        while (total > MAX_TOTAL_BYTES && files.isNotEmpty()) {
            val f = files.removeAt(0)
            total -= f.length()
            f.delete()
        }
    }

    private fun readCatalog(): SongCatalog? = try {
        File(dir, CATALOG).takeIf { it.isFile }?.readText()?.let(SongCatalog::parse)
    } catch (e: IOException) {
        null
    }

    private class Meta(val etag: String?, val checkedAt: Long)

    private fun readMeta(): Meta = try {
        val lines = File(dir, META).readLines()
        Meta(lines.getOrNull(1)?.ifEmpty { null }, lines.getOrNull(0)?.toLongOrNull() ?: 0L)
    } catch (e: IOException) {
        Meta(null, 0L)
    }

    private fun writeMeta(etag: String?, checkedAt: Long) {
        dir.mkdirs()
        replace(File(dir, META), "$checkedAt\n${etag.orEmpty()}")
    }

    @Synchronized
    private fun readPlayed(): Map<String, List<String>> = try {
        val o = JSONObject(File(dir, PLAYED).readText())
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map(a::getString) } }
    } catch (e: Exception) {
        emptyMap()
    }

    @Synchronized
    private fun writePlayed(played: Map<String, List<String>>) {
        dir.mkdirs()
        replace(File(dir, PLAYED), JSONObject(played.mapValues { JSONArray(it.value) }).toString())
    }

    private fun replace(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            tmp.delete()
        }
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** All the sounds kept on the device (AS-3). */
        const val MAX_TOTAL_BYTES = 10L * 1024 * 1024

        private const val CATALOG = "catalog.json"
        private const val META = "catalog.meta"
        private const val PLAYED = "played.json"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
