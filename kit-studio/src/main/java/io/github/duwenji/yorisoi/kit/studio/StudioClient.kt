package io.github.duwenji.yorisoi.kit.studio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** A note as the studio lists it (API-STUDIO「一言の項目」). */
data class Note(
    val id: String,
    val text: String,
    /** geohash7 */
    val cell: String,
    val spotName: String?,
    val expiresAt: Instant,
    val mine: Boolean,
)

data class PlacementAudio(val url: String, val durationSec: Double, val bytes: Long, val contentType: String, val sha256: String)

/** Reading hours in Japan, `HH:mm` to `HH:mm`. */
data class Hours(val from: String, val to: String)

/** A placement as the studio lists it (API-STUDIO「掲載の項目」). */
data class Placement(
    val id: String,
    val providerName: String,
    val text: String,
    val audio: PlacementAudio?,
    /** geohash6 */
    val cells: List<String>,
    val startsAt: Instant,
    val endsAt: Instant,
    val hours: Hours?,
)

data class CellList(val notes: List<Note>, val placements: List<Placement>, val fetchedAt: Instant, val refetchAfterSec: Int)

data class PlacedNote(val id: String, val expiresAt: Instant, val remainingToday: Int)

/** API-STUDIO「概数」: null below 3. */
data class Approx(val atLeast: Int)

data class MyNote(
    val id: String,
    val text: String,
    val cell: String,
    val spotName: String?,
    val expiresAt: Instant,
    /** `active`, `hidden` (by reports) or `removed`. */
    val state: String,
    val heard: Approx?,
    val likes: Approx?,
)

data class Block(val id: String, val createdAt: Instant, val expiresAt: Instant)

data class Terms(val version: Int, val url: String)

enum class NoteTtl(val wire: String) { HOUR("1h"), DAY("24h"), WEEK("7d") }

enum class ReportReason(val wire: String) { DANGER("danger"), HARASSMENT("harassment"), AD("ad"), RIGHTS("rights"), INAPPROPRIATE("inappropriate"), OTHER("other") }

/** One heard or opened note or placement (API-STUDIO `reactions`). */
data class Ref(val type: String, val id: String) {
    companion object {
        fun note(id: String) = Ref("note", id)
        fun placement(id: String) = Ref("placement", id)
    }
}

/**
 * A studio failure: [code] is API-STUDIO's, or `network` when the studio couldn't be reached.
 * [until] (a ban) and [resetsAt] (today's notes) come with some codes.
 */
