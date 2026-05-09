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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QueryStats
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            if (state.thisMonthHasDriving) {
                GasSavingsCard(state)
            }

            if (state.sessionCount == 0) {
                com.evsct.app.ui.EmptyState(
                    icon = Icons.Default.QueryStats,
                    title = "No sessions yet",
                    body = "Stats appear here once you've logged at least " +
                        "one charging session.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                return@Column
            }

            val units = LocalUserUnits.current
            ChartCard("Cost by month") {
                BarList(
                    items = state.monthlyCost,
                    labelWidth = 64.dp,
                    formatValue = { Format.money(it, state.costCurrency) },
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
                        formatValue = { Format.money(it, state.costCurrency) },
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

            val anyDc = state.dcFastByDayHour.any { row -> row.any { it > 0 } }
            val anyAc = state.acByDayHour.any { row -> row.any { it > 0 } }
            if (anyDc || anyAc) {
                ChartCard("When you charge") {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (anyDc) {
                            TimeOfDayHeatmap(
                                title = "DC Fast",
                                grid = state.dcFastByDayHour,
                                accent = EvAccents.DcFast,
                            )
                        }
                        if (anyAc) {
                            TimeOfDayHeatmap(
                                title = "AC (L2 + L1)",
                                grid = state.acByDayHour,
                                accent = EvAccents.AcL2,
                            )
                        }
                    }
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
                Stat("Total cost", Format.money(state.totalCost, state.costCurrency))
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
            if (state.excludedByCurrency > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cost totals are in ${state.costCurrency}. " +
                        "${state.excludedByCurrency} session" +
                        (if (state.excludedByCurrency == 1) "" else "s") +
                        " in another currency excluded.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Tongue-in-cheek-but-motivating "vs gas" card for the current calendar
 * month. Numbers come from the ViewModel — the card just lays them out.
 * Always renders the savings line; flips to "Paid $X more than gas" in
 * the rare case where charging this month was the more expensive option.
 */
@Composable
private fun GasSavingsCard(state: StatsUi) {
    val saved = state.thisMonthSavings
    val savedAbsText = Format.money(kotlin.math.abs(saved), state.costCurrency)
    val savedPositive = saved >= 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "vs gas this month",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (savedPositive) "Saved $savedAbsText"
                else "Paid $savedAbsText more than gas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (savedPositive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${Format.money(state.thisMonthCost, state.costCurrency)} charging vs " +
                    "~${Format.money(state.thisMonthGasCost, state.costCurrency)} on gas",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Assumes \$2.15/L · 12 L/100 km",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Day-of-week × hour-of-day heatmap. Sunday on top, midnight on the left.
 * Cell shading is alpha-scaled against [grid]'s busiest hour so a few-stop
 * vehicle still has visibly-shaded cells without the busiest one going off
 * the deep end.
 */
@Composable
private fun TimeOfDayHeatmap(
    title: String,
    grid: List<List<Int>>,
    accent: Color,
) {
    val maxCount = grid.flatten().max()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyCellColor = MaterialTheme.colorScheme.surfaceVariant
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            peakLabel(grid)?.let { peak ->
                Text(
                    "Peak: $peak",
                    style = labelStyle,
                    color = labelColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (day in 0..6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dayLabels[day],
                        style = labelStyle,
                        color = labelColor,
                        modifier = Modifier.width(24.dp),
                    )
                    for (hour in 0..23) {
                        val count = grid[day][hour]
                        // 0.20..1.00 alpha range so even single-session
                        // cells visibly tint, but the busiest still pop.
                        val cellColor = if (count == 0) emptyCellColor
                        else accent.copy(alpha = 0.20f + (count.toFloat() / maxCount) * 0.80f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(cellColor),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Hour tick row — 5 labels at SpaceBetween line up roughly with
        // grid columns 0 / 6 / 12 / 18 / 23. Pixel-perfect alignment isn't
        // necessary; the goal is just to anchor "morning vs afternoon."
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 25.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("12 am", "6 am", "noon", "6 pm", "11 pm").forEach { tick ->
                Text(tick, style = labelStyle, color = labelColor)
            }
        }
    }
}

/** "Sat 2 pm" string for the busiest cell, or null when the grid is empty. */
private fun peakLabel(grid: List<List<Int>>): String? {
    var best = 0
    var bestDay = -1
    var bestHour = -1
    for (d in 0..6) for (h in 0..23) {
        val c = grid[d][h]
        if (c > best) {
            best = c; bestDay = d; bestHour = h
        }
    }
    if (best == 0) return null
    val day = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[bestDay]
    return "$day ${formatHour12(bestHour)}"
}

private fun formatHour12(h: Int): String = when {
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "noon"
    else -> "${h - 12} pm"
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
