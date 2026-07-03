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

/** How (and whether) the time-based cost rate is shown on session cards.
 *  PER_MINUTE / PER_HOUR pick the unit; OFF hides it entirely. */
enum class CardTimeRate { OFF, PER_MINUTE, PER_HOUR }

data class UserUnits(
    val useMiles: Boolean = false,
    val defaultCurrency: String = "CAD",
    val cardTimeRate: CardTimeRate = CardTimeRate.PER_MINUTE,
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
        /** [CardTimeRate] enum name controlling the time-cost rate shown on
         *  session cards. */
        private val CARD_TIME_RATE = stringPreferencesKey("card_time_rate")
        /** Epoch millis of the last attempted map address-backfill geocode
         *  pass. Used to throttle re-runs across process death so we don't
         *  re-geocode the same unresolvable addresses every cold start. */
        private val LAST_MAP_BACKFILL_AT = longPreferencesKey("last_map_backfill_at")
        /** Session id the in-progress charge notification is tracking.
         *  Mirrored from [com.evsct.app.util.InProgressChargeNotifier]'s
         *  in-memory state so process death doesn't orphan the ongoing
         *  notification — the notifier restores this at next startup. */
        private val TRACKED_CHARGE_SESSION_ID = longPreferencesKey("tracked_charge_session_id")
        /** User's preferred Google Maps base layer (NORMAL / SATELLITE /
         *  HYBRID / TERRAIN). Stored as the [com.google.maps.android.compose.MapType]
         *  enum name. */
        private val MAP_TYPE = stringPreferencesKey("map_type")
        /** App-wide theme override: SYSTEM follows the OS dark-mode setting,
         *  LIGHT and DARK force the corresponding palette. */
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        /** Whether the map view clusters nearby pins. When false, every pin
         *  renders individually regardless of zoom — useful when the user
         *  wants full visibility of every visited stop. */
        private val MAP_CLUSTERING_ENABLED = booleanPreferencesKey("map_clustering_enabled")
        /** Whether the map renders a density heatmap weighted by visit count
         *  instead of individual pins. Lives next to the basemap toggle in
         *  the layers menu — it's a display mode, not a filter. */
        private val MAP_HEATMAP_ENABLED = booleanPreferencesKey("map_heatmap_enabled")
        /** Whether the map draws colored polylines connecting consecutive
         *  same-trip sessions in chronological order. Sits next to the
         *  heatmap toggle in the layers menu. */
        private val MAP_POLYLINES_ENABLED = booleanPreferencesKey("map_polylines_enabled")

        val SUPPORTED_CURRENCIES = listOf("CAD", "USD")
        val SUPPORTED_MAP_TYPES = listOf("NORMAL", "SATELLITE", "HYBRID", "TERRAIN")
        val SUPPORTED_THEME_MODES = listOf("SYSTEM", "LIGHT", "DARK")

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
            cardTimeRate = prefs[CARD_TIME_RATE]
                ?.let { name -> runCatching { CardTimeRate.valueOf(name) }.getOrNull() }
                ?: CardTimeRate.PER_MINUTE,
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

    suspend fun setCardTimeRate(mode: CardTimeRate) {
        dataStore.edit { it[CARD_TIME_RATE] = mode.name }
    }

    /** Most recent epoch millis the map screen attempted its address-backfill
     *  geocode pass, regardless of how many addresses succeeded. */
    suspend fun lastMapBackfillAt(): Long? =
        dataStore.data.first()[LAST_MAP_BACKFILL_AT]?.takeIf { it > 0 }

    suspend fun recordMapBackfillAttempt(epochMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_MAP_BACKFILL_AT] = epochMillis }
    }

    /** Session id the in-progress charge notification was tracking, or null
     *  when no charge is being tracked. */
    suspend fun trackedChargeSessionId(): Long? =
        dataStore.data.first()[TRACKED_CHARGE_SESSION_ID]?.takeIf { it > 0 }

    /** Reactive variant of [trackedChargeSessionId] for UI that reacts when
     *  tracking starts or ends (the stale-tracking nudge on the log). */
    val trackedChargeSessionIdFlow: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[TRACKED_CHARGE_SESSION_ID]?.takeIf { it > 0 }
    }

    suspend fun setTrackedChargeSessionId(id: Long?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(TRACKED_CHARGE_SESSION_ID)
            else prefs[TRACKED_CHARGE_SESSION_ID] = id
        }
    }

    /** Selected Google Maps base layer. Falls back to NORMAL on unset or
     *  on any value the app doesn't recognise (e.g. older / forward-compat
     *  install with a value the current build doesn't support). */
    val mapType: Flow<String> = dataStore.data.map { prefs ->
        prefs[MAP_TYPE]?.takeIf { it in SUPPORTED_MAP_TYPES } ?: "NORMAL"
    }

    suspend fun setMapType(type: String) {
        if (type !in SUPPORTED_MAP_TYPES) return
        dataStore.edit { it[MAP_TYPE] = type }
    }

    /** Selected theme override. Defaults to SYSTEM (follow OS dark-mode). */
    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.takeIf { it in SUPPORTED_THEME_MODES } ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        if (mode !in SUPPORTED_THEME_MODES) return
        dataStore.edit { it[THEME_MODE] = mode }
    }

    /** Whether the map clusters nearby pins. Defaults to true. */
    val mapClusteringEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MAP_CLUSTERING_ENABLED] ?: true
    }

    suspend fun setMapClusteringEnabled(enabled: Boolean) {
        dataStore.edit { it[MAP_CLUSTERING_ENABLED] = enabled }
    }

    /** Whether the map paints a density heatmap instead of pins. Defaults
     *  to false so the first-open experience stays the familiar pin view. */
    val mapHeatmapEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MAP_HEATMAP_ENABLED] ?: false
    }

    suspend fun setMapHeatmapEnabled(enabled: Boolean) {
        dataStore.edit { it[MAP_HEATMAP_ENABLED] = enabled }
    }

    /** Whether the map draws colored trip-route polylines. Defaults to false
     *  so the first-open experience stays a clean pin map. */
    val mapPolylinesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[MAP_POLYLINES_ENABLED] ?: false
    }

    suspend fun setMapPolylinesEnabled(enabled: Boolean) {
        dataStore.edit { it[MAP_POLYLINES_ENABLED] = enabled }
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
