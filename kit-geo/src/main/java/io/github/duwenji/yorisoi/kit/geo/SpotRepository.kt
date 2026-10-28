package io.github.duwenji.yorisoi.kit.geo

import android.location.Location
import io.github.duwenji.yorisoi.core.format.SpotKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Where the spots shown came from. */
enum class SpotSource {
    /** Overpass, just now. */
    NETWORK,

    /** A kept answer from nearby, new enough to use without asking ([SpotRepository]'s `freshMs`). */
    CACHE,

    /** An older kept answer from nearby, shown while asking Overpass again. */
    STALE,

    /** The nearest kept answer, because Overpass failed or is being given a rest. */
    FALLBACK,
}

/**
 * Current location, nearby spots and amenities, shared by the UI and the walk service.
 * [shops] gives the eating and shopping kinds the channel in use adds to the search.
 * With a [cache], answers from nearby are used instead of asking the public Overpass servers
 * again, and are what's shown when they fail. [useCache] turns it off for now (the user's choice),
 * neither reading nor keeping answers; [freshMs] is how new an answer must be to use it without
 * asking, up to [SpotCache.MAX_AGE_MS]. After a failed search, the next waits a while
 * ([retryDelaysMs], by default [RETRY_DELAYS_MS]) rather than asking again at the next location;
 * an app that moves fast waits less, as it soon leaves the kept answers behind.
 *
 * [searchCenter] is where to search from a location: the location itself, or a point ahead of it
 * for an app that moves fast (a cyclist's). [signals] adds the traffic signals to the search, and
 * [facilityKinds] are the amenities searched (a walker's by default).
 */
