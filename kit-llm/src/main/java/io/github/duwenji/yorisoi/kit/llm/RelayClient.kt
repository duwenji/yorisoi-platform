package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.text.localized
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
import io.github.duwenji.yorisoi.core.agent.Reply
import io.github.duwenji.yorisoi.core.agent.ToolDef
import io.github.duwenji.yorisoi.core.agent.ToolUse
import io.github.duwenji.yorisoi.core.agent.Turn
import org.json.JSONArray
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

/** The plans of API-AI-RELAY R-2: free, and たっぷりプラン (`plus`) bought on Google Play. */
enum class RelayPlan(val wire: String) {
    FREE("free"),
    PLUS("plus");

    companion object {
        /** An unknown plan is taken as free (API-AI-RELAY「互換性方針」). */
        fun of(wire: String?): RelayPlan = entries.firstOrNull { it.wire == wire } ?: FREE
    }
}

/** Today's calls on the operator's AI (API-AI-RELAY `quota`). */
data class RelayQuota(val limit: Int, val remaining: Int, val resetsAt: Instant, val plan: RelayPlan = RelayPlan.FREE) {
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

/** A たっぷりプラン purchase on Google Play: its token and subscription id (API-AI-RELAY R-15, R-21). */
data class PlanPurchase(val purchaseToken: String, val productId: String)

/** The app's active たっぷりプラン purchase, or null; the app reads it from Google Play's billing library. */
fun interface PlanPurchases {
    suspend fun active(): PlanPurchase?
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
    /** The app's たっぷりプラン, sent with each new session and after it expires (API-AI-RELAY R-18). */
    private val plans: PlanPurchases? = null,
) : LlmClient {
    private val base = baseUrl.trimEnd('/')
    private val sessionLock = Mutex()
    /** When the purchase was last sent, so a free answer doesn't send it on every call. */
    @Volatile private var planSentAt: Instant? = null

    private val _quota = MutableStateFlow<RelayQuota?>(null)
    /** Today's calls as the relay last reported them; null until it has. */
    val quota: StateFlow<RelayQuota?> = _quota.asStateFlow()

    override suspend fun generate(system: String, user: String, purpose: Purpose): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("kind", purpose.kind)
            .put("system", system)
            .put("user", user)
        val out = authorized { token -> call("POST", "/v1/generate", body, token) }
        out.optJSONObject("quota")?.let { onQuota(parseQuota(it)) }
        out.optString("text").trim()
    }

