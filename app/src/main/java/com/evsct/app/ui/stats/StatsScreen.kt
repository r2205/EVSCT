package com.evsct.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.ui.BarList
import com.evsct.app.ui.EvsctBarTitle
import com.evsct.app.ui.MoneyStat
import com.evsct.app.ui.forType
import com.evsct.app.ui.theme.LocalEvAccents
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenYearRecap: (vehicleId: Long?) -> Unit,
    onOpenLogForBrand: (brand: String, vehicleId: Long?) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { EvsctBarTitle("Stats") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    // A labeled action, not a bare glyph: the PDF icon
                    // under-sold this (the recap is a whole screen that
                    // exports as PDF or HTML) and read as "some PDF
                    // button" in device testing. The text doubles as the
                    // TalkBack name.
                    TextButton(
                        onClick = { onOpenYearRecap(state.vehicleFilterId) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Summarize,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Recap")
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
            if (state.isLoading) {
                // First frame: nothing has emitted yet. The headline would
                // show all zeros and the zero-session branch would flash
                // "No sessions yet" over real data.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

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

            ChartWindowSelector(
                selected = state.chartWindow,
                onSelect = viewModel::setChartWindow,
            )

            val bucketNoun = when (state.chartWindow) {
                StatsChartWindow.LAST_12_MONTHS -> "month"
                StatsChartWindow.ALL_YEARS -> "year"
            }
            ChartCard("Cost by $bucketNoun") {
                BarList(
                    items = state.costSeries,
                    labelWidth = 64.dp,
                    formatValue = { Format.money(it, state.costCurrency) },
                )
            }

            ChartCard("Energy by $bucketNoun") {
                BarList(
                    items = state.energySeries,
                    labelWidth = 64.dp,
                    formatValue = { Format.kwh(it) },
                )
            }

            if (state.byBrandCost.isNotEmpty()) {
                ChartCard(
                    "Top brands by spend",
                    subtitle = "Tap a brand to see its sessions in the Log",
                ) {
                    BarList(
                        items = state.byBrandCost,
                        labelWidth = 130.dp,
                        formatValue = { Format.money(it, state.costCurrency) },
                        onRowClick = { brand ->
                            onOpenLogForBrand(brand, state.vehicleFilterId)
                        },
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
                ChartCard(
                    "When you charge",
                    subtitle = "Rows are days, columns are hours — tap a square for its count",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        val accents = LocalEvAccents.current
                        if (anyDc) {
                            TimeOfDayHeatmap(
                                title = "DC Fast",
                                grid = state.dcFastByDayHour,
                                accent = accents.dcFast.accent,
                            )
                        }
                        if (anyAc) {
                            TimeOfDayHeatmap(
                                title = "AC (L2 + L1)",
                                grid = state.acByDayHour,
                                accent = accents.acL2.accent,
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
                // Shared multi-currency stat: one line per currency, so the
                // headline never has to silently drop foreign-currency spend
                // the way the single-currency charts below do.
                MoneyStat("Total cost", state.totalCostByCurrency)
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
                val n = state.excludedByCurrency
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cost charts below are in ${state.costCurrency}. " +
                        if (n == 1) "1 session in another currency doesn't appear in them."
                        else "$n sessions in another currency don't appear in them.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Two-way window toggle for the cost/energy trend charts directly below
 *  it — also serves as the honest label for what those charts cover. */
@Composable
private fun ChartWindowSelector(
    selected: StatsChartWindow,
    onSelect: (StatsChartWindow) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        StatsChartWindow.entries.forEachIndexed { index, window ->
            SegmentedButton(
                selected = window == selected,
                onClick = { onSelect(window) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatsChartWindow.entries.size,
                ),
            ) { Text(window.label) }
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
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TypeSplitBar(byType: Map<ChargingType, Int>) {
    val accents = LocalEvAccents.current
    val total = byType.values.sum().coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Decorative for TalkBack: the legend right below carries the
            // same counts and percentages as real text.
            .clearAndSetSemantics { },
    ) {
        ChargingType.entries.forEach { type ->
            val count = byType[type] ?: 0
            if (count == 0) return@forEach
            val frac = count.toFloat() / total.toFloat()
            Box(
                modifier = Modifier
                    .weight(frac)
                    .fillMaxHeight()
                    .background(accents.forType(type).accent),
            )
        }
    }
}

@Composable
private fun TypeLegend(byType: Map<ChargingType, Int>) {
    val accents = LocalEvAccents.current
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
                        .background(accents.forType(type).accent),
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
 * the deep end. Tapping a row selects the cell under the finger (outlined)
 * and prints its exact count below the legend; tapping it again clears it.
 */
@Composable
private fun TimeOfDayHeatmap(
    title: String,
    grid: List<List<Int>>,
    accent: Color,
) {
    val maxCount = grid.flatten().max()
    // Keyed on the grid: new data invalidates both the selection and the
    // tap handlers below, so a stale (day, hour) can't describe old counts.
    var selected by remember(grid) { mutableStateOf<Pair<Int, Int>?>(null) }
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // outlineVariant, not surfaceVariant: in light mode surfaceVariant is
    // nearly the card's own tone, so zero-count cells vanished. In dark
    // this palette's outlineVariant IS surfaceVariant (same hex), so the
    // dark grid — which already read well — is untouched.
    val emptyCellColor = MaterialTheme.colorScheme.outlineVariant
    val selectedOutline = MaterialTheme.colorScheme.onSurface
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
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            // 168 cells are useless to TalkBack one at a time; the grid
            // reads as one summary instead. Sighted-only affordances (tap
            // a square) have their info in the Peak label and this text.
            modifier = Modifier.semantics {
                contentDescription = heatmapSummary(title, grid)
            },
        ) {
            for (day in 0..6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dayLabels[day],
                        style = labelStyle,
                        color = labelColor,
                        modifier = Modifier.width(24.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            // One tap handler per day strip, mapping the tap's
                            // x to an hour column. Cheaper and more accurate
                            // than 168 tiny clickables, whose enforced minimum
                            // touch targets would overlap ambiguously.
                            .pointerInput(grid) {
                                detectTapGestures { offset ->
                                    val hour = (offset.x / size.width * 24)
                                        .toInt()
                                        .coerceIn(0, 23)
                                    val cell = day to hour
                                    selected = if (selected == cell) null else cell
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        for (hour in 0..23) {
                            val count = grid[day][hour]
                            // 0.20..1.00 alpha range so even single-session
                            // cells visibly tint, but the busiest still pop.
                            val cellColor = if (count == 0) emptyCellColor
                            else accent.copy(alpha = 0.20f + (count.toFloat() / maxCount) * 0.80f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(cellColor)
                                    .then(
                                        if (selected == day to hour) {
                                            Modifier.border(
                                                1.dp,
                                                selectedOutline,
                                                RoundedCornerShape(1.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
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
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = labelStyle, color = labelColor)
            Spacer(Modifier.width(4.dp))
            listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.copy(alpha = alpha)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("More", style = labelStyle, color = labelColor)
        }
        selected?.let { (day, hour) ->
            val count = grid[day][hour]
            Spacer(Modifier.height(4.dp))
            Text(
                "${dayLabels[day]} ${hourRange(hour)} · $count session" +
                    (if (count == 1) "" else "s"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** "5–6 pm" style label for the one-hour bucket starting at [hour]. */
private fun hourRange(hour: Int): String =
    "${formatHour12(hour)}–${formatHour12((hour + 1) % 24)}"

/** One-sentence TalkBack summary standing in for the whole heatmap grid. */
private fun heatmapSummary(title: String, grid: List<List<Int>>): String {
    val total = grid.sumOf { row -> row.sum() }
    val peak = peakLabel(grid)?.let { " Busiest: $it." } ?: ""
    return "$title heatmap of charging by day of week and hour: " +
        "$total session" + (if (total == 1) "" else "s") + " total." + peak
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
