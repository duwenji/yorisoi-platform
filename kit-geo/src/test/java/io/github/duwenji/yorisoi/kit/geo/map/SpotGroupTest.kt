package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.core.format.SpotKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotGroupTest {
    @Test
    fun placesOfWorshipAreOneGroup() {
        listOf("神社", "寺院", "教会", "礼拝所").forEach { assertEquals(it, SpotGroup.WORSHIP, SpotGroup.of(it)) }
    }

    @Test
    fun landmarksGoWithHistoricSites() {
        assertEquals(SpotGroup.HISTORIC, SpotGroup.of("史跡"))
        assertEquals(SpotGroup.HISTORIC, SpotGroup.of("名所"))
    }

    @Test
    fun viewpointsAndWaterAreNature() {
        listOf("公園", "庭園", "海辺", "水辺", "自然", "展望スポット").forEach { assertEquals(it, SpotGroup.NATURE, SpotGroup.of(it)) }
    }

    @Test
    fun shopsAndUnknownCategoriesAreOther() {
        SpotKind.entries.filter { it.onlyWhenPreferred }.forEach { assertEquals(it.category, SpotGroup.OTHER, SpotGroup.of(it.category)) }
        assertEquals(SpotGroup.OTHER, SpotGroup.of("スポット"))
    }
}
