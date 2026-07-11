package com.evsct.app.ui.map

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.util.Format
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.view.DefaultClusterRenderer
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

@OptIn(
    ExperimentalMaterial3Api::class,
    com.google.maps.android.compose.MapsComposeExperimentalApi::class,
)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onEditSession: (Long) -> Unit,
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
        if (pins.size == 1) {
            val p = pins.first()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(p.latitude, p.longitude), 12f,
            )
        } else {
            val bounds = LatLngBounds.Builder().apply {
                pins.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 6f)
        }
        hasFramedCamera = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Charging map", fontWeight = FontWeight.SemiBold)
                        val subtitle = subtitleFor(state)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                    isMyLocationEnabled = false,
                    mapType = mapTypeOf(state.mapType),
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = true,
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
        }
    }

    if (showFilters) {
        FilterSheet(
            ui = state,
            onToggleTrip = viewModel::toggleTripVisibility,
            onShowAllTrips = viewModel::showAllTrips,
            onHideAllTrips = viewModel::hideAllTrips,
            onSetVehicleFilter = viewModel::setVehicleFilter,
            onSetColorByTrip = viewModel::setColorByTrip,
            onSetClusteringEnabled = viewModel::setClusteringEnabled,
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
    onSetColorByTrip: (Boolean) -> Unit,
    onSetClusteringEnabled: (Boolean) -> Unit,
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

            // --- Color toggle ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Color pins by trip",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Off shows every pin in red instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ui.colorByTrip,
                    onCheckedChange = onSetColorByTrip,
                )
            }

            // --- Clustering toggle ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cluster nearby pins",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Off shows every pin individually regardless of zoom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ui.clusteringEnabled,
                    onCheckedChange = onSetClusteringEnabled,
                )
            }

            // --- Vehicle filter (hidden when there's only one vehicle to choose
            //     from, since the chips would be a no-op). ---
            if (ui.vehicles.size >= 2) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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

