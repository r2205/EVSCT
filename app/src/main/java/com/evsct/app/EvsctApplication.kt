package com.evsct.app

import android.app.Application
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class EvsctApplication : Application() {

    @Inject lateinit var tripRepository: TripRepository

    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // One-shot backfill: assign a default pin color to any trip created
        // before the v6 schema migration whose pinColor is still null. New
        // trips already get auto-coloured at insert.
        appScope.launch { tripRepository.backfillMissingPinColors() }
    }
}
