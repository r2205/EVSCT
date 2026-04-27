package com.evsct.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
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
    val tripNamesById: Map<Long, String> = emptyMap(),
    val totalCost: Double = 0.0,
    val totalKwh: Double = 0.0,
    val sessionCount: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<SessionListUi> =
        combine(
            sessionRepository.observeAll(),
            tripRepository.observeAll(),
            selected,
        ) { sessions, trips, selectedIds ->
            // Drop any stale ids that no longer match a row (e.g. after a delete).
            val sessionIdSet = sessions.mapTo(mutableSetOf()) { it.id }
            val cleaned = selectedIds.intersect(sessionIdSet)
            SessionListUi(
                sessions = sessions,
                trips = trips,
                tripNamesById = trips.associate { it.id to it.name },
                totalCost = sessions.sumOf { it.totalCost ?: 0.0 },
                totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
                sessionCount = sessions.size,
                selectedIds = cleaned,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUi())

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
