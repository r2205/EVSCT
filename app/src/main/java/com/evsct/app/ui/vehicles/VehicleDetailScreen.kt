package com.evsct.app.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.util.Format
import com.evsct.app.util.Units
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditSession: (Long) -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-pull the vehicle whenever this screen comes back to the foreground
    // (e.g. after returning from edit) so name/photo changes show up right away.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.vehicle?.name ?: "Vehicle",
                        fontWeight = FontWeight.SemiBold,
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
                    state.vehicle?.let { v ->
                        IconButton(onClick = { onEdit(v.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit vehicle")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val vehicle = state.vehicle
        if (vehicle == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Vehicle not found.") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { HeaderCard(vehicle) }
            item { LifetimeCard(state) }

            val highlights = listOfNotNull(
                state.fastestSession?.let {
                    HighlightTile(
                        label = "Fastest charge",
                        primary = Format.kw(it.value) + " avg",
                        secondary = sessionLabel(it.session),
                        icon = Icons.Default.Speed,
                        sessionId = it.session.id,
                    )
                },
                state.cheapestPriceSession?.let {
                    HighlightTile(
                        label = "Cheapest $/kWh",
                        primary = Format.moneyRate(it.value, "kWh"),
                        secondary = sessionLabel(it.session),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        sessionId = it.session.id,
                    )
                },
                state.mostExpensivePriceSession?.let {
                    HighlightTile(
                        label = "Most expensive $/kWh",
                        primary = Format.moneyRate(it.value, "kWh"),
                        secondary = sessionLabel(it.session),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        sessionId = it.session.id,
                    )
                },
                state.lastChargedAt?.let {
                    HighlightTile(
                        label = "Last charged",
                        primary = Format.dateTime(it),
                        secondary = state.mostUsedBrand?.let { (b, n) -> "$b · $n visits" },
                        icon = Icons.Default.Today,
                        sessionId = null,
                    )
                },
            )
            items(highlights, key = { it.label }) { tile ->
                HighlightCard(tile, onClick = tile.sessionId?.let { id -> { onEditSession(id) } })
            }

            if (state.sessions.isNotEmpty()) {
                item {
                    Text(
                        "Recent sessions",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(state.sessions.take(10), key = { it.id }) { s ->
                    RecentSessionRow(s, onClick = { onEditSession(s.id) })
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeaderCard(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VehicleHero(vehicle.imagePath)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vehicle.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (vehicle.displayLabel != vehicle.name) {
                    Text(vehicle.displayLabel, style = MaterialTheme.typography.bodyMedium)
                }
                vehicle.batteryCapacityKwh?.let {
                    Text("$it kWh battery", style = MaterialTheme.typography.bodySmall)
                }
                vehicle.nominalRangeKm?.let {
                    val units = LocalUserUnits.current
                    Text(
                        "${Format.distance(it.toDouble(), units.useMiles)} nominal range",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleHero(imagePath: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val file = imagePath?.let { File(ctx.filesDir, it) }
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun LifetimeCard(state: VehicleDetailUi) {
    val units = LocalUserUnits.current
    val distUnit = Units.distanceUnit(units.useMiles)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Lifetime",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Sessions", state.sessionCount.toString())
                Stat("Total cost", Format.money(state.totalCost, units.defaultCurrency))
                Stat("Energy", Format.kwh(state.totalEnergyKwh))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat(
                    "Distance",
                    if (state.totalDistanceKm > 0)
                        Format.distance(state.totalDistanceKm, units.useMiles) else "—",
                )
                Stat(
                    "Cost / $distUnit",
                    Format.moneyRatePerDistance(state.costPerKm, units.useMiles),
                )
                Stat("Cost / kWh", Format.moneyRate(state.avgEffPricePerKwh, "kWh"))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Avg power", Format.kw(state.avgPowerKw))
                if (state.avgKmPerKwh != null) {
                    val display = Units.kmToDisplay(state.avgKmPerKwh, units.useMiles)
                    Stat("Efficiency", "%.2f $distUnit/kWh".format(display))
                }
                state.mostUsedBrand?.let { (brand, _) ->
                    Stat("Top brand", brand)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class HighlightTile(
    val label: String,
    val primary: String,
    val secondary: String?,
    val icon: ImageVector,
    val sessionId: Long?,
)

@Composable
private fun HighlightCard(tile: HighlightTile, onClick: (() -> Unit)?) {
    val rowMod = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick)
    else Modifier.fillMaxWidth()
    Card(modifier = rowMod) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    tile.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                tile.secondary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(s: ChargingSession, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${s.brand ?: "Unknown"} · ${s.locationCity ?: "—"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(Format.dateTime(s.sessionStart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${Format.kwh(s.energyKwh)} · ${Format.duration(s.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                Format.money(s.totalCost, s.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun sessionLabel(s: ChargingSession): String {
    val place = listOfNotNull(
        s.brand?.trim()?.takeIf(String::isNotEmpty),
        s.locationCity?.trim()?.takeIf(String::isNotEmpty),
    ).joinToString(" · ")
    val date = Format.date(s.sessionStart)
    return if (place.isEmpty()) date else "$place · $date"
}
