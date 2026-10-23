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

    /** A kept answer from nearby, new enough to use without asking ([SpotCache.FRESH_MS]). */
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
 * again, and are what's shown when they fail. After a failed search, the next waits a while
 * ([RETRY_DELAYS_MS]) rather than asking again at the next location.
 */
class SpotRepository(
    private val overpass: OverpassClient,
    private val shops: () -> Set<SpotKind> = { emptySet() },
    private val cache: SpotCache? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _spots = MutableStateFlow<List<Poi>>(emptyList())
    val spots: StateFlow<List<Poi>> = _spots.asStateFlow()

    private val _facilities = MutableStateFlow<List<Facility>>(emptyList())
    val facilities: StateFlow<List<Facility>> = _facilities.asStateFlow()

    /** Where the spots shown came from; null before the first search. */
    @Volatile
    var source: SpotSource? = null
        private set

    /** How many times the spots shown have been replaced; a change across [onLocation] means it searched. */
    @Volatile
    var updates: Int = 0
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
     */
    suspend fun onLocation(location: Location, forceRefresh: Boolean = false) {
        _location.value = location
        search(location.latitude, location.longitude, forceRefresh)
    }

    /** Searches again where the user is, if the channel's shops changed (a switch of channel). */
    suspend fun onShopsChanged() {
        _location.value?.let { onLocation(it) }
    }

    internal suspend fun search(lat: Double, lon: Double, forceRefresh: Boolean = false) = fetchLock.withLock {
        val here = LatLon(lat, lon)
        val shops = shops()
        val shown = shownAt
        if (!forceRefresh && shown != null && shops == lastShops && RouteGeometry.distanceM(shown, here) <= REFETCH_DISTANCE_M) return@withLock
        if (!forceRefresh) {
            cache?.find(lat, lon, shops, REFETCH_DISTANCE_M, SpotCache.FRESH_MS)?.let { kept ->
                if (show(kept, SpotSource.CACHE)) {
                    shownAt = kept.center
                    lastShops = shops
                    return@withLock
                }
            }
            if (now() < retryAt) {
                showFallback(lat, lon, shops)
                return@withLock
            }
        }
        val stale = cache?.find(lat, lon, shops, REFETCH_DISTANCE_M, SpotCache.MAX_AGE_MS)?.takeIf { show(it, SpotSource.STALE) }
        try {
            val body = overpass.fetch(lat, lon, shops = shops)
            show(overpass.parse(body), SpotSource.NETWORK)
            shownKept = null
            cache?.put(lat, lon, shops, body, replaceWithinM = REPLACE_DISTANCE_M)
            shownAt = here
            lastShops = shops
            failures = 0
            retryAt = 0L
        } catch (e: IOException) {
            retryAt = now() + RETRY_DELAYS_MS[minOf(failures, RETRY_DELAYS_MS.lastIndex)]
            failures++
            // The answer shown is no longer good here; search again once the wait is over.
            shownAt = null
            if (stale == null) showFallback(lat, lon, shops)
            throw e
        }
    }

    /** Shows the nearest kept answer that still covers the point, if any. */
    private fun showFallback(lat: Double, lon: Double, shops: Set<SpotKind>) {
        cache?.find(lat, lon, shops, FALLBACK_DISTANCE_M, SpotCache.MAX_AGE_MS)?.let { show(it, SpotSource.FALLBACK) }
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
