package io.github.duwenji.yorisoi.kit.songs

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar

/** Who wrote a song's words or tune, as the catalog says (platform API-SONGS `songs[].rights`). */
data class SongMaker(val name: String, val died: Int?)

/** One song of the catalog (platform API-SONGS「catalog.json」). */
data class Song(
    val id: String,
    val title: String,
    val seasons: Set<String>,
    val timesOfDay: Set<String>,
    val weather: Set<String>,
    val durationSec: Int,
    val file: String,
    val bytes: Long,
    val sha256: String,
    val lyricist: SongMaker,
    val composer: SongMaker,
    /** The first songbook. */
    val score: String,
    /** Where the notes were taken from, as a line to show (its title and licence). */
    val notesFrom: String,
)

/** The catalog: the line to show in the app's "about" and the songs (AS-6). */
data class SongCatalog(val updatedAt: String, val credit: String, val songs: List<Song>) {
    companion object {
        const val FORMAT = "yorisoi-songs"
        const val VERSION = 1
        const val MAX_BYTES = 1_000_000L
        private val ID = Regex("^[a-z0-9-]{2,40}$")
        private val SHA = Regex("^[0-9a-f]{64}$")

        /**
         * The catalog in [json], or null when it isn't one this version reads (its format or version,
         * or a field it must have). A song whose fields are wrong is left out, the others kept (エラー表).
         * Values a list doesn't know are dropped, as if not there.
         */
        fun parse(json: String): SongCatalog? = try {
            val o = JSONObject(json)
            if (o.optString("format") != FORMAT || o.optInt("version") != VERSION) {
                null
            } else {
                val songs = o.getJSONArray("songs")
                SongCatalog(
                    o.getString("updatedAt"),
                    o.getString("credit"),
                    (0 until songs.length()).mapNotNull { songOf(songs.getJSONObject(it)) },
                )
            }
        } catch (e: JSONException) {
            null
        }

        private fun songOf(s: JSONObject): Song? = try {
            val rights = s.getJSONObject("rights")
            val notes = rights.optJSONObject("notesFrom")
            val song = Song(
                id = s.getString("id"),
                title = s.getString("title"),
                seasons = s.getJSONArray("seasons").strings().filter { it in SongSeason.VALUES }.toSet(),
                timesOfDay = s.getJSONArray("timesOfDay").strings().filter { it in TIMES }.toSet(),
                weather = s.getJSONArray("weather").strings().filter { it in WEATHER }.toSet(),
                durationSec = s.getInt("durationSec"),
                file = s.getString("file"),
                bytes = s.getLong("bytes"),
                sha256 = s.getString("sha256"),
                lyricist = makerOf(rights.getJSONObject("lyricist")),
                composer = makerOf(rights.getJSONObject("composer")),
                score = rights.getString("score"),
                notesFrom = notes?.let { "${it.optString("title")}（${it.optString("license")}）" }.orEmpty(),
            )
            song.takeIf {
                ID.matches(it.id) && it.seasons.isNotEmpty() && it.bytes in 1..MAX_BYTES && SHA.matches(it.sha256) &&
                    !it.file.contains('/') && it.file.endsWith(".m4a")
            }
        } catch (e: JSONException) {
            null
        }

        private fun makerOf(o: JSONObject) = SongMaker(o.getString("name"), if (o.isNull("died")) null else o.getInt("died"))

        private fun JSONArray.strings() = (0 until length()).map(::getString)

        val TIMES = setOf("morning", "day", "evening", "night")
        val WEATHER = setOf("clear", "cloudy", "rain", "snow")
    }
}

/** The seasons by the device's date (AS-5): March to May spring, and so on. */
object SongSeason {
    val VALUES = setOf("spring", "summer", "autumn", "winter", "all")
    private val ORDER = listOf("winter", "spring", "summer", "autumn")

    /** The season of [month] (1–12). */
    fun of(month: Int): String = ORDER[(month % 12) / 3]

    fun next(season: String): String = ORDER[(ORDER.indexOf(season) + 1) % 4]

    fun now(time: Long): String = of(Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.MONTH) + 1)
}
