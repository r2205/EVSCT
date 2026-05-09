package com.evsct.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.backup.BackupIo
import com.evsct.app.data.backup.BackupResult
import com.evsct.app.data.backup.PrepareShareResult
import com.evsct.app.data.csv.CsvImportResult
import com.evsct.app.data.csv.CsvIo
import com.evsct.app.data.csv.XlsxImportResult
import com.evsct.app.data.csv.XlsxImporter
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.BackupReminderSettings
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.util.BackupReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val busy: Boolean = false,
    val message: String? = null,
    val reminder: BackupReminderSettings = BackupReminderSettings(),
    val units: UserUnits = UserUnits(),
    /** Theme override (SYSTEM / LIGHT / DARK) — resolved at the activity
     *  level so a change here applies app-wide on the next recomposition. */
    val themeMode: String = "SYSTEM",
    /** A backup zip that was just written to cacheDir and is ready to hand
     *  to ACTION_SEND. The screen launches the chooser, then clears this
     *  via [consumePendingShare]. */
    val pendingShareFile: File? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val csvIo: CsvIo,
    private val xlsxImporter: XlsxImporter,
    private val backupIo: BackupIo,
    private val appPreferences: AppPreferences,
    private val backupReminderScheduler: BackupReminderScheduler,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUi())

    val state: StateFlow<SettingsUi> =
        combine(
            transient,
            appPreferences.reminderSettings,
            appPreferences.userUnits,
            appPreferences.themeMode,
        ) { ui, reminder, units, themeMode ->
            ui.copy(reminder = reminder, units = units, themeMode = themeMode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setUseMiles(useMiles: Boolean) = viewModelScope.launch {
        appPreferences.setUseMiles(useMiles)
    }

    fun setDefaultCurrency(currency: String) = viewModelScope.launch {
        appPreferences.setDefaultCurrency(currency)
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        appPreferences.setThemeMode(mode)
    }

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setReminderEnabled(enabled)
        backupReminderScheduler.refresh()
    }

    fun setReminderThresholdDays(days: Long) = viewModelScope.launch {
        appPreferences.setReminderThresholdDays(days)
        backupReminderScheduler.refresh()
    }

    fun setReminderNotifyEnabled(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setReminderNotifyEnabled(enabled)
        backupReminderScheduler.refresh()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching { csvIo.export(uri) }
            .onSuccess { count ->
                transient.update { it.copy(busy = false, message = "Exported $count sessions to CSV.") }
            }
            .onFailure { e ->
                transient.update { it.copy(busy = false, message = "Export failed: ${e.message}") }
            }
    }

    fun importCsv(uri: Uri, replaceExisting: Boolean) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching { csvIo.import(uri, replaceExisting) }
            .onSuccess { (imported, skipped): CsvImportResult ->
                transient.update {
                    it.copy(busy = false, message = "Imported $imported sessions (skipped $skipped).")
                }
            }
            .onFailure { e ->
                transient.update { it.copy(busy = false, message = "Import failed: ${e.message}") }
            }
    }

    fun importXlsx(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        runCatching { xlsxImporter.import(uri) }
            .onSuccess { result: XlsxImportResult ->
                transient.update {
                    it.copy(
                        busy = false,
                        message = "Imported ${result.imported} sessions from XLSX (skipped ${result.skipped}).",
                    )
                }
            }
            .onFailure { e ->
                transient.update { it.copy(busy = false, message = "XLSX import failed: ${e.message}") }
            }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        when (val result = backupIo.export(uri)) {
            is BackupResult.ExportSuccess -> transient.update {
                it.copy(
                    busy = false,
                    message = "Backup written: ${result.sessions} sessions, " +
                        "${result.trips} trips, ${result.vehicles} vehicles.",
                )
            }
            is BackupResult.Failure -> transient.update {
                it.copy(busy = false, message = "Backup failed: ${result.message}")
            }
            else -> transient.update { it.copy(busy = false) }
        }
    }

    fun shareBackup() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        when (val result = backupIo.prepareShareFile()) {
            is PrepareShareResult.Success -> transient.update {
                it.copy(
                    busy = false,
                    pendingShareFile = result.prepared.file,
                    // Don't post the success message yet — surface it after
                    // the chooser is launched so the dialog doesn't fight
                    // the system share sheet for focus.
                )
            }
            is PrepareShareResult.Failure -> transient.update {
                it.copy(busy = false, message = "Backup failed: ${result.message}")
            }
        }
    }

    /** Called once the screen has dispatched the share chooser intent so
     *  the LaunchedEffect that watches pendingShareFile doesn't re-fire. */
    fun consumePendingShare() = transient.update { it.copy(pendingShareFile = null) }

    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        when (val result = backupIo.restore(uri)) {
            is BackupResult.RestoreSuccess -> transient.update {
                it.copy(
                    busy = false,
                    message = "Restored ${result.sessions} sessions, " +
                        "${result.trips} trips, ${result.vehicles} vehicles.",
                )
            }
            is BackupResult.Failure -> transient.update {
                it.copy(busy = false, message = "Restore failed: ${result.message}")
            }
            else -> transient.update { it.copy(busy = false) }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null) }
}
