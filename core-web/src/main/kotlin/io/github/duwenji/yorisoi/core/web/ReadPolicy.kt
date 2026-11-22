package io.github.duwenji.yorisoi.core.web

import java.net.URI

/** Why a URL may not be read (ADR-AGENT A-17・A-19・A-24・A-26). */
enum class Deny(val id: String) {
    NOT_WEB("not-web"),
    TOO_DEEP("too-deep"),
    OTHER_SITE("other-site"),
    OUTSIDE_SOURCE_PATH("outside-source-path"),
    EXCLUDED_DOMAIN("excluded-domain"),
    EXCLUDED_PREFIX("excluded-prefix"),
    NO_EXCLUSION_LIST("no-exclusion-list"),
    ROBOTS("robots"),
}

/**
 * Which URLs a preparation may read (ADR-AGENT A-17): a source's own URLs, and pages reached from them by
 * following links on the same site, at most [MAX_DEPTH] times. A source on a shared service (a blog or a
 * site builder) reaches only under its own host and path. Nothing excluded is read; until an exclusion list
 * has been fetched once, only the ledger's own URLs are read (A-26).
 *
 * @param exclusion the operator's list with the channel's `skip` added, or null when none was ever fetched
 */
class ReadPolicy(private val exclusion: ExclusionList?) {
    /**
     * Why [url] may not be read, or null when it may.
     *
     * @param source the source's own URL the path started from
     * @param fromLedger whether that source is the ledger's (false: a channel's)
     * @param depth how many links were followed from the source (0: the source's own URL)
     * @param robotsAllows whether the site's robots.txt lets the app read [url]
     */
    fun check(url: String, source: String, fromLedger: Boolean, depth: Int, robotsAllows: Boolean = true): Deny? {
        val u = runCatching { URI(url) }.getOrNull() ?: return Deny.NOT_WEB
        if (u.scheme !in setOf("http", "https") || u.host.isNullOrEmpty()) return Deny.NOT_WEB
        if (exclusion == null && !(fromLedger && depth == 0)) return Deny.NO_EXCLUSION_LIST
        if (depth > MAX_DEPTH) return Deny.TOO_DEEP
        if (depth > 0) {
            val s = URI(source)
            val host = u.host.lowercase(); val sourceHost = s.host.lowercase()
            if (PublicSuffix.registrableDomain(host) != PublicSuffix.registrableDomain(sourceHost)) return Deny.OTHER_SITE
            if (PublicSuffix.isSharedService(sourceHost)) {
                val base = (s.path ?: "/").substringBeforeLast('/') + "/"
                if (host != sourceHost || !(u.path ?: "/").startsWith(base)) return Deny.OUTSIDE_SOURCE_PATH
            }
        }
        exclusion?.match(url)?.let { hit -> return if (hit.byDomain) Deny.EXCLUDED_DOMAIN else Deny.EXCLUDED_PREFIX }
        if (!robotsAllows) return Deny.ROBOTS
        return null
    }

    companion object {
        /** How many links a path may follow from a source's own URL (ADR-AGENT A-17). */
        const val MAX_DEPTH = 2
    }
}

