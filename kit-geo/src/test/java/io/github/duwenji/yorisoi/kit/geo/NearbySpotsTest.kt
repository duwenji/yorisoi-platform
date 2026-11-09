package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.format.SpotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spots 1km around, the kinds added, and the spots picked to talk of (docs/requirements/REQ-NEARBY-TALK.md). */
class NearbySpotsTest {
    private val client = OverpassClient()

    @Test
    fun `spots are searched 1km around, amenities and signals no farther than 600m (F-1)`() {
        val query = client.query(35.0, 139.0, OverpassClient.SPOT_RADIUS_M, emptySet(), signals = true)
        assertTrue(query.contains("""nwr(around:1000,35.0,139.0)["name"]["historic"];"""))
        assertTrue(query.contains("out tags geom 150;"))
        assertTrue(query.contains("""nwr(around:600,35.0,139.0)["amenity"="toilets"]"""))
        assertTrue(query.contains("""nwr(around:250,35.0,139.0)["amenity"="bench"]"""))
        assertTrue(query.contains("""node(around:600,35.0,139.0)["highway"="traffic_signals"]"""))
        assertFalse(query.contains("""around:1000,35.0,139.0)["amenity"="toilets"]"""))
    }

    @Test
    fun `stations, bridges, slopes, libraries and theatres are searched (F-4)`() {
        val query = client.query(35.0, 139.0, 1000, emptySet())
        assertTrue(query.contains("""nwr(around:1000,35.0,139.0)["name"]["railway"="station"];"""))
        assertTrue(query.contains("""nwr(around:1000,35.0,139.0)["name"]["man_made"="bridge"];"""))
        assertTrue(query.contains("""way(around:1000,35.0,139.0)["highway"]["bridge"]["bridge"!="no"]["name"~"橋$"];"""))
        assertTrue(query.contains("""way(around:1000,35.0,139.0)["highway"]["name"~"坂$"];"""))
        assertTrue(query.contains("""nwr(around:1000,35.0,139.0)["name"]["amenity"~"^(library|theatre)$"];"""))
        // Markets stay a shop kind, searched only when a channel prefers them.
        assertFalse(query.contains("marketplace"))
    }

    @Test
    fun `the new kinds get their categories, matching the channel's spot kinds`() {
        assertEquals("駅", OverpassClient.categoryOf(mapOf("name" to "鎌倉", "railway" to "station")))
        assertEquals("橋", OverpassClient.categoryOf(mapOf("name" to "日本橋", "man_made" to "bridge")))
        assertEquals("橋", OverpassClient.categoryOf(mapOf("name" to "日本橋", "highway" to "primary", "bridge" to "yes")))
        assertEquals("坂", OverpassClient.categoryOf(mapOf("name" to "九段坂", "highway" to "secondary")))
        assertEquals("図書館", OverpassClient.categoryOf(mapOf("name" to "中央図書館", "amenity" to "library")))
        assertEquals("劇場", OverpassClient.categoryOf(mapOf("name" to "歌舞伎座", "amenity" to "theatre")))
        // A historic bridge is told about as a historic site.
        assertEquals("史跡", OverpassClient.categoryOf(mapOf("name" to "猿橋", "man_made" to "bridge", "historic" to "bridge")))
        for (kind in listOf(SpotKind.STATION, SpotKind.BRIDGE, SpotKind.SLOPE, SpotKind.LIBRARY, SpotKind.THEATRE)) {
            assertFalse(kind.onlyWhenPreferred)
        }
    }

