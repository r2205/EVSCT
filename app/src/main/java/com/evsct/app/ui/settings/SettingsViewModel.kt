package com.evsct.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun clearMessage() = _state.update { it.copy(message = null) }
}
