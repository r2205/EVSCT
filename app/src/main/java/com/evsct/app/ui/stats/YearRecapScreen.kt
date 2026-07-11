package com.evsct.app.ui.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearRecapScreen(
    onBack: () -> Unit,
    viewModel: YearRecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let { viewModel.saveAsPdf(it) } }

    val saveHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri -> uri?.let { viewModel.saveAsHtml(it) } }

    // Mirror the Full backup share flow: VM writes a recap file to cacheDir,
    // posts it here, we wrap with FileProvider and fire ACTION_SEND. The mime
    // type follows the file extension since the same channel carries both the
    // PDF and HTML exports.
    LaunchedEffect(state.pendingShareFile) {
        val file = state.pendingShareFile ?: return@LaunchedEffect
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val mime = if (file.extension.equals("html", ignoreCase = true)) "text/html" else "application/pdf"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share recap"))
        viewModel.consumePendingShare()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year recap", fontWeight = FontWeight.SemiBold) },
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
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (state.isLoading) {
                // Before the first emission, availableYears is empty and the
                // zero-session branch would flash "No sessions in <year> yet."
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (state.availableYears.isNotEmpty()) {
                YearTabs(
                    years = state.availableYears,
                    selected = state.selectedYear,
                    onSelect = viewModel::setYear,
                )
            }

            if (state.sessionCount == 0) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No sessions in ${state.selectedYear} yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            RecapHeadlineCard(state)
            RecapMonthlyCard(state)
            if (state.topBrands.isNotEmpty()) RecapBrandsCard(state)
            state.longestTrip?.let { RecapLongestTripCard(it) }

            Spacer(Modifier.height(16.dp))
            ExportButtons(
                busy = state.busy,
                onSavePdf = {
                    savePdfLauncher.launch(
                        defaultRecapFilename(state.selectedYear, state.vehicleName, "pdf"),
                    )
                },
                onSharePdf = { viewModel.shareAsPdf() },
                onSaveHtml = {
                    saveHtmlLauncher.launch(
                        defaultRecapFilename(state.selectedYear, state.vehicleName, "html"),
                    )
                },
                onShareHtml = { viewModel.shareAsHtml() },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Result") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearTabs(years: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val selectedIndex = years.indexOf(selected).coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        years.forEachIndexed { index, year ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(year) },
                text = {
                    Text(
                        year.toString(),
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun RecapHeadlineCard(state: YearRecapUi) {
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
                HeadlineStat("Sessions", state.sessionCount.toString())
                HeadlineStat("Total cost", Format.money(state.totalCost, state.costCurrency))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                HeadlineStat("Energy", Format.kwh(state.totalKwh))
                HeadlineStat("Distance", Format.distance(state.totalDistanceKm, units.useMiles))
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

@Composable
private fun HeadlineStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RecapCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RecapMonthlyCard(state: YearRecapUi) {
    RecapCard("Monthly cost") {
        SimpleBarList(
            items = state.monthlyCost,
            labelWidth = 40.dp,
            valueWidth = 92.dp,
            formatValue = { Format.money(it, state.costCurrency) },
        )
    }
}

@Composable
private fun RecapBrandsCard(state: YearRecapUi) {
    RecapCard("Top brands") {
        SimpleBarList(
            items = state.topBrands,
            labelWidth = 130.dp,
            valueWidth = 92.dp,
            formatValue = { Format.money(it, state.costCurrency) },
        )
    }
}

@Composable
private fun RecapLongestTripCard(trip: LongestTripSummary) {
    val units = LocalUserUnits.current
    RecapCard("Longest trip") {
        Text(trip.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        val parts = buildList {
            add(Format.distance(trip.distanceKm, units.useMiles))
            add("${trip.sessionCount} session" + if (trip.sessionCount == 1) "" else "s")
            if (trip.totalCost != null && trip.currency != null) {
                add(Format.money(trip.totalCost, trip.currency))
            }
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Local copy of StatsScreen's BarList — kept private to this file rather
 * than coupling YearRecap's layout to StatsScreen's evolving private API.
 * Each row is `[label] [bar———] [value]` with bars normalized to the
 * largest value in [items].
 */
@Composable
private fun SimpleBarList(
    items: List<Pair<String, Double>>,
    labelWidth: androidx.compose.ui.unit.Dp,
    valueWidth: androidx.compose.ui.unit.Dp,
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
                    modifier = Modifier.width(valueWidth),
                )
            }
        }
    }
}

@Composable
private fun ExportButtons(
    busy: Boolean,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveHtml: () -> Unit,
    onShareHtml: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSavePdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) { Text("Save as PDF…") }
        OutlinedButton(
            onClick = onSharePdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) { Text("Share PDF…") }
        Button(
            onClick = onSaveHtml,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) { Text("Save as HTML…") }
        OutlinedButton(
            onClick = onShareHtml,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) { Text("Share HTML…") }
    }
}
