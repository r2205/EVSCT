package com.evsct.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionListUi(
    val sessions: List<ChargingSession> = emptyList(),
    val tripNamesById: Map<Long, String> = emptyMap(),
    val totalCost: Double = 0.0,
    val totalKwh: Double = 0.0,
    val sessionCount: Int = 0,
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    val state: StateFlow<SessionListUi> =
        combine(sessionRepository.observeAll(), tripRepository.observeAll()) { sessions, trips ->
            SessionListUi(
                sessions = sessions,
                tripNamesById = trips.associate { it.id to it.name },
                totalCost = sessions.sumOf { it.totalCost ?: 0.0 },
                totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
                sessionCount = sessions.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUi())

    fun delete(session: ChargingSession) = viewModelScope.launch {
        sessionRepository.delete(session)
    }
}
