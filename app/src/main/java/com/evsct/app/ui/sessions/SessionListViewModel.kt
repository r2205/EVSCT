package com.evsct.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.BackupReminderSettings
import com.evsct.app.data.repository.SessionReceiptRepository
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.InProgressChargeNotifier
import com.evsct.app.util.Tags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionFilters(
    val query: String = "",
    val brand: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    /** Selected tags. A session matches when it carries at least one of these
     *  (OR semantics). Empty = no tag filter. Compared case-insensitively. */
    val tags: Set<String> = emptySet(),
) {
    val hasActive: Boolean
        get() = query.isNotBlank() || brand != null || dateFrom != null ||
            dateTo != null || tags.isNotEmpty()
}

/**
 * Sort order for the charging log. Date is the default and matches what the
 * DAO returns; other options re-sort in-memory after filters apply. Sessions
 * missing the sort field fall to the end of the list so the ones with data
 * stay actionable at the top.
 */
enum class SortOption(val label: String) {
    DATE("Date (newest)"),
    COST("Cost (highest)"),
    EFFICIENCY("Efficiency ($/kWh)"),
    BRAND("Brand (A–Z)"),
}

data class BackupNudge(
    val show: Boolean = false,
    val daysSinceLastBackup: Long? = null,
)

data class SessionListUi(
    val sessions: List<ChargingSession> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val brandsInUse: List<String> = emptyList(),
    /** All distinct tags across every session, deduped case-insensitively
     *  (display reflects the first-seen casing) and sorted A–Z. */
    val tagsInUse: List<String> = emptyList(),
    val tripNamesById: Map<Long, String> = emptyMap(),
    val vehicleNamesById: Map<Long, String> = emptyMap(),
    /** Session ids that carry at least one receipt — drives the paperclip
     *  icon on each row without forcing a per-row DAO query. */
    val sessionsWithReceipts: Set<Long> = emptySet(),
    val totalCostByCurrency: CurrencyTotals = CurrencyTotals(emptyMap()),
    val totalKwh: Double = 0.0,
    val sessionCount: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
    val vehicleFilterId: Long? = null,
    val filters: SessionFilters = SessionFilters(),
    val sortOption: SortOption = SortOption.DATE,
    val backupNudge: BackupNudge = BackupNudge(),
    /** The quick-tracked session, when it has been running past the stale
     *  threshold — a charge "in progress" for 12+ hours was almost
     *  certainly abandoned. Drives the "Still charging?" banner; null when
     *  nothing is tracked or the charge is still young. */
    val staleTrackedSession: ChargingSession? = null,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val sessionReceiptRepository: SessionReceiptRepository,
    private val appPreferences: AppPreferences,
    private val inProgressChargeNotifier: InProgressChargeNotifier,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val vehicleFilter = MutableStateFlow<Long?>(null)
    private val filters = MutableStateFlow(SessionFilters())
    private val sortOption = MutableStateFlow(SortOption.DATE)
    private val backupNudgeDismissed = MutableStateFlow(false)

    /** Bundle filter + sort so the outer combine still fits the 5-arg combine. */
    private val filtersAndSort = combine(filters, sortOption) { f, s -> f to s }

    /** Bundle the slow-changing data into one Triple so the outer combine fits the
     *  built-in 5-arg overload comfortably. */
    private val coreData = combine(
        sessionRepository.observeAll(),
        tripRepository.observeAll(),
        vehicleRepository.observeAll(),
    ) { sessions, trips, vehicles -> Triple(sessions, trips, vehicles) }

    private val baseUi: kotlinx.coroutines.flow.Flow<Pair<SessionListUi, Int>> =
        combine(coreData, selected, vehicleFilter, filtersAndSort) { core, selectedIds, filter, fs ->
            val (allSessions, trips, vehicles) = core
            val (f, sort) = fs

            // Drop a vehicle filter that points to a deleted vehicle.
            val effectiveVehicleFilter = filter?.takeIf { id -> vehicles.any { it.id == id } }

            // Distinct brands from the unfiltered set, useful for the brand filter
            // sheet so the picker stays the same regardless of the active filters.
            val brandsInUse = allSessions
                .mapNotNull { it.brand?.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }

            // Same idea for tags: aggregate across the unfiltered set so the
            // filter sheet shows every tag the user has ever used, regardless
            // of what filters are currently active.
            val tagsInUse = allSessions
                .asSequence()
                .flatMap { Tags.parse(it.tags).asSequence() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
                .toList()

            val sessions = allSessions.asSequence()
                .filter { effectiveVehicleFilter == null || it.vehicleId == effectiveVehicleFilter }
                .filter { it.matches(f) }
                .sortedWith(comparatorFor(sort))
                .toList()

            val sessionIdSet = sessions.mapTo(mutableSetOf()) { it.id }
            val cleanedSelection = selectedIds.intersect(sessionIdSet)

            SessionListUi(
                sessions = sessions,
                trips = trips,
                vehicles = vehicles,
                brandsInUse = brandsInUse,
                tagsInUse = tagsInUse,
                tripNamesById = trips.associate { it.id to it.name },
                vehicleNamesById = vehicles.associate { it.id to it.name },
                totalCostByCurrency = CurrencyTotals.from(sessions),
                totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
                sessionCount = sessions.size,
                selectedIds = cleanedSelection,
                vehicleFilterId = effectiveVehicleFilter,
                filters = f,
                sortOption = sort,
            ) to allSessions.size
        }

    /** Set of session ids that have at least one receipt attached. Derived
     *  from the receipts table so the row icon stays in sync without a
     *  per-row DAO query. */
    private val sessionsWithReceipts =
        sessionReceiptRepository.observeCountsBySession().map { rows ->
            rows.asSequence().filter { it.count > 0 }.mapTo(mutableSetOf()) { it.sessionId }
        }

    /** Quick-tracked session that has been running past [STALE_TRACKING_MS].
     *  Staleness is evaluated whenever an input emits (tracking starts or
     *  ends, any session changes) and again each time the screen
     *  resubscribes — so a charge that crosses the threshold while the app
     *  is closed gets flagged on the next open, which is when the banner
     *  can be seen anyway. */
    private val staleTrackedSession =
        combine(
            appPreferences.trackedChargeSessionIdFlow,
            sessionRepository.observeAll(),
        ) { trackedId, sessions ->
            if (trackedId == null) null
            else sessions.firstOrNull { it.id == trackedId }
                ?.takeIf { System.currentTimeMillis() - it.sessionStart >= STALE_TRACKING_MS }
        }

    /** Paired so the outer combine stays within the 5-flow overload. */
    private val receiptsAndStaleTracking =
        combine(sessionsWithReceipts, staleTrackedSession) { receipts, stale ->
            receipts to stale
        }

    val state: StateFlow<SessionListUi> =
        combine(
            baseUi,
            appPreferences.lastBackupAt,
            appPreferences.reminderSettings,
            backupNudgeDismissed,
            receiptsAndStaleTracking,
        ) { pair, lastBackupAt, reminder, dismissed, (withReceipts, staleTracked) ->
            val (ui, totalSessions) = pair
            ui.copy(
                backupNudge = computeBackupNudge(totalSessions, lastBackupAt, reminder, dismissed),
                sessionsWithReceipts = withReceipts,
                staleTrackedSession = staleTracked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUi())

    fun dismissBackupNudge() {
        backupNudgeDismissed.value = true
    }

    fun setVehicleFilter(vehicleId: Long?) {
        vehicleFilter.value = vehicleId
        if (selected.value.isNotEmpty()) selected.value = emptySet()
    }

    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
    }

    fun setBrandFilter(brand: String?) {
        filters.update { it.copy(brand = brand) }
    }

    fun setDateRange(from: Long?, to: Long?) {
        filters.update { it.copy(dateFrom = from, dateTo = to) }
    }

    fun setTagsFilter(tags: Set<String>) {
        filters.update { it.copy(tags = tags) }
    }

    fun clearFilters() {
        filters.value = SessionFilters()
    }

    fun setSortOption(option: SortOption) {
        sortOption.value = option
    }

    fun toggleSelection(id: Long) {
        selected.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun selectAll() {
        selected.value = state.value.sessions.mapTo(mutableSetOf()) { it.id }
    }

    fun assignTripToSelection(tripId: Long?) {
        // Act on the displayed selection (raw set ∩ visible rows), not the
        // raw set. Filters applied after selecting can hide rows without
        // clearing them from the raw set — the top bar says "3 selected",
        // and reassigning the hidden ones too would silently corrupt their
        // trip tags.
        val ids = state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            sessionRepository.assignTrip(ids, tripId)
            clearSelection()
        }
    }

    // Deliberately no list-level delete here. A bare repository.delete
    // would CASCADE the session_receipts rows but leak their files on
    // disk permanently — the edit screen's deleteAndExit is the one
    // correct session-delete path (it reconciles receipt files). If
    // list-side delete is ever added, route it through that logic.

    /**
     * Quick-track entry point for the "Start charge" FAB. Persists a fresh
     * session in the database with sessionStart = now, the active vehicle
     * tab (or the user's default), and the user's default currency — every
     * other field stays null so the edit screen can fill it in. Posts the
     * persistent in-progress notification so the user has a tap-shortcut
     * back to this session from the notification shade (incl. on the
     * Android Auto shade) while their charge runs. The created id is
     * handed back via [onCreated] so the caller can navigate to the
     * matching edit screen.
     */
    fun startTrackedSession(preselectVehicleId: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val units = appPreferences.userUnits.first()
            val effectiveVehicleId = preselectVehicleId
                ?: vehicleRepository.findDefault()?.id
            val now = System.currentTimeMillis()
            val session = ChargingSession(
                sessionStart = now,
                vehicleId = effectiveVehicleId,
                currency = units.defaultCurrency,
            )
            val savedId = sessionRepository.upsert(session)
            inProgressChargeNotifier.post(
                sessionId = savedId,
                brand = null,
                city = null,
                sessionStart = now,
            )
            onCreated(savedId)
        }
    }
}

