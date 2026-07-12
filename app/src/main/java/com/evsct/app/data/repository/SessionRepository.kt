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
            dao.updateAndClearStaleContinuity(toSave, now)
            toSave.id
        }
    }

    suspend fun insertAll(sessions: List<ChargingSession>): List<Long> = dao.insertAll(sessions)

    suspend fun assignTrip(ids: Collection<Long>, tripId: Long?) {
        if (ids.isEmpty()) return
        dao.assignTripToIdsChunked(ids.toList(), tripId, System.currentTimeMillis())
    }

    suspend fun setCoordinates(ids: Collection<Long>, lat: Double, lng: Double) {
        if (ids.isEmpty()) return
        dao.setCoordinatesForIdsChunked(ids.toList(), lat, lng, System.currentTimeMillis())
    }

    suspend fun delete(session: ChargingSession) =
        dao.deleteAndClearStaleContinuity(session, System.currentTimeMillis())

    suspend fun deleteMany(sessions: List<ChargingSession>) {
        if (sessions.isEmpty()) return
        dao.deleteAllAndClearStaleContinuity(sessions, System.currentTimeMillis())
    }

    /** Reinstate a just-deleted row exactly as it was (same id, same
     *  timestamps) — the delete-undo path. Bypasses [upsert]'s existing-row
     *  routing, whose @Update would silently no-op on a row that no longer
     *  exists. A follower's continuesPrevious flag cleared by the delete is
     *  NOT re-set: the flag is an attestation, and conservatively staying
     *  cleared only costs the user a re-tick. */
    suspend fun restore(session: ChargingSession) {
        dao.insert(session)
    }

    suspend fun deleteAll() = dao.deleteAll()
}
