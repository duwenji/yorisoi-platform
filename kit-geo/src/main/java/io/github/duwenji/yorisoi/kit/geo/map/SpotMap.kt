package io.github.duwenji.yorisoi.kit.geo.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.duwenji.yorisoi.kit.geo.R
import io.github.duwenji.yorisoi.kit.geo.Facility
import io.github.duwenji.yorisoi.kit.geo.FacilityKind
import io.github.duwenji.yorisoi.kit.geo.LatLon
import io.github.duwenji.yorisoi.kit.geo.Poi
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.TileDownloader
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

/** Marker for each facility kind; the colors double as the legend in the nearest-facilities bar. */
fun facilityDot(kind: FacilityKind) = when (kind) {
    FacilityKind.TOILETS -> R.drawable.facility_toilet_dot
    FacilityKind.DRINKING_WATER -> R.drawable.facility_water_dot
    FacilityKind.VENDING_MACHINE -> R.drawable.facility_vending_dot
    FacilityKind.SHELTER -> R.drawable.facility_shelter_dot
    FacilityKind.BENCH -> R.drawable.facility_bench_dot
    FacilityKind.BICYCLE_PARKING -> R.drawable.facility_bicycle_parking_dot
    FacilityKind.CONVENIENCE_STORE -> R.drawable.facility_convenience_dot
    FacilityKind.BICYCLE_SHOP -> R.drawable.facility_bicycle_shop_dot
    FacilityKind.BICYCLE_PUMP -> R.drawable.facility_bicycle_pump_dot
}

/**
 * The fill of each [facilityDot], for Compose: painterResource can't load `<shape>` drawables.
 * Keep the two in step.
 */
fun facilityColor(kind: FacilityKind) = when (kind) {
    FacilityKind.TOILETS -> Color(0xFF1565C0)
    FacilityKind.DRINKING_WATER -> Color(0xFF00838F)
    FacilityKind.VENDING_MACHINE -> Color(0xFFEF6C00)
    FacilityKind.SHELTER -> Color(0xFF6A1B9A)
    FacilityKind.BENCH -> Color(0xFF8D6E63)
    FacilityKind.BICYCLE_PARKING -> Color(0xFF455A64)
    FacilityKind.CONVENIENCE_STORE -> Color(0xFF2E7D32)
    FacilityKind.BICYCLE_SHOP -> Color(0xFFC62828)
    FacilityKind.BICYCLE_PUMP -> Color(0xFFAD1457)
}

/**
 * Map showing the user's position (with the way they face, [heading]), nearby spots in their
 * [SpotGroup] colors and amenities, the way already [walked] on this walk, and the way to the
 * spot being guided to. Pans to [focus] when it changes.
 * [onViewportChanged] reports the area on screen (zoom, north, south, east, west) once the
 * map settles, for attributions that depend on it.
 * The [selected] spot is drawn larger and on top, the others faded, and the map pans to it when
 * it changes. Each change of [recenterRequests] pans back to the user's position.
 * With [fitWalked], the map shows the whole of [walked] each time it changes, as when looking
 * back on a walk with no [location].
 * With [up], the map turns so that bearing (degrees clockwise from north) is at the top, and a
 * small arrow shows north; null keeps north up.
 * [onTilesFailed] is called when OpenStreetMap tiles keep failing to download, so the screen
 * can switch to other tiles.
 */
