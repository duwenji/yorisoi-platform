package io.github.duwenji.yorisoi.core.format.material

/** The kinds of material (API-MATERIALS「素材」, API-CHANNEL-PACKAGE `resources[].type`). */
enum class MaterialType(val json: String) { TEXT("text"), IMAGE("image"), AUDIO("audio"), VIDEO("video") }

/** What a producer can be asked about: a spot, a point, an event of the walk, or nothing in particular. */
enum class SubjectKind(val json: String) { SPOT("spot"), POINT("point"), EVENT("event"), NONE("none") }

/** Who answers for a producer: the app's own code, the operator's review, or a test ticket (before review). */
enum class Trust(val json: String) { BUILTIN("builtin"), REVIEWED("reviewed"), TRIAL("trial") }

/**
 * When a producer gets its material: made on the phone, fetched when the channel is chosen, when
 * shown, or by area while walking or riding (lists of the cells around, refreshed on moving to another
 * cell and every 5 minutes; API-MATERIALS `fetch=area`).
 */
enum class Fetch(val json: String) { LOCAL("local"), PREFETCH("prefetch"), ON_DEMAND("onDemand"), AREA("area") }

/**
 * Whether producing material tells an outside service what it's about (for example, which spot), or,
 * for [Fetch.AREA], which cell (geohash6) the phone is in when fetching.
 */
enum class Exposure(val json: String) { NONE("none"), SUBJECT("subject"), AREA("area") }

/**
 * What a producer makes and how (API-MATERIALS「素材提供者の記述」). Every producer — built in, a third
 * party's collection, or a channel's own resources — is described the same way; what the app
 * allows follows from these values ([ProducerPolicy]), not from what kind of producer it is.
 *
 * @param params the instructions a channel may give it (API-MATERIALS I-8).
 * @param verbatim people's own words, read out as they are (API-MATERIALS P-8, ADR-MATERIALS D-38): never the
 *   AI's grounding, never reworded, introduced by where they come from.
 */
data class ProducerDescriptor(
    val id: String,
    val version: Int,
    val types: Set<MaterialType>,
    val subjects: Set<SubjectKind>,
    val trust: Trust,
    val fetch: Fetch,
    val exposure: Exposure,
    val generated: Boolean,
    val params: Set<String> = emptySet(),
    val verbatim: Boolean = false,
)

/** The app's rules for producers by their description (API-MATERIALS 方針 P-1・P-2・P-7・P-8). Channels and producers can't change them. */
object ProducerPolicy {
    /** The rule [descriptor] breaks, or null when it may be used. */
    fun violation(descriptor: ProducerDescriptor): String? = with(descriptor) {
        when {
            // P-1: only the app's own code may fetch when shown, reveal the subject, or generate.
            trust != Trust.BUILTIN && fetch != Fetch.PREFETCH -> "P-1: a ${trust.json} producer must prefetch ($id)"
            trust != Trust.BUILTIN && exposure != Exposure.NONE -> "P-1: a ${trust.json} producer must not expose the subject ($id)"
            trust != Trust.BUILTIN && generated -> "P-1: a ${trust.json} producer must not generate ($id)"
            // P-8: people's own words are never generated.
            verbatim && generated -> "P-8: a verbatim producer must not generate ($id)"
            // P-2 and P-7 follow: fetching on demand or by area, and revealing the subject or the cell, are left to built-in producers.
            else -> null
        }
    }
}

/**
 * The producers built into the app (API-MATERIALS「組み込みの素材提供者」). Adding one takes an app update
 * (ADR-002 D-6) and a new row in that table. The management system's checks read this list to
 * know which `builtin:` references a channel may use.
 */
object BuiltInProducers {
    const val PREFIX = "builtin:"

    /** Bumped when a built-in producer changes what it makes. */
    private const val VERSION = 1

