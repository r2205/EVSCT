package com.evsct.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.backup.BackupIo
import com.evsct.app.data.backup.BackupResult
import com.evsct.app.data.csv.CsvImportResult
import com.evsct.app.data.csv.CsvIo
import com.evsct.app.data.csv.XlsxImportResult
import com.evsct.app.data.csv.XlsxImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val csvIo: CsvIo,
    private val xlsxImporter: XlsxImporter,
    private val backupIo: BackupIo,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUi())
    val state: StateFlow<SettingsUi> = _state.asStateFlow()

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        runCatching { csvIo.export(uri) }
            .onSuccess { count ->
                _state.update { it.copy(busy = false, message = "Exported $count sessions to CSV.") }
            }
            .onFailure { e ->
                _state.update { it.copy(busy = false, message = "Export failed: ${e.message}") }
            }
    }

    fun importCsv(uri: Uri, replaceExisting: Boolean) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        runCatching { csvIo.import(uri, replaceExisting) }
            .onSuccess { (imported, skipped): CsvImportResult ->
                _state.update {
                    it.copy(busy = false, message = "Imported $imported sessions (skipped $skipped).")
                }
            }
            .onFailure { e ->
                _state.update { it.copy(busy = false, message = "Import failed: ${e.message}") }
            }
    }

    fun importXlsx(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        runCatching { xlsxImporter.import(uri) }
            .onSuccess { result: XlsxImportResult ->
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Imported ${result.imported} sessions from XLSX (skipped ${result.skipped}).",
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(busy = false, message = "XLSX import failed: ${e.message}") }
            }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        when (val result = backupIo.export(uri)) {
            is BackupResult.ExportSuccess -> _state.update {
                it.copy(
                    busy = false,
                    message = "Backup written: ${result.sessions} sessions, " +
                        "${result.trips} trips, ${result.vehicles} vehicles.",
                )
            }
            is BackupResult.Failure -> _state.update {
                it.copy(busy = false, message = "Backup failed: ${result.message}")
            }
            else -> _state.update { it.copy(busy = false) }
        }
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        when (val result = backupIo.restore(uri)) {
            is BackupResult.RestoreSuccess -> _state.update {
                it.copy(
                    busy = false,
                    message = "Restored ${result.sessions} sessions, " +
                        "${result.trips} trips, ${result.vehicles} vehicles.",
                )
            }
            is BackupResult.Failure -> _state.update {
                it.copy(busy = false, message = "Restore failed: ${result.message}")
            }
            else -> _state.update { it.copy(busy = false) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