class StudioException(
    val code: String,
    message: String,
    val until: Instant? = null,
    val resetsAt: Instant? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Where the studio's bearer token comes from: the operator's AI relay (API-STUDIO ST-2). */
interface StudioTokens {
    suspend fun token(): String
    fun forget()
}

/** What [StudioFeed] needs of the studio; [StudioClient] in the app, a fake in tests. */
interface StudioApi {
    suspend fun cells(cells: List<String>, notes: Boolean, placements: Boolean): CellList
    suspend fun reactions(heard: List<Ref>, opened: List<Ref>, liked: List<String>)
}

/** The studio's API for the apps (platform API-STUDIO). */
class StudioClient(
    baseUrl: String,
    private val tokens: StudioTokens,
    private val http: OkHttpClient = defaultHttp,
) : StudioApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun cells(cells: List<String>, notes: Boolean, placements: Boolean): CellList {
        val kinds = listOfNotNull("note".takeIf { notes }, "placement".takeIf { placements }).joinToString(",")
        val url = "$base/v1/studio/cells".toHttpUrl().newBuilder()
            .addQueryParameter("cells", cells.joinToString(","))
            .addQueryParameter("kinds", kinds)
            .build().toString()
        val out = authorized { token -> call("GET", url, null, token) }!!
        return CellList(
            notes = out.getJSONArray("notes").objects().map(::note),
            placements = out.getJSONArray("placements").objects().map(::placement),
            fetchedAt = Instant.parse(out.getString("fetchedAt")),
            refetchAfterSec = out.optInt("refetchAfterSec", 300),
        )
    }

    suspend fun placeNote(text: String, cell: String, spotName: String?, ttl: NoteTtl, termsVersion: Int): PlacedNote {
        val body = JSONObject().put("text", text).put("cell", cell).put("ttl", ttl.wire).put("termsVersion", termsVersion)
        spotName?.let { body.put("spotName", it) }
        val out = authorized { token -> call("POST", "$base/v1/studio/notes", body, token) }!!
        return PlacedNote(out.getString("id"), Instant.parse(out.getString("expiresAt")), out.getInt("remainingToday"))
    }

    /** My notes still up, and today's notes left. */
    suspend fun myNotes(): Pair<List<MyNote>, Int> {
        val out = authorized { token -> call("GET", "$base/v1/studio/notes/mine", null, token) }!!
        val notes = out.getJSONArray("notes").objects().map {
            MyNote(
                it.getString("id"), it.getString("text"), it.getString("cell"), it.optStringOrNull("spotName"),
                Instant.parse(it.getString("expiresAt")), it.getString("state"), approx(it.opt("heard")), approx(it.opt("likes")),
            )
        }
        return notes to out.getInt("remainingToday")
    }

    suspend fun deleteNote(id: String) {
        authorized { token -> call("DELETE", "$base/v1/studio/notes/$id", null, token) }
    }

    override suspend fun reactions(heard: List<Ref>, opened: List<Ref>, liked: List<String>) {
        val refs = { list: List<Ref> -> JSONArray(list.map { JSONObject().put("type", it.type).put("id", it.id) }) }
        val body = JSONObject().put("heard", refs(heard)).put("opened", refs(opened)).put("liked", JSONArray(liked))
        authorized { token -> call("POST", "$base/v1/studio/reactions", body, token) }
    }

    suspend fun report(ref: Ref, reason: ReportReason) {
        val body = JSONObject().put("type", ref.type).put("id", ref.id).put("reason", reason.wire)
        authorized { token -> call("POST", "$base/v1/studio/reports", body, token) }
    }

    suspend fun block(noteId: String): Block {
        val out = authorized { token -> call("POST", "$base/v1/studio/blocks", JSONObject().put("noteId", noteId), token) }!!
        return Block(out.getString("id"), Instant.parse(out.getString("createdAt")), Instant.parse(out.getString("createdAt")).plusSeconds(365L * 24 * 3600))
    }

    suspend fun blocks(): List<Block> {
        val out = authorized { token -> call("GET", "$base/v1/studio/blocks", null, token) }!!
        return out.getJSONArray("blocks").objects().map { Block(it.getString("id"), Instant.parse(it.getString("createdAt")), Instant.parse(it.getString("expiresAt"))) }
    }

    suspend fun unblock(id: String) {
        authorized { token -> call("DELETE", "$base/v1/studio/blocks/$id", null, token) }
    }

    suspend fun terms(): Terms {
        val out = withContext(Dispatchers.IO) { call("GET", "$base/v1/studio/terms", null, null)!! }
        return Terms(out.getInt("version"), out.getString("url"))
    }

    /** Runs [block] with the relay's token, once more with a new session if the token was unknown. */
    private suspend fun authorized(block: (String) -> JSONObject?): JSONObject? = withContext(Dispatchers.IO) {
        try {
            block(tokens.token())
        } catch (e: StudioException) {
            if (e.code != "token_invalid") throw e
            tokens.forget()
            block(tokens.token())
        }
    }

    private fun call(method: String, url: String, body: JSONObject?, token: String?): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.isSuccessful) return if (text.isBlank()) null else JSONObject(text)
                val problem = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    JSONObject()
                }
                // WAF answers 429 without a problem body (API-STUDIO エラー表).
                val code = problem.optString("code").ifEmpty {
                    when {
                        res.code == 429 -> "too_many_requests"
                        res.code >= 500 -> "upstream_error"
                        else -> "invalid_request"
                    }
                }
                throw StudioException(
                    code, messageFor(code),
                    until = problem.optStringOrNull("until")?.let(Instant::parse),
                    resetsAt = problem.optStringOrNull("resetsAt")?.let(Instant::parse),
                )
            }
        } catch (e: StudioException) {
            throw e
        } catch (e: IOException) {
            throw StudioException("network", "スタジオに接続できませんでした", cause = e)
        } catch (e: JSONException) {
            throw StudioException("invalid_response", "スタジオの応答を解釈できませんでした", cause = e)
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        internal val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            // A note's check calls the operator's AI before answering.
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()

        /** What the user is told about a note that wasn't placed, or another failure. */
        fun messageFor(code: String): String = when (code) {
            "note_rejected" -> "置けませんでした"
            "daily_note_limit" -> "今日置ける一言（10 件）を使い切りました"
            "author_banned" -> "この端末からは一言を置けません"
            "area_closed" -> "この辺りには置けません"
            "terms_not_accepted" -> "利用規約に同意してから置いてください"
            "text_too_long" -> "60 文字までです"
            "relay_paused", "upstream_error" -> "今は置けません。少し待ってからお試しください"
            "studio_paused" -> "スタジオは今止まっています"
            "too_many_requests" -> "混み合っています。少し待ってからお試しください"
            else -> "スタジオでエラーが発生しました"
        }

        internal fun note(o: JSONObject) = Note(
            o.getString("id"), o.getString("text"), o.getString("cell"), o.optStringOrNull("spotName"),
            Instant.parse(o.getString("expiresAt")), o.optBoolean("mine", false),
        )

        internal fun placement(o: JSONObject) = Placement(
            id = o.getString("id"),
            providerName = o.getString("providerName"),
            text = o.getString("text"),
            audio = o.optJSONObject("audio")?.let {
                PlacementAudio(it.getString("url"), it.getDouble("durationSec"), it.getLong("bytes"), it.getString("contentType"), it.getString("sha256"))
            },
            cells = o.getJSONArray("cells").let { a -> (0 until a.length()).map(a::getString) },
            startsAt = Instant.parse(o.getString("startsAt")),
            endsAt = Instant.parse(o.getString("endsAt")),
            hours = o.optJSONObject("hours")?.let { Hours(it.getString("from"), it.getString("to")) },
        )

        private fun approx(value: Any?): Approx? = (value as? JSONObject)?.let { Approx(it.getInt("atLeast")) }
    }
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

internal fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

/** The relay's session as the studio's tokens (API-STUDIO ST-2). */
class RelayTokens(private val relay: io.github.duwenji.yorisoi.kit.llm.RelayClient) : StudioTokens {
    override suspend fun token(): String = relay.token()
    override fun forget() = relay.forgetToken()
}
