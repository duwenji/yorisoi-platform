package io.github.duwenji.yorisoi.core.format

import io.github.duwenji.yorisoi.core.format.material.MaterialType
import io.github.duwenji.yorisoi.core.format.material.Prelude
import io.github.duwenji.yorisoi.core.format.material.StudioCategory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** API-CHANNEL-PACKAGE 素材・きっかけ (手順 8・9・10, 確認観点 V-10・V-15・V-18・V-19) and 1.2's collections and selectors. */
class StationMediaTest {
    private fun manifest(change: JSONObject.() -> Unit = {}) = manifestJson(publisher = null).apply(change)

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    private val credit = JSONObject().put("author", "鎌倉歴史散歩の会").put("license", "CC-BY-4.0")

    private fun intro() = JSONObject().put("id", "intro").put("type", "text").put("file", "resources/text/intro.md")
    private fun photo() = JSONObject().put("id", "old-photo").put("type", "image").put("file", "resources/img/old.png")
        .put("alt", "明治時代の本宮").put("credit", credit)
    private fun narration() = JSONObject().put("id", "narration").put("type", "audio")
        .put("url", "https://cdn.example.com/n.ogg").put("sha256", "a".repeat(64)).put("size", 2_480_000)
        .put("durationSec", 154).put("mime", "audio/ogg").put("transcript", "intro").put("credit", credit)

    private fun cue(id: String, trigger: JSONObject, vararg present: JSONObject) =
        JSONObject().put("id", id).put("trigger", trigger).put("present", JSONArray(present.toList()))

    private fun spot(osm: String = "way/123456") = JSONObject().put("type", "spot").put("osm", osm)
    private fun show(asWhat: String, key: String, value: Any) = JSONObject().put("as", asWhat).put(key, value)

    /** A channel with text and an image, a collection, a selector, and cues using all of them. */
    private fun full(change: JSONObject.() -> Unit = {}) = manifest {
        put("resources", JSONArray(listOf(intro(), photo())))
        put("collections", JSONArray().put(JSONObject().put("as", "kamakura").put("id", "kamakura-audio-guide").put("minVersion", 2)))
        put("selectors", JSONArray().put(JSONObject().put("as", "history-text")
            .put("match", JSONObject().put("type", "text").put("tags", JSONArray(listOf("history", "faith"))))))
        put("cues", JSONArray(listOf(
            cue("hachimangu", spot(), show("speak", "resource", "intro"), show("card", "source", JSONArray(listOf("kamakura#old", "self#old-photo")))),
            cue("temples", JSONObject().put("type", "spot").put("any", true).put("categories", JSONArray(listOf("temple", "shrine"))),
                show("speak", "source", JSONArray(listOf("history-text", "kamakura", "builtin:ai-guide")))),
            cue("welcome", JSONObject().put("type", "event").put("event", "select"),
                show("sheet", "sources", JSONArray(listOf("self#old-photo", JSONArray(listOf("kamakura#map", "builtin:commons-photo")))))),
        )))
        change()
    }

    private fun files(manifest: JSONObject, extra: Map<String, ByteArray> = emptyMap(), withMedia: Boolean = true) = MemoryStationFiles(
        packageFiles(manifest) +
            (if (withMedia) mapOf("resources/text/intro.md" to "鶴岡八幡宮は、源頼義が由比郷に祀ったのが始まりです。\n".toByteArray(), "resources/img/old.png" to png(40, 30)) else emptyMap()) +
            extra,
    )

    private fun check(files: StationFiles, stage: Int = StationValidator.ALL_STAGES) = StationValidator.check(files, StationOrigin.BUILT_IN, mediaStage = stage)

    private fun rejected(files: StationFiles, stage: Int = StationValidator.ALL_STAGES): RejectCode {
        val result = check(files, stage)
        assertTrue("expected a rejection, got $result", result is StationCheck.Rejected)
        return (result as StationCheck.Rejected).code
    }

