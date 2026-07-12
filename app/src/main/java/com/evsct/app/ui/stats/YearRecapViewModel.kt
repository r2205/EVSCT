package com.evsct.app.ui.stats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.R
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.OpFeedback
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.BrandSpend
import com.evsct.app.util.OdometerDistance
import com.evsct.app.util.StopKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.UUID
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

/** Stops visited across more than one trip render gray, matching the live
 *  map's "shared" pin. */
private const val MAP_SHARED_GRAY = "#757575"

/** Untripped stops use the report's EV green rather than the live map's red,
 *  so the recap map stays on-palette with the rest of the document. */
private const val MAP_UNTRIPPED_GREEN = "#2E7D32"

/** A distinct, located charging stop within the recap period, ready to plot
 *  as an SVG pin. Coordinates are the average of the visits that share the
 *  stop (mirrors the live map's [com.evsct.app.ui.map.MapStop]). [colorHex]
 *  is the trip color, the shared-stop gray, or the untripped green. */
data class RecapMapStop(
    val lat: Double,
    val lng: Double,
    val colorHex: String,
    val label: String,
    val visits: Int,
)

/** A trip's located visits in chronological order, for an SVG route line. */
data class RecapTripPath(
    val colorHex: String,
    /** (lat, lng) in chronological order; always 2+ points. */
    val points: List<Pair<Double, Double>>,
)

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
    /** Distinct located charging stops in the period, deduped + trip-colored
     *  like the live map. Empty when no in-year session has coordinates. */
    val mapStops: List<RecapMapStop> = emptyList(),
    /** One route line per trip with 2+ located in-year visits. */
    val mapTripPaths: List<RecapTripPath> = emptyList(),
    /** (label, colorHex) pairs for the map legend, in display order. */
    val mapLegend: List<Pair<String, String>> = emptyList(),
    /** Display name of the vehicle the recap is scoped to, or null when
     *  scoped to all vehicles. Used to suffix the exported PDF filename
     *  so multi-vehicle users don't get N identical "evsct-recap-2024.pdf"
     *  files in their downloads. */
    val vehicleName: String? = null,
    /** PDF that was just written to cacheDir and is ready to be shared.
     *  Cleared by [consumePendingShare] after the chooser fires. */
    val pendingShareFile: File? = null,
    val feedback: OpFeedback? = null,
    /** Which export is running, so the spinner shows on the button that
     *  was tapped instead of a bar at the top of the scroll. */
    val busyOp: RecapOp? = null,
) {
    val busy: Boolean get() = busyOp != null
}

