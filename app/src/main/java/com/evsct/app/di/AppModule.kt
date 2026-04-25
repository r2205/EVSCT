package com.evsct.app.di

import android.content.Context
import androidx.room.Room
import com.evsct.app.data.db.ChargingSessionDao
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.db.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EvsctDatabase =
        Room.databaseBuilder(context, EvsctDatabase::class.java, EvsctDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: EvsctDatabase): ChargingSessionDao = db.sessionDao()

    @Provides
    fun provideTripDao(db: EvsctDatabase): TripDao = db.tripDao()
}
