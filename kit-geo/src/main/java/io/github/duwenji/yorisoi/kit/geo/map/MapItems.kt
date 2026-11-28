package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.kit.geo.Facility
import io.github.duwenji.yorisoi.kit.geo.FacilityKind
import io.github.duwenji.yorisoi.kit.geo.LatLon
import io.github.duwenji.yorisoi.kit.geo.Poi
import io.github.duwenji.yorisoi.kit.geo.WalkRoute

/** A nearby spot; [visitCount] is how many walks have stopped there. */
data class SpotItem(val poi: Poi, val distanceM: Int?, val visitCount: Int)

/** A nearby toilet, drinking fountain or bench. */
data class FacilityItem(val facility: Facility, val distanceM: Int?)

/**
 * Where a way drawn on the map leads: a spot, or a facility of the kind [facility].
 * [name] is what the label over the map says.
 */
data class RouteTarget(val id: String, val name: String, val lat: Double, val lon: Double, val facility: FacilityKind? = null) {
    val at: LatLon get() = LatLon(lat, lon)

    companion object {
        fun of(poi: Poi) = RouteTarget(poi.id, poi.name, poi.lat, poi.lon)

        fun of(facility: Facility) = RouteTarget(facility.id, facility.title, facility.lat, facility.lon, facility.kind)
    }
}

/**
 * The way to [target] drawn on the map; [askedFrom] is where the route was last looked up.
 * While [loading], [route] is a straight placeholder.
 */
data class RouteToSpot(val target: RouteTarget, val route: WalkRoute, val askedFrom: LatLon, val loading: Boolean = false)