@Composable
fun SpotMap(
    tiles: MapTiles,
    location: Location?,
    heading: Float?,
    spots: List<SpotItem>,
    facilities: List<FacilityItem>,
    walked: List<LatLon>,
    route: RouteToSpot?,
    focus: Facility?,
    onSpotClick: (SpotItem) -> Unit,
    onViewportChanged: (zoom: Int, north: Double, south: Double, east: Double, west: Double) -> Unit,
    modifier: Modifier = Modifier,
    selected: Poi? = null,
    recenterRequests: Int = 0,
    fitWalked: Boolean = false,
    up: Float? = null,
    onTilesFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val meIcon = remember { ContextCompat.getDrawable(context, R.drawable.me_dot) }
    val facilityIcons = remember { FacilityKind.entries.associateWith { ContextCompat.getDrawable(context, facilityDot(it)) } }
    val reportViewport by rememberUpdatedState(onViewportChanged)
    val tilesFailed by rememberUpdatedState(onTilesFailed)
    val mapView = remember {
        // MapView settles the tile cache's folder before making its own provider; this one needs it too.
        Configuration.getInstance().getOsmdroidTileCache(context)
        val provider = WatchedTileProvider(context.applicationContext)
        MapView(context, provider).apply {
            provider.onOsmFailing { post { tilesFailed() } }
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    reportViewportOf(this@apply, reportViewport)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    reportViewportOf(this@apply, reportViewport)
                    return false
                }
            }, VIEWPORT_SETTLE_MS))
        }
    }
    val copyright = remember { CopyrightOverlay(context) }
    val north = remember { NorthArrowOverlay(density) }
    val markers = remember { MapMarkers() }
    // Recenter only when the user has moved noticeably, so panning isn't constantly undone.
    val centeredAt = remember { arrayOfNulls<Location>(1) }
    val focusedOn = remember { arrayOfNulls<Facility>(1) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        // osmdroid turns the map counterclockwise by its orientation.
        val orientation = up?.let { -it } ?: 0f
        if (orientation != map.mapOrientation) map.mapOrientation = orientation
        if (tiles != markers.tiles) {
            map.setTileSource(tiles.tileSource())
            markers.tiles = tiles
            // The new tiles may need an attribution for the area already on screen.
            map.post { reportViewportOf(map, reportViewport) }
        }
        // Distances change with every location update; the markers only when the places do,
        // so a bubble the user opened stays open while they walk.
        // Sets: the lists are sorted by distance, so walking reorders them without changing them.
        val facilityIds = facilities.map { it.facility.id }.toSet()
        if (facilityIds != markers.facilityIds) {
            markers.facilities.forEach { it.closeInfoWindow() }
            markers.facilities = facilities.map { item ->
                val f = item.facility
                Marker(map).apply {
                    position = GeoPoint(f.lat, f.lon)
                    icon = facilityIcons[f.kind]
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = f.name?.let { "${f.kind.label}（$it）" } ?: f.kind.label
                }
            }
            markers.facilityIds = facilityIds
        }
        markers.spotsById = spots.associateBy { it.poi.id }
        markers.onSpotClick = onSpotClick
        val spotIds = spots.map { it.poi.id }.toSet()
        if (spotIds != markers.spotIds) {
            markers.spots = spots.map { item ->
                val id = item.poi.id
                Marker(map).apply {
                    this.id = id
                    position = GeoPoint(item.poi.lat, item.poi.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    // Looked up on tap, so the sheet gets the current distance and visit count.
                    setOnMarkerClickListener { _, _ -> markers.spotsById[id]?.let(markers.onSpotClick); true }
                }
            }
            markers.spotIds = spotIds
        }
        // Colors and the highlight change without the places changing.
        val selectedId = selected?.id
        markers.spots.forEach { marker ->
            val item = markers.spotsById[marker.id] ?: return@forEach
            marker.icon = markers.spotIcon(context, SpotGroup.of(item.poi.category), marker.id == selectedId)
            marker.alpha = if (selectedId == null || marker.id == selectedId) 1f else FADED_ALPHA
        }

        map.overlays.clear()
        // Under the spots, so a bench in a park doesn't cover the park.
        map.overlays += markers.facilities
        // A single point is only where the walk began; the position dot already shows it.
        if (walked.size >= 2) {
            val track = markers.walked ?: Polyline(map).apply {
                outlinePaint.color = WALKED_COLOR
                outlinePaint.strokeWidth = 4 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            }.also { markers.walked = it }
            // The list is replaced only when the walk has grown, so this skips most updates.
            if (walked !== markers.walkedPoints) {
                track.setPoints(walked.map { GeoPoint(it.lat, it.lon) })
                markers.walkedPoints = walked
            }
            // Under the way ahead, which matters more while walking.
            map.overlays += track
            if (fitWalked && walked !== markers.fittedTo) {
                fitTo(map, walked, density)
                markers.fittedTo = walked
            }
        }
        if (route != null) {
            val line = markers.route ?: Polyline(map).apply {
                outlinePaint.color = ROUTE_COLOR
                outlinePaint.strokeWidth = 5 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
            }.also { markers.route = it }
            // Dashed when it's only the direction, not a way along paths.
            line.outlinePaint.pathEffect = if (route.route.onPaths) null else DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
            line.setPoints(route.route.points.map { GeoPoint(it.lat, it.lon) })
            map.overlays += line
        }
        // The selected spot last, so no other marker covers it.
        map.overlays += markers.spots.sortedBy { it.id == selectedId }
        if (location != null) {
            val me = markers.me ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                // Turns with the map, so the beam drawn from north still points the way faced.
                isFlat = true
            }.also { markers.me = it }
            me.icon = heading?.let { markers.headingIcon(context, it) } ?: meIcon
            me.position = GeoPoint(location.latitude, location.longitude)
            map.overlays += me
            val last = centeredAt[0]
            val asked = recenterRequests != markers.recenterRequests
            markers.recenterRequests = recenterRequests
            if (asked || last == null || last.distanceTo(location) > 150f) {
                map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                centeredAt[0] = location
            }
        }
        if (focus != null && focus != focusedOn[0]) {
            map.controller.animateTo(GeoPoint(focus.lat, focus.lon))
            focusedOn[0] = focus
        }
        if (selectedId != markers.selectedId) {
            selected?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            markers.selectedId = selectedId
        }
        // Google's attribution changes with the area and comes with its logo; the screen draws both.
        if (tiles !is MapTiles.Google) map.overlays += copyright
        if (up != null) map.overlays += north
        map.invalidate()
    }
}

