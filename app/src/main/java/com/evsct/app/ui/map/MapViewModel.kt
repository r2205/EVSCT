package com.evsct.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.util.LocationAutofill
import com.evsct.app.util.StopKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A distinct charging stop aggregated across sessions that share an address. */
data class MapStop(
    val key: String,
    val brand: String?,
    val address: String?,
    val city: String?,
    val province: String?,
    val stationName: String?,
    val latitude: Double,
    val longitude: Double,
    val visits: Int,
    val lastVisit: Long,
    val sessionIds: List<Long>,
    val pinKind: PinKind,
)

sealed interface PinKind {
    /** Stop where every visible visit was un-tripped. */
    data object Untripped : PinKind

    /** Stop where every visible visit belongs to the same trip. */
    data class SingleTrip(val tripPinColorKey: String?) : PinKind

    /** Stop visited across two or more distinct trips (in the visible set). */
    data object Shared : PinKind
}

/**
 * Filter row shown in the map's filter sheet. Identifies a trip by id, or
 * the special "untripped" bucket when [tripId] is null.
 */
data class TripFilterOption(
    val tripId: Long?,
    val name: String,
    val pinColorKey: String?,
    val visible: Boolean,
)

/**
 * Chronologically-ordered list of (lat, lng) coordinates for a single trip,
 * used to render a colored polyline connecting the trip's charging stops.
 * Trips with fewer than two located sessions don't produce a polyline (one
 * point is not a line).
 */
data class TripPolyline(
    val tripId: Long,
    val pinColorKey: String?,
    val points: List<Pair<Double, Double>>,
)

data class MapFilters(
    /** Trip IDs (and null = "untripped") whose pins are hidden. Empty = show all. */
    val hiddenKeys: Set<Long?> = emptySet(),
    /** When non-null, only sessions tagged to this vehicle contribute to pins. */
    val vehicleFilter: Long? = null,
)

