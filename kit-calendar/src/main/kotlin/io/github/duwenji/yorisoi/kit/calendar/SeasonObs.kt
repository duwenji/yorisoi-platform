package io.github.duwenji.yorisoi.kit.calendar

import org.json.JSONObject
import java.time.LocalDate

/** The kinds of the 気象庁's seasonal observations (docs/contracts/api/season-obs.schema.json). */
enum class ObsKind(val json: String, val label: String) {
    FIRST_SNOW("first-snow", "初雪"),
    FIRST_FROST("first-frost", "初霜"),
    FIRST_ICE("first-ice", "初氷"),
    PHENOLOGY("phenology", "生物季節観測"),
    SNOW_CAP("snow-cap", "初冠雪"),
    ;

    companion object {
        fun of(json: String): ObsKind? = entries.firstOrNull { it.json == json }
    }
}

/** One observation: what, when, where, and how far from the usual year and from last year (days; earlier is negative). */
data class SeasonObservation(
    val kind: ObsKind,
    val name: String?,
    val date: LocalDate,
    val place: String,
    val prefecture: String,
    val municipality: String?,
    val fromNormal: Int?,
    val fromLastYear: Int?,
    val source: String?,
) {
    /**
     * The observation as one fact for the preparer and the speaker (at most 60 characters), with the values as
     * the 気象庁 gave them (ADR-AGENT A-28: they are not changed).
     */
    fun fact(): String {
        val what = when (kind) {
            ObsKind.PHENOLOGY -> name ?: kind.label
            ObsKind.SNOW_CAP -> "${place}の初冠雪"
            else -> kind.label
        }
        val at = if (kind == ObsKind.SNOW_CAP) "" else "${place}で "
        val head = "$at${date.monthValue}月${date.dayOfMonth}日に${what}を${if (kind == ObsKind.SNOW_CAP) "確認" else "観測"}"
        val tail = listOfNotNull(fromNormal?.let { "平年${diff(it)}" }, fromLastYear?.let { "昨年${diff(it)}" }).joinToString("、")
        val text = if (tail.isEmpty()) head else "$head、$tail"
        return if (text.length <= 60) text else head.take(60)
    }

    private fun diff(d: Int): String = when {
        d < 0 -> "より ${-d} 日早い"
        d > 0 -> "より $d 日遅い"
        else -> "並み"
    }
}

/** One cold season's observations file (寒候年: August to July), as the operator's batch hands it out. */
data class SeasonObsFile(val season: String, val attribution: String, val observations: List<SeasonObservation>) {
    /**
     * What `season_obs` gives (ADR-AGENT A-12・A-28): the observations of the last [days] days up to [today] in
     * [prefecture] and the prefectures next to it, the one's own first, newest first, at most [limit].
     */
    fun around(prefecture: String, today: LocalDate, days: Long = 14, limit: Int = 20): List<SeasonObservation> {
        val near = Prefectures.neighborsOf(prefecture) + prefecture
        val from = today.minusDays(days)
        return observations.asSequence()
            .filter { it.prefecture in near && !it.date.isBefore(from) && !it.date.isAfter(today) }
            .sortedWith(compareBy<SeasonObservation> { it.prefecture != prefecture }.thenByDescending { it.date })
            .take(limit)
            .toList()
    }

    companion object {
        /** Reads a file; observations of a kind this version doesn't know are left out. */
        fun parse(json: String): SeasonObsFile {
            val o = JSONObject(json)
            val arr = o.getJSONArray("observations")
            val list = (0 until arr.length()).mapNotNull { i ->
                val x = arr.getJSONObject(i)
                val kind = ObsKind.of(x.getString("kind")) ?: return@mapNotNull null
                SeasonObservation(
                    kind = kind,
                    name = x.optString("name").ifEmpty { null },
                    date = LocalDate.parse(x.getString("date")),
                    place = x.getString("place"),
                    prefecture = x.getString("prefecture"),
                    municipality = x.optString("municipality").ifEmpty { null },
                    fromNormal = if (x.isNull("fromNormal") || !x.has("fromNormal")) null else x.getInt("fromNormal"),
                    fromLastYear = if (x.isNull("fromLastYear") || !x.has("fromLastYear")) null else x.getInt("fromLastYear"),
                    source = x.optString("source").ifEmpty { null },
                )
            }
            return SeasonObsFile(o.getString("season"), o.getString("attribution"), list)
        }
    }
}
