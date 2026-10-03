package com.evsct.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.evsct.app.data.entity.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY COALESCE(startDate, createdAt) DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun findById(id: Long): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: Trip): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<Trip>): List<Long>

    @Update
    suspend fun update(trip: Trip)

    /** Set just [tripId]'s vehicle. CSV import uses it to settle a trip's
     *  car mid-transaction, where TripRepository.upsert's Flow read of the
     *  trips table must not run. */
    @Query("UPDATE trips SET vehicleId = :vehicleId WHERE id = :tripId")
    suspend fun setVehicle(tripId: Long, vehicleId: Long?)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
