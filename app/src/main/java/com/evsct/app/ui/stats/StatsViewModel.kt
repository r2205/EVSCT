package com.evsct.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUi(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleFilterId: Long? = null,
    val sessionCount: Int = 0,
    val totalCost: Double = 0.0,
    val totalEnergyKwh: Double = 0.0,
    val avgEffPricePerKwh: Double? = null,
    val avgPowerKw: Double? = null,
    /** Last 12 months, oldest first; (label, $ spent). */
    val monthlyCost: List<Pair<String, Double>> = emptyList(),
    /** Last 12 months, oldest first; (label, kWh). */
    val monthlyEnergy: List<Pair<String, Double>> = emptyList(),
    /** Top brands by $ spent, descending. */
    val byBrandCost: List<Pair<String, Double>> = emptyList(),
    /** Sessions per charging type, in enum order. */
    val byType: Map<ChargingType, Int> = emptyMap(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val vehicleFilter = MutableStateFlow<Long?>(null)

    val state: StateFlow<StatsUi> = combine(
        sessionRepository.observeAll(),
        vehicleRepository.observeAll(),
        vehicleFilter,
    ) { allSessions, vehicles, filter ->
        val effectiveFilter = filter?.takeIf { id -> vehicles.any { it.id == id } }
        val sessions = if (effectiveFilter == null) allSessions
        else allSessions.filter { it.vehicleId == effectiveFilter }

        StatsUi(
            isLoading = false,
            vehicles = vehicles,
            vehicleFilterId = effectiveFilter,
            sessionCount = sessions.size,
            totalCost = sessions.sumOf { it.totalCost ?: 0.0 },
            totalEnergyKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
            avgEffPricePerKwh = computeAvgEffPrice(sessions),
            avgPowerKw = computeAvgPower(sessions),
            monthlyCost = monthlySeries(sessions) { it.totalCost ?: 0.0 },
            monthlyEnergy = monthlySeries(sessions) { it.energyKwh ?: 0.0 },
            byBrandCost = brandCostSeries(sessions),
            byType = sessions.groupingBy { it.chargingType }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun setVehicleFilter(id: Long?) { vehicleFilter.value = id }

    private fun computeAvgEffPrice(sessions: List<ChargingSession>): Double? {
        val totalCost = sessions.sumOf { it.totalCost ?: 0.0 }
        val totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 }
        return if (totalKwh > 0) totalCost / totalKwh else null
    }

    private fun computeAvgPower(sessions: List<ChargingSession>): Double? {
        val totalKwh = sessions.sumOf { it.energyKwh ?: 0.0 }
        val totalHours = sessions.sumOf { (it.durationSeconds ?: 0L) / 3600.0 }
        return if (totalHours > 0) totalKwh / totalHours else null
    }

    private fun monthlySeries(
        sessions: List<ChargingSession>,
        valueOf: (ChargingSession) -> Double,
    ): List<Pair<String, Double>> {
        if (sessions.isEmpty()) return emptyList()
        val labelFmt = SimpleDateFormat("MMM yy", Locale.getDefault())
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)

        // Build last-12-months window from "now".
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val months = (0 until 12).map {
            val date = cal.time
            cal.add(Calendar.MONTH, -1)
            date
        }.reversed()

        val totalsByKey = sessions.groupBy { keyFmt.format(Date(it.sessionStart)) }
            .mapValues { (_, ss) -> ss.sumOf(valueOf) }

        return months.map { date ->
            val key = keyFmt.format(date)
            labelFmt.format(date) to (totalsByKey[key] ?: 0.0)
        }
    }

    private fun brandCostSeries(sessions: List<ChargingSession>): List<Pair<String, Double>> {
        return sessions
            .filter { !it.brand.isNullOrBlank() && (it.totalCost ?: 0.0) > 0 }
            .groupBy { it.brand!!.trim() }
            .mapValues { (_, ss) -> ss.sumOf { it.totalCost ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
            .take(8)
    }
}
