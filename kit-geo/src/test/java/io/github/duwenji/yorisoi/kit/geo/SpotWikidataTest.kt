package io.github.duwenji.yorisoi.kit.geo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tiles' Wikidata values and what they change (platform REQ-WIKIDATA). */
class SpotWikidataTest {
    private val hachimangu = SpotWikidata(
        id = "Q1146088", description = "神奈川県鎌倉市にある神社", reading = "ツルガオカハチマングウ",
        kinds = listOf("神社", "八幡宮"), inception = "1063", heritage = listOf("史跡"),
        founders = listOf("源頼義"), jawiki = "鶴岡八幡宮", image = "Tsurugaoka.jpg", website = "https://www.hachimangu.or.jp/",
    )

    @Test
    fun `read from a tile, and written back the same`() {
        val json = JSONObject(
            """{"id":"Q1146088","description":"神奈川県鎌倉市にある神社","reading":"ツルガオカハチマングウ","kinds":["神社","八幡宮"],
               "inception":"1063","heritage":["史跡"],"founders":["源頼義"],"jawiki":"鶴岡八幡宮","image":"Tsurugaoka.jpg",
               "website":"https://www.hachimangu.or.jp/","unknown":1}""",
        )
        val read = SpotWikidata.fromJson(json)
        assertEquals(hachimangu, read)
        assertEquals(read, SpotWikidata.fromJson(read!!.toJson()))
        assertNull(SpotWikidata.fromJson(JSONObject("""{"id":"x"}""")))
        assertNull(SpotWikidata.fromJson(JSONObject("""{"id":"Q1","website":"javascript:alert(1)"}"""))?.website)
    }

    @Test
    fun `the tile's spot carries its values`() {
        val tile = SpotTiles.parse(
            """{"format":"yorisoi-spot-tile","version":1,"layer":"base","tile":"xn70z","spots":[
                {"id":"node/1","lat":35.3,"lon":139.5,"tags":{"name":"鶴岡八幡宮","amenity":"place_of_worship","religion":"shinto","wikidata":"Q1146088"},
                 "wikidata":{"id":"Q1146088","jawiki":"鶴岡八幡宮"}},
                {"id":"node/2","lat":35.3,"lon":139.5,"tags":{"name":"小さな社","amenity":"place_of_worship"}}],
               "facilities":[],"signals":[]}""",
            "base", "xn70z",
        )
        val (shrine, small) = tile.spots
        assertEquals("鶴岡八幡宮", shrine.wikidata?.jawiki)
        assertTrue(shrine.fromTile && small.fromTile)
        assertNull(small.wikidata)
    }

    @Test
    fun `the facts take OSM first and fill in from Wikidata`() {
        val poi = Poi("node/1", "鶴岡八幡宮", 35.3, 139.5, "神社", mapOf("religion" to "shinto"), wikidata = hachimangu)
        val facts = SpotFacts.of(poi)
        assertEquals("ツルガオカハチマングウ", facts.reading)
        assertEquals(listOf("神道", "創建 1063年", "史跡"), facts.summary)
        assertEquals(
            listOf(
                SpotFact("説明", "神奈川県鎌倉市にある神社"),
                // 神社 is the spot's kind already.
                SpotFact("種類", "八幡宮"),
                SpotFact("創建・設置", "1063年"),
                SpotFact("宗教", "神道"),
                SpotFact("文化財", "史跡"),
                SpotFact("創設者", "源頼義"),
            ),
            facts.details,
        )
        assertEquals(
            listOf(
                SpotLink("Wikipedia「鶴岡八幡宮」", "https://ja.wikipedia.org/wiki/%E9%B6%B4%E5%B2%A1%E5%85%AB%E5%B9%A1%E5%AE%AE"),
                SpotLink("公式サイト（hachimangu.or.jp）", "https://www.hachimangu.or.jp/"),
            ),
            facts.links,
        )

        val osm = SpotFacts.of(
            mapOf(
                "description:ja" to "源頼義が勧請した", "start_date" to "1180", "heritage" to "2", "heritage:type" to "重要文化財",
                "name:ja-Hira" to "つるがおかはちまんぐう", "wikipedia" to "ja:鶴岡八幡宮 (鎌倉市)", "website" to "https://example.jp/",
            ),
            hachimangu, "神社",
        )
        assertEquals("つるがおかはちまんぐう", osm.reading)
        assertEquals(SpotFact("説明", "源頼義が勧請した"), osm.details.first())
        assertTrue(SpotFact("創建・設置", "1180年") in osm.details)
        assertTrue(SpotFact("文化財", "重要文化財") in osm.details)
        assertEquals(listOf("Wikipedia「鶴岡八幡宮 (鎌倉市)」", "公式サイト（example.jp）"), osm.links.map { it.label })
    }

