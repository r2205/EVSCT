package com.evsct.app.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.util.VehicleImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val imageStore: VehicleImageStore,
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(vehicle: Vehicle) = viewModelScope.launch {
        repository.delete(vehicle)
        imageStore.delete(vehicle.imagePath)
    }
}
