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
import com.evsct.app.util.DrivingLeg
import com.evsct.app.util.EfficiencyAnalysis
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
    val legs: List<DrivingLeg> = emptyList(),
    val avgKmPerKwh: Double? = null,
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
            vehicleRepository.observeAll(),
        ) { trip, sessions, vehicles ->
            val stats = trip?.let {
                TripWithStats(
                    trip = it,
                    sessionCount = sessions.size,
                    totalCost = sessions.sumOf { s -> s.totalCost ?: 0.0 },
                    totalEnergyKwh = sessions.sumOf { s -> s.energyKwh ?: 0.0 },
                    totalDistanceKm = TripRepository.computeTripDistance(it, sessions),
                )
            }
            val (legs, avg) = analyzeLegs(sessions, vehicles)
            TripDetailUi(
                trip = trip,
                sessions = sessions,
                stats = stats,
                legs = legs,
                avgKmPerKwh = avg,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUi())
    }

    /** Group sessions by vehicle, run analysis per group, and merge. The trip's
     *  weighted avg km/kWh treats every leg equally regardless of which car
     *  drove it (totals over totals, not a mean of means). */
    private fun analyzeLegs(
        sessions: List<ChargingSession>,
        vehicles: List<Vehicle>,
    ): Pair<List<DrivingLeg>, Double?> {
        val byVehicle = sessions.groupBy { it.vehicleId }
        val allLegs = mutableListOf<DrivingLeg>()
        for ((vehicleId, group) in byVehicle) {
            val v = vehicles.firstOrNull { it.id == vehicleId }
            allLegs += EfficiencyAnalysis.analyze(group, v).legs
        }
        val totalKm = allLegs.sumOf { it.distanceKm }
        val totalKwh = allLegs.sumOf { it.energyUsedKwh }
        val avg = if (totalKm > 0 && totalKwh > 0) totalKm / totalKwh else null
        return allLegs.sortedBy { it.to.sessionStart } to avg
    }

    fun updateTrip(trip: Trip) = viewModelScope.launch {
        tripRepository.upsert(trip)
        refresh()
    }

    private suspend fun refresh() {
        _trip.value = tripRepository.findById(tripId)
    }
}
