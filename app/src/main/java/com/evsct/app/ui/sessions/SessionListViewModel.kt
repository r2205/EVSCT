package com.evsct.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionFilters(
    val query: String = "",
    val brand: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
) {
    val hasActive: Boolean
        get() = query.isNotBlank() || brand != null || dateFrom != null || dateTo != null
}

data class SessionListUi(
    val sessions: List<ChargingSession> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val brandsInUse: List<String> = emptyList(),
    val tripNamesById: Map<Long, String> = emptyMap(),
    val vehicleNamesById: Map<Long, String> = emptyMap(),
    val totalCost: Double = 0.0,
    val totalKwh: Double = 0.0,
    val sessionCount: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
    val vehicleFilterId: Long? = null,
    val filters: SessionFilters = SessionFilters(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val vehicleFilter = MutableStateFlow<Long?>(null)
    private val filters = MutableStateFlow(SessionFilters())

    /** Bundle the slow-changing data into one Triple so the outer combine fits the
     *  built-in 5-arg overload comfortably. */
    private val coreData = combine(
        sessionRepository.observeAll(),
        tripRepository.observeAll(),
        vehicleRepository.observeAll(),
    ) { sessions, trips, vehicles -> Triple(sessions, trips, vehicles) }

    val state: StateFlow<SessionListUi> =
        combine(coreData, selected, vehicleFilter, filters) { core, selectedIds, filter, f ->
            val (allSessions, trips, vehicles) = core

            // Drop a vehicle filter that points to a deleted vehicle.
            val effectiveVehicleFilter = filter?.takeIf { id -> vehicles.any { it.id == id } }

            // Distinct brands from the unfiltered set, useful for the brand filter
            // sheet so the picker stays the same regardless of the active filters.
            val brandsInUse = allSessions
                .mapNotNull { it.brand?.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }

            val sessions = allSessions.asSequence()
                .filter { effectiveVehicleFilter == null || it.vehicleId == effectiveVehicleFilter }
                .filter { it.matches(f) }
                .toList()

            val sessionIdSet = sessions.mapTo(mutableSetOf()) { it.id }
            val cleanedSelection = selectedIds.intersect(sessionIdSet)

            SessionListUi(
                sessions = sessions,
                trips = trips,
                vehicles = vehicles,
                brandsInUse = brandsInUse,
                tripNamesById = trips.associate { it.id to it.name },
                vehicleNamesById = vehicles.associate { it.id to it.name },
                totalCost = sessions.sumOf { it.totalCost ?: 0.0 },
                totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
                sessionCount = sessions.size,
                selectedIds = cleanedSelection,
                vehicleFilterId = effectiveVehicleFilter,
                filters = f,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUi())

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

    fun clearFilters() {
        filters.value = SessionFilters()
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
        val ids = selected.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            sessionRepository.assignTrip(ids, tripId)
            clearSelection()
        }
    }

    fun delete(session: ChargingSession) = viewModelScope.launch {
        sessionRepository.delete(session)
    }
}

private fun ChargingSession.matches(f: SessionFilters): Boolean {
    if (f.brand != null && !brand.equals(f.brand, ignoreCase = true)) return false
    if (f.dateFrom != null && sessionStart < f.dateFrom) return false
    if (f.dateTo != null && sessionStart > f.dateTo) return false
    if (f.query.isNotBlank()) {
        val q = f.query.trim()
        val haystack = listOfNotNull(
            brand, locationCity, locationProvince, locationAddress,
            stationName, stallName, notes,
        ).joinToString(" ")
        if (!haystack.contains(q, ignoreCase = true)) return false
    }
    return true
}
