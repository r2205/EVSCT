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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.R
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.ui.BarList
import com.evsct.app.ui.EvsctBarTitle
import com.evsct.app.ui.MoneyStat
import com.evsct.app.ui.STACK_STATS_FONT_SCALE
import com.evsct.app.ui.StatColumns
import com.evsct.app.ui.VehicleScope
import com.evsct.app.ui.VehicleScopeTabs
import com.evsct.app.ui.needsVehiclePicker
import com.evsct.app.ui.forType
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.ui.theme.LocalEvAccents
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenYearRecap: (VehicleScope) -> Unit,
    onOpenLogForBrand: (brand: String, scope: VehicleScope) -> Unit,
    onOpenVehicles: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { EvsctBarTitle(stringResource(R.string.nav_stats)) },
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
                        onClick = { onOpenYearRecap(state.vehicleScope) },
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
                        Text(stringResource(R.string.stats_recap))
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

            if (needsVehiclePicker(state.vehicles.size, state.hasUnassignedSessions)) {
                VehicleScopeTabs(
                    vehicles = state.vehicles,
                    includeUnassigned = state.hasUnassignedSessions,
                    scope = state.vehicleScope,
                    onSelect = viewModel::setVehicleScope,
                    onManageVehicles = onOpenVehicles,
                )
            }

            HeadlineCard(state)

            if (state.thisMonthHasDriving) {
                GasSavingsCard(state)
            }

            if (state.sessionCount == 0) {
                com.evsct.app.ui.EmptyState(
                    icon = Icons.Default.QueryStats,
                    title = stringResource(R.string.stats_no_sessions_yet),
                    body = stringResource(R.string.stats_stats_appear_here_once),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                return@Column
            }

            ChartWindowSelector(
                selected = state.chartWindow,
                onSelect = viewModel::setChartWindow,
            )

            val bucketNoun = when (state.chartWindow) {
                StatsChartWindow.LAST_12_MONTHS -> stringResource(R.string.stats_bucket_month)
                StatsChartWindow.ALL_YEARS -> stringResource(R.string.stats_bucket_year)
            }
            ChartCard(stringResource(R.string.stats_cost_by, bucketNoun)) {
                BarList(
                    items = state.costSeries,
                    labelWidth = 64.dp,
                    formatValue = { Format.money(it, state.costCurrency) },
                )
            }

            ChartCard(stringResource(R.string.stats_energy_by, bucketNoun)) {
                BarList(
                    items = state.energySeries,
                    labelWidth = 64.dp,
                    formatValue = { Format.kwh(it) },
                )
            }

            if (state.byBrandCost.isNotEmpty()) {
                ChartCard(
                    stringResource(R.string.stats_top_brands),
                    subtitle = stringResource(R.string.stats_tap_a_brand_to),
                ) {
                    BarList(
                        items = state.byBrandCost,
                        labelWidth = 130.dp,
                        formatValue = { Format.money(it, state.costCurrency) },
                        onRowClick = { brand ->
                            onOpenLogForBrand(brand, state.vehicleScope)
                        },
                    )
                }
            }

            if (state.byType.values.sum() > 0) {
                ChartCard(stringResource(R.string.stats_charging_type)) {
                    TypeSplitBar(state.byType)
                    Spacer(Modifier.height(8.dp))
                    TypeLegend(state.byType)
                }
            }

            val anyDc = state.dcFastByDayHour.any { row -> row.any { it > 0 } }
            val anyAc = state.acByDayHour.any { row -> row.any { it > 0 } }
            if (anyDc || anyAc) {
                ChartCard(
                    stringResource(R.string.stats_when_you_charge),
                    subtitle = stringResource(R.string.stats_rows_are_days_columns),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        val accents = LocalEvAccents.current
                        if (anyDc) {
                            TimeOfDayHeatmap(
                                title = stringResource(R.string.stats_dc_fast),
                                grid = state.dcFastByDayHour,
                                accent = accents.dcFast.accent,
                            )
                        }
                        if (anyAc) {
                            TimeOfDayHeatmap(
                                title = stringResource(R.string.stats_ac_l2_l1),
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
            // StatColumns decides row-versus-stacked; see ui/StatStacking.kt for
            // why wrapping alone wasn't enough. The second row is the reason
            // this is shared rather than done twice: two stats, so a wrap leaves
            // each one floating alone and centred on its own line.
            StatColumns(modifier = Modifier.fillMaxWidth()) { statModifier ->
                Stat(stringResource(R.string.common_sessions), state.sessionCount.toString(), statModifier)
                // Shared multi-currency stat: one line per currency, so the
                // headline never has to silently drop foreign-currency spend
                // the way the single-currency charts below do.
                MoneyStat(stringResource(R.string.common_total_cost), state.totalCostByCurrency, statModifier)
                Stat(stringResource(R.string.common_energy), Format.kwh(state.totalEnergyKwh), statModifier)
            }
            Spacer(Modifier.height(12.dp))
            StatColumns(modifier = Modifier.fillMaxWidth()) { statModifier ->
                Stat(stringResource(R.string.stats_avg_eff_price), Format.moneyRate(state.avgEffPricePerKwh, "kWh"), statModifier)
                Stat(stringResource(R.string.common_avg_power), Format.kw(state.avgPowerKw), statModifier)
            }
            if (state.excludedByCurrency > 0) {
                val n = state.excludedByCurrency
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.stats_excluded_by_currency, n, state.costCurrency, n,
                    ),
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
                stringResource(R.string.stats_vs_gas_this_month),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (savedPositive) stringResource(R.string.stats_saved, savedAbsText)
                else stringResource(R.string.stats_paid_more, savedAbsText),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (savedPositive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.stats_charging_vs_gas,
                    Format.money(state.thisMonthCost, state.costCurrency),
                    Format.money(state.thisMonthGasCost, state.costCurrency),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.stats_gas_assumptions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
    val dayLabels = stringArrayResource(R.array.stats_day_labels)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            peakLabel(grid, dayLabels)?.let { peak ->
                Text(
                    stringResource(R.string.stats_peak, peak),
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
                contentDescription = heatmapSummary(title, grid, dayLabels)
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
            stringArrayResource(R.array.stats_hour_ticks).forEach { tick ->
                Text(tick, style = labelStyle, color = labelColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stats_less), style = labelStyle, color = labelColor)
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
            Text(stringResource(R.string.stats_more), style = labelStyle, color = labelColor)
        }
        selected?.let { (day, hour) ->
            val count = grid[day][hour]
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(
                    R.plurals.stats_heatmap_cell, count, dayLabels[day], hourRange(hour), count,
                ),
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
private fun heatmapSummary(title: String, grid: List<List<Int>>, dayLabels: Array<String>): String {
    val total = grid.sumOf { row -> row.sum() }
    val peak = peakLabel(grid, dayLabels)?.let { " Busiest: $it." } ?: ""
    return "$title heatmap of charging by day of week and hour: " +
        "$total session" + (if (total == 1) "" else "s") + " total." + peak
}

/** "Sat 2 pm" string for the busiest cell, or null when the grid is empty. */
private fun peakLabel(grid: List<List<Int>>, dayLabels: Array<String>): String? {
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
    val day = dayLabels[bestDay]
    return "$day ${formatHour12(bestHour)}"
}

private fun formatHour12(h: Int): String = when {
    h == 0 -> "12 am"
    h < 12 -> "$h am"
    h == 12 -> "noon"
    else -> "${h - 12} pm"
}

private fun ChargingType.shortLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC FAST"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}


/* ------------------------------- Previews -------------------------------- */

/*
 * The headline had no previews, which is why its two SpaceAround rows went
 * unexamined through #50 and #51 — the same defect the Log's summary card was
 * just fixed for, and the second row is the sharper case because it holds only
 * two stats.
 *
 * The thresholds are named rather than typed twice, so these stay pinned to the
 * value in ui/StatStacking.kt if it ever moves.
 */

private fun previewStats(mixedCurrency: Boolean = false) = StatsUi(
    isLoading = false,
    sessionCount = 128,
    totalEnergyKwh = 3_412.75,
    totalCostByCurrency = CurrencyTotals(
        if (mixedCurrency) mapOf("CAD" to 1_284.50, "USD" to 96.20)
        else mapOf("CAD" to 1_284.50)
    ),
    avgEffPricePerKwh = 0.376,
    avgPowerKw = 84.2,
)

@Preview(name = "Headline — normal", showBackground = true, widthDp = 400)
@Composable
private fun PreviewHeadlineNormal() {
    EvsctTheme { HeadlineCard(previewStats()) }
}

/** Exactly at the threshold, so this is the first stacked step. */
@Preview(
    name = "Headline — stacked at threshold",
    showBackground = true,
    widthDp = 400,
    fontScale = STACK_STATS_FONT_SCALE,
)
@Composable
private fun PreviewHeadlineStacked() {
    EvsctTheme { HeadlineCard(previewStats(mixedCurrency = true)) }
}

/** One step below the threshold: still a wrapped row, which is the branch the
 *  stacking replaces and therefore the one worth keeping an eye on. */
@Preview(
    name = "Headline — 1.3x, still a row",
    showBackground = true,
    widthDp = 400,
    fontScale = 1.3f,
)
@Composable
private fun PreviewHeadlineBelowThreshold() {
    EvsctTheme { HeadlineCard(previewStats(mixedCurrency = true)) }
}
