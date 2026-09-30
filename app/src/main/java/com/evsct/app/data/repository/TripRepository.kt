package com.evsct.app.data.repository

import com.evsct.app.data.db.TripDao
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.ui.map.TripPinColor
import com.evsct.app.util.TripReport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val sessionRepository: SessionRepository,
    private val vehicleRepository: VehicleRepository,
) {
    fun observeAll(): Flow<List<Trip>> = tripDao.observeAll()

    /** Every trip with its totals, including the energy its drive used —
     *  which needs each trip's car (for battery capacity) and the whole log
     *  (to catch charges between two of its readings). */
    fun observeAllWithStats(): Flow<List<TripWithStats>> =
        combine(
            tripDao.observeAll(),
            sessionRepository.observeAll(),
            vehicleRepository.observeAll(),
        ) { trips, sessions, vehicles ->
            val sessionsByTrip = sessions.groupBy { it.tripId }
            trips.map { trip ->
                val tripSessions = sessionsByTrip[trip.id].orEmpty()
                TripWithStats.of(trip, tripSessions, TripReport.of(trip, tripSessions, sessions, vehicles))
            }
        }

    suspend fun findById(id: Long): Trip? = tripDao.findById(id)

    suspend fun upsert(trip: Trip): Long {
        // Auto-pick a map-pin color when none is set — for new trips, and
        // for existing ones reset to "Auto" in the color picker. The trip's
        // own row is excluded from the usage count so a re-pick spreads
        // across the palette instead of counting itself.
        val withColor = if (trip.pinColor == null) {
            val used = tripDao.observeAll().first()
                .filter { it.id != trip.id }
                .map { it.pinColor }
            trip.copy(pinColor = TripPinColor.nextDefault(used).name)
        } else {
            trip
        }
        return if (withColor.id == 0L) {
            tripDao.insert(withColor)
        } else {
            // Avoid REPLACE-on-conflict: it would delete the existing row first,
            // which fires ON DELETE SET NULL on charging_sessions.tripId and
            // strips every session of its trip tag.
            tripDao.update(withColor)
            withColor.id
        }
    }

    suspend fun delete(trip: Trip) = tripDao.delete(trip)

    suspend fun setVehicle(tripId: Long, vehicleId: Long?) = tripDao.setVehicle(tripId, vehicleId)

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
}
