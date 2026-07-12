package com.evsct.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.util.LocationAutofill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Small view-model for the location picker: the persisted map-type pref
 * (same DataStore key as the main map, so toggling on either screen flips
 * both) plus the location plumbing behind the my-location button and the
 * open-where-I-am camera seed.
 */
@HiltViewModel
class MapPickerViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val locationAutofill: LocationAutofill,
) : ViewModel() {

    val mapType: StateFlow<String> = appPreferences.mapType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "NORMAL")

    fun setMapType(type: String) {
        viewModelScope.launch { appPreferences.setMapType(type) }
    }

    /** Whether the app currently holds a location permission. */
    fun hasLocationPermission(): Boolean = locationAutofill.hasPermission()

    /** Device's current coordinates, or null when permission/provider/fix
     *  is unavailable. */
    suspend fun currentLatLng(): Pair<Double, Double>? = locationAutofill.currentLatLng()
}
