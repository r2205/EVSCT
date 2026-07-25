package com.evsct.app.ui.stats

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.ui.BarList
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearRecapScreen(
    onBack: () -> Unit,
    viewModel: YearRecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
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

    val snackbarHostState = remember { SnackbarHostState() }
    // Routine successes go to a snackbar; failures render as the titled
    // dialog at the bottom of this composable.
    LaunchedEffect(state.feedback) {
        val fb = state.feedback
        if (fb != null && !fb.asDialog) {
            snackbarHostState.showSnackbar(fb.body)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // The scope belongs on screen, not only in the exported
                    // file's title. Without it a recap that had silently
                    // widened to every session looked identical to a correctly
                    // scoped one — which is how a scoped-to-Unassigned recap
                    // quietly showing everything went unnoticed.
                    Column {
                        Text("Year recap", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.vehicleName ?: "All vehicles",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
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
            RecapMapCard(state, basemap)
            RecapMonthlyCard(state)
            if (state.topBrands.isNotEmpty()) RecapBrandsCard(state)
            state.longestTrip?.let { RecapLongestTripCard(it) }

            Spacer(Modifier.height(16.dp))
            ExportButtons(
                busyOp = state.busyOp,
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

    state.feedback?.let { fb ->
        if (fb.asDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.clearFeedback() },
                icon = if (fb.isError) {
                    {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else null,
                title = { Text(fb.title) },
                text = { Text(fb.body) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearFeedback() }) { Text("OK") }
                },
            )
        }
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
        BarList(
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
        BarList(
            items = state.topBrands,
            labelWidth = 130.dp,
            valueWidth = 92.dp,
            formatValue = { Format.money(it, state.costCurrency) },
        )
    }
}

/**
 * On-screen twin of the HTML export's SVG map — same [RecapMapProjection],
 * same trip colors — so the user sees the map before choosing to export.
 * Renders nothing when no in-year session has coordinates. Coast rings
 * come from the lazily loaded basemap; until it lands (or if the asset is
 * missing) pins and routes still draw, mirroring the export's fallback.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecapMapCard(state: YearRecapUi, basemap: NaBasemap?) {
    val proj = remember(state.mapStops, state.mapTripPaths) {
        RecapMapProjection.fit(state.mapStops, state.mapTripPaths)
    } ?: return

    RecapCard("Charging map") {
        // NOT outlineVariant: in this palette's dark scheme it's the very
        // same tone as the map's surfaceVariant fill (0xFF414940 for both,
        // mirroring baseline M3), which made the coastlines invisible in
        // dark mode. outline is the slot defined to contrast with surfaces
        // in both themes; softened so the basemap stays behind the pins.
        val coastColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        val pinHalo = MaterialTheme.colorScheme.surface
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((proj.width / proj.height).toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // The projection speaks viewport units; scale them to pixels.
            val s = size.width / proj.width.toFloat()
            fun px(lat: Double, lng: Double) = Offset(
                (proj.x(lng) * s).toFloat(),
                (proj.y(lat) * s).toFloat(),
            )

            basemap?.rings?.forEach { ring ->
                if (!proj.overlaps(ring)) return@forEach
                val path = Path()
                ring.forEachIndexed { i, (lat, lng) ->
                    val p = px(lat, lng)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = coastColor, style = Stroke(width = 1.dp.toPx()))
            }

            state.mapTripPaths.forEach { trip ->
                val path = Path()
                trip.points.forEachIndexed { i, (lat, lng) ->
                    val p = px(lat, lng)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path,
                    color = hexColor(trip.colorHex).copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            val pinRadius = 4.dp.toPx()
            val haloWidth = 1.dp.toPx()
            state.mapStops.forEach { stop ->
                val center = px(stop.lat, stop.lng)
                drawCircle(hexColor(stop.colorHex), radius = pinRadius, center = center)
                drawCircle(
                    pinHalo,
                    radius = pinRadius,
                    center = center,
                    style = Stroke(width = haloWidth),
                )
            }
        }
        if (state.mapLegend.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.mapLegend.forEach { (label, hex) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(hexColor(hex)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Parse the recap map's "#RRGGBB" colors into a Compose color; malformed
 *  input falls back to the shared-stop gray instead of throwing. */
private fun hexColor(hex: String): Color =
    runCatching { Color(0xFF000000L or hex.removePrefix("#").toLong(16)) }
        .getOrDefault(Color(0xFF757575))

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

/** Which document format the export buttons below target. */
private enum class RecapFormat(val label: String) { PDF("PDF"), HTML("HTML") }

/**
 * Format picker + Save/Share pair — two buttons instead of the four-button
 * stack this replaced. The picker is disabled while an export runs so the
 * running operation always matches the selected format.
 */
@Composable
private fun ExportButtons(
    busyOp: RecapOp?,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveHtml: () -> Unit,
    onShareHtml: () -> Unit,
) {
    val busy = busyOp != null
    var format by rememberSaveable { mutableStateOf(RecapFormat.PDF) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RecapFormat.entries.forEachIndexed { index, f ->
                SegmentedButton(
                    selected = format == f,
                    onClick = { format = f },
                    enabled = !busy,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = RecapFormat.entries.size,
                    ),
                ) { Text(f.label) }
            }
        }
        Text(
            when (format) {
                RecapFormat.PDF -> "One-page summary — easy to print or archive."
                RecapFormat.HTML ->
                    "Interactive page with the charging map — opens in any browser."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { if (format == RecapFormat.PDF) onSavePdf() else onSaveHtml() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            ExportButtonContent(
                "Save as ${format.label}…",
                busyOp == RecapOp.SAVE_PDF || busyOp == RecapOp.SAVE_HTML,
            )
        }
        OutlinedButton(
            onClick = { if (format == RecapFormat.PDF) onSharePdf() else onShareHtml() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            ExportButtonContent(
                "Share ${format.label}…",
                busyOp == RecapOp.SHARE_PDF || busyOp == RecapOp.SHARE_HTML,
            )
        }
    }
}

/** Button label with a leading spinner while its export runs — progress
 *  shows where the user tapped, not in a bar at the top of the scroll. */
@Composable
private fun ExportButtonContent(text: String, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(text)
}
