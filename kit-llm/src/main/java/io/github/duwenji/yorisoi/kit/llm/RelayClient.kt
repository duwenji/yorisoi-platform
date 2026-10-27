package io.github.duwenji.yorisoi.kit.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

/** The app as the relay knows it (API-AI-RELAY `app`). */
data class RelayApp(val packageName: String, val versionCode: Long)

/** Today's calls on the operator's AI (API-AI-RELAY `quota`). */
data class RelayQuota(val limit: Int, val remaining: Int, val resetsAt: Instant) {
    /** Whether today's calls are used up and the day hasn't turned yet. */
    fun exhausted(now: Instant = Instant.now()): Boolean = remaining <= 0 && now.isBefore(resetsAt)
}

/**
 * Where the install id and the session token live on the device. Neither is backed up nor moved
 * to another device (API-AI-RELAY AR-2).
 */
interface RelaySessionStore {
    /** The install id: a UUID (v4) made the first time and kept. */
    fun installId(): String
    fun session(): Pair<String, Instant>?
    fun saveSession(token: String, expiresAt: Instant)
    fun clearSession()
}

/** A Play Integrity Standard token bound to [requestHash] (API-AI-RELAY AR-3). */
fun interface IntegrityTokenProvider {
    suspend fun token(requestHash: String): String
}

/** A relay failure; [code] is API-AI-RELAY's, or `network` when the relay couldn't be reached. */
class RelayException(
    val code: String,
    message: String,
    val quota: RelayQuota? = null,
    cause: Throwable? = null,
) : GuideException(message, cause)

/**
 * The operator's AI (platform API-AI-RELAY): no key on the device; a session for this install,
 * proved with Play Integrity when [integrity] is given (the development relay doesn't ask for it),
 * and a daily count kept by the relay, reported in [quota].
 */
class RelayClient(
    baseUrl: String,
    private val app: RelayApp,
    private val store: RelaySessionStore,
    private val integrity: IntegrityTokenProvider? = null,
    private val http: OkHttpClient = defaultHttp,
    private val now: () -> Instant = Instant::now,
) : LlmClient {
    private val base = baseUrl.trimEnd('/')
    private val sessionLock = Mutex()

    private val _quota = MutableStateFlow<RelayQuota?>(null)
    /** Today's calls as the relay last reported them; null until it has. */
    val quota: StateFlow<RelayQuota?> = _quota.asStateFlow()

    override suspend fun generate(system: String, user: String, purpose: Purpose): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("kind", if (purpose == Purpose.GUIDE) "guide" else "chat")
            .put("system", system)
            .put("user", user)
        val out = authorized { token -> call("POST", "/v1/generate", body, token) }
        out.optJSONObject("quota")?.let { _quota.value = parseQuota(it) }
        out.optString("text").trim()
    }

    /** Asks for today's calls without using one; also serves as the connection test. */
    suspend fun refreshQuota(): RelayQuota = withContext(Dispatchers.IO) {
        parseQuota(authorized { token -> call("GET", "/v1/quota", null, token) }).also { _quota.value = it }
    }

    /** Runs [block] with a session, opening a new one once if the relay no longer knows the token. */
    private suspend fun authorized(block: (String) -> JSONObject): JSONObject {
        val token = session()
        return try {
            block(token)
        } catch (e: RelayException) {
            if (e.code != "token_invalid") throw e
            store.clearSession()
            block(session())
        }
    }

    private suspend fun session(): String = sessionLock.withLock {
        store.session()?.takeIf { (_, expires) -> expires.isAfter(now().plus(SESSION_MARGIN)) }?.first
            ?: try {
                openSession()
            } catch (e: RelayException) {
                // A nonce can expire between the two calls on a slow network: one more try.
                if (e.code != "nonce_invalid") throw e
                openSession()
            }
    }

    private suspend fun openSession(): String {
        val installId = store.installId()
        val nonce = call("POST", "/v1/session/challenge", JSONObject(), null).getString("nonce")
        val body = JSONObject()
            .put("installId", installId)
            .put("nonce", nonce)
            .put("app", JSONObject().put("package", app.packageName).put("versionCode", app.versionCode))
        integrity?.let { body.put("integrityToken", it.token(requestHash(installId, nonce))) }
        val out = call("POST", "/v1/session", body, null)
        val token = out.getString("token")
        store.saveSession(token, Instant.parse(out.getString("expiresAt")))
        out.optJSONObject("quota")?.let { _quota.value = parseQuota(it) }
        return token
    }

    private fun call(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        val request = Request.Builder()
            .url(base + path)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.isSuccessful) return JSONObject(text)
                val problem = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    JSONObject()
                }
                val quota = problem.optJSONObject("quota")?.let(::parseQuota)
                if (quota != null) _quota.value = quota
                val code = problem.optString("code").ifEmpty {
                    when {
                        res.code == 429 -> "too_many_requests"
                        res.code >= 500 -> "upstream_error"
                        else -> "invalid_request"
                    }
                }
                throw RelayException(code, messageFor(code, quota), quota)
            }
        } catch (e: IOException) {
            throw RelayException("network", "運営の AI に接続できませんでした", cause = e)
        } catch (e: JSONException) {
            throw RelayException("invalid_response", "運営の AI の応答を解釈できませんでした", cause = e)
        }
    }

    companion object {
        /** A token this close to expiring is renewed first, so a walk doesn't stall on it. */
        private val SESSION_MARGIN: Duration = Duration.ofMinutes(5)
        private val JSON = "application/json".toMediaType()
        /**
         * The relay answers once the model has written the whole line, which can take longer than
         * OkHttp's 10 s read timeout; the relay gives up at 28 s (the Lambda's timeout).
         */
        internal val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        /** The Integrity request hash: base64url of SHA-256 over `{installId}.{nonce}` (API-AI-RELAY). */
        fun requestHash(installId: String, nonce: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest("$installId.$nonce".toByteArray(Charsets.UTF_8)))

        internal fun parseQuota(json: JSONObject) =
            RelayQuota(json.getInt("limit"), json.getInt("remaining"), Instant.parse(json.getString("resetsAt")))

        /** What the user is told; the app goes on with its set phrases whatever the code. */
        internal fun messageFor(code: String, quota: RelayQuota?): String = when (code) {
            "daily_limit_reached" -> "今日の運営の AI の回数${quota?.let { "（${it.limit} 回）" }.orEmpty()}を使い切りました"
            "relay_paused" -> "運営の AI は今月の予算に達したため止まっています。設定で自分の APIキーに切り替えられます"
            "integrity_failed" -> "この端末・アプリでは運営の AI を使えません。設定で自分の APIキーを使えます"
            "too_many_requests" -> "運営の AI が混み合っています。しばらくしてからお試しください"
            "model_refused" -> "このスポットの解説は用意できませんでした"
            "incomplete_output" -> "AI の応答が途中で切れました"
            "input_too_long", "invalid_request" -> "運営の AI に送れない内容でした"
            else -> "運営の AI でエラーが発生しました"
        }
    }
}