    /** Spot narration by the AI (`GuideRepository`). */
    val AI_GUIDE = ProducerDescriptor(
        "${PREFIX}ai-guide", VERSION, setOf(MaterialType.TEXT), setOf(SubjectKind.SPOT),
        Trust.BUILTIN, Fetch.ON_DEMAND, Exposure.SUBJECT, generated = true, params = setOf("persona", "focus", "length"),
    )

    /** The walking companion's lines by the AI (`WalkCompanion`). */
    val AI_COMPANION = ProducerDescriptor(
        "${PREFIX}ai-companion", VERSION, setOf(MaterialType.TEXT), setOf(SubjectKind.EVENT),
        Trust.BUILTIN, Fetch.ON_DEMAND, Exposure.SUBJECT, generated = true, params = setOf("persona", "topics", "events", "tone"),
    )

    /** The fixed lines used without the AI (`prompts/fallback/`). */
    val FALLBACK_TEXT = ProducerDescriptor(
        "${PREFIX}fallback-text", VERSION, setOf(MaterialType.TEXT), setOf(SubjectKind.SPOT, SubjectKind.EVENT),
        Trust.BUILTIN, Fetch.LOCAL, Exposure.NONE, generated = false,
    )

    /** Spot photos from Wikimedia Commons (`SpotPhotos`). */
    val COMMONS_PHOTO = ProducerDescriptor(
        "${PREFIX}commons-photo", VERSION, setOf(MaterialType.IMAGE), setOf(SubjectKind.SPOT),
        Trust.BUILTIN, Fetch.ON_DEMAND, Exposure.SUBJECT, generated = false,
    )

    /** Background sounds synthesized on the phone (`Soundscape`). */
    val AMBIENT = ProducerDescriptor(
        "${PREFIX}ambient", VERSION, setOf(MaterialType.AUDIO), setOf(SubjectKind.NONE),
        Trust.BUILTIN, Fetch.LOCAL, Exposure.NONE, generated = false, params = setOf("sound"),
    )

    /** The mood picture drawn by the app (`MoodScene`). */
    val MOOD_SCENE = ProducerDescriptor(
        "${PREFIX}mood-scene", VERSION, setOf(MaterialType.IMAGE), setOf(SubjectKind.NONE),
        Trust.BUILTIN, Fetch.LOCAL, Exposure.NONE, generated = false,
    )

    /** Shops' and places' announcements by producers, fetched by area (kit-studio; ADR-MATERIALS D-39・D-44). */
    val STUDIO = ProducerDescriptor(
        "${PREFIX}studio", VERSION, setOf(MaterialType.TEXT), setOf(SubjectKind.EVENT, SubjectKind.SPOT, SubjectKind.POINT),
        Trust.BUILTIN, Fetch.AREA, Exposure.AREA, generated = false, params = setOf(CueParams.CATEGORIES, CueParams.PRELUDE), verbatim = true,
    )

    /** Notes users leave at a place, fetched by area (kit-studio; ADR-MATERIALS D-41・D-44). */
    val NOTE = ProducerDescriptor(
        "${PREFIX}note", VERSION, setOf(MaterialType.TEXT), setOf(SubjectKind.EVENT, SubjectKind.SPOT, SubjectKind.POINT),
        Trust.BUILTIN, Fetch.AREA, Exposure.AREA, generated = false, params = setOf(CueParams.PRELUDE), verbatim = true,
    )

    val all: List<ProducerDescriptor> = listOf(AI_GUIDE, AI_COMPANION, FALLBACK_TEXT, COMMONS_PHOTO, AMBIENT, MOOD_SCENE, STUDIO, NOTE)

    /**
     * Producers a channel gets in a shorthand cue unless it names them in its cues or turns them off
     * in `shorthands` (API-MATERIALS「省略形の展開」, ADR-MATERIALS D-53・D-54).
     */
    val withShorthand: List<ProducerDescriptor> = listOf(STUDIO, NOTE)

    fun byId(id: String): ProducerDescriptor? = all.firstOrNull { it.id == id }
}
