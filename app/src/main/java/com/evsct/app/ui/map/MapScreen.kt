package com.evsct.app.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.ui.EvsctBarTitle
import com.evsct.app.util.Format
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import kotlin.math.ln
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    com.google.maps.android.compose.MapsComposeExperimentalApi::class,
)
@Composable
fun MapScreen(
    onEditSession: (Long) -> Unit,
    /** "Show only this trip" payload from the trip detail screen, read off
     *  this entry's SavedStateHandle by the nav graph (same relay as the
     *  Stats → Log brand drill-down). Null when no request is pending. */
    requestedTripFocusId: Long? = null,
    onTripFocusRequestConsumed: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.runBackfillIfNeeded() }

    var showFilters by remember { mutableStateOf(false) }
    var showLayersMenu by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        // Default view: roughly the centre of North America at a continent zoom.
        position = CameraPosition.fromLatLngZoom(LatLng(45.0, -98.0), 3f)
    }

    // The ClusterManager lives across recompositions; MapEffect(Unit) below
    // wires it up exactly once when the GoogleMap is first ready. Items and
    // the colorByTrip flag are pushed in via separate effects so changing
    // them doesn't tear down the manager.
    val context = LocalContext.current
    val clusterManager = remember {
        mutableStateOf<ClusterManager<ChargingStopClusterItem>?>(null)
    }
    val clusterRenderer = remember {
        mutableStateOf<ChargingStopClusterRenderer?>(null)
    }

    // My-location: the blue dot renders whenever permission is held; the
    // FAB below either jumps the camera to the current fix or requests the
    // permission first. The SDK's own button is disabled so there's exactly
    // one control, in one place, with a spinner while the fix is fetched.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    var locating by remember { mutableStateOf(false) }
    fun jumpToMyLocation() {
        if (locating) return
        scope.launch {
            locating = true
            val fix = viewModel.currentLatLng()
            locating = false
            if (fix != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(fix.first, fix.second), 14f),
                )
            } else {
                snackbarHostState.showSnackbar(
                    "Couldn't get a location fix. Check that location is turned on.",
                )
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        if (hasLocationPermission) {
            jumpToMyLocation()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Location permission is off — the map can't jump to where you are.",
                )
            }
        }
    }

    // Auto-frame on the first non-empty stops emission only. After that the
    // camera is the user's — toggling a trip filter, finishing a backfill,
    // or adding a session would otherwise yank the view away from wherever
    // the user just panned to. rememberSaveable preserves the "framed"
    // state across rotation but drops it when the screen is left and
    // reopened, so a fresh visit re-frames.
    var hasFramedCamera by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.stops) {
        if (hasFramedCamera) return@LaunchedEffect
        val pins = state.stops
        if (pins.isEmpty()) return@LaunchedEffect
        frameCameraOn(cameraPositionState, pins.map { it.latitude to it.longitude })
        hasFramedCamera = true
    }

    // Apply a trip detail "view on map" request once, then clear the handle
    // key so a later Map visit doesn't re-apply a stale focus. The camera is
    // framed explicitly from the coordinates focusTrip returns — flipping
    // hasFramedCamera back to false instead would race the auto-frame above,
    // whose next stops emission can still be the unfiltered set. Setting it
    // true afterwards keeps that effect from re-yanking a camera we just
    // aimed. Consumption happens only after focusTrip completes, so a visit
    // torn down mid-apply leaves the key in place for the next one. A trip
    // with no located stops applies the filter but leaves the camera alone —
    // the "hidden by filter" / backfill affordances take over from there.
    LaunchedEffect(requestedTripFocusId) {
        val tripId = requestedTripFocusId ?: return@LaunchedEffect
        val points = viewModel.focusTrip(tripId)
        onTripFocusRequestConsumed()
        if (points.isNotEmpty()) {
            frameCameraOn(cameraPositionState, points)
            hasFramedCamera = true
        }
    }

    // Geocode failures used to vanish silently — the backfill counted them
    // but nothing surfaced the number, so stops just never appeared. Tell
    // the user once per completed pass.
    LaunchedEffect(state.backfillCompleted, state.backfillFailed) {
        if (state.backfillCompleted && state.backfillFailed > 0) {
            val n = state.backfillFailed
            snackbarHostState.showSnackbar(
                message = (if (n == 1) "1 address couldn't be located"
                else "$n addresses couldn't be located") +
                    " — those stops stay off the map. Check the address, or open the " +
                    "session and use Pick on map.",
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // "Map" (the tab's label), not "Charging map": the brand
                    // lockup plus the long form overflows ahead of this bar's
                    // two actions on 360dp-wide phones, and the subtitle
                    // below already says what the map shows.
                    EvsctBarTitle("Map", subtitle = subtitleFor(state))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showLayersMenu = true }) {
                            Icon(Icons.Default.Layers, contentDescription = "Map type")
                        }
                        MapTypeMenu(
                            expanded = showLayersMenu,
                            current = state.mapType,
                            onSelect = { type ->
                                viewModel.setMapType(type)
                                showLayersMenu = false
                            },
                            onDismiss = { showLayersMenu = false },
                            heatmapEnabled = state.heatmapEnabled,
                            onToggleHeatmap = { enabled ->
                                viewModel.setHeatmapEnabled(enabled)
                                showLayersMenu = false
                            },
                            polylinesEnabled = state.polylinesEnabled,
                            onTogglePolylines = { enabled ->
                                viewModel.setPolylinesEnabled(enabled)
                                showLayersMenu = false
                            },
                            polylinesAvailable = !state.heatmapEnabled,
                            colorByTripEnabled = state.colorByTrip,
                            onToggleColorByTrip = { enabled ->
                                viewModel.setColorByTrip(enabled)
                                showLayersMenu = false
                            },
                            clusteringEnabled = state.clusteringEnabled,
                            onToggleClustering = { enabled ->
                                viewModel.setClusteringEnabled(enabled)
                                showLayersMenu = false
                            },
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(
                            badge = {
                                if (state.anyFilterActive) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary)
                                }
                            },
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = mapTypeOf(state.mapType),
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = true,
                    // Our FAB below replaces the SDK button, so the control
                    // exists (and can ask for permission) even before the
                    // permission is granted.
                    myLocationButtonEnabled = false,
                ),
            ) {
                // Construct the ClusterManager + custom renderer once, the
                // first time the GoogleMap is composed. We need MapEffect
                // (rather than the maps-compose-utils Clustering composable)
                // because the public Clustering(items, clusterManager)
                // overload doesn't accept callbacks or content — and we
                // want a custom DefaultClusterRenderer subclass to control
                // both the algorithm's maxDistanceBetweenClusteredItems
                // and the renderer's minClusterSize.
                MapEffect(Unit) { map ->
                    val mgr = ClusterManager<ChargingStopClusterItem>(context, map)
                    mgr.algorithm = NonHierarchicalDistanceBasedAlgorithm<ChargingStopClusterItem>().apply {
                        // 40px (down from 100 default): pins must be visually
                        // very close before they merge, so road-trip stops
                        // along a highway stay separate at country zoom.
                        maxDistanceBetweenClusteredItems = 40
                    }
                    val renderer = ChargingStopClusterRenderer(context, map, mgr).apply {
                        // 6 (up from 4): a 5-stop trip leg renders as 5
                        // individual pins instead of folding into a "5" badge.
                        minClusterSize = 6
                    }
                    mgr.renderer = renderer
                    // Click behaviour splits by stop type:
                    //  - 1 session  → return false so the default Maps info
                    //    window pops with brand + address + visit count (the
                    //    familiar "tooltip"). The info-window listener below
                    //    handles navigation on tap.
                    //  - N sessions → consume the click and open our custom
                    //    sheet so the user can pick which session to open.
                    mgr.setOnClusterItemClickListener { item ->
                        val ids = item.stop.sessionIds
                        if (ids.size > 1) {
                            viewModel.selectStop(item.stop)
                            true
                        } else {
                            false
                        }
                    }
                    // Tapping the info window of a single-session pin jumps
                    // to that session's edit screen. Multi-session pins never
                    // show the info window (consumed above), so this only
                    // fires for the single-session path.
                    mgr.setOnClusterItemInfoWindowClickListener { item ->
                        val ids = item.stop.sessionIds
                        if (ids.size == 1) onEditSession(ids.single())
                    }
                    map.setOnCameraIdleListener(mgr)
                    map.setOnMarkerClickListener(mgr)
                    map.setOnInfoWindowClickListener(mgr)
                    clusterManager.value = mgr
                    clusterRenderer.value = renderer
                }

                // Trip route polylines. Drawn beneath markers so pins stay
                // tappable, and suppressed when heatmap mode is on so the
                // two display modes don't overlap into visual noise.
                if (state.polylinesEnabled && !state.heatmapEnabled) {
                    state.tripPolylines.forEach { polyline ->
                        val color = TripPinColor.fromKey(polyline.pinColorKey)?.swatch
                            ?: Color(0xFF6E6E6E)
                        Polyline(
                            points = polyline.points.map { (lat, lng) -> LatLng(lat, lng) },
                            color = color,
                            width = 8f,
                            geodesic = true,
                        )
                    }
                }

                // Heatmap overlay. Built only when the user has flipped to
                // heatmap mode AND there's at least one stop to weight —
                // HeatmapTileProvider rejects empty data sets. Visit count
                // drives intensity so frequently-used home / commute
                // chargers burn brighter than one-off road-trip stops —
                // but log-compressed: the tile provider normalizes its
                // color ramp to the hottest spot, so a raw 50-visit home
                // stop pushes every 1-visit road-trip stop below the
                // gradient's visible threshold and the map renders as a
                // single blob. ln keeps home hottest while one-offs stay
                // at ~20% intensity instead of ~2%.
                if (state.heatmapEnabled && state.stops.isNotEmpty()) {
                    val tileProvider = remember(state.stops) {
                        HeatmapTileProvider.Builder()
                            .weightedData(
                                state.stops.map {
                                    WeightedLatLng(
                                        LatLng(it.latitude, it.longitude),
                                        1.0 + ln(it.visits.toDouble().coerceAtLeast(1.0)),
                                    )
                                },
                            )
                            .build()
                    }
                    TileOverlay(tileProvider = tileProvider, fadeIn = true)
                }
            }
            // Push items into the manager whenever the visible stops or the
            // colorByTrip toggle changes. The renderer caches markers and
            // only consults onBeforeClusterItemRendered on first creation —
            // so a colorByTrip flip needs a full clearItems/addItems pass
            // to force the icon to repaint, not just cluster(). When the
            // heatmap is on we leave the manager empty so pins disappear
            // entirely while the overlay is the only thing on the map.
            LaunchedEffect(
                state.stops,
                state.colorByTrip,
                state.clusteringEnabled,
                state.heatmapEnabled,
                clusterManager.value,
            ) {
                val mgr = clusterManager.value ?: return@LaunchedEffect
                val renderer = clusterRenderer.value
                renderer?.colorByTrip = state.colorByTrip
                renderer?.clusteringEnabled = state.clusteringEnabled
                mgr.clearItems()
                if (!state.heatmapEnabled) {
                    mgr.addItems(state.stops.map { ChargingStopClusterItem(it) })
                }
                mgr.cluster()
            }

            if (state.backfillRunning) {
                BackfillBanner(text = "Locating ${state.unlocatedDistinct} stops…")
            }
            // isLoading: before the first emission every log looks empty —
            // let the basemap show alone rather than flash "No locations".
            if (!state.isLoading && state.stops.isEmpty() && !state.backfillRunning) {
                EmptyState(
                    message = when {
                        state.totalDistinct == 0 ->
                            "No locations to map yet. Add a session with GPS autofill or a real address."
                        state.anyFilterActive ->
                            "Every stop is hidden by your current filter."
                        else ->
                            "Could not locate any stops. Check that addresses are filled in."
                    },
                )
            }

            // What the pin colors mean, at a glance. Only rendered when the
            // colors are actually in play (colorByTrip on, pins visible, at
            // least one visible trip); tapping it opens the filter sheet
            // where the same trips can be toggled.
            val legendEntries = legendEntriesFor(state)
            if (state.colorByTrip && !state.heatmapEnabled && legendEntries.isNotEmpty()) {
                TripLegendOverlay(
                    entries = legendEntries,
                    onClick = { showFilters = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 16.dp),
                )
            }

            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        jumpToMyLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                if (locating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = "My location")
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            ui = state,
            onToggleTrip = viewModel::toggleTripVisibility,
            onShowAllTrips = viewModel::showAllTrips,
            onHideAllTrips = viewModel::hideAllTrips,
            onSetVehicleFilter = viewModel::setVehicleFilter,
            onResetAll = viewModel::resetFilters,
            onDismiss = { showFilters = false },
        )
    }

    state.selectedStop?.let { stop ->
        StopSessionsSheet(
            stop = stop,
            sessions = state.selectedStopSessions,
            tripNamesById = state.tripNamesById,
            vehicleNamesById = state.vehicles.associate { it.id to it.name },
            showVehicle = state.vehicles.size >= 2,
            onPick = { id ->
                viewModel.selectStop(null)
                onEditSession(id)
            },
            onDismiss = { viewModel.selectStop(null) },
        )
    }
}

