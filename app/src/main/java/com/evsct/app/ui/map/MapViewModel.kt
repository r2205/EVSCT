package com.evsct.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.util.LocationAutofill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
)

data class MapUi(
    val stops: List<MapStop> = emptyList(),
    val totalDistinct: Int = 0,
    val unlocatedDistinct: Int = 0,
    val backfillRunning: Boolean = false,
    val backfillCompleted: Boolean = false,
    val backfillFailed: Int = 0,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val locationAutofill: LocationAutofill,
) : ViewModel() {

    private val backfillStatus = MutableStateFlow(BackfillState())
    private var backfillRequested = false

    val state: StateFlow<MapUi> = combine(
        sessionRepository.observeAll(),
        backfillStatus,
    ) { sessions, status ->
        val groups = sessions.groupBy(::stopKey).filterKeys { it.isNotBlank() }
        val stops = groups.mapNotNull { (key, group) -> buildStop(key, group) }
            .sortedByDescending { it.lastVisit }
        val unlocated = groups.values.count { group -> group.none { it.hasCoordinates() } }
        MapUi(
            stops = stops,
            totalDistinct = groups.size,
            unlocatedDistinct = unlocated,
            backfillRunning = status.running,
            backfillCompleted = status.completed,
            backfillFailed = status.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUi())

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
            backfillStatus.value = BackfillState(running = true)
            var failed = 0
            for ((_, group) in groups) {
                val query = group.firstNotNullOfOrNull { it.geocodeQuery() } ?: continue
                val located = locationAutofill.geocodeAddress(query)
                val lat = located?.latitude
                val lng = located?.longitude
                if (lat != null && lng != null) {
                    sessionRepository.setCoordinates(group.map { it.id }, lat, lng)
                } else {
                    failed += 1
                }
            }
            backfillStatus.value = BackfillState(completed = true, failed = failed)
        }
    }

    private fun buildStop(key: String, group: List<ChargingSession>): MapStop? {
        val located = group.filter { it.hasCoordinates() }
        if (located.isEmpty()) return null
        val avgLat = located.mapNotNull { it.latitude }.average()
        val avgLng = located.mapNotNull { it.longitude }.average()
        val newest = group.maxByOrNull { it.sessionStart } ?: return null
        return MapStop(
            key = key,
            brand = newest.brand,
            address = newest.locationAddress,
            city = newest.locationCity,
            province = newest.locationProvince,
            stationName = newest.stationName,
            latitude = avgLat,
            longitude = avgLng,
            visits = group.size,
            lastVisit = group.maxOf { it.sessionStart },
            sessionIds = group.map { it.id },
        )
    }

    private data class BackfillState(
        val running: Boolean = false,
        val completed: Boolean = false,
        val failed: Int = 0,
    )
}

private fun stopKey(s: ChargingSession): String = listOfNotNull(
    s.brand?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    (s.locationAddress ?: s.stationName)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    s.locationCity?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
).joinToString("|")

private fun ChargingSession.hasCoordinates(): Boolean =
    latitude != null && longitude != null

private fun ChargingSession.geocodeQuery(): String? = listOfNotNull(
    locationAddress?.takeIf { it.isNotBlank() } ?: stationName?.takeIf { it.isNotBlank() },
    locationCity?.takeIf { it.isNotBlank() },
    locationProvince?.takeIf { it.isNotBlank() },
).joinToString(", ").takeIf { it.isNotBlank() }
