package com.evsct.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

        /** Days since last backup that triggers the "back up?" nudge. */
        const val BACKUP_NUDGE_THRESHOLD_DAYS: Long = 30

        /** Minimum sessions before nudging users who've never backed up. */
        const val BACKUP_NUDGE_MIN_SESSIONS: Int = 5
    }

    val lastBackupAt: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[LAST_BACKUP_AT]?.takeIf { it > 0 }
    }

    suspend fun recordBackup(epochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_BACKUP_AT] = epochMillis }
    }
}
