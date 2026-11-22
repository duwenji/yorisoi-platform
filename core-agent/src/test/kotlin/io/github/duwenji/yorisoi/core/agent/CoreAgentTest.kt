package io.github.duwenji.yorisoi.core.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreAgentTest {
    private val tools = AgentTools.load()

    @Test
    fun `the shared definitions are packed and checked`() {
        assertEquals(1, tools.version)
        assertEquals(setOf("read_page", "nearby_spots", "materials"), tools.lookups.map { it.name }.toSet())
        assertTrue(tools.argumentErrors("read_page", JSONObject("""{"ids":["P1","L2"]}""")).isEmpty())
        assertTrue(tools.argumentErrors("read_page", JSONObject("""{"ids":["http://x"]}""")).isNotEmpty())
        assertTrue(tools.argumentErrors("read_page", JSONObject("""{"ids":["P1","P2","P3","P4","P5","P6"]}""")).isNotEmpty())
        assertTrue(tools.argumentErrors("read_page", JSONObject("""{"ids":["P1"],"url":"x"}""")).isNotEmpty())
        assertEquals(listOf("unknown tool web_search"), tools.argumentErrors("web_search", JSONObject()))
    }

    private fun book(): ResultBook = ResultBook().apply {
        add("calendar", listOf("今日 11月22日は二十四節気の小雪"))                                     // R1
        add("season_obs", listOf("福井地方気象台で 11月22日に初雪を観測、平年より 11 日早い"))          // R2
        add("read_page", listOf("福井市のお知らせでは、11月下旬から越前水仙の出荷が始まる"),           // R3
            Origin("福井市", "municipality", "お知らせ", "https://www.city.fukui.lg.jp/news/", "2026-11-20"))
        add("read_page", listOf("L1 11月のイベント"), role = ResultRole.LINKS)                     // R4
    }

    private fun submit(json: String, scenes: Set<String> = setOf("calendar", "season", "livelihood", "events")) =
        ThreadCheck.check(JSONObject(json), book(), scenes)

    @Test
    fun `a good thread is taken with the results' own text`() {
        val c = submit("""{"threads":[{"path":"calendar>season>livelihood","steps":[
            {"topic":"今日は小雪","refs":["R1"],"bridge":""},
            {"topic":"初雪が早め","refs":["R2"],"bridge":"季節の進みとして"},
            {"topic":"水仙の出荷","refs":["R3"],"bridge":"地域の営みに目を向けて"}]}]}""")
        assertEquals(1, c.threads.size)
        assertEquals("福井市のお知らせでは、11月下旬から越前水仙の出荷が始まる", c.threads[0].steps[2].results[0].text)
        assertEquals("municipality", c.threads[0].steps[2].results[0].origin?.kind)
        assertTrue(c.dropped.isEmpty())
    }

    @Test
    fun `numbers outside the book, wrong scenes, links and repeats are taken out`() {
        val c = submit("""{"threads":[{"path":"calendar>livelihood","steps":[
            {"topic":"小雪","refs":["R1","R9"],"bridge":""},
            {"topic":"暮らし","refs":["R1","R4","R3","R3"],"bridge":"さっきの話に関連して"}]}]}""")
        val steps = c.threads.single().steps
        assertEquals(listOf("R1"), steps[0].results.map { it.id })
        assertEquals(listOf("R3"), steps[1].results.map { it.id })
        assertTrue(c.dropped.any { "R9" in it })
        assertTrue(c.dropped.any { "R1 (calendar) does not serve livelihood" in it })
        assertTrue(c.dropped.any { "R4 is not a fact" in it })
    }

    @Test
    fun `a bridge with a digit is emptied, and ordinary words stay`() {
        val c = submit("""{"threads":[{"path":"calendar>season","steps":[
            {"topic":"小雪","refs":["R1"],"bridge":"一緒に歩きながら"},
            {"topic":"初雪","refs":["R2"],"bridge":"平年より11日早く"}]}]}""")
        assertEquals("一緒に歩きながら", c.threads.single().steps[0].bridge)
        assertEquals("", c.threads.single().steps[1].bridge)
    }

    @Test
    fun `a thread with one step left, a free-text path, and scenes the channel stopped go`() {
        assertTrue(submit("""{"threads":[{"path":"calendar>season","steps":[{"topic":"a","refs":["R1"],"bridge":""},{"topic":"b","refs":["R9"],"bridge":""}]}]}""").threads.isEmpty())
        assertTrue(submit("""{"threads":[{"path":"小雪→冬の訪れ","steps":[{"topic":"a","refs":["R1"],"bridge":""},{"topic":"b","refs":["R2"],"bridge":""}]}]}""").threads.isEmpty())
        val noCalendar = submit("""{"threads":[{"path":"calendar>season","steps":[{"topic":"a","refs":["R1"],"bridge":""},{"topic":"b","refs":["R2"],"bridge":""}]}]}""", scenes = setOf("season"))
        assertTrue(noCalendar.threads.isEmpty())
    }

    @Test
    fun `a preparation offers only the submission last, runs four tools a reply, and drops the words`() {
        val offered = ArrayList<List<String>>()
        val mustUse = ArrayList<Boolean>()
        val model = PreparerModel { _, turns, t, must ->
            offered += t.map { it.name }; mustUse += must
            when (offered.size) {
                1 -> Reply((1..5).map { ToolUse("u$it", "read_page", JSONObject("""{"ids":["P1"]}""")) } + ToolUse("bad", "read_page", JSONObject("""{"ids":["https://x"]}""")), "<｜DSML｜function_calls")
                2 -> Reply(listOf(ToolUse("u9", "web_search", JSONObject())), "")
                else -> {
                    val answers = turns.filterIsInstance<Turn.Answers>().first().answers
                    assertEquals("上限を超えたので実行しませんでした", answers[4].text)
                    assertTrue(turns.none { it is Turn.Asked && it.toString().contains("DSML") })
                    Reply(listOf(ToolUse("s", AgentTools.SUBMIT, JSONObject("""{"threads":[{"path":"calendar>season","steps":[{"topic":"a","refs":["R1"],"bridge":""},{"topic":"b","refs":["R2"],"bridge":""}]}]}"""))), "")
                }
            }
        }
        var ran = 0
        val runner = ToolRunner { name, _, book -> ran++; ToolOutcome("（なし）", emptyList()) }
        val b = ResultBook().apply { add("calendar", listOf("小雪")); add("season_obs", listOf("初雪")) }
        val p = Preparation(tools, model, runner).run("system", "input", b, setOf("calendar", "season"))
        assertEquals(4, ran)
        assertEquals(listOf(false, false, true), mustUse)
        assertEquals(listOf(AgentTools.SUBMIT), offered[2])
        assertEquals(1, p.checked!!.threads.size)
        assertTrue(p.refused.any { it.startsWith("web_search") })
    }

    @Test
    fun `the results are cut by rule when they grow past the budget`() {
        val book = ResultBook()
        val links = book.add("read_page", listOf("L1 一覧"), role = ResultRole.LINKS)
        val facts = book.add("read_page", listOf("越前水仙の出荷が始まる"))
        val turns = mutableListOf<Turn>(
            Turn.Input("x"),
            Turn.Answers(mutableListOf(Answer("a", "あ".repeat(6_000), links), Answer("b", "い".repeat(6_000), facts))),
        )
        assertEquals(1, ContextBudget.fit(turns))
        val answers = (turns[1] as Turn.Answers).answers
        assertTrue(answers[0].text.endsWith(ContextBudget.CUT_NOTE))
        assertTrue(answers[1].text.startsWith("い"))
    }

    @Test
    fun `hints are read by page and checked`() {
        val pages = listOf(HintPage("お知らせ", "11月下旬から越前水仙の出荷が始まります。"), HintPage("催し", "12月12日に戦国馬さんぽ。"))
        val answer = """
            前置きです。
            - [1] 11月下旬から越前水仙の出荷が始まる
            - [1] 出荷は12月まで続く
            - [2] 12月12日に戦国馬さんぽがある
            - [2] お問い合わせは 0776-20-5111
            - [3] ページにない
        """.trimIndent()
        val hints = HintMaker.read(answer, pages)
        assertEquals(listOf("11月下旬から越前水仙の出荷が始まる"), hints[0])
        assertEquals(listOf("12月12日に戦国馬さんぽがある"), hints[1])
        assertTrue(HintMaker.read("なし", pages).all { it.isEmpty() })
        assertTrue(HintMaker.read("- [1] なし\n- [2] なし。", pages).all { it.isEmpty() })
        assertTrue(HintMaker.request(pages, "福井市", "2026-11-22").contains("<<<ページ 2: 催し"))
    }

    @Test
    fun `the preparer's instructions need the headings and the guard last`() {
        val ok = "# 役割\nあ\n# 進め方\nい\n# 事実の扱い\nう\n# 守ること\nえ"
        assertTrue(PromptCheck.problems(ok).isEmpty())
        assertEquals(listOf("no # 守ること"), PromptCheck.problems("# 役割\n# 進め方\n# 事実の扱い"))
        assertTrue(PromptCheck.problems("# 役割\n# 進め方\n# 守ること\n# 事実の扱い").isNotEmpty())
        assertNull(PromptCheck.problems(ok).firstOrNull())
    }
}