    @Test
    fun `Wikidata's designation comes before OSM's bare level`() {
        val facts = SpotFacts.of(mapOf("heritage" to "2"), SpotWikidata("Q1", heritage = listOf("国宝", "史跡")))
        assertTrue(SpotFact("文化財", "国宝・史跡") in facts.details)
        assertTrue(SpotFact("文化財", "国の指定") in SpotFacts.of(mapOf("heritage" to "2")).details)
    }

    @Test
    fun `notability needs a Japanese article or a national designation (F-20)`() {
        fun poi(tags: Map<String, String>, wd: SpotWikidata? = null, category: String = "史跡") =
            Poi("n", "n", 35.0, 139.0, category, tags, wikidata = wd)

        assertEquals(2, poi(mapOf("wikipedia" to "ja:浅草寺")).notability)
        assertEquals(2, poi(mapOf("wikidata" to "Q1"), SpotWikidata("Q1", jawiki = "浅草寺")).notability)
        assertEquals(1, poi(mapOf("wikidata" to "Q1")).notability)
        assertEquals(1, poi(mapOf("wikipedia" to "en:Sensō-ji")).notability)
        assertEquals(0, poi(emptyMap()).notability)
        assertEquals(1, poi(emptyMap(), SpotWikidata("Q1", heritage = listOf("重要文化財"))).notability)
        assertEquals(0, poi(emptyMap(), SpotWikidata("Q1", heritage = listOf("登録有形文化財"))).notability)
        assertEquals(2, poi(mapOf("wikidata" to "Q1", "heritage" to "2")).notability)
        assertEquals(4, poi(mapOf("wikipedia" to "ja:浅草寺", "heritage" to "1"), category = "寺院").notability)
    }

    @Test
    fun `a tile's spot finds its photo without asking Wikidata (F-19)`() {
        val tags = mapOf("wikidata" to "Q1")
        val tileWithImage = Poi("n", "n", 0.0, 0.0, "", tags, wikidata = SpotWikidata("Q1", image = "A.jpg"), fromTile = true)
        val tileWithout = Poi("n", "n", 0.0, 0.0, "", tags, wikidata = null, fromTile = true)
        val overpass = Poi("n", "n", 0.0, 0.0, "", tags)
        assertEquals(CommonsFile.Hint.FILE, CommonsFile.hint(tileWithImage))
        assertEquals("A.jpg", CommonsFile.fileOf(tileWithImage))
        assertEquals(CommonsFile.Hint.NONE, CommonsFile.hint(tileWithout))
        assertEquals(CommonsFile.Hint.ASK_WIKIDATA, CommonsFile.hint(overpass))
        // The tags' own file comes first.
        val tagged = tileWithImage.copy(tags = tags + ("wikimedia_commons" to "File:B.jpg"))
        assertEquals("B.jpg", CommonsFile.fileOf(tagged))
        assertFalse(CommonsFile.hint(Poi("n", "n", 0.0, 0.0, "", emptyMap())) != CommonsFile.Hint.NONE)
    }
}
