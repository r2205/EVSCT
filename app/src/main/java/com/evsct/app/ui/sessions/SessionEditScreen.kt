package com.evsct.app.ui.sessions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.util.Brands
import com.evsct.app.util.DurationFormat
import com.evsct.app.util.ReceiptImageStore
import com.evsct.app.util.Tags
import com.evsct.app.util.Format
import java.util.Calendar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditScreen(
    onDone: () -> Unit,
    onPickLocation: (lat: Double?, lng: Double?) -> Unit = { _, _ -> },
    pickedLat: Double? = null,
    pickedLng: Double? = null,
    onPickedConsumed: () -> Unit = {},
    viewModel: SessionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The map picker (a sibling nav destination) writes its result into
    // this screen's SavedStateHandle and pops back. NavGraph reads those
    // keys and passes them in as pickedLat/pickedLng; apply once and tell
    // the caller to clear them so we don't re-apply on recomposition.
    LaunchedEffect(pickedLat, pickedLng) {
        if (pickedLat != null && pickedLng != null) {
            viewModel.applyPickedLocation(pickedLat, pickedLng)
            onPickedConsumed()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOdometerWarning by remember { mutableStateOf(false) }

    fun trySave() {
        if (state.odometerText.isBlank()) showOdometerWarning = true
        else viewModel.save(onDone)
    }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.autofillFromLocation()
        } else {
            viewModel.update {
                it.copy(transientMessage = "Location permission denied.")
            }
        }
    }

    val receiptPhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addReceipt(it) } }

    val receiptPdfPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.addReceipt(it) } }

    var receiptToPreview by remember { mutableStateOf<String?>(null) }
    var receiptToRename by remember { mutableStateOf<UiReceipt?>(null) }
    var showReceiptChooser by remember { mutableStateOf(false) }

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTransientMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "New session" else "Edit session",
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
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = { trySave() }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.hints.isNotEmpty()) {
                ValidationHintsCard(state.hints)
            }

            DateTimeRow(
                epoch = state.sessionStart,
                onPickDateTime = { newEpoch -> viewModel.update { it.copy(sessionStart = newEpoch) } },
                context = context,
            )

            SectionLabel("Vehicle")
            VehiclePicker(state) { id -> viewModel.update { it.copy(vehicleId = id) } }

            ChargingTypeRow(state.chargingType) { type ->
                viewModel.update { it.copy(chargingType = type) }
            }

            PricingModelRow(state.pricingModel) { pm ->
                viewModel.update { it.copy(pricingModel = pm) }
            }

            val warnedFields = remember(state.hints) {
                state.hints.flatMap { it.fields }.toSet()
            }

            SectionLabel("Session")
            NumberField(
                label = "Odometer (${com.evsct.app.util.Units.distanceUnit(state.useMiles)})",
                value = state.odometerText,
                isError = HintField.ODOMETER in warnedFields,
            ) { v -> viewModel.update { it.copy(odometerText = v) } }
            NumberField(
                label = "Energy delivered (kWh)",
                value = state.energyText,
                isError = HintField.ENERGY in warnedFields,
            ) { v -> viewModel.update { it.copy(energyText = v) } }
            NumberField(
                label = "Total cost (${state.currency})",
                value = state.costText,
                isError = HintField.COST in warnedFields,
            ) { v -> viewModel.update { it.copy(costText = v) } }
            CurrencyChips(
                selected = state.currency,
                onSelect = { c -> viewModel.update { it.copy(currency = c) } },
            )
            DurationField(
                value = state.durationText,
                isError = HintField.DURATION in warnedFields,
                onValue = { v -> viewModel.update { it.copy(durationText = v) } },
            )
            if (state.isTracking) {
                LiveElapsedChip(
                    sessionStart = state.sessionStart,
                    onUse = { elapsedSec ->
                        viewModel.update {
                            it.copy(durationText = DurationFormat.pretty(elapsedSec))
                        }
                    },
                )
            }
            NumberField(
                label = "Wait time (min, optional)",
                value = state.waitTimeText,
                onValue = { v -> viewModel.update { it.copy(waitTimeText = v) } },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumberField(
                    label = "Battery start %",
                    value = state.batteryStartText,
                    modifier = Modifier.weight(1f),
                    isError = HintField.BATTERY_START in warnedFields,
                    onValue = { v -> viewModel.update { it.copy(batteryStartText = v) } },
                )
                NumberField(
                    label = "Battery end %",
                    value = state.batteryEndText,
                    modifier = Modifier.weight(1f),
                    isError = HintField.BATTERY_END in warnedFields,
                    onValue = { v -> viewModel.update { it.copy(batteryEndText = v) } },
                )
            }

            SectionLabel("Posted rates (optional)")
            NumberField(
                label = "Posted energy price ($/kWh)",
                value = state.postedEnergyPriceText,
                isError = HintField.POSTED_ENERGY_PRICE in warnedFields,
            ) { v -> viewModel.update { it.copy(postedEnergyPriceText = v) } }
            NumberField(
                label = "Posted time-based rate ($/min)",
                value = state.postedTimeRateText,
                isError = HintField.POSTED_TIME_RATE in warnedFields,
            ) { v -> viewModel.update { it.copy(postedTimeRateText = v) } }
            NumberField(
                label = "Posted max power (kW)",
                value = state.postedMaxPowerText,
                isError = HintField.POSTED_MAX_POWER in warnedFields,
            ) { v ->
                viewModel.update { it.copy(postedMaxPowerText = v) }
            }

            SectionLabel("Station")
            if (state.recentStops.isNotEmpty()) {
                RecentStopsButton(
                    stops = state.recentStops,
                    onPick = { viewModel.applyStop(it) },
                )
            }
            BrandPicker(state.brand, state.brandSuggestions) { v ->
                viewModel.update { it.copy(brand = v) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LocationAutofillCard(
                    isLoading = state.isFetchingLocation,
                    onRequestAutofill = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                PickLocationCard(
                    onPick = { onPickLocation(state.latitude, state.longitude) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFieldPlain(
                    label = "City",
                    value = state.city,
                    modifier = Modifier.weight(2f),
                    onValue = { v -> viewModel.update { it.copy(city = v) } },
                )
                RegionField(
                    value = state.province,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> viewModel.update { it.copy(province = v) } },
                )
            }
            TextFieldPlain("Address", state.address) { v ->
                viewModel.update { it.copy(address = v) }
            }
            TextFieldPlain("Station / stall name / stall number", state.stationName) { v ->
                viewModel.update { it.copy(stationName = v) }
            }

            SectionLabel("Trip")
            TripPicker(state) { id -> viewModel.update { it.copy(tripId = id) } }
            ContinuesPreviousToggle(
                checked = state.continuesPrevious,
                onCheckedChange = { v -> viewModel.update { it.copy(continuesPrevious = v) } },
            )

            SectionLabel("Receipts")
            ReceiptsCard(
                receipts = state.receipts,
                onAdd = { showReceiptChooser = true },
                onRemove = { path -> viewModel.removeReceipt(path) },
                onRename = { receipt -> receiptToRename = receipt },
                onPreview = { path ->
                    if (ReceiptImageStore.isPdf(path)) {
                        openReceiptExternally(context, path)
                    } else {
                        receiptToPreview = path
                    }
                },
            )

            SectionLabel("Tags")
            TagsField(
                tags = state.tags,
                onAdd = { tag ->
                    viewModel.update { it.copy(tags = Tags.add(it.tags, tag)) }
                },
                onRemove = { tag ->
                    viewModel.update { it.copy(tags = Tags.remove(it.tags, tag)) }
                },
            )

            SectionLabel("Notes")
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                label = { Text("Notes") },
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    receiptToPreview?.let { path ->
        ReceiptPreviewDialog(
            relativePath = path,
            onDismiss = { receiptToPreview = null },
        )
    }

    receiptToRename?.let { receipt ->
        RenameReceiptDialog(
            initial = receipt.originalFileName.orEmpty(),
            onConfirm = { newName ->
                viewModel.renameReceipt(receipt.filePath, newName)
                receiptToRename = null
            },
            onDismiss = { receiptToRename = null },
        )
    }

    if (showReceiptChooser) {
        ReceiptKindChooser(
            onPickPhoto = {
                showReceiptChooser = false
                receiptPhotoPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onPickPdf = {
                showReceiptChooser = false
                receiptPdfPicker.launch(arrayOf("application/pdf"))
            },
            onDismiss = { showReceiptChooser = false },
        )
    }

    if (showOdometerWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOdometerWarning = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Save without odometer?") },
            text = {
                Text(
                    "The odometer reading helps with trip distance and efficiency stats. " +
                        "You can still save without it."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showOdometerWarning = false
                    viewModel.save(onDone)
                }) { Text("Save anyway") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOdometerWarning = false }) {
                    Text("Add odometer")
                }
            },
        )
    }


    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete this session?") },
            text = { Text("This will permanently remove this charging session from your log.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAndExit(onDone)
                    },
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DateTimeRow(
    epoch: Long,
    onPickDateTime: (Long) -> Unit,
    context: android.content.Context,
) {
    val cal = remember(epoch) { Calendar.getInstance().apply { timeInMillis = epoch } }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Date / time", style = MaterialTheme.typography.labelSmall)
                Text(Format.dateTime(epoch), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                onPickDateTime(cal.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    label = { Text("Date") },
                )
                AssistChip(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                cal.set(Calendar.HOUR_OF_DAY, h)
                                cal.set(Calendar.MINUTE, m)
                                cal.set(Calendar.SECOND, 0)
                                onPickDateTime(cal.timeInMillis)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    label = { Text("Time") },
                )
            }
        }
    }
}

