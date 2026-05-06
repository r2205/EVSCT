package com.evsct.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
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
                // Cluster nearby stops at low zoom so 30 chargers in one
                // city don't overlap into a blob. The library's default
                // cluster badge (a tinted circle with the count) appears
                // when stops are close enough at the current zoom; tapping
                // a cluster animates a zoom-in. Individual pins keep
                // their trip-color tint via the Compose icon below.
                Clustering(
                    items = state.stops.map { ChargingStopClusterItem(it) },
                    onClusterClick = { false /* false → SDK default zoom-in */ },
                    onClusterItemClick = { false /* false → SDK shows info window */ },
                    clusterItemContent = { item ->
                        ChargingStopPin(item.stop.pinKind, state.colorByTrip)
                    },
                )
            }

            if (state.backfillRunning) {
                BackfillBanner(text = "Locating ${state.unlocatedDistinct} stops…")
            }
            if (state.stops.isEmpty() && !state.backfillRunning) {
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
            onSetColorByTrip = viewModel::setColorByTrip,
            onResetAll = viewModel::resetFilters,
            onDismiss = { showFilters = false },
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

/** Convert the persisted map-type string into the Maps Compose enum. Falls
 *  back to NORMAL on anything unrecognised. */
private fun mapTypeOf(value: String): MapType = when (value) {
    "SATELLITE" -> MapType.SATELLITE
    "HYBRID" -> MapType.HYBRID
    "TERRAIN" -> MapType.TERRAIN
    else -> MapType.NORMAL
}

private data class MapTypeOption(val key: String, val label: String)

private val MAP_TYPE_OPTIONS = listOf(
    MapTypeOption("NORMAL", "Default"),
    MapTypeOption("SATELLITE", "Satellite"),
    MapTypeOption("HYBRID", "Hybrid"),
    MapTypeOption("TERRAIN", "Terrain"),
)

@Composable
private fun MapTypeMenu(
    expanded: Boolean,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MAP_TYPE_OPTIONS.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(option.key) },
                trailingIcon = {
                    if (option.key == current) {
                        Icon(Icons.Default.Check, contentDescription = "Selected")
                    }
                },
            )
        }
    }
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

/** Cluster-item adapter so the maps-compose-utils Clustering composable can
 *  read position / title / snippet from a [MapStop] without us hand-rolling
 *  the lower-level ClusterManager API. */
private class ChargingStopClusterItem(val stop: MapStop) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(stop.latitude, stop.longitude)
    override fun getTitle(): String? = stop.brand?.takeIf { it.isNotBlank() }
        ?: stop.stationName?.takeIf { it.isNotBlank() }
        ?: "Charging stop"
    override fun getSnippet(): String? = snippetFor(stop)
    override fun getZIndex(): Float? = null
}

/** Compose-rendered pin used inside Clustering's clusterItemContent. The
 *  library rasterises this composable into a marker icon, which lets us pick
 *  up trip colors from the Compose palette instead of round-tripping through
 *  BitmapDescriptorFactory.defaultMarker(hue). */
@Composable
private fun ChargingStopPin(kind: PinKind, colorByTrip: Boolean) {
    val tint = when {
        !colorByTrip -> Color(0xFFE53935)  // All-red mode mirrors the filter sheet.
        kind is PinKind.SingleTrip -> TripPinColor.fromKey(kind.tripPinColorKey)?.swatch
            ?: Color(0xFFE53935)
        kind is PinKind.Shared -> Color(0xFF6E6E6E)  // Neutral gray for multi-trip stops.
        else -> Color(0xFFE53935)  // Untripped.
    }
    Icon(
        imageVector = Icons.Default.Place,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(36.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    ui: MapUi,
    onToggleTrip: (Long?) -> Unit,
    onShowAllTrips: () -> Unit,
    onSetColorByTrip: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
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
                if (ui.tripOptions.any { !it.visible } || !ui.untrippedVisible) {
                    TextButton(onClick = onShowAllTrips) { Text("Show all") }
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

