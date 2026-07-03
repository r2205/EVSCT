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
import com.evsct.app.util.EfficiencyAnalysis
import com.evsct.app.util.ExcludedPair
import com.evsct.app.util.TripAnchor
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
            val stats = trip?.let {
                TripWithStats(
                    trip = it,
                    sessionCount = sessions.size,
                    totalCostByCurrency = CurrencyTotals.from(sessions),
                    totalEnergyKwh = sessions.sumOf { s -> s.energyKwh ?: 0.0 },
                    totalDistanceKm = TripRepository.computeTripDistance(it, sessions),
                )
            }
            val analysis = analyzeLegs(trip, sessions, allSessions, vehicles)
            val totalChargeSeconds = sessions.sumOf { it.durationSeconds ?: 0L }
            val sessionsWithoutDuration = sessions.count { it.durationSeconds == null }
            TripDetailUi(
                trip = trip,
                sessions = sessions,
                stats = stats,
                totalChargeSeconds = totalChargeSeconds,
                sessionsWithoutDuration = sessionsWithoutDuration,
                legs = analysis.legs,
                excludedLegs = analysis.excluded,
                avgKmPerKwh = analysis.avgKmPerKwh,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUi())
    }

    private data class TripAnalysis(
        val legs: List<DrivingLeg>,
        val excluded: List<ExcludedPair>,
        val avgKmPerKwh: Double?,
    )

    /** Group sessions by vehicle, run analysis per group, and merge. The trip's
     *  weighted avg km/kWh treats every leg equally regardless of which car
     *  drove it (totals over totals, not a mean of means). Each group also
     *  gets the vehicle's complete session history so pairs with an
     *  out-of-trip charge in between are excluded instead of silently
     *  producing a distorted leg.
     *
     *  The trip's start/end battery+odometer readings anchor the first and
     *  last legs — but only when the trip is single-vehicle: "the trip
     *  started at 100%" is ambiguous across two cars. */
    private fun analyzeLegs(
        trip: Trip?,
        sessions: List<ChargingSession>,
        allSessions: List<ChargingSession>,
        vehicles: List<Vehicle>,
    ): TripAnalysis {
        val byVehicle = sessions.groupBy { it.vehicleId }
        val startAnchor = trip
            ?.let { TripAnchor(it.startOdometerKm, it.startBatteryPct, it.startDate) }
            ?.takeIf { it.hasData }
        val endAnchor = trip
            ?.let { TripAnchor(it.endOdometerKm, it.endBatteryPct, it.endDate) }
            ?.takeIf { it.hasData }

        val allLegs = mutableListOf<DrivingLeg>()
        val allExcluded = mutableListOf<ExcludedPair>()
        for ((vehicleId, group) in byVehicle) {
            val v = vehicles.firstOrNull { it.id == vehicleId }
            val vehicleTimeline = allSessions.filter { it.vehicleId == vehicleId }
            val report = EfficiencyAnalysis.analyze(
                group, v, vehicleTimeline,
                tripStart = startAnchor.takeIf { byVehicle.size == 1 },
                tripEnd = endAnchor.takeIf { byVehicle.size == 1 },
            )
            allLegs += report.legs
            allExcluded += report.excluded
        }
        // A trip with boundary readings but no sessions yet: still one
        // whole-trip leg, when the garage has exactly one car to pin the
        // battery capacity on.
        if (byVehicle.isEmpty() && (startAnchor != null || endAnchor != null) &&
            vehicles.size == 1
        ) {
            val only = vehicles.single()
            val report = EfficiencyAnalysis.analyze(
                emptyList(), only,
                allSessions.filter { it.vehicleId == only.id },
                tripStart = startAnchor,
                tripEnd = endAnchor,
            )
            allLegs += report.legs
            allExcluded += report.excluded
        }
        val totalKm = allLegs.sumOf { it.distanceKm }
        val totalKwh = allLegs.sumOf { it.energyUsedKwh }
        val avg = if (totalKm > 0 && totalKwh > 0) totalKm / totalKwh else null
        return TripAnalysis(
            legs = allLegs.sortedBy { it.to.sessionStart },
            excluded = allExcluded.sortedBy { it.to.sessionStart },
            avgKmPerKwh = avg,
        )
    }

    fun updateTrip(trip: Trip) = viewModelScope.launch {
        tripRepository.upsert(trip)
        refresh()
    }

    private suspend fun refresh() {
        _trip.value = tripRepository.findById(tripId)
    }
}
