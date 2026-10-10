package com.evsct.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.util.ImportOrder
import com.evsct.app.util.OdometerDistance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * A restore or CSV import re-inserts sessions with fresh ids, and the app
 * breaks ties on sessionStart by id. Read the way export reads them (newest
 * first) and re-inserted the way restore and import now insert them, two
 * same-timestamp sessions must keep their order, and the odometer walk its
 * distance. Before ImportOrder the second pair flipped, and the walk read
 * 10,000 → 10,300 → (10,100 skipped) → 10,500: 700 km instead of 500.
 */
@RunWith(AndroidJUnit4::class)
class ImportTieOrderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbs = mutableListOf<EvsctDatabase>()

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, EvsctDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { dbs += it }

    @After
    fun close() = dbs.forEach { it.close() }

    @Test
    fun `round trips keep same-timestamp sessions in order`() = runBlocking {
        val day = 86_400_000L
        val source = newDb()
        val car = source.vehicleDao().insert(Vehicle(name = "EV6"))
        // Inserted in true order; the two day-2 charges share a midnight
        // timestamp, as date-only CSV rows do.
        listOf(10_000.0 to 0L, 10_100.0 to day, 10_300.0 to day, 10_500.0 to 2 * day).forEach { (odo, t) ->
            source.sessionDao().insert(ChargingSession(sessionStart = t, odometerKm = odo, vehicleId = car))
        }

        var exported = source.sessionDao().observeAll().first()
        repeat(3) {
            val target = newDb()
            val targetCar = target.vehicleDao().insert(Vehicle(name = "EV6"))
            val ordered = ImportOrder.chronological(exported, { it.sessionStart }, { it.id })
            target.sessionDao().insertAll(ordered.map { it.copy(id = 0, vehicleId = targetCar) })
            exported = target.sessionDao().observeAll().first()

            val dayTwo = exported.filter { it.sessionStart == day }.sortedBy { it.id }.map { it.odometerKm }
            assertEquals(listOf(10_100.0, 10_300.0), dayTwo)
            assertEquals(500.0, OdometerDistance.inWindow(exported, 0, 3 * day), 1e-9)
        }
    }
}
