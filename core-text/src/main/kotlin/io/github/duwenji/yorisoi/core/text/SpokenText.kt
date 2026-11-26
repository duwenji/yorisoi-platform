package io.github.duwenji.yorisoi.core.text

/**
 * Text as it may be read out. Stage directions the AI sometimes writes for its own delivery, Markdown marks
 * such as 「**続き：**」, and notes on how it wrote the reply (「〜にひと言触れました」) are taken out, since
 * they'd be read out.
 */
object SpokenText {
    /** Words a direction on how to say a line uses, in the four languages (REQ-LANGUAGES F-13). */
    private const val DIRECTION_WORDS =
        "口調|声|トーン|調子|語り|話し|間を|笑|微笑|ささや|囁" +
            "|\\b(?:voice|tone|softly|gently|quietly|whisper\\w*|laugh\\w*|smil\\w*|pause|calm\\w*|cheerful\\w*)\\b" +
            "|语气|语调|轻声|低声|笑着|停顿" +
            "|목소리|어조|톤|말투|속삭|웃|부드럽게|차분하게|잠시 멈"

    /**
     * A bracketed direction at the start of a sentence about how to say it, such as
     * 「（しっとりと、落ち着いた口調で）」「(softly, in a calm voice)」「（语气轻柔）」「(부드러운 목소리로)」. Readings like
     * 吽形（うんぎょう） follow a word, so they stay.
     */
    private val STAGE_DIRECTION = Regex(
        "(?:^|(?<=[。！？!?.\n]))[ \t　]*[（(][^（）()\n]{0,40}(?:$DIRECTION_WORDS)[^（）()\n]{0,40}[）)][ \t　]*",
        RegexOption.IGNORE_CASE,
    )

    /** Bold and italic marks (「**」「*」「__」) and headings at the start of a line. Single underscores stay. */
    private val EMPHASIS = Regex("\\*+|_{2,}")
    private val HEADING = Regex("(?m)^[ \t　]*#{1,6}[ \t　]+")

    /**
     * Words a line of speech doesn't use but a note on the reply does: the prompt's own words for the
     * reply and the user, and saying what was or wasn't mentioned. 「余談」 stays: 「余談ですが」 is speech.
     */
    private val META_NOTE = Regex(
        "触れました|触れていません|言及し(?:ま|な|て)|本文|字数|文字数|プロンプト|指示(?:に従|どおり|通り|のとおり)|利用者|出力し" +
            // The same in the other languages (REQ-LANGUAGES F-13).
            "|\\b(?:word count|character count|the prompt|the instructions|the user)\\b|提示词|按照指示|用户" +
            "|글자 ?수|프롬프트|지시(?:에 따라|대로)|사용자",
        RegexOption.IGNORE_CASE,
    )

    private val BLANK_LINES = Regex("\n{3,}")

    /**
     * A heading or a list item at the start of a line: a reply written as notes (「# 調べた手がかり」「- …」), not
     * as speech. Numbered lines (「1：」) aren't counted: a batch of chats is numbered on purpose.
     */
    private val NOTE_LINE = Regex("(?m)^[ \t　]*(?:#{1,6}[ \t　]|[-+•‣][ \t　]|・)")

    /** Whether [text] is written as notes (headings, list items) rather than as speech: not read out (AI-ROLES H-6 ③). */
    fun isNotes(text: String): Boolean = NOTE_LINE.containsMatchIn(text)

    /** [text] as it may be read out: without stage directions, Markdown marks and notes on the reply. */
    fun spoken(text: String): String = withoutMetaNotes(withoutStageDirections(withoutMarkdown(text)))

    /** [text] without the lines that are notes on the reply rather than speech; the rest as it was. */
    fun withoutMetaNotes(text: String): String =
        text.lines().filterNot { META_NOTE.containsMatchIn(it) }.joinToString("\n").replace(BLANK_LINES, "\n\n").trim()

    /** [text] without the stage directions in it. */
    fun withoutStageDirections(text: String): String = STAGE_DIRECTION.replace(text, "").trim()

    /** [text] without Markdown's bold, italic and heading marks. */
    fun withoutMarkdown(text: String): String = HEADING.replace(EMPHASIS.replace(text, ""), "").trim()
}
