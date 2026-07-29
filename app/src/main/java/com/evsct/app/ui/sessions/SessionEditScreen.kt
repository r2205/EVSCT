package com.evsct.app.ui.sessions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.evsct.app.R
import com.evsct.app.ui.readableFormWidth
import com.evsct.app.ui.theme.EvsctTheme
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
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

    // Re-arm the ViewModel's exit latch whenever this nav entry reaches
    // RESUMED (LocalLifecycleOwner inside a NavHost is the back-stack
    // entry). A screen whose post-save pop actually ran never resumes
    // again — so resuming with the latch set means ifResumed dropped the
    // pop mid-transition, and without the reset Save/Delete would stay
    // dead on a screen that's still visible.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

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
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val odometerFocusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    fun trySave() {
        if (state.odometerText.isBlank()) showOdometerWarning = true
        else viewModel.save(onDone)
    }

    val dirty = viewModel.isDirty(state)
    fun requestExit() {
        if (dirty) showDiscardConfirm = true else onDone()
    }
    // System back gets the same guard as the toolbar arrow. Enabled only
    // while dirty so a clean form keeps the default (unintercepted) pop.
    BackHandler(enabled = dirty) { showDiscardConfirm = true }

    // Resolved in composition: the launcher callback is not a composable scope.
    val locationDeniedMsg = stringResource(R.string.form_location_denied)
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.autofillFromLocation()
        } else {
            viewModel.update {
                it.copy(transientMessage = locationDeniedMsg)
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
                        when {
                            // Until the load lands, isNew defaults true even
                            // for an existing session — don't claim either.
                            state.isLoading -> ""
                            state.isNew -> stringResource(R.string.form_title_new)
                            else -> stringResource(R.string.form_title_edit)
                        },
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
                    IconButton(onClick = { requestExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.form_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.form_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Save sits at the foot of the form, which is where a two-dozen
            // input scroll actually ends — the top-bar check was a full
            // scroll away from the last field the user filled in. Deliberately
            // the ONLY save: two of them is a real ambiguity on a form this
            // long, so the check moved rather than being duplicated. Delete
            // stays in the top bar, away from the button thumbs land on.
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider()
                    Box(
                        // windowInsetsPadding, not navigationBarsPadding: it
                        // honors what the nav graph's Scaffold already
                        // consumed, so the button clears the gesture pill on
                        // this sub-screen without double-padding if that ever
                        // changes.
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        // Saving before the load lands would insert a blank
                        // NEW row (isNew still defaults true), not update the
                        // one being opened.
                        Button(
                            onClick = { trySave() },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.form_save_session))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            // Fields aren't seeded yet — rendering the form now shows a
            // blank "new session" shell for an existing session, and
            // anything typed would be clobbered when the load lands.
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxHeight()
                // Capped and centred in wide windows; identical to the old
                // fillMaxSize on a portrait phone. See ui/AdaptiveLayout.kt.
                .readableFormWidth()
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

            SectionLabel(stringResource(R.string.form_section_vehicle))
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

            SectionLabel(stringResource(R.string.form_section_session))
            NumberField(
                label = stringResource(R.string.form_odometer_unit, com.evsct.app.util.Units.distanceUnit(state.useMiles)),
                value = state.odometerText,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(odometerFocusRequester),
                isError = HintField.ODOMETER in warnedFields,
            ) { v -> viewModel.update { it.copy(odometerText = v) } }
            NumberField(
                label = stringResource(R.string.form_energy_delivered_kwh),
                value = state.energyText,
                isError = HintField.ENERGY in warnedFields,
            ) { v -> viewModel.update { it.copy(energyText = v) } }
            NumberField(
                label = stringResource(R.string.form_total_cost_currency, state.currency),
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

            SectionLabel(stringResource(R.string.form_section_station))
            if (state.recentStops.isNotEmpty()) {
                RecentStopsButton(
                    stops = state.recentStops,
                    onPick = { viewModel.applyStop(it) },
                )
            }
            BrandPicker(state.brand, state.brandSuggestions) { v ->
                viewModel.update { it.copy(brand = v) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFieldPlain(
                    label = stringResource(R.string.form_city),
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

            // Everything below folds. While a charge is being entered the
            // groups all start open — this is where the user decides what the
            // charge needs, and hunting through folds to find out is worse
            // than scrolling. A live-tracked charge counts as entry too: its
            // row exists in the DB (so isNew is false) but the user is still
            // filling it in. Reviewing a saved charge is the opposite job, and
            // there the empty groups fold out of the way.
            val enteringCharge = state.isNew || state.isTracking

            CollapsibleSection(
                title = stringResource(R.string.form_battery_wait),
                startExpanded = enteringCharge,
                filledCount = listOf(
                    state.waitTimeText,
                    state.batteryStartText,
                    state.batteryEndText,
                ).count { it.isNotBlank() },
                demandsAttention = HintField.BATTERY_START in warnedFields ||
                    HintField.BATTERY_END in warnedFields,
            ) {
                NumberField(
                    label = stringResource(R.string.form_wait_time_min_optional),
                    value = state.waitTimeText,
                    onValue = { v -> viewModel.update { it.copy(waitTimeText = v) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NumberField(
                        label = stringResource(R.string.form_battery_start),
                        value = state.batteryStartText,
                        modifier = Modifier.weight(1f),
                        isError = HintField.BATTERY_START in warnedFields,
                        onValue = { v -> viewModel.update { it.copy(batteryStartText = v) } },
                    )
                    NumberField(
                        label = stringResource(R.string.form_battery_end),
                        value = state.batteryEndText,
                        modifier = Modifier.weight(1f),
                        isError = HintField.BATTERY_END in warnedFields,
                        onValue = { v -> viewModel.update { it.copy(batteryEndText = v) } },
                    )
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.form_posted_rates),
                startExpanded = enteringCharge,
                filledCount = listOf(
                    state.postedEnergyPriceText,
                    state.postedTimeRateText,
                    state.postedMaxPowerText,
                ).count { it.isNotBlank() },
                demandsAttention = HintField.POSTED_ENERGY_PRICE in warnedFields ||
                    HintField.POSTED_TIME_RATE in warnedFields ||
                    HintField.POSTED_MAX_POWER in warnedFields,
            ) {
                NumberField(
                    label = stringResource(R.string.form_posted_energy_price),
                    value = state.postedEnergyPriceText,
                    isError = HintField.POSTED_ENERGY_PRICE in warnedFields,
                ) { v -> viewModel.update { it.copy(postedEnergyPriceText = v) } }
                // Stations advertise time-based pricing in $/min or $/hr; the
                // toggle picks the entry unit and converts the typed value in
                // place when flipped. Storage stays canonical $/min.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberField(
                        label = stringResource(R.string.form_posted_time_rate, state.postedTimeRateUnit.suffix),
                        value = state.postedTimeRateText,
                        modifier = Modifier.weight(1f),
                        isError = HintField.POSTED_TIME_RATE in warnedFields,
                    ) { v -> viewModel.update { it.copy(postedTimeRateText = v) } }
                    SingleChoiceSegmentedButtonRow {
                        TimeRateUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = state.postedTimeRateUnit == unit,
                                onClick = { viewModel.setPostedTimeRateUnit(unit) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = TimeRateUnit.entries.size,
                                ),
                            ) { Text(unit.suffix) }
                        }
                    }
                }
                NumberField(
                    label = stringResource(R.string.form_posted_max_power_kw),
                    value = state.postedMaxPowerText,
                    isError = HintField.POSTED_MAX_POWER in warnedFields,
                ) { v ->
                    viewModel.update { it.copy(postedMaxPowerText = v) }
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.form_more_station_detail),
                startExpanded = enteringCharge,
                // Coordinates count as a filled field even though no text
                // input shows them: a location picked on the map is exactly
                // the kind of thing that must not vanish behind a fold.
                filledCount = listOf(
                    state.address.isNotBlank(),
                    state.stationName.isNotBlank(),
                    state.latitude != null && state.longitude != null,
                ).count { it },
            ) {
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
                TextFieldPlain(stringResource(R.string.form_address), state.address) { v ->
                    viewModel.update { it.copy(address = v) }
                }
                TextFieldPlain(stringResource(R.string.form_station_name), state.stationName) { v ->
                    viewModel.update { it.copy(stationName = v) }
                }
            }

            CollapsibleSection(
                title = stringResource(R.string.form_trip),
                startExpanded = enteringCharge,
                filledCount = listOf(
                    state.tripId != null,
                    state.continuesPrevious,
                ).count { it },
            ) {
                TripPicker(state) { id -> viewModel.update { it.copy(tripId = id) } }
                ContinuesPreviousToggle(
                    checked = state.continuesPrevious,
                    onCheckedChange = { v -> viewModel.update { it.copy(continuesPrevious = v) } },
                )
            }

            CollapsibleSection(
                title = stringResource(R.string.form_receipts),
                startExpanded = enteringCharge,
                filledCount = state.receipts.size,
            ) {
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
            }

            CollapsibleSection(
                title = stringResource(R.string.form_tags),
                startExpanded = enteringCharge,
                // The uncommitted draft counts: save() folds it into the tag
                // list, so a tag typed but not entered is real data.
                filledCount = state.tags.size + if (state.tagDraft.isBlank()) 0 else 1,
            ) {
                TagsField(
                    tags = state.tags,
                    draft = state.tagDraft,
                    onDraftChange = { v ->
                        viewModel.update { it.copy(tagDraft = v) }
                    },
                    onAdd = { tag ->
                        viewModel.update { it.copy(tags = Tags.add(it.tags, tag)) }
                    },
                    onRemove = { tag ->
                        viewModel.update { it.copy(tags = Tags.remove(it.tags, tag)) }
                    },
                )
            }

            CollapsibleSection(
                title = stringResource(R.string.form_notes),
                startExpanded = enteringCharge,
                filledCount = if (state.notes.isBlank()) 0 else 1,
            ) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    label = { Text(stringResource(R.string.form_notes)) },
                )
            }

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
            title = { Text(stringResource(R.string.form_save_without_odometer)) },
            text = {
                Text(
                    stringResource(R.string.form_the_odometer_reading_helps)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showOdometerWarning = false
                    viewModel.save(onDone)
                }) { Text(stringResource(R.string.form_save_anyway)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showOdometerWarning = false
                    // Take the user TO the field they agreed to fill in —
                    // focusing it opens the keyboard and auto-scrolls it
                    // into view, instead of dropping them back wherever
                    // they were on this long form.
                    odometerFocusRequester.requestFocus()
                }) {
                    Text(stringResource(R.string.form_add_odometer))
                }
            },
        )
    }

    if (showDiscardConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.form_discard_changes)) },
            text = { Text(stringResource(R.string.form_this_session_has_unsaved)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDone()
                    },
                ) {
                    Text(stringResource(R.string.form_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.form_keep_editing))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.form_delete_this_session)) },
            text = { Text(stringResource(R.string.form_this_will_permanently_remove)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = false
                        viewModel.deleteAndExit(onDone)
                    },
                ) {
                    Text(
                        stringResource(R.string.form_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.form_cancel))
                }
            },
        )
    }
}

