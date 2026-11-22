package io.github.duwenji.yorisoi.kit.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MunicipalitiesTest {
    private val points = Municipalities.parse(
        """code,name,lat,lon
182010,福井県福井市,36.06190,136.22320
182010,福井県福井市,36.05000,136.20000
182044,福井県鯖江市,35.95660,136.18440
bad,line
182087,福井県あわら市,not-a-number,136.2
""",
    )

    @Test
    fun `the nearest representative point gives the municipality`() {
        assertEquals(Municipality("182010", "福井県福井市"), points.at(36.0600, 136.2200))
        assertEquals("福井県鯖江市", points.at(35.9500, 136.1900)?.name)
        assertEquals("18", points.at(36.0600, 136.2200)?.prefecture)
    }

    @Test
    fun `no point within 5 km gives none`() {
        assertNull(points.at(36.3000, 135.9000))
    }

    @Test
    fun `the index gives the prefectures a point may be in`() {
        val index = """{"version":1,"prefectures":{"17":[36.0,136.2,37.9,137.4],"18":[35.3,135.4,36.35,136.9]}}"""
        assertEquals(listOf("18"), Municipalities.prefecturesAt(index, 35.9, 136.2))
        assertEquals("near the border, both", listOf("17", "18"), Municipalities.prefecturesAt(index, 36.3, 136.3))
        assertEquals(emptyList<String>(), Municipalities.prefecturesAt(index, 40.0, 140.0))
        assertEquals(emptyList<String>(), Municipalities.prefecturesAt("not json", 36.0, 136.0))
    }
}
