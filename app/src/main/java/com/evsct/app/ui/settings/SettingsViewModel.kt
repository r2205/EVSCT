package com.evsct.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.backup.BackupIo
import com.evsct.app.data.backup.BackupResult
import com.evsct.app.data.backup.PrepareShareResult
import com.evsct.app.data.csv.CsvImportResult
import com.evsct.app.data.csv.CsvIo
import com.evsct.app.data.csv.PrepareCsvShareResult
import com.evsct.app.data.csv.XlsxImportResult
import com.evsct.app.data.csv.XlsxImporter
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.BackupReminderSettings
import com.evsct.app.data.prefs.CardTimeRate
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.ui.OpFeedback
import com.evsct.app.util.BackupReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which long-running operation is in flight, so the screen can show the
 *  spinner ON the button that started it — a shared progress bar at the
 *  top of the scrolling column is off-screen exactly when the user tapped
 *  one of the lower cards. */
enum class SettingsOp {
    EXPORT_BACKUP, SHARE_BACKUP, RESTORE, UNDO_RESTORE,
    EXPORT_CSV, SHARE_CSV, IMPORT_CSV, IMPORT_XLSX,
}

data class SettingsUi(
    val busyOp: SettingsOp? = null,
    val feedback: OpFeedback? = null,
    val reminder: BackupReminderSettings = BackupReminderSettings(),
    val units: UserUnits = UserUnits(),
    /** Theme override (SYSTEM / LIGHT / DARK) — resolved at the activity
     *  level so a change here applies app-wide on the next recomposition. */
    val themeMode: String = "SYSTEM",
    /** A backup zip that was just written to cacheDir and is ready to hand
     *  to ACTION_SEND. The screen launches the chooser, then clears this
     *  via [consumePendingShare]. */
    val pendingShareFile: File? = null,
    /** Epoch millis of the last recorded backup (Save completed, share
     *  target picked, or restore) — null when never backed up. Rendered
     *  in the Full backup card so backup hygiene is visible without
     *  waiting for the reminder to fire. */
    val lastBackupAt: Long? = null,
    /** Epoch millis of the automatic pre-restore safety snapshot — null
     *  when no restore has ever run. Non-null shows the "Undo last
     *  restore" row. */
    val preRestoreSnapshotAt: Long? = null,
) {
    val busy: Boolean get() = busyOp != null
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val csvIo: CsvIo,
    private val xlsxImporter: XlsxImporter,
    private val backupIo: BackupIo,
    private val appPreferences: AppPreferences,
    private val backupReminderScheduler: BackupReminderScheduler,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUi())

    init {
        refreshSnapshotInfo()
    }

    val state: StateFlow<SettingsUi> =
        combine(
            transient,
            appPreferences.reminderSettings,
            appPreferences.userUnits,
            appPreferences.themeMode,
            appPreferences.lastBackupAt,
        ) { ui, reminder, units, themeMode, lastBackupAt ->
            ui.copy(
                reminder = reminder,
                units = units,
                themeMode = themeMode,
                lastBackupAt = lastBackupAt,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    /** The snapshot timestamp lives on disk, not in a Flow — re-read it on
     *  screen load and after every restore-shaped operation. */
    private fun refreshSnapshotInfo() = viewModelScope.launch {
        val at = withContext(Dispatchers.IO) { backupIo.preRestoreSnapshotAt() }
        transient.update { it.copy(preRestoreSnapshotAt = at) }
    }

    fun setUseMiles(useMiles: Boolean) = viewModelScope.launch {
        appPreferences.setUseMiles(useMiles)
    }

    fun setDefaultCurrency(currency: String) = viewModelScope.launch {
        appPreferences.setDefaultCurrency(currency)
    }

    fun setCardTimeRate(mode: CardTimeRate) = viewModelScope.launch {
        appPreferences.setCardTimeRate(mode)
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

    /** Human-first failure copy: a plain sentence up front, the raw
     *  exception detail in parentheses for bug reports. */
    private fun humanFailure(what: String, detail: String?): String =
        "$what." + (detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")

    private fun finish(feedback: OpFeedback?) =
        transient.update { it.copy(busyOp = null, feedback = feedback) }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.EXPORT_CSV, feedback = null) }
        runCatching { csvIo.export(uri) }
            .onSuccess { count ->
                finish(OpFeedback("CSV exported", "Exported $count sessions to CSV."))
            }
            .onFailure { e ->
                finish(OpFeedback(
                    title = "CSV export failed",
                    body = humanFailure("The file couldn't be written", e.message),
                    isError = true,
                ))
            }
    }

    fun shareCsv() = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.SHARE_CSV, feedback = null) }
        when (val result = csvIo.prepareShareFile()) {
            is PrepareCsvShareResult.Success -> transient.update {
                it.copy(busyOp = null, pendingShareFile = result.prepared.file)
            }
            is PrepareCsvShareResult.Failure -> finish(OpFeedback(
                title = "CSV share failed",
                body = humanFailure("The share file couldn't be prepared", result.message),
                isError = true,
            ))
        }
    }

    fun importCsv(uri: Uri, replaceExisting: Boolean) = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.IMPORT_CSV, feedback = null) }
        if (replaceExisting) {
            // Same safety net as restore: snapshot the current data before
            // the wipe so a wrong file is recoverable via "Undo" in the
            // Full backup card. A snapshot failure aborts the import.
            val snapshotError = backupIo.snapshotBeforeReplace()
            if (snapshotError != null) {
                finish(OpFeedback("Import cancelled", snapshotError, isError = true))
                refreshSnapshotInfo()
                return@launch
            }
        }
        runCatching { csvIo.import(uri, replaceExisting) }
            .onSuccess { (imported, skipped): CsvImportResult ->
                if (replaceExisting) {
                    // A wipe-and-replace deserves acknowledgement, and the
                    // undo pointer belongs right in the result.
                    finish(OpFeedback(
                        title = "Import complete",
                        body = "Replaced your log with $imported sessions from " +
                            "the CSV (skipped $skipped rows). Your previous data " +
                            "was snapshotted first — \"Undo last restore or " +
                            "import\" on this screen brings it back.",
                        asDialog = true,
                    ))
                } else {
                    finish(OpFeedback(
                        title = "Import complete",
                        body = "Imported $imported sessions (skipped $skipped).",
                    ))
                }
            }
            .onFailure { e ->
                finish(OpFeedback(
                    title = "Import failed",
                    body = humanFailure(
                        "The CSV couldn't be imported — is it an EVSCT export?",
                        e.message,
                    ),
                    isError = true,
                ))
            }
        if (replaceExisting) refreshSnapshotInfo()
    }

    fun importXlsx(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.IMPORT_XLSX, feedback = null) }
        runCatching { xlsxImporter.import(uri) }
            .onSuccess { result: XlsxImportResult ->
                finish(OpFeedback(
                    title = "XLSX import complete",
                    body = "Imported ${result.imported} sessions from XLSX " +
                        "(skipped ${result.skipped}).",
                ))
            }
            .onFailure { e ->
                finish(OpFeedback(
                    title = "XLSX import failed",
                    body = humanFailure("The spreadsheet couldn't be read", e.message),
                    isError = true,
                ))
            }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.EXPORT_BACKUP, feedback = null) }
        when (val result = backupIo.export(uri)) {
            is BackupResult.ExportSuccess -> finish(OpFeedback(
                title = "Backup saved",
                body = "Backup written: ${result.sessions} sessions, " +
                    "${result.trips} trips, ${result.vehicles} vehicles.",
            ))
            is BackupResult.Failure -> finish(OpFeedback(
                title = "Backup failed",
                body = humanFailure("The backup couldn't be written", result.message),
                isError = true,
            ))
            else -> finish(feedback = null)
        }
    }

    fun shareBackup() = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.SHARE_BACKUP, feedback = null) }
        when (val result = backupIo.prepareShareFile()) {
            is PrepareShareResult.Success -> transient.update {
                it.copy(
                    busyOp = null,
                    pendingShareFile = result.prepared.file,
                    // No success feedback — the system share sheet takes
                    // over the screen immediately.
                )
            }
            is PrepareShareResult.Failure -> finish(OpFeedback(
                title = "Backup share failed",
                body = humanFailure("The share file couldn't be prepared", result.message),
                isError = true,
            ))
        }
    }

    /** Called once the screen has dispatched the share chooser intent so
     *  the LaunchedEffect that watches pendingShareFile doesn't re-fire. */
    fun consumePendingShare() = transient.update { it.copy(pendingShareFile = null) }

    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.RESTORE, feedback = null) }
        when (val result = backupIo.restore(uri)) {
            is BackupResult.RestoreSuccess -> finish(OpFeedback(
                title = "Restore complete",
                body = "Restored ${result.sessions} sessions, ${result.trips} " +
                    "trips, ${result.vehicles} vehicles. Your previous data " +
                    "was snapshotted first — \"Undo last restore or import\" " +
                    "on this screen brings it back.",
                asDialog = true,
            ))
            is BackupResult.Failure -> finish(OpFeedback(
                // BackupIo's refusal messages are already written for
                // humans ("entry 2 of 312 in sessions…"); pass them through.
                title = "Restore failed",
                body = result.message,
                isError = true,
            ))
            else -> finish(feedback = null)
        }
        refreshSnapshotInfo()
    }

    /** Restore the automatic snapshot taken just before the last restore or
     *  replace-import — recovery for "that was the wrong file". */
    fun undoRestore() = viewModelScope.launch {
        transient.update { it.copy(busyOp = SettingsOp.UNDO_RESTORE, feedback = null) }
        when (val result = backupIo.restoreFromSnapshot()) {
            is BackupResult.RestoreSuccess -> finish(OpFeedback(
                title = "Undo complete",
                body = "Brought back ${result.sessions} sessions, " +
                    "${result.trips} trips, ${result.vehicles} vehicles from " +
                    "the snapshot. The data you just replaced was snapshotted " +
                    "too, so the undo is itself undoable.",
                asDialog = true,
            ))
            is BackupResult.Failure -> finish(OpFeedback(
                title = "Undo failed",
                body = result.message,
                isError = true,
            ))
            else -> finish(feedback = null)
        }
        refreshSnapshotInfo()
    }

    fun clearFeedback() = transient.update { it.copy(feedback = null) }
}
