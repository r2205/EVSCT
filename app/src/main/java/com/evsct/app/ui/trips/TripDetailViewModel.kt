package com.evsct.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.DrivingLeg
import com.evsct.app.util.ExcludedPair
import com.evsct.app.util.TripReport
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
    /** Sum of [ChargingSession.durationSeconds] across the trip's sessions
     *  (null durations contribute 0). */
    val totalChargeSeconds: Long = 0L,
    /** How many sessions in the trip have a null durationSeconds. When > 0
     *  the total is a lower bound and the UI flags it. */
    val sessionsWithoutDuration: Int = 0,
    val legs: List<DrivingLeg> = emptyList(),
    val excludedLegs: List<ExcludedPair> = emptyList(),
    val avgKmPerKwh: Double? = null,
    /** Every vehicle, for the edit dialog's picker and for naming the car a
     *  mismatched session was logged on. */
    val vehicles: List<Vehicle> = emptyList(),
    /** The trip's car; null when the trip has none yet (or it was deleted). */
    val vehicle: Vehicle? = null,
    /** Ids of tagged sessions logged on a car other than the trip's. */
    val otherCarSessionIds: Set<Long> = emptySet(),
    /** How many tagged sessions have no vehicle set (counted as the trip's car). */
    val unassignedSessionCount: Int = 0,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<Long>(Routes.TRIP_DETAIL_ARG) ?: -1L

    private val _trip = MutableStateFlow<Trip?>(null)
    val state: StateFlow<TripDetailUi>

    init {
        viewModelScope.launch { refresh() }
        state = combine(
            _trip.asStateFlow(),
            sessionRepository.observeForTrip(tripId),
            // Full session list so the efficiency analysis can detect
            // charges that happened between two trip sessions but aren't in
            // the trip (e.g. a home top-up mid-trip). Pairing across those
            // silently distorts the leg's distance and battery delta.
            sessionRepository.observeAll(),
            vehicleRepository.observeAll(),
        ) { trip, sessions, allSessions, vehicles ->
            val report = trip?.let { TripReport.of(it, sessions, allSessions, vehicles) }
            val stats = trip?.let {
                TripWithStats(
                    trip = it,
                    sessionCount = sessions.size,
                    totalCostByCurrency = CurrencyTotals.from(sessions),
                    totalEnergyKwh = sessions.sumOf { s -> s.energyKwh ?: 0.0 },
                    totalDistanceKm = report?.distanceKm ?: 0.0,
                )
            }
            val totalChargeSeconds = sessions.sumOf { it.durationSeconds ?: 0L }
            val sessionsWithoutDuration = sessions.count { it.durationSeconds == null }
            TripDetailUi(
                trip = trip,
                sessions = sessions,
                stats = stats,
                totalChargeSeconds = totalChargeSeconds,
                sessionsWithoutDuration = sessionsWithoutDuration,
                legs = report?.legs.orEmpty(),
                excludedLegs = report?.excluded.orEmpty(),
                avgKmPerKwh = report?.avgKmPerKwh,
                vehicles = vehicles,
                vehicle = report?.vehicle,
                otherCarSessionIds = report?.otherCarSessions.orEmpty().mapTo(mutableSetOf()) { it.id },
                unassignedSessionCount = report?.unassignedSessions?.size ?: 0,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUi())
    }

    fun updateTrip(trip: Trip) = viewModelScope.launch {
        tripRepository.upsert(trip)
        refresh()
    }

    private suspend fun refresh() {
        _trip.value = tripRepository.findById(tripId)
    }
}
