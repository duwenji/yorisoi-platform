package io.github.duwenji.yorisoi.kit.web

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * How the app reaches the web (ADR-AGENT A-19・A-20): its own User-Agent (the app's name and version and the
 * operator's contact page), no cookies and no install id, and short waits so a slow site doesn't hold the
 * preparation up.
 *
 * @param userAgent e.g. 「SanpoGuide/1.16.0 (+https://duwenji.github.io/yorisoi/contact)」
 */
class WebHttp(val userAgent: String, pageTimeoutSeconds: Long = 5) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(pageTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(pageTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(pageTimeoutSeconds * 2, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** The product token robots.txt is read for: the User-Agent's first word without its version. */
    val productToken: String get() = userAgent.substringBefore('/').substringBefore(' ').lowercase()
}
