package com.evsct.app.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

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
    viewModel: MapPickerViewModel = hiltViewModel(),
) {
    val mapType by viewModel.mapType.collectAsStateWithLifecycle()
    var showLayersMenu by remember { mutableStateOf(false) }
    // Both callbacks pop the back stack; a double-tap during the exit
    // transition would pop twice and remove the session-edit screen too.
    var exited by remember { mutableStateOf(false) }
    fun exitOnce(action: () -> Unit) {
        if (exited) return
        exited = true
        action()
    }
    // Re-arm the latch whenever this nav entry reaches RESUMED (same
    // pattern as the edit screens' exit latches). The nav-graph callbacks
    // run inside ifResumed, which silently drops a tap that lands during
    // the enter transition — without the reset that swallowed tap would
    // leave `exited` latched and Cancel, the back arrow, and "Use this
    // location" all permanently dead. A pop that actually ran never
    // resumes this entry again, so double-tap protection is unaffected.
    LifecycleResumeEffect(Unit) {
        exited = false
        onPauseOrDispose { }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = if (initialLat != null && initialLng != null) {
            // Seed at the existing coords so the user can fine-tune.
            CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 15f)
        } else {
            // No prior coords: open at a continent zoom over North America;
            // the effect below tries to replace this with the device's
            // location, and the my-location button covers the rest.
            CameraPosition.fromLatLngZoom(LatLng(45.0, -98.0), 3f)
        }
    }

    // My-location plumbing: mirrors the charging map's FAB. The picker is
    // where "log the charger in front of me" happens, so being able to
    // jump to yourself matters even more here.
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
                    CameraUpdateFactory.newLatLngZoom(LatLng(fix.first, fix.second), 15f),
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
        if (hasLocationPermission) jumpToMyLocation()
    }

    // With no prior coordinates, open where the user is standing instead of
    // a continent view — most manual picks are for the charger in front of
    // them. Quietly skipped without the permission (the FAB can still ask),
    // and the camera is only replaced while it still sits at the zoomed-out
    // default, so a user who has already started panning isn't yanked away
    // by a slow GPS fix.
    LaunchedEffect(Unit) {
        if (initialLat != null && initialLng != null) return@LaunchedEffect
        if (!viewModel.hasLocationPermission()) return@LaunchedEffect
        val fix = viewModel.currentLatLng() ?: return@LaunchedEffect
        if (cameraPositionState.position.zoom <= 3.5f) {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(LatLng(fix.first, fix.second), 15f)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Pick location", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { exitOnce(onCancel) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showLayersMenu = true }) {
                            Icon(Icons.Default.Layers, contentDescription = "Map type")
                        }
                        MapTypeMenu(
                            expanded = showLayersMenu,
                            current = mapType,
                            onSelect = { type ->
                                viewModel.setMapType(type)
                                showLayersMenu = false
                            },
                            onDismiss = { showLayersMenu = false },
                        )
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
                        onClick = { exitOnce(onCancel) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val target = cameraPositionState.position.target
                            exitOnce { onConfirm(target.latitude, target.longitude) }
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
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = mapTypeOf(mapType),
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = false,
                    // Replaced by the FAB below, which also handles the
                    // permission request.
                    myLocationButtonEnabled = false,
                ),
            )
            // Centered crosshair overlay — the pin stays in screen center
            // while the map moves under it. The camera target is whatever
            // is under the pin's TIP, so the icon is shifted up: the Place
            // glyph's tip sits at y≈21.5 of its 24dp viewport, i.e. 43dp
            // down a 48dp icon — 19dp below the icon's center. Without the
            // shift every picked location lands ~19dp south of where the
            // pin visibly points.
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Selected location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .offset(y = (-19).dp),
            )
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
            Spacer(modifier = Modifier.width(0.dp))  // No-op; keeps layout tree stable.
        }
    }
}
