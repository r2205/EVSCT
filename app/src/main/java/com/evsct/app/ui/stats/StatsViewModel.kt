package com.evsct.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.VehicleScope
import com.evsct.app.ui.orAllIfEmpty
import com.evsct.app.util.BrandSpend
import com.evsct.app.util.CurrencyTotals
import com.evsct.app.util.OdometerDistance
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

/** Page-level time window: scopes every stat on the screen except the
 *  this-month "vs gas" card, which is pinned to the current calendar
 *  month by definition. Doubles as the trend charts' bucketing choice —
 *  monthly buckets for the 12-month window, yearly buckets for all time. */
enum class StatsChartWindow(val label: String) {
    LAST_12_MONTHS("Last 12 months"),
    ALL_TIME("All time"),
}

data class StatsUi(
    val isLoading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleScope: VehicleScope = VehicleScope.All,
    /** True when any session at all lacks a vehicle. Computed off the
     *  unfiltered set so selecting a vehicle can't make the Unassigned
     *  tab disappear from under the user. */
    val hasUnassignedSessions: Boolean = false,
    /** True when the vehicle scope has any sessions at all, ignoring
     *  [chartWindow]. Gates the empty state: a scope whose data is all
     *  older than the selected window should show zeroed stats, not
     *  "No sessions yet". */
    val hasAnySessions: Boolean = false,
    /** Sessions inside [chartWindow] — like every count/total below. */
    val sessionCount: Int = 0,
    /** Chart cost aggregates are filtered to the user's default currency,
     *  since adding CAD + USD into one chart bar produces a meaningless
     *  number. The currency tag tells the UI what to label chart totals
     *  with. */
    val costCurrency: String = "CAD",
    /** Headline total across every currency the user has paid in — the
     *  shared MoneyStat stacks one line per currency, so the headline
     *  doesn't have to exclude anything. */
    val totalCostByCurrency: CurrencyTotals = CurrencyTotals(emptyMap()),
    /** How many sessions the single-currency cost charts (trend, top
     *  brands, averages) leave out because their currency didn't match
     *  [costCurrency]. UI flags this when > 0. */
    val excludedByCurrency: Int = 0,
    val totalEnergyKwh: Double = 0.0,
    val avgEffPricePerKwh: Double? = null,
    val avgPowerKw: Double? = null,
    /** Which window every stat above and below covers (the "vs gas" card
     *  excepted), and how the trend series are bucketed. */
    val chartWindow: StatsChartWindow = StatsChartWindow.LAST_12_MONTHS,
    /** Cost trend, oldest bucket first; (label, $ in [costCurrency]). One
     *  bucket per month or per year, per [chartWindow]. */
    val costSeries: List<Pair<String, Double>> = emptyList(),
    /** Energy trend, same bucketing; (label, kWh) — across all currencies. */
    val energySeries: List<Pair<String, Double>> = emptyList(),
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
    /** Charging cost for sessions started in the current calendar month,
     *  in [costCurrency]. */
    val thisMonthCost: Double = 0.0,
    /** Estimated km driven during the current calendar month — odometer
     *  deltas where available, else falls back to kWh × an EV efficiency
     *  constant. */
    val thisMonthDistanceKm: Double = 0.0,
    /** What [thisMonthDistanceKm] of driving would have cost in gas, in
     *  [costCurrency], using the hardcoded gas-price + L/100 km constants. */
    val thisMonthGasCost: Double = 0.0,
    /** [thisMonthGasCost] − [thisMonthCost]. Positive = saved by driving
     *  electric; negative = the rare case where charging cost more. */
    val thisMonthSavings: Double = 0.0,
    /** Whether we have enough this-month data (any driving distance) to
     *  show the gas-comparison card. The screen uses this to hide the
     *  card on cold-start months entirely. */
    val thisMonthHasDriving: Boolean = false,
)

private fun emptyDayHourGrid(): List<List<Int>> = List(7) { List(24) { 0 } }

