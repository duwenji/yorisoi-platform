package io.github.duwenji.yorisoi.kit.web

import io.github.duwenji.yorisoi.core.web.FeedItem
import io.github.duwenji.yorisoi.core.web.FeedText
import io.github.duwenji.yorisoi.core.web.Page
import io.github.duwenji.yorisoi.core.web.PageLink
import io.github.duwenji.yorisoi.core.web.PageText
import io.github.duwenji.yorisoi.core.web.Robots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What reading a URL gave. */
sealed interface Fetched {
    data class AsPage(val page: Page) : Fetched
    data class AsFeed(val items: List<FeedItem>) : Fetched
    /** Not read: robots.txt, the size, the kind of content, or the site failing. */
    data class NotRead(val reason: String) : Fetched
}

/**
 * Reads pages and feeds from the device (ADR-AGENT A-19・A-20・A-34). Two at a time, one per site; a page at most
 * [MAX_BYTES], HTML, text or a feed only; a failure is tried once more after a pause. robots.txt is read for
 * the app's product token and kept a day. A read is kept in [cache]: a source's own list page or feed for
 * [LIST_AGE], a page followed from it for [PAGE_AGE]; past that it is asked again with `If-None-Match` /
 * `If-Modified-Since`. Which URLs may be read is decided before, by core-web's ReadPolicy.
 */
