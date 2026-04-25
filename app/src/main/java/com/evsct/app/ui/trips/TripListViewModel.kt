package com.evsct.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import com.evsct.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    val trips: StateFlow<List<TripWithStats>> =
        tripRepository.observeAllWithStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) tripRepository.upsert(Trip(name = name.trim()))
    }

    fun delete(trip: Trip) = viewModelScope.launch {
        tripRepository.delete(trip)
    }
}
