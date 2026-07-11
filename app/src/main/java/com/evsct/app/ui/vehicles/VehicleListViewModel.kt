package com.evsct.app.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VehicleListViewModel @Inject constructor(
    repository: VehicleRepository,
) : ViewModel() {

    /** null until the first database emission lands, so the screen can show
     *  a loading indicator instead of flashing "No vehicles yet" over real
     *  data on every open. */
    val vehicles: StateFlow<List<Vehicle>?> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Deliberately no list-level delete. The edit screen's deleteAndExit
    // is the one delete path — it cleans up the profile image and any
    // files an edit session touched.
}
