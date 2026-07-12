package com.evsct.app.ui.sessions

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.prefs.CardTimeRate
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.MoneyStat
import com.evsct.app.ui.theme.EvAccents
import com.evsct.app.util.Derived
import com.evsct.app.util.Format
import com.evsct.app.util.Tags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onAddSession: (preselectVehicleId: Long?) -> Unit,
    onStartTrackedSession: (sessionId: Long) -> Unit,
    onEditSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTripPicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // A delete just happened — one session from the edit screen, or a
    // multi-select batch from this screen: offer a window to take it back.
    // Undo re-inserts the rows (and re-links any receipt files, which stay
    // on disk until this offer resolves).
    val pendingUndo by viewModel.pendingDeleteUndo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingUndo) {
        val pending = pendingUndo
        if (pending != null) {
            val n = pending.sessions.size
            val result = snackbarHostState.showSnackbar(
                message = if (n == 1) "Session deleted" else "$n sessions deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.undoDelete()
                SnackbarResult.Dismissed -> viewModel.finalizeDeleteUndo()
            }
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
                    title = { Text("Charging log", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch) "Close search" else "Search",
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
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
                                Icon(Icons.Default.Checklist, contentDescription = "Select sessions")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add session")
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
            if (state.vehicles.size >= 2) {
                VehicleTabs(
                    vehicles = state.vehicles,
                    selectedVehicleId = state.vehicleFilterId,
                    onSelect = { id -> viewModel.setVehicleFilter(id) },
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
                    com.evsct.app.ui.EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matching sessions",
                        body = "Your search and filters don't match any " +
                            "sessions.",
                        actionLabel = "Clear filters",
                        onAction = {
                            showSearch = false
                            viewModel.clearFilters()
                        },
                    )
                } else if (state.vehicleFilterId == null && state.vehicles.isEmpty()) {
                    // First-launch path: no vehicles, no sessions. Saving a
                    // session without a vehicle works but leaves it untagged
                    // and skews per-vehicle stats, so route the user to set
                    // up a vehicle first.
                    com.evsct.app.ui.EmptyState(
                        icon = Icons.Default.DirectionsCar,
                        title = "Welcome to EVSCT",
                        body = "Add a vehicle first so charging sessions can " +
                            "be tagged to it for stats and efficiency tracking.",
                        actionLabel = "Open Settings",
                        onAction = onOpenSettings,
                    )
                } else {
                    EmptyState(state.vehicleFilterId != null)
                }
            } else {
                SummaryCard(state)
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.id }) { s ->
                        val isSelected = s.id in state.selectedIds
                        // Only surface the vehicle name when the user is on the
                        // "All" tab — otherwise every row would carry the same
                        // tag.
                        val vehicleName = if (state.vehicleFilterId == null) {
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
                        )
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
            title = { Text(if (n == 1) "Delete 1 session?" else "Delete $n sessions?") },
            text = {
                Text(
                    "This permanently removes the selected sessions and any " +
                        "receipts attached to them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showBulkDeleteConfirm = false
                    viewModel.deleteSelectedSessions()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddSheet) {
        AddSessionChooserSheet(
            onTrackNow = {
                showAddSheet = false
                startTrackedSession(state.vehicleFilterId)
            },
            onAddPast = {
                showAddSheet = false
                onAddSession(state.vehicleFilterId)
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
                text = { Text(option.label) },
                onClick = { onSelect(option) },
                trailingIcon = {
                    if (option == current) {
                        Icon(Icons.Default.Check, contentDescription = "Selected")
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
        title = { Text("$selectedCount selected", fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.DoneAll, contentDescription = "Select all")
            }
            IconButton(onClick = onAssignTrip, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Assign trip")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected")
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
                "Log a charge",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            AddSessionChooserRow(
                icon = Icons.Default.Bolt,
                title = "Track a charge now",
                subtitle = "Start a live timer for a charge happening right now. " +
                    "A notification keeps it handy while you wait.",
                onClick = onTrackNow,
            )
            HorizontalDivider()
            AddSessionChooserRow(
                icon = Icons.Default.Add,
                title = "Log a past charge",
                subtitle = "Backfill the details of a charge you've already finished.",
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
                "Assign $selectedCount session${if (selectedCount == 1) "" else "s"} to…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            TripPickerRow(
                label = "Unassigned",
                emphasis = false,
                onClick = { onPick(null) },
            )
            HorizontalDivider()
            if (trips.isEmpty()) {
                Text(
                    "No trips yet. Create one from the Trips screen.",
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
    val units = LocalUserUnits.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("Sessions", state.sessionCount.toString())
            MoneyStat("Total cost", state.totalCostByCurrency)
            Stat("Energy", Format.kwh(state.totalKwh))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(filtered: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(72.dp)
                    .width(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (filtered) "No sessions for this vehicle" else "No sessions yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (filtered) "Tap + to add one, or pick All to see other vehicles."
                else "Tap + to log your first charge.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTabs(
    vehicles: List<com.evsct.app.data.entity.Vehicle>,
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
                text = { Text(label, fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal) },
            )
        }
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
) {
    val units = LocalUserUnits.current
    val typeAccent = chargingTypeAccent(session.chargingType)
    val barColor = typeAccent.bar
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                            session.brand ?: "Unknown",
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
                                    contentDescription = "Has receipt",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(session.chargingType)
                    Spacer(Modifier.width(8.dp))
                    Text(Format.kwh(session.energyKwh), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    val waitNote = session.waitTimeMinutes
                        ?.takeIf { it > 0 }
                        ?.let { " +${it}m wait" }
                        .orEmpty()
                    Text(
                        Format.duration(session.durationSeconds) + waitNote,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Format.kw(Derived.effectiveAvgPowerKw(session)) + " avg",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val effPrice = Derived.effectiveEnergyPricePerKwh(session)
                val timeRate = when (units.cardTimeRate) {
                    CardTimeRate.PER_MINUTE ->
                        Derived.effectiveTimeRatePerMin(session)?.let { Format.moneyPerTime(it, "min") }
                    CardTimeRate.PER_HOUR ->
                        Derived.effectiveTimeRatePerHour(session)?.let { Format.moneyPerTime(it, "hr") }
                    CardTimeRate.OFF -> null
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
                                "Eff. ${Format.moneyRate(effPrice, "kWh")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (timeRate != null) {
                            Text(
                                "Eff. $timeRate",
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
                val tags = remember(session.tags) { Tags.parse(session.tags) }
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
    val accent = chargingTypeAccent(type)
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

private data class TypeAccent(val bar: Color, val container: Color, val onContainer: Color)

// Shared per-type instances. chargingTypeAccent() runs for every visible row
// (and again inside each row's TypeBadge), so returning constants avoids
// allocating a fresh TypeAccent on every call while scrolling.
private val DcFastAccent = TypeAccent(EvAccents.DcFast, EvAccents.DcFastContainer, Color(0xFF3B2400))
private val AcL2Accent = TypeAccent(EvAccents.AcL2, EvAccents.AcL2Container, Color(0xFF002B57))
private val AcL1Accent = TypeAccent(EvAccents.AcL1, EvAccents.AcL1Container, Color(0xFF200052))

private fun chargingTypeAccent(type: ChargingType): TypeAccent = when (type) {
    ChargingType.DC_FAST -> DcFastAccent
    ChargingType.AC_L2 -> AcL2Accent
    ChargingType.AC_L1 -> AcL1Accent
}

private fun ChargingType.shortLabel(): String = when (this) {
    ChargingType.DC_FAST -> "DC FAST"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
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
                placeholder = { Text("Search city, address, notes…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
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
                    Icon(Icons.Default.Tune, contentDescription = "Filters")
                }
            }
            if (filters.hasActive) {
                TextButton(onClick = onClearAll) { Text("Clear") }
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
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove brand filter") },
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
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove date filter") },
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
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove tag filter") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatDateRange(from: Long?, to: Long?): String {
    val fmt = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return when {
        from != null && to != null -> "${fmt.format(java.util.Date(from))} – ${fmt.format(java.util.Date(to))}"
        from != null -> "From ${fmt.format(java.util.Date(from))}"
        to != null -> "Until ${fmt.format(java.util.Date(to))}"
        else -> "Date"
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
                "Filter sessions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            Text("Date range", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                DatePresetChip("All time", isActive = dateFrom == null && dateTo == null) {
                    dateFrom = null; dateTo = null
                }
                DatePresetChip("This month", isActive = matchesPreset(dateFrom, dateTo, DatePreset.THIS_MONTH)) {
                    val (f, t) = DatePreset.THIS_MONTH.range()
                    dateFrom = f; dateTo = t
                }
                DatePresetChip("Last 3 mo.", isActive = matchesPreset(dateFrom, dateTo, DatePreset.LAST_3_MONTHS)) {
                    val (f, t) = DatePreset.LAST_3_MONTHS.range()
                    dateFrom = f; dateTo = t
                }
                DatePresetChip("Last year", isActive = matchesPreset(dateFrom, dateTo, DatePreset.LAST_YEAR)) {
                    val (f, t) = DatePreset.LAST_YEAR.range()
                    dateFrom = f; dateTo = t
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showFromPicker = true },
                    label = { Text(dateFrom?.let { "From: ${formatDateRange(it, null).removePrefix("From ")}" } ?: "From…") },
                )
                AssistChip(
                    onClick = { showToPicker = true },
                    label = { Text(dateTo?.let { "To: ${formatDateRange(null, it).removePrefix("Until ")}" } ?: "To…") },
                )
            }
            // Visible validation when the user accidentally inverts the range —
            // otherwise the predicate (sessionStart >= from AND <= to) silently
            // hides every session and the list looks empty for no obvious reason.
            val dateRangeInvalid = dateFrom != null && dateTo != null && dateFrom!! > dateTo!!
            if (dateRangeInvalid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "“From” must be on or before “To”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Brand", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (brands.isEmpty()) {
                Text(
                    "No brands recorded yet.",
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
                        label = { Text("Any brand") },
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
            Text("Tags", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (tags.isEmpty()) {
                Text(
                    "No tags yet — add some when editing a session.",
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
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    enabled = !dateRangeInvalid,
                    onClick = { onApply(brand, dateFrom, dateTo, tagSelection) },
                ) {
                    Text("Apply")
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
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
                        "Still charging?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "A tracked charge started ${Format.dateTime(session.sessionStart)} " +
                            "is still running. Open it to finish or delete it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(onClick = onOpen) { Text("Open session") }
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
            d < 1L -> "today"
            d == 1L -> "1 day ago"
            d < 365L -> "$d days ago"
            else -> "over a year ago"
        }
    }
    val message = if (daysText == null) {
        "You haven't backed up yet — protect your sessions before they're lost."
    } else {
        "Last backup was $daysText. Time to refresh it."
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
                        "Back up your data?",
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
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Not now") }
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                    Text("Open Settings")
                }
            }
        }
    }
}