class SpotRepository(
    private val overpass: OverpassClient,
    private val shops: () -> Set<SpotKind> = { emptySet() },
    private val cache: SpotCache? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val useCache: () -> Boolean = { true },
    private val freshMs: () -> Long = { SpotCache.FRESH_MS },
    private val searchCenter: (Location) -> LatLon = { LatLon(it.latitude, it.longitude) },
    private val signals: Boolean = false,
    private val facilityKinds: Set<FacilityKind> = FacilityKind.WALK,
    private val retryDelaysMs: LongArray = RETRY_DELAYS_MS,
) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _spots = MutableStateFlow<List<Poi>>(emptyList())
    val spots: StateFlow<List<Poi>> = _spots.asStateFlow()

    private val _facilities = MutableStateFlow<List<Facility>>(emptyList())
    val facilities: StateFlow<List<Facility>> = _facilities.asStateFlow()

    private val _signals = MutableStateFlow<List<LatLon>>(emptyList())

    /** The traffic signals around the answer shown; always empty unless the repository was made with `signals`. */
    val trafficSignals: StateFlow<List<LatLon>> = _signals.asStateFlow()

    /** Where the spots shown came from; null before the first search. */
    @Volatile
    var source: SpotSource? = null
        private set

    /** How many times the spots shown have been replaced; a change across [onLocation] means it searched. */
    @Volatile
    var updates: Int = 0
        private set

    /** Where Overpass was last asked about, whether it answered or not; null before the first time. */
    @Volatile
    var searchedAt: LatLon? = null
        private set

    private val fetchLock = Mutex()

    /** The centre of the answer shown, while it is good: from Overpass, or kept and fresh. */
    private var shownAt: LatLon? = null
    private var lastShops: Set<SpotKind> = emptySet()

    /** The kept answer shown, if one is; not read and shown again at every location. */
    private var shownKept: SpotCache.Entry? = null
    private var failures = 0
    private var retryAt = 0L

    /**
     * Updates the location, re-querying spots only after moving [REFETCH_DISTANCE_M] from the
     * answer shown or when the channel now wants other shops. [forceRefresh] asks Overpass even
     * when the answer shown, or a kept one, would do, and even while waiting after a failure.
     * True when the spots shown were replaced by this call, not by another one at the same time.
     */
    suspend fun onLocation(location: Location, forceRefresh: Boolean = false): Boolean {
        _location.value = location
        val center = searchCenter(location)
        return search(center.lat, center.lon, forceRefresh)
    }

    /** Searches again where the user is, if the channel's shops changed (a switch of channel). */
    suspend fun onShopsChanged() {
        _location.value?.let { onLocation(it) }
    }

    internal suspend fun search(lat: Double, lon: Double, forceRefresh: Boolean = false): Boolean = fetchLock.withLock {
        val before = updates
        searchAt(lat, lon, forceRefresh)
        updates != before
    }

    private suspend fun searchAt(lat: Double, lon: Double, forceRefresh: Boolean) {
        val here = LatLon(lat, lon)
        val shops = shops()
        val shown = shownAt
        val cache = cache?.takeIf { useCache() }
        if (!forceRefresh && shown != null && shops == lastShops && RouteGeometry.distanceM(shown, here) <= REFETCH_DISTANCE_M) return
        if (!forceRefresh) {
            cache?.find(lat, lon, shops, REFETCH_DISTANCE_M, freshMs().coerceIn(0L, SpotCache.MAX_AGE_MS), signals, facilityKinds)?.let { kept ->
                if (show(kept, SpotSource.CACHE)) {
                    shownAt = kept.center
                    lastShops = shops
                    return
                }
            }
            if (now() < retryAt) {
                cache?.let { showFallback(it, lat, lon, shops) }
                return
            }
        }
        val stale = cache?.find(lat, lon, shops, REFETCH_DISTANCE_M, SpotCache.MAX_AGE_MS, signals, facilityKinds)?.takeIf { show(it, SpotSource.STALE) }
        searchedAt = here
        try {
            val body = overpass.fetch(lat, lon, shops = shops, signals = signals, facilities = facilityKinds)
            show(overpass.parse(body), SpotSource.NETWORK)
            shownKept = null
            cache?.put(lat, lon, shops, body, replaceWithinM = REPLACE_DISTANCE_M, signals = signals, facilities = facilityKinds)
            shownAt = here
            lastShops = shops
            failures = 0
            retryAt = 0L
        } catch (e: IOException) {
            retryAt = now() + retryDelaysMs[minOf(failures, retryDelaysMs.lastIndex)]
            failures++
            // The answer shown is no longer good here; search again once the wait is over.
            shownAt = null
            if (stale == null) cache?.let { showFallback(it, lat, lon, shops) }
            throw e
        }
    }

    /** Shows the nearest kept answer that still covers the point, if any. */
    private fun showFallback(cache: SpotCache, lat: Double, lon: Double, shops: Set<SpotKind>) {
        cache.find(lat, lon, shops, FALLBACK_DISTANCE_M, SpotCache.MAX_AGE_MS, signals, facilityKinds)?.let { show(it, SpotSource.FALLBACK) }
    }

    /** False when the kept answer can't be read; the cache then just didn't have it. */
    private fun show(kept: SpotCache.Entry, from: SpotSource): Boolean {
        shownKept?.let { if (it.fetchedAt == kept.fetchedAt && it.center == kept.center) return true }
        val found = try {
            kept.body()?.let(overpass::parse)
        } catch (e: org.json.JSONException) {
            null
        } ?: return false
        show(found, from)
        shownKept = kept
        return true
    }

    private fun show(found: NearbyResult, from: SpotSource) {
        _spots.value = found.spots
        _facilities.value = found.facilities
        _signals.value = found.signals
        source = from
        updates++
    }

    companion object {
        private const val REFETCH_DISTANCE_M = 300.0

        /** A new answer replaces the kept ones whose searches overlap it most. */
        private const val REPLACE_DISTANCE_M = 150.0

        /** A kept answer searched 600m around its centre; past that it has nothing for here. */
        private const val FALLBACK_DISTANCE_M = 600.0

        /** How long to wait before asking Overpass again, after one failed search, two, and more. */
        internal val RETRY_DELAYS_MS = longArrayOf(30_000, 60_000, 120_000, 300_000)
    }
}