/** osmdroid's tile provider, with the downloads of OpenStreetMap tiles watched for a run of failures. */
private class WatchedTileProvider(context: Context) : MapTileProviderBasic(context) {
    // Set while the superclass constructor runs; having no initializer, it isn't reset afterwards.
    private lateinit var downloader: MapTileDownloader

    override fun createDownloaderProvider(check: INetworkAvailablityCheck?, source: ITileSource?): MapTileDownloader =
        super.createDownloaderProvider(check, source).also { downloader = it }

    /** [report] runs on a download thread. Offline, osmdroid doesn't try, so nothing is counted. */
    fun onOsmFailing(report: () -> Unit) {
        val streak = FailureStreak()
        downloader.setTileDownloader(object : TileDownloader() {
            override fun downloadTile(index: Long, cache: IFilesystemCache?, source: OnlineTileSourceBase?): Drawable? {
                val tile = super.downloadTile(index, cache, source)
                if (source === TileSourceFactory.MAPNIK && streak.record(tile != null)) report()
                return tile
            }
        })
    }
}

/** An arrow at the top right pointing north, for a map turned away from north up. */
private class NorthArrowOverlay(private val density: Float) : Overlay() {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
    // So the disc stands out on pale tiles.
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9E9E9E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC62828.toInt() }
    private val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E9E9E.toInt() }

    override fun draw(canvas: Canvas, projection: Projection) {
        val r = 16 * density
        val cx = projection.width - 8 * density - r
        val cy = 8 * density + r
        // Undo the map's turn, to draw on the screen as it is.
        projection.save(canvas, false, false)
        canvas.save()
        canvas.rotate(projection.orientation, cx, cy)
        canvas.drawCircle(cx, cy, r, disc)
        canvas.drawCircle(cx, cy, r, rim)
        val w = r * 0.36f
        canvas.drawPath(Path().apply { moveTo(cx, cy - r * 0.7f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }, tip)
        canvas.drawPath(Path().apply { moveTo(cx, cy + r * 0.7f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }, tail)
        canvas.restore()
        projection.restore(canvas, false)
    }
}

/** Markers kept across map updates; rebuilt only when the set of places changes. */
private class MapMarkers {
    var facilityIds: Set<String>? = null
    var facilities: List<Marker> = emptyList()
    var spotIds: Set<String>? = null
    var spots: List<Marker> = emptyList()
    var spotsById: Map<String, SpotItem> = emptyMap()
    var onSpotClick: (SpotItem) -> Unit = {}
    var me: Marker? = null
    var route: Polyline? = null
    var walked: Polyline? = null
    var walkedPoints: List<LatLon>? = null
    var fittedTo: List<LatLon>? = null
    var tiles: MapTiles? = null
    var selectedId: String? = null
    var recenterRequests = 0
    private val headingIcons = HashMap<Int, Drawable>()
    private val spotIcons = HashMap<Pair<SpotGroup, Boolean>, Drawable>()

    /** A spot's dot in its group's color; made on first use. */
    fun spotIcon(context: Context, group: SpotGroup, selected: Boolean): Drawable =
        spotIcons.getOrPut(group to selected) { drawSpotIcon(context, group.color.toArgb(), selected) }

    /** The position dot with a beam toward [degrees]; one per [HEADING_STEP] degrees, made on first use. */
    fun headingIcon(context: Context, degrees: Float): Drawable {
        val step = ((degrees / HEADING_STEP).roundToInt() * HEADING_STEP) % 360
        return headingIcons.getOrPut(step) { drawHeadingIcon(context, step.toFloat()) }
    }
}

private const val HEADING_STEP = 5
/** The other spots while one is selected: still there to tap, but behind it. */
private const val FADED_ALPHA = 0.45f
/** Wait until panning or zooming pauses before asking for the attribution of the new area. */
private const val VIEWPORT_SETTLE_MS = 600L

/** Zooms [map] to show all of [points], once it has a size. */
private fun fitTo(map: MapView, points: List<LatLon>, density: Float) {
    val box = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.lat, it.lon) })
    val border = (FIT_BORDER_DP * density).roundToInt()
    if (map.isLayoutOccurred) {
        map.zoomToBoundingBox(box, false, border)
    } else {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, border) }
    }
}

