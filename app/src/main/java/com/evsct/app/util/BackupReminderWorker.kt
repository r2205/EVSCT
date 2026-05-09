package com.evsct.app.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Wakes briefly when a backup reminder is due, posts (or cancels) the
 * notification via [BackupReminderScheduler.refresh], and the scheduler
 * itself enqueues the next check before the worker returns. The chain
 * stays alive as long as the user is overdue; backing up cancels it.
 */
@HiltWorker
class BackupReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: BackupReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        scheduler.refresh()
        return Result.success()
    }
}
