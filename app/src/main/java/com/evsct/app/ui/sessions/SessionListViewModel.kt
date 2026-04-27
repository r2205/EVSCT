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

data class SessionListUi(
    val sessions: List<ChargingSession> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val tripNamesById: Map<Long, String> = emptyMap(),
    val vehicleNamesById: Map<Long, String> = emptyMap(),
    val totalCost: Double = 0.0,
    val totalKwh: Double = 0.0,
    val sessionCount: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
    val vehicleFilterId: Long? = null,
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

    val state: StateFlow<SessionListUi> =
        combine(
            sessionRepository.observeAll(),
            tripRepository.observeAll(),
            vehicleRepository.observeAll(),
            selected,
            vehicleFilter,
        ) { allSessions, trips, vehicles, selectedIds, filter ->
            // If the picked vehicle no longer exists, drop back to "All".
            val effectiveFilter = filter?.takeIf { id -> vehicles.any { it.id == id } }
            val sessions = if (effectiveFilter == null) allSessions
            else allSessions.filter { it.vehicleId == effectiveFilter }

            val sessionIdSet = sessions.mapTo(mutableSetOf()) { it.id }
            val cleanedSelection = selectedIds.intersect(sessionIdSet)

            SessionListUi(
                sessions = sessions,
                trips = trips,
                vehicles = vehicles,
                tripNamesById = trips.associate { it.id to it.name },
                vehicleNamesById = vehicles.associate { it.id to it.name },
                totalCost = sessions.sumOf { it.totalCost ?: 0.0 },
                totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
                sessionCount = sessions.size,
                selectedIds = cleanedSelection,
                vehicleFilterId = effectiveFilter,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUi())

    fun setVehicleFilter(vehicleId: Long?) {
        vehicleFilter.value = vehicleId
        // Drop selection so it doesn't span across two filter views.
        if (selected.value.isNotEmpty()) selected.value = emptySet()
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
