package com.evsct.app.ui.vehicles

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.di.AppScope
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.Units
import com.evsct.app.util.VehicleImageStore
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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
    /** Range entered in the user's preferred distance unit. Stored canonical
     *  km on save. */
    val rangeText: String = "",
    val useMiles: Boolean = false,
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
    private val appPreferences: AppPreferences,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val vehicleId: Long = savedStateHandle.get<Long>(Routes.VEHICLE_EDIT_ARG) ?: -1L

    private val _state = MutableStateFlow(VehicleEditUi())
    val state: StateFlow<VehicleEditUi> = _state.asStateFlow()

    /** See SessionEditViewModel for the same pattern — file deletes are
     *  deferred to save() so backing out without saving doesn't strand the
     *  database row pointing at a missing file. */
    private var originalImagePath: String? = null
    private val touchedImagePaths = mutableSetOf<String>()
    @Volatile private var imageCleanupHandled = false

    init {
        viewModelScope.launch {
            val units = appPreferences.userUnits.first()
            if (vehicleId > 0) {
                val v = repository.findById(vehicleId)
                if (v != null) {
                    originalImagePath = v.imagePath
                    v.imagePath?.let { touchedImagePaths += it }
                    val rangeText = v.nominalRangeKm?.let {
                        Units.kmToDisplay(it.toDouble(), units.useMiles).roundToInt().toString()
                    }.orEmpty()
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
                            rangeText = rangeText,
                            useMiles = units.useMiles,
                            vin = v.vin.orEmpty(),
                            notes = v.notes.orEmpty(),
                            isDefault = v.isDefault,
                            imagePath = v.imagePath,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, isNew = true, useMiles = units.useMiles) }
                }
            } else {
                val noDefaultExists = repository.findDefault() == null
                _state.update {
                    it.copy(
                        isLoading = false,
                        isNew = true,
                        isDefault = noDefaultExists,
                        useMiles = units.useMiles,
                    )
                }
            }
        }
    }

    fun update(transform: (VehicleEditUi) -> VehicleEditUi) = _state.update(transform)

    fun pickImage(uri: Uri) {
        viewModelScope.launch {
            // Defer deletion of the previous file until save() commits. If the
            // user backs out the database row keeps referencing the original.
            val path = imageStore.copyFromUri(uri)
            touchedImagePaths += path
            _state.update { it.copy(imagePath = path) }
        }
    }

    fun clearImage() {
        _state.update { it.copy(imagePath = null) }
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
                nominalRangeKm = s.rangeText.toDoubleOrNull()?.let {
                    Units.displayToKm(it, s.useMiles).roundToInt()
                },
                vin = s.vin.takeIf { it.isNotBlank() },
                notes = s.notes.takeIf { it.isNotBlank() },
                imagePath = s.imagePath,
                isDefault = s.isDefault,
            )
            repository.upsert(vehicle)
            reconcileImageFiles(finalPath = vehicle.imagePath)
            onSaved()
        }
    }

    fun deleteAndExit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (vehicleId > 0) {
                repository.findById(vehicleId)?.let {
                    repository.delete(it)
                }
            }
            reconcileImageFiles(finalPath = null)
            onDeleted()
        }
    }

    /** Drop every image file we touched during this edit except [finalPath]. */
    private suspend fun reconcileImageFiles(finalPath: String?) {
        imageCleanupHandled = true
        val toDelete = touchedImagePaths.filter { it != finalPath }
        toDelete.forEach { imageStore.delete(it) }
    }

    override fun onCleared() {
        super.onCleared()
        if (imageCleanupHandled) return
        // User backed out without saving. Drop only the speculative copies;
        // the database row (if any) still references originalImagePath.
        val orphans = touchedImagePaths.filter { it != originalImagePath }
        if (orphans.isEmpty()) return
        appScope.launch {
            orphans.forEach { imageStore.delete(it) }
        }
    }
}
