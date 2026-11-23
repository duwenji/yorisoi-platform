package io.github.duwenji.yorisoi.core.web

/**
 * The registrable domain of a host, from the Public Suffix List (bundled; MPL 2.0), so a link stays on
 * the site it came from (AGENT A-17). Two rules are added for Japan's local governments: every
 * `<prefecture>.lg.jp` is a suffix, so 福井県 (pref.fukui.lg.jp) and 福井市 (city.fukui.lg.jp) are
 * different sites; the list itself only has `lg.jp`.
 */
object PublicSuffix {
    private val EXTRA = listOf("*.lg.jp")

    private class Rules(val exact: Set<String>, val wildcard: Set<String>, val exception: Set<String>, val private: Set<String>)

    private val rules: Rules by lazy { load() }

    private fun load(): Rules {
        val text = PublicSuffix::class.java.getResourceAsStream("public_suffix_list.dat")!!.bufferedReader(Charsets.UTF_8).readText()
        val exact = HashSet<String>(); val wildcard = HashSet<String>(); val exception = HashSet<String>(); val private = HashSet<String>()
        var inPrivate = false
        fun add(raw: String, isPrivate: Boolean) {
            val rule = raw.trim().lowercase()
            when {
                rule.startsWith("!") -> exception += rule.substring(1)
                rule.startsWith("*.") -> wildcard += rule.substring(2).also { if (isPrivate) private += "*.$it" }
                else -> exact += rule.also { if (isPrivate) private += it }
            }
        }
        for (line in text.lineSequence()) {
            if (line.contains("===BEGIN PRIVATE DOMAINS===")) inPrivate = true
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("//")) continue
            add(l.split(' ', '\t')[0], inPrivate)
        }
        EXTRA.forEach { add(it, false) }
        return Rules(exact, wildcard, exception, private)
    }

    /** The public suffix of [host] (the longest matching rule; a bare TLD when no rule matches). */
    fun suffix(host: String): String {
        val labels = host.trimEnd('.').lowercase().split('.')
        var best = labels.last()
        for (i in labels.indices) {
            val name = labels.subList(i, labels.size).joinToString(".")
            if (name in rules.exception) return labels.subList(i + 1, labels.size).joinToString(".")
            val parent = labels.subList(i + 1, labels.size).joinToString(".")
            if (name in rules.exact || (parent.isNotEmpty() && parent in rules.wildcard)) {
                if (name.length > best.length) best = name
            }
        }
        return best
    }

    /** [host]'s registrable domain (its public suffix and one more label), or null when the host is a suffix itself. */
    fun registrableDomain(host: String): String? {
        val h = host.trimEnd('.').lowercase()
        val s = suffix(h)
        if (h == s) return null
        val rest = h.removeSuffix(".$s")
        return rest.substringAfterLast('.') + "." + s
    }

    /**
     * Whether [host] sits on a shared service (its suffix comes from the list's private section, e.g. a blog
     * or a site builder), where one registrable domain belongs to one user only.
     */
    fun isSharedService(host: String): Boolean {
        val s = suffix(host)
        val parent = s.substringAfter('.', "")
        return s in rules.private || (parent.isNotEmpty() && "*.$parent" in rules.private)
    }
}
