package com.evsct.app.data.db

import com.evsct.app.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises [VehicleDao.saveEnsuringSingleDefault], the default (interface-
 * body) method that keeps the isDefault flag on at most one row. The fake
 * implements the abstract query methods in memory with the same semantics
 * as their SQL, so the inherited default method runs its real logic.
 */
class VehicleDaoDefaultTest {

    @Test
    fun `inserting a new default vehicle demotes the previous default`() = runBlocking {
        val dao = FakeVehicleDao()
        val old = dao.insert(vehicle("Old", isDefault = true))

        val new = dao.saveEnsuringSingleDefault(vehicle("New", isDefault = true))

        assertFalse(dao.findById(old)!!.isDefault)
        assertTrue(dao.findById(new)!!.isDefault)
        assertEquals(new, dao.findDefault()!!.id)
    }

    @Test
    fun `updating an existing vehicle to default demotes the previous default`() = runBlocking {
        val dao = FakeVehicleDao()
        val old = dao.insert(vehicle("Old", isDefault = true))
        val other = dao.insert(vehicle("Other", isDefault = false))

        dao.saveEnsuringSingleDefault(dao.findById(other)!!.copy(isDefault = true))

        assertFalse(dao.findById(old)!!.isDefault)
        assertTrue(dao.findById(other)!!.isDefault)
    }

    @Test
    fun `saving a non-default vehicle leaves the existing default alone`() = runBlocking {
        val dao = FakeVehicleDao()
        val old = dao.insert(vehicle("Old", isDefault = true))

        dao.saveEnsuringSingleDefault(vehicle("New", isDefault = false))

        assertTrue(dao.findById(old)!!.isDefault)
        assertEquals(old, dao.findDefault()!!.id)
    }

    @Test
    fun `updating goes through update, not REPLACE-insert`() = runBlocking {
        val dao = FakeVehicleDao()
        val id = dao.insert(vehicle("Car", isDefault = false))

        dao.saveEnsuringSingleDefault(dao.findById(id)!!.copy(name = "Renamed"))

        assertEquals(0, dao.replaceInserts)
        assertEquals("Renamed", dao.findById(id)!!.name)
    }

    private fun vehicle(name: String, isDefault: Boolean) = Vehicle(
        name = name,
        isDefault = isDefault,
        createdAt = 0,
        updatedAt = 0,
    )
}

/** In-memory stand-in whose query methods mirror their SQL semantics. */
private class FakeVehicleDao : VehicleDao {

    private val rows = linkedMapOf<Long, Vehicle>()
    private var nextId = 1L

    /** Counts inserts issued for rows that already exist (REPLACE path). */
    var replaceInserts = 0
        private set

    override fun observeAll(): Flow<List<Vehicle>> = flowOf(rows.values.toList())

    override suspend fun findById(id: Long): Vehicle? = rows[id]

    override suspend fun findDefault(): Vehicle? = rows.values.firstOrNull { it.isDefault }

    override suspend fun insert(vehicle: Vehicle): Long {
        if (vehicle.id != 0L && rows.containsKey(vehicle.id)) replaceInserts++
        val id = if (vehicle.id == 0L) nextId++ else vehicle.id
        rows[id] = vehicle.copy(id = id)
        return id
    }

    override suspend fun insertAll(vehicles: List<Vehicle>): List<Long> = vehicles.map { insert(it) }

    override suspend fun update(vehicle: Vehicle) {
        if (rows.containsKey(vehicle.id)) rows[vehicle.id] = vehicle
    }

    override suspend fun delete(vehicle: Vehicle) {
        rows.remove(vehicle.id)
    }

    override suspend fun clearDefaultExcept(exceptId: Long) {
        rows.keys.filter { it != exceptId }.forEach { id ->
            rows[id]?.let { rows[id] = it.copy(isDefault = false) }
        }
    }

    override suspend fun deleteAll() = rows.clear()
}
