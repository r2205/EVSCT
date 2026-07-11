package com.evsct.app.data.repository

import com.evsct.app.data.db.VehicleDao
import com.evsct.app.data.entity.Vehicle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleDao,
) {
    fun observeAll(): Flow<List<Vehicle>> = dao.observeAll()

    suspend fun findById(id: Long): Vehicle? = dao.findById(id)

    suspend fun findDefault(): Vehicle? = dao.findDefault()

    suspend fun upsert(vehicle: Vehicle): Long {
        val now = System.currentTimeMillis()
        val toSave = if (vehicle.id == 0L) {
            vehicle.copy(createdAt = now, updatedAt = now)
        } else {
            vehicle.copy(updatedAt = now)
        }
        return dao.saveEnsuringSingleDefault(toSave)
    }

    suspend fun delete(vehicle: Vehicle) = dao.delete(vehicle)
}
