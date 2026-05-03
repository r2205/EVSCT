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
    version = 6,
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
                // SQLite can't add a column with a foreign-key constraint via
                // ALTER TABLE. To match the schema Room derives from the
                // entities, recreate `charging_sessions` with the new
                // `vehicleId` column and FK, copy data over, then swap.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `year` INTEGER,
                        `make` TEXT,
                        `model` TEXT,
                        `trim` TEXT,
                        `batteryCapacityKwh` REAL,
                        `nominalRangeKm` INTEGER,
                        `vin` TEXT,
                        `notes` TEXT,
                        `imagePath` TEXT,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `charging_sessions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionStart` INTEGER NOT NULL,
                        `durationSeconds` INTEGER,
                        `odometerKm` REAL,
                        `energyKwh` REAL,
                        `totalCost` REAL,
                        `currency` TEXT NOT NULL,
                        `postedEnergyPricePerKwh` REAL,
                        `postedTimeRatePerMin` REAL,
                        `postedMaxPowerKw` REAL,
                        `batteryStartPct` INTEGER,
                        `batteryEndPct` INTEGER,
                        `chargingType` TEXT NOT NULL,
                        `pricingModel` TEXT NOT NULL,
                        `brand` TEXT,
                        `locationCity` TEXT,
                        `locationProvince` TEXT,
                        `locationAddress` TEXT,
                        `stationName` TEXT,
                        `stallName` TEXT,
                        `tripId` INTEGER,
                        `vehicleId` INTEGER,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `charging_sessions_new` (
                        id, sessionStart, durationSeconds, odometerKm, energyKwh, totalCost,
                        currency, postedEnergyPricePerKwh, postedTimeRatePerMin, postedMaxPowerKw,
                        batteryStartPct, batteryEndPct, chargingType, pricingModel,
                        brand, locationCity, locationProvince, locationAddress, stationName, stallName,
                        tripId, vehicleId, notes, createdAt, updatedAt
                    )
                    SELECT
                        id, sessionStart, durationSeconds, odometerKm, energyKwh, totalCost,
                        currency, postedEnergyPricePerKwh, postedTimeRatePerMin, postedMaxPowerKw,
                        batteryStartPct, batteryEndPct, chargingType, pricingModel,
                        brand, locationCity, locationProvince, locationAddress, stationName, stallName,
                        tripId, NULL, notes, createdAt, updatedAt
                    FROM `charging_sessions`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `charging_sessions`")
                db.execSQL("ALTER TABLE `charging_sessions_new` RENAME TO `charging_sessions`")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_charging_sessions_tripId` ON `charging_sessions` (`tripId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_charging_sessions_vehicleId` ON `charging_sessions` (`vehicleId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_charging_sessions_sessionStart` ON `charging_sessions` (`sessionStart`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `startOdometerKm` REAL")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `endOdometerKm` REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `charging_sessions` ADD COLUMN `receiptImagePath` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `charging_sessions` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `charging_sessions` ADD COLUMN `longitude` REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `pinColor` TEXT")
            }
        }
    }
}