    /**
     * One preparer's turn on the operator's AI (POST /v1/agent; platform AGENT A-45): the conversation, the
     * version and the names of the tools offered. The relay builds the tools from its own definitions and
     * answers with the tools the model asked for.
     */
    suspend fun agent(system: String, turns: List<Turn>, tools: List<ToolDef>, mustUse: Boolean, toolsVersion: Int): Reply = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("toolsVersion", toolsVersion)
            .put("tools", JSONArray(tools.map { it.name }))
            .put("mustUse", mustUse)
            .put("system", system)
            .put("messages", JSONArray(turns.map { t ->
                when (t) {
                    is Turn.Input -> JSONObject().put("role", "user").put("text", t.text)
                    is Turn.Asked -> JSONObject().put("role", "assistant").put("toolUses", JSONArray(t.uses.map {
                        JSONObject().put("id", it.id).put("name", it.name).put("input", it.input)
                    }))
                    is Turn.Answers -> JSONObject().put("role", "tool").put("results", JSONArray(t.answers.map {
                        JSONObject().put("id", it.useId).put("text", it.text)
                    }))
                }
            }))
        val out = authorized { token -> call("POST", "/v1/agent", body, token) }
        out.optJSONObject("quota")?.let { onQuota(parseQuota(it)) }
        val uses = out.optJSONArray("toolUses") ?: JSONArray()
        Reply((0 until uses.length()).map { i ->
            val u = uses.getJSONObject(i)
            ToolUse(u.getString("id"), u.getString("name"), u.optJSONObject("input") ?: JSONObject())
        }, "")
    }

    /** Asks for today's calls without using one; also serves as the connection test. */
    suspend fun refreshQuota(): RelayQuota = withContext(Dispatchers.IO) {
        val q = parseQuota(authorized { token -> call("GET", "/v1/quota", null, token) })
        onQuota(q)
        _quota.value ?: q
    }

    /**
     * Sends a たっぷりプラン purchase (POST /v1/plan; API-AI-RELAY R-15) right after it is bought; the relay
     * checks it with Google Play. Throws [RelayException] (`plan_not_active` when it isn't active).
     */
    suspend fun sendPlan(purchase: PlanPurchase): RelayQuota = withContext(Dispatchers.IO) {
        val out = authorized { token -> postPlan(token, purchase) }
        parseQuota(out.getJSONObject("quota")).also { _quota.value = it }
    }

    private fun postPlan(token: String, purchase: PlanPurchase): JSONObject {
        planSentAt = now()
        return call("POST", "/v1/plan", JSONObject().put("purchaseToken", purchase.purchaseToken).put("productId", purchase.productId), token)
    }

    /**
     * Keeps [quota]; when the relay counts this install as free while the app holds a purchase (a new
     * session, or past the expiry the relay last saw, R-18), sends it again, at most every 10 minutes.
     */
    private suspend fun onQuota(q: RelayQuota) {
        _quota.value = q
        if (q.plan == RelayPlan.FREE) restorePlan(store.session()?.first ?: return)
    }

    private suspend fun restorePlan(token: String) {
        val p = plans ?: return
        val sent = planSentAt
        if (sent != null && sent.plus(PLAN_RETRY).isAfter(now())) return
        val purchase = try {
            p.active()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            postPlan(token, purchase).optJSONObject("quota")?.let { _quota.value = parseQuota(it) }
        } catch (e: RelayException) {
            // Stays free: the relay couldn't check it now; the next free answer tries again.
        }
    }

    /**
     * A token of this install's session, for another service that accepts the relay's sessions
     * (the studio, platform API-LOCAL-MATERIALS ST-2). Opens a session when there is none.
     */
    suspend fun token(): String = withContext(Dispatchers.IO) { session() }

    /** Forgets the session after another service said its token is no longer known. */
    fun forgetToken() = store.clearSession()

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
        // A new session starts free; a held purchase is sent at once (R-18).
        planSentAt = null
        restorePlan(token)
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
            throw RelayException("network", messageFor("network", null), cause = e)
        } catch (e: JSONException) {
            throw RelayException("invalid_response", messageFor("invalid_response", null), cause = e)
        }
    }

    companion object {
        /** A token this close to expiring is renewed first, so a walk doesn't stall on it. */
        private val SESSION_MARGIN: Duration = Duration.ofMinutes(5)
        private val PLAN_RETRY: Duration = Duration.ofMinutes(10)
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
            RelayQuota(json.getInt("limit"), json.getInt("remaining"), Instant.parse(json.getString("resetsAt")), RelayPlan.of(json.optString("plan").ifEmpty { null }))

        /** What the user is told; the app goes on with its set phrases whatever the code. */
        internal fun messageFor(code: String, quota: RelayQuota?): String = when (code) {
            "network" -> localized(
                "運営の AI に接続できませんでした", "Could not connect to the operator's AI", "无法连接到运营方的 AI", "운영자의 AI에 연결할 수 없었습니다",
            )
            "invalid_response" -> localized(
                "運営の AI の応答を解釈できませんでした", "Could not read the operator's AI's reply", "无法解析运营方 AI 的回复", "운영자 AI의 응답을 해석할 수 없었습니다",
            )
            "daily_limit_reached" -> localized(
                "今日の運営の AI の回数${quota?.let { "（${it.limit} 回）" }.orEmpty()}を使い切りました",
                "Today's calls to the operator's AI${quota?.let { " (${it.limit})" }.orEmpty()} are used up",
                "今天运营方 AI 的次数${quota?.let { "（${it.limit} 次）" }.orEmpty()}已用完",
                "오늘의 운영자 AI 사용 횟수${quota?.let { "(${it.limit}회)" }.orEmpty()}를 모두 사용했습니다",
            )
            "relay_paused" -> localized(
                "運営の AI は今月の予算に達したため止まっています。設定で自分の APIキーに切り替えられます",
                "The operator's AI is paused: this month's budget is reached. You can switch to your own API key in the settings",
                "运营方的 AI 已达到本月预算，暂时停止。可以在设置中改用自己的 API 密钥",
                "운영자의 AI는 이번 달 예산에 도달해 멈춰 있습니다. 설정에서 자신의 API 키로 바꿀 수 있습니다",
            )
            "integrity_failed" -> localized(
                "この端末・アプリでは運営の AI を使えません。設定で自分の APIキーを使えます",
                "The operator's AI can't be used on this device or app. You can use your own API key in the settings",
                "此设备或应用无法使用运营方的 AI。可以在设置中使用自己的 API 密钥",
                "이 기기·앱에서는 운영자의 AI를 사용할 수 없습니다. 설정에서 자신의 API 키를 사용할 수 있습니다",
            )
            "plan_not_active" -> localized("たっぷりプランが有効になっていません", "The Plus plan is not active", "Plus 套餐尚未生效", "플러스 플랜이 활성화되어 있지 않습니다")
            "too_many_requests" -> localized(
                "運営の AI が混み合っています。しばらくしてからお試しください", "The operator's AI is busy. Try again in a while",
                "运营方的 AI 繁忙，请稍后再试", "운영자의 AI가 혼잡합니다. 잠시 후 다시 시도해 주세요",
            )
            "model_refused" -> Messages.refused
            "incomplete_output" -> localized("AI の応答が途中で切れました", "The AI's reply was cut off", "AI 的回复中途中断了", "AI의 응답이 중간에 끊겼습니다")
            "input_too_long", "invalid_request" -> localized(
                "運営の AI に送れない内容でした", "This could not be sent to the operator's AI", "这些内容无法发送给运营方的 AI", "운영자의 AI에 보낼 수 없는 내용이었습니다",
            )
            else -> localized("運営の AI でエラーが発生しました", "The operator's AI had an error", "运营方的 AI 出现错误", "운영자의 AI에서 오류가 발생했습니다")
        }
    }
}