    @Test
    fun `a channel using its own resources, a collection and a selector is read`() {
        val result = check(files(full()))
        assertTrue("$result", result is StationCheck.Ok)
        val media = (result as StationCheck.Ok).station.media
        assertEquals(listOf("intro", "old-photo"), media.resources.items.map { it.id })
        assertEquals("鶴岡八幡宮は、源頼義が由比郷に祀ったのが始まりです。", media.resources.texts["intro"])
        assertEquals(3, media.cues.size)
        assertEquals(Trigger.AnySpot(listOf(SpotKind.TEMPLE, SpotKind.SHRINE)), media.cues[1].trigger)
        assertEquals(
            listOf(Reference.Alias("history-text", null), Reference.Alias("kamakura", null), Reference.BuiltIn("builtin:ai-guide")),
            media.cues[1].present[0].items[0],
        )
        assertEquals(2, media.cues[2].present[0].items.size)
        assertEquals(1, MediaStage.required(media))
    }

    @Test
    fun `without a stage any material is turned away unread, as today`() {
        assertEquals(RejectCode.UNSUPPORTED_RESOURCE, rejected(files(full()), stage = 0))
        assertEquals(RejectCode.UNSUPPORTED_RESOURCE, rejected(files(manifest { put("selectors", JSONArray().put("x")) }, withMedia = false), stage = 0))
        // ... and files under resources/ aren't allowed either.
        assertEquals(RejectCode.UNEXPECTED_FILE, rejected(files(manifest()), stage = 0))
        assertTrue(check(files(manifest(), withMedia = false), stage = 0) is StationCheck.Ok)
    }

    @Test
    fun `audio needs stage 2 and video stage 3`() {
        val withAudio = full {
            put("resources", getJSONArray("resources").put(narration()))
            put("cues", getJSONArray("cues").put(cue("kirido", JSONObject().put("type", "point").put("lat", 35.3251).put("lon", 139.5562).put("radiusM", 40),
                show("play", "resource", "narration"))))
        }
        assertEquals(RejectCode.UNSUPPORTED_RESOURCE, rejected(files(withAudio), stage = 1))
        assertEquals(2, MediaStage.required((check(files(withAudio), stage = 2) as StationCheck.Ok).station.media))
    }

