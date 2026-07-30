package com.evsct.app.ui.sessions

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.R
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.prefs.CardTimeRate
import com.evsct.app.ui.EmptyState
import com.evsct.app.ui.EvsctBarTitle
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.MoneyStat
import com.evsct.app.ui.StatColumns
import com.evsct.app.ui.VehicleScope
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.ui.vehicleScopeFromToken
import com.evsct.app.ui.VehicleScopeTabs
import com.evsct.app.ui.needsVehiclePicker
import com.evsct.app.ui.forType
import com.evsct.app.ui.theme.LocalEvAccents
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.Derived
import com.evsct.app.util.Format
import com.evsct.app.util.Tags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    onAddSession: (preselectVehicleId: Long?) -> Unit,
    onStartTrackedSession: (sessionId: Long) -> Unit,
    onEditSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVehicles: () -> Unit,
    /** Brand-drill-down payload from Stats, read off this entry's
     *  SavedStateHandle by the nav graph (same relay the map picker
     *  uses). Vehicle id uses a -1 sentinel for "all vehicles". */
    requestedBrandFilter: String? = null,
    requestedBrandScopeToken: String? = null,
    onBrandFilterRequestConsumed: () -> Unit = {},
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Apply the Stats drill-down once, then clear the handle keys so a
    // later visit to the Log doesn't re-apply a stale request.
    LaunchedEffect(requestedBrandFilter, requestedBrandScopeToken) {
        val brand = requestedBrandFilter ?: return@LaunchedEffect
        viewModel.applyBrandDrilldown(
            brand = brand,
            scope = vehicleScopeFromToken(requestedBrandScopeToken),
        )
        onBrandFilterRequestConsumed()
    }
    var showTripPicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // derivedStateOf: recompose only when the threshold is crossed, not on
    // every scrolled pixel. A dozen rows down is where "fling back by hand"
    // starts to feel like work.
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 12 }
    }
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    // A delete just happened — one session from the edit screen, or a
    // multi-select batch from this screen: offer a window to take it back.
    // Undo re-inserts the rows (and re-links any receipt files, which stay
    // on disk until this offer resolves).
    val pendingUndo by viewModel.pendingDeleteUndo.collectAsStateWithLifecycle()
    val res = LocalContext.current.resources
    val undoLabel = stringResource(R.string.log_undo)
    LaunchedEffect(pendingUndo) {
        val pending = pendingUndo ?: return@LaunchedEffect
        var resolved = false
        try {
            val n = pending.sessions.size
            val result = snackbarHostState.showSnackbar(
                message = res.getQuantityString(R.plurals.log_deleted_count, n, n),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            resolved = true
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                SnackbarResult.Dismissed -> viewModel.finalizeDeleteUndo()
            }
        } finally {
            // Torn down mid-offer (the user left the Log while the snackbar
            // was still up): without this the never-resolved offer haunts
            // the NEXT visit as a ghost "Session deleted" — device testing
            // hit it right after saving an unrelated session. Forfeit the
            // offer we were showing — and only that one: a replacement
            // offer restarts this effect, and the old instance's teardown
            // must not kill the new offer.
            if (!resolved) viewModel.finalizeDeleteUndoIf(pending)
        }
    }

    // POST_NOTIFICATIONS is runtime-granted on Android 13+ and nothing else
    // in the quick-track flow requests it, so without this prompt the
    // "Charging in progress" shade shortcut silently never appears. The
    // charge starts regardless of the outcome — the permission only gates
    // the notification, not the in-app tracking.
    val context = LocalContext.current
    // Saveable: the permission dialog covers this screen, and rotating (or
    // process death) behind it recreates the composition — plain remember
    // dropped the pending action, so granting the permission did nothing.
    // The vehicle id uses a -1 sentinel to stay a Bundle-friendly Long.
    var pendingTrackRequest by rememberSaveable { mutableStateOf(false) }
    var pendingTrackVehicleId by rememberSaveable { mutableStateOf(-1L) }
    val trackPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (pendingTrackRequest) {
            pendingTrackRequest = false
            viewModel.startTrackedSession(pendingTrackVehicleId.takeIf { it > 0 }) { id ->
                onStartTrackedSession(id)
            }
        }
    }

    fun startTrackedSession(vehicleId: Long?) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingTrackRequest = true
            pendingTrackVehicleId = vehicleId ?: -1L
            trackPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startTrackedSession(vehicleId) { id -> onStartTrackedSession(id) }
        }
    }

    // Which vehicle a newly added charge should start on. Unassigned behaves
    // like All here: a new charge should pick up the default vehicle, not
    // deliberately become another unassigned row for the user to clean up.
    val preselectVehicleId = (state.vehicleScope as? VehicleScope.One)?.id

    // Keep the search/filter strip visible whenever there are active filters,
    // so the user can always see and remove them.
    val showSearchStrip = showSearch || state.filters.hasActive

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = !state.isSelectionMode && showSearchStrip) {
        showSearch = false
        viewModel.clearFilters()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onClear = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                    onAssignTrip = { showTripPicker = true },
                    onDelete = { showBulkDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    // "Log" (the tab's label), not "Charging log": with the
                    // brand mark leading the title, the long form ellipsizes
                    // next to this bar's four actions on 360dp-wide phones.
                    title = { EvsctBarTitle(stringResource(R.string.nav_log)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch) stringResource(R.string.log_close_search) else stringResource(R.string.log_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.log_sort))
                            }
                            SortMenu(
                                expanded = showSortMenu,
                                current = state.sortOption,
                                onSelect = { option ->
                                    viewModel.setSortOption(option)
                                    showSortMenu = false
                                },
                                onDismiss = { showSortMenu = false },
                            )
                        }
                        if (state.sessions.isNotEmpty()) {
                            // Visible route into multi-select — long-press
                            // on a row also works but nothing advertised it.
                            IconButton(onClick = { viewModel.requestSelectionMode() }) {
                                Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.log_select_sessions))
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.log_settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Back-to-top appears once the user is deep in the list;
                // available in selection mode too (long lists are exactly
                // where multi-select happens).
                androidx.compose.animation.AnimatedVisibility(visible = showScrollToTop) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.log_back_to_top))
                    }
                }
                if (!state.isSelectionMode) {
                    // Labeled at the top of the list ("Add session"), then
                    // collapses to the bare + once the user scrolls — the
                    // label aids discovery, the collapse returns the space.
                    // Resolved here, not in the semantics block below:
                    // that lambda is not a composable scope.
                    val addSessionLabel = stringResource(R.string.log_add_session)
                    ExtendedFloatingActionButton(
                        onClick = { showAddSheet = true },
                        expanded = fabExpanded,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.log_add_session)) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // Stable TalkBack name whether expanded (visible
                        // label) or collapsed (icon only, label gone).
                        modifier = Modifier.semantics {
                            contentDescription = addSessionLabel
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showSearchStrip) {
                SearchAndFilterStrip(
                    query = state.filters.query,
                    onQueryChange = { viewModel.setQuery(it) },
                    filters = state.filters,
                    onOpenFilterSheet = { showFilterSheet = true },
                    onClearAll = {
                        showSearch = false
                        viewModel.clearFilters()
                    },
                    onClearBrand = { viewModel.setBrandFilter(null) },
                    onClearDateRange = { viewModel.setDateRange(null, null) },
                    onClearTags = { viewModel.setTagsFilter(emptySet()) },
                )
            }
            // One tab per bucket that actually holds sessions, plus All, and
            // only once there's more than one bucket to choose between: a lone
            // vehicle with nothing unassigned needs no chrome at all.
            if (needsVehiclePicker(state.vehicles.size, state.hasUnassignedSessions)) {
                VehicleScopeTabs(
                    vehicles = state.vehicles,
                    includeUnassigned = state.hasUnassignedSessions,
                    scope = state.vehicleScope,
                    onSelect = { scope -> viewModel.setVehicleScope(scope) },
                    onManageVehicles = onOpenVehicles,
                )
            }
            if (state.backupNudge.show && !state.isSelectionMode) {
                BackupNudgeBanner(
                    nudge = state.backupNudge,
                    onOpenSettings = onOpenSettings,
                    onDismiss = { viewModel.dismissBackupNudge() },
                )
            }
            state.staleTrackedSession?.let { stale ->
                if (!state.isSelectionMode) {
                    StaleTrackingBanner(
                        session = stale,
                        onOpen = { onEditSession(stale.id) },
                    )
                }
            }
            if (state.isLoading) {
                // Room hasn't emitted yet. Zero-state UI here would flash
                // "Welcome"/"No sessions" on every open and read as data loss.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.sessions.isEmpty()) {
                if (state.filters.hasActive) {
                    // The log has sessions — the active search/filters just
                    // match none of them. The first-launch copy ("No sessions
                    // yet") would wrongly imply the whole log is empty.
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = stringResource(R.string.log_no_matching_sessions),
                        body = stringResource(R.string.log_your_search_and_filters),
                        actionLabel = stringResource(R.string.log_clear_filters),
                        onAction = {
                            showSearch = false
                            viewModel.clearFilters()
                        },
                    )
                } else if (state.vehicleScope == VehicleScope.All && state.vehicles.isEmpty()) {
                    // First-launch path: no vehicles, no sessions. Saving a
                    // session without a vehicle works but leaves it untagged
                    // and skews per-vehicle stats, so route the user to set
                    // up a vehicle first — straight to the Vehicles screen,
                    // whose own empty state offers "Add vehicle". The old
                    // target was Settings, which asked a brand-new user to
                    // find Vehicles inside a screen they'd never opened,
                    // right after the body told them what to do.
                    EmptyState(
                        icon = Icons.Default.DirectionsCar,
                        title = stringResource(R.string.log_welcome_to_evsct),
                        body = stringResource(R.string.log_add_a_vehicle_first),
                        actionLabel = stringResource(R.string.log_add_a_vehicle),
                        onAction = onOpenVehicles,
                    )
                } else {
                    // Nothing to show, per bucket. All of these go through the
                    // shared EmptyState so they match the app's other empty
                    // screens — a hand-written copy used to live here and had
                    // drifted (uncentered text, no gap under the title, no room
                    // for an action).
                    val scope = state.vehicleScope
                    EmptyState(
                        icon = Icons.Default.Bolt,
                        title = when (scope) {
                            VehicleScope.All -> stringResource(R.string.log_empty_all_title)
                            VehicleScope.Unassigned -> stringResource(R.string.log_empty_unassigned_title)
                            is VehicleScope.One -> stringResource(R.string.log_empty_vehicle_title)
                        },
                        body = when (scope) {
                            VehicleScope.All ->
                                stringResource(R.string.log_empty_all_body)
                            // Defensive: the Unassigned tab only exists while
                            // such sessions do, and the scope falls back to All
                            // the moment the last one is assigned. The sealed
                            // type still wants a branch.
                            VehicleScope.Unassigned ->
                                stringResource(R.string.log_empty_unassigned_body)
                            is VehicleScope.One ->
                                stringResource(R.string.log_empty_vehicle_body)
                        },
                        // A button beats the old "or pick All" instruction,
                        // which asked the user to go find a tab. The All case
                        // needs none: the Add session FAB is already on screen
                        // and labelled, so a second button would duplicate it.
                        actionLabel = if (scope == VehicleScope.All) null else stringResource(R.string.log_show_all_sessions),
                        onAction = if (scope == VehicleScope.All) {
                            null
                        } else {
                            { viewModel.setVehicleScope(VehicleScope.All) }
                        },
                    )
                }
            } else {
                SummaryCard(state)
                // Month headers only make sense while the list is in date
                // order; a cost- or brand-sorted list interleaves months.
                val groupedByMonth = remember(state.sessions, state.sortOption) {
                    if (state.sortOption == SortOption.DATE) monthGroups(state.sessions)
                    else null
                }
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fun sessionItems(sessions: List<ChargingSession>) {
                        items(sessions, key = { it.id }) { s ->
                            val isSelected = s.id in state.selectedIds
                            // Only surface the vehicle name when the user is on the
                            // "All" tab — otherwise every row would carry the same
                            // tag.
                            val vehicleName = if (state.vehicleScope == VehicleScope.All) {
                                s.vehicleId?.let { state.vehicleNamesById[it] }
                            } else null
                            SessionRow(
                                session = s,
                                tripName = s.tripId?.let { state.tripNamesById[it] },
                                vehicleName = vehicleName,
                                hasReceipt = s.id in state.sessionsWithReceipts,
                                isSelected = isSelected,
                                isSelectionMode = state.isSelectionMode,
                                onClick = {
                                    if (state.isSelectionMode) viewModel.toggleSelection(s.id)
                                    else onEditSession(s.id)
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(s.id)
                                },
                                // Rows glide when a delete/undo or re-sort
                                // moves them instead of teleporting.
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (groupedByMonth != null) {
                        groupedByMonth.forEach { group ->
                            stickyHeader(key = "month-${group.key}") {
                                MonthHeader(group.label)
                            }
                            sessionItems(group.sessions)
                        }
                    } else {
                        sessionItems(state.sessions)
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showBulkDeleteConfirm) {
        val n = state.selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(pluralStringResource(R.plurals.log_delete_confirm_title, n, n)) },
            text = {
                Text(
                    stringResource(R.string.log_this_permanently_removes_the)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showBulkDeleteConfirm = false
                    viewModel.deleteSelectedSessions()
                }) {
                    Text(stringResource(R.string.log_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(stringResource(R.string.log_cancel)) }
            },
        )
    }

    if (showAddSheet) {
        AddSessionChooserSheet(
            onTrackNow = {
                showAddSheet = false
                startTrackedSession(preselectVehicleId)
            },
            onAddPast = {
                showAddSheet = false
                onAddSession(preselectVehicleId)
            },
            onDismiss = { showAddSheet = false },
        )
    }

    if (showTripPicker) {
        TripPickerSheet(
            trips = state.trips,
            selectedCount = state.selectedIds.size,
            onPick = { tripId ->
                viewModel.assignTripToSelection(tripId)
                showTripPicker = false
            },
            onDismiss = { showTripPicker = false },
        )
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = state.filters,
            brands = state.brandsInUse,
            tags = state.tagsInUse,
            onApply = { brand, from, to, tagSel ->
                viewModel.setBrandFilter(brand)
                viewModel.setDateRange(from, to)
                viewModel.setTagsFilter(tagSel)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

/** Tiny dropdown anchored to the Sort icon in the top app bar. Each entry
 *  shows a trailing checkmark when it's the active sort. */
@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                onClick = { onSelect(option) },
                trailingIcon = {
                    if (option == current) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.log_selected))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onAssignTrip: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.log_selected_count, selectedCount), fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.log_cancel_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.log_select_all))
            }
            IconButton(onClick = onAssignTrip, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.log_assign_trip))
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.log_delete_selected))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionChooserSheet(
    onTrackNow: () -> Unit,
    onAddPast: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.log_log_a_charge),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            AddSessionChooserRow(
                icon = Icons.Default.Bolt,
                title = stringResource(R.string.log_track_a_charge_now),
                subtitle = stringResource(R.string.log_start_a_live_timer),
                onClick = onTrackNow,
            )
            HorizontalDivider()
            AddSessionChooserRow(
                icon = Icons.Default.Add,
                title = stringResource(R.string.log_log_a_past_charge),
                subtitle = stringResource(R.string.log_backfill_the_details_of),
                onClick = onAddPast,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddSessionChooserRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripPickerSheet(
    trips: List<com.evsct.app.data.entity.Trip>,
    selectedCount: Int,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                pluralStringResource(R.plurals.log_assign_to, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            TripPickerRow(
                label = stringResource(R.string.log_unassigned),
                emphasis = false,
                onClick = { onPick(null) },
            )
            HorizontalDivider()
            if (trips.isEmpty()) {
                Text(
                    stringResource(R.string.log_no_trips_yet_create),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                trips.forEach { trip ->
                    TripPickerRow(
                        label = trip.name,
                        emphasis = true,
                        onClick = { onPick(trip.id) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TripPickerRow(label: String, emphasis: Boolean, onClick: () -> Unit) {
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
            fontWeight = if (emphasis) FontWeight.Medium else FontWeight.Normal,
            color = if (emphasis) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(state: SessionListUi) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        // Three stats across is the right shape only while all three fit, and
        // the FlowRow from #50 stopped them clipping without making them line
        // up. StatColumns owns that decision now, shared with the Stats
        // headline; ui/StatStacking.kt has the reasoning.
        StatColumns(modifier = Modifier.fillMaxWidth().padding(16.dp)) { statModifier ->
            Stat(stringResource(R.string.common_sessions), state.sessionCount.toString(), statModifier)
            MoneyStat(stringResource(R.string.common_total_cost), state.totalCostByCurrency, statModifier)
            Stat(stringResource(R.string.common_energy), Format.kwh(state.totalKwh), statModifier)
        }
    }
}


@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(
    session: ChargingSession,
    tripName: String?,
    vehicleName: String?,
    hasReceipt: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = LocalUserUnits.current
    val typeAccent = LocalEvAccents.current.forType(session.chargingType)
    val barColor = typeAccent.accent
    val tags = remember(session.tags) { Tags.parse(session.tags) }
    // Hoisted above the description because both it and the chips further down
    // render these two, and the spoken form has to agree with what's on screen
    // — including staying silent when the user has the time rate switched off.
    val effPrice = Derived.effectiveEnergyPricePerKwh(session)
    val timeRate = cardTimeRate(session, units.cardTimeRate)
    // timeRate has to be a key: it depends on the user's card preference, which
    // `session` says nothing about, so keying on the session alone would leave
    // the old rate spoken after a toggle in Settings. effPrice is implied by
    // the session and keyed anyway, so the list doesn't have to be read as a
    // claim about which inputs matter.
    val rowDescription = remember(
        session, tripName, vehicleName, hasReceipt, tags, effPrice, timeRate,
    ) {
        sessionRowDescription(
            session, tripName, vehicleName, hasReceipt, tags, effPrice, timeRate,
        )
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // One node per row, phrased as a sentence. Left alone, the card
            // read as a dozen loose fragments, and the visual grouping that
            // makes a row scannable is exactly what a screen reader can't
            // recover. (It also used to announce two "·" separators as "middle
            // dot"; those are gone now — the stat line below wraps, and a
            // separator can't survive a line break.)
            //
            // clearAndSetSemantics rather than semantics(mergeDescendants):
            // ContentDescription's merge policy APPENDS descendants' own
            // descriptions to the parent's, so merging would read the
            // sentence and then trail "Has receipt" from the icon below.
            // Clearing drops the descendants outright; nothing inside the
            // row is actionable, and the row's own click actions survive
            // (they're on this layout node, not a descendant).
            //
            // selected rides along so TalkBack still announces
            // "selected"/"not selected" while multi-selecting; the check
            // circle alone is visual-only.
            .clearAndSetSemantics {
                contentDescription = rowDescription
                if (isSelectionMode) selected = isSelected
                // Set inside the block, not as a .testTag() modifier: this
                // clears descendant semantics, and the tag must be part of
                // what's kept, not part of what's cleared.
                testTag = "sessionRow"
            },
        shape = RoundedCornerShape(14.dp),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Leading colored accent bar by charging type, painted directly
                // rather than laid out as a fillMaxHeight() sibling. The sibling
                // approach forced the Row into an IntrinsicSize.Min pass — an
                // extra measure of every child on every row — which was
                // measurable jank when flinging the log. drawBehind paints the
                // 6dp stripe at the row's full height for free.
                .drawBehind {
                    drawRect(color = barColor, size = Size(6.dp.toPx(), size.height))
                }
                .padding(start = 6.dp),
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .align(Alignment.CenterVertically)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.brand ?: stringResource(R.string.common_unknown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            session.locationCity ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasReceipt) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Receipt,
                                    contentDescription = stringResource(R.string.log_has_receipt),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                Format.money(session.totalCost, session.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            Format.dateTime(session.sessionStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // FlowRow, and the "·" separators are gone with the plain Row
                // that held them. #50 converted the summary card and the Stats
                // headline for this reason and missed this line, which is the
                // one that actually clips: a Row can't wrap, so at large font
                // scale "85 kW avg" ran out of width and got cut.
                //
                // The dots couldn't survive the change. A wrapped line breaks
                // wherever the width runs out, which strands a separator at the
                // end of one line or the start of the next, reading as a typo
                // rather than punctuation. Spacing carries the grouping instead,
                // which is also what the row's spoken description already does —
                // the dots were only ever announced as "middle dot".
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TypeBadge(session.chargingType)
                    Text(Format.kwh(session.energyKwh), style = MaterialTheme.typography.bodySmall)
                    val waitNote = session.waitTimeSeconds
                        ?.takeIf { it > 0 }
                        ?.let { " +${Format.duration(it)} wait" }
                        .orEmpty()
                    Text(
                        Format.duration(session.durationSeconds) + waitNote,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        Format.kw(Derived.effectiveAvgPowerKw(session)) + " avg",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (effPrice != null || timeRate != null || tripName != null || vehicleName != null) {
                    Spacer(Modifier.height(6.dp))
                    // FlowRow (not Row) so the rate chips and pills wrap to a
                    // second line instead of clipping when several are shown at
                    // once (e.g. both eff. rates plus vehicle + trip pills on
                    // the "All" tab).
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (effPrice != null) {
                            Text(
                                stringResource(R.string.log_effective_rate, Format.moneyRate(effPrice, "kWh")),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (timeRate != null) {
                            Text(
                                stringResource(
                                    R.string.log_effective_rate,
                                    Format.moneyPerTime(timeRate.value, timeRate.shortUnit),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (vehicleName != null) {
                            VehiclePill(vehicleName)
                        }
                        if (tripName != null) {
                            TripPill(tripName)
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SessionTagsRow(tags)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionTagsRow(tags: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { TagPill(it) }
    }
}

@Composable
private fun TagPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            "#$label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypeBadge(type: ChargingType) {
    val accent = LocalEvAccents.current.forType(type)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            type.shortLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = accent.onContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TripPill(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun VehiclePill(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun ChargingType.shortLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC FAST"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}

/** Spoken form of the badge label — the abbreviations read as loose letters
 *  ("A C L 2") when a screen reader hits them cold. */
private fun ChargingType.spokenLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC fast"
    ChargingType.AC_L2 -> "AC level 2"
    ChargingType.AC_L1 -> "AC level 1"
}

/**
 * The cost-per-time rate a row shows, already resolved against the user's
 * [CardTimeRate] preference — null when they've switched it off, or when the
 * session has no cost or no duration to divide.
 *
 * One function decides this because two callers need the same answer in
 * different words: the visible chip wants "min" and the spoken sentence wants
 * "minute". Interpreting the preference twice is how the two drift apart.
 */
internal data class CardTimeRateValue(
    val value: Double,
    /** Compact unit for the chip: "min", "hr". */
    val shortUnit: String,
    /** Spoken unit, which [Format]'s abbreviations don't survive out loud. */
    val spokenUnit: String,
)

internal fun cardTimeRate(
    session: ChargingSession,
    preference: CardTimeRate,
): CardTimeRateValue? = when (preference) {
    CardTimeRate.PER_MINUTE -> Derived.effectiveTimeRatePerMin(session)
        ?.let { CardTimeRateValue(it, shortUnit = "min", spokenUnit = "minute") }
    CardTimeRate.PER_HOUR -> Derived.effectiveTimeRatePerHour(session)
        ?.let { CardTimeRateValue(it, shortUnit = "hr", spokenUnit = "hour") }
    CardTimeRate.OFF -> null
}

/* The compact units [Format] renders are right for the eye and wrong for the
 * ear: "kWh" and "kW" get spelled out letter by letter. These reuse Format's
 * (locale-aware) number formatting and swap the unit for a word. */

private fun spokenKwh(value: Double?): String? =
    value?.let { Format.kwh(it).replace("kWh", "kilowatt hours") }

private fun spokenKw(value: Double?): String? =
    value?.let { Format.kw(it).replace("kW", "kilowatts") }

/* The rate chips are the worst of the lot: "Eff. $0.550/kWh" reads as "eff
 * dollar zero point five five zero slash k-W-h". The slash is the real damage
 * — it turns a rate into two unrelated numbers — so it becomes "per", and
 * "Eff." becomes the word it abbreviates. */

private fun spokenEnergyRate(perKwh: Double): String =
    "effective " + Format.moneyRate(perKwh, "kWh").replace("/kWh", " per kilowatt hour")

private fun spokenTimeRate(rate: CardTimeRateValue): String =
    "effective " + Format.moneyPerTime(rate.value, rate.shortUnit)
        .replace("/${rate.shortUnit}", " per ${rate.spokenUnit}")

/** "1 hour 25 minutes" — [Format.duration]'s "1h 25m" reads as bare letters,
 *  and its "0m 42s" second branch matters little out loud. */
internal fun spokenDuration(seconds: Long?): String? {
    if (seconds == null || seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val hourPart = when {
        h == 1L -> "1 hour"
        h > 1L -> "$h hours"
        else -> null
    }
    val minutePart = when {
        m == 1L -> "1 minute"
        m > 1L -> "$m minutes"
        // Don't drop a sub-minute charge to nothing when there's no hour
        // either — "less than a minute" is the honest reading.
        hourPart == null -> "less than a minute"
        else -> null
    }
    return listOfNotNull(hourPart, minutePart).joinToString(" ")
}

/**
 * One spoken sentence for a session row, following the same reading order the
 * card lays out visually: who and where, what it cost and when, how the charge
 * went, what it worked out to per unit, then the pills that qualify it.
 *
 * [effectiveEnergyRate] and [effectiveTimeRate] are supplied rather than
 * derived, because whether the time rate appears at all is a user preference
 * and this sentence must say exactly what the row shows — no more, and no less.
 */
internal fun sessionRowDescription(
    session: ChargingSession,
    tripName: String?,
    vehicleName: String?,
    hasReceipt: Boolean,
    tags: List<String>,
    effectiveEnergyRate: Double?,
    effectiveTimeRate: CardTimeRateValue?,
): String {
    val parts = mutableListOf<String>()
    parts += session.brand ?: "Unknown brand"
    session.locationCity?.let { parts += it }
    parts += Format.money(session.totalCost, session.currency)
    parts += Format.dateTime(session.sessionStart)
    parts += session.chargingType.spokenLabel()
    spokenKwh(session.energyKwh)?.let { parts += it }
    spokenDuration(session.durationSeconds)?.let { parts += it }
    spokenDuration(session.waitTimeSeconds)?.let { parts += "$it wait" }
    spokenKw(Derived.effectiveAvgPowerKw(session))?.let { parts += "$it average" }
    // Both rates are passed in rather than derived here, so the sentence can't
    // announce a rate the card isn't showing. "effective" repeats on each, as
    // "Eff." does on screen, rather than being folded into one clause — the
    // two rates are separate facts and a listener gets one pass at them.
    effectiveEnergyRate?.let { parts += spokenEnergyRate(it) }
    effectiveTimeRate?.let { parts += spokenTimeRate(it) }
    vehicleName?.let { parts += it }
    tripName?.let { parts += "trip $it" }
    if (hasReceipt) parts += "receipt attached"
    if (tags.isNotEmpty()) {
        parts += if (tags.size == 1) "tag ${tags.first()}"
        else "tags ${tags.joinToString(", ")}"
    }
    return parts.joinToString(", ")
}

/** One month's worth of consecutive sessions in the date-sorted list. */
private data class MonthGroup(
    /** Stable "yyyy-MM" key for the sticky header's LazyColumn identity. */
    val key: String,
    /** Display label, e.g. "July 2026". */
    val label: String,
    val sessions: List<ChargingSession>,
)

/** Bucket a date-sorted (newest-first) session list into month groups,
 *  preserving order. LinkedHashMap keyed by month keeps one group per
 *  month even in the degenerate case of stray out-of-order timestamps. */
private fun monthGroups(sessions: List<ChargingSession>): List<MonthGroup> {
    if (sessions.isEmpty()) return emptyList()
    val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    val labelFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val byMonth = LinkedHashMap<String, MutableList<ChargingSession>>()
    sessions.forEach { s ->
        byMonth.getOrPut(keyFmt.format(Date(s.sessionStart))) { mutableListOf() }.add(s)
    }
    return byMonth.map { (key, group) ->
        MonthGroup(key, labelFmt.format(Date(group.first().sessionStart)), group)
    }
}

/** Sticky month divider. Opaque background so rows visibly slide beneath
 *  it while it holds the top edge. */
@Composable
private fun MonthHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilterStrip(
    query: String,
    onQueryChange: (String) -> Unit,
    filters: SessionFilters,
    onOpenFilterSheet: () -> Unit,
    onClearAll: () -> Unit,
    onClearBrand: () -> Unit,
    onClearDateRange: () -> Unit,
    onClearTags: () -> Unit,
) {
    val anyChip = filters.brand != null || filters.dateFrom != null ||
        filters.dateTo != null || filters.tags.isNotEmpty()
    val activeFilterCount = listOfNotNull(
        filters.brand,
        if (filters.dateFrom != null || filters.dateTo != null) "date" else null,
        if (filters.tags.isNotEmpty()) "tags" else null,
    ).size

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.log_search_city_address_notes)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.log_clear_search))
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                },
            ) {
                IconButton(onClick = onOpenFilterSheet) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.log_filters))
                }
            }
            if (filters.hasActive) {
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.log_clear)) }
            }
        }
        if (anyChip) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                filters.brand?.let { brand ->
                    AssistChip(
                        onClick = onClearBrand,
                        label = { Text(brand) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.log_remove_brand_filter)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
                if (filters.dateFrom != null || filters.dateTo != null) {
                    AssistChip(
                        onClick = onClearDateRange,
                        label = { Text(formatDateRange(filters.dateFrom, filters.dateTo)) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.log_remove_date_filter)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
                if (filters.tags.isNotEmpty()) {
                    val label = if (filters.tags.size == 1) {
                        "#${filters.tags.first()}"
                    } else {
                        "${filters.tags.size} tags"
                    }
                    AssistChip(
                        onClick = onClearTags,
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.log_remove_tag_filter)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatDate(at: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(at))

@Composable
private fun formatDateRange(from: Long?, to: Long?): String {
    val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return when {
        from != null && to != null -> "${fmt.format(java.util.Date(from))} – ${fmt.format(java.util.Date(to))}"
        from != null -> stringResource(R.string.log_date_from, fmt.format(java.util.Date(from)))
        to != null -> stringResource(R.string.log_date_until, fmt.format(java.util.Date(to)))
        else -> stringResource(R.string.log_date)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: SessionFilters,
    brands: List<String>,
    tags: List<String>,
    onApply: (brand: String?, from: Long?, to: Long?, tags: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var brand by remember(filters.brand) { mutableStateOf(filters.brand) }
    var dateFrom by remember(filters.dateFrom) { mutableStateOf(filters.dateFrom) }
    var dateTo by remember(filters.dateTo) { mutableStateOf(filters.dateTo) }
    // Local working copy lower-cased so toggling a chip a second time matches
    // regardless of the casing each session stored. Re-keys whenever the
    // applied selection changes from outside.
    var tagSelection by remember(filters.tags) {
        mutableStateOf(filters.tags.map { it.lowercase() }.toSet())
    }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            // Scrollable: with many brands/tags (or large fonts) the Apply
            // row lands below the fold and would otherwise be unreachable.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                stringResource(R.string.log_filter_sessions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.log_date_range), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                DatePresetChip(stringResource(R.string.log_preset_all_time), isActive = dateFrom == null && dateTo == null) {
                    dateFrom = null; dateTo = null
                }
                DatePresetChip(stringResource(R.string.log_preset_this_month), isActive = matchesPreset(dateFrom, dateTo, DatePreset.THIS_MONTH)) {
                    val (f, t) = DatePreset.THIS_MONTH.range()
                    dateFrom = f; dateTo = t
                }
                DatePresetChip(stringResource(R.string.log_preset_last_3_months), isActive = matchesPreset(dateFrom, dateTo, DatePreset.LAST_3_MONTHS)) {
                    val (f, t) = DatePreset.LAST_3_MONTHS.range()
                    dateFrom = f; dateTo = t
                }
                DatePresetChip(stringResource(R.string.log_preset_last_year), isActive = matchesPreset(dateFrom, dateTo, DatePreset.LAST_YEAR)) {
                    val (f, t) = DatePreset.LAST_YEAR.range()
                    dateFrom = f; dateTo = t
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showFromPicker = true },
                    label = { Text(dateFrom?.let { stringResource(R.string.log_from_label, formatDate(it)) } ?: stringResource(R.string.log_from_placeholder)) },
                )
                AssistChip(
                    onClick = { showToPicker = true },
                    label = { Text(dateTo?.let { stringResource(R.string.log_to_label, formatDate(it)) } ?: stringResource(R.string.log_to_placeholder)) },
                )
            }
            // Visible validation when the user accidentally inverts the range —
            // otherwise the predicate (sessionStart >= from AND <= to) silently
            // hides every session and the list looks empty for no obvious reason.
            val dateRangeInvalid = dateFrom != null && dateTo != null && dateFrom!! > dateTo!!
            if (dateRangeInvalid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.log_from_must_be_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.log_brand), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (brands.isEmpty()) {
                Text(
                    stringResource(R.string.log_no_brands_recorded_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = brand == null,
                        onClick = { brand = null },
                        label = { Text(stringResource(R.string.log_any_brand)) },
                    )
                    brands.forEach { b ->
                        FilterChip(
                            selected = brand.equals(b, ignoreCase = true),
                            onClick = { brand = if (brand.equals(b, ignoreCase = true)) null else b },
                            label = { Text(b) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.log_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.log_no_tags_yet_add),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { t ->
                        val key = t.lowercase()
                        FilterChip(
                            selected = key in tagSelection,
                            onClick = {
                                tagSelection = if (key in tagSelection) tagSelection - key
                                               else tagSelection + key
                            },
                            label = { Text(t) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_cancel)) }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    enabled = !dateRangeInvalid,
                    onClick = { onApply(brand, dateFrom, dateTo, tagSelection) },
                ) {
                    Text(stringResource(R.string.log_apply))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFromPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateFrom)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateFrom = pickerState.selectedDateMillis?.let { pickedDayStart(it) }
                    showFromPicker = false
                }) { Text(stringResource(R.string.log_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text(stringResource(R.string.log_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (showToPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateTo)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateTo = pickerState.selectedDateMillis?.let { pickedDayEnd(it) }
                    showToPicker = false
                }) { Text(stringResource(R.string.log_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text(stringResource(R.string.log_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DatePresetChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = { Text(label) },
    )
}

private enum class DatePreset {
    THIS_MONTH, LAST_3_MONTHS, LAST_YEAR;

    fun range(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        val end = run {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            cal.set(java.util.Calendar.MINUTE, 59)
            cal.set(java.util.Calendar.SECOND, 59)
            cal.set(java.util.Calendar.MILLISECOND, 999)
            cal.timeInMillis
        }
        val startCal = java.util.Calendar.getInstance()
        when (this) {
            THIS_MONTH -> {
                startCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            LAST_3_MONTHS -> {
                startCal.add(java.util.Calendar.MONTH, -3)
            }
            LAST_YEAR -> {
                startCal.add(java.util.Calendar.YEAR, -1)
            }
        }
        startCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        startCal.set(java.util.Calendar.MINUTE, 0)
        startCal.set(java.util.Calendar.SECOND, 0)
        startCal.set(java.util.Calendar.MILLISECOND, 0)
        return startCal.timeInMillis to end
    }
}

private fun matchesPreset(from: Long?, to: Long?, preset: DatePreset): Boolean {
    if (from == null || to == null) return false
    val (pf, pt) = preset.range()
    // Compare to within ~1 minute since "now" will move while the sheet is open.
    return kotlin.math.abs(from - pf) < 60_000L && kotlin.math.abs(to - pt) < 60_000L
}

/**
 * Convert a Material3 DatePicker's [DatePickerState.selectedDateMillis]
 * (which is the UTC midnight of the day the user picked) into the local-TZ
 * start of that calendar day. Without this conversion, users west of UTC
 * end up with a filter that starts on the previous day.
 */
private fun pickedDayStart(pickerUtcMillis: Long): Long {
    val date = java.time.Instant.ofEpochMilli(pickerUtcMillis)
        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
    return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Companion to [pickedDayStart] returning the last millisecond of the
 *  picked day in the local timezone. */
private fun pickedDayEnd(pickerUtcMillis: Long): Long {
    val date = java.time.Instant.ofEpochMilli(pickerUtcMillis)
        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
    val nextDayStart = date.plusDays(1)
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    return nextDayStart - 1
}

@Composable
private fun StaleTrackingBanner(
    session: ChargingSession,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.log_still_charging),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.log_tracked_running, Format.dateTime(session.sessionStart)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(onClick = onOpen) { Text(stringResource(R.string.log_open_session)) }
            }
        }
    }
}

@Composable
private fun BackupNudgeBanner(
    nudge: BackupNudge,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val daysText = nudge.daysSinceLastBackup?.let { d ->
        when {
            d < 1L -> stringResource(R.string.log_backup_today)
            d < 365L -> pluralStringResource(R.plurals.log_backup_days_ago, d.toInt(), d.toInt())
            else -> stringResource(R.string.log_backup_over_year)
        }
    }
    val message = if (daysText == null) {
        stringResource(R.string.log_backup_never)
    } else {
        stringResource(R.string.log_backup_refresh, daysText)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.log_back_up_your_data),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_not_now)) }
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.log_open_settings))
                }
            }
        }
    }
}

/* ------------------------------- Previews -------------------------------- */

/*
 * A trial set, deliberately small. These render in Android Studio's preview
 * pane (open this file, then the Split or Design toggle at the top right) with
 * no build and no device — so the cases below are ones that are a nuisance to
 * reach by hand in a running app.
 *
 * They live in this file because SessionRow and SummaryCard are private, and a
 * preview can only reach a private composable from the same file.
 *
 * Two of them are here specifically because #50 changed both of these to
 * FlowRow to stop the last column clipping at large font scale, and that PR
 * shipped saying the large-font case had not been exercised. It still hasn't.
 * The fontScale = 2f previews are that check.
 */

private fun previewSession(
    brand: String? = "Petro-Canada",
    locationCity: String? = "Kingston",
    durationSeconds: Long? = 1_800L,
    waitTimeSeconds: Long? = null,
    energyKwh: Double? = 42.5,
    totalCost: Double? = 24.50,
    chargingType: ChargingType = ChargingType.DC_FAST,
    tags: String? = null,
) = ChargingSession(
    id = 1L,
    sessionStart = 1_752_000_000_000L,
    durationSeconds = durationSeconds,
    waitTimeSeconds = waitTimeSeconds,
    energyKwh = energyKwh,
    totalCost = totalCost,
    chargingType = chargingType,
    brand = brand,
    locationCity = locationCity,
    tags = tags,
)

@Composable
private fun PreviewRow(
    session: ChargingSession,
    tripName: String? = null,
    vehicleName: String? = null,
    hasReceipt: Boolean = false,
) {
    EvsctTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SessionRow(
                session = session,
                tripName = tripName,
                vehicleName = vehicleName,
                hasReceipt = hasReceipt,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
                onLongClick = {},
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Preview(name = "Row — typical", showBackground = true, widthDp = 400)
@Composable
private fun PreviewRowTypical() = PreviewRow(previewSession())

/** Every optional part at once, which is the layout's worst case and takes
 *  real setup to produce on a device. */
@Preview(name = "Row — everything set", showBackground = true, widthDp = 400)
@Composable
private fun PreviewRowEverything() = PreviewRow(
    previewSession(
        brand = "Electrify Canada",
        locationCity = "Saint-Jean-sur-Richelieu",
        waitTimeSeconds = 7 * 60L,
        tags = "roadtrip,reimbursed,winter",
    ),
    tripName = "Gaspé loop",
    vehicleName = "Ioniq 5",
    hasReceipt = true,
)

/** The check #50 never ran. If the stat line or the rate chips clip instead of
 *  wrapping, it shows here. */
@Preview(name = "Row — 2x font", showBackground = true, widthDp = 400, fontScale = 2f)
@Composable
private fun PreviewRowLargeFont() = PreviewRow(
    previewSession(waitTimeSeconds = 7 * 60L, tags = "roadtrip"),
    tripName = "Gaspé loop",
    vehicleName = "Ioniq 5",
    hasReceipt = true,
)

@Preview(
    name = "Row — dark",
    showBackground = true,
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewRowDark() = PreviewRow(
    previewSession(chargingType = ChargingType.AC_L2),
    vehicleName = "Ioniq 5",
)

/** Mixed currencies plus 2x font is the exact case the FlowRow was for: three
 *  unweighted columns with the widest possible total. */
@Preview(name = "Summary — mixed currency, 2x font", showBackground = true, widthDp = 400, fontScale = 2f)
@Composable
private fun PreviewSummaryLargeFont() {
    EvsctTheme {
        SummaryCard(
            SessionListUi(
                isLoading = false,
                sessionCount = 128,
                totalKwh = 3_412.75,
                totalCostByCurrency = CurrencyTotals(mapOf("CAD" to 1_284.50, "USD" to 96.20)),
            )
        )
    }
}

/** Just under [STACK_STATS_FONT_SCALE], so this is the wrapped-row branch — the
 *  layout the stacking exists to replace. Keep it: it's the check that the
 *  fallback still behaves for the widths font scale can't predict. */
@Preview(name = "Summary — 1.3x (still a row)", showBackground = true, widthDp = 400, fontScale = 1.3f)
@Composable
private fun PreviewSummaryBelowThreshold() {
    EvsctTheme {
        SummaryCard(
            SessionListUi(
                isLoading = false,
                sessionCount = 128,
                totalKwh = 3_412.75,
                totalCostByCurrency = CurrencyTotals(mapOf("CAD" to 1_284.50, "USD" to 96.20)),
            )
        )
    }
}

@Preview(name = "Summary — normal", showBackground = true, widthDp = 400)
@Composable
private fun PreviewSummaryNormal() {
    EvsctTheme {
        SummaryCard(
            SessionListUi(
                isLoading = false,
                sessionCount = 128,
                totalKwh = 3_412.75,
                totalCostByCurrency = CurrencyTotals(mapOf("CAD" to 1_284.50)),
            )
        )
    }
}
