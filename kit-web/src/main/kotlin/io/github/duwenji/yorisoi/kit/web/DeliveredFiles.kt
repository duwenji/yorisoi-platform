package io.github.duwenji.yorisoi.kit.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** A delivered file as the app holds it: its bytes and when they were fetched; [bytes] null when never fetched. */
data class Delivered(val bytes: ByteArray?, val fetchedAt: Instant?) {
    /** Whether a version was ever fetched (ADR-AGENT A-26: some reads wait for the first one). */
    val everFetched: Boolean get() = bytes != null
}

/**
 * The operator's delivered files (ADR-AGENT A-26・A-28): the sources' ledger, the exclusion list and the season's
 * observations, from the same delivery as the spot tiles. A file is asked again once it is older than the
 * caller's age, with `If-None-Match`; when that fails the one held is used; a file never fetched comes back
 * with no bytes, so the caller can read in the narrower way the ADR sets for it.
 */
class DeliveredFiles(private val http: WebHttp, private val store: FileStore, private val now: () -> Instant = Instant::now) {
    /** [url]'s file, fetched again when the one held is older than [maxAge]. */
    suspend fun get(url: String, maxAge: Duration): Delivered = withContext(Dispatchers.IO) {
        val key = "delivered|$url"
        val kept = store.get(key)
        val at = kept?.facts?.optLong("at")?.let(Instant::ofEpochMilli)
        if (kept != null && at != null && at.plus(maxAge).isAfter(now())) return@withContext Delivered(kept.bytes, at)
        val req = Request.Builder().url(url).header("User-Agent", http.userAgent).apply {
            kept?.facts?.optString("etag")?.takeIf { it.isNotEmpty() }?.let { header("If-None-Match", it) }
        }.build()
        runCatching {
            http.client.newCall(req).execute().use { res ->
                when {
                    res.code == 304 && kept != null -> {
                        store.touch(key, kept.facts.put("at", now().toEpochMilli()))
                        Delivered(kept.bytes, now())
                    }
                    res.isSuccessful -> {
                        val bytes = res.body?.bytes() ?: ByteArray(0)
                        store.put(key, bytes, JSONObject().put("at", now().toEpochMilli()).put("etag", res.header("ETag").orEmpty()))
                        Delivered(bytes, now())
                    }
                    else -> null
                }
            }
        }.getOrNull() ?: Delivered(kept?.bytes, at)
    }
}
