package io.github.duwenji.yorisoi.kit.geo

import io.github.duwenji.yorisoi.core.format.SpotKind
import kotlin.math.roundToInt

/**
 * A spot around the user worth a mention, with where it is from them (REQ-NEARBY-TALK F-7).
 * [distanceM] is rounded to 50m; [compass] is one of the eight directions (北, 北東, ...);
 * [side] is where it is from the way the user goes ([relativeDirection]: 前方, 右手, 左手, 後ろ), or null when that isn't known.
 */
data class NearbyHighlight(val poi: Poi, val distanceM: Int, val compass: String, val side: String?)

/** Picks the spots around the user to give the AI, so that it can talk of what is near (REQ-NEARBY-TALK F-6). */
object NearbyHighlights {
    const val MAX = 6

    /**
     * At most [max] of [spots] within [radiusM] of [here], leaving out [exclude] (ids: those talked
     * about on this walk, the one being told about) and the kinds in [skip]. The kinds in [prefer]
     * first, then the most notable ([Poi.notability]), then the nearest. [headingDeg] is the way the
     * user goes (degrees clockwise from north), if known.
     */
    fun pick(
        spots: List<Poi>,
        here: LatLon,
        headingDeg: Double? = null,
        exclude: Set<String> = emptySet(),
        prefer: Collection<SpotKind> = emptyList(),
        skip: Collection<SpotKind> = emptyList(),
        max: Int = MAX,
        radiusM: Double = OverpassClient.SPOT_RADIUS_M.toDouble(),
    ): List<NearbyHighlight> {
        val preferred = prefer.map { it.category }.toSet()
        val skipped = skip.map { it.category }.toSet()
        return spots.asSequence()
            .filter { it.id !in exclude && it.category !in skipped }
            .map { it to it.distanceM(here) }
            .filter { (_, d) -> d <= radiusM }
            .sortedWith(
                compareBy<Pair<Poi, Double>> { (p, _) -> if (p.category in preferred) 0 else 1 }
                    .thenByDescending { (p, _) -> p.notability }
                    .thenBy { (_, d) -> d },
            )
            .take(max)
            .map { (p, d) ->
                val bearing = RouteGeometry.bearingDeg(here, LatLon(p.lat, p.lon))
                NearbyHighlight(p, rounded(d), compass(bearing), headingDeg?.let { relativeDirection(it.toFloat(), bearing.toFloat()) })
            }
            .toList()
    }

    /** To 50m, and no less than 50m: "0m away" isn't how anyone says it. */
    internal fun rounded(distanceM: Double): Int = maxOf(STEP_M, (distanceM / STEP_M).roundToInt() * STEP_M)

    internal fun compass(bearingDeg: Double): String = COMPASS[(((bearingDeg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

    private const val STEP_M = 50
    private val COMPASS = listOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
}
