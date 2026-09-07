package com.evsct.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.R
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.MoneyStat
import com.evsct.app.ui.StatColumns
import com.evsct.app.util.DrivingLeg
import com.evsct.app.util.EfficiencyAnalysis
import com.evsct.app.util.ExcludedPair
import com.evsct.app.util.Format
import com.evsct.app.util.Units
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onEditSession: (Long) -> Unit,
    /** Jump to the Map tab filtered down to just this trip's stops. */
    onViewOnMap: (tripId: Long) -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Saveable so rotating (or process death) doesn't dismiss the edit
    // dialog and discard everything typed into it.
    var showEdit by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.trip?.name ?: stringResource(R.string.trip_fallback_title),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.trip_back))
                    }
                },
                actions = {
                    state.trip?.let { trip ->
                        // An empty trip can never contribute a pin, so the
                        // map jump only appears once sessions are tagged.
                        // Sessions without coordinates still count: the map's
                        // geocode backfill may locate them on arrival.
                        if (state.sessions.isNotEmpty()) {
                            IconButton(onClick = { onViewOnMap(trip.id) }) {
                                Icon(Icons.Default.Map, contentDescription = stringResource(R.string.trip_view_on_map))
                            }
                        }
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.trip_edit_trip))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            state.stats?.let { st ->
                val units = LocalUserUnits.current
                val distUnit = Units.distanceUnit(units.useMiles)
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        tripDateLabel(st.trip)?.let { dates ->
                            Text(
                                dates,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        // StatColumns rather than a plain SpaceBetween Row: three
                        // stats across only works while all three fit, and at the
                        // larger accessibility font sizes a "$834.84 CAD" or
                        // "1,651.76 kWh" column ran into its neighbours. Same
                        // helper the Log summary and Stats headline use; see
                        // ui/StatStacking.kt for why wrapping alone wasn't enough.
                        StatColumns(modifier = Modifier.fillMaxWidth()) { statModifier ->
                            Stat(stringResource(R.string.common_sessions), st.sessionCount.toString(), statModifier)
                            MoneyStat(stringResource(R.string.common_total_cost), st.totalCostByCurrency, statModifier)
                            Stat(stringResource(R.string.common_energy), Format.kwh(st.totalEnergyKwh), statModifier)
                        }
                        val missing = state.sessionsWithoutDuration
                        val timeText = Format.duration(state.totalChargeSeconds) +
                            if (missing > 0) "*" else ""
                        StatColumns(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { statModifier ->
                            Stat(stringResource(R.string.common_charge_time), timeText, statModifier)
                        }
                        if (missing > 0) {
                            // Under the stat rather than beside it: beside, the
                            // note shared its line with the stat and squeezed to a
                            // sliver at large font scale. Full width, it wraps.
                            val sCount = st.sessionCount
                            Text(
                                text = pluralStringResource(
                                    R.plurals.common_missing_duration, sCount, missing, sCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                        if (st.totalDistanceKm > 0) {
                            StatColumns(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { statModifier ->
                                Stat(stringResource(R.string.common_distance), Format.distance(st.totalDistanceKm, units.useMiles), statModifier)
                                Stat(
                                    stringResource(R.string.common_cost_per_distance, distUnit),
                                    Format.moneyRatePerDistance(st.costPerKm, units.useMiles),
                                    statModifier,
                                )
                                Stat(stringResource(R.string.common_cost_per_kwh), Format.moneyRate(st.costPerKwh, "kWh"), statModifier)
                            }
                        }
                    }
                }
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // trip != null distinguishes "loaded and genuinely empty"
                // from the pre-load frame, same idea as the other screens'
                // isLoading gates.
                if (state.trip != null && state.sessions.isEmpty()) {
                    item {
                        com.evsct.app.ui.EmptyState(
                            icon = Icons.Default.Bolt,
                            title = stringResource(R.string.trip_no_sessions_in_this),
                            body = stringResource(R.string.trip_tag_sessions_from_the),
                            modifier = Modifier.fillParentMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(state.sessions, key = { it.id }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEditSession(s.id) },
                    ) {
                        // Same shape as VehicleDetailScreen's RecentSessionRow.
                        // The old SpaceBetween Row gave neither child a weight,
                        // so a long "Brand · City" title claimed the whole width
                        // and the cost was left to squeeze in beside it: no gap
                        // ("North Bay$43.93"), then "CAD" pushed onto its own
                        // line. Weighting the title column measures the cost
                        // first at its natural width and wraps the title around
                        // whatever is left.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${s.brand ?: stringResource(R.string.common_unknown)} · ${s.locationCity ?: "—"}",
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(Format.dateTime(s.sessionStart), style = MaterialTheme.typography.bodySmall)
                                Text("${Format.kwh(s.energyKwh)} · ${Format.duration(s.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(Format.money(s.totalCost, s.currency))
                        }
                    }
                }
                if (state.legs.isNotEmpty() || state.excludedLegs.isNotEmpty()) {
                    item {
                        EfficiencyCard(
                            legs = state.legs,
                            excluded = state.excludedLegs,
                            avgKmPerKwh = state.avgKmPerKwh,
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        state.trip?.let { trip ->
            TripEditDialog(
                trip = trip,
                onDismiss = { showEdit = false },
                onSave = {
                    viewModel.updateTrip(it)
                    showEdit = false
                },
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Centred explicitly: a value long enough to wrap inside its column
        // would otherwise wrap left-aligned under a centred label.
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EfficiencyCard(
    legs: List<DrivingLeg>,
    excluded: List<ExcludedPair>,
    avgKmPerKwh: Double?,
) {
    val units = LocalUserUnits.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.trip_driving_efficiency),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        legCountCaption(legs.size, excluded.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = avgKmPerKwh?.let { formatKmPerKwh(it, units.useMiles) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.trip_collapse) else stringResource(R.string.trip_expand),
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    legs.forEach { leg ->
                        LegRow(leg, units.useMiles)
                    }
                    excluded.forEach { ex ->
                        ExcludedRow(ex)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedRow(ex: ExcludedPair) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            legLabel(ex.from) + " → " + legLabel(ex.to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            ex.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun legCountCaption(measured: Int, unmeasurable: Int): String {
    val parts = mutableListOf<String>()
    if (measured > 0) parts += "$measured measured"
    if (unmeasurable > 0) parts += "$unmeasurable unmeasurable"
    return parts.joinToString(" · ")
}

@Composable
private fun LegRow(leg: DrivingLeg, useMiles: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                legLabel(leg.from) + " → " + legLabel(leg.to),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatKmPerKwh(leg.kmPerKwh, useMiles),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "${Format.distance(leg.distanceKm, useMiles)} · ${Format.kwh(leg.energyUsedKwh)} used",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun legLabel(s: ChargingSession): String = when (s.id) {
    // Virtual endpoints carrying the trip-level battery/odometer readings.
    EfficiencyAnalysis.TRIP_START_ANCHOR_ID -> stringResource(R.string.trip_start)
    EfficiencyAnalysis.TRIP_END_ANCHOR_ID -> stringResource(R.string.trip_end)
    else -> s.locationCity?.takeIf { it.isNotBlank() }
        ?: s.brand?.takeIf { it.isNotBlank() }
        ?: Format.date(s.sessionStart)
}

private fun formatKmPerKwh(value: Double, useMiles: Boolean): String {
    val display = Units.kmToDisplay(value, useMiles)
    val unit = Units.distanceUnit(useMiles)
    // Locale pinned like every other numeric display — the default locale
    // would render "3,52 km/kWh" next to US-separated Format.* strings.
    return "%.2f $unit/kWh".format(Locale.US, display)
}
