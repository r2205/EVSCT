package com.evsct.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Fullscreen map for picking a location manually. The user pans/zooms the
 * map; a centered crosshair pin marks where the chosen location will land.
 * Confirming reads the camera target (always the screen center) and hands
 * the lat/lng back to the calling screen.
 *
 * Used to fix sessions whose typed address geocoded to the wrong place —
 * the user can drop the pin exactly where the charger actually is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    onCancel: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = if (initialLat != null && initialLng != null) {
            // Seed at the existing coords so the user can fine-tune.
            CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 15f)
        } else {
            // No prior coords: open at a continent zoom over North America;
            // user pans/zooms to wherever they want.
            CameraPosition.fromLatLngZoom(LatLng(45.0, -98.0), 3f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pick location", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val target = cameraPositionState.position.target
                            onConfirm(target.latitude, target.longitude)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Use this location") }
                }
            }
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
                    mapToolbarEnabled = false,
                ),
            )
            // Centered crosshair overlay — the pin stays in screen center
            // while the map moves under it. The camera target is whatever
            // is under the pin.
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Selected location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
            Spacer(modifier = Modifier.width(0.dp))  // No-op; keeps layout tree stable.
        }
    }
}
