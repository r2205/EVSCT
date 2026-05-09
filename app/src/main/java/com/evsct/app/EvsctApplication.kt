package com.evsct.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.di.AppScope
import com.evsct.app.util.BackupReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class EvsctApplication : Application(), Configuration.Provider {

    @Inject lateinit var tripRepository: TripRepository

    @Inject @AppScope lateinit var appScope: CoroutineScope

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    /** Hand WorkManager our Hilt-aware factory so [BackupReminderWorker]
     *  (and any future @HiltWorker) gets its dependencies injected. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // One-shot backfill: assign a default pin color to any trip created
        // before the v6 schema migration whose pinColor is still null. New
        // trips already get auto-coloured at insert.
        appScope.launch { tripRepository.backfillMissingPinColors() }
        // Re-evaluate the backup reminder on every cold start. Posts the
        // notification if the user is already overdue, and re-enqueues the
        // background worker so the daily nag chain keeps running even
        // after process death.
        appScope.launch { backupReminderScheduler.refresh() }
    }
}
