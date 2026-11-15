package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

/** How sure a kind of fact is, which sets how it may be told (REQ-KOYOMI D-7, F-17). */
enum class Certainty {
    /** By astronomy or by law: told as a fact. */
    FIXED,

    /** Said of old: told as what has been said. */
    TRADITION,

    /** Set by someone: told with who set it. */
    DESIGNATED,

    /** Kept in some places: told with where. */
    REGIONAL,
}

/** The kinds of the calendar's facts, in `channel.json` spelling (REQ-KOYOMI「暦の種類」). */
enum class KoyomiKind(val json: String, val certainty: Certainty) {
    SEKKI("sekki", Certainty.FIXED),
    KOU("kou", Certainty.TRADITION),
    ZASSETSU("zassetsu", Certainty.TRADITION),
    SEKKU("sekku", Certainty.TRADITION),
    LUNAR("lunar", Certainty.TRADITION),
    HOLIDAY("holiday", Certainty.FIXED),
    MOON("moon", Certainty.FIXED),
    ROKUYOU("rokuyou", Certainty.TRADITION),
    KINENBI("kinenbi", Certainty.DESIGNATED),
    REGIONAL("regional", Certainty.REGIONAL),
    ;

    companion object {
        fun of(json: String): KoyomiKind? = entries.firstOrNull { it.json == json }
    }
}

/** A day, or days from [start] to [end], the calendar has something to say of. */
internal data class Event(
    val kind: KoyomiKind,
    val key: String,
    val name: String,
    val start: LocalDate,
    val end: LocalDate = start,
    val note: String,
    val reading: String? = null,
    val designatedBy: String? = null,
    val regions: String? = null,
) {
    /** Kind, key and first day: one per occurrence (REQ-KOYOMI F-2). */
    val id: String get() = "${kind.json}:$key:$start"
}
