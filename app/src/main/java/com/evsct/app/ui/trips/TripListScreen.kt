package com.evsct.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.R
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.ui.EmptyState
import com.evsct.app.ui.EvsctBarTitle
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.VehicleScope
import com.evsct.app.ui.VehicleScopeTabs
import com.evsct.app.ui.needsVehiclePicker
import com.evsct.app.util.Format
import com.evsct.app.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onOpenTrip: (Long) -> Unit,
    /** The vehicle strip's car icon — same shortcut as the Log and Stats. */
    onOpenVehicles: () -> Unit,
    viewModel: TripListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trips = state.trips
    // Saveable so rotating (or process death) doesn't dismiss the dialog
    // and discard everything typed into it.
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.evsct.app.data.entity.Trip?>(null) }
    val haptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { EvsctBarTitle(stringResource(R.string.nav_trips)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { dialogOpen = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.trips_new_trip))
            }
        },
    ) { padding ->
        // Same strip as the Log and Stats, bucketing trips by their own car.
        val showTabs = needsVehiclePicker(state.vehicles.size, state.hasUnassignedTrips)
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showTabs) {
                VehicleScopeTabs(
                    vehicles = state.vehicles,
                    includeUnassigned = state.hasUnassignedTrips,
                    scope = state.vehicleScope,
                    onSelect = viewModel::setVehicleScope,
                    onManageVehicles = onOpenVehicles,
                )
            }
            if (trips.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Map,
                    title = stringResource(R.string.trips_no_trips_yet),
                    // The button below replaces the old "Tap + to create one" —
                    // that sentence pointed at an unlabelled circle and asked the
                    // reader to work out which one.
                    body = stringResource(R.string.trips_trips_group_sessions_for),
                    actionLabel = stringResource(R.string.trips_new_trip),
                    onAction = { dialogOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trips, key = { it.trip.id }) { tws ->
                        // Under "All" each row names its car, as the Log's
                        // rows do; a vehicle tab already says which car.
                        val carName = if (showTabs && state.vehicleScope == VehicleScope.All) {
                            state.vehicles.firstOrNull { it.id == tws.trip.vehicleId }?.name
                        } else {
                            null
                        }
                        TripRow(
                            tws = tws,
                            carName = carName,
                            onOpen = { onOpenTrip(tws.trip.id) },
                            onDelete = { pendingDelete = tws.trip },
                            // Rows glide to their new slot when a date edit
                            // re-sorts the list or a trip is deleted.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.trips_delete_confirm_title, trip.name)) },
            text = {
                Text(
                    stringResource(R.string.trips_sessions_tagged_with_this)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.delete(trip)
                    pendingDelete = null
                }) { Text(stringResource(R.string.trips_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.trips_cancel)) }
            },
        )
    }

    if (dialogOpen) {
        TripEditDialog(
            trip = null,
            vehicles = state.vehicles,
            newTripVehicleId = state.newTripVehicleId,
            onDismiss = { dialogOpen = false },
            onSave = { trip ->
                viewModel.upsert(trip)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun TripRow(
    tws: TripWithStats,
    carName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = LocalUserUnits.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tws.trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                listOfNotNull(carName, tripDateLabel(tws.trip))
                    .joinToString(" · ")
                    .takeIf { it.isNotEmpty() }
                    ?.let { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                if (tws.sessionCount == 0) {
                    // No charging: "0 sessions · — · 0.0 kWh" said nothing.
                    // What the trip has is its drive — distance and the
                    // energy its own readings put on it.
                    val drive = listOfNotNull(
                        tws.totalDistanceKm.takeIf { it > 0 }?.let { Format.distance(it, units.useMiles) },
                        tws.energyUsedKwh?.let { stringResource(R.string.trips_energy_used, Format.kwh(it)) },
                    )
                    Text(
                        drive.joinToString(" · ").ifEmpty { stringResource(R.string.trips_no_sessions_yet) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        pluralStringResource(
                            R.plurals.trips_summary,
                            tws.sessionCount,
                            tws.sessionCount,
                            Money.format(tws.totalCostByCurrency),
                            Format.kwh(tws.totalEnergyKwh),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (tws.totalDistanceKm > 0) {
                        Text(
                            "${Format.distance(tws.totalDistanceKm, units.useMiles)} · " +
                                Format.moneyRatePerDistance(tws.costPerKm, units.useMiles),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.trips_delete_trip))
            }
        }
    }
}
