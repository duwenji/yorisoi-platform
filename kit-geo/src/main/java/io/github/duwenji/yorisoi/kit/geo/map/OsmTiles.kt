package io.github.duwenji.yorisoi.kit.geo.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import java.io.IOException
import java.util.concurrent.TimeUnit

/** tile.openstreetmap.org answered, but not with a tile (blocked, overloaded); [message] is for the user. */
class OsmTilesException(message: String) : IOException(message)

/**
 * Whether OpenStreetMap's tile server will serve this app, before the map switches to it.
 * The server has no SLA and may block an app without notice (OSMF tile usage policy), so the
 * app falls back to the GSI map when this fails. Tiles themselves are downloaded by the map view.
 */
class OsmTiles(
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
) {
    @Volatile private var servedAt = 0L

    /** Asks for one tile, with the app's user agent; at most once an hour while it keeps working. */
    suspend fun check() {
        if (System.currentTimeMillis() - servedAt < RECHECK_MS) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(CHECK_URL)
                // The same user agent the map view sends, which the policy requires to name the app.
                .header("User-Agent", Configuration.getInstance().userAgentValue)
                .build()
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw errorOf(res.code)
            }
        }
        servedAt = System.currentTimeMillis()
    }

    internal companion object {
        /** The whole world at zoom 0: the tile most likely already in the server's cache. */
        const val CHECK_URL = "https://tile.openstreetmap.org/0/0/0.png"
        const val RECHECK_MS = 60 * 60 * 1000L

        fun errorOf(code: Int) = OsmTilesException(
            when (code) {
                403 -> "OpenStreetMap の地図の利用を断られました"
                429 -> "OpenStreetMap の地図が混み合っています"
                else -> "OpenStreetMap の地図を取得できませんでした（HTTP $code）"
            },
        )
    }
}

/**
 * Counts tile downloads that came back empty in a row; [record] says when there have been
 * [limit] of them with no tile in between, and starts counting again.
 */
internal class FailureStreak(private val limit: Int = 4) {
    private var count = 0

    @Synchronized
    fun record(ok: Boolean): Boolean {
        count = if (ok) 0 else count + 1
        if (count < limit) return false
        count = 0
        return true
    }
}