class PageFetcher(
    private val http: WebHttp,
    private val cache: FileStore,
    private val now: () -> Instant = Instant::now,
    private val retryPause: Duration = Duration.ofSeconds(1),
) {
    private val all = Semaphore(2)
    private val sites = HashMap<String, Mutex>()

    private fun siteLock(host: String): Mutex = synchronized(sites) { sites.getOrPut(host) { Mutex() } }

    /** Reads [url]; [list] says it is a source's own list page or feed (kept a shorter time). */
    suspend fun fetch(url: String, list: Boolean): Fetched {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return Fetched.NotRead("not-web")
        if (!robots(url).allows(url)) return Fetched.NotRead("robots")
        val key = "page|$url"
        val kept = cache.get(key)
        val age = if (list) LIST_AGE else PAGE_AGE
        if (kept != null && Instant.ofEpochMilli(kept.facts.getLong("at")).plus(age).isAfter(now())) return decode(kept, url)
        return all.withPermit {
            siteLock(host).withLock { withContext(Dispatchers.IO) { readWithRetry(url, key, kept) } }
        }
    }

    private suspend fun readWithRetry(url: String, key: String, kept: FileStore.Entry?): Fetched {
        repeat(2) { attempt ->
            val r = runCatching { readOnce(url, key, kept) }
            r.getOrNull()?.let { return it }
            if (attempt == 0) delay(retryPause.toMillis())
        }
        return kept?.let { decode(it, url) } ?: Fetched.NotRead("failed")
    }

    /** One request; throws on a network failure or a server error, so the caller tries once more. */
    private fun readOnce(url: String, key: String, kept: FileStore.Entry?): Fetched {
        val req = Request.Builder().url(url).header("User-Agent", http.userAgent).apply {
            kept?.facts?.optString("etag")?.takeIf { it.isNotEmpty() }?.let { header("If-None-Match", it) }
            kept?.facts?.optString("lastModified")?.takeIf { it.isNotEmpty() }?.let { header("If-Modified-Since", it) }
        }.build()
        http.client.newCall(req).execute().use { res ->
            if (res.code == 304 && kept != null) {
                cache.touch(key, kept.facts.put("at", now().toEpochMilli()))
                return decode(kept, url)
            }
            if (res.code >= 500) throw IOException("server ${res.code}")
            if (!res.isSuccessful) return Fetched.NotRead("status-${res.code}")
            val type = res.header("Content-Type").orEmpty().lowercase()
            val feed = FEED_TYPES.any { type.contains(it) }
            if (!feed && HTML_TYPES.none { type.contains(it) }) return Fetched.NotRead("content-type")
            val source = res.body?.source() ?: return Fetched.NotRead("empty")
            // request() is true only when more than MAX_BYTES came: the page is not read (A-20).
            if (source.request(MAX_BYTES + 1)) return Fetched.NotRead("too-large")
            val bytes = source.buffer.readByteArray()
            val facts = JSONObject().put("at", now().toEpochMilli()).put("etag", res.header("ETag").orEmpty()).put("lastModified", res.header("Last-Modified").orEmpty())
            val fetched = if (feed || looksLikeFeed(bytes)) Fetched.AsFeed(FeedText.read(bytes))
            else Fetched.AsPage(PageText.read(String(bytes, charsetOf(type)), res.request.url.toString()))
            cache.put(key, encode(fetched).toByteArray(Charsets.UTF_8), facts)
            return fetched
        }
    }

    private val robotsKept = HashMap<String, Pair<Instant, Robots>>()

    /** The site's robots.txt for the app, kept a day; a missing or failing one allows everything. */
    private suspend fun robots(url: String): Robots {
        val u = URI(url)
        val base = "${u.scheme}://${u.authority}"
        synchronized(robotsKept) { robotsKept[base]?.takeIf { it.first.plus(ROBOTS_AGE).isAfter(now()) }?.let { return it.second } }
        val r = withContext(Dispatchers.IO) {
            runCatching {
                http.client.newCall(Request.Builder().url("$base/robots.txt").header("User-Agent", http.userAgent).build()).execute().use { res ->
                    if (res.isSuccessful) Robots.parse(res.body?.string().orEmpty(), http.productToken) else Robots.ALLOW_ALL
                }
            }.getOrDefault(Robots.ALLOW_ALL)
        }
        synchronized(robotsKept) { robotsKept[base] = now() to r }
        return r
    }

    private fun looksLikeFeed(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 300), Charsets.UTF_8).trimStart()
        return head.startsWith("<?xml") && (head.contains("<rss") || head.contains("<feed") || head.contains("<rdf"))
    }

    private fun charsetOf(type: String) = runCatching {
        java.nio.charset.Charset.forName(Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1) ?: "UTF-8")
    }.getOrDefault(Charsets.UTF_8)

    private fun encode(f: Fetched): String = when (f) {
        is Fetched.AsPage -> JSONObject().put("kind", "page").put("title", f.page.title).put("published", f.page.published?.toString() ?: JSONObject.NULL)
            .put("text", f.page.text).put("links", JSONArray(f.page.links.map { JSONObject().put("t", it.title).put("u", it.url) })).toString()
        is Fetched.AsFeed -> JSONObject().put("kind", "feed").put("items", JSONArray(f.items.map {
            JSONObject().put("t", it.title).put("u", it.url).put("d", it.date?.toString() ?: JSONObject.NULL)
        })).toString()
        is Fetched.NotRead -> error("not kept")
    }

    private fun decode(e: FileStore.Entry, url: String): Fetched {
        val o = JSONObject(String(e.bytes, Charsets.UTF_8))
        return if (o.getString("kind") == "feed") {
            val a = o.getJSONArray("items")
            Fetched.AsFeed((0 until a.length()).map { i -> a.getJSONObject(i).let { FeedItem(it.getString("t"), it.getString("u"), it.optString("d").takeIf { d -> d.isNotEmpty() && d != "null" }?.let(LocalDate::parse)) } })
        } else {
            val a = o.getJSONArray("links")
            Fetched.AsPage(Page(o.getString("title"), o.optString("published").takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDate::parse), o.getString("text"),
                (0 until a.length()).map { i -> a.getJSONObject(i).let { PageLink(it.getString("t"), it.getString("u")) } }))
        }
    }

    companion object {
        const val MAX_BYTES = 1_048_576L
        val LIST_AGE: Duration = Duration.ofHours(6)
        val PAGE_AGE: Duration = Duration.ofDays(7)
        val ROBOTS_AGE: Duration = Duration.ofDays(1)
        private val HTML_TYPES = listOf("text/html", "application/xhtml+xml", "text/plain")
        private val FEED_TYPES = listOf("rss+xml", "atom+xml", "rdf+xml", "application/xml", "text/xml")
    }
}
