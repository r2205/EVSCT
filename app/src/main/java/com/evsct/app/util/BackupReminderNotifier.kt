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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
        val daysSince = if (lastBackupAt == null) null
        else (System.currentTimeMillis() - lastBackupAt) / 86_400_000L
        val shouldShow = lastBackupAt != null && daysSince != null &&
            daysSince >= reminder.thresholdDays
        if (shouldShow) post(daysSince) else cancel()
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun post(daysSince: Long) {
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Back up your EVSCT data")
            .setContentText("It's been $daysSince days since your last backup.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "It's been $daysSince days since your last full backup. " +
                        "Open EVSCT and export a backup to keep your sessions safe."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
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
