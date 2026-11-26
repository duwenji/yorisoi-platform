package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.agent.AgentTools
import io.github.duwenji.yorisoi.core.agent.PreparerModel
import io.github.duwenji.yorisoi.core.agent.Reply
import io.github.duwenji.yorisoi.core.agent.ToolDef
import io.github.duwenji.yorisoi.core.agent.Turn
import kotlin.coroutines.cancellation.CancellationException

/**
 * How the AI is reached (API-AI-RELAY R-1): the operator's AI through its relay, paid by the
 * operator up to its daily limit, or the user's own key straight to the service they chose.
 */
enum class AiRoute { OPERATOR, OWN_KEY }

/**
 * The app's AI settings as the gateway needs them: the route and, for the user's own key, the
 * service, key and model, and how many calls a day it may make (null for no limit; AI-ROLES H-9).
 */
data class AiConnection(
    val route: AiRoute,
    val provider: Provider = Provider.CLAUDE,
    val apiKey: String = "",
    val model: String = provider.defaultModel,
    val baseUrl: String = provider.defaultBaseUrl,
    val dailyLimit: Int? = null,
    /** The preparer's model on the user's own key; blank for the same as [model] (platform AGENT A-46). */
    val agentModel: String = "",
) {
    /** The model the preparer runs on with the user's own key. */
    val preparerModel: String get() = agentModel.ifBlank { model }

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

    /** Whether today's calls reached the limit (AI-ROLES H-9, API-AI-RELAY R-2). */
    fun limitReached(): Boolean {
        val c = connection()
        return when (c.route) {
            AiRoute.OPERATOR -> relay?.quota?.value?.exhausted() == true
            AiRoute.OWN_KEY -> usage.reached(c.dailyLimit)
        }
    }

    /**
     * The AI's reply to [system] and [user], without stage directions and Markdown marks.
     *
     * @throws AiUnavailableException when no AI is set up or today's limit is reached (on the relay too)
     */
    suspend fun generate(purpose: Purpose, system: String, user: String): String {
        val c = connection()
        if (!c.isConfigured(relay != null)) throw AiUnavailableException("No AI service is set up")
        if (limitReached() || !takeCall(c)) throw AiUnavailableException("The daily limit of AI calls is reached")
        val startedAt = System.currentTimeMillis()
        fun report(reply: String?, error: String?) = onCall(
            // Hints are written as a list on purpose: only speech counts as notes.
            AiCall(
                purpose.name.lowercase(), c.name, system, user, reply, error,
                reply?.let(OutputCheck::violation)?.takeUnless { purpose == Purpose.HINTS && it == OutputCheck.NOTES },
                System.currentTimeMillis() - startedAt,
            ),
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
        // Recorded as the AI wrote it, so stage directions and marks it slipped in can still be counted.
        report(text, null)
        return OutputCheck.spoken(text)
    }

    /**
     * The preparer (platform AGENT A-10・A-16・A-46) on today's route: the operator's relay (its model for the
     * use `agent`), or the user's own key with [AiConnection.preparerModel]. Each call counts as one call of the
     * day, like [generate]; the caller keeps the preparer within its share of them (A-33). Null when no AI is set up.
     */
    fun preparer(tools: AgentTools): PreparerModel? {
        val c = connection()
        if (!c.isConfigured(relay != null)) return null
        val model: PreparerModel = when (c.route) {
            AiRoute.OPERATOR -> relay?.let { r -> PreparerModel { s, turns, t, must -> r.agent(s, turns, t, must, tools.version) } } ?: return null
            AiRoute.OWN_KEY -> when (c.provider) {
                Provider.CLAUDE -> ClaudeToolModel(c.apiKey, c.preparerModel)
                else -> OpenAiToolModel(c.baseUrl, c.apiKey, c.preparerModel)
            }
        }
        return PreparerModel { system, turns, offered, mustUse -> counted(c, system, turns, offered) { model.call(system, turns, offered, mustUse) } }
    }

    private suspend fun counted(c: AiConnection, system: String, turns: List<Turn>, offered: List<ToolDef>, call: suspend () -> Reply): Reply {
        if (limitReached() || !takeCall(c)) throw AiUnavailableException("The daily limit of AI calls is reached")
        val startedAt = System.currentTimeMillis()
        val asked = (turns.lastOrNull() as? Turn.Answers)?.answers?.joinToString("\n") { it.text } ?: (turns.lastOrNull() as? Turn.Input)?.text.orEmpty()
        fun report(reply: String?, error: String?) = onCall(
            AiCall("agent", if (c.route == AiRoute.OPERATOR) c.name else c.preparerModel, system, asked, reply, error, null, System.currentTimeMillis() - startedAt),
        )
        return try {
            call().also { r -> report(r.uses.joinToString("\n") { "${it.name} ${it.input}" }.ifEmpty { "（道具なし: ${offered.joinToString { it.name }}）" }, null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(null, "${e.javaClass.simpleName}: ${e.message}")
            if (e is RelayException && e.code == "daily_limit_reached") throw AiUnavailableException("The daily limit of AI calls is reached", e)
            throw e
        }
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
