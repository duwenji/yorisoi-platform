package io.github.duwenji.yorisoi.kit.geo.map

import androidx.compose.ui.graphics.Color
import io.github.duwenji.yorisoi.core.format.SpotKind

/**
 * The spot kinds in a few groups, for the marker colors on the map and a filter in the app's
 * list. The colors keep apart from the facility markers (rounded squares) by shape, and from
 * each other by lightness as well as hue.
 */
enum class SpotGroup(val label: String, val color: Color) {
    WORSHIP("寺社", Color(0xFFB0412E)),
    HISTORIC("史跡", Color(0xFF2E7D32)),
    CULTURE("文化", Color(0xFF6A4FB3)),
    NATURE("自然", Color(0xFF7C8C1E)),
    OTHER("お店ほか", Color(0xFF546E7A)),
    ;

    companion object {
        /** The group of a [io.github.duwenji.yorisoi.kit.geo.Poi.category]; [OTHER] for one no kind has. */
        fun of(category: String): SpotGroup = when (SpotKind.entries.firstOrNull { it.category == category }) {
            SpotKind.SHRINE, SpotKind.TEMPLE, SpotKind.CHURCH, SpotKind.WORSHIP -> WORSHIP
            SpotKind.HISTORIC, SpotKind.ATTRACTION -> HISTORIC
            SpotKind.MUSEUM, SpotKind.GALLERY, SpotKind.ARTWORK -> CULTURE
            SpotKind.VIEWPOINT, SpotKind.PARK, SpotKind.GARDEN, SpotKind.BEACH, SpotKind.WATER, SpotKind.NATURE -> NATURE
            SpotKind.RESTAURANT, SpotKind.CAFE, SpotKind.SWEETS, SpotKind.FOOD_SHOP, SpotKind.MARKET,
            SpotKind.CRAFTS, SpotKind.SHOP, null -> OTHER
        }
    }
}
