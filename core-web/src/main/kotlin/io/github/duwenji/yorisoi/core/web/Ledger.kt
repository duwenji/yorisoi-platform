package io.github.duwenji.yorisoi.core.web

import org.json.JSONObject

/** The kinds of a source (ADR-AGENT A-21・A-23). The first five are the ledger's; [CHANNEL] is a channel's own. */
enum class SourceKind(val id: String, val official: Boolean) {
    NATIONAL("national", true),
    PREFECTURE("prefecture", true),
    MUNICIPALITY("municipality", true),
    TOURISM("tourism", false),
    ASSOCIATION("association", false),
    CHANNEL("channel", false),
    ;

    companion object {
        fun of(id: String): SourceKind? = entries.firstOrNull { it.id == id }
    }
}

/** What a source offers to read: a feed (RSS・Atom) or a list page (ADR-AGENT A-21). */
data class SourceRead(val url: String, val feed: Boolean, val title: String? = null)

/** One source a preparation may read (ADR-AGENT A-21〜A-23). */
data class Source(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val municipality: String?,
    val reads: List<SourceRead>,
    val scenes: Set<String>,
    val readable: Boolean = true,
) {
    /** Whether the ledger put this source in (true) or a channel did (false). */
    val fromLedger: Boolean get() = kind != SourceKind.CHANNEL
}

/** One prefecture's (or the nation's) ledger file (docs/contracts/api/web-sources.schema.json). */
data class Ledger(val version: Int, val prefecture: String, val sources: List<Source>) {
    /** The readable sources for [municipality] (its own rows and the prefecture- and nation-wide ones) that serve [scenes]. */
    fun forPlace(municipality: String?, scenes: Set<String>): List<Source> = sources.filter { s ->
        s.readable && s.scenes.any { it in scenes } && (s.municipality == null || s.municipality == municipality)
    }

    companion object {
        /** Reads a ledger file; rows with values this version doesn't know are left out. */
        fun parse(json: String): Ledger {
            val o = JSONObject(json)
            val rows = o.getJSONArray("sources")
            val sources = (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                val kind = SourceKind.of(r.getString("kind"))?.takeIf { it != SourceKind.CHANNEL } ?: return@mapNotNull null
                val scope = r.getJSONObject("scope")
                val reads = r.getJSONArray("reads").let { a ->
                    (0 until a.length()).map { j ->
                        val x = a.getJSONObject(j)
                        SourceRead(x.getString("url"), x.getString("form") == "feed", x.optString("title").ifEmpty { null })
                    }
                }
                val scenes = r.getJSONArray("scenes").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                Source(
                    id = r.getString("id"),
                    name = r.getString("name"),
                    kind = kind,
                    municipality = scope.optString("code").ifEmpty { null },
                    reads = reads,
                    scenes = scenes,
                    readable = r.optString("status", "ok") == "ok",
                )
            }
            return Ledger(o.getInt("version"), o.getString("prefecture"), sources)
        }
    }
}
