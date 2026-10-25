package io.github.duwenji.yorisoi.kit.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The amenities an app asks for: a walker's by default, a cyclist's when asked (kit-geo README「施設」). */
class OverpassFacilitiesTest {
    private val client = OverpassClient()
    private val cyclist = setOf(
        FacilityKind.TOILETS, FacilityKind.DRINKING_WATER, FacilityKind.BICYCLE_PARKING,
        FacilityKind.CONVENIENCE_STORE, FacilityKind.BICYCLE_SHOP, FacilityKind.BICYCLE_PUMP,
    )

    @Test
    fun `a walker's amenities are searched by default, benches and machines only close by`() {
        val query = client.query(35.0, 139.0, 600, emptySet())
        assertTrue(query.contains("""nwr(around:600,35.0,139.0)["amenity"="toilets"]["access"!~"^(private|no)$"];""" + "\nout center 40;"))
        assertTrue(query.contains("""nwr(around:250,35.0,139.0)["amenity"="bench"];"""))
        assertTrue(query.contains("""nwr(around:250,35.0,139.0)["amenity"="vending_machine"]"""))
        assertFalse(query.contains("bicycle"))
        assertFalse(query.contains("convenience"))
    }

    @Test
    fun `a cyclist's amenities are searched as asked, each kind with its own output`() {
        val query = client.query(35.0, 139.0, 800, emptySet(), facilities = cyclist)
        assertFalse(query.contains("bench"))
        assertFalse(query.contains("vending_machine"))
        assertTrue(query.contains("""nwr(around:800,35.0,139.0)["amenity"="bicycle_parking"]"""))
        assertTrue(query.contains("""nwr(around:800,35.0,139.0)["shop"="convenience"];""" + "\nout center 40;"))
        assertTrue(query.contains("""nwr(around:800,35.0,139.0)["shop"="bicycle"];"""))
        // Two ways a pump is mapped, as one output.
        assertTrue(
            query.contains(
                """(nwr(around:800,35.0,139.0)["amenity"="bicycle_repair_station"]["service:bicycle:pump"="yes"];""" +
                    """nwr(around:800,35.0,139.0)["amenity"="compressed_air"]["access"!~"^(private|no)$"];);""" + "\nout center 40;",
            ),
        )
        assertEquals(cyclist.size, Regex("out center 40;").findAll(query).count())
    }

    @Test
    fun `amenities get their kind from their tags`() {
        val found = client.parse(
            """
            {"elements": [
              {"type": "node", "id": 1, "lat": 35.1, "lon": 139.1, "tags": {"amenity": "bicycle_parking"}},
              {"type": "node", "id": 2, "lat": 35.2, "lon": 139.2, "tags": {"shop": "convenience", "name": "コンビニ由比ヶ浜店"}},
              {"type": "way", "id": 3, "center": {"lat": 35.3, "lon": 139.3}, "tags": {"shop": "bicycle", "name": "鎌倉サイクル"}},
              {"type": "node", "id": 4, "lat": 35.4, "lon": 139.4, "tags": {"amenity": "bicycle_repair_station", "service:bicycle:pump": "yes"}}
            ]}
            """.trimIndent(),
        )
        assertEquals(
            listOf(FacilityKind.BICYCLE_PARKING, FacilityKind.CONVENIENCE_STORE, FacilityKind.BICYCLE_SHOP, FacilityKind.BICYCLE_PUMP),
            found.facilities.map { it.kind },
        )
        assertEquals("鎌倉サイクル", found.facilities[2].name)
        assertEquals(35.3, found.facilities[2].lat, 0.0)
        assertTrue(found.spots.isEmpty())
    }

    @Test
    fun `a repair stand without a pump is no pump, an air compressor is one`() {
        assertNull(FacilityKind.of(mapOf("amenity" to "bicycle_repair_station")))
        assertEquals(FacilityKind.BICYCLE_PUMP, FacilityKind.of(mapOf("amenity" to "compressed_air")))
        assertEquals(FacilityKind.TOILETS, FacilityKind.of(mapOf("amenity" to "toilets")))
    }

    @Test
    fun `a place's side is told from the way the user goes`() {
        assertEquals("前方", relativeDirection(heading = 350f, bearing = 10f))
        assertEquals("右手", relativeDirection(heading = 0f, bearing = 90f))
        assertEquals("左手", relativeDirection(heading = 90f, bearing = 0f))
        assertEquals("後ろ", relativeDirection(heading = 0f, bearing = -170f))
    }
}
