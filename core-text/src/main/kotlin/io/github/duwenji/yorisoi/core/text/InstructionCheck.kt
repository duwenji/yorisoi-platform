package io.github.duwenji.yorisoi.core.text

/**
 * Sentences that read as instructions to an AI rather than as information (AGENT A-18): text taken
 * from a page or a link title that tries to steer the AI that reads it. They are dropped before any AI
 * sees them. The match is kept narrow so ordinary notices (「申し込みは窓口へ」) pass.
 */
object InstructionCheck {
    private val PATTERNS = listOf(
        // Japanese
        "(?:これまで|以前|上記|前)の(?:指示|命令|設定|プロンプト)(?:を|は).{0,8}(?:無視|忘れ|取り消|破棄)",
        "(?:指示|命令|ルール|制約)を無視",
        "(?:以下|次)の(?:指示|命令)に従",
        "あなたは(?:これから|今から|今後)?.{0,20}(?:として|のように)(?:振る舞|ふるま|行動|答え|話し)",
        "システム(?:プロンプト|メッセージ|の指示)",
        "(?:AI|ＡＩ|アシスタント|言語モデル)(?:へ|に)の(?:指示|命令)",
        // English
        "(?i)ignore (?:all |any |the )?(?:previous|prior|above) (?:instructions|prompts?)",
        "(?i)disregard (?:all |the )?(?:previous|prior|above)",
        "(?i)you are now (?:a|an|the) ",
        "(?i)system prompt",
    ).map { Regex(it) }

    /** Whether [text] reads as an instruction to an AI. */
    fun looksLikeInstruction(text: String): Boolean {
        val plain = ContactCheck.normalize(text)
        return PATTERNS.any { it.containsMatchIn(plain) }
    }
}