    @Test
    fun `the ways of one slope are one spot, with all their lines (F-5)`() {
        val found = client.parse(
            """
            {"elements": [
              {"type": "way", "id": 30, "bounds": {"minlat": 35.000, "minlon": 139.000, "maxlat": 35.001, "maxlon": 139.001},
               "geometry": [{"lat": 35.000, "lon": 139.000}, {"lat": 35.001, "lon": 139.001}], "tags": {"name": "九段坂", "highway": "secondary"}},
              {"type": "way", "id": 20, "bounds": {"minlat": 35.001, "minlon": 139.001, "maxlat": 35.002, "maxlon": 139.002},
               "geometry": [{"lat": 35.001, "lon": 139.001}, {"lat": 35.002, "lon": 139.002}], "tags": {"name": "九段坂", "highway": "secondary", "incline": "up"}},
              {"type": "way", "id": 40, "bounds": {"minlat": 35.050, "minlon": 139.050, "maxlat": 35.051, "maxlon": 139.051},
               "geometry": [{"lat": 35.050, "lon": 139.050}, {"lat": 35.051, "lon": 139.051}], "tags": {"name": "九段坂", "highway": "secondary"}},
              {"type": "node", "id": 1, "lat": 35.010, "lon": 139.010, "tags": {"name": "鎌倉", "railway": "station", "operator": "JR"}},
              {"type": "node", "id": 2, "lat": 35.011, "lon": 139.011, "tags": {"name": "鎌倉", "railway": "station", "operator": "江ノ電"}}
            ]}
            """.trimIndent(),
        )
        val slopes = found.spots.filter { it.category == "坂" }
        // The third piece is 6km away: another slope of the same name.
        assertEquals(2, slopes.size)
        val joined = slopes.first()
        assertEquals("way/20", joined.id)
        assertEquals(2, joined.shape!!.lines.size)
        assertFalse(joined.shape!!.isArea)
        assertEquals("up", joined.tags["incline"])
        assertEquals(listOf("node/1"), found.spots.filter { it.category == "駅" }.map { it.id })
    }

    @Test
    fun `the spots around are picked by the channel's kinds, notability and distance (F-6)`() {
        val here = LatLon(35.0, 139.0)
        fun at(northM: Double, eastM: Double) = RouteGeometry.destination(RouteGeometry.destination(here, 0.0, northM), 90.0, eastM)
        fun poi(id: String, category: String, p: LatLon, wiki: Boolean = false) =
            Poi(id, id, p.lat, p.lon, category, if (wiki) mapOf("wikipedia" to "ja:$id") else emptyMap())
        val spots = listOf(
            poi("park", "公園", at(100.0, 0.0)),
            poi("shrine", "神社", at(400.0, 0.0), wiki = true),
            poi("far", "神社", at(1200.0, 0.0), wiki = true),
            poi("told", "寺院", at(50.0, 0.0), wiki = true),
            poi("art", "アート", at(0.0, 200.0)),
            poi("station", "駅", at(-300.0, 0.0)),
        )
        val picked = NearbyHighlights.pick(spots, here, exclude = setOf("told"), skip = listOf(SpotKind.ARTWORK))
        assertEquals(listOf("shrine", "park", "station"), picked.map { it.poi.id })
        val withPrefer = NearbyHighlights.pick(spots, here, prefer = listOf(SpotKind.STATION), max = 2)
        assertEquals(listOf("station", "told"), withPrefer.map { it.poi.id })
    }

    @Test
    fun `each is given with its distance to 50m and its direction (F-7)`() {
        val here = LatLon(35.0, 139.0)
        val ne = RouteGeometry.destination(here, 45.0, 380.0)
        val spot = Poi("a", "a", ne.lat, ne.lon, "神社", emptyMap())
        val seen = NearbyHighlights.pick(listOf(spot), here, headingDeg = 10.0).single()
        assertEquals(400, seen.distanceM)
        assertEquals("北東", seen.compass)
        assertEquals("前方", seen.side)
        assertNull(NearbyHighlights.pick(listOf(spot), here).single().side)
        assertEquals(50, NearbyHighlights.rounded(0.0))
        assertEquals("北", NearbyHighlights.compass(350.0))
        assertEquals("南西", NearbyHighlights.compass(225.0))
    }
}
