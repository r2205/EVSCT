package com.evsct.app.ui.sessions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.util.Brands
import com.evsct.app.util.DurationFormat
import com.evsct.app.util.Format
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditScreen(
    onDone: () -> Unit,
    viewModel: SessionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                it.copy(locationMessage = "Location permission denied.")
            }
        }
    }

    val receiptPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.pickReceipt(it) } }

    var receiptToPreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.locationMessage) {
        state.locationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearLocationMessage()
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                label = "Odometer (km)",
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
            DurationField(
                value = state.durationText,
                isError = HintField.DURATION in warnedFields,
                onValue = { v -> viewModel.update { it.copy(durationText = v) } },
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
            )
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
            TextFieldPlain("Station / stall name", state.stationName) { v ->
                viewModel.update { it.copy(stationName = v) }
            }

            SectionLabel("Vehicle")
            VehiclePicker(state) { id -> viewModel.update { it.copy(vehicleId = id) } }

            SectionLabel("Trip")
            TripPicker(state) { id -> viewModel.update { it.copy(tripId = id) } }

            SectionLabel("Receipt")
            ReceiptCard(
                imagePath = state.receiptImagePath,
                onPick = {
                    receiptPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onClear = { viewModel.clearReceipt() },
                onPreview = { receiptToPreview = state.receiptImagePath },
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
        modifier = Modifier.fillMaxWidth(),
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
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                    if (isLoading) "Finding location…" else "Use current location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    "Auto-fills city, prov/state, and address from GPS.",
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
private fun ReceiptCard(
    imagePath: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onPreview: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = imagePath?.let { File(ctx.filesDir, it) }
    val hasImage = file != null && file.exists()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (hasImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPreview),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Receipt photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPick) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Change")
                    }
                    OutlinedButton(onClick = onClear) { Text("Remove") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPick)
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
                            "Attach a receipt photo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            "Useful for expense reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptPreviewDialog(
    relativePath: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = File(ctx.filesDir, relativePath)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun resetTransform() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                // Pinch to zoom, drag to pan when zoomed.
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                // Single tap dismisses while at native size; double tap toggles zoom.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale == 1f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) resetTransform() else scale = 2.5f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Receipt photo (full screen)",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }
        }
    }
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
