package io.github.duwenji.yorisoi.kit.web

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

class KitWebTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private var now = Instant.parse("2026-11-22T03:00:00Z")
    private val requests = ArrayList<RecordedRequest>()
    private val http = WebHttp("SanpoGuide/1.16.0 (+https://example.jp/contact)")

    private val page = """<html><head><title>お知らせ</title></head><body><main>
        <p>11月下旬から越前水仙の出荷が始まります。</p><p class="d">更新日：2026年11月20日</p>
        <a href="/a.html">11月のイベント</a></main></body></html>"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return when (request.path) {
                    "/robots.txt" -> MockResponse().setBody("User-agent: *\nDisallow: /private/\n")
                    "/news/" -> if (request.getHeader("If-None-Match") == "\"v1\"") MockResponse().setResponseCode(304)
                        else MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setHeader("ETag", "\"v1\"").setBody(page)
                    "/feed.xml" -> MockResponse().setHeader("Content-Type", "application/rss+xml").setBody(
                        """<?xml version="1.0"?><rss><channel><item><title>水仙</title><link>https://example.jp/1</link><pubDate>Fri, 20 Nov 2026 09:00:00 +0900</pubDate></item></channel></rss>""")
                    "/big.html" -> MockResponse().setHeader("Content-Type", "text/html").setBody("あ".repeat(400_000))
                    "/file.pdf" -> MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF")
                    "/flaky.html" -> if (requests.count { it.path == "/flaky.html" } == 1) MockResponse().setResponseCode(503)
                        else MockResponse().setHeader("Content-Type", "text/html").setBody(page)
                    "/ledger.json" -> if (request.getHeader("If-None-Match") == "\"L1\"") MockResponse().setResponseCode(304)
                        else MockResponse().setHeader("ETag", "\"L1\"").setBody("""{"version":3}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun fetcher(store: FileStore = FileStore(tmp.newFolder(), 20_000_000)) = PageFetcher(http, store, now = { now }, retryPause = Duration.ZERO)

    @Test
    fun `a page is read with the app's User-Agent and no cookies, kept, and asked again with its ETag`() = runBlocking {
        val store = FileStore(tmp.newFolder(), 20_000_000)
        val f = fetcher(store)
        val p = f.fetch(server.url("/news/").toString(), list = true) as Fetched.AsPage
        assertTrue(p.page.text.contains("越前水仙"))
        assertEquals("2026-11-20", p.page.published.toString())
        val first = requests.first { it.path == "/news/" }
        assertEquals("SanpoGuide/1.16.0 (+https://example.jp/contact)", first.getHeader("User-Agent"))
        assertNull(first.getHeader("Cookie"))

        f.fetch(server.url("/news/").toString(), list = true)
        assertEquals("kept within 6 hours", 1, requests.count { it.path == "/news/" })
        now = now.plus(Duration.ofHours(7))
        val again = f.fetch(server.url("/news/").toString(), list = true)
        assertTrue(again is Fetched.AsPage)
        assertEquals("\"v1\"", requests.last { it.path == "/news/" }.getHeader("If-None-Match"))
    }

    @Test
    fun `robots, size, kind of content and a failing site`() = runBlocking {
        val f = fetcher()
        assertEquals(Fetched.NotRead("robots"), f.fetch(server.url("/private/x.html").toString(), list = false))
        assertEquals(Fetched.NotRead("too-large"), f.fetch(server.url("/big.html").toString(), list = false))
        assertEquals(Fetched.NotRead("content-type"), f.fetch(server.url("/file.pdf").toString(), list = false))
        assertTrue("tried once more after a 503", f.fetch(server.url("/flaky.html").toString(), list = false) is Fetched.AsPage)
        assertEquals(1, requests.count { it.path == "/robots.txt" })
    }

    @Test
    fun `a feed gives its items`() = runBlocking {
        val r = fetcher().fetch(server.url("/feed.xml").toString(), list = true) as Fetched.AsFeed
        assertEquals("水仙", r.items.single().title)
    }

    @Test
    fun `delivered files are kept, asked again with their ETag, and known when never fetched`() = runBlocking {
        val files = DeliveredFiles(http, FileStore(tmp.newFolder(), 1_000_000), now = { now })
        val url = server.url("/ledger.json").toString()
        val d = files.get(url, Duration.ofDays(7))
        assertTrue(d.everFetched)
        assertEquals("""{"version":3}""", String(d.bytes!!))
        files.get(url, Duration.ofDays(7))
        assertEquals(1, requests.count { it.path == "/ledger.json" })
        now = now.plus(Duration.ofDays(8))
        assertEquals("""{"version":3}""", String(files.get(url, Duration.ofDays(7)).bytes!!))
        assertEquals("\"L1\"", requests.last { it.path == "/ledger.json" }.getHeader("If-None-Match"))
        assertFalse(files.get(server.url("/missing.json").toString(), Duration.ofDays(1)).everFetched)
    }

    @Test
    fun `the store stays under its size, dropping what was used longest ago, and hints follow the page text`() {
        val store = FileStore(tmp.newFolder(), 3_000)
        store.put("a", ByteArray(1_000), org.json.JSONObject())
        Thread.sleep(20)
        store.put("b", ByteArray(1_000), org.json.JSONObject())
        Thread.sleep(20)
        store.get("a")
        store.put("c", ByteArray(1_500), org.json.JSONObject())
        assertTrue(store.size() <= 3_000)
        assertNull(store.get("b"))

        val hints = HintCache(FileStore(tmp.newFolder(), 1_000_000), now = { now })
        hints.put("https://x/1", "本文", listOf("水仙の出荷"))
        assertEquals(listOf("水仙の出荷"), hints.get("https://x/1", "本文", list = true))
        assertNull("the page changed", hints.get("https://x/1", "新しい本文", list = true))
        now = now.plus(Duration.ofHours(7))
        assertNull("older than a list page is kept", hints.get("https://x/1", "本文", list = true))
    }
}
