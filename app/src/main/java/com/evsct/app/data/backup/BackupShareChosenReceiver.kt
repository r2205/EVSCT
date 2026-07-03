package com.evsct.app.data.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.di.AppScope
import com.evsct.app.util.BackupReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Target of the share sheet's chosen-component callback for the backup
 * zip (see the chooser construction in SettingsScreen). The system fires
 * this exactly when the user picks a share target and never on cancel —
 * so this is the moment the backup plausibly left the device, and the
 * moment the "last backed up" timestamp is recorded and the reminder
 * cleared. Recording at prepare time instead would let a cancelled share
 * sheet silence the backup reminder for a full threshold period while
 * the zip only ever existed in app cache.
 */
@AndroidEntryPoint
class BackupShareChosenReceiver : BroadcastReceiver() {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // The DataStore write and WorkManager refresh are suspend calls;
        // goAsync keeps the receiver alive while they run off-main.
        val pending = goAsync()
        appScope.launch {
            try {
                appPreferences.recordBackup()
                backupReminderScheduler.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
