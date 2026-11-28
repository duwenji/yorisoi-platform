package io.github.duwenji.yorisoi.kit.geo.map

import io.github.duwenji.yorisoi.kit.geo.LatLon
import io.github.duwenji.yorisoi.kit.geo.RouteGeometry
import io.github.duwenji.yorisoi.kit.geo.WalkRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The way drawn on the map to a spot or a facility ([route]), kept up to date in [scope] as the
 * user moves ([location]). [lookup] finds the way (RouteClient.walk or bike) when the target
 * changes, and again only when the user has left it, so the routing server (a public one) isn't
 * asked on every step. While it loads, and if it can't be found, a straight line shows the direction.
 *
 * A facility the user picked stays until they [clear] it or reach it; a spot the app [suggest]s
 * doesn't replace it, since someone looking for a toilet shouldn't be led elsewhere.
 */
class RouteKeeper(
    scope: CoroutineScope,
    location: Flow<LatLon?>,
    private val lookup: suspend (from: LatLon, to: LatLon) -> WalkRoute,
    private val onLookupFailed: (Exception) -> Unit = {},
) {
    private val _target = MutableStateFlow<RouteTarget?>(null)
    val target: StateFlow<RouteTarget?> = _target.asStateFlow()

    private val _route = MutableStateFlow<RouteToSpot?>(null)
    val route: StateFlow<RouteToSpot?> = _route.asStateFlow()

    init {
        // Not collectLatest: a location update every few seconds would cancel each lookup in flight.
        scope.launch {
            combine(_target, location) { target, here -> target to here }
                .collect { (target, here) -> update(target, here) }
        }
    }

    /** The user picked [target]: shown in place of any other. */
    fun show(target: RouteTarget) {
        _target.value = target
    }

    /** A spot the app points to by itself (the one introduced last); shown unless a facility is. */
    fun suggest(target: RouteTarget) {
        if (_target.value?.facility == null) _target.value = target
    }

    fun clear() {
        _target.value = null
    }

    private suspend fun update(target: RouteTarget?, here: LatLon?) {
        if (target == null) {
            _route.value = null
            return
        }
        if (here == null) return
        val to = target.at
        val current = _route.value?.takeIf { it.target == target }
        if (current != null) {
            // Reached: only after coming from farther, so a facility picked close by still shows.
            if (target.facility != null && RouteGeometry.distanceM(here, to) < ARRIVED_M &&
                RouteGeometry.distanceM(current.askedFrom, to) >= ARRIVED_M
            ) {
                _target.compareAndSet(target, null)
                return
            }
            val stillValid = if (current.route.onPaths) {
                RouteGeometry.distanceToLineM(here, current.route.points) < OFF_ROUTE_M
            } else {
                RouteGeometry.distanceM(current.askedFrom, here) < RETRY_AFTER_M
            }
            if (stillValid) {
                // A straight line follows the user; a found route stays as found.
                if (!current.route.onPaths) _route.value = current.copy(route = WalkRoute.straight(here, to))
                return
            }
        } else {
            // Something to look at while the route loads, and what stays if it can't be found.
            _route.value = RouteToSpot(target, WalkRoute.straight(here, to), here, loading = true)
        }
        val found = try {
            lookup(here, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onLookupFailed(e)
            WalkRoute.straight(here, to)
        }
        // The user may have picked another target meanwhile.
        if (_target.value == target) _route.value = RouteToSpot(target, found, here)
    }

    companion object {
        /** How far off a found route the user may stray before it's looked up again. */
        const val OFF_ROUTE_M = 30.0

        /** How far the user may move before a failed lookup (a straight line) is tried again. */
        const val RETRY_AFTER_M = 100.0

        /** How close to a facility counts as reached, which takes its route away. */
        const val ARRIVED_M = 20.0
    }
}
