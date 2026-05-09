package com.evsct.app.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.evsct.app.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the backup-reminder notification end-to-end. Wraps the existing
 * [BackupReminderNotifier] (which knows how to post / cancel the
 * notification right now) and adds the future scheduling layer via
 * WorkManager so the OS can wake the app briefly when a backup is overdue
 * — even when the app is closed.
 *
 * Replaces direct calls to the notifier from the rest of the app:
 * call [refresh] whenever something changes that could affect the
 * reminder (settings toggled, threshold edited, backup recorded, app
 * launched). It posts/cancels the notification synchronously and
 * enqueues (or replaces) a single OneTimeWorkRequest as the next
 * scheduled check.
 *
 * Battery cost is essentially zero: WorkManager hands the job to the OS
 * scheduler, which batches it with other deferred work in the next Doze
 * maintenance window. Nothing of ours runs in between.
 */
@Singleton
class BackupReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val notifier: BackupReminderNotifier,
) {
    /**
     * Re-evaluate the reminder right now (post or cancel the notification)
     * and schedule the next background check. Idempotent — safe to call
     * from multiple triggers (settings change, backup recorded, app
     * launch, the worker itself).
     */
    suspend fun refresh() {
        notifier.refresh()
        reschedule()
    }

    /**
     * Plan the next [BackupReminderWorker] firing based on the current
     * state. The cases handled:
     *  - reminder disabled / system notify off → cancel any pending work.
     *  - never backed up → fire one threshold-window from now.
     *  - already due → fire again in 24 h to keep nagging daily until the
     *    user backs up (which cancels the chain via [refresh]).
     *  - not yet due → fire at the exact due-by timestamp.
     */
    private suspend fun reschedule() {
        val workManager = WorkManager.getInstance(context)
        val snapshot = appPreferences.snapshot()
        val reminder = snapshot.reminder
        if (!reminder.enabled || !reminder.notifyEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = System.currentTimeMillis()
        val thresholdMs = TimeUnit.DAYS.toMillis(reminder.thresholdDays)
        val lastBackupAt = snapshot.lastBackupAt
        val targetMs = when {
            lastBackupAt == null -> now + thresholdMs
            now >= lastBackupAt + thresholdMs -> now + ONE_DAY_MS
            else -> lastBackupAt + thresholdMs
        }
        val delayMs = (targetMs - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<BackupReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        // REPLACE so a fresh refresh() always wins over a stale pending
        // worker — e.g. user shortens the threshold from 30 to 7 days.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        /** Unique-work name lets us replace an existing pending check
         *  rather than stacking up a worker per trigger. */
        const val WORK_NAME = "evsct_backup_reminder_check"
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