/** Snap the camera to (lat, lng) [points]: a single point gets a street-ish
 *  zoom, a spread gets its bounds' centre at a regional zoom. Shared by the
 *  first-emission auto-frame and the trip-focus jump. */
private fun frameCameraOn(
    cameraPositionState: CameraPositionState,
    points: List<Pair<Double, Double>>,
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        val (lat, lng) = points.first()
        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 12f)
    } else {
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { (lat, lng) -> include(LatLng(lat, lng)) }
        }.build()
        cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 6f)
    }
}

/** (label, dot color) rows for the on-map legend: each visible trip, then
 *  the untripped bucket, then the shared-stop gray when any pin uses it.
 *  Empty when no visible trip is contributing pins — a legend that only
 *  says "Untripped" would be noise. */
private fun legendEntriesFor(ui: MapUi): List<Pair<String, Color>> {
    if (ui.stops.isEmpty()) return emptyList()
    val visibleTrips = ui.tripOptions.filter { it.visible }
    if (visibleTrips.isEmpty()) return emptyList()
    return buildList {
        visibleTrips.forEach { option ->
            add(option.name to (TripPinColor.fromKey(option.pinColorKey)?.swatch ?: SHARED_PIN_COLOR))
        }
        if (ui.showUntrippedOption && ui.untrippedVisible) add("Untripped" to UNTRIPPED_PIN_COLOR)
        if (ui.stops.any { it.pinKind == PinKind.Shared }) add("Multiple trips" to SHARED_PIN_COLOR)
    }
}

