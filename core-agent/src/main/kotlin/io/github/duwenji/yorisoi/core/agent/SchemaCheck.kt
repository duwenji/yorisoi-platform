package io.github.duwenji.yorisoi.core.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * The part of JSON Schema the tools' definitions use (ADR-AGENT A-13・A-15): `type` (object, array,
 * string, integer, boolean), `properties`, `required`, `additionalProperties: false`, `items`,
 * `minItems`・`maxItems`, `maxLength`, `pattern`, `enum` and `oneOf`. An AI's arguments are checked against it
 * before a tool runs or a submission is taken; nothing outside this subset is relied on.
 */
object SchemaCheck {
    /** What is wrong with [value] for [schema], one line each; empty when it fits. */
    fun errors(value: Any?, schema: JSONObject, path: String = "$"): List<String> {
        val out = ArrayList<String>()
        check(value, schema, path, out)
        return out
    }

    private fun check(v: Any?, s: JSONObject, path: String, out: MutableList<String>) {
        if (s.has("oneOf")) {
            val alts = s.getJSONArray("oneOf")
            val fits = (0 until alts.length()).count { errors(v, alts.getJSONObject(it), path).isEmpty() }
            if (fits != 1) out += "$path: fits $fits of the alternatives"
            return
        }
        s.optJSONArray("enum")?.let { e -> if ((0 until e.length()).none { e.get(it) == v }) out += "$path: not one of the values" }
        when (s.optString("type")) {
            "object" -> {
                if (v !is JSONObject) { out += "$path: not an object"; return }
                val props = s.optJSONObject("properties") ?: JSONObject()
                s.optJSONArray("required")?.let { r -> for (i in 0 until r.length()) if (!v.has(r.getString(i))) out += "$path: no ${r.getString(i)}" }
                for (k in v.keySet()) {
                    val p = props.optJSONObject(k)
                    if (p == null) { if (s.optBoolean("additionalProperties", true).not()) out += "$path: unknown $k" } else check(v.get(k), p, "$path.$k", out)
                }
            }
            "array" -> {
                if (v !is JSONArray) { out += "$path: not an array"; return }
                if (s.has("minItems") && v.length() < s.getInt("minItems")) out += "$path: fewer than ${s.getInt("minItems")}"
                if (s.has("maxItems") && v.length() > s.getInt("maxItems")) out += "$path: more than ${s.getInt("maxItems")}"
                s.optJSONObject("items")?.let { item -> for (i in 0 until v.length()) check(v.get(i), item, "$path[$i]", out) }
            }
            "string" -> {
                if (v !is String) { out += "$path: not a string"; return }
                if (s.has("maxLength") && v.codePointCount(0, v.length) > s.getInt("maxLength")) out += "$path: longer than ${s.getInt("maxLength")}"
                if (s.has("pattern") && !Regex(s.getString("pattern")).containsMatchIn(v)) out += "$path: does not match"
            }
            "integer" -> if (v !is Int && v !is Long) out += "$path: not an integer"
            "boolean" -> if (v !is Boolean) out += "$path: not a boolean"
        }
    }
}