enum class RecapOp { SAVE_PDF, SHARE_PDF, SAVE_HTML, SHARE_HTML }

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
        c.copy(busyOp = t.busyOp, feedback = t.feedback, pendingShareFile = t.pendingShareFile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearRecapUi(isLoading = true))

    fun setYear(year: Int) { selectedYear.value = year }

    /** Parsed once and reused: the bundled North America outline drawn behind
     *  the recap map pins. Derived from Natural Earth 1:50m admin-1 data
     *  (public domain), simplified and trimmed to US states + CA provinces. A
     *  missing or unparseable asset yields an empty basemap and the renderer
     *  simply draws pins with no borders. */
    @Volatile private var cachedBasemap: NaBasemap? = null

    private fun loadBasemap(): NaBasemap {
        cachedBasemap?.let { return it }
        val text = runCatching {
            context.resources.openRawResource(R.raw.na_basemap)
                .bufferedReader().use { it.readText() }
        }.getOrNull()
        return (text?.let { parseNaBasemap(it) } ?: NaBasemap(emptyList())).also { cachedBasemap = it }
    }

    /** The bundled EVSCT lockup (logo + wordmark), inlined into the HTML
     *  report header as raw SVG. Null if the asset can't be read, in which
     *  case the report falls back to a plain text heading. */
    @Volatile private var cachedLogo: String? = null
    @Volatile private var logoLoaded = false

    private fun loadLogo(): String? {
        if (logoLoaded) return cachedLogo
        cachedLogo = runCatching {
            context.resources.openRawResource(R.raw.evsct_lockup)
                .bufferedReader().use { it.readText() }
        }.getOrNull()
        logoLoaded = true
        return cachedLogo
    }

    /** Render into a cacheDir staging file, then copy the bytes to [uri].
     *  Mirrors BackupIo.export: a render failure aborts before the SAF
     *  stream is opened (which "wt"-truncates immediately), so a recap
     *  file the user is overwriting survives it. Only the final byte-copy
     *  can truncate the destination (SAF has no atomic replace), and that
     *  path says so in its error message. */
    private fun stagedWriteTo(uri: Uri, ext: String, render: (OutputStream) -> Unit) {
        val staging = File(context.cacheDir, "recap-staging-${UUID.randomUUID()}.$ext")
        try {
            staging.outputStream().use(render)
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open output for writing.")
            try {
                out.use { output -> staging.inputStream().use { it.copyTo(output) } }
            } catch (e: Exception) {
                throw IOException(
                    "Could not write to the chosen location — the destination file may " +
                        "be incomplete. Save again before relying on it." +
                        (e.message?.let { " ($it)" } ?: ""),
                    e,
                )
            }
        } finally {
            staging.delete()
        }
    }

    /** Save the recap to a SAF-picked Uri. The screen wires a CreateDocument
     *  launcher; this method runs the actual write. */
    fun saveAsPdf(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = RecapOp.SAVE_PDF, feedback = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val ui = computed.value
                val units = appPreferences.userUnits.first()
                stagedWriteTo(uri, "pdf") { writeYearRecapPdf(it, ui, units) }
            }
        }.onSuccess {
            finish(OpFeedback("Recap saved", "PDF recap saved."))
        }.onFailure { e ->
            finish(saveFailure(e))
        }
    }

    private fun finish(feedback: OpFeedback?) =
        transient.update { it.copy(busyOp = null, feedback = feedback) }

    /** stagedWriteTo's own messages are already user-facing; wrap anything
     *  else in a plain sentence with the raw detail in parentheses. */
    private fun saveFailure(e: Throwable) = OpFeedback(
        title = "Save failed",
        body = e.message ?: "The recap couldn't be written.",
        isError = true,
    )

    /** Build the recap PDF in a private cache subdir and post the file to
     *  the screen for an ACTION_SEND chooser dispatch. */
    fun shareAsPdf() = viewModelScope.launch {
        transient.update { it.copy(busyOp = RecapOp.SHARE_PDF, feedback = null) }
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
            transient.update { it.copy(busyOp = null, pendingShareFile = file) }
        }.onFailure { e ->
            finish(shareFailure(e))
        }
    }

    private fun shareFailure(e: Throwable) = OpFeedback(
        title = "Share failed",
        body = "The recap couldn't be prepared for sharing." +
            (e.message?.let { " ($it)" } ?: ""),
        isError = true,
    )

    /** Save the recap as a self-contained HTML file to a SAF-picked Uri.
     *  Twin of [saveAsPdf]; the screen wires a separate CreateDocument
     *  launcher for the text/html mime type. */
    fun saveAsHtml(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = RecapOp.SAVE_HTML, feedback = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val ui = computed.value
                val units = appPreferences.userUnits.first()
                val basemap = loadBasemap()
                val logo = loadLogo()
                stagedWriteTo(uri, "html") { writeYearRecapHtml(it, ui, units, basemap, logo) }
            }
        }.onSuccess {
            finish(OpFeedback("Recap saved", "HTML recap saved."))
        }.onFailure { e ->
            finish(saveFailure(e))
        }
    }

    /** Build the recap HTML in the shared recap-share cache subdir and post
     *  the file for an ACTION_SEND chooser dispatch. Twin of [shareAsPdf];
     *  the share dir is cleared on every prepare so the two formats never
     *  leave a stale file behind. */
    fun shareAsHtml() = viewModelScope.launch {
        transient.update { it.copy(busyOp = RecapOp.SHARE_HTML, feedback = null) }
        runCatching {
            withContext(Dispatchers.IO) {
                val ui = computed.value
                val units = appPreferences.userUnits.first()
                val shareDir = File(context.cacheDir, "recap-share").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val target = File(shareDir, defaultRecapFilename(ui.selectedYear, ui.vehicleName, "html"))
                val basemap = loadBasemap()
                val logo = loadLogo()
                target.outputStream().use { writeYearRecapHtml(it, ui, units, basemap, logo) }
                target
            }
        }.onSuccess { file ->
            transient.update { it.copy(busyOp = null, pendingShareFile = file) }
        }.onFailure { e ->
            finish(shareFailure(e))
        }
    }

    fun consumePendingShare() = transient.update { it.copy(pendingShareFile = null) }
    fun clearFeedback() = transient.update { it.copy(feedback = null) }

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
        val odoDistance = OdometerDistance.inWindow(sessions, yearStart, yearEnd)
        val totalDistance = if (odoDistance > 1.0) odoDistance else totalKwh * FALLBACK_KM_PER_KWH

        val topBrands = BrandSpend.top(costSessions)

        val monthlyCost = monthlySeries(costSessions, effectiveYear) { it.totalCost ?: 0.0 }
        val monthlyKwh = monthlySeries(inYear, effectiveYear) { it.energyKwh ?: 0.0 }
        val longest = longestTripIn(trips, effectiveYear, sessions)
        val map = recapMapData(inYear, trips)

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
            mapStops = map.stops,
            mapTripPaths = map.paths,
            mapLegend = map.legend,
        )
    }

    /** Trip color, the shared-stop gray, or the untripped green. */
    private fun tripColorHex(pinColor: String?): String =
        com.evsct.app.ui.map.TripPinColor.fromKey(pinColor)?.hex ?: MAP_SHARED_GRAY

    private data class RecapMap(
        val stops: List<RecapMapStop>,
        val paths: List<RecapTripPath>,
        val legend: List<Pair<String, String>>,
    )

    /**
     * Build the recap map: distinct located stops (deduped by brand+address+
     * city like the live map), each colored by its trip, plus a route line per
     * trip with 2+ located visits. Mirrors [com.evsct.app.ui.map.MapViewModel]'s
     * stop logic but scoped to the recap period and flattened for SVG output.
     */
    private fun recapMapData(
        inYear: List<ChargingSession>,
        trips: List<TripWithStats>,
    ): RecapMap {
        val located = inYear.filter { it.latitude != null && it.longitude != null }
        if (located.isEmpty()) return RecapMap(emptyList(), emptyList(), emptyList())

        val tripById = trips.associateBy { it.trip.id }
        // Every session here has coordinates, so StopKey never blank-keys:
        // sessions without brand/address/city fall back to a geo bucket
        // instead of silently vanishing from the recap map.
        val groups = located.groupBy(StopKey::of).filterKeys { it.isNotBlank() }

        // Track which trip buckets actually appear on the map so the legend
        // lists only relevant entries.
        var anyUntripped = false
        var anyShared = false
        val legendTrips = linkedMapOf<Long, Pair<String, String>>()  // id -> (name, hex)

        val stops = groups.mapNotNull { (_, group) ->
            val avgLat = group.mapNotNull { it.latitude }.average()
            val avgLng = group.mapNotNull { it.longitude }.average()
            val tripIds = group.map { it.tripId }.distinct()
            val colorHex = when {
                tripIds.size == 1 && tripIds.single() == null -> { anyUntripped = true; MAP_UNTRIPPED_GREEN }
                tripIds.size == 1 -> {
                    val id = tripIds.single()!!
                    val t = tripById[id]
                    val hex = tripColorHex(t?.trip?.pinColor)
                    if (t != null) legendTrips[id] = t.trip.name to hex
                    hex
                }
                else -> { anyShared = true; MAP_SHARED_GRAY }
            }
            val newest = group.maxByOrNull { it.sessionStart } ?: return@mapNotNull null
            val label = listOfNotNull(
                newest.brand?.takeIf { it.isNotBlank() },
                newest.locationCity?.takeIf { it.isNotBlank() },
            ).joinToString(", ").ifBlank { "Charging stop" }
            RecapMapStop(avgLat, avgLng, colorHex, label, group.size)
        }

        val paths = trips.mapNotNull { t ->
            val pts = located.asSequence()
                .filter { it.tripId == t.trip.id }
                .sortedBy { it.sessionStart }
                .map { it.latitude!! to it.longitude!! }
                .toList()
            if (pts.size < 2) null else RecapTripPath(tripColorHex(t.trip.pinColor), pts)
        }

        val legend = buildList {
            addAll(legendTrips.values.sortedBy { it.first.lowercase() })
            if (anyShared) add("Multiple trips" to MAP_SHARED_GRAY)
            if (anyUntripped) add("Untripped" to MAP_UNTRIPPED_GREEN)
        }

        return RecapMap(stops, paths, legend)
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
