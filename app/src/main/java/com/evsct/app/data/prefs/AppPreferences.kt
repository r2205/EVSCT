package com.evsct.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BackupReminderSettings(
    val enabled: Boolean = true,
    val thresholdDays: Long = AppPreferences.DEFAULT_THRESHOLD_DAYS,
    val notifyEnabled: Boolean = false,
)

/**
 * Lightweight wrapper around the app's DataStore<Preferences> for the few
 * cross-screen flags that don't belong in the relational schema.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        /** Epoch millis of the last successful full-backup export. */
        private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        private val REMINDER_ENABLED = booleanPreferencesKey("backup_reminder_enabled")
        private val REMINDER_THRESHOLD_DAYS = longPreferencesKey("backup_reminder_threshold_days")
        private val REMINDER_NOTIFY = booleanPreferencesKey("backup_reminder_notify")

        const val DEFAULT_THRESHOLD_DAYS: Long = 30

        /** Minimum sessions before nudging users who've never backed up. */
        const val BACKUP_NUDGE_MIN_SESSIONS: Int = 5

        const val MIN_THRESHOLD_DAYS: Long = 1
        const val MAX_THRESHOLD_DAYS: Long = 365
    }

    val lastBackupAt: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[LAST_BACKUP_AT]?.takeIf { it > 0 }
    }

    val reminderSettings: Flow<BackupReminderSettings> = dataStore.data.map { prefs ->
        BackupReminderSettings(
            enabled = prefs[REMINDER_ENABLED] ?: true,
            thresholdDays = (prefs[REMINDER_THRESHOLD_DAYS] ?: DEFAULT_THRESHOLD_DAYS)
                .coerceIn(MIN_THRESHOLD_DAYS, MAX_THRESHOLD_DAYS),
            notifyEnabled = prefs[REMINDER_NOTIFY] ?: false,
        )
    }

    suspend fun recordBackup(epochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_BACKUP_AT] = epochMillis }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderThresholdDays(days: Long) {
        val clamped = days.coerceIn(MIN_THRESHOLD_DAYS, MAX_THRESHOLD_DAYS)
        dataStore.edit { it[REMINDER_THRESHOLD_DAYS] = clamped }
    }

    suspend fun setReminderNotifyEnabled(notify: Boolean) {
        dataStore.edit { it[REMINDER_NOTIFY] = notify }
    }

    suspend fun snapshot(): Snapshot {
        val prefs = dataStore.data.first()
        return Snapshot(
            lastBackupAt = prefs[LAST_BACKUP_AT]?.takeIf { it > 0 },
            reminder = BackupReminderSettings(
                enabled = prefs[REMINDER_ENABLED] ?: true,
                thresholdDays = (prefs[REMINDER_THRESHOLD_DAYS] ?: DEFAULT_THRESHOLD_DAYS)
                    .coerceIn(MIN_THRESHOLD_DAYS, MAX_THRESHOLD_DAYS),
                notifyEnabled = prefs[REMINDER_NOTIFY] ?: false,
            ),
        )
    }

    data class Snapshot(
        val lastBackupAt: Long?,
        val reminder: BackupReminderSettings,
    )
}
