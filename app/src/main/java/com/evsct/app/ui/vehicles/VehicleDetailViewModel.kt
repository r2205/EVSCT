package com.evsct.app.ui.vehicles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
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
    val vehicle: Vehicle? = null,
    val sessions: List<ChargingSession> = emptyList(),
    val sessionCount: Int = 0,
    val totalCost: Double = 0.0,
    val totalEnergyKwh: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val avgEffPricePerKwh: Double? = null,
    val avgPowerKw: Double? = null,
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
)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val vehicleId: Long = savedStateHandle.get<Long>(Routes.VEHICLE_DETAIL_ARG) ?: -1L

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val state: StateFlow<VehicleDetailUi>

    init {
        viewModelScope.launch { refreshVehicle() }

        state = combine(_vehicle.asStateFlow(), sessionRepository.observeAll()) { vehicle, allSessions ->
            val sessions = allSessions.filter { it.vehicleId == vehicleId }
                .sortedByDescending { it.sessionStart }
            buildUi(vehicle, sessions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUi())
    }

    fun refresh() = viewModelScope.launch { refreshVehicle() }

    private suspend fun refreshVehicle() {
        _vehicle.value = vehicleRepository.findById(vehicleId)
    }

    private fun buildUi(vehicle: Vehicle?, sessions: List<ChargingSession>): VehicleDetailUi {
        if (vehicle == null) return VehicleDetailUi()

        val totalCost = sessions.sumOf { it.totalCost ?: 0.0 }
        val totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 }
        val odometers = sessions.mapNotNull { it.odometerKm }
        val totalDistance = if (odometers.size < 2) 0.0 else odometers.max() - odometers.min()

        val avgEff = if (totalKwh > 0) totalCost / totalKwh else null
        val totalHours = sessions.sumOf { (it.durationSeconds ?: 0L) / 3600.0 }
        val avgPower = if (totalHours > 0) totalKwh / totalHours else null
        val costPerKm = if (totalDistance > 0) totalCost / totalDistance else null

        val fastest = sessions
            .mapNotNull { s -> Derived.effectiveAvgPowerKw(s)?.let { VehicleHighlight(s, it) } }
            .maxByOrNull { it.value }

        val priceHighlights = sessions
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
            totalCost = totalCost,
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
        )
    }
}
