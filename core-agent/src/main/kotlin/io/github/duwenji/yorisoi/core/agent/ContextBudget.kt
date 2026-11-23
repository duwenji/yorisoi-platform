package io.github.duwenji.yorisoi.core.agent

/**
 * The preparer's context budget (AGENT A-36・A-37): the fixed instructions up to [SYSTEM] characters, the
 * preparation's input (without the results taken before it) up to [INPUT], and the tools' results (with those)
 * up to [RESULTS]; together within the relay's [TOTAL]. When the results grow past theirs, they are cut by
 * rule, never summarised by an AI: first the link lists already followed, then the pages with no hint, then the
 * oldest answers. A cut answer keeps its numbered facts as 「R1 …」 lines only, and the fixed text 「（縮めました）」.
 */
object ContextBudget {
    const val SYSTEM = 3_000
    const val INPUT = 2_000
    const val RESULTS = 10_000
    const val TOTAL = 16_000
    const val CUT_NOTE = "（縮めました）"

    /** Whether the fixed instructions and the input fit their parts. */
    fun fits(system: String, input: String): Boolean = system.length <= SYSTEM && input.length <= INPUT

    /** Cuts the answers in [turns] until the results fit; gives back how many answers were cut. */
    fun fit(turns: List<Turn>): Int {
        val answers = turns.filterIsInstance<Turn.Answers>().flatMap { it.answers }
        var cuts = 0
        fun size() = turns.sumOf { t -> when (t) { is Turn.Input -> t.text.length; is Turn.Answers -> t.answers.sumOf { it.text.length }; is Turn.Asked -> 0 } }
        val order = answers.filter { a -> a.results.isNotEmpty() && a.results.all { it.role == ResultRole.LINKS } } +
            answers.filter { a -> a.results.isNotEmpty() && a.results.all { it.role == ResultRole.EMPTY_PAGE } } +
            answers
        for (a in order) {
            if (size() <= RESULTS) break
            if (a.text.endsWith(CUT_NOTE)) continue
            val facts = a.results.filter { it.role == ResultRole.FACT }
            a.text = (facts.joinToString("\n") { "${it.id} ${it.text}" } + "\n" + CUT_NOTE).trim()
            cuts++
        }
        return cuts
    }
}
