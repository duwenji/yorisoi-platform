package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.core.text.Words
import androidx.compose.ui.graphics.Color
import io.github.duwenji.yorisoi.core.format.SpotKind

/**
 * The spot kinds in a few groups, for the marker colors on the map and a filter in the app's
 * list. The colors keep apart from the facility markers (rounded squares) by shape, and from
 * each other by lightness as well as hue.
 */
enum class SpotGroup(private val words: Words, val color: Color) {
    WORSHIP(Words("寺社", "Shrines & temples", "寺社", "신사·절"), Color(0xFFB0412E)),
    HISTORIC(Words("史跡", "Historic sites", "史迹", "사적"), Color(0xFF2E7D32)),
    CULTURE(Words("文化", "Culture", "文化", "문화"), Color(0xFF6A4FB3)),
    NATURE(Words("自然", "Nature", "自然", "자연"), Color(0xFF7C8C1E)),
    OTHER(Words("お店ほか", "Shops & more", "商店等", "가게 등"), Color(0xFF546E7A)),
    ;

    /** The group's name, in the app's language. */
    val label: String get() = words.text

    companion object {
        /** The group of a [io.github.duwenji.yorisoi.kit.geo.Poi.category]; [OTHER] for one no kind has. */
        fun of(category: String): SpotGroup = when (SpotKind.entries.firstOrNull { it.category == category }) {
            SpotKind.SHRINE, SpotKind.TEMPLE, SpotKind.CHURCH, SpotKind.WORSHIP -> WORSHIP
            SpotKind.HISTORIC, SpotKind.ATTRACTION -> HISTORIC
            SpotKind.MUSEUM, SpotKind.GALLERY, SpotKind.ARTWORK, SpotKind.LIBRARY, SpotKind.THEATRE -> CULTURE
            SpotKind.VIEWPOINT, SpotKind.PARK, SpotKind.GARDEN, SpotKind.BEACH, SpotKind.WATER, SpotKind.NATURE -> NATURE
            SpotKind.RESTAURANT, SpotKind.CAFE, SpotKind.SWEETS, SpotKind.FOOD_SHOP, SpotKind.MARKET,
            SpotKind.CRAFTS, SpotKind.SHOP, SpotKind.STATION, SpotKind.BRIDGE, SpotKind.SLOPE, null -> OTHER
        }
    }
}
