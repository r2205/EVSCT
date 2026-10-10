package com.evsct.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.evsct.app.MainActivity
import com.evsct.app.R
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Posts (or cancels) the "back up your data" Android notification based on
 * the user's reminder settings and the timestamp of the last backup. The
 * in-app banner on the session list mirrors the same logic; the notifier is
 * for nudging the user when they're not actively in the app.
 */
@Singleton
class BackupReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val sessionRepository: SessionRepository,
) {
    companion object {
        const val CHANNEL_ID = "backup_reminder"
        const val NOTIFICATION_ID = 1001
    }

    /** Re-evaluate reminder conditions and post or cancel the notification. */
    suspend fun refresh() {
        val snapshot = appPreferences.snapshot()
        val reminder = snapshot.reminder
        if (!reminder.enabled || !reminder.notifyEnabled) {
            cancel()
            return
        }
        if (!hasNotificationPermission()) {
            cancel()
            return
        }
        val lastBackupAt = snapshot.lastBackupAt
        if (lastBackupAt == null) {
            // Never backed up — the cohort a backup reminder exists for.
            // Mirror the in-app banner's rule: only nag once there's enough
            // data to be worth protecting, so fresh installs stay quiet.
            // (The scheduler explicitly arms a check for this case; without
            // this branch the worker chain re-armed forever without ever
            // posting anything.)
            val sessionCount = sessionRepository.observeAll().first().size
            if (sessionCount >= AppPreferences.BACKUP_NUDGE_MIN_SESSIONS) remind(daysSince = null)
            else cancel()
            return
        }
        val daysSince = (System.currentTimeMillis() - lastBackupAt) / 86_400_000L
        if (daysSince >= reminder.thresholdDays) remind(daysSince) else cancel()
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Show the reminder, paced to once a day. refresh() runs on every app
     * foregrounding, and posting each time sounded the channel again on
     * every return to the app, even coming back from the photo picker.
     * See [reminderAction].
     */
    private suspend fun remind(daysSince: Long?) {
        val now = System.currentTimeMillis()
        when (reminderAction(isShowing(), appPreferences.reminderLastPostedAt(), now)) {
            ReminderAction.REFRESH_SILENTLY -> post(daysSince, silently = true)
            ReminderAction.POST -> {
                post(daysSince, silently = false)
                appPreferences.recordReminderPosted(now)
            }
            ReminderAction.LEAVE_DISMISSED -> Unit
        }
    }

    private fun isShowing(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    /** [daysSince] is null for the never-backed-up nudge. [silently] keeps
     *  an update to a notification already in the shade from sounding. */
    private fun post(daysSince: Long?, silently: Boolean) {
        ensureChannel()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentText = if (daysSince == null) "Your charging log has never been backed up."
        else "It's been $daysSince days since your last backup."
        val bigText = (
            if (daysSince == null) "Your charging log has never been backed up. "
            else "It's been $daysSince days since your last full backup. "
            ) + "Open EVSCT and export a backup to keep your sessions safe."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Back up your EVSCT data")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silently)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and post; swallow.
        }
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Backup reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminds you to back up your charging-session data."
        }
        nm.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

internal enum class ReminderAction { POST, REFRESH_SILENTLY, LEAVE_DISMISSED }

/** How long a reminder the user dismissed (or tapped) stays away. Under a
 *  day so the scheduler's daily check, which can land a little early after
 *  Doze batching, still finds the reminder due. */
internal const val REMINDER_REPOST_INTERVAL_MS = 20L * 60 * 60 * 1000

/**
 * What an overdue reminder should do right now. Already in the shade: update
 * it without sound, so the day count stays current. Gone, and last posted
 * within [REMINDER_REPOST_INTERVAL_MS]: the user dismissed or tapped it
 * today, so leave it gone until the next daily nag. Otherwise: post, with
 * sound. File-level so a test can drive it without a NotificationManager.
 */
internal fun reminderAction(isShowing: Boolean, lastPostedAt: Long?, now: Long): ReminderAction = when {
    isShowing -> ReminderAction.REFRESH_SILENTLY
    lastPostedAt != null && now - lastPostedAt in 0 until REMINDER_REPOST_INTERVAL_MS ->
        ReminderAction.LEAVE_DISMISSED
    else -> ReminderAction.POST
}