    @Test
    fun `resources keep to their rules`() {
        fun withResource(r: JSONObject) = files(full { put("resources", JSONArray(listOf(intro(), photo(), r))) })
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full { put("resources", JSONArray(listOf(intro(), photo().apply { remove("alt") }))) })))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full { put("resources", JSONArray(listOf(intro(), photo().apply { remove("credit") }))) })))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(narration().put("url", "http://cdn.example.com/n.ogg"))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(narration().apply { remove("transcript") })))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(narration().put("transcript", "old-photo"))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(narration().put("durationSec", 601))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(narration().put("mime", "audio/mpeg"))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(intro().put("id", "intro2").put("file", "resources/text/missing.md"))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(withResource(intro().put("id", "intro"))))
        // Text is read out or shown as is: no URLs or Markdown links.
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full(), mapOf("resources/text/intro.md" to "詳しくは https://example.com へ\n".toByteArray()))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full(), mapOf("resources/text/intro.md" to "[八幡宮](x)\n".toByteArray()))))
        // Images: a real JPEG, PNG or WebP, no side over 1,600px.
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full(), mapOf("resources/img/old.png" to png(1601, 10)))))
        assertEquals(RejectCode.BAD_RESOURCE, rejected(files(full(), mapOf("resources/img/old.png" to "not a picture".toByteArray()))))
        // A file nothing points at.
        assertEquals(RejectCode.UNEXPECTED_FILE, rejected(files(full(), mapOf("resources/img/spare.png" to png(4, 4)))))
    }

    @Test
    fun `cues show only what their presentation allows`() {
        fun withCue(c: JSONObject) = files(full { put("cues", getJSONArray("cues").put(c)) })
        assertEquals(RejectCode.BAD_CUE, rejected(withCue(cue("x", spot(), show("play", "resource", "intro")))))
        assertEquals(RejectCode.BAD_CUE, rejected(withCue(cue("x", spot(), show("fullscreen", "source", "builtin:ai-guide")))))
        assertEquals(RejectCode.BAD_CUE, rejected(withCue(cue("x", spot(), show("card", "resources", JSONArray().put("intro"))))))
        assertEquals(RejectCode.BAD_CUE, rejected(withCue(cue("x", spot()))))
        val long = "あ".repeat(201) + "\n"
        assertEquals(RejectCode.BAD_CUE, rejected(files(full { put("cues", getJSONArray("cues").put(cue("x", spot(), show("card", "resource", "intro")))) },
            mapOf("resources/text/intro.md" to long.toByteArray()))))
    }

    @Test
    fun `references must name something`() {
        fun withSource(source: Any) = files(full { put("cues", getJSONArray("cues").put(cue("x", spot(), show("speak", "source", source)))) })
        assertEquals(RejectCode.UNRESOLVED_REFERENCE, rejected(withSource("nowhere#intro")))
        assertEquals(RejectCode.UNRESOLVED_REFERENCE, rejected(withSource("builtin:unknown")))
        assertEquals(RejectCode.UNRESOLVED_REFERENCE, rejected(withSource("self#missing")))
        assertEquals(RejectCode.UNRESOLVED_REFERENCE, rejected(withSource("history-text#intro")))
        assertEquals(RejectCode.BAD_CUE, rejected(withSource(JSONArray(listOf("self#intro", "builtin:ai-guide", "builtin:fallback-text", "kamakura")))))
    }

    @Test
    fun `declarations and narration follow API-CHANNEL-PACKAGE 1_2`() {
        // Declared but never used.
        val unused = full { put("selectors", getJSONArray("selectors").put(JSONObject().put("as", "spare")
            .put("match", JSONObject().put("type", "audio").put("tags", JSONArray().put("nature"))))) }
        assertEquals(RejectCode.BAD_CUE, rejected(files(unused)))
        // Reserved and repeated aliases.
        assertEquals(RejectCode.BAD_CUE, rejected(files(full { getJSONArray("collections").getJSONObject(0).put("as", "self") })))
        assertEquals(RejectCode.BAD_CUE, rejected(files(full { getJSONArray("selectors").getJSONObject(0).put("as", "kamakura") })))
        // A cue that asks the AI itself can't also say how it relates to the AI.
        val both = cue("x", spot(), show("speak", "source", JSONArray(listOf("kamakura", "builtin:ai-guide")))).put("narration", "add")
        assertEquals(RejectCode.BAD_CUE, rejected(files(full { put("cues", getJSONArray("cues").put(both)) })))
        // Narration is for spot cues.
        val onEvent = cue("y", JSONObject().put("type", "event").put("event", "start"), show("speak", "resource", "intro")).put("narration", "replace")
        assertEquals(RejectCode.BAD_CUE, rejected(files(full { put("cues", getJSONArray("cues").put(onEvent)) })))
        // At most five cues for any spot.
        val many = full {
            val cues = getJSONArray("cues")
            repeat(5) { cues.put(cue("any-$it", JSONObject().put("type", "spot").put("any", true).put("categories", JSONArray().put("park")),
                show("speak", "source", "history-text"))) }
        }
        assertEquals(RejectCode.BAD_CUE, rejected(files(many)))
    }

    @Test
    fun `the stage counts what selectors and collections may bring`() {
        val media = (check(files(full {
            put("selectors", JSONArray().put(JSONObject().put("as", "history-text")
                .put("match", JSONObject().put("type", "video").put("tags", JSONArray().put("history")))))
            put("cues", JSONArray(listOf(
                cue("hachimangu", spot(), show("sheet", "source", "history-text"), show("card", "source", "kamakura")),
                cue("intro", spot("node/1"), show("speak", "resource", "intro"), show("card", "resource", "old-photo")),
            )))
        })) as StationCheck.Ok).station.media
        assertEquals(MaterialType.VIDEO, media.selectors[0].type)
        assertEquals(3, MediaStage.required(media))
    }

    private fun idle() = JSONObject().put("type", "event").put("event", "idle")

    @Test
    fun `idle cues show only producers fetched by area, with their params`() {
        val studio = show("speak", "source", "builtin:studio")
            .put("params", JSONObject().put("categories", JSONArray(listOf("shrine-temple", "event"))).put("prelude", "by-the-way"))
        val media = (check(files(manifest { put("cues", JSONArray().put(cue("temple-news", idle(), studio))) }, withMedia = false)) as StationCheck.Ok).station.media
        val p = media.cues[0].present[0]
        assertEquals(Trigger.Event(CueEvent.IDLE), media.cues[0].trigger)
        assertEquals(listOf(StudioCategory.SHRINE_TEMPLE, StudioCategory.EVENT), p.params.categories)
        assertEquals(Prelude.BY_THE_WAY, p.params.prelude)
        assertEquals(1, MediaStage.required(media))

        fun one(c: JSONObject) = files(manifest { put("cues", JSONArray().put(c)) }, withMedia = false)
        // Idle has nothing to ask the AI or a collection about, and no repeat.
        assertEquals(RejectCode.BAD_CUE, rejected(one(cue("x", idle(), show("speak", "source", "builtin:ai-guide")))))
        assertEquals(RejectCode.BAD_CUE, rejected(one(cue("x", idle(), show("speak", "source", "builtin:note")).put("repeat", "once"))))
        // Only what a producer here declares, with a rule for its values.
        assertEquals(RejectCode.BAD_CUE, rejected(one(cue("x", idle(), show("speak", "source", "builtin:note").put("params", JSONObject().put("categories", JSONArray().put("food")))))))
        assertEquals(RejectCode.BAD_VALUE, rejected(one(cue("x", idle(), show("speak", "source", "builtin:studio").put("params", JSONObject().put("prelude", "hello"))))))
        assertEquals(RejectCode.BAD_VALUE, rejected(one(cue("x", idle(), show("speak", "source", "builtin:studio").put("params", JSONObject().put("categories", JSONArray().put("bars")))))))
        assertEquals(RejectCode.BAD_CUE, rejected(one(cue("x", spot(), show("speak", "source", "builtin:ai-guide").put("params", JSONObject().put("persona", "x"))))))
        // A spot cue may show the studio around that spot too.
        assertTrue(check(one(cue("x", spot(), show("speak", "source", "builtin:studio")))) is StationCheck.Ok)
    }

    @Test
    fun `shorthands turn off a producer's shorthand cue, even without material`() {
        val off = manifest { put("shorthands", JSONObject().put("builtin:studio", false)) }
        assertEquals(setOf("builtin:studio"), (check(files(off, withMedia = false), stage = 0) as StationCheck.Ok).station.media.shorthandsOff)
        assertEquals(setOf("builtin:studio"), (check(files(off, withMedia = false)) as StationCheck.Ok).station.media.shorthandsOff)
        assertEquals(RejectCode.BAD_CUE, rejected(files(manifest { put("shorthands", JSONObject().put("builtin:studio", true)) }, withMedia = false)))
        assertEquals(RejectCode.BAD_VALUE, rejected(files(manifest { put("shorthands", JSONObject().put("builtin:ai-guide", false)) }, withMedia = false)))
    }

    @Test
    fun `an app passes over what a newer format added`() {
        fun app(m: JSONObject) = StationValidator.check(files(m, withMedia = false), StationOrigin.BUILT_IN, mediaStage = StationValidator.ALL_STAGES, strictness = Strictness.APP)
        val newer = manifest {
            put("shorthands", JSONObject().put("builtin:studio", false).put("builtin:someday", false))
            put("cues", JSONArray(listOf(
                cue("a", idle(), show("speak", "source", "builtin:studio").put("params", JSONObject().put("categories", JSONArray(listOf("event", "someday"))).put("prelude", "someday"))),
                cue("b", spot(), show("speak", "source", JSONArray(listOf("builtin:someday", "builtin:ai-guide")))),
                cue("c", spot("way/2"), show("speak", "source", "builtin:someday")),
            )))
        }
        val media = (app(newer) as StationCheck.Ok).station.media
        assertEquals(setOf("builtin:studio"), media.shorthandsOff)
        assertEquals(listOf(StudioCategory.EVENT), media.cues[0].present[0].params.categories)
        assertEquals(Prelude.NONE, media.cues[0].present[0].params.prelude)
        assertEquals(listOf(Reference.BuiltIn("builtin:ai-guide")), media.cues[1].present[0].items[0])
        assertEquals(listOf("a", "b"), media.cues.map { it.id })
    }
}
