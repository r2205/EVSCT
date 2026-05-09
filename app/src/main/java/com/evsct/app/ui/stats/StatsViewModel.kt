package com.evsct.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
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
    /** Cost-based aggregates are filtered to the user's default currency,
     *  since adding CAD + USD into one chart bar produces a meaningless
     *  number. The currency tag tells the UI what to label totals with. */
    val costCurrency: String = "CAD",
    val totalCost: Double = 0.0,
    /** How many sessions were excluded from cost aggregates because their
     *  currency didn't match [costCurrency]. UI flags this when > 0. */
    val excludedByCurrency: Int = 0,
    val totalEnergyKwh: Double = 0.0,
    val avgEffPricePerKwh: Double? = null,
    val avgPowerKw: Double? = null,
    /** Last 12 months, oldest first; (label, $ spent in [costCurrency]). */
    val monthlyCost: List<Pair<String, Double>> = emptyList(),
    /** Last 12 months, oldest first; (label, kWh) — across all currencies. */
    val monthlyEnergy: List<Pair<String, Double>> = emptyList(),
    /** Top brands by $ spent in [costCurrency], descending. */
    val byBrandCost: List<Pair<String, Double>> = emptyList(),
    /** Sessions per charging type, in enum order. */
    val byType: Map<ChargingType, Int> = emptyMap(),
    /** 7×24 grid of DC Fast session counts, indexed [day][hour] where
     *  day 0 = Sunday and hour 0 = midnight. Surfaces road-trip / weekend
     *  patterns separately from the home/work AC grid. */
    val dcFastByDayHour: List<List<Int>> = emptyDayHourGrid(),
    /** 7×24 grid of AC (L2 + L1) session counts, indexed [day][hour].
     *  Surfaces overnight / commute patterns. */
    val acByDayHour: List<List<Int>> = emptyDayHourGrid(),
)

private fun emptyDayHourGrid(): List<List<Int>> = List(7) { List(24) { 0 } }

@HiltViewModel
class StatsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    vehicleRepository: VehicleRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    private val vehicleFilter = MutableStateFlow<Long?>(null)

    val state: StateFlow<StatsUi> = combine(
        sessionRepository.observeAll(),
        vehicleRepository.observeAll(),
        vehicleFilter,
        appPreferences.userUnits,
    ) { allSessions, vehicles, filter, units ->
        val effectiveFilter = filter?.takeIf { id -> vehicles.any { it.id == id } }
        val sessions = if (effectiveFilter == null) allSessions
        else allSessions.filter { it.vehicleId == effectiveFilter }

        // Cost aggregates only see sessions in the user's default currency.
        // Energy/duration/count aggregates stay across all sessions.
        val costCurrency = units.defaultCurrency
        val costSessions = sessions.filter { it.currency == costCurrency }
        val excluded = sessions.count { (it.totalCost ?: 0.0) != 0.0 && it.currency != costCurrency }

        StatsUi(
            isLoading = false,
            vehicles = vehicles,
            vehicleFilterId = effectiveFilter,
            sessionCount = sessions.size,
            costCurrency = costCurrency,
            totalCost = costSessions.sumOf { it.totalCost ?: 0.0 },
            excludedByCurrency = excluded,
            totalEnergyKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
            avgEffPricePerKwh = computeAvgEffPrice(costSessions),
            avgPowerKw = computeAvgPower(sessions),
            monthlyCost = monthlySeries(costSessions) { it.totalCost ?: 0.0 },
            monthlyEnergy = monthlySeries(sessions) { it.energyKwh ?: 0.0 },
            byBrandCost = brandCostSeries(costSessions),
            byType = sessions.groupingBy { it.chargingType }.eachCount(),
            dcFastByDayHour = dayHourGrid(sessions.filter { it.chargingType == ChargingType.DC_FAST }),
            acByDayHour = dayHourGrid(sessions.filter {
                it.chargingType == ChargingType.AC_L2 || it.chargingType == ChargingType.AC_L1
            }),
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

    /** Bucket [sessions] into a 7×24 grid by the local-time day-of-week +
     *  hour the session started. Calendar.DAY_OF_WEEK is 1 (Sun) – 7 (Sat),
     *  shifted to 0–6 so Sunday lines up with the top row of the heatmap. */
    private fun dayHourGrid(sessions: List<ChargingSession>): List<List<Int>> {
        if (sessions.isEmpty()) return emptyDayHourGrid()
        val cal = Calendar.getInstance()
        val grid = Array(7) { IntArray(24) }
        sessions.forEach { s ->
            cal.timeInMillis = s.sessionStart
            val day = cal.get(Calendar.DAY_OF_WEEK) - 1
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            grid[day][hour]++
        }
        return grid.map { it.toList() }
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
