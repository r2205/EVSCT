package com.evsct.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip

@Database(
    entities = [ChargingSession::class, Trip::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EvsctDatabase : RoomDatabase() {
    abstract fun sessionDao(): ChargingSessionDao
    abstract fun tripDao(): TripDao

    companion object {
        const val NAME = "evsct.db"
    }
}
