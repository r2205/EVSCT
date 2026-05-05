package com.evsct.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.evsct.app.data.db.ChargingSessionDao
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.db.TripDao
import com.evsct.app.data.db.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.evsctDataStore: DataStore<Preferences> by preferencesDataStore("evsct_prefs")

/** Application-scoped coroutine scope for fire-and-forget work that must
 *  outlive a ViewModel's lifecycle (e.g., orphaned-file cleanup after the
 *  user navigates back without saving). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EvsctDatabase =
        Room.databaseBuilder(context, EvsctDatabase::class.java, EvsctDatabase.NAME)
            .addMigrations(
                EvsctDatabase.MIGRATION_1_2,
                EvsctDatabase.MIGRATION_2_3,
                EvsctDatabase.MIGRATION_3_4,
                EvsctDatabase.MIGRATION_4_5,
                EvsctDatabase.MIGRATION_5_6,
                EvsctDatabase.MIGRATION_6_7,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: EvsctDatabase): ChargingSessionDao = db.sessionDao()

    @Provides
    fun provideTripDao(db: EvsctDatabase): TripDao = db.tripDao()

    @Provides
    fun provideVehicleDao(db: EvsctDatabase): VehicleDao = db.vehicleDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.evsctDataStore

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
