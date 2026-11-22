package io.github.duwenji.yorisoi.core.web

import org.json.JSONObject
import java.net.URI

/**
 * The operator's exclusion list (ADR-AGENT A-24・A-26; docs/contracts/api/web-exclusion.schema.json): a
 * domain with the domains under it, or a URL prefix. A channel's `agent.skip` adds to it and never takes away.
 */
class ExclusionList(val version: Int, private val domains: List<Pair<String, String>>, private val prefixes: List<Pair<String, String>>) {
    /** An entry [url] falls under: its kind, and whether it was a domain entry (else a URL prefix). */
    data class Hit(val kind: String, val byDomain: Boolean)

    /** The entry [url] falls under, or null when it isn't excluded. */
    fun match(url: String): Hit? {
        val host = hostOf(url) ?: return null
        domains.firstOrNull { (d, _) -> host == d || host.endsWith(".$d") }?.let { return Hit(it.second, true) }
        prefixes.firstOrNull { (p, _) -> url.startsWith(p) }?.let { return Hit(it.second, false) }
        return null
    }

    /** This list with a channel's `agent.skip` domains and prefixes added (kind 「channel-skip」). */
    fun plus(skip: List<String>): ExclusionList {
        val (urls, names) = skip.partition { it.startsWith("http://") || it.startsWith("https://") }
        return ExclusionList(version, domains + names.map { it.lowercase() to "channel-skip" }, prefixes + urls.map { it to "channel-skip" })
    }

    companion object {
        fun parse(json: String): ExclusionList {
            val o = JSONObject(json)
            val e = o.getJSONArray("entries")
            val domains = ArrayList<Pair<String, String>>(); val prefixes = ArrayList<Pair<String, String>>()
            for (i in 0 until e.length()) {
                val x = e.getJSONObject(i); val m = x.getJSONObject("match"); val kind = x.getString("kind")
                if (m.has("domain")) domains += m.getString("domain").lowercase() to kind else prefixes += m.getString("prefix") to kind
            }
            return ExclusionList(o.getInt("version"), domains, prefixes)
        }

        internal fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
    }
}
