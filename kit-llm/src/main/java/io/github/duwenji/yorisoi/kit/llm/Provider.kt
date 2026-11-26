package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.text.localized

/** LLM services the guide can use. Everything except Claude speaks the OpenAI chat API. */
enum class Provider(
    private val brand: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyPageUrl: String,
) {
    CLAUDE("Claude (Anthropic)", "", "claude-opus-5", "https://console.anthropic.com/settings/keys"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", "https://platform.deepseek.com/api_keys"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5-mini", "https://platform.openai.com/api-keys"),
    GEMINI(
        "Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-2.5-flash", "https://aistudio.google.com/apikey",
    ),
    CUSTOM("", "", "", ""),
    ;

    /** The name shown in settings: the service's own, or "custom" in the app's language. */
    val label: String get() = if (this == CUSTOM) {
        localized("カスタム（OpenAI互換）", "Custom (OpenAI-compatible)", "自定义（兼容 OpenAI）", "사용자 지정(OpenAI 호환)")
    } else {
        brand
    }

    companion object {
        /** Claude models offered in settings; the first is the default. */
        val CLAUDE_MODELS = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5")
    }
}

/**
 * What a call is for; the operator's relay counts them apart and picks the model by it (API-AI-RELAY `kind`;
 * platform AGENT A-16): the speaker's narration and talk, and the hint maker's hints. The preparer's
 * turns go by [AiGateway.preparer].
 */
enum class Purpose(val kind: String) { GUIDE("guide"), CHAT("chat"), HINTS("hints") }

/** A text-generation backend. */
interface LlmClient {
    suspend fun generate(system: String, user: String, purpose: Purpose = Purpose.CHAT): String
}

/** A failure with a message that can be shown to the user as-is. */
open class GuideException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun messageForStatus(status: Int, detail: String?): String = when (status) {
    401 -> localized("APIキーが正しくありません", "The API key is not valid", "API 密钥不正确", "API 키가 올바르지 않습니다")
    402 -> localized(
        "APIの残高・支払い設定を確認してください", "Check the API balance and payment settings",
        "请检查 API 余额和付款设置", "API 잔액과 결제 설정을 확인해 주세요",
    )
    403 -> localized(
        "このAPIキーでは利用が許可されていません", "This API key is not allowed to use it",
        "此 API 密钥无权使用", "이 API 키로는 사용이 허용되지 않습니다",
    )
    404 -> localized(
        "モデル名または接続先URLが見つかりません", "The model name or the URL was not found",
        "找不到模型名称或连接地址", "모델 이름 또는 접속 URL을 찾을 수 없습니다",
    )
    429 -> localized(
        "リクエストが多すぎます。しばらく待ってから再度お試しください", "Too many requests. Wait a while and try again",
        "请求过多，请稍后再试", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요",
    )
    in 500..599 -> localized(
        "AIサービス側でエラーが発生しました（HTTP $status）", "The AI service had an error (HTTP $status)",
        "AI 服务出现错误（HTTP $status）", "AI 서비스에서 오류가 발생했습니다(HTTP $status)",
    )
    else -> localized("AIサービスのエラー（HTTP $status）", "AI service error (HTTP $status)", "AI 服务错误（HTTP $status）", "AI 서비스 오류(HTTP $status)") +
        (detail?.let { ": $it" } ?: "")
}

/** The messages the clients share, in the app's language (REQ-LANGUAGES F-4). */
internal object Messages {
    val cantConnect: String get() = localized(
        "AIサービスに接続できませんでした", "Could not connect to the AI service", "无法连接到 AI 服务", "AI 서비스에 연결할 수 없었습니다",
    )
    val cantRead: String get() = localized(
        "AIサービスの応答を解釈できませんでした", "Could not read the AI service's reply", "无法解析 AI 服务的回复", "AI 서비스의 응답을 해석할 수 없었습니다",
    )
    val badUrl: String get() = localized("接続先URLが正しくありません", "The URL is not valid", "连接地址不正确", "접속 URL이 올바르지 않습니다")
    val empty: String get() = localized("AIから空の応答が返りました", "The AI returned an empty reply", "AI 返回了空的回复", "AI가 빈 응답을 보냈습니다")
    val refused: String get() = localized(
        "このスポットの解説は用意できませんでした", "No narration could be made for this spot", "无法为这个地点准备解说", "이 장소의 해설을 준비할 수 없었습니다",
    )
}
