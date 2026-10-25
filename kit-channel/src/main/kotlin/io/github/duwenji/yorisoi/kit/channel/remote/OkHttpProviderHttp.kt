package io.github.duwenji.yorisoi.kit.channel.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [ProviderHttp] over OkHttp: no location or user data in the request (API-002 位置情報), and a byte
 * limit. The app adds OkHttp to its dependencies; kit-channel only compiles against it.
 *
 * @param userAgent the app and its version, as `CycleGuide/1.2.0`
 */
class OkHttpProviderHttp(
    private val userAgent: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) : ProviderHttp {
    override fun get(url: String, maxBytes: Int): ByteArray {
        client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("empty response")
            if (body.contentLength() > maxBytes) throw TooLargeException(url)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw TooLargeException(url)
                }
            }
            return out.toByteArray()
        }
    }
}
