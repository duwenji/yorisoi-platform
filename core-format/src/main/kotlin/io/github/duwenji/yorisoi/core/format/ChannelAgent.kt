package io.github.duwenji.yorisoi.core.format

import org.json.JSONObject

/** The scenes the preparer may look into (platform ADR-AGENT A-6; API-CHANNEL-PACKAGE「場面」). */
enum class AgentScene(override val json: String) : JsonValue {
    CALENDAR("calendar"), SEASON("season"), LIVELIHOOD("livelihood"), EVENTS("events"), SPOT("spot"),
}

/** The kinds of the ledger's sources (ADR-AGENT A-21), which `agent.skip` may name. */
enum class SourceKindName(override val json: String) : JsonValue {
    NATIONAL("national"), PREFECTURE("prefecture"), MUNICIPALITY("municipality"), TOURISM("tourism"), ASSOCIATION("association"),
}

/** A page of the channel's own the preparer may read (`agent.sources`, ADR-AGENT A-23). */
data class AgentSource(val name: String, val urls: List<String>, val scenes: Set<AgentScene>)

/**
 * `agent` of `channel.json` (platform ADR-AGENT A-6・A-8): whether the preparer is used, and within what. Left out,
 * the preparer isn't used ([OFF]). This is what the channel wrote; the app narrows it with the channel's other
 * values (`talk.events`, `talk.level`, A-7) when it prepares.
 *
 * @property paths the kinds of thread to aim for, each 2〜4 scenes (never [AgentScene.SPOT])
 * @property skipKinds the ledger's kinds not to use
 * @property skipDomains domains (and the ones below them) not to read
 * @property skipPrefixes URL prefixes not to read
 * @property noLedger an `agent.skip` kind this core-format doesn't know (A-8): the ledger isn't used at all
 */
data class ChannelAgent(
    val plan: Boolean,
    val web: Boolean = true,
    val scenes: Set<AgentScene> = AgentScene.entries.toSet(),
    val paths: List<List<AgentScene>> = emptyList(),
    val sources: List<AgentSource> = emptyList(),
    val skipKinds: Set<SourceKindName> = emptySet(),
    val skipDomains: List<String> = emptyList(),
    val skipPrefixes: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val noLedger: Boolean = false,
) {
    companion object {
        val OFF = ChannelAgent(plan = false)
        const val MAX_PATHS = 5
        const val MAX_SOURCES = 10
        const val MAX_SKIP = 20
        const val MAX_TOPICS = 10
        private val DOMAIN = Regex("^[a-z0-9.-]+\\.[a-z]{2,}$")
        private val PREFIX = Regex("^https?://[^/\\s]+\\S*$")
        private val HTTPS = Regex("^https://[^/\\s]+\\S*$")

        /**
         * Reads `agent` ([json] is `channel.json`). With [lenient] (an app), values it doesn't know are dropped as
         * A-8 says, and nothing loosens; otherwise they're turned away as `bad_value`.
         */
        internal fun read(json: JSONObject, lenient: Boolean): ChannelAgent {
            val raw = json.optNullable("agent") ?: return OFF
            val a = raw as? JSONObject ?: reject(RejectCode.BAD_MANIFEST, "agent is not an object")
            fun bad(detail: String): Nothing = reject(RejectCode.BAD_VALUE, "agent.$detail")
            fun onOff(key: String, default: Boolean, on: String): Boolean {
                val v = a.optString(key, RejectCode.BAD_MANIFEST) ?: return default
                return when (v) {
                    on -> true
                    "off" -> false
                    else -> if (lenient) false else bad("$key \"$v\"")
                }
            }
            val plan = onOff("mode", default = false, on = "plan")
            val web = onOff("web", default = true, on = "on")

            fun scenesOf(field: String, values: List<String>): Set<AgentScene> {
                if (values.toSet().size != values.size) bad("$field has duplicates")
                return values.mapNotNull { v -> parseEnum<AgentScene>(v) ?: if (lenient) null else bad("$field \"$v\"") }.toSet()
            }
            val scenes = if (a.optNullable("scenes") == null) AgentScene.entries.toSet()
            else scenesOf("scenes", a.optList("scenes").strings("agent.scenes", RejectCode.BAD_MANIFEST))

            val writtenPaths = a.optList("paths").strings("agent.paths", RejectCode.BAD_MANIFEST)
            if (writtenPaths.size > MAX_PATHS) bad("paths has ${writtenPaths.size} (max $MAX_PATHS)")
            val paths = writtenPaths.mapNotNull { p ->
                val parts = p.split('>')
                val known = parts.map { parseEnum<AgentScene>(it) }
                when {
                    known.any { it == null } -> if (lenient) null else bad("paths \"$p\": an unknown scene")
                    parts.size !in 2..4 -> bad("paths \"$p\": 2 to 4 scenes")
                    AgentScene.SPOT in known -> bad("paths \"$p\": spot is not in a thread")
                    known.any { it !in scenes } -> if (lenient) null else bad("paths \"$p\": a scene not in scenes")
                    else -> known.filterNotNull()
                }
            }

            val writtenSources = a.optList("sources").objects("agent.sources", RejectCode.BAD_MANIFEST)
            if (writtenSources.size > MAX_SOURCES) bad("sources has ${writtenSources.size} (max $MAX_SOURCES)")
            val sources = writtenSources.mapIndexed { i, s ->
                val name = s.string("name").also { checkLength("agent.sources[$i].name", it, 1, 20) }
                val urls = s.list("urls").strings("agent.sources[$i].urls", RejectCode.BAD_MANIFEST)
                if (urls.size !in 1..3) bad("sources[$i].urls has ${urls.size} (1 to 3)")
                urls.firstOrNull { !HTTPS.matches(it) || it.length > 500 }?.let { bad("sources[$i].urls \"$it\"") }
                val sourceScenes = scenesOf("sources[$i].scenes", s.list("scenes").strings("agent.sources[$i].scenes", RejectCode.BAD_MANIFEST))
                if (sourceScenes.isEmpty() && !lenient) bad("sources[$i].scenes is empty")
                AgentSource(name, urls, sourceScenes)
            }

            val skip = a.optList("skip").strings("agent.skip", RejectCode.BAD_MANIFEST)
            if (skip.size > MAX_SKIP) bad("skip has ${skip.size} (max $MAX_SKIP)")
            val kinds = mutableSetOf<SourceKindName>()
            val domains = mutableListOf<String>()
            val prefixes = mutableListOf<String>()
            var noLedger = false
            for (v in skip) {
                when {
                    v.contains("://") -> if (PREFIX.matches(v) && v.length <= 500) prefixes += v else bad("skip \"$v\"")
                    v.contains('.') -> if (DOMAIN.matches(v)) domains += v else bad("skip \"$v\"")
                    else -> parseEnum<SourceKindName>(v)?.let { kinds += it } ?: if (lenient) noLedger = true else bad("skip \"$v\"")
                }
            }

            val topics = a.optList("topics").strings("agent.topics", RejectCode.BAD_MANIFEST)
            if (topics.size > MAX_TOPICS) bad("topics has ${topics.size} (max $MAX_TOPICS)")
            topics.forEachIndexed { i, t ->
                checkLength("agent.topics[$i]", t, 1, 20)
                if ("{{" in t || findUrl(t) != null) bad("topics[$i] has a URL or \"{{\"")
            }
            return ChannelAgent(plan, web, scenes, paths, sources, kinds, domains, prefixes, topics, noLedger)
        }
    }
}
