package com.evsct.app.ui.vehicles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.Derived
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

data class VehicleHighlight(
    val session: ChargingSession,
    val value: Double,
)

data class VehicleDetailUi(
    /** True until the first vehicle lookup completes. Distinguishes "still
     *  loading" (spinner) from "vehicle genuinely missing" (not-found). */
    val isLoading: Boolean = true,
    val vehicle: Vehicle? = null,
    val sessions: List<ChargingSession> = emptyList(),
    val sessionCount: Int = 0,
    /** Costs grouped by per-session currency. Mixed currencies render as a
     *  breakdown ("$245 CAD · $89 USD") and suppress derived rates. */
    val totalCostByCurrency: CurrencyTotals = CurrencyTotals(emptyMap()),
    val totalEnergyKwh: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    /** Avg effective $/kWh across the vehicle's lifetime — only meaningful
     *  when every session shares one currency. Null when mixed. */
    val avgEffPricePerKwh: Double? = null,
    val avgPowerKw: Double? = null,
    /** Cost per km — same single-currency rule as [avgEffPricePerKwh]. */
    val costPerKm: Double? = null,
    val fastestSession: VehicleHighlight? = null,
    val cheapestPriceSession: VehicleHighlight? = null,
    val mostExpensivePriceSession: VehicleHighlight? = null,
    val mostUsedBrand: Pair<String, Int>? = null,
    val lastChargedAt: Long? = null,
    /** Distance per energy across measurable legs (same vehicle, consecutive
     *  by trip or by the user-set "continues from previous" flag). Stored as
     *  km/kWh; the screen converts to mi/kWh when needed. */
    val avgKmPerKwh: Double? = null,
    /** Sum of durationSeconds across this vehicle's sessions (null durations
     *  contribute 0). */
    val totalChargeSeconds: Long = 0L,
    /** How many of this vehicle's sessions are missing a durationSeconds
     *  value. When > 0 the total is a lower bound and the UI flags it. */
    val sessionsWithoutDuration: Int = 0,
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val vehicleId: Long = savedStateHandle.get<Long>(Routes.VEHICLE_DETAIL_ARG) ?: -1L

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    private val _vehicleLookedUp = MutableStateFlow(false)
    val state: StateFlow<VehicleDetailUi>

    init {
        viewModelScope.launch { refreshVehicle() }

        state = combine(
            _vehicle.asStateFlow(),
            _vehicleLookedUp.asStateFlow(),
            sessionRepository.observeAll(),
        ) { vehicle, lookedUp, allSessions ->
            val sessions = allSessions.filter { it.vehicleId == vehicleId }
                .sortedByDescending { it.sessionStart }
            buildUi(vehicle, sessions).copy(isLoading = !lookedUp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUi())
    }

    fun refresh() = viewModelScope.launch { refreshVehicle() }

    private suspend fun refreshVehicle() {
        _vehicle.value = vehicleRepository.findById(vehicleId)
        _vehicleLookedUp.value = true
    }

    private fun buildUi(vehicle: Vehicle?, sessions: List<ChargingSession>): VehicleDetailUi {
        if (vehicle == null) return VehicleDetailUi()

        val totals = CurrencyTotals.from(sessions)
        val totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 }
        val odometers = sessions.mapNotNull { it.odometerKm }
        val totalDistance = if (odometers.size < 2) 0.0 else odometers.max() - odometers.min()

        // Derived rates only compose when every session shares a currency.
        // When mixed, a "$/kWh" or "$/km" number has no single unit.
        val singleTotal = totals.singleTotal
        val avgEff = if (singleTotal != null && totalKwh > 0) singleTotal / totalKwh else null
        // Avg power must divide energy by charge time from the SAME
        // sessions. Summing kWh over all sessions but hours over only the
        // ones with a duration (imports routinely lack durations) inflated
        // the stat arbitrarily — ten 50 kWh charges with one recorded hour
        // read as 500 kW.
        val powerPaired = sessions.filter { it.energyKwh != null && (it.durationSeconds ?: 0L) > 0L }
        val pairedKwh = powerPaired.sumOf { it.energyKwh ?: 0.0 }
        val pairedHours = powerPaired.sumOf { (it.durationSeconds ?: 0L) / 3600.0 }
        val avgPower = if (pairedHours > 0) pairedKwh / pairedHours else null
        val costPerKm = if (singleTotal != null && totalDistance > 0) singleTotal / totalDistance else null

        val fastest = sessions
            .mapNotNull { s -> Derived.effectiveAvgPowerKw(s)?.let { VehicleHighlight(s, it) } }
            .maxByOrNull { it.value }

        // Rank the cheapest/most-expensive $/kWh within a single currency only,
        // since "$0.30/kWh USD" vs "$0.32/kWh CAD" can't be ranked by face value.
        // Pick the dominant currency (the one with the largest total spend) so
        // road-trip outliers don't take over the highlight cards.
        val dominantCurrency = totals.byCurrency.entries.maxByOrNull { it.value }?.key
        val priceHighlights = sessions
            .filter { it.currency == dominantCurrency }
            .mapNotNull { s -> Derived.effectiveEnergyPricePerKwh(s)?.let { VehicleHighlight(s, it) } }
            .filter { it.value > 0 }

        val mostUsedBrand = sessions
            .mapNotNull { it.brand?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.toPair()

        val efficiency = EfficiencyAnalysis.analyze(sessions, vehicle)

        return VehicleDetailUi(
            vehicle = vehicle,
            sessions = sessions,
            sessionCount = sessions.size,
            totalCostByCurrency = totals,
            totalEnergyKwh = totalKwh,
            totalDistanceKm = totalDistance,
            avgEffPricePerKwh = avgEff,
            avgPowerKw = avgPower,
            costPerKm = costPerKm,
            fastestSession = fastest,
            cheapestPriceSession = priceHighlights.minByOrNull { it.value },
            mostExpensivePriceSession = priceHighlights.maxByOrNull { it.value },
            mostUsedBrand = mostUsedBrand,
            lastChargedAt = sessions.maxOfOrNull { it.sessionStart },
            avgKmPerKwh = efficiency.avgKmPerKwh,
            totalChargeSeconds = sessions.sumOf { it.durationSeconds ?: 0L },
            sessionsWithoutDuration = sessions.count { it.durationSeconds == null },
        )
    }
}
