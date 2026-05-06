package com.evsct.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

data class UserUnits(
    val useMiles: Boolean = false,
    val defaultCurrency: String = "CAD",
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
        private val USE_MILES = booleanPreferencesKey("use_miles")
        private val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        /** Epoch millis of the last attempted map address-backfill geocode
         *  pass. Used to throttle re-runs across process death so we don't
         *  re-geocode the same unresolvable addresses every cold start. */
        private val LAST_MAP_BACKFILL_AT = longPreferencesKey("last_map_backfill_at")

        val SUPPORTED_CURRENCIES = listOf("CAD", "USD")

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

    val userUnits: Flow<UserUnits> = dataStore.data.map { prefs ->
        UserUnits(
            useMiles = prefs[USE_MILES] ?: false,
            defaultCurrency = prefs[DEFAULT_CURRENCY]
                ?.takeIf { it in SUPPORTED_CURRENCIES }
                ?: "CAD",
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

    suspend fun setUseMiles(useMiles: Boolean) {
        dataStore.edit { it[USE_MILES] = useMiles }
    }

    suspend fun setDefaultCurrency(currency: String) {
        if (currency !in SUPPORTED_CURRENCIES) return
        dataStore.edit { it[DEFAULT_CURRENCY] = currency }
    }

    /** Most recent epoch millis the map screen attempted its address-backfill
     *  geocode pass, regardless of how many addresses succeeded. */
    suspend fun lastMapBackfillAt(): Long? =
        dataStore.data.first()[LAST_MAP_BACKFILL_AT]?.takeIf { it > 0 }

    suspend fun recordMapBackfillAttempt(epochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_MAP_BACKFILL_AT] = epochMillis }
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
