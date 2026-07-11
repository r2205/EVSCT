package com.evsct.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.evsct.app.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY isDefault DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun findById(id: Long): Vehicle?

    @Query("SELECT * FROM vehicles WHERE isDefault = 1 LIMIT 1")
    suspend fun findDefault(): Vehicle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: Vehicle): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehicles: List<Vehicle>): List<Long>

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Query("UPDATE vehicles SET isDefault = 0 WHERE id != :exceptId")
    suspend fun clearDefaultExcept(exceptId: Long)

    /**
     * Persist [vehicle] and, when it is flagged default, demote every other
     * row in the same transaction. Done as two separate implicit
     * transactions, a process death between them could commit two vehicles
     * with isDefault = 1 — and findDefault() would then pick one
     * arbitrarily, quietly attaching new sessions to the wrong car.
     */
    @Transaction
    suspend fun saveEnsuringSingleDefault(vehicle: Vehicle): Long {
        val id = if (vehicle.id == 0L) {
            insert(vehicle)
        } else {
            // REPLACE-on-conflict insert would delete and reinsert the row,
            // untagging that vehicle's sessions via ON DELETE SET NULL.
            update(vehicle)
            vehicle.id
        }
        if (vehicle.isDefault) clearDefaultExcept(id)
        return id
    }

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
