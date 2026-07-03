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
import com.evsct.app.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Posts (and updates and cancels) the persistent "Charging in progress"
 * notification used by the "Start charge" quick-track flow. Designed so the
 * user can plug in at a station, leave the app, and find a tap-shortcut back
 * to the in-progress session log on the notification shade — including on the
 * Android Auto shade when the phone is connected to a head unit.
 *
 * Holds the currently-tracked session id in memory so we can no-op redundant
 * updates and so [updateIfTracking] from the edit screen only acts when the
 * notification is actually live for the same session. The id is mirrored to
 * DataStore and restored (best-effort, async) at next startup, so process
 * death while a charge is tracked no longer orphans the ongoing
 * notification — without that, the post-kill save's [cancelIfFor] would
 * no-op and the setOngoing entry stayed stuck in the shade until the next
 * tracked charge.
 */
@Singleton
class InProgressChargeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    @AppScope private val appScope: CoroutineScope,
) {
    companion object {
        const val CHANNEL_ID = "charging_in_progress"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_OPEN_SESSION_ID = "open_session_id"
    }

    @Volatile private var trackedSessionId: Long? = null

    init {
        appScope.launch {
            val persisted = appPreferences.trackedChargeSessionId() ?: return@launch
            // A post() that raced ahead of this read wins; cancel() can't
            // race it because cancel paths require a non-null tracked id.
            if (trackedSessionId == null) trackedSessionId = persisted
        }
    }

    /** Begin (or refresh) the persistent notification for [sessionId]. Safe
     *  to call repeatedly with updated brand/city as the user types — the
     *  posted notification is just replaced. */
    fun post(sessionId: Long, brand: String?, city: String?, sessionStart: Long) {
        // Track first, notify second: the in-app tracking features (live
        // elapsed chip, elapsed-time fallback into the duration on save)
        // must work even when POST_NOTIFICATIONS is denied — the permission
        // only gates the shade shortcut.
        val idChanged = trackedSessionId != sessionId
        trackedSessionId = sessionId
        // The DataStore mirror exists for process-death recovery, so it
        // only needs writing when the tracked id CHANGES — re-persisting
        // the same id on every shade-text refresh was a disk commit per
        // brand/city keystroke while live-tracking.
        if (idChanged) persistTrackedId(sessionId)
        if (!hasNotificationPermission()) return
        ensureChannel()
        val notification = build(sessionId, brand, city, sessionStart)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and post; swallow.
        }
    }

    /** Update the live notification, but only if it's tracking [sessionId].
     *  Call from the edit screen's state-change hook so changing brand/city
     *  refreshes the shade text without revealing the notification for
     *  unrelated edits. */
    fun updateIfTracking(sessionId: Long, brand: String?, city: String?, sessionStart: Long) {
        if (trackedSessionId != sessionId) return
        post(sessionId, brand, city, sessionStart)
    }

    /** Drop the notification regardless of which session it's for. */
    fun cancel() {
        trackedSessionId = null
        persistTrackedId(null)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun persistTrackedId(id: Long?) {
        appScope.launch { appPreferences.setTrackedChargeSessionId(id) }
    }

    /** Drop the notification only when it's currently tracking [sessionId].
     *  Used by save() so completing some other session in the background
     *  doesn't accidentally clear an unrelated in-progress charge. */
    fun cancelIfFor(sessionId: Long) {
        if (trackedSessionId == sessionId) cancel()
    }

    fun isTrackingForSession(sessionId: Long): Boolean = trackedSessionId == sessionId

    private fun build(
        sessionId: Long,
        brand: String?,
        city: String?,
        sessionStart: Long,
    ): android.app.Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SESSION_ID, sessionId)
        }
        // Use the session id as the request code so PendingIntent.getActivity
        // returns a fresh per-session pending intent rather than reusing one
        // for a different session (and thus carrying its old extras).
        val pending = PendingIntent.getActivity(
            context,
            sessionId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = brand?.takeIf { it.isNotBlank() }
            ?.let { "Charging at $it" }
            ?: "Charging session in progress"
        val locationText = city?.takeIf { it.isNotBlank() }
        val tapHint = "Tap to add cost, kWh, etc."
        val text = locationText?.let { "$it · $tapHint" } ?: tapHint
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // Live elapsed-time stopwatch in the notification — much more
            // useful than a static "Started at HH:mm" because the user
            // glances at the shade to see how long they've been charging.
            .setUsesChronometer(true)
            .setWhen(sessionStart)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Charging in progress",
            // LOW: persistent shade entry, no sound, no heads-up. The user
            // explicitly opted in via "Start charge" — they don't need a
            // buzz, just a glanceable shortcut.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent shortcut back to a charge being logged."
            setShowBadge(false)
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
