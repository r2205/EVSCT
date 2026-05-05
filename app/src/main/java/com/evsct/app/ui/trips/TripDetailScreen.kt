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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.MoneyStat
import com.evsct.app.util.DrivingLeg
import com.evsct.app.util.ExcludedPair
import com.evsct.app.util.Format
import com.evsct.app.util.Units

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onEditSession: (Long) -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEdit by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.trip?.name ?: "Trip",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.trip != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit trip")
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Stat("Sessions", st.sessionCount.toString())
                            MoneyStat("Total cost", st.totalCostByCurrency)
                            Stat("Energy", Format.kwh(st.totalEnergyKwh))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val missing = state.sessionsWithoutDuration
                            val timeText = Format.duration(state.totalChargeSeconds) +
                                if (missing > 0) "*" else ""
                            Stat("Charge time", timeText)
                            if (missing > 0) {
                                val sCount = st.sessionCount
                                Text(
                                    text = "* $missing of $sCount session" +
                                        (if (sCount == 1) "" else "s") +
                                        " missing duration",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                )
                            }
                        }
                        if (st.totalDistanceKm > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Stat("Distance", Format.distance(st.totalDistanceKm, units.useMiles))
                                Stat(
                                    "Cost / $distUnit",
                                    Format.moneyRatePerDistance(st.costPerKm, units.useMiles),
                                )
                                Stat("Cost / kWh", Format.moneyRate(st.costPerKwh, "kWh"))
                            }
                        }
                    }
                }
            }
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.sessions, key = { it.id }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEditSession(s.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${s.brand ?: "Unknown"} · ${s.locationCity ?: "—"}", fontWeight = FontWeight.Medium)
                                Text(Format.money(s.totalCost, s.currency))
                            }
                            Text(Format.dateTime(s.sessionStart), style = MaterialTheme.typography.bodySmall)
                            Text("${Format.kwh(s.energyKwh)} · ${Format.duration(s.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
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
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall)
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
                        "Driving efficiency",
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
                    contentDescription = if (expanded) "Collapse" else "Expand",
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

private fun legLabel(s: ChargingSession): String =
    s.locationCity?.takeIf { it.isNotBlank() }
        ?: s.brand?.takeIf { it.isNotBlank() }
        ?: Format.date(s.sessionStart)

private fun formatKmPerKwh(value: Double, useMiles: Boolean): String {
    val display = Units.kmToDisplay(value, useMiles)
    val unit = Units.distanceUnit(useMiles)
    return "%.2f $unit/kWh".format(display)
}
