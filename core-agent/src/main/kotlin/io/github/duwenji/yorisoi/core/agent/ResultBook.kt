package io.github.duwenji.yorisoi.core.agent

/** Where a fact came from, shown with it when it is spoken (ADR-AGENT A-30・A-31). */
data class Origin(
    val name: String,
    /** `national`・`prefecture`・`municipality` may be stated as fact; the others are said as 「〜と紹介されていました」. */
    val kind: String,
    val title: String? = null,
    val url: String? = null,
    val date: String? = null,
)

/** What a result is, for cutting the context (ADR-AGENT A-37): the facts stay longest. */
enum class ResultRole { FACT, LINKS, EMPTY_PAGE }

/** One numbered result of a tool: one fact (1 sentence, at most [ResultBook.MAX_FACT] characters) and where it came from. */
data class Result(val id: String, val tool: String, val text: String, val origin: Origin?, val role: ResultRole = ResultRole.FACT)

/**
 * The numbered results of one preparation (ADR-AGENT A-12): every tool's results, the ones taken before the
 * preparation included, in one sequence R1, R2, …. Threads point at them by number (A-29), and the speaker is
 * given the result's own text, never the preparer's wording.
 */
class ResultBook {
    private val items = LinkedHashMap<String, Result>()
    private var next = 1

    /** Adds [texts] from [tool] (each cut to [MAX_FACT] characters) and gives back the numbered results. */
    fun add(tool: String, texts: List<String>, origin: Origin? = null, role: ResultRole = ResultRole.FACT): List<Result> =
        texts.map { t -> Result("R${next++}", tool, cut(t.trim(), MAX_FACT), origin, role).also { items[it.id] = it } }

    operator fun get(id: String): Result? = items[id]

    val all: List<Result> get() = items.values.toList()

    /** The results as the preparer reads them: 「R1 …」 one per line, or 「（なし）」. */
    fun render(results: List<Result>): String = if (results.isEmpty()) "（なし）" else results.joinToString("\n") { "${it.id} ${it.text}" }

    companion object {
        const val MAX_FACT = 60

        internal fun cut(s: String, max: Int): String =
            if (s.codePointCount(0, s.length) <= max) s else s.substring(0, s.offsetByCodePoints(0, max))
    }
}
