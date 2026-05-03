package com.evsct.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.runBackfillIfNeeded() }

    val cameraPositionState = rememberCameraPositionState {
        // Default view: roughly the centre of North America at a continent zoom.
        position = CameraPosition.fromLatLngZoom(LatLng(45.0, -98.0), 3f)
    }

    // Once stops arrive, frame the camera around them.
    LaunchedEffect(state.stops) {
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
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = true,
                ),
            ) {
                // BitmapDescriptorFactory only works once the Maps SDK is
                // initialized — which happens when GoogleMap is composed.
                // Building the shared-pin icon inside this content lambda
                // guarantees the SDK is ready before we touch the factory.
                val sharedPinIcon = remember { sharedTripPinDescriptor() }
                state.stops.forEach { stop ->
                    Marker(
                        state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                        title = stop.brand?.takeIf { it.isNotBlank() }
                            ?: stop.stationName?.takeIf { it.isNotBlank() }
                            ?: "Charging stop",
                        snippet = snippetFor(stop),
                        icon = iconFor(stop.pinKind, sharedPinIcon),
                    )
                }
            }

            if (state.backfillRunning) {
                BackfillBanner(text = "Locating ${state.unlocatedDistinct} stops…")
            }
            if (state.stops.isEmpty() && !state.backfillRunning) {
                EmptyState(
                    message = if (state.totalDistinct == 0)
                        "No locations to map yet. Add a session with GPS autofill or a real address."
                    else
                        "Could not locate any stops. Check that addresses are filled in.",
                )
            }
        }
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

private fun iconFor(kind: PinKind, sharedIcon: BitmapDescriptor): BitmapDescriptor? = when (kind) {
    PinKind.Untripped -> null  // Default red marker.
    is PinKind.SingleTrip -> {
        val color = TripPinColor.fromKey(kind.tripPinColorKey)
        if (color == null) null else BitmapDescriptorFactory.defaultMarker(color.mapsHue)
    }
    PinKind.Shared -> sharedIcon
}

/**
 * A small gray pin used for stops that have been visited across multiple
 * trips. Built once and reused since BitmapDescriptors are expensive to
 * create per-frame.
 */
private fun sharedTripPinDescriptor(): BitmapDescriptor {
    val sizePx = 64
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2.4f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E6E6E.toInt() }
    canvas.drawCircle(cx, cy, radius, fill)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, radius, ring)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