/** Compact translucent legend card overlaid on the map. Shows at most
 *  [MAX_LEGEND_ROWS] rows plus a "+N more" line; the full list lives in
 *  the filter sheet, which tapping the legend opens. */
@Composable
private fun TripLegendOverlay(
    entries: List<Pair<String, Color>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.widthIn(max = 180.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.take(MAX_LEGEND_ROWS).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entries.size > MAX_LEGEND_ROWS) {
                Text(
                    "+${entries.size - MAX_LEGEND_ROWS} more…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_LEGEND_ROWS = 6

/** Fallback/shared gray — matches the shared-stop pin bitmap. */
private val SHARED_PIN_COLOR = Color(0xFF6E6E6E)

/** The Maps SDK's default red marker, which untripped stops keep. */
private val UNTRIPPED_PIN_COLOR = Color(0xFFE53935)

private fun subtitleFor(ui: MapUi): String? {
    if (ui.stops.isEmpty() && ui.totalDistinct == 0) return null
    return buildString {
        append("${ui.stops.size} location")
        if (ui.stops.size != 1) append("s")
        if (ui.unlocatedDistinct > 0 && ui.backfillCompleted) {
            append(" · ${ui.unlocatedDistinct} unlocated")
        }
    }
}

private fun snippetFor(stop: MapStop): String {
    val parts = listOfNotNull(
        stop.address?.takeIf { it.isNotBlank() },
        listOfNotNull(
            stop.city?.takeIf { it.isNotBlank() },
            stop.province?.takeIf { it.isNotBlank() },
        ).joinToString(", ").takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val visits = "${stop.visits} visit" + if (stop.visits == 1) "" else "s"
    return if (parts.isBlank()) visits else "$parts · $visits"
}

@Composable
private fun BackfillBanner(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Adapter so the underlying ClusterManager can read position / title /
 *  snippet from a [MapStop]. */
private class ChargingStopClusterItem(val stop: MapStop) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(stop.latitude, stop.longitude)
    override fun getTitle(): String? = stop.brand?.takeIf { it.isNotBlank() }
        ?: stop.stationName?.takeIf { it.isNotBlank() }
        ?: "Charging stop"
    override fun getSnippet(): String? = snippetFor(stop)
    override fun getZIndex(): Float? = null
}

/** Custom cluster renderer that paints individual pins with the SDK's
 *  iconic colored markers (trip-colored when colorByTrip is on, the shared
 *  gray bitmap for multi-trip stops, default red otherwise). The default
 *  cluster-badge appearance is left intact — only the per-item icons are
 *  overridden. [colorByTrip] is mutable so the screen can flip filter mode
 *  without rebuilding the renderer. */
private class ChargingStopClusterRenderer(
    context: android.content.Context,
    map: com.google.android.gms.maps.GoogleMap,
    clusterManager: ClusterManager<ChargingStopClusterItem>,
) : DefaultClusterRenderer<ChargingStopClusterItem>(context, map, clusterManager) {

    var colorByTrip: Boolean = true
    var clusteringEnabled: Boolean = true

    private val sharedIcon: BitmapDescriptor by lazy { sharedTripPinDescriptor() }

    override fun onBeforeClusterItemRendered(
        item: ChargingStopClusterItem,
        markerOptions: com.google.android.gms.maps.model.MarkerOptions,
    ) {
        iconFor(item.stop.pinKind, sharedIcon, colorByTrip)?.let {
            markerOptions.icon(it)
        }
        item.title?.let { markerOptions.title(it) }
        item.snippet?.let { markerOptions.snippet(it) }
    }

    override fun shouldRenderAsCluster(
        cluster: com.google.maps.android.clustering.Cluster<ChargingStopClusterItem>,
    ): Boolean =
        clusteringEnabled && super.shouldRenderAsCluster(cluster)
}

/** Pick the SDK marker icon for a given pin kind. Returns null to fall
 *  through to the default red marker (saves a per-frame BitmapDescriptor
 *  for the common case). */
private fun iconFor(
    kind: PinKind,
    sharedIcon: BitmapDescriptor,
    colorByTrip: Boolean,
): BitmapDescriptor? {
    if (!colorByTrip) return null  // All-red mode: default marker for everything.
    return when (kind) {
        PinKind.Untripped -> null  // Default red marker.
        is PinKind.SingleTrip -> {
            val color = TripPinColor.fromKey(kind.tripPinColorKey)
            if (color == null) null else BitmapDescriptorFactory.defaultMarker(color.mapsHue)
        }
        PinKind.Shared -> sharedIcon
    }
}

/** A small gray pin used for stops visited across multiple trips. Built
 *  once and reused since BitmapDescriptors are expensive to create. */
private fun sharedTripPinDescriptor(): BitmapDescriptor {
    val sizePx = 64
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2.4f
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6E6E6E.toInt()
    }
    canvas.drawCircle(cx, cy, radius, fill)
    val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, radius, ring)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    ui: MapUi,
    onToggleTrip: (Long?) -> Unit,
    onShowAllTrips: () -> Unit,
    onHideAllTrips: () -> Unit,
    onSetVehicleFilter: (Long?) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            // Scrollable: a long trip list would otherwise push the lower
            // rows (and Show all / Hide all) off the bottom of the sheet.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Filter map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (ui.anyFilterActive) {
                    TextButton(onClick = onResetAll) { Text("Reset") }
                }
            }
            // NOTE: display-mode toggles (trip colors, clustering, heatmap,
            // routes) live in the Layers menu — this sheet only decides
            // WHICH stops show, not how the map looks.

            // --- Vehicle filter (hidden when there's only one vehicle to choose
            //     from, since the chips would be a no-op). ---
            if (ui.vehicles.size >= 2) {
                Text(
                    "Show pins for vehicle",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.vehicleFilterId == null,
                        onClick = { onSetVehicleFilter(null) },
                        label = { Text("All vehicles") },
                    )
                    ui.vehicles.forEach { vehicle ->
                        FilterChip(
                            selected = ui.vehicleFilterId == vehicle.id,
                            onClick = { onSetVehicleFilter(vehicle.id) },
                            label = { Text(vehicle.name) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // --- Trip visibility list ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show pins from",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                val anyHidden = ui.tripOptions.any { !it.visible } ||
                    (ui.showUntrippedOption && !ui.untrippedVisible)
                val anyVisible = ui.tripOptions.any { it.visible } ||
                    (ui.showUntrippedOption && ui.untrippedVisible)
                if (anyHidden) {
                    TextButton(onClick = onShowAllTrips) { Text("Show all") }
                }
                if (anyVisible && (ui.tripOptions.isNotEmpty() || ui.showUntrippedOption)) {
                    TextButton(onClick = onHideAllTrips) { Text("Hide all") }
                }
            }

            if (ui.tripOptions.isEmpty() && !ui.showUntrippedOption) {
                Text(
                    "No trips to filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                ui.tripOptions.forEach { option ->
                    TripFilterRow(
                        label = option.name,
                        swatch = TripPinColor.fromKey(option.pinColorKey)?.swatch,
                        checked = option.visible,
                        onToggle = { onToggleTrip(option.tripId) },
                    )
                }
                if (ui.showUntrippedOption) {
                    TripFilterRow(
                        label = "Untripped sessions",
                        swatch = null,  // Renders as the default-red dot.
                        checked = ui.untrippedVisible,
                        onToggle = { onToggleTrip(null) },
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet that lists every session sharing the tapped pin. Used to
 * disambiguate "which session at this stop is mis-tagged" — each row shows
 * the session date, energy, trip badge (or Untripped), and the vehicle
 * name when there's more than one vehicle in the garage. Tap a row to jump
 * to its edit screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSessionsSheet(
    stop: MapStop,
    sessions: List<ChargingSession>,
    tripNamesById: Map<Long, String>,
    vehicleNamesById: Map<Long, String>,
    showVehicle: Boolean,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    stop.brand?.takeIf { it.isNotBlank() }
                        ?: stop.stationName?.takeIf { it.isNotBlank() }
                        ?: "Charging stop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val locationLine = listOfNotNull(
                    stop.address?.takeIf { it.isNotBlank() },
                    listOfNotNull(
                        stop.city?.takeIf { it.isNotBlank() },
                        stop.province?.takeIf { it.isNotBlank() },
                    ).joinToString(", ").takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (locationLine.isNotEmpty()) {
                    Text(
                        locationLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val plural = if (sessions.size == 1) "" else "s"
                Text(
                    "${sessions.size} session$plural — tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            sessions.forEach { session ->
                StopSessionRow(
                    session = session,
                    tripName = session.tripId?.let { tripNamesById[it] },
                    vehicleName = if (showVehicle) {
                        session.vehicleId?.let { vehicleNamesById[it] }
                    } else null,
                    onClick = { onPick(session.id) },
                )
            }
        }
    }
}

@Composable
private fun StopSessionRow(
    session: ChargingSession,
    tripName: String?,
    vehicleName: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Format.dateTime(session.sessionStart),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                Format.kwh(session.energyKwh),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StopTripBadge(tripName)
            if (vehicleName != null) {
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StopTripBadge(tripName: String?) {
    val container = if (tripName != null) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (tripName != null) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            tripName ?: "Untripped",
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
        )
    }
}

@Composable
private fun TripFilterRow(
    label: String,
    swatch: Color?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatch ?: Color(0xFFE53935)),  // Untripped == red.
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