/** A tracked charge older than this is presumed abandoned and gets the
 *  "Still charging?" banner. 12 h clears any DC or L2 session; only a
 *  multi-day Level 1 trickle charge can trip it legitimately, and there
 *  the banner is an ignorable question, not an action. */
private const val STALE_TRACKING_MS = 12L * 60 * 60 * 1000

private fun computeBackupNudge(
    totalSessions: Int,
    lastBackupAt: Long?,
    reminder: BackupReminderSettings,
    dismissed: Boolean,
): BackupNudge {
    if (dismissed || !reminder.enabled) return BackupNudge(show = false)
    if (lastBackupAt == null) {
        // Don't nag empty installs; only nudge once the user has accumulated
        // enough data to be worth backing up.
        if (totalSessions < AppPreferences.BACKUP_NUDGE_MIN_SESSIONS) return BackupNudge(show = false)
        return BackupNudge(show = true, daysSinceLastBackup = null)
    }
    val days = (System.currentTimeMillis() - lastBackupAt) / 86_400_000L
    return if (days >= reminder.thresholdDays) {
        BackupNudge(show = true, daysSinceLastBackup = days)
    } else {
        BackupNudge(show = false)
    }
}

private fun ChargingSession.matches(f: SessionFilters): Boolean {
    if (f.brand != null && !brand.equals(f.brand, ignoreCase = true)) return false
    if (f.dateFrom != null && sessionStart < f.dateFrom) return false
    if (f.dateTo != null && sessionStart > f.dateTo) return false
    if (f.tags.isNotEmpty()) {
        val sessionTags = Tags.parse(tags).map { it.lowercase() }.toSet()
        val wanted = f.tags.map { it.lowercase() }
        if (wanted.none { it in sessionTags }) return false
    }
    if (f.query.isNotBlank()) {
        val q = f.query.trim()
        val haystack = listOfNotNull(
            brand, locationCity, locationProvince, locationAddress,
            stationName, stallName, notes, tags,
        ).joinToString(" ")
        if (!haystack.contains(q, ignoreCase = true)) return false
    }
    return true
}

