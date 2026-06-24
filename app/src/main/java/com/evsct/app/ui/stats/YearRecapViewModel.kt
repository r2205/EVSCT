package com.evsct.app.ui.stats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DateFormatSymbols
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** EV efficiency used to fall back to a kWh-derived distance when odometer
 *  data is too sparse to compute year totals. Mirrors the gas-savings card
 *  in [StatsViewModel] so both surfaces tell the same story. */
private const val FALLBACK_KM_PER_KWH = 4.0

data class LongestTripSummary(
    val name: String,
    val distanceKm: Double,
    val sessionCount: Int,
    /** Single-currency total when the trip's sessions all share one
     *  currency, else null — matches [TripWithStats.totalCostByCurrency]
     *  semantics. */
    val totalCost: Double?,
    val currency: String?,
)

data class YearRecapUi(
    val isLoading: Boolean = true,
    /** Years the user has *any* charging sessions in, descending. Drives
     *  the segmented year picker at the top of the screen. */
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val sessionCount: Int = 0,
    /** Display currency for cost totals — the user's default. Sessions in
     *  other currencies are excluded from cost (but counted everywhere
     *  else); [excludedByCurrency] reports how many. */
    val costCurrency: String = "CAD",
    val totalCost: Double = 0.0,
    val excludedByCurrency: Int = 0,
    val totalKwh: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    /** Top brands by spend (in [costCurrency]), descending, capped at 8. */
    val topBrands: List<Pair<String, Double>> = emptyList(),
    /** 12 entries Jan→Dec; (label, $ in [costCurrency]). */
    val monthlyCost: List<Pair<String, Double>> = emptyMonthly(),
    /** 12 entries Jan→Dec; (label, kWh). */
    val monthlyKwh: List<Pair<String, Double>> = emptyMonthly(),
    /** Trip with the highest distance among trips with at least one
     *  session in [selectedYear]. Whole-trip distance, not in-year only. */
    val longestTrip: LongestTripSummary? = null,
    /** Display name of the vehicle the recap is scoped to, or null when
     *  scoped to all vehicles. Used to suffix the exported PDF filename
     *  so multi-vehicle users don't get N identical "evsct-recap-2024.pdf"
     *  files in their downloads. */
    val vehicleName: String? = null,
    /** PDF that was just written to cacheDir and is ready to be shared.
     *  Cleared by [consumePendingShare] after the chooser fires. */
    val pendingShareFile: File? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

private fun emptyMonthly(): List<Pair<String, Double>> {
    val labels = DateFormatSymbols.getInstance().shortMonths.take(12)
    return labels.map { it to 0.0 }
}

/** Default filename for a recap export. Includes a slugified vehicle name
 *  when the recap is scoped to a single vehicle so multi-vehicle users get
 *  distinct files. [ext] is the extension without a dot ("pdf", "html"). */
internal fun defaultRecapFilename(year: Int, vehicleName: String?, ext: String = "pdf"): String {
    if (vehicleName.isNullOrBlank()) return "evsct-recap-$year.$ext"
    val slug = vehicleName
        .replace(Regex("[^A-Za-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "vehicle" }
    return "evsct-recap-$year-$slug.$ext"
}

@HiltViewModel
class YearRecapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    sessionRepository: SessionRepository,
    tripRepository: TripRepository,
    vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Vehicle scope captured at navigation time. Null = "All vehicles".
     *  -1L is the sentinel the route builder uses for "no filter" so the
     *  arg can stay typed as a primitive Long. */
    private val vehicleFilterId: Long? =
        savedStateHandle.get<Long>(Routes.YEAR_RECAP_VEHICLE_ARG)?.takeIf { it >= 0 }

    private val selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val transient = MutableStateFlow(YearRecapUi(isLoading = true))

    private val computed: StateFlow<YearRecapUi> = combine(
        sessionRepository.observeAll(),
        tripRepository.observeAllWithStats(),
        vehicleRepository.observeAll(),
        selectedYear,
        appPreferences.userUnits,
    ) { sessions, trips, vehicles, year, units ->
        // Apply the vehicle scope before any year-bucketing — the recap is
        // a snapshot of one vehicle (or all) for the chosen year, including
        // its available-years list.
        val scoped = if (vehicleFilterId == null) sessions
        else sessions.filter { it.vehicleId == vehicleFilterId }
        val vehicleName = vehicleFilterId?.let { id -> vehicles.firstOrNull { it.id == id }?.name }
        recapFor(scoped, trips, year, units).copy(vehicleName = vehicleName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearRecapUi(isLoading = true))

    /** Final state merges the computed snapshot with transient flags
     *  (busy / message / pendingShareFile) the screen toggles via the VM
     *  methods below. */
    val state: StateFlow<YearRecapUi> = combine(computed, transient) { c, t ->
        c.copy(busy = t.busy, message = t.message, pendingShareFile = t.pendingShareFile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearRecapUi(isLoading = true))

    fun setYear(year: Int) { selectedYear.value = year }

    /** Save the recap to a SAF-picked Uri. The screen wires a CreateDocument
     *  launcher; this method runs the actual write. */
    fun saveAsPdf(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Could not open output for writing.")
                out.use { writeYearRecapPdf(it, computed.value, appPreferences.userUnits.first()) }
            }
        }.onSuccess {
            transient.update { it.copy(busy = false, message = "Recap saved.") }
        }.onFailure { e ->
            transient.update { it.copy(busy = false, message = "Save failed: ${e.message}") }
        }
    }

    /** Build the recap PDF in a private cache subdir and post the file to
     *  the screen for an ACTION_SEND chooser dispatch. */
    fun shareAsPdf() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val ui = computed.value
                val units = appPreferences.userUnits.first()
                val shareDir = File(context.cacheDir, "recap-share").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val target = File(shareDir, defaultRecapFilename(ui.selectedYear, ui.vehicleName))
                target.outputStream().use { writeYearRecapPdf(it, ui, units) }
                target
            }
        }.onSuccess { file ->
            transient.update { it.copy(busy = false, pendingShareFile = file) }
        }.onFailure { e ->
            transient.update { it.copy(busy = false, message = "Share failed: ${e.message}") }
        }
    }

    /** Save the recap as a self-contained HTML file to a SAF-picked Uri.
     *  Twin of [saveAsPdf]; the screen wires a separate CreateDocument
     *  launcher for the text/html mime type. */
    fun saveAsHtml(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Could not open output for writing.")
                out.use { writeYearRecapHtml(it, computed.value, appPreferences.userUnits.first()) }
            }
        }.onSuccess {
            transient.update { it.copy(busy = false, message = "Recap saved.") }
        }.onFailure { e ->
            transient.update { it.copy(busy = false, message = "Save failed: ${e.message}") }
        }
    }

    /** Build the recap HTML in the shared recap-share cache subdir and post
     *  the file for an ACTION_SEND chooser dispatch. Twin of [shareAsPdf];
     *  the share dir is cleared on every prepare so the two formats never
     *  leave a stale file behind. */
    fun shareAsHtml() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val ui = computed.value
                val units = appPreferences.userUnits.first()
                val shareDir = File(context.cacheDir, "recap-share").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val target = File(shareDir, defaultRecapFilename(ui.selectedYear, ui.vehicleName, "html"))
                target.outputStream().use { writeYearRecapHtml(it, ui, units) }
                target
            }
        }.onSuccess { file ->
            transient.update { it.copy(busy = false, pendingShareFile = file) }
        }.onFailure { e ->
            transient.update { it.copy(busy = false, message = "Share failed: ${e.message}") }
        }
    }

    fun consumePendingShare() = transient.update { it.copy(pendingShareFile = null) }
    fun clearMessage() = transient.update { it.copy(message = null) }

    /* --- computation --- */

    private fun recapFor(
        sessions: List<ChargingSession>,
        trips: List<TripWithStats>,
        year: Int,
        units: UserUnits,
    ): YearRecapUi {
        val years = sessions.map { yearOf(it.sessionStart) }.distinct().sortedDescending()
        // Coerce the selection: if the user picked a year that no longer
        // has data (rare, e.g. they deleted everything for that year), fall
        // back to the most recent year that does.
        val effectiveYear = if (year in years) year else years.firstOrNull() ?: year

        val (yearStart, yearEnd) = yearBounds(effectiveYear)
        val inYear = sessions.filter { it.sessionStart in yearStart until yearEnd }
        val costCurrency = units.defaultCurrency
        val costSessions = inYear.filter { it.currency == costCurrency }
        val excluded = inYear.count {
            (it.totalCost ?: 0.0) != 0.0 && it.currency != costCurrency
        }

        val totalCost = costSessions.sumOf { it.totalCost ?: 0.0 }
        val totalKwh = inYear.sumOf { it.energyKwh ?: 0.0 }
        val odoDistance = odometerDistanceForRange(sessions, yearStart, yearEnd)
        val totalDistance = if (odoDistance > 1.0) odoDistance else totalKwh * FALLBACK_KM_PER_KWH

        val topBrands = costSessions
            .filter { !it.brand.isNullOrBlank() && (it.totalCost ?: 0.0) > 0 }
            .groupBy { it.brand!!.trim() }
            .mapValues { (_, ss) -> ss.sumOf { it.totalCost ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
            .take(8)

        val monthlyCost = monthlySeries(costSessions, effectiveYear) { it.totalCost ?: 0.0 }
        val monthlyKwh = monthlySeries(inYear, effectiveYear) { it.energyKwh ?: 0.0 }
        val longest = longestTripIn(trips, effectiveYear, sessions)

        return YearRecapUi(
            isLoading = false,
            availableYears = years,
            selectedYear = effectiveYear,
            sessionCount = inYear.size,
            costCurrency = costCurrency,
            totalCost = totalCost,
            excludedByCurrency = excluded,
            totalKwh = totalKwh,
            totalDistanceKm = totalDistance,
            topBrands = topBrands,
            monthlyCost = monthlyCost,
            monthlyKwh = monthlyKwh,
            longestTrip = longest,
        )
    }

    private fun monthlySeries(
        sessions: List<ChargingSession>,
        year: Int,
        valueOf: (ChargingSession) -> Double,
    ): List<Pair<String, Double>> {
        val labels = DateFormatSymbols.getInstance().shortMonths.take(12)
        val totals = DoubleArray(12)
        val cal = Calendar.getInstance()
        sessions.forEach { s ->
            cal.timeInMillis = s.sessionStart
            if (cal.get(Calendar.YEAR) != year) return@forEach
            totals[cal.get(Calendar.MONTH)] += valueOf(s)
        }
        return labels.mapIndexed { i, label -> label to totals[i] }
    }

    /** Per-vehicle sum of odometer deltas where the *end* session lies in
     *  [yearStart, yearEnd). Walks the whole session list so the boundary
     *  delta from the year before counts. */
    private fun odometerDistanceForRange(
        sessions: List<ChargingSession>,
        yearStart: Long,
        yearEnd: Long,
    ): Double {
        var total = 0.0
        sessions.groupBy { it.vehicleId }
            .values
            .map { group -> group.sortedBy { it.sessionStart } }
            .forEach { sorted ->
                for (i in 1 until sorted.size) {
                    val prev = sorted[i - 1]
                    val curr = sorted[i]
                    if (curr.sessionStart < yearStart || curr.sessionStart >= yearEnd) continue
                    val prevOdo = prev.odometerKm ?: continue
                    val currOdo = curr.odometerKm ?: continue
                    val delta = currOdo - prevOdo
                    if (delta > 0) total += delta
                }
            }
        return total
    }

    private fun longestTripIn(
        trips: List<TripWithStats>,
        year: Int,
        allSessions: List<ChargingSession>,
    ): LongestTripSummary? {
        val tripIdsInYear = allSessions
            .filter { yearOf(it.sessionStart) == year }
            .mapNotNull { it.tripId }
            .toSet()
        if (tripIdsInYear.isEmpty()) return null
        val candidate = trips
            .filter { it.trip.id in tripIdsInYear && it.totalDistanceKm > 0 }
            .maxByOrNull { it.totalDistanceKm } ?: return null
        return LongestTripSummary(
            name = candidate.trip.name,
            distanceKm = candidate.totalDistanceKm,
            sessionCount = candidate.sessionCount,
            totalCost = candidate.totalCostByCurrency.singleTotal,
            currency = candidate.totalCostByCurrency.singleCurrency,
        )
    }

    private fun yearOf(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)

    /** Epoch millis for the calendar year [start, end). */
    private fun yearBounds(year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        return start to cal.timeInMillis
    }
}
