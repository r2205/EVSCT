package com.evsct.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle

@Database(
    entities = [ChargingSession::class, Trip::class, Vehicle::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EvsctDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChargingSessionDao
    abstract fun tripDao(): TripDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val NAME = "evsct.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        year INTEGER,
                        make TEXT,
                        model TEXT,
                        trim TEXT,
                        batteryCapacityKwh REAL,
                        nominalRangeKm INTEGER,
                        vin TEXT,
                        notes TEXT,
                        imagePath TEXT,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN vehicleId INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charging_sessions_vehicleId ON charging_sessions(vehicleId)"
                )
            }
        }
    }
}
