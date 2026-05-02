package com.evsct.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.theme.EvAccents
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.vehicles.size >= 2) {
                VehicleTabs(state.vehicles, state.vehicleFilterId, viewModel::setVehicleFilter)
            }

            HeadlineCard(state)

            if (state.sessionCount == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No sessions to summarize yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            val units = LocalUserUnits.current
            ChartCard("Cost by month") {
                BarList(
                    items = state.monthlyCost,
                    labelWidth = 64.dp,
                    formatValue = { Format.money(it, units.defaultCurrency) },
                )
            }

            ChartCard("Energy by month") {
                BarList(
                    items = state.monthlyEnergy,
                    labelWidth = 64.dp,
                    formatValue = { Format.kwh(it) },
                )
            }

            if (state.byBrandCost.isNotEmpty()) {
                ChartCard("Top brands by spend") {
                    BarList(
                        items = state.byBrandCost,
                        labelWidth = 130.dp,
                        formatValue = { Format.money(it, units.defaultCurrency) },
                    )
                }
            }

            if (state.byType.values.sum() > 0) {
                ChartCard("Charging type") {
                    TypeSplitBar(state.byType)
                    Spacer(Modifier.height(8.dp))
                    TypeLegend(state.byType)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeadlineCard(state: StatsUi) {
    val units = LocalUserUnits.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Stat("Sessions", state.sessionCount.toString())
                Stat("Total cost", Format.money(state.totalCost, units.defaultCurrency))
                Stat("Energy", Format.kwh(state.totalEnergyKwh))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Stat("Avg eff. $/kWh", Format.moneyRate(state.avgEffPricePerKwh, "kWh"))
                Stat("Avg power", Format.kw(state.avgPowerKw))
            }
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
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * Horizontal bar list. Each row is `[label] [bar───] [value]`.
 * Bars are normalized against the largest value in [items].
 */
@Composable
private fun BarList(
    items: List<Pair<String, Double>>,
    labelWidth: androidx.compose.ui.unit.Dp,
    formatValue: (Double) -> String,
) {
    val maxValue = items.maxOfOrNull { it.second } ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(labelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val frac = if (maxValue > 0) (value / maxValue).toFloat() else 0f
                    if (frac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(frac)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatValue(value),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(82.dp),
                )
            }
        }
    }
}

@Composable
private fun TypeSplitBar(byType: Map<ChargingType, Int>) {
    val total = byType.values.sum().coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ChargingType.entries.forEach { type ->
            val count = byType[type] ?: 0
            if (count == 0) return@forEach
            val frac = count.toFloat() / total.toFloat()
            Box(
                modifier = Modifier
                    .weight(frac)
                    .fillMaxHeight()
                    .background(typeColor(type)),
            )
        }
    }
}

@Composable
private fun TypeLegend(byType: Map<ChargingType, Int>) {
    val total = byType.values.sum().coerceAtLeast(1)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChargingType.entries.forEach { type ->
            val count = byType[type] ?: 0
            if (count == 0) return@forEach
            val pct = (count * 100.0 / total).toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(typeColor(type)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${type.shortLabel()} · $count · $pct%",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun typeColor(type: ChargingType): Color = when (type) {
    ChargingType.DC_FAST -> EvAccents.DcFast
    ChargingType.AC_L2 -> EvAccents.AcL2
    ChargingType.AC_L1 -> EvAccents.AcL1
}

private fun ChargingType.shortLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC Fast"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTabs(
    vehicles: List<Vehicle>,
    selectedVehicleId: Long?,
    onSelect: (Long?) -> Unit,
) {
    val tabs = buildList {
        add(null to "All")
        vehicles.forEach { add(it.id to it.name) }
    }
    val selectedIndex = tabs.indexOfFirst { it.first == selectedVehicleId }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        tabs.forEachIndexed { index, (id, label) ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(id) },
                text = {
                    Text(
                        label,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
