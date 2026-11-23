package io.github.duwenji.yorisoi.core.agent

import org.json.JSONObject

/** One tool the preparer may ask for (docs/contracts/api/agent-tools.v1.json). */
data class ToolDef(val name: String, val description: String, val inputSchema: JSONObject)

/**
 * The tools' definitions, read from the shared file packed with this module (AGENT A-11・A-45). The relay
 * keeps a copy of the same file; the app sends it only the version and the names.
 */
class AgentTools private constructor(val version: Int, private val byName: Map<String, ToolDef>) {
    /** The tools the preparer may ask for while it looks things up (everything but the submission). */
    val lookups: List<ToolDef> get() = byName.values.filter { it.name != SUBMIT }

    /** The submission tool, the only one offered on the last turn (A-15). */
    val submit: ToolDef get() = byName.getValue(SUBMIT)

    operator fun get(name: String): ToolDef? = byName[name]

    /** What is wrong with [input] for the tool [name] (an unknown tool is wrong too); empty when it may run. */
    fun argumentErrors(name: String, input: JSONObject): List<String> {
        val t = byName[name] ?: return listOf("unknown tool $name")
        return SchemaCheck.errors(input, t.inputSchema)
    }

    companion object {
        const val SUBMIT = "submit_threads"

        /** The definitions of [version], packed with this module. */
        fun load(version: Int = 1): AgentTools {
            val text = AgentTools::class.java.getResourceAsStream("agent-tools.v$version.json")
                ?.bufferedReader(Charsets.UTF_8)?.readText()
                ?: error("agent-tools.v$version.json is not packed")
            return parse(text)
        }

        fun parse(json: String): AgentTools {
            val o = JSONObject(json)
            val arr = o.getJSONArray("tools")
            val defs = (0 until arr.length()).map { i ->
                val t = arr.getJSONObject(i)
                ToolDef(t.getString("name"), t.getString("description"), t.getJSONObject("inputSchema"))
            }
            return AgentTools(o.getInt("version"), defs.associateBy { it.name })
        }
    }
}