/**
 * Comparator for the requested sort. The primary key is whatever the user
 * picked; sessions missing the field sort to the end via nullsLast so rows
 * the user can actually compare stay at the top. Date is the secondary key
 * everywhere so ties always fall back to recency.
 */
private fun comparatorFor(sort: SortOption): Comparator<ChargingSession> {
    val recencyTiebreaker = compareByDescending<ChargingSession> { it.sessionStart }
    return when (sort) {
        SortOption.DATE -> recencyTiebreaker
        // compareByDescending swaps the operands, so nullsFirst (which orders
        // null < non-null) is what actually pushes nulls to the END.
        SortOption.COST -> compareByDescending<ChargingSession, Double?>(nullsFirst<Double>()) {
            it.totalCost
        }.then(recencyTiebreaker)
        SortOption.EFFICIENCY -> compareBy<ChargingSession, Double?>(nullsLast<Double>()) {
            com.evsct.app.util.Derived.effectiveEnergyPricePerKwh(it)
        }.then(recencyTiebreaker)
        SortOption.BRAND -> compareBy<ChargingSession, String?>(
            nullsLast<String>(String.CASE_INSENSITIVE_ORDER),
        ) {
            it.brand?.trim()?.takeIf { b -> b.isNotEmpty() }
        }.then(recencyTiebreaker)
    }
}