/** Checkmark for a selected FilterChip. M3's FilterChip only tints the
 *  selected chip — the canonical leading check has to be passed in
 *  explicitly, or selection reads as a subtle color change. Shared by
 *  every single-select chip row on this form. */
private fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (!selected) null else {
        {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
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

/**
 * A foldable group of optional fields, headed by a tappable row.
 *
 * Folding is tuned to the two different jobs this screen does.
 *
 * **Entering a charge** ([startExpanded]) opens everything. The user is about
 * to decide what this charge needs recording, and making them unfold seven
 * groups to find out is worse than a long scroll — entry behaves like the flat
 * form it replaced, except now anything irrelevant can be folded out of the
 * way.
 *
 * **Reviewing a saved charge** folds the groups that came back empty, which is
 * where the length actually hurts: scrolling past six blank groups to reach the
 * notes on a charge from four months ago.
 *
 * Either way, folding must never hide something the user entered:
 *
 *  - **A group holding a value is open**, whichever job we're doing —
 *    [filledCount] seeds the initial state, and is re-checked afterwards
 *    because values arrive while the form is on screen. The Recent-stops
 *    shortcut sits among the always-visible fields but fills the address and
 *    station name inside a folded group; a group that gains its first value
 *    opens so that data doesn't land out of sight.
 *  - **A collapsed group still says what's inside**, via the "n set" badge —
 *    a fold should never be mistaken for an empty section.
 *  - **Once the user takes a group in hand, their choice sticks**
 *    ([userToggled]) — collapsing a filled group has to stay collapsed, or the
 *    auto-open above would fight them on every keystroke.
 *
 * [demandsAttention] overrides all of that and forces the group open. A
 * validation hint pointing at a field inside a folded group would otherwise be
 * invisible: the card at the top of the form would name a problem with no way
 * to see the field it's about.
 *
 * Expansion is [rememberSaveable], so it survives rotation and process death —
 * the map picker and the photo picker both put this screen at risk of the
 * latter.
 *
 * Internal rather than private so [CollapsibleSectionTest] can drive the fold
 * rules directly — they are this screen's real behavioral guarantees, and
 * unit tests compile into the same module.
 */
@Composable
internal fun CollapsibleSection(
    title: String,
    filledCount: Int,
    startExpanded: Boolean = false,
    demandsAttention: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(startExpanded || filledCount > 0) }
    var userToggled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(filledCount, demandsAttention) {
        if (demandsAttention) expanded = true
        else if (!userToggled && filledCount > 0) expanded = true
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "sectionChevron",
    )
    // Resolved here: the semantics block below is not a composable scope.
    val expandedDesc = stringResource(R.string.form_section_expanded)
    val collapsedDesc = stringResource(R.string.form_section_collapsed)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    userToggled = true
                    expanded = !expanded
                }
                // One TalkBack stop that announces the fold state. Without
                // stateDescription the header reads as bare text and the
                // chevron — the only thing carrying open/closed — is
                // invisible to a screen reader.
                //
                // The testTag is how CollapsibleSectionTest folds and unfolds
                // the group; the title suffix keeps it addressable once the
                // form's seven sections are on screen together.
                .testTag("sectionHeader:$title")
                .semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) expandedDesc else collapsedDesc
                }
                // 14dp against a line of titleSmall lands on the 48dp touch
                // target without pinning a height.
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!expanded && filledCount > 0) {
                Spacer(Modifier.width(8.dp))
                FilledCountBadge(filledCount)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** "2 set" on a collapsed group — the promise that folding hid nothing. */
@Composable
private fun FilledCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.form_filled_count, count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
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
                Text(stringResource(R.string.form_date_time), style = MaterialTheme.typography.labelSmall)
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
                    label = { Text(stringResource(R.string.form_date)) },
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
                    label = { Text(stringResource(R.string.form_time)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChargingTypeRow(current: ChargingType, onPick: (ChargingType) -> Unit) {
    // FlowRow, not Row: at large font scales the chips exceed the screen
    // width, and a fixed Row clips the trailing options off-screen.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChargingType.entries.forEach { t ->
            FilterChip(
                selected = t == current,
                onClick = { onPick(t) },
                label = { Text(stringResource(t.labelRes())) },
                leadingIcon = selectedCheck(t == current),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PricingModelRow(current: PricingModel, onPick: (PricingModel) -> Unit) {
    // Five chips overflow a narrow screen even at normal font scale —
    // wrap to a second line instead of clipping Free/Hybrid off-screen.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PricingModel.entries.forEach { p ->
            FilterChip(
                selected = p == current,
                onClick = { onPick(p) },
                label = { Text(stringResource(p.labelRes())) },
                leadingIcon = selectedCheck(p == current),
            )
        }
    }
}

@StringRes
private fun ChargingType.labelRes(): Int = when (this) {
    ChargingType.DC_FAST -> R.string.form_type_dc_fast
    ChargingType.AC_L2 -> R.string.form_type_ac_l2
    ChargingType.AC_L1 -> R.string.form_type_ac_l1
}

@StringRes
private fun PricingModel.labelRes(): Int = when (this) {
    PricingModel.PER_KWH -> R.string.form_pricing_per_kwh
    PricingModel.PER_MINUTE -> R.string.form_pricing_per_minute
    PricingModel.FLAT -> R.string.form_pricing_flat
    PricingModel.FREE -> R.string.form_pricing_free
    PricingModel.HYBRID -> R.string.form_pricing_hybrid
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
                leadingIcon = selectedCheck(selected == code),
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
            label = { Text(stringResource(R.string.form_station_brand)) },
            placeholder = { Text(stringResource(R.string.form_tap_to_choose)) },
            readOnly = true,
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.form_open_brand_picker),
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
                stringResource(R.string.form_choose_a_brand),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.form_search_or_type_a)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.form_clear))
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
                            label = stringResource(R.string.form_use_brand, q),
                            isCurrent = false,
                            onClick = { onPick(q) },
                        )
                    }
                    item { androidx.compose.material3.HorizontalDivider() }
                }
                if (filteredHistory.isNotEmpty()) {
                    item { BrandSectionHeader(stringResource(R.string.form_brands_used)) }
                    items(filteredHistory) { brand ->
                        BrandRow(
                            label = brand,
                            isCurrent = brand.equals(current, ignoreCase = true),
                            onClick = { onPick(brand) },
                        )
                    }
                }
                if (filteredCurated.isNotEmpty()) {
                    item { BrandSectionHeader(stringResource(R.string.form_brands_all)) }
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
                            stringResource(R.string.form_no_matches),
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
                contentDescription = stringResource(R.string.form_currently_selected),
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
        // FilterChip (not AssistChip + container tint): it announces the
        // selected state to TalkBack and adds the checkmark, so selection
        // isn't conveyed by background color alone. Matches the charging
        // type / pricing / currency pickers.
        FilterChip(
            selected = state.tripId == null,
            onClick = { onPick(null) },
            label = { Text(stringResource(R.string.form_none)) },
            leadingIcon = selectedCheck(state.tripId == null),
        )
        state.trips.forEach { trip ->
            FilterChip(
                selected = state.tripId == trip.id,
                onClick = { onPick(trip.id) },
                label = { Text(trip.name) },
                leadingIcon = selectedCheck(state.tripId == trip.id),
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
            Text(stringResource(R.string.form_continues_from_previous_session), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.form_tick_if_no_untracked),
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
            stringResource(R.string.form_add_a_vehicle_from),
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
        // FilterChip for the same reason as the trip picker: selection gets
        // a checkmark + semantics instead of a background tint only.
        FilterChip(
            selected = state.vehicleId == null,
            onClick = { onPick(null) },
            label = { Text(stringResource(R.string.form_unassigned)) },
            leadingIcon = selectedCheck(state.vehicleId == null),
        )
        state.vehicles.forEach { vehicle ->
            FilterChip(
                selected = state.vehicleId == vehicle.id,
                onClick = { onPick(vehicle.id) },
                label = { Text(vehicle.name) },
                leadingIcon = selectedCheck(state.vehicleId == vehicle.id),
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(stringResource(R.string.form_prov_state)) },
        placeholder = { Text(stringResource(R.string.form_e_g_sk)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
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
        Text(stringResource(R.string.form_use_a_recent_stop))
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
                stringResource(R.string.form_recent_stops),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.form_search_brand_city_address)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.form_clear))
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
                            stringResource(R.string.form_no_matches),
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
                    if (isLoading) stringResource(R.string.form_gps_finding) else stringResource(R.string.form_gps_use),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.form_auto_fill_from_your),
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
                    stringResource(R.string.form_pick_on_map),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.form_drop_a_pin_manually),
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

    val focusManager = LocalFocusManager.current
    // Live readout of how the current text will be read ("= 0h 32m 14s" for
    // "32:14"), shown only while editing — the resting field already
    // displays the pretty form. The supporting slot stays reserved (blank
    // when there's nothing to preview) for the whole focus span so the form
    // doesn't jump on every keystroke.
    val preview = if (hasFocus) {
        DurationFormat.parse(fieldValue.text)
            ?.takeIf { it > 0 }
            ?.let { stringResource(R.string.form_duration_preview, DurationFormat.pretty(it)) }
            ?: ""
    } else {
        null
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fv ->
            fieldValue = fv
            if (fv.text != value) onValue(fv.text)
        },
        label = { Text(stringResource(R.string.form_charging_duration)) },
        placeholder = { Text(stringResource(R.string.form_e_g_duration)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
        singleLine = true,
        isError = isError,
        supportingText = if (preview != null) {
            { Text(preview) }
        } else {
            null
        },
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
                        stringResource(R.string.form_),
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
    // Draft lives in the ViewModel (not local remember) so the top-bar
    // Save can fold an uncommitted tag into the session instead of
    // silently dropping it.
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val commit: () -> Unit = {
        val trimmed = draft.trim()
        if (trimmed.isNotEmpty()) onAdd(trimmed)
        onDraftChange("")
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
                                contentDescription = stringResource(R.string.form_remove_tag, tag),
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
                    onDraftChange("")
                } else {
                    onDraftChange(v)
                }
            },
            label = { Text(stringResource(R.string.form_add_tag)) },
            placeholder = { Text(stringResource(R.string.form_e_g_work_charge)) },
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
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
                            stringResource(R.string.form_attach_receipts),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.form_photo_or_pdf_add),
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
                    Text(stringResource(R.string.form_add_another))
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
                    contentDescription = stringResource(R.string.form_receipt_photo),
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
                androidx.compose.material3.TextButton(onClick = onRename) { Text(stringResource(R.string.form_rename)) }
                Spacer(Modifier.width(4.dp))
            }
            androidx.compose.material3.TextButton(onClick = onRemove) { Text(stringResource(R.string.form_remove)) }
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
                stringResource(R.string.form_tap_to_open_in),
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
        title = { Text(stringResource(R.string.form_rename_receipt)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.form_file_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(draft.trim().takeIf { it.isNotEmpty() }) },
            ) { Text(stringResource(R.string.form_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) }
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
                stringResource(R.string.form_attach_receipt_as),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ChooserRow(
                icon = Icons.Default.Image,
                title = stringResource(R.string.form_photo),
                subtitle = stringResource(R.string.form_pick_from_your_gallery),
                onClick = onPickPhoto,
            )
            ChooserRow(
                icon = Icons.Default.PictureAsPdf,
                title = stringResource(R.string.form_pdf),
                subtitle = stringResource(R.string.form_pick_a_pdf_file),
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
    val chooser = android.content.Intent.createChooser(intent, context.getString(R.string.form_open_receipt))
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
        contentDescription = stringResource(R.string.form_receipt_photo_full_screen),
        onDismiss = onDismiss,
    )
}

