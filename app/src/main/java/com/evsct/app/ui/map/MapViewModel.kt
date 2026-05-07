package com.evsct.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.util.LocationAutofill
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

data class MapFilters(
    /** Trip IDs (and null = "untripped") whose pins are hidden. Empty = show all. */
    val hiddenKeys: Set<Long?> = emptySet(),
    val colorByTrip: Boolean = true,
)

data class MapUi(
    val stops: List<MapStop> = emptyList(),
    val totalDistinct: Int = 0,
    val unlocatedDistinct: Int = 0,
    val backfillRunning: Boolean = false,
    val backfillCompleted: Boolean = false,
    val backfillFailed: Int = 0,
    val tripOptions: List<TripFilterOption> = emptyList(),
    val showUntrippedOption: Boolean = false,
    val untrippedVisible: Boolean = true,
    val colorByTrip: Boolean = true,
    val anyFilterActive: Boolean = false,
    /** User's preferred Google Maps base layer (NORMAL / SATELLITE / HYBRID
     *  / TERRAIN). The screen converts this to the Maps Compose enum. */
    val mapType: String = "NORMAL",
    /** When false, every pin renders individually regardless of zoom. */
    val clusteringEnabled: Boolean = true,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val locationAutofill: LocationAutofill,
    private val appPreferences: com.evsct.app.data.prefs.AppPreferences,
) : ViewModel() {

    private val backfillStatus = MutableStateFlow(BackfillState())
    private var backfillRequested = false
    private val filters = MutableStateFlow(MapFilters())

    // typed combine maxes out at 5 flows; collapse the two map-related
    // prefs into a single Pair stream so we still fit.
    private val mapPrefs = combine(
        appPreferences.mapType,
        appPreferences.mapClusteringEnabled,
    ) { type, enabled -> type to enabled }

    val state: StateFlow<MapUi> = combine(
        sessionRepository.observeAll(),
        tripRepository.observeAll(),
        backfillStatus,
        filters,
        mapPrefs,
    ) { sessions, trips, status, f, prefs ->
        val (mapType, clusteringEnabled) = prefs
        val tripColorById = trips.associate { it.id to it.pinColor }
        val groups = sessions.groupBy(::stopKey).filterKeys { it.isNotBlank() }

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

        MapUi(
            stops = stops,
            totalDistinct = groups.size,
            unlocatedDistinct = unlocated,
            backfillRunning = status.running,
            backfillCompleted = status.completed,
            backfillFailed = status.failed,
            tripOptions = tripOptions,
            showUntrippedOption = hasUntripped,
            untrippedVisible = untrippedVisible,
            colorByTrip = f.colorByTrip,
            anyFilterActive = f.hiddenKeys.isNotEmpty() || !f.colorByTrip,
            mapType = mapType,
            clusteringEnabled = clusteringEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUi())

    fun setClusteringEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setMapClusteringEnabled(enabled) }
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

    fun setColorByTrip(enabled: Boolean) {
        filters.update { it.copy(colorByTrip = enabled) }
    }

    fun resetFilters() {
        filters.value = MapFilters()
    }

    /**
     * Geocode every distinct stop that has no coordinates yet. Runs once per
     * VM lifetime; results are written back to the matching session rows so
     * the next open is instant.
     */
    fun runBackfillIfNeeded() {
        if (backfillRequested) return
        backfillRequested = true
        viewModelScope.launch {
            // Snapshot the current sessions list without retaining a long-lived collector.
            val sessions = sessionRepository.observeAll().first()
            val groups = sessions
                .groupBy(::stopKey)
                .filterKeys { it.isNotBlank() }
                .filterValues { group -> group.none { it.hasCoordinates() } }
                .filterValues { group -> group.any { !it.geocodeQuery().isNullOrBlank() } }
            if (groups.isEmpty()) {
                backfillStatus.value = BackfillState(completed = true)
                return@launch
            }
            // Persisted throttle: addresses that didn't resolve last time
            // (no network, ambiguous, etc.) would otherwise be retried on
            // every cold start. Skip the pass entirely if we attempted one
            // recently; the user can still force a retry by waiting it out
            // or by editing the address (which clears that session's coords
            // and re-geocodes immediately on save).
            val lastAttempt = appPreferences.lastMapBackfillAt() ?: 0L
            val sinceLast = System.currentTimeMillis() - lastAttempt
            if (lastAttempt > 0 && sinceLast < BACKFILL_THROTTLE_MS) {
                backfillStatus.value = BackfillState(completed = true)
                return@launch
            }
            backfillStatus.value = BackfillState(running = true)
            var failed = 0
            for ((_, group) in groups) {
                // Pick the first session in the group that has the fields
                // we need; the structured geocoder validates the result
                // against the city it was given.
                val sample = group.firstOrNull { !it.geocodeQuery().isNullOrBlank() } ?: continue
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

    companion object {
        /** Don't re-run the address geocode pass more than once per day —
         *  long enough that process-death restarts share a single attempt,
         *  short enough that the user gets a fresh shot if their network
         *  was down or an address became resolvable. */
        private const val BACKFILL_THROTTLE_MS = 24L * 60L * 60L * 1000L
    }
}

/** Stops are grouped by brand + address + city only. Station/stall name is
 *  intentionally NOT part of the key — visits to the same physical charger
 *  should share a pin even when each visit logs a different stall number.
 *  Mirrors the matching helper in SessionEditViewModel. */
private fun stopKey(s: ChargingSession): String = listOfNotNull(
    s.brand?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    s.locationAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    s.locationCity?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
).joinToString("|")

private fun ChargingSession.hasCoordinates(): Boolean =
    latitude != null && longitude != null

private fun ChargingSession.geocodeQuery(): String? = listOfNotNull(
    locationAddress?.takeIf { it.isNotBlank() } ?: stationName?.takeIf { it.isNotBlank() },
    locationCity?.takeIf { it.isNotBlank() },
    locationProvince?.takeIf { it.isNotBlank() },
).joinToString(", ").takeIf { it.isNotBlank() }
