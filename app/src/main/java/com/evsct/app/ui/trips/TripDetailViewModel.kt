package com.evsct.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailUi(
    val trip: Trip? = null,
    val sessions: List<ChargingSession> = emptyList(),
    val stats: TripWithStats? = null,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<Long>(Routes.TRIP_DETAIL_ARG) ?: -1L

    private val _trip = MutableStateFlow<Trip?>(null)
    val state: StateFlow<TripDetailUi>

    init {
        viewModelScope.launch {
            _trip.value = tripRepository.findById(tripId)
        }
        state = combine(_trip.asStateFlow(), sessionRepository.observeForTrip(tripId)) { trip, sessions ->
            val stats = trip?.let {
                TripWithStats(
                    trip = it,
                    sessionCount = sessions.size,
                    totalCost = sessions.sumOf { s -> s.totalCost ?: 0.0 },
                    totalEnergyKwh = sessions.sumOf { s -> s.energyKwh ?: 0.0 },
                    totalDistanceKm = sessions.mapNotNull { s -> s.odometerKm }
                        .let { o -> if (o.size < 2) 0.0 else o.max() - o.min() },
                )
            }
            TripDetailUi(trip = trip, sessions = sessions, stats = stats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUi())
    }
}
