package io.github.duwenji.yorisoi.core.agent

import org.json.JSONObject

/** A turn of the conversation the app keeps and sends whole each time (ADR-AGENT A-11). */
sealed interface Turn {
    /** The preparation's input (A-4), with the results taken before it. */
    data class Input(val text: String) : Turn
    /** The tools the preparer asked for. Its words are not kept (A-15). */
    data class Asked(val uses: List<ToolUse>) : Turn
    /** The tools' answers, by the id of each use. [text] may be cut later to fit the budget (A-37). */
    data class Answers(val answers: MutableList<Answer>) : Turn
}

data class ToolUse(val id: String, val name: String, val input: JSONObject)

data class Answer(val useId: String, var text: String, val results: List<Result>)

/** What the AI answered: the tools it asked for and its words (which are thrown away). */
data class Reply(val uses: List<ToolUse>, val text: String)

/** The AI the preparer runs on (kit-llm puts the route's form on it). */
fun interface PreparerModel {
    /**
     * One call.
     * @param tools the tools offered on this turn
     * @param mustUse whether the AI must answer with a tool (the last turn, A-15)
     */
    suspend fun call(system: String, turns: List<Turn>, tools: List<ToolDef>, mustUse: Boolean): Reply
}

/** The tools the app runs (kit-agent-tools). It adds its results to the book and answers in words for the AI. */
fun interface ToolRunner {
    suspend fun run(name: String, input: JSONObject, book: ResultBook): ToolOutcome
}

/** A tool's answer for the AI and the results it added. */
data class ToolOutcome(val text: String, val results: List<Result>)

/** One preparation's outcome and its log (A-49). */
data class Prepared(val checked: Checked?, val calls: Int, val toolCalls: List<String>, val refused: List<String>, val budgetCuts: Int)

/**
 * One preparation (ADR-AGENT A-11〜A-15・A-33・A-36〜A-38): at most [maxCalls] calls; the last offers only the
 * submission and must use it. Each reply's tools run at most [MAX_TOOLS_PER_REPLY] at a time; a tool with wrong
 * arguments or an unknown name doesn't run and the reason goes back. The AI's words are dropped. The
 * conversation is kept to the budget (A-36) by [ContextBudget]. Every preparation starts afresh (A-38).
 */
class Preparation(
    private val tools: AgentTools,
    private val model: PreparerModel,
    private val runner: ToolRunner,
    private val maxCalls: Int = 3,
) {
    suspend fun run(system: String, input: String, book: ResultBook, scenes: Set<String>): Prepared {
        val turns = ArrayList<Turn>()
        turns += Turn.Input(input)
        val toolCalls = ArrayList<String>()
        val refused = ArrayList<String>()
        var cuts = 0
        for (call in 1..maxCalls) {
            val last = call == maxCalls
            val reply = model.call(system, turns, if (last) listOf(tools.submit) else tools.lookups + tools.submit, last)
            val submitted = reply.uses.firstOrNull { it.name == AgentTools.SUBMIT }
            if (submitted != null) {
                val errors = tools.argumentErrors(AgentTools.SUBMIT, submitted.input)
                if (errors.isNotEmpty()) return Prepared(null, call, toolCalls, refused + errors.map { "submit: $it" }, cuts)
                return Prepared(ThreadCheck.check(submitted.input, book, scenes), call, toolCalls, refused, cuts)
            }
            if (reply.uses.isEmpty() || last) return Prepared(null, call, toolCalls, refused, cuts)
            turns += Turn.Asked(reply.uses)
            val answers = ArrayList<Answer>()
            reply.uses.forEachIndexed { i, use ->
                toolCalls += use.name
                val errors = tools.argumentErrors(use.name, use.input)
                val (text, results) = when {
                    i >= MAX_TOOLS_PER_REPLY -> "上限を超えたので実行しませんでした" to emptyList()
                    errors.isNotEmpty() -> { refused += "${use.name}: ${errors.joinToString("; ")}"; "実行しませんでした: ${errors.joinToString("; ")}" to emptyList() }
                    else -> runner.run(use.name, use.input, book).let { it.text to it.results }
                }
                answers += Answer(use.id, text, results)
            }
            turns += Turn.Answers(answers)
            cuts += ContextBudget.fit(turns)
        }
        return Prepared(null, maxCalls, toolCalls, refused, cuts)
    }

    companion object {
        const val MAX_TOOLS_PER_REPLY = 4
    }
}
