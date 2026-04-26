package com.evsct.app.ui.vehicles

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.VehicleImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleEditUi(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val year: String = "",
    val make: String = "",
    val model: String = "",
    val trim: String = "",
    val batteryKwh: String = "",
    val rangeKm: String = "",
    val vin: String = "",
    val notes: String = "",
    val isDefault: Boolean = false,
    val imagePath: String? = null,
)

@HiltViewModel
class VehicleEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VehicleRepository,
    private val imageStore: VehicleImageStore,
) : ViewModel() {

    private val vehicleId: Long = savedStateHandle.get<Long>(Routes.VEHICLE_EDIT_ARG) ?: -1L

    private val _state = MutableStateFlow(VehicleEditUi())
    val state: StateFlow<VehicleEditUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (vehicleId > 0) {
                val v = repository.findById(vehicleId)
                if (v != null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isNew = false,
                            name = v.name,
                            year = v.year?.toString().orEmpty(),
                            make = v.make.orEmpty(),
                            model = v.model.orEmpty(),
                            trim = v.trim.orEmpty(),
                            batteryKwh = v.batteryCapacityKwh?.toString().orEmpty(),
                            rangeKm = v.nominalRangeKm?.toString().orEmpty(),
                            vin = v.vin.orEmpty(),
                            notes = v.notes.orEmpty(),
                            isDefault = v.isDefault,
                            imagePath = v.imagePath,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, isNew = true) }
                }
            } else {
                val noDefaultExists = repository.findDefault() == null
                _state.update { it.copy(isLoading = false, isNew = true, isDefault = noDefaultExists) }
            }
        }
    }

    fun update(transform: (VehicleEditUi) -> VehicleEditUi) = _state.update(transform)

    fun pickImage(uri: Uri) {
        viewModelScope.launch {
            val previous = _state.value.imagePath
            val path = imageStore.copyFromUri(uri)
            _state.update { it.copy(imagePath = path) }
            if (previous != null) imageStore.delete(previous)
        }
    }

    fun clearImage() {
        viewModelScope.launch {
            val previous = _state.value.imagePath
            _state.update { it.copy(imagePath = null) }
            if (previous != null) imageStore.delete(previous)
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val vehicle = Vehicle(
                id = if (s.isNew) 0 else vehicleId,
                name = s.name.ifBlank {
                    listOfNotNull(
                        s.year.takeIf { it.isNotBlank() },
                        s.make.takeIf { it.isNotBlank() },
                        s.model.takeIf { it.isNotBlank() },
                    ).joinToString(" ").ifBlank { "Vehicle" }
                },
                year = s.year.toIntOrNull(),
                make = s.make.takeIf { it.isNotBlank() },
                model = s.model.takeIf { it.isNotBlank() },
                trim = s.trim.takeIf { it.isNotBlank() },
                batteryCapacityKwh = s.batteryKwh.toDoubleOrNull(),
                nominalRangeKm = s.rangeKm.toIntOrNull(),
                vin = s.vin.takeIf { it.isNotBlank() },
                notes = s.notes.takeIf { it.isNotBlank() },
                imagePath = s.imagePath,
                isDefault = s.isDefault,
            )
            repository.upsert(vehicle)
            onSaved()
        }
    }

    fun deleteAndExit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (vehicleId > 0) {
                repository.findById(vehicleId)?.let {
                    repository.delete(it)
                    imageStore.delete(it.imagePath)
                }
            }
            onDeleted()
        }
    }
}
