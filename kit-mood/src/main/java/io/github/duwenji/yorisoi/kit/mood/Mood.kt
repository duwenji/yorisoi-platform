package io.github.duwenji.yorisoi.kit.mood

import io.github.duwenji.yorisoi.kit.geo.weather.WeatherCodes
import io.github.duwenji.yorisoi.core.text.AppLanguage
import io.github.duwenji.yorisoi.core.text.Languages
import io.github.duwenji.yorisoi.core.text.localized
import java.util.Calendar

enum class TimeOfDay(val label: String) {
    MORNING("朝"), DAY("昼"), EVENING("夕方"), NIGHT("夜"), LATE_NIGHT("深夜");

    val isDark: Boolean get() = this == NIGHT || this == LATE_NIGHT

    companion object {
        fun of(hour: Int) = when (hour) {
            in 4..9 -> MORNING
            in 10..15 -> DAY
            in 16..18 -> EVENING
            in 19..22 -> NIGHT
            else -> LATE_NIGHT
        }

        fun of(c: Calendar) = of(c.get(Calendar.HOUR_OF_DAY))
    }
}

enum class Season(val label: String) {
    SPRING("春"), SUMMER("夏"), AUTUMN("秋"), WINTER("冬");

    companion object {
        /** [month] is 1..12. */
        fun of(month: Int) = when (month) {
            in 3..5 -> SPRING
            in 6..8 -> SUMMER
            in 9..11 -> AUTUMN
            else -> WINTER
        }

        fun of(c: Calendar) = of(c.get(Calendar.MONTH) + 1)
    }
}

/** The weather reduced to what changes the feel of a walk. */
enum class Sky(val label: String) {
    CLEAR("晴れ"), CLOUDY("くもり"), FOG("霧"), RAIN("雨"), SNOW("雪"), THUNDER("雷雨");

    val isWet: Boolean get() = this == RAIN || this == SNOW || this == THUNDER

    companion object {
        /** From a WMO weather code; null when unknown. */
        fun of(code: Int?): Sky? = when {
            code == null -> null
            WeatherCodes.isThunder(code) -> THUNDER
            code in 71..77 || code in 85..86 -> SNOW
            WeatherCodes.isWet(code) -> RAIN
            code == 45 || code == 48 -> FOG
            code == 3 -> CLOUDY
            code in 0..2 -> CLEAR
            else -> null
        }
    }
}

/** What kind of place the user is walking through, guessed from the spots around them. */
enum class Place(val label: String) {
    SHRINE_TEMPLE("寺社"), PARK("公園・緑地"), WATERSIDE("水辺"), HISTORIC("史跡"), TOWN("街なか");

    companion object {
        /** The place a spot category (as shown in the app) suggests; null for ones that say little. */
        fun ofCategory(category: String): Place? = when (category) {
            "神社", "寺院", "教会", "礼拝所" -> SHRINE_TEMPLE
            "公園", "庭園", "自然" -> PARK
            "水辺", "海辺" -> WATERSIDE
            "史跡", "博物館" -> HISTORIC
            else -> null
        }
    }
}

/**
 * The feel of the moment: what the screen colors, the background sound and the companion's
 * tone follow. [sky] is null outside walk mode (no weather then); [place] is null until spots
 * have been searched.
 */
data class Mood(val time: TimeOfDay, val season: Season, val sky: Sky? = null, val place: Place? = null) {
    /** For the companion's prompt, e.g. "秋の夕方、雨、寺社の近く". */
    val summary: String
        get() = listOfNotNull("${season.label}の${time.label}", sky?.label, place?.let { "${it.label}の近く" })
            .joinToString("、")

    /**
     * For the screen, in the app's language (REQ-LANGUAGES F-4), e.g. "autumn evening, rain, near shrines & temples".
     * The prompt keeps [summary] in Japanese (D-10).
     */
    val display: String
        get() = listOfNotNull(
            localized("${season.label}の${time.label}", "${seasonWord(Languages.current)} ${timeWord(Languages.current)}", "${season.label}天的${timeWord(Languages.current)}", "${seasonWord(Languages.current)} ${timeWord(Languages.current)}"),
            sky?.let { skyWord(it) },
            place?.let { placeWord(it) },
        ).joinToString(localized("、", ", ", "，", ", "))

    private fun seasonWord(l: AppLanguage) = when (season) {
        Season.SPRING -> localized("春", "spring", "春", "봄", l)
        Season.SUMMER -> localized("夏", "summer", "夏", "여름", l)
        Season.AUTUMN -> localized("秋", "autumn", "秋", "가을", l)
        Season.WINTER -> localized("冬", "winter", "冬", "겨울", l)
    }

    private fun timeWord(l: AppLanguage) = when (time) {
        TimeOfDay.MORNING -> localized("朝", "morning", "早晨", "아침", l)
        TimeOfDay.DAY -> localized("昼", "daytime", "白天", "낮", l)
        TimeOfDay.EVENING -> localized("夕方", "evening", "傍晚", "저녁", l)
        TimeOfDay.NIGHT -> localized("夜", "night", "夜晚", "밤", l)
        TimeOfDay.LATE_NIGHT -> localized("深夜", "late night", "深夜", "심야", l)
    }

    private fun skyWord(sky: Sky) = when (sky) {
        Sky.CLEAR -> localized("晴れ", "clear", "晴", "맑음")
        Sky.CLOUDY -> localized("くもり", "cloudy", "多云", "흐림")
        Sky.FOG -> localized("霧", "fog", "雾", "안개")
        Sky.RAIN -> localized("雨", "rain", "雨", "비")
        Sky.SNOW -> localized("雪", "snow", "雪", "눈")
        Sky.THUNDER -> localized("雷雨", "thunderstorm", "雷雨", "뇌우")
    }

    private fun placeWord(place: Place) = when (place) {
        Place.SHRINE_TEMPLE -> localized("寺社の近く", "near shrines & temples", "寺社附近", "신사·절 근처")
        Place.PARK -> localized("公園・緑地の近く", "near parks", "公园绿地附近", "공원 근처")
        Place.WATERSIDE -> localized("水辺の近く", "by the water", "水边", "물가 근처")
        Place.HISTORIC -> localized("史跡の近く", "near historic sites", "史迹附近", "사적 근처")
        Place.TOWN -> localized("街なかの近く", "in town", "街区", "시내")
    }

    companion object {
        fun at(c: Calendar, sky: Sky? = null, place: Place? = null) = Mood(TimeOfDay.of(c), Season.of(c), sky, place)
    }
}
