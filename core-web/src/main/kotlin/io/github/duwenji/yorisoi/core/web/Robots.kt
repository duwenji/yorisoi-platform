package io.github.duwenji.yorisoi.core.web

import java.net.URI

/**
 * A site's robots.txt (AGENT A-19): the group for the app's product token, or else for `*`, and its
 * Allow and Disallow rules. The longest matching rule wins; Allow wins a tie. `*` and a closing `$` work
 * as in RFC 9309. A missing or unreadable robots.txt allows everything.
 */
class Robots private constructor(private val rules: List<Triple<Boolean, Regex, Int>>) {
    /** Whether [url]'s path and query may be read. */
    fun allows(url: String): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return false
        val target = (u.rawPath?.ifEmpty { "/" } ?: "/") + (u.rawQuery?.let { "?$it" } ?: "")
        var best: Pair<Boolean, Int>? = null
        for ((allow, pattern, len) in rules) {
            val m = pattern.find(target) ?: continue
            if (m.range.first != 0) continue
            if (best == null || len > best.second || (len == best.second && allow)) best = allow to len
        }
        return best?.first ?: true
    }

    companion object {
        /** Everything allowed (no robots.txt, or it could not be read as text). */
        val ALLOW_ALL = Robots(emptyList())

        /** Reads [text] for [productToken] (e.g. 「yorisoi」). */
        fun parse(text: String, productToken: String): Robots {
            val token = productToken.lowercase()
            val groups = ArrayList<Pair<MutableList<String>, MutableList<Pair<Boolean, String>>>>()
            var lastWasAgent = false
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val key = line.substringBefore(':').trim().lowercase()
                val value = line.substringAfter(':', "").trim()
                when (key) {
                    "user-agent" -> {
                        if (!lastWasAgent) groups += mutableListOf<String>() to mutableListOf()
                        groups.last().first += value.lowercase(); lastWasAgent = true
                    }
                    "allow", "disallow" -> {
                        lastWasAgent = false
                        if (groups.isEmpty()) continue
                        if (value.isEmpty()) continue // 「Disallow:」 with nothing allows everything
                        groups.last().second += (key == "allow") to value
                    }
                    else -> lastWasAgent = false
                }
            }
            val mine = groups.filter { g -> g.first.any { it != "*" && token.contains(it) } }
            val chosen = mine.ifEmpty { groups.filter { "*" in it.first } }
            val rules = chosen.flatMap { it.second }.map { (allow, path) -> Triple(allow, toRegex(path), path.length) }
            return Robots(rules)
        }

        private fun toRegex(path: String): Regex {
            val end = path.endsWith("$")
            val body = (if (end) path.dropLast(1) else path).split("*").joinToString(".*") { Regex.escape(it) }
            return Regex(if (end) "$body$" else body)
        }
    }
}
