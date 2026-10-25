package io.github.duwenji.yorisoi.kit.llm

import kotlin.coroutines.cancellation.CancellationException

/**
 * How the AI is reached (ADR-AI-RELAY R-1): the operator's AI through its relay, paid by the
 * operator up to its daily limit, or the user's own key straight to the service they chose.
 */
enum class AiRoute { OPERATOR, OWN_KEY }

/**
 * The app's AI settings as the gateway needs them: the route and, for the user's own key, the
 * service, key and model, and how many calls a day it may make (null for no limit; ADR-AI-ROLES H-9).
 */
data class AiConnection(
    val route: AiRoute,
    val provider: Provider = Provider.CLAUDE,
    val apiKey: String = "",
    val model: String = provider.defaultModel,
    val baseUrl: String = provider.defaultBaseUrl,
    val dailyLimit: Int? = null,
) {
    /** Whether a call can be made: the build has a relay, or the key, model and address are set. */
    fun isConfigured(operatorAvailable: Boolean): Boolean = when (route) {
        AiRoute.OPERATOR -> operatorAvailable
        AiRoute.OWN_KEY -> apiKey.isNotBlank() && model.isNotBlank() && (provider == Provider.CLAUDE || baseUrl.isNotBlank())
    }

    /** The model named beside what the AI said: the service's own for a user's key. */
    val name: String get() = if (route == AiRoute.OPERATOR) OPERATOR_AI_NAME else model

    companion object {
        const val OPERATOR_AI_NAME = "運営の AI"
    }
}

/**
 * One call to the AI service, for the developer's record: what was asked, what came back (or why
 * nothing did) and how long it took. [violation] is what [OutputCheck] found.
 */
data class AiCall(
    val kind: String,
    val model: String,
    val system: String,
    val user: String,
    val reply: String?,
    val error: String?,
    val violation: String?,
    val ms: Long,
)

/** No call was made: no AI is set up, or today's limit is reached. */
class AiUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * The AI, however the app reaches it: the operator's [relay] (null in a build without one) or the
 * user's own key, within the day's limit (the relay's on the operator's AI, [usage] on the user's
 * key), with the stage directions the AI slips in taken out. The app writes the prompts and
 * decides what to do with a reply [OutputCheck] finds unusable.
 *
 * @param connection read at each call, so a change in the settings applies at once
 */
class AiGateway(
    private val connection: () -> AiConnection,
    private val usage: AiUsage,
    private val relay: RelayClient?,
) {
    private var client: Pair<AiConnection, LlmClient>? = null

    /** Hears of every call made, for the developer's record. */
    var onCall: (AiCall) -> Unit = {}

    val isConfigured: Boolean get() = connection().isConfigured(relay != null)

    /** Whether today's calls reached the limit (ADR-AI-ROLES H-9, ADR-AI-RELAY R-2). */
    fun limitReached(): Boolean {
        val c = connection()
        return when (c.route) {
            AiRoute.OPERATOR -> relay?.quota?.value?.exhausted() == true
            AiRoute.OWN_KEY -> usage.reached(c.dailyLimit)
        }
    }

    /**
     * The AI's reply to [system] and [user], without stage directions.
     *
     * @throws AiUnavailableException when no AI is set up or today's limit is reached (on the relay too)
     */
    suspend fun generate(purpose: Purpose, system: String, user: String): String {
        val c = connection()
        if (!c.isConfigured(relay != null)) throw AiUnavailableException("No AI service is set up")
        if (limitReached() || !takeCall(c)) throw AiUnavailableException("The daily limit of AI calls is reached")
        val startedAt = System.currentTimeMillis()
        fun report(reply: String?, error: String?) = onCall(
            AiCall(purpose.name.lowercase(), c.name, system, user, reply, error, reply?.let(OutputCheck::violation), System.currentTimeMillis() - startedAt),
        )
        val text = try {
            clientFor(c).generate(system, user, purpose)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(null, "${e.javaClass.simpleName}: ${e.message}")
            // The relay's count ran out before ours: the same as reaching the limit here.
            if (e is RelayException && e.code == "daily_limit_reached") throw AiUnavailableException("The daily limit of AI calls is reached", e)
            throw e
        }
        // Recorded as the AI wrote it, so stage directions it slipped in can still be counted.
        report(text, null)
        return OutputCheck.withoutStageDirections(text)
    }

    /** Counts a call on the user's own key against their limit; the relay counts the operator's AI. */
    private fun takeCall(c: AiConnection): Boolean = c.route == AiRoute.OPERATOR || usage.tryUse(c.dailyLimit)

    @Synchronized
    private fun clientFor(c: AiConnection): LlmClient {
        if (c.route == AiRoute.OPERATOR) return relay ?: error("This build has no relay")
        client?.let { (was, made) -> if (was == c) return made }
        return createClient(c).also { client = c to it }
    }

    companion object {
        /** A client for the user's own key ([AiRoute.OWN_KEY]). */
        fun createClient(c: AiConnection): LlmClient = when (c.provider) {
            Provider.CLAUDE -> ClaudeClient(c.apiKey, c.model)
            else -> OpenAiCompatibleClient(c.baseUrl, c.apiKey, c.model)
        }
    }
}
