package com.evsct.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.SessionReceipt
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle

@Database(
    entities = [ChargingSession::class, SessionReceipt::class, Trip::class, Vehicle::class],
    version = 12,
    // Schema JSONs land in app/schemas/ (see room.schemaLocation in
    // build.gradle.kts) and are committed, so future schema changes diff
    // visibly in review and MigrationTestHelper can verify the chain.
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EvsctDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChargingSessionDao
    abstract fun sessionReceiptDao(): SessionReceiptDao
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `charging_sessions` ADD COLUMN `continuesPrevious` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `charging_sessions` ADD COLUMN `waitTimeMinutes` INTEGER"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `charging_sessions` ADD COLUMN `tags` TEXT"
                )
            }
        }

        /**
         * Moves single receipts into a proper many-to-one [session_receipts]
         * table so a session can carry several files. The legacy
         * `charging_sessions.receiptImagePath` column is left in place to
         * avoid a heavy table rebuild — going forward the app writes null
         * to it and reads from the new table exclusively.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_receipts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `charging_sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_receipts_sessionId` " +
                        "ON `session_receipts` (`sessionId`)"
                )
                // Promote every existing single receipt into the new table.
                // The session's createdAt is the best stand-in for when the
                // receipt was attached — we don't track per-receipt times in
                // the old schema.
                db.execSQL(
                    """
                    INSERT INTO `session_receipts` (sessionId, filePath, createdAt)
                    SELECT id, receiptImagePath, createdAt
                    FROM `charging_sessions`
                    WHERE receiptImagePath IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        /** Adds the picker-supplied display name onto each receipt row so the
         *  edit screen can show "expense-aug-2025.pdf" instead of the generic
         *  "PDF receipt" label. Receipts created before this migration stay
         *  null and fall back to the generic label. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `session_receipts` ADD COLUMN `originalFileName` TEXT"
                )
            }
        }

        /** Adds trip-level battery % at start and end. With the existing
         *  start/end odometer these anchor the first and last efficiency
         *  legs (home → first charge, last charge → home). Existing trips
         *  stay null — no anchors, no behavior change. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `startBatteryPct` INTEGER")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `endBatteryPct` INTEGER")
            }
        }
    }
}
