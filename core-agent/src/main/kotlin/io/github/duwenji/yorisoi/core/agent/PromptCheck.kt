package io.github.duwenji.yorisoi.core.agent

/**
 * The check of the preparer's fixed instructions (AGENT A-42): both apps write their own (`agent/system`)
 * for walking or cycling, with the same headings, and the shared guard (`shared/guard`, under 「# 守ること」)
 * last. Without them the preparer isn't used.
 */
object PromptCheck {
    val HEADINGS = listOf("# 役割", "# 進め方", "# 事実の扱い", "# 守ること")

    /** The headings [system] lacks, or that are out of order (the guard not last); empty when it may be used. */
    fun problems(system: String): List<String> {
        val lines = system.lines().map { it.trim() }
        val found = lines.filter { it.startsWith("# ") }
        val missing = HEADINGS.filter { it !in found }.map { "no $it" }
        if (missing.isNotEmpty()) return missing
        val order = HEADINGS.map { found.indexOf(it) }
        val problems = ArrayList<String>()
        if (order != order.sorted()) problems += "headings out of order"
        if (found.last() != HEADINGS.last()) problems += "${HEADINGS.last()} is not last"
        if (system.length > ContextBudget.SYSTEM) problems += "longer than ${ContextBudget.SYSTEM}"
        return problems
    }
}
