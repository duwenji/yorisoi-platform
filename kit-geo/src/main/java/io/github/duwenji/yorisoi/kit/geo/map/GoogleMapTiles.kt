package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.core.text.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A Map Tiles API session: every tile request carries [token]. [expiresAt] is epoch ms. */
data class GoogleTileSession(val token: String, val expiresAt: Long)

/** The key was refused, or the Map Tiles API isn't enabled for it; [message] is for the user. */
class GoogleMapsException(message: String) : IOException(message)

/**
 * Google's Map Tiles API (2D tiles) with the user's own API key: creates the session that
 * tile URLs need, and fetches the attribution that has to be shown for the visible area.
 * Tiles themselves are downloaded by the map view.
 */
class GoogleMapTiles(
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    // A session lasts two weeks; one per key and map type is enough for the app's lifetime.
    private val sessions = ConcurrentHashMap<String, GoogleTileSession>()

    suspend fun session(apiKey: String, type: GoogleMapType): GoogleTileSession {
        val cacheKey = "${type.apiName}/$apiKey"
        sessions[cacheKey]?.takeIf { it.expiresAt - System.currentTimeMillis() > RENEW_BEFORE_MS }?.let { return it }
        return withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("mapType", type.apiName)
                .put("language", "ja-JP")
                .put("region", "JP")
                .toString()
                .toRequestBody("application/json".toMediaType())
            val url = "$BASE/v1/createSession".toHttpUrl().newBuilder().addQueryParameter("key", apiKey).build()
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw errorOf(res.code, text)
                parseSession(text)
            }
        }.also { sessions[cacheKey] = it }
    }

    /** The attribution for the area on screen, e.g. "Map data ©2026 Google, ZENRIN". */
    suspend fun copyright(
        apiKey: String, session: GoogleTileSession, zoom: Int,
        north: Double, south: Double, east: Double, west: Double,
    ): String = withContext(Dispatchers.IO) {
        fun deg(v: Double) = String.format(Locale.ROOT, "%.6f", v)
        val url = "$BASE/tile/v1/viewport".toHttpUrl().newBuilder()
            .addQueryParameter("session", session.token)
            .addQueryParameter("key", apiKey)
            .addQueryParameter("zoom", zoom.toString())
            .addQueryParameter("north", deg(north))
            .addQueryParameter("south", deg(south))
            .addQueryParameter("east", deg(east))
            .addQueryParameter("west", deg(west))
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw errorOf(res.code, text)
            JSONObject(text).optString("copyright")
        }
    }

    internal companion object {
        const val BASE = "https://tile.googleapis.com"
        /** Renew a day early, so a session never runs out while the map is open. */
        const val RENEW_BEFORE_MS = 24 * 60 * 60 * 1000L

        /** `https://tile.googleapis.com/v1/2dtiles/{z}/{x}/{y}?session=...&key=...` */
        fun tileUrl(z: Int, x: Int, y: Int, session: GoogleTileSession, apiKey: String) =
            "$BASE/v1/2dtiles/$z/$x/$y?session=${session.token}&key=$apiKey"

        fun parseSession(body: String): GoogleTileSession {
            val json = JSONObject(body)
            val token = json.optString("session").ifEmpty { throw IOException("Map Tiles API: no session in the response") }
            // Seconds since the epoch, sent as a string.
            val expiry = json.optString("expiry").toLongOrNull() ?: throw IOException("Map Tiles API: no expiry")
            return GoogleTileSession(token, expiry * 1000)
        }

        fun errorOf(code: Int, body: String): IOException {
            val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
            return when (code) {
                400, 401, 403 -> GoogleMapsException(
                    localized(
                        "Google マップの APIキーが使えませんでした。キーと、Map Tiles API が有効になっているかを確認してください",
                        "The Google Maps API key could not be used. Check the key and that the Map Tiles API is enabled",
                        "无法使用 Google 地图的 API 密钥。请检查密钥以及 Map Tiles API 是否已启用",
                        "Google 지도 API 키를 사용할 수 없었습니다. 키와 Map Tiles API가 사용 설정되어 있는지 확인해 주세요",
                    ) + if (detail.isNotEmpty()) " ($detail)" else "",
                )
                429 -> GoogleMapsException(
                    localized(
                        "Google マップの利用上限に達しました。しばらくしてから試してください", "The Google Maps limit is reached. Try again in a while",
                        "已达到 Google 地图的使用上限，请稍后再试", "Google 지도 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요",
                    ),
                )
                else -> IOException("Map Tiles API error: HTTP $code $detail")
            }
        }
    }
}
