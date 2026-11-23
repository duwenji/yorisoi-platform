package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.agent.PreparerModel
import io.github.duwenji.yorisoi.core.agent.Reply
import io.github.duwenji.yorisoi.core.agent.ToolDef
import io.github.duwenji.yorisoi.core.agent.ToolUse
import io.github.duwenji.yorisoi.core.agent.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The preparer on the user's own key (platform AGENT A-10・A-46): the same turns in each service's
 * form of function calling. The model's words are dropped; only the tools it asked for come back (A-15).
 */
internal object ToolForms {
    const val MAX_TOKENS = 2_048
    val JSON = "application/json".toMediaType()
    val http: OkHttpClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()

    fun post(request: Request): JSONObject {
        try {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val detail = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: JSONException) { null }
                    throw GuideException(messageForStatus(res.code, detail))
                }
                return JSONObject(text)
            }
        } catch (e: IOException) {
            throw GuideException("AIサービスに接続できませんでした", e)
        } catch (e: JSONException) {
            throw GuideException("AIサービスの応答を解釈できませんでした", e)
        }
    }
}

/** Claude's Messages API with `tools` (raw JSON, so the turns map one to one). */
class ClaudeToolModel(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.anthropic.com",
) : PreparerModel {
    override suspend fun call(system: String, turns: List<Turn>, tools: List<ToolDef>, mustUse: Boolean): Reply = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", ToolForms.MAX_TOKENS)
            .put("system", system)
            .put("messages", messages(turns))
            .put("tools", JSONArray(tools.map { JSONObject().put("name", it.name).put("description", it.description).put("input_schema", it.inputSchema) }))
            .put("tool_choice", if (!mustUse) JSONObject().put("type", "auto") else if (tools.size == 1) JSONObject().put("type", "tool").put("name", tools[0].name) else JSONObject().put("type", "any"))
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(ToolForms.JSON))
            .build()
        val out = ToolForms.post(request)
        val content = out.optJSONArray("content") ?: JSONArray()
        val uses = (0 until content.length()).mapNotNull { i ->
            val b = content.getJSONObject(i)
            if (b.optString("type") == "tool_use") ToolUse(b.getString("id"), b.getString("name"), b.optJSONObject("input") ?: JSONObject()) else null
        }
        Reply(uses, "")
    }

    private fun messages(turns: List<Turn>): JSONArray = JSONArray(turns.map { t ->
        when (t) {
            is Turn.Input -> JSONObject().put("role", "user").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", t.text)))
            is Turn.Asked -> JSONObject().put("role", "assistant").put("content", JSONArray(t.uses.map {
                JSONObject().put("type", "tool_use").put("id", it.id).put("name", it.name).put("input", it.input)
            }))
            is Turn.Answers -> JSONObject().put("role", "user").put("content", JSONArray(t.answers.map {
                JSONObject().put("type", "tool_result").put("tool_use_id", it.useId).put("content", it.text)
            }))
        }
    })
}

/** Any OpenAI Chat Completions service with `tools` (DeepSeek, OpenAI, Gemini, ...). */
class OpenAiToolModel(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : PreparerModel {
    override suspend fun call(system: String, turns: List<Turn>, tools: List<ToolDef>, mustUse: Boolean): Reply = withContext(Dispatchers.IO) {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) when (t) {
            is Turn.Input -> messages.put(JSONObject().put("role", "user").put("content", t.text))
            is Turn.Asked -> messages.put(JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put("tool_calls", JSONArray(t.uses.map {
                JSONObject().put("id", it.id).put("type", "function").put("function", JSONObject().put("name", it.name).put("arguments", it.input.toString()))
            })))
            is Turn.Answers -> t.answers.forEach { messages.put(JSONObject().put("role", "tool").put("tool_call_id", it.useId).put("content", it.text)) }
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", ToolForms.MAX_TOKENS)
            .put("messages", messages)
            .put("tools", JSONArray(tools.map {
                JSONObject().put("type", "function").put("function", JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.inputSchema))
            }))
            .put("tool_choice", if (mustUse) "required" else "auto")
        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(ToolForms.JSON))
                .build()
        } catch (e: IllegalArgumentException) {
            throw GuideException("接続先URLが正しくありません", e)
        }
        val out = ToolForms.post(request)
        val message = out.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val calls = message.optJSONArray("tool_calls") ?: JSONArray()
        val uses = (0 until calls.length()).mapNotNull { i ->
            val c = calls.getJSONObject(i)
            val f = c.optJSONObject("function") ?: return@mapNotNull null
            val args = try { JSONObject(f.optString("arguments").ifEmpty { "{}" }) } catch (e: JSONException) { JSONObject() }
            ToolUse(c.optString("id").ifEmpty { "call_$i" }, f.getString("name"), args)
        }
        Reply(uses, "")
    }
}
