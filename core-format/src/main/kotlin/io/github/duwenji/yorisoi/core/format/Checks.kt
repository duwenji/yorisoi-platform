package io.github.duwenji.yorisoi.core.format

import io.github.duwenji.yorisoi.core.text.AppLanguage
import io.github.duwenji.yorisoi.core.text.WrittenText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// The pieces every package check shares (channels, API-CHANNEL-PACKAGE; collections, API-MATERIALS): turning a broken
// rule into a rejection, and reading JSON fields strictly.

/** A broken rule, caught at the top of a check and returned as its result. */
internal open class Reject(val code: RejectCode, detail: String) : Exception(detail)

/**
 * A value outside the ecosystem's lists in a cue (API-CHANNEL-PACKAGE「アプリでの知らない値」): rejected in
 * review, but an app reading a package leaves out the cue or presentation that holds it.
 */
internal class UnknownValue(code: RejectCode, detail: String) : Reject(code, detail)

internal fun unknownValue(code: RejectCode, detail: String): Nothing = throw UnknownValue(code, detail)

internal fun reject(code: RejectCode, detail: String): Nothing = throw Reject(code, detail)

internal val ID_PATTERN = Regex("[a-z0-9-]{3,40}")
internal val ACCOUNT_ID_PATTERN = Regex("sg1[a-z2-7]{32}")
// The text checks every path shares live in core-text (AGENT A-52).
internal val HEADING_PATTERN = WrittenText.HEADING
/** The first URL in [text], full-width forms included (core-text WrittenText), or null. */
internal fun findUrl(text: String): String? = WrittenText.findUrl(text)

/** Strict UTF-8, or a rejection with [code]. */
internal fun decodeUtf8(bytes: ByteArray, path: String, code: RejectCode): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: CharacterCodingException) {
    reject(code, "$path is not UTF-8")
}

/** A JSON object read from [path] of [files], or a rejection with [code]. */
internal fun readJson(files: StationFiles, path: String, code: RejectCode): JSONObject {
    val bytes = files.read(path) ?: reject(code, "no $path")
    return try {
        JSONObject(decodeUtf8(bytes, path, code))
    } catch (e: JSONException) {
        reject(code, "$path: ${e.message}")
    }
}

/** Characters as a reader counts them (one per code point, so emoji and rare kanji count once). */
internal fun String.charCount(): Int = codePointCount(0, length)

internal fun checkLength(field: String, value: String, min: Int, max: Int, code: RejectCode = RejectCode.BAD_VALUE) {
    val chars = value.charCount()
    if (chars < min || chars > max) reject(code, "$field has $chars chars ($min..$max)")
}

// Field readers. A missing or mistyped field is [code] (by default `bad_manifest`); a value outside
// what the format allows is `bad_value` or the caller's own code.

internal fun JSONObject.optNullable(key: String): Any? = opt(key)?.takeIf { it != JSONObject.NULL }

internal fun JSONObject.required(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): Any =
    optNullable(key) ?: reject(code, "no \"$key\"")

internal fun JSONObject.string(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): String =
    required(key, code) as? String ?: reject(code, "\"$key\" is not a string")

internal fun JSONObject.optString(key: String, code: RejectCode): String? =
    optNullable(key)?.let { it as? String ?: reject(code, "\"$key\" is not a string") }

internal fun JSONObject.int(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): Int =
    required(key, code) as? Int ?: reject(code, "\"$key\" is not an integer")

internal fun JSONObject.optInt(key: String, code: RejectCode): Int? =
    optNullable(key)?.let { it as? Int ?: reject(code, "\"$key\" is not an integer") }

/** A number that may have a fraction (coordinates). */
internal fun JSONObject.double(key: String, code: RejectCode): Double =
    (required(key, code) as? Number)?.toDouble() ?: reject(code, "\"$key\" is not a number")

internal fun JSONObject.bool(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): Boolean =
    required(key, code) as? Boolean ?: reject(code, "\"$key\" is not true or false")

internal fun JSONObject.obj(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): JSONObject =
    required(key, code) as? JSONObject ?: reject(code, "\"$key\" is not an object")

