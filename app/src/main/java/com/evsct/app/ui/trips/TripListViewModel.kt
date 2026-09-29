package com.evsct.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.VehicleScope
import com.evsct.app.ui.orAllIfEmpty
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripListUi(
    /** The trips in [vehicleScope], newest first. */
    val trips: List<TripWithStats> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleScope: VehicleScope = VehicleScope.All,
    /** True when any trip has no vehicle — the Unassigned tab's bucket.
     *  Computed off every trip, like the Log's hasUnassignedSessions. */
    val hasUnassignedTrips: Boolean = false,
    /** The car a new trip starts on: the vehicle tab being viewed, else the
     *  default vehicle (the dialog falls back to a lone vehicle itself). */
    val newTripVehicleId: Long? = null,
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val vehicleScopeFlow = MutableStateFlow<VehicleScope>(VehicleScope.All)

    val state: StateFlow<TripListUi> =
        combine(
            tripRepository.observeAllWithStats(),
            vehicleRepository.observeAll(),
            vehicleScopeFlow,
        ) { allTrips, vehicles, scope ->
            val hasUnassigned = allTrips.any { it.trip.vehicleId == null }
            val effectiveScope = scope.orAllIfEmpty(vehicles, hasUnassigned)
            TripListUi(
                trips = allTrips.filter { effectiveScope.matchesVehicleId(it.trip.vehicleId) },
                vehicles = vehicles,
                vehicleScope = effectiveScope,
                hasUnassignedTrips = hasUnassigned,
                newTripVehicleId = (effectiveScope as? VehicleScope.One)?.id
                    ?: vehicles.firstOrNull { it.isDefault }?.id,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUi())

    fun setVehicleScope(scope: VehicleScope) {
        vehicleScopeFlow.value = scope
    }

    fun upsert(trip: Trip) = viewModelScope.launch {
        if (trip.name.isNotBlank()) tripRepository.upsert(trip)
    }

    fun delete(trip: Trip) = viewModelScope.launch {
        tripRepository.delete(trip)
    }
}