/** Room around the way when the map fits it, so its ends aren't on the edge. */
private const val FIT_BORDER_DP = 32

private fun reportViewportOf(
    map: MapView, report: (zoom: Int, north: Double, south: Double, east: Double, west: Double) -> Unit,
) {
    if (map.width == 0 || map.height == 0) return
    val box = map.boundingBox
    report(map.zoomLevelDouble.toInt(), box.latNorth, box.latSouth, box.lonEast, box.lonWest)
}
private const val ROUTE_COLOR = 0xCC1E88E5.toInt()
/** The way walked: apart from the blue of the way ahead and the position dot. */
private const val WALKED_COLOR = 0xB3D81B60.toInt()

/** A filled circle with a white rim: 16dp, or 26dp with a thicker rim when [selected]. */
private fun drawSpotIcon(context: Context, color: Int, selected: Boolean): Drawable {
    val d = context.resources.displayMetrics.density
    val size = ((if (selected) 26 else 16) * d).roundToInt()
    val rim = (if (selected) 3 else 2) * d
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawCircle(c, c, c, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    canvas.drawCircle(c, c, c - rim, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    return BitmapDrawable(context.resources, bitmap)
}

/** Same dot as `me_dot`, with a fading beam behind it. Canvas rotation is clockwise, like compass degrees. */
private fun drawHeadingIcon(context: Context, degrees: Float): Drawable {
    val d = context.resources.displayMetrics.density
    val size = (56 * d).roundToInt()
    val c = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.save()
    canvas.rotate(degrees, c, c)
    val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(c, c, c, 0x991E88E5.toInt(), 0x001E88E5, Shader.TileMode.CLAMP)
    }
    canvas.drawArc(RectF(0f, 0f, size.toFloat(), size.toFloat()), -90f - 30f, 60f, true, beam)
    canvas.restore()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3 * d
    }
    val r = 10 * d - stroke.strokeWidth / 2
    canvas.drawCircle(c, c, r, fill)
    canvas.drawCircle(c, c, r, stroke)
    return BitmapDrawable(context.resources, bitmap)
}
