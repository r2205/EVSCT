package com.evsct.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [EvsctDatabase.MIGRATION_14_15] run the way an app update runs it: a
 * database built from the committed v14 schema is opened by Room at v15,
 * which migrates it and then checks every table against what the entities
 * expect — the check that crashes the app at launch when a migration leaves
 * the schema even slightly off.
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `existing trips get the car their sessions agree on`() {
        createV14 {
            vehicle(1)
            vehicle(2)
            trip(1)
            session(10, trip = 1, vehicle = 1)
            session(11, trip = 1, vehicle = null)  // goes along with car 1
            trip(2)
            session(20, trip = 2, vehicle = 1)
            session(21, trip = 2, vehicle = 2)     // mixed cars: user picks
            trip(3)                                // nothing to go on: user picks
        }

        val room = openMigrated()
        try {
            val trips = runBlocking { room.tripDao().observeAllOnce() }
            assertEquals(mapOf(1L to 1L, 2L to null, 3L to null), trips)
            // The sessions keep their trip tags.
            val tags = room.openHelper.readableDatabase
                .query("SELECT id, tripId FROM charging_sessions ORDER BY id").use { c ->
                    buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
                }
            assertEquals(mapOf(10L to 1L, 11L to 1L, 20L to 2L, 21L to 2L), tags)
        } finally {
            room.close()
        }
    }

    @Test
    fun `with one car, trips nothing else settles get it`() {
        createV14 {
            vehicle(5)
            trip(1)                                // no sessions
            trip(2)
            session(20, trip = 2, vehicle = null)  // only an unassigned session
        }

        val room = openMigrated()
        try {
            assertEquals(mapOf(1L to 5L, 2L to 5L), runBlocking { room.tripDao().observeAllOnce() })
        } finally {
            room.close()
        }
    }

    @Test
    fun `deleting a car afterwards clears its trips' car`() {
        createV14 {
            vehicle(1)
            vehicle(2)
            trip(1)
            session(10, trip = 1, vehicle = 1)
        }

        val room = openMigrated()
        try {
            runBlocking {
                room.vehicleDao().delete(room.vehicleDao().findById(1)!!)
                assertNull(room.tripDao().findById(1)!!.vehicleId)
            }
        } finally {
            room.close()
        }
    }

    private suspend fun TripDao.observeAllOnce(): Map<Long, Long?> =
        observeAll().first().associate { it.id to it.vehicleId }

    private fun openMigrated(): EvsctDatabase =
        Room.databaseBuilder(context, EvsctDatabase::class.java, DB_NAME)
            .addMigrations(EvsctDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()

    /** A v14 database exactly as the committed 14.json describes it, filled
     *  by [fill] and stamped with user_version 14. */
    private fun createV14(fill: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(schemaFile(14).readText()).getJSONObject("database")
        val file = context.getDatabasePath(DB_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.fill()
            db.version = 14
        }
    }

    /** Unit tests run from the module directory; fall back to the repo root. */
    private fun schemaFile(version: Int): File =
        listOf("schemas", "app/schemas")
            .map { File(it, "com.evsct.app.data.db.EvsctDatabase/$version.json") }
            .first { it.exists() }

    private fun SQLiteDatabase.vehicle(id: Long) = execSQL(
        "INSERT INTO vehicles (id, name, isDefault, createdAt, updatedAt) VALUES (?, ?, 0, 0, 0)",
        arrayOf<Any?>(id, "Car $id"),
    )

    private fun SQLiteDatabase.trip(id: Long) = execSQL(
        "INSERT INTO trips (id, name, pinColor, createdAt) VALUES (?, ?, 'RED', 0)",
        arrayOf<Any?>(id, "Trip $id"),
    )

    private fun SQLiteDatabase.session(id: Long, trip: Long?, vehicle: Long?) = execSQL(
        "INSERT INTO charging_sessions (id, sessionStart, currency, chargingType, pricingModel, " +
            "tripId, vehicleId, continuesPrevious, createdAt, updatedAt) " +
            "VALUES (?, ?, 'CAD', 'DC_FAST', 'PER_KWH', ?, ?, 0, 0, 0)",
        arrayOf<Any?>(id, id, trip, vehicle),
    )

    private companion object {
        const val DB_NAME = "migration-14-15-test.db"
    }
}
