package com.evsct.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT DISTINCT locationCity || ', ' || locationProvince AS city FROM charging_sessions WHERE locationCity IS NOT NULL ORDER BY city COLLATE NOCASE")
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
}
