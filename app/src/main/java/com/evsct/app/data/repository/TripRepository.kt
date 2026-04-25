package com.evsct.app.data.repository

import com.evsct.app.data.db.TripDao
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
                    totalCost = tripSessions.sumOf { it.totalCost ?: 0.0 },
                    totalEnergyKwh = tripSessions.sumOf { it.energyKwh ?: 0.0 },
                    totalDistanceKm = tripDistanceKm(tripSessions.mapNotNull { it.odometerKm }),
                )
            }
        }

    suspend fun findById(id: Long): Trip? = tripDao.findById(id)

    suspend fun upsert(trip: Trip): Long = tripDao.insert(trip)

    suspend fun delete(trip: Trip) = tripDao.delete(trip)

    private fun tripDistanceKm(odometers: List<Double>): Double {
        if (odometers.size < 2) return 0.0
        val sorted = odometers.sorted()
        return sorted.last() - sorted.first()
    }
}
