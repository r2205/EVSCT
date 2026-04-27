package com.evsct.app.data.repository

import com.evsct.app.data.db.ChargingSessionDao
import com.evsct.app.data.entity.ChargingSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
    private val dao: ChargingSessionDao,
) {
    fun observeAll(): Flow<List<ChargingSession>> = dao.observeAll()

    fun observeForTrip(tripId: Long): Flow<List<ChargingSession>> = dao.observeForTrip(tripId)

    fun observeBrands(): Flow<List<String>> = dao.observeBrands()

    fun observeCities(): Flow<List<String>> = dao.observeCities()

    suspend fun findById(id: Long): ChargingSession? = dao.findById(id)

    suspend fun upsert(session: ChargingSession): Long {
        val now = System.currentTimeMillis()
        val toSave = if (session.id == 0L) {
            session.copy(createdAt = now, updatedAt = now)
        } else {
            session.copy(updatedAt = now)
        }
        return if (toSave.id == 0L) {
            dao.insert(toSave)
        } else {
            dao.update(toSave)
            toSave.id
        }
    }

    suspend fun insertAll(sessions: List<ChargingSession>): List<Long> = dao.insertAll(sessions)

    suspend fun assignTrip(ids: Collection<Long>, tripId: Long?) {
        if (ids.isEmpty()) return
        dao.assignTripToIds(ids.toList(), tripId, System.currentTimeMillis())
    }

    suspend fun delete(session: ChargingSession) = dao.delete(session)

    suspend fun deleteAll() = dao.deleteAll()
}
