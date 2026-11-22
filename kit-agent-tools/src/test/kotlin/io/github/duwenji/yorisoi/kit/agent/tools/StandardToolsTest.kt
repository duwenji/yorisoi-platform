package io.github.duwenji.yorisoi.kit.agent.tools

import io.github.duwenji.yorisoi.core.agent.ResultBook
import io.github.duwenji.yorisoi.core.agent.ResultRole
import io.github.duwenji.yorisoi.core.web.ExclusionList
import io.github.duwenji.yorisoi.core.web.ReadPolicy
import io.github.duwenji.yorisoi.core.web.Source
import io.github.duwenji.yorisoi.core.web.SourceKind
import io.github.duwenji.yorisoi.core.web.SourceRead
import io.github.duwenji.yorisoi.kit.calendar.KoyomiFact
import io.github.duwenji.yorisoi.kit.calendar.KoyomiKind
import io.github.duwenji.yorisoi.kit.calendar.Relation
import io.github.duwenji.yorisoi.kit.calendar.SeasonObsFile
import io.github.duwenji.yorisoi.kit.web.FileStore
import io.github.duwenji.yorisoi.kit.web.HintCache
import io.github.duwenji.yorisoi.kit.web.PageFetcher
import io.github.duwenji.yorisoi.kit.web.WebHttp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class StandardToolsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val today = LocalDate.of(2026, 11, 22)
    private var hintCalls = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/robots.txt" -> MockResponse().setResponseCode(404)
                "/news/" -> MockResponse().setHeader("Content-Type", "text/html").setBody(
                    """<html><title>お知らせ一覧</title><main><a href="/news/1.html">越前水仙の出荷</a><a href="https://other.example/x">よそ</a></main></html>""")
                "/news/1.html" -> MockResponse().setHeader("Content-Type", "text/html").setBody(
                    """<html><title>越前水仙</title><main><p>11月下旬から越前水仙の出荷が始まります。</p><p>更新日：2026年11月20日</p></main></html>""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun tools(policy: AgentPolicy, input: PrepInput, exclusion: ExclusionList? = ExclusionList.parse("""{"version":1,"entries":[]}""")): StandardTools {
        val http = WebHttp("SanpoGuide/1.16.0")
        val fetcher = PageFetcher(http, FileStore(tmp.newFolder(), 20_000_000), retryPause = Duration.ZERO)
        return StandardTools(input, policy, ReadPolicy(exclusion), fetcher, HintCache(FileStore(tmp.newFolder(), 1_000_000)), { _, user ->
            hintCalls++
            assertTrue("the page text goes to the hint maker", user.contains("越前水仙の出荷が始まります"))
            "- [1] 11月下旬から越前水仙の出荷が始まる"
        }, place = "福井市")
    }

    private val ledgerSource
        get() = Source("fukui", "福井市", SourceKind.MUNICIPALITY, "182010", listOf(SourceRead(server.url("/news/").toString(), feed = false, title = "お知らせ")), setOf("livelihood", "events"))

    @Test
    fun `the facts taken before the preparation follow the channel's policy`() {
        val koyomi = listOf(
            KoyomiFact("s", KoyomiKind.SEKKI, "小雪", "しょうせつ", today, Relation.Today, "雪が降り始めるころ"),
            KoyomiFact("r", KoyomiKind.ROKUYOU, "仏滅", null, today, Relation.Today, "六曜"),
        )
        val obs = SeasonObsFile.parse("""{"season":"2027","attribution":"気象庁","observations":[{"kind":"first-snow","date":"2026-11-22","place":"福井地方気象台","prefecture":"18","fromNormal":-11,"fromLastYear":-11}]}""")
        val book = ResultBook()
        val text = tools(AgentPolicy(setOf("calendar", "season", "livelihood"), calendarSkip = setOf(KoyomiKind.ROKUYOU)), PrepInput(today, "18", koyomi = koyomi, seasonObs = obs)).prefetch(book)
        assertTrue(text.contains("R1 今日 11月22日は小雪"))
        assertFalse("rokuyou skipped", text.contains("仏滅"))
        assertTrue(text.contains("福井地方気象台で 11月22日に初雪を観測"))
        assertFalse("no events scene", text.contains("## 催し"))
        assertEquals(setOf("calendar", "season_obs"), book.all.map { it.tool }.toSet())

        val noCalendar = tools(AgentPolicy(setOf("livelihood")), PrepInput(today, "18", koyomi = koyomi)).prefetch(ResultBook())
        assertFalse(noCalendar.contains("小雪"))
    }

    @Test
    fun `a source's list gives links, a link gives hints, and the preparer never sees the page`() = runBlocking {
        val t = tools(AgentPolicy(setOf("livelihood")), PrepInput(today, "18", sources = listOf(ledgerSource)))
        assertTrue(t.sourceList().startsWith("P1 福井市 お知らせ（市区町村）"))
        val book = ResultBook()
        val list = t.run("read_page", JSONObject("""{"ids":["P1"]}"""), book)
        assertTrue(list.text.contains("L1 越前水仙の出荷"))
        assertFalse("other sites are not offered", list.text.contains("よそ"))
        assertTrue(list.results.all { it.role == ResultRole.LINKS || it.role == ResultRole.EMPTY_PAGE })

        val page = t.run("read_page", JSONObject("""{"ids":["L1"]}"""), book)
        assertEquals(1, hintCalls)
        val hint = page.results.single { it.role == ResultRole.FACT }
        assertEquals("11月下旬から越前水仙の出荷が始まる", hint.text)
        assertEquals("municipality", hint.origin?.kind)
        assertEquals("2026-11-20", hint.origin?.date)
        assertFalse("the page's own text is not in the answer", page.text.contains("始まります。"))

        t.run("read_page", JSONObject("""{"ids":["L1"]}"""), book)
        assertEquals("the hints are kept", 1, hintCalls)
        assertTrue(t.run("read_page", JSONObject("""{"ids":["P9"]}"""), book).text.contains("番号がありません"))
    }

    @Test
    fun `without an exclusion list ever fetched, links are not followed, and web off reads nothing`() = runBlocking {
        val t = tools(AgentPolicy(setOf("livelihood")), PrepInput(today, "18", sources = listOf(ledgerSource)), exclusion = null)
        t.sourceList()
        val book = ResultBook()
        assertFalse(t.run("read_page", JSONObject("""{"ids":["P1"]}"""), book).text.contains("L1"))
        val off = tools(AgentPolicy(setOf("livelihood"), web = false), PrepInput(today, "18", sources = listOf(ledgerSource)))
        assertEquals("なし", off.sourceList())
        assertEquals("ページは読めません", off.run("read_page", JSONObject("""{"ids":["P1"]}"""), ResultBook()).text)
    }
}
