package com.evsct.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tiny view-model for the location picker — exposes only the persisted
 * map-type pref so the picker can show the same layers menu (default /
 * satellite / hybrid / terrain) as the main map. Reads and writes go
 * through the same DataStore key, so toggling on either screen flips both.
 */
@HiltViewModel
class MapPickerViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val mapType: StateFlow<String> = appPreferences.mapType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "NORMAL")

    fun setMapType(type: String) {
        viewModelScope.launch { appPreferences.setMapType(type) }
    }
}
