package io.github.duwenji.yorisoi.core.format.material

import io.github.duwenji.yorisoi.core.format.JsonValue

/** What a studio announcement is about; its producer picks one, and a channel may show only some (API-MATERIALS「スタジオの分類」). */
enum class StudioCategory(override val json: String, val label: String) : JsonValue {
    SHRINE_TEMPLE("shrine-temple", "寺社・その行事"),
    EVENT("event", "催し"),
    FOOD("food", "飲食"),
    SHOPPING("shopping", "買い物"),
    SIGHTSEEING("sightseeing", "観光の案内"),
    NATURE("nature", "自然・季節"),
    CULTURE("culture", "展示・文化"),
    TRAFFIC("traffic", "交通・通行"),
    COMMUNITY("community", "地域のお知らせ"),
}

/**
 * The fixed words a channel may put before where people's own words come from (API-MATERIALS「前置き」,
 * ADR-MATERIALS D-49). The same in every app; never made by the AI.
 */
enum class Prelude(override val json: String, val spoken: String) : JsonValue {
    NONE("none", ""),
    BY_THE_WAY("by-the-way", "ところで、"),
    INCIDENTALLY("incidentally", "ちなみに、"),
    PAUSE("pause", "ちょっとひと息。"),
}

/**
 * What a cue tells a built-in producer (API-CHANNEL-PACKAGE `present[].params`, API-MATERIALS I-8). Only the
 * instructions with a rule for their values exist here; a producer accepts those it declares.
 *
 * @param categories the studio categories to show, or null for all. Empty when a channel named
 *   only categories this app doesn't know: then nothing matches (API-MATERIALS「スタジオの分類」).
 */
data class CueParams(val categories: List<StudioCategory>? = null, val prelude: Prelude? = null) {
    val isEmpty: Boolean get() = categories == null && prelude == null

    companion object {
        const val CATEGORIES = "categories"
        const val PRELUDE = "prelude"
        val NONE = CueParams()
    }
}