@Composable
private fun ChargingTypeRow(current: ChargingType, onPick: (ChargingType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChargingType.entries.forEach { t ->
            FilterChip(
                selected = t == current,
                onClick = { onPick(t) },
                label = { Text(t.displayName()) },
            )
        }
    }
}

@Composable
private fun PricingModelRow(current: PricingModel, onPick: (PricingModel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PricingModel.entries.forEach { p ->
            FilterChip(
                selected = p == current,
                onClick = { onPick(p) },
                label = { Text(p.displayName()) },
            )
        }
    }
}

private fun ChargingType.displayName(): String = when (this) {
    ChargingType.DC_FAST -> "DC Fast"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}

private fun PricingModel.displayName(): String = when (this) {
    PricingModel.PER_KWH -> "$/kWh"
    PricingModel.PER_MINUTE -> "$/min"
    PricingModel.FLAT -> "Flat"
    PricingModel.FREE -> "Free"
    PricingModel.HYBRID -> "Hybrid"
}

@Composable
private fun CurrencyChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        com.evsct.app.data.prefs.AppPreferences.SUPPORTED_CURRENCIES.forEach { code ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(code) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandPicker(
    value: String,
    history: List<String>,
    onValue: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text("Station brand") },
            placeholder = { Text("Tap to choose…") },
            readOnly = true,
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Open brand picker",
                )
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }

    if (showSheet) {
        BrandPickerSheet(
            current = value,
            history = history,
            onPick = { picked ->
                onValue(picked)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandPickerSheet(
    current: String,
    history: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val historySet = remember(history) { history.toSet() }
    val curatedNotInHistory = remember(historySet) { Brands.SUGGESTED.filter { it !in historySet } }

    val q = query.trim()
    val filteredHistory = if (q.isEmpty()) history else history.filter { it.contains(q, ignoreCase = true) }
    val filteredCurated = if (q.isEmpty()) curatedNotInHistory
        else curatedNotInHistory.filter { it.contains(q, ignoreCase = true) }
    val showCustomOption = q.isNotEmpty() &&
        history.none { it.equals(q, ignoreCase = true) } &&
        Brands.SUGGESTED.none { it.equals(q, ignoreCase = true) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Choose a brand",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search or type a custom brand…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
            )

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                if (showCustomOption) {
                    item {
                        BrandRow(
                            label = "Use \"$q\"",
                            isCurrent = false,
                            onClick = { onPick(q) },
                        )
                    }
                    item { androidx.compose.material3.HorizontalDivider() }
                }
                if (filteredHistory.isNotEmpty()) {
                    item { BrandSectionHeader("Used in your sessions") }
                    items(filteredHistory) { brand ->
                        BrandRow(
                            label = brand,
                            isCurrent = brand.equals(current, ignoreCase = true),
                            onClick = { onPick(brand) },
                        )
                    }
                }
                if (filteredCurated.isNotEmpty()) {
                    item { BrandSectionHeader("All networks") }
                    items(filteredCurated) { brand ->
                        BrandRow(
                            label = brand,
                            isCurrent = brand.equals(current, ignoreCase = true),
                            onClick = { onPick(brand) },
                        )
                    }
                }
                if (filteredHistory.isEmpty() && filteredCurated.isEmpty() && !showCustomOption) {
                    item {
                        Text(
                            "No matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BrandSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun BrandRow(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Currently selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripPicker(state: SessionEditUi, onPick: (Long?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(null) },
            label = { Text("None") },
            colors = if (state.tripId == null) AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else AssistChipDefaults.assistChipColors(),
        )
        state.trips.forEach { trip ->
            AssistChip(
                onClick = { onPick(trip.id) },
                label = { Text(trip.name) },
                colors = if (state.tripId == trip.id) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun ContinuesPreviousToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Continues from previous session", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tick if no untracked charging happened since your last logged session " +
                    "(extends km/kWh stats across trips).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclePicker(state: SessionEditUi, onPick: (Long?) -> Unit) {
    if (state.vehicles.isEmpty()) {
        Text(
            "Add a vehicle from Settings to tag sessions by car.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(null) },
            label = { Text("Unassigned") },
            colors = if (state.vehicleId == null) AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else AssistChipDefaults.assistChipColors(),
        )
        state.vehicles.forEach { vehicle ->
            AssistChip(
                onClick = { onPick(vehicle.id) },
                label = { Text(vehicle.name) },
                colors = if (state.vehicleId == vehicle.id) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun RegionField(
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Prov / State") },
        placeholder = { Text("e.g. SK") },
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            val nowFocused = focusState.isFocused
            if (hasFocus && !nowFocused) {
                val normalized = com.evsct.app.util.RegionCodes.normalize(value)
                if (normalized != value) onValue(normalized)
            }
            hasFocus = nowFocused
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentStopsButton(
    stops: List<RecentStop>,
    onPick: (RecentStop) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(
        onClick = { showSheet = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text("Use a recent stop…")
    }
    if (showSheet) {
        RecentStopsSheet(
            stops = stops,
            onPick = {
                onPick(it)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentStopsSheet(
    stops: List<RecentStop>,
    onPick: (RecentStop) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = if (q.isEmpty()) stops else stops.filter {
        listOfNotNull(it.brand, it.city, it.province, it.address, it.stationName)
            .any { v -> v.contains(q, ignoreCase = true) }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Recent stops",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search brand, city, address…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
            )

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    items(filtered, key = { "${it.brand}|${it.address}|${it.city}|${it.lastUsedAt}" }) { stop ->
                        RecentStopRow(stop, onClick = { onPick(stop) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RecentStopRow(stop: RecentStop, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stop.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            stop.secondary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (stop.visits == 1) "1 visit" else "${stop.visits} visits",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocationAutofillCard(
    isLoading: Boolean,
    onRequestAutofill: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Card(
        modifier = modifier
            .clickable(enabled = !isLoading, onClick = onRequestAutofill),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isLoading) "Finding…" else "Use GPS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    "Auto-fill from your location",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Companion to [LocationAutofillCard] for sessions where GPS isn't an
 *  option (e.g. logging from home, fixing a past entry). Tapping opens the
 *  map picker so the user can drop the pin exactly where the charger is. */
@Composable
private fun PickLocationCard(
    onPick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Card(
        modifier = modifier.clickable(onClick = onPick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Pick on map",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    "Drop a pin manually",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DurationField(
    value: String,
    isError: Boolean = false,
    onValue: (String) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }

    // External value changes (e.g. focus-driven pretty <-> editable swap) need to
    // be reflected in our local TextFieldValue so the cursor stays sane.
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fv ->
            fieldValue = fv
            if (fv.text != value) onValue(fv.text)
        },
        label = { Text("Charging duration") },
        placeholder = { Text("e.g. 25  ·  1:25  ·  0:11:00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        isError = isError,
        trailingIcon = {
            // The phone keypad has no `:`, so insert one at the cursor position
            // when the user taps this button. Visible only while the field is
            // focused so the resting field stays clean.
            if (hasFocus) {
                IconButton(onClick = {
                    val sel = fieldValue.selection
                    val text = fieldValue.text
                    val before = text.substring(0, sel.min)
                    val after = text.substring(sel.max)
                    val newText = "$before:$after"
                    val newCursor = sel.min + 1
                    fieldValue = TextFieldValue(newText, TextRange(newCursor))
                    onValue(newText)
                }) {
                    Text(
                        ":",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (hasFocus && !nowFocused) {
                    DurationFormat.parse(fieldValue.text)?.let {
                        onValue(DurationFormat.pretty(it))
                    }
                } else if (!hasFocus && nowFocused) {
                    DurationFormat.parse(fieldValue.text)?.let {
                        onValue(DurationFormat.editable(it))
                    }
                }
                hasFocus = nowFocused
            },
    )
}

/**
 * Free-form tag editor: existing tags render as InputChips with an X to
 * remove; the bottom field accepts new tags. Submitting via Enter or by
 * typing a comma commits the chip and clears the field — the comma path
 * lets users keep typing through "work, winter, fast" without reaching
 * for the Done key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsField(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val commit: () -> Unit = {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty()) onAdd(trimmed)
        draft = ""
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { v ->
                // Comma is the storage delimiter — treat it as a "commit this
                // chip" gesture rather than letting it land in the chip name.
                if (v.endsWith(",")) {
                    val candidate = v.dropLast(1).trim()
                    if (candidate.isNotEmpty()) onAdd(candidate)
                    draft = ""
                } else {
                    draft = v
                }
            },
            label = { Text("Add tag…") },
            placeholder = { Text("e.g. work charge") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isError: Boolean = false,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = isError,
        modifier = modifier,
    )
}

@Composable
private fun TextFieldPlain(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}


@Composable
private fun ReceiptsCard(
    receipts: List<UiReceipt>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRename: (UiReceipt) -> Unit,
    onPreview: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (receipts.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Attach receipts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            "Photo or PDF — add as many as you need.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                receipts.forEachIndexed { idx, r ->
                    if (idx > 0) Spacer(Modifier.height(12.dp))
                    ReceiptTile(
                        path = r.filePath,
                        originalFileName = r.originalFileName,
                        onPreview = { onPreview(r.filePath) },
                        onRemove = { onRemove(r.filePath) },
                        onRename = { onRename(r) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add another")
                }
            }
        }
    }
}

@Composable
private fun ReceiptTile(
    path: String,
    originalFileName: String?,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = File(ctx.filesDir, path)
    val isPdf = ReceiptImageStore.isPdf(path)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            if (isPdf) {
                PdfThumbnail(originalFileName = originalFileName)
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Rename is only useful on PDFs — photos don't show a filename
            // anywhere in the UI, so the action would be invisible.
            if (isPdf) {
                androidx.compose.material3.TextButton(onClick = onRename) { Text("Rename") }
                Spacer(Modifier.width(4.dp))
            }
            androidx.compose.material3.TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun PdfThumbnail(originalFileName: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                originalFileName?.takeIf { it.isNotBlank() } ?: "PDF receipt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "Tap to open in your PDF viewer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lets the user backfill (or correct) the display name of an existing PDF
 * receipt. The on-disk file stays UUID-named; only the [SessionReceipt.
 * originalFileName] label is rewritten. Leaving the field blank clears the
 * label and the tile falls back to the generic "PDF receipt" caption.
 */
@Composable
private fun RenameReceiptDialog(
    initial: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename receipt") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("File name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(draft.trim().takeIf { it.isNotEmpty() }) },
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptKindChooser(
    onPickPhoto: () -> Unit,
    onPickPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Attach receipt as…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ChooserRow(
                icon = Icons.Default.Image,
                title = "Photo",
                subtitle = "Pick from your gallery or camera roll.",
                onClick = onPickPhoto,
            )
            ChooserRow(
                icon = Icons.Default.PictureAsPdf,
                title = "PDF",
                subtitle = "Pick a PDF file from your device.",
                onClick = onPickPdf,
            )
        }
    }
}

@Composable
private fun ChooserRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openReceiptExternally(context: android.content.Context, relativePath: String) {
    val file = File(context.filesDir, relativePath)
    if (!file.exists()) return
    val authority = "${context.packageName}.fileprovider"
    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, ReceiptImageStore.mimeType(relativePath))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(intent, "Open receipt")
    runCatching { context.startActivity(chooser) }
}

@Composable
private fun ReceiptPreviewDialog(
    relativePath: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = File(ctx.filesDir, relativePath)
    com.evsct.app.ui.ImageZoomDialog(
        model = file,
        contentDescription = "Receipt photo (full screen)",
        onDismiss = onDismiss,
    )
}

@Composable
private fun ValidationHintsCard(hints: List<ValidationHint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = com.evsct.app.ui.theme.EvAccents.DcFastContainer,
            contentColor = Color(0xFF3B2400),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hints.size == 1) "Heads-up" else "${hints.size} things to double-check",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            hints.forEach { hint ->
                Spacer(Modifier.height(8.dp))
                Text(
                    hint.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    hint.detail,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Live-ticking chip that shows the elapsed time since [sessionStart] for a
 * tracked charge. Tapping it fills the form's duration field with the
 * current elapsed seconds. Lets the user pin the latest stopwatch value at
 * the moment they actually finish charging — without having to compute it
 * by hand or remember when they plugged in.
 */
@Composable
private fun LiveElapsedChip(sessionStart: Long, onUse: (Long) -> Unit) {
    var nowMs by remember(sessionStart) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sessionStart) {
        // 1s tick is fine — Compose only recomposes the chip itself, and the
        // user is unlikely to stare at this for hours. The effect cancels
        // automatically when the chip leaves the composition.
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsedSec = ((nowMs - sessionStart).coerceAtLeast(0L)) / 1000L
    val label = "Use elapsed: ${DurationFormat.editable(elapsedSec)}"
    AssistChip(
        onClick = { onUse(elapsedSec) },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    )
}
