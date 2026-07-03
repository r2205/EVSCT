package com.evsct.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.evsct.app.data.entity.ChargingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Query("SELECT * FROM charging_sessions ORDER BY sessionStart DESC")
    fun observeAll(): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions WHERE tripId = :tripId ORDER BY sessionStart ASC")
    fun observeForTrip(tripId: Long): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    suspend fun findById(id: Long): ChargingSession?

    @Query(
        """
        SELECT TRIM(brand) FROM charging_sessions
        WHERE brand IS NOT NULL AND TRIM(brand) != ''
        GROUP BY TRIM(brand) COLLATE NOCASE
        ORDER BY COUNT(*) DESC, TRIM(brand) COLLATE NOCASE
        """
    )
    fun observeBrands(): Flow<List<String>>

    // SQLite's `<text> || NULL` evaluates to NULL, so a naive
    // `locationCity || ', ' || locationProvince` would silently drop every
    // session with a city set but a null/blank province. The CASE expression
    // emits "City, Prov" when province is present and just "City" otherwise.
    @Query(
        """
        SELECT DISTINCT
            CASE
                WHEN locationProvince IS NULL OR TRIM(locationProvince) = ''
                    THEN TRIM(locationCity)
                ELSE TRIM(locationCity) || ', ' || TRIM(locationProvince)
            END AS city
        FROM charging_sessions
        WHERE locationCity IS NOT NULL AND TRIM(locationCity) != ''
        ORDER BY city COLLATE NOCASE
        """
    )
    fun observeCities(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChargingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ChargingSession>): List<Long>

    @Update
    suspend fun update(session: ChargingSession)

    @Delete
    suspend fun delete(session: ChargingSession)

    @Query("UPDATE charging_sessions SET tripId = :tripId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun assignTripToIds(ids: List<Long>, tripId: Long?, now: Long): Int

    @Query(
        "UPDATE charging_sessions SET latitude = :lat, longitude = :lng, updatedAt = :now " +
            "WHERE id IN (:ids)"
    )
    suspend fun setCoordinatesForIds(ids: List<Long>, lat: Double, lng: Double, now: Long): Int

    @Query("DELETE FROM charging_sessions")
    suspend fun deleteAll()

    // `IS` instead of `=` so a null vehicleId matches other null-vehicle
    // sessions; the efficiency analysis groups by vehicleId the same way.
    @Query(
        """
        SELECT * FROM charging_sessions
        WHERE vehicleId IS :vehicleId AND id != :excludeId AND sessionStart >= :start
        ORDER BY sessionStart ASC, id ASC LIMIT 1
        """
    )
    suspend fun firstAfter(vehicleId: Long?, start: Long, excludeId: Long): ChargingSession?

    @Query("UPDATE charging_sessions SET continuesPrevious = 0, updatedAt = :now WHERE id = :id")
    suspend fun clearContinuesPrevious(id: Long, now: Long)

    /**
     * Deletes [session] and clears `continuesPrevious` on the session that
     * immediately followed it on the same vehicle's timeline. That flag
     * attests "no untracked charging since the previous session" — and the
     * previous session is the one being deleted, which becomes exactly an
     * untracked charge in the gap. Left set, the flag would silently
     * re-target an older session the user never vouched for.
     */
    @Transaction
    suspend fun deleteAndClearStaleContinuity(session: ChargingSession, now: Long) {
        delete(session)
        val follower = firstAfter(session.vehicleId, session.sessionStart, session.id)
        if (follower != null && follower.continuesPrevious) {
            clearContinuesPrevious(follower.id, now)
        }
    }

    /**
     * Updates [session] and, when the edit moved it (new start time or
     * vehicle) out from directly in front of a flagged follower, clears that
     * follower's `continuesPrevious` — the attestation targeted this session
     * at its old position, and after the move it would silently re-target
     * whichever session is adjacent now.
     *
     * The reverse direction — this session landing directly in front of some
     * other flagged session — is deliberately left alone: a tracked charge
     * appearing inside an attested gap only narrows the gap (this is the
     * backfill workflow), it doesn't invalidate "nothing untracked in
     * between".
     */
    @Transaction
    suspend fun updateAndClearStaleContinuity(session: ChargingSession, now: Long) {
        val before = findById(session.id)
        update(session)
        if (before == null) return
        if (before.vehicleId == session.vehicleId && before.sessionStart == session.sessionStart) return
        val follower = firstAfter(before.vehicleId, before.sessionStart, before.id) ?: return
        if (!follower.continuesPrevious) return
        val stillPredecessor =
            firstAfter(session.vehicleId, session.sessionStart, session.id)?.id == follower.id
        if (!stillPredecessor) clearContinuesPrevious(follower.id, now)
    }
}