/* --- Hardcoded gas-equivalence constants for the "vs gas" card. Currency
 *     here is implicitly the user's default (no FX); these defaults assume
 *     CAD pump prices in BC. Earmarked to be promoted to user-settable
 *     preferences in a follow-up. */
private const val GAS_PRICE_PER_L = 2.15
private const val GAS_CONSUMPTION_L_PER_100KM = 12.0
private const val EV_EFFICIENCY_KM_PER_KWH = 4.0

/** Most year buckets the "All time" chart window will render. */
private const val MAX_YEAR_BUCKETS = 20

@HiltViewModel
class StatsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    vehicleRepository: VehicleRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    private val vehicleScopeFlow = MutableStateFlow<VehicleScope>(VehicleScope.All)
    private val chartWindow = MutableStateFlow(StatsChartWindow.LAST_12_MONTHS)

    val state: StateFlow<StatsUi> = combine(
        sessionRepository.observeAll(),
        vehicleRepository.observeAll(),
        vehicleScopeFlow,
        chartWindow,
        appPreferences.userUnits,
    ) { allSessions, vehicles, filter, window, units ->
        val hasUnassigned = allSessions.any { it.vehicleId == null }
        val effectiveScope = filter.orAllIfEmpty(vehicles, hasUnassigned)
        val scoped = allSessions.filter { effectiveScope.matches(it) }

        // Page-level window: every aggregate below sees only the windowed
        // sessions. The one exception is the this-month gas card at the
        // bottom, which gets the unwindowed [scoped] lists — its window is
        // the current calendar month regardless of the toggle.
        val sessions = when (window) {
            StatsChartWindow.LAST_12_MONTHS -> {
                val start = last12MonthsStart()
                scoped.filter { it.sessionStart >= start }
            }
            StatsChartWindow.ALL_TIME -> scoped
        }

        // Cost aggregates only see sessions in the user's default currency.
        // Energy/duration/count aggregates stay across all currencies.
        val costCurrency = units.defaultCurrency
        val costSessions = sessions.filter { it.currency == costCurrency }
        val excluded = sessions.count { (it.totalCost ?: 0.0) != 0.0 && it.currency != costCurrency }

        StatsUi(
            isLoading = false,
            vehicles = vehicles,
            vehicleScope = effectiveScope,
            hasUnassignedSessions = hasUnassigned,
            hasAnySessions = scoped.isNotEmpty(),
            sessionCount = sessions.size,
            costCurrency = costCurrency,
            totalCostByCurrency = CurrencyTotals.from(sessions),
            excludedByCurrency = excluded,
            totalEnergyKwh = sessions.sumOf { it.energyKwh ?: 0.0 },
            avgEffPricePerKwh = computeAvgEffPrice(costSessions),
            avgPowerKw = computeAvgPower(sessions),
            chartWindow = window,
            costSeries = when (window) {
                StatsChartWindow.LAST_12_MONTHS -> monthlySeries(costSessions) { it.totalCost ?: 0.0 }
                StatsChartWindow.ALL_TIME -> yearlySeries(costSessions) { it.totalCost ?: 0.0 }
            },
            energySeries = when (window) {
                StatsChartWindow.LAST_12_MONTHS -> monthlySeries(sessions) { it.energyKwh ?: 0.0 }
                StatsChartWindow.ALL_TIME -> yearlySeries(sessions) { it.energyKwh ?: 0.0 }
            },
            byBrandCost = BrandSpend.top(costSessions),
            byType = sessions.groupingBy { it.chargingType }.eachCount(),
            dcFastByDayHour = dayHourGrid(sessions.filter { it.chargingType == ChargingType.DC_FAST }),
            acByDayHour = dayHourGrid(sessions.filter {
                it.chargingType == ChargingType.AC_L2 || it.chargingType == ChargingType.AC_L1
            }),
        ).withGasComparison(scoped, scoped.filter { it.currency == costCurrency })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun setVehicleScope(scope: VehicleScope) { vehicleScopeFlow.value = scope }

    fun setChartWindow(window: StatsChartWindow) { chartWindow.value = window }

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

    /** Epoch millis of the first day of the month 11 months back — the
     *  oldest bucket [monthlySeries] renders, so the page-level filter and
     *  the monthly chart cover exactly the same sessions. */
    private fun last12MonthsStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -11)
        }.timeInMillis
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

    /** One bucket per calendar year for the "All time" window, first data
     *  year through the current year, zero-filled and oldest first. Capped
     *  at [MAX_YEAR_BUCKETS] most recent years so a single typo'd 1970
     *  session can't explode the chart into decades of empty rows. */
    private fun yearlySeries(
        sessions: List<ChargingSession>,
        valueOf: (ChargingSession) -> Double,
    ): List<Pair<String, Double>> {
        if (sessions.isEmpty()) return emptyList()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val totalsByYear = sessions.groupBy { s ->
            cal.timeInMillis = s.sessionStart
            cal.get(Calendar.YEAR)
        }.mapValues { (_, ss) -> ss.sumOf(valueOf) }
        val lastYear = maxOf(totalsByYear.keys.max(), currentYear)
        val firstYear = maxOf(totalsByYear.keys.min(), lastYear - (MAX_YEAR_BUCKETS - 1))
        return (firstYear..lastYear).map { y -> y.toString() to (totalsByYear[y] ?: 0.0) }
    }

    /**
     * Compute the gas-equivalent comparison for the current calendar month
     * and return a copy of [this] with the matching fields populated.
     *
     * Both lists arrive vehicle-scoped but NOT windowed by [chartWindow]:
     * this card is pinned to the current calendar month by definition, so
     * it must not move with the page-level window toggle.
     *
     * Distance preference:
     *   1. [OdometerDistance.inWindow] over ALL of the vehicle-scoped
     *      [sessions] — distance is a physical quantity with no currency,
     *      and walking a currency-filtered list both disagreed with the
     *      Year Recap and let deltas spanning a foreign-currency session
     *      skew the total.
     *   2. If no odometer data is usable, fall back to month-kWh ×
     *      [EV_EFFICIENCY_KM_PER_KWH] so the card still works for users who
     *      don't log odometer readings (also unfiltered — energy is
     *      physical too).
     *
     * Only the money stays scoped to [costSessions] (the user's default
     * currency): sums across currencies have no single unit. A month with
     * foreign-currency charging therefore shows its full driving but only
     * home-currency spend — same trade-off the Year Recap makes.
     */
    private fun StatsUi.withGasComparison(
        sessions: List<ChargingSession>,
        costSessions: List<ChargingSession>,
    ): StatsUi {
        val (monthStart, monthEnd) = currentMonthBounds()
        val monthCost = costSessions
            .filter { it.sessionStart in monthStart until monthEnd }
            .sumOf { it.totalCost ?: 0.0 }

        val odoDistance = OdometerDistance.inWindow(sessions, monthStart, monthEnd)
        val kwhDistance = sessions
            .filter { it.sessionStart in monthStart until monthEnd }
            .sumOf { it.energyKwh ?: 0.0 } * EV_EFFICIENCY_KM_PER_KWH
        val distance = if (odoDistance > 1.0) odoDistance else kwhDistance

        val gasCost = (distance / 100.0) * GAS_CONSUMPTION_L_PER_100KM * GAS_PRICE_PER_L
        return copy(
            thisMonthCost = monthCost,
            thisMonthDistanceKm = distance,
            thisMonthGasCost = gasCost,
            thisMonthSavings = gasCost - monthCost,
            thisMonthHasDriving = distance > 0.0,
        )
    }

    /** Epoch millis for the current calendar month: [start, end). */
    private fun currentMonthBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
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

}