/** A list that may be left out (empty then). */
internal fun JSONObject.optList(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): List<Any> {
    val value = optNullable(key) ?: return emptyList()
    val array = value as? JSONArray ?: reject(code, "\"$key\" is not a list")
    return (0 until array.length()).map { array.opt(it) ?: JSONObject.NULL }
}

internal fun JSONObject.list(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): List<Any> {
    required(key, code) as? JSONArray ?: reject(code, "\"$key\" is not a list")
    return optList(key, code)
}

internal fun List<Any>.objects(field: String, code: RejectCode): List<JSONObject> =
    map { it as? JSONObject ?: reject(code, "$field has an item that is not an object") }

internal fun List<Any>.strings(field: String, code: RejectCode): List<String> =
    map { it as? String ?: reject(code, "$field has an item that is not a string") }

internal inline fun <reified E> JSONObject.enum(key: String, code: RejectCode = RejectCode.BAD_MANIFEST): E where E : Enum<E>, E : JsonValue {
    val value = string(key, code)
    return parseEnum<E>(value) ?: reject(if (code == RejectCode.BAD_MANIFEST) RejectCode.BAD_VALUE else code, "$key \"$value\"")
}

/**
 * The languages a package offers (REQ-LANGUAGES F-16・F-20): `langs`, 1–4 of the four, [lang] first; `[lang]`
 * when left out.
 */
internal fun JSONObject.readLangs(lang: String): List<String> {
    val langs = optNullable("langs")?.let { list("langs").strings("langs", RejectCode.BAD_MANIFEST).distinctOrReject("langs") } ?: listOf(lang)
    if (langs.size !in 1..AppLanguage.entries.size) reject(RejectCode.BAD_VALUE, "langs has ${langs.size} values (1..${AppLanguage.entries.size})")
    if (langs.first() != lang) reject(RejectCode.BAD_VALUE, "langs must start with lang \"$lang\"")
    langs.firstOrNull { t -> AppLanguage.entries.none { it.tag == t } }?.let { reject(RejectCode.BAD_VALUE, "langs \"$it\"") }
    return langs
}

/**
 * A text written once, or per language (REQ-LANGUAGES F-17): a string is the only language's; an object
 * gives one for each of [langs], no more and no fewer. [maxJapanese] is the limit in Japanese characters,
 * made a limit as long to read out in each language (D-6).
 */
internal fun JSONObject.langTexts(key: String, langs: List<String>, maxJapanese: Int, code: RejectCode = RejectCode.BAD_VALUE): Map<String, String> {
    val value = required(key, RejectCode.BAD_MANIFEST)
    return langValue(key, value, langs, maxJapanese, code, all = true)
}

/**
 * [value] as a text per language: a string is [langs]' first; an object gives texts for some of [langs]
 * ([all]: each of them), [langs]' first among them.
 */
internal fun langValue(key: String, value: Any, langs: List<String>, maxJapanese: Int, code: RejectCode, all: Boolean, min: Int = 1): Map<String, String> {
    fun limit(tag: String) = AppLanguage.of(tag)?.fromJapanese(maxJapanese) ?: maxJapanese
    val texts = when (value) {
        is String -> {
            if (all && langs.size > 1) reject(code, "$key must be given for each of langs")
            mapOf(langs.first() to value)
        }
        is JSONObject -> {
            val keys = value.keySet()
            if (all && keys != langs.toSet()) reject(code, "$key has ${keys.sorted()}, not langs $langs")
            if (!all && (langs.first() !in keys || !langs.containsAll(keys))) reject(code, "$key has ${keys.sorted()}: ${langs.first()} and only langs $langs")
            langs.filter { it in keys }.associateWith { value.optString(it).takeIf { _ -> value.opt(it) is String } ?: reject(code, "$key.$it is not a string") }
        }
        else -> reject(code, "\"$key\" is not a string or an object")
    }
    texts.forEach { (tag, text) -> checkLength(if (texts.size > 1) "$key.$tag" else key, text, min, limit(tag), code) }
    return texts
}

internal fun List<String>.distinctOrReject(field: String): List<String> =
    also { if (toSet().size != size) reject(RejectCode.BAD_VALUE, "$field has duplicates") }
