package com.evsct.app.data.repository

import com.evsct.app.data.db.TripDao
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.ui.map.TripPinColor
import com.evsct.app.util.CurrencyTotals
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val sessionRepository: SessionRepository,
) {
    fun observeAll(): Flow<List<Trip>> = tripDao.observeAll()

    fun observeAllWithStats(): Flow<List<TripWithStats>> =
        combine(tripDao.observeAll(), sessionRepository.observeAll()) { trips, sessions ->
            trips.map { trip ->
                val tripSessions = sessions.filter { it.tripId == trip.id }
                TripWithStats(
                    trip = trip,
                    sessionCount = tripSessions.size,
                    totalCostByCurrency = CurrencyTotals.from(tripSessions),
                    totalEnergyKwh = tripSessions.sumOf { it.energyKwh ?: 0.0 },
                    totalDistanceKm = computeTripDistance(trip, tripSessions),
                )
            }
        }

    suspend fun findById(id: Long): Trip? = tripDao.findById(id)

    suspend fun upsert(trip: Trip): Long =
        if (trip.id == 0L) {
            // Auto-pick a map-pin color for new trips so each one stands out
            // on the map without making the user choose every time.
            val withColor = if (trip.pinColor == null) {
                val used = tripDao.observeAll().first().map { it.pinColor }
                trip.copy(pinColor = TripPinColor.nextDefault(used).name)
            } else trip
            tripDao.insert(withColor)
        } else {
            // Avoid REPLACE-on-conflict: it would delete the existing row first,
            // which fires ON DELETE SET NULL on charging_sessions.tripId and
            // strips every session of its trip tag.
            tripDao.update(trip)
            trip.id
        }

    suspend fun delete(trip: Trip) = tripDao.delete(trip)

    /**
     * Assign a default pin color to any trip whose [Trip.pinColor] is null.
     * Trips created before the v6 schema migration kept null pinColor forever
     * because [upsert] only auto-picks colors at insert time. Called once at
     * app start; a no-op when nothing's missing.
     */
    suspend fun backfillMissingPinColors() {
        val trips = tripDao.observeAll().first()
        val missing = trips.filter { it.pinColor == null }
        if (missing.isEmpty()) return
        val used = trips.mapNotNull { it.pinColor }.toMutableList()
        missing.forEach { trip ->
            val color = TripPinColor.nextDefault(used).name
            tripDao.update(trip.copy(pinColor = color))
            used += color
        }
    }

    companion object {
        /**
         * If the user filled in both trip-level start and end odometer values,
         * use that (covers free home charging where session odometers under-report).
         * Otherwise fall back to the spread of session odometer readings.
         */
        fun computeTripDistance(
            trip: Trip,
            sessions: List<com.evsct.app.data.entity.ChargingSession>,
        ): Double {
            val s = trip.startOdometerKm
            val e = trip.endOdometerKm
            if (s != null && e != null && e >= s) return e - s

            val odometers = sessions.mapNotNull { it.odometerKm }
            if (odometers.size < 2) return 0.0
            val sorted = odometers.sorted()
            return sorted.last() - sorted.first()
        }
    }
}