data class MapUi(
    /** True until the first database emission lands, so the screen doesn't
     *  flash "No locations to map yet" over a log that has plenty. */
    val isLoading: Boolean = true,
    val stops: List<MapStop> = emptyList(),
    val totalDistinct: Int = 0,
    val unlocatedDistinct: Int = 0,
    val backfillRunning: Boolean = false,
    val backfillCompleted: Boolean = false,
    val backfillFailed: Int = 0,
    val tripOptions: List<TripFilterOption> = emptyList(),
    val showUntrippedOption: Boolean = false,
    val untrippedVisible: Boolean = true,
    /** Vehicles known to the app. Empty / one-element means the vehicle
     *  picker has nothing useful to show and the filter sheet hides it. */
    val vehicles: List<Vehicle> = emptyList(),
    /** Currently selected vehicle, or null for "all vehicles". */
    val vehicleFilterId: Long? = null,
    val colorByTrip: Boolean = true,
    val anyFilterActive: Boolean = false,
    /** User's preferred Google Maps base layer (NORMAL / SATELLITE / HYBRID
     *  / TERRAIN). The screen converts this to the Maps Compose enum. */
    val mapType: String = "NORMAL",
    /** When false, every pin renders individually regardless of zoom. */
    val clusteringEnabled: Boolean = true,
    /** When true, the map renders a visit-weighted density heatmap and
     *  suppresses individual pins. Toggled from the layers menu. */
    val heatmapEnabled: Boolean = false,
    /** When true, draw a colored polyline per visible trip connecting that
     *  trip's located sessions in chronological order. */
    val polylinesEnabled: Boolean = false,
    /** One entry per visible trip with two-plus located visits, in the same
     *  order the screen should render them. Empty when polylines are off. */
    val tripPolylines: List<TripPolyline> = emptyList(),
    /** All trip names keyed by id, so the session-picker sheet can label
     *  each session with its trip without re-querying the repo. */
    val tripNamesById: Map<Long, String> = emptyMap(),
    /** Stop the user tapped on, or null when the picker isn't open.
     *  Drives the bottom sheet that lists the sessions sharing that pin. */
    val selectedStop: MapStop? = null,
    /** Sessions belonging to [selectedStop], newest first. Empty list when
     *  no stop is selected. */
    val selectedStopSessions: List<ChargingSession> = emptyList(),
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val locationAutofill: LocationAutofill,
    private val appPreferences: com.evsct.app.data.prefs.AppPreferences,
) : ViewModel() {

    private val backfillStatus = MutableStateFlow(BackfillState())
    private var backfillInFlight = false
    private val filters = MutableStateFlow(MapFilters())
    private val selectedStop = MutableStateFlow<MapStop?>(null)

    /** Bundle filters + the picker selection into a single stream so the
     *  outer state combine still fits in 5 args. */
    private val filtersAndSelection = combine(filters, selectedStop) { f, sel -> f to sel }

    // typed combine maxes out at 5 flows; collapse the map-display prefs
    // into a single MapPrefs stream so we still fit.
    private val mapPrefs = combine(
        appPreferences.mapType,
        appPreferences.mapClusteringEnabled,
        appPreferences.mapHeatmapEnabled,
        appPreferences.mapPolylinesEnabled,
        appPreferences.mapColorByTrip,
    ) { type, clustering, heatmap, polylines, colorByTrip ->
        MapPrefs(type, clustering, heatmap, polylines, colorByTrip)
    }

    // Pair trips + vehicles so the outer combine still fits in 5 args.
    private val tripsAndVehicles = combine(
        tripRepository.observeAll(),
        vehicleRepository.observeAll(),
    ) { trips, vehicles -> trips to vehicles }

    val state: StateFlow<MapUi> = combine(
        sessionRepository.observeAll(),
        tripsAndVehicles,
        backfillStatus,
        filtersAndSelection,
        mapPrefs,
    ) { allSessions, (trips, vehicles), status, fs, prefs ->
        val (f, currentSelectedStop) = fs
        val (mapType, clusteringEnabled, heatmapEnabled, polylinesEnabled, colorByTrip) = prefs

        // Drop a vehicle filter that points to a deleted vehicle so the
        // sheet doesn't keep advertising a phantom selection.
        val effectiveVehicleFilter = f.vehicleFilter?.takeIf { id -> vehicles.any { it.id == id } }

        // Apply the vehicle filter up front: every downstream calculation
        // (stops, trip options, untripped option) operates on the filtered set.
        val sessions = if (effectiveVehicleFilter == null) allSessions
            else allSessions.filter { it.vehicleId == effectiveVehicleFilter }

        val tripColorById = trips.associate { it.id to it.pinColor }
        // StopKey gives coordinate-only sessions (no brand/address/city) a
        // geo-bucket key, so they render as pins instead of vanishing; only
        // sessions with no text AND no coordinates blank-key out here.
        val groups = sessions.groupBy(StopKey::of).filterKeys { it.isNotBlank() }

        val stops = groups.mapNotNull { (key, group) -> buildStop(key, group, tripColorById, f) }
            .sortedByDescending { it.lastVisit }
        val unlocated = groups.values.count { group -> group.none { it.hasCoordinates() } }

        // Build the trip filter rows, sorted by name. Skip trips that don't have
        // any sessions (they'd be confusing to filter against).
        val tripsWithSessions = trips.filter { trip ->
            sessions.any { it.tripId == trip.id }
        }.sortedBy { it.name.lowercase() }
        val tripOptions = tripsWithSessions.map { trip ->
            TripFilterOption(
                tripId = trip.id,
                name = trip.name,
                pinColorKey = trip.pinColor,
                visible = trip.id !in f.hiddenKeys,
            )
        }
        val hasUntripped = sessions.any { it.tripId == null }
        val untrippedVisible = null !in f.hiddenKeys

        // Build one polyline per visible trip with 2+ located visits, in
        // chronological order. Hidden trips skip — their pins are gone, so
        // their route shouldn't draw either. Skipped entirely when polylines
        // are off so we don't pay the sort cost. Trips without a stored
        // pinColor still get an entry (the screen falls back to gray).
        val tripPolylines: List<TripPolyline> = if (!polylinesEnabled) emptyList()
            else tripsWithSessions
                .asSequence()
                .filter { it.id !in f.hiddenKeys }
                .mapNotNull { trip ->
                    val pts = sessions.asSequence()
                        .filter { it.tripId == trip.id && it.hasCoordinates() }
                        .sortedBy { it.sessionStart }
                        .map { it.latitude!! to it.longitude!! }
                        .toList()
                    if (pts.size < 2) null
                    else TripPolyline(trip.id, trip.pinColor, pts)
                }
                .toList()

        // Re-resolve the selected stop against the current visible set so the
        // picker auto-closes if a filter change makes its pin disappear.
        val resolvedSelectedStop = currentSelectedStop?.let { sel ->
            stops.firstOrNull { it.key == sel.key }
        }
        val pickerSessions = resolvedSelectedStop
            ?.let { stop -> allSessions.filter { it.id in stop.sessionIds } }
            ?.sortedByDescending { it.sessionStart }
            .orEmpty()

        MapUi(
            isLoading = false,
            stops = stops,
            totalDistinct = groups.size,
            unlocatedDistinct = unlocated,
            backfillRunning = status.running,
            backfillCompleted = status.completed,
            backfillFailed = status.failed,
            tripOptions = tripOptions,
            showUntrippedOption = hasUntripped,
            untrippedVisible = untrippedVisible,
            vehicles = vehicles,
            vehicleFilterId = effectiveVehicleFilter,
            colorByTrip = colorByTrip,
            anyFilterActive = f.hiddenKeys.isNotEmpty() ||
                effectiveVehicleFilter != null,
            mapType = mapType,
            clusteringEnabled = clusteringEnabled,
            heatmapEnabled = heatmapEnabled,
            polylinesEnabled = polylinesEnabled,
            tripPolylines = tripPolylines,
            tripNamesById = trips.associate { it.id to it.name },
            selectedStop = resolvedSelectedStop,
            selectedStopSessions = pickerSessions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUi())

    fun setClusteringEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setMapClusteringEnabled(enabled) }
    }

    fun setHeatmapEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setMapHeatmapEnabled(enabled) }
    }

    fun setPolylinesEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setMapPolylinesEnabled(enabled) }
    }

    fun setMapType(type: String) {
        viewModelScope.launch { appPreferences.setMapType(type) }
    }

    fun toggleTripVisibility(tripId: Long?) {
        filters.update {
            val nextHidden = if (tripId in it.hiddenKeys) it.hiddenKeys - tripId
                             else it.hiddenKeys + tripId
            it.copy(hiddenKeys = nextHidden)
        }
    }

    fun showAllTrips() {
        filters.update { it.copy(hiddenKeys = emptySet()) }
    }

    /**
     * Mark every currently-known trip bucket (and the untripped bucket if it
     * has any sessions) as hidden. Combined with the per-row checkbox, this
     * gives the user a fast "start from nothing, opt in just a few" workflow
     * when their trip list is long.
     */
    fun hideAllTrips() {
        val ui = state.value
        val keys = buildSet<Long?> {
            ui.tripOptions.forEach { add(it.tripId) }
            if (ui.showUntrippedOption) add(null)
        }
        filters.update { it.copy(hiddenKeys = keys) }
    }

    fun setVehicleFilter(vehicleId: Long?) {
        filters.update { it.copy(vehicleFilter = vehicleId) }
    }

    /** Open or close the per-pin session picker. Pass null to dismiss. */
    fun selectStop(stop: MapStop?) {
        selectedStop.value = stop
    }

    fun setColorByTrip(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setMapColorByTrip(enabled) }
    }

    fun resetFilters() {
        filters.value = MapFilters()
    }

    /** Whether the app currently holds a location permission — the screen's
     *  my-location button asks before choosing "request" vs "locate". */
    fun hasLocationPermission(): Boolean = locationAutofill.hasPermission()

    /** Device's current coordinates for the my-location button, or null
     *  when permission/provider/fix is unavailable. */
    suspend fun currentLatLng(): Pair<Double, Double>? = locationAutofill.currentLatLng()

    /**
     * Geocode every distinct stop that has no coordinates yet; results are
     * written back to the matching session rows so the next open is instant.
     * Called on every map entry — cheap when there's nothing new to do.
     */
    fun runBackfillIfNeeded() {
        if (backfillInFlight) return
        backfillInFlight = true
        viewModelScope.launch {
            try {
                runBackfill()
            } finally {
                backfillInFlight = false
            }
        }
    }

    private suspend fun runBackfill() {
        // Snapshot the current sessions list without retaining a long-lived collector.
        val sessions = sessionRepository.observeAll().first()
        // Work on the sessions that still need coordinates, grouped by
        // (stop, exact geocode inputs). Grouping by StopKey alone was
        // too coarse both ways: a brand-only key can mix physically
        // different stations (stamping one sample's point onto all of
        // them collapsed distinct stops onto one pin), and once part of
        // a group was located the rest of it was never retried — those
        // sessions simply stayed off the map forever.
        val groups = sessions
            .filter { !it.hasCoordinates() && !it.geocodeQuery().isNullOrBlank() }
            .filter { StopKey.of(it).isNotBlank() }
            .groupBy { StopKey.of(it) to it.geocodeQuery() }
        if (groups.isEmpty()) {
            backfillStatus.value = BackfillState(completed = true)
            return
        }
        // Persisted throttle: addresses that didn't resolve last time
        // (no network, ambiguous, truly bogus) would otherwise be
        // retried on every map open. But an address created or edited
        // SINCE the last attempt has never been tried at all — skipping
        // it isn't throttling a retry, it's ignoring fresh input (and
        // it delayed the couldn't-locate snackbar by up to a day). So
        // inside the window, only the new work runs.
        val lastAttempt = appPreferences.lastMapBackfillAt() ?: 0L
        val sinceLast = System.currentTimeMillis() - lastAttempt
        val throttled = lastAttempt > 0 && sinceLast < BACKFILL_THROTTLE_MS
        val groupsToTry = if (!throttled) groups
        else groups.filterValues { group -> group.any { it.updatedAt > lastAttempt } }
        if (groupsToTry.isEmpty()) {
            backfillStatus.value = BackfillState(completed = true)
            return
        }
        backfillStatus.value = BackfillState(running = true)
        var failed = 0
        for ((_, group) in groupsToTry) {
            // Every member of the group shares the same geocode inputs
            // by construction, so one lookup locates them all — and a
            // group that shares only a brand with some other station is
            // its own group here, so it can never be stamped with that
            // station's point. The structured geocoder validates the
            // result against the city it was given.
            val sample = group.first()
            val located = locationAutofill.geocode(
                address = sample.locationAddress?.takeIf { it.isNotBlank() }
                    ?: sample.stationName?.takeIf { it.isNotBlank() },
                city = sample.locationCity?.takeIf { it.isNotBlank() },
                province = sample.locationProvince?.takeIf { it.isNotBlank() },
            )
            val lat = located?.latitude
            val lng = located?.longitude
            if (lat != null && lng != null) {
                sessionRepository.setCoordinates(group.map { it.id }, lat, lng)
            } else {
                failed += 1
            }
        }
        appPreferences.recordMapBackfillAttempt()
        backfillStatus.value = BackfillState(completed = true, failed = failed)
    }

    private fun buildStop(
        key: String,
        group: List<ChargingSession>,
        tripColorById: Map<Long, String?>,
        filters: MapFilters,
    ): MapStop? {
        val located = group.filter { it.hasCoordinates() }
        if (located.isEmpty()) return null

        // Hide a stop entirely when none of its located visits map to a
        // currently-visible trip (or untripped) bucket.
        val visible = located.filter { it.tripId !in filters.hiddenKeys }
        if (visible.isEmpty()) return null

        val avgLat = visible.mapNotNull { it.latitude }.average()
        val avgLng = visible.mapNotNull { it.longitude }.average()
        val newest = visible.maxByOrNull { it.sessionStart } ?: return null

        // Pin kind reflects the visible subset, so hiding one trip on a
        // stop visited across two trips re-colors it to the remaining trip
        // instead of staying gray-shared.
        val tripIds = visible.map { it.tripId }.distinct()
        val pinKind = when {
            tripIds.size == 1 && tripIds.single() == null -> PinKind.Untripped
            tripIds.size == 1 -> PinKind.SingleTrip(tripColorById[tripIds.single()!!])
            else -> PinKind.Shared
        }

        return MapStop(
            key = key,
            brand = newest.brand,
            address = newest.locationAddress,
            city = newest.locationCity,
            province = newest.locationProvince,
            stationName = newest.stationName,
            latitude = avgLat,
            longitude = avgLng,
            visits = visible.size,
            lastVisit = visible.maxOf { it.sessionStart },
            sessionIds = visible.map { it.id },
            pinKind = pinKind,
        )
    }

    private data class BackfillState(
        val running: Boolean = false,
        val completed: Boolean = false,
        val failed: Int = 0,
    )

    /** Bundle of map display prefs streamed in by [mapPrefs]. Destructured
     *  by the outer state combine to keep things readable. */
    private data class MapPrefs(
        val mapType: String,
        val clusteringEnabled: Boolean,
        val heatmapEnabled: Boolean,
        val polylinesEnabled: Boolean,
        val colorByTrip: Boolean,
    )

    companion object {
        /** Don't re-run the address geocode pass more than once per day —
         *  long enough that process-death restarts share a single attempt,
         *  short enough that the user gets a fresh shot if their network
         *  was down or an address became resolvable. */
        private const val BACKFILL_THROTTLE_MS = 24L * 60L * 60L * 1000L
    }
}

private fun ChargingSession.hasCoordinates(): Boolean =
    latitude != null && longitude != null

private fun ChargingSession.geocodeQuery(): String? = listOfNotNull(
    locationAddress?.takeIf { it.isNotBlank() } ?: stationName?.takeIf { it.isNotBlank() },
    locationCity?.takeIf { it.isNotBlank() },
    locationProvince?.takeIf { it.isNotBlank() },
).joinToString(", ").takeIf { it.isNotBlank() }