@Composable
private fun ValidationHintsCard(hints: List<ValidationHint>) {
    val accent = com.evsct.app.ui.theme.LocalEvAccents.current.dcFast
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accent.container,
            contentColor = accent.onContainer,
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
                    pluralStringResource(R.plurals.form_hints_title, hints.size, hints.size),
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
    val label = stringResource(R.string.form_use_elapsed, DurationFormat.editable(elapsedSec))
    AssistChip(
        onClick = { onUse(elapsedSec) },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    )
}

/* ------------------------------- Previews -------------------------------- */

/*
 * The seven folding groups from #50, which that PR shipped marked "not yet
 * exercised" and which nothing has checked since.
 *
 * One thing writing these turned up: **"collapsed with a badge" is not a
 * reachable starting state.** `expanded` initialises to
 * `startExpanded || filledCount > 0`, and the LaunchedEffect re-opens any group
 * that holds a value, so a group with content is always open on arrival. The
 * badge only appears after the user manually collapses a filled group — which is
 * the intended behaviour (auto-open beats a badge) but means the badge itself has
 * likely never been looked at. Hence the separate FilledCountBadge preview: a
 * static preview of the section can't produce it. Studio's interactive preview
 * can, by clicking the header.
 */

@Composable
private fun PreviewSectionBody() {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        label = { Text("Battery at start (%)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = "",
        onValueChange = {},
        label = { Text("Battery at end (%)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Reviewing a saved charge: the group came back empty, so it folds. This is
 *  the state that makes a long form shorter, and the only collapsed one a
 *  static preview can render. */
@Preview(name = "Section — collapsed, empty", showBackground = true, widthDp = 400)
@Composable
private fun PreviewSectionCollapsed() {
    EvsctTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CollapsibleSection(title = "Battery & wait", filledCount = 0) {
                PreviewSectionBody()
            }
        }
    }
}

/** Holding two values, so it opens either way — the guarantee that folding
 *  never hides entered data. */
@Preview(name = "Section — open because filled", showBackground = true, widthDp = 400)
@Composable
private fun PreviewSectionFilled() {
    EvsctTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CollapsibleSection(title = "Battery & wait", filledCount = 2) {
                PreviewSectionBody()
            }
        }
    }
}

/** Forced open by a validation hint pointing inside it. Without this the hint
 *  card would name a problem with no way to reach the field. */
@Preview(name = "Section — forced open by a hint", showBackground = true, widthDp = 400)
@Composable
private fun PreviewSectionDemandsAttention() {
    EvsctTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CollapsibleSection(
                title = "Posted rates",
                filledCount = 0,
                demandsAttention = true,
            ) {
                PreviewSectionBody()
            }
        }
    }
}

/** Several stacked, which is what the form actually looks like: the dividers and
 *  the 14dp header padding carry the rhythm. At 1.5x because the header row is
 *  the touch target that padding was tuned for. */
@Preview(
    name = "Sections — stacked, 1.5x font",
    showBackground = true,
    widthDp = 400,
    fontScale = 1.5f,
)
@Composable
private fun PreviewSectionsStacked() {
    EvsctTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            CollapsibleSection(title = "Battery & wait", filledCount = 0) { PreviewSectionBody() }
            CollapsibleSection(title = "Posted rates", filledCount = 0) { PreviewSectionBody() }
            CollapsibleSection(title = "More station detail", filledCount = 0) { PreviewSectionBody() }
            CollapsibleSection(title = "Trip", filledCount = 0) { PreviewSectionBody() }
        }
    }
}

/** Unreachable inside a freshly composed section, so previewed on its own. */
@Preview(name = "Badge — n set", showBackground = true, widthDp = 200, heightDp = 80)
@Composable
private fun PreviewFilledCountBadge() {
    EvsctTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            FilledCountBadge(3)
        }
    }
}
