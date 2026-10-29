package io.github.duwenji.yorisoi.kit.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/** The names of the operator's spot tiles (API-SPOT-TILES). */
class GeohashTest {
    @Test
    fun `points are named as geohashes`() {
        assertEquals("xn76u", Geohash.encode(35.6812, 139.7671, 5))
        assertEquals("xn70z", Geohash.encode(35.3262, 139.5566, 5))
        assertEquals("xn7ev", Geohash.encode(35.8327, 140.1456, 5))
    }

    @Test
    fun `a search's circle reaches one tile in the middle, up to four at a corner`() {
        // xn76u spans 35.6396-35.6836 N, 139.7461-139.7900 E.
        assertEquals(listOf("xn76u"), Geohash.covering(35.6616, 139.7680, 600.0, 5))
        assertEquals(4, Geohash.covering(35.6836, 139.7900, 600.0, 5).size)
        assertEquals(2, Geohash.covering(35.6616, 139.7900, 600.0, 5).size)
    }
}
