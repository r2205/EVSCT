package com.evsct.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.UserUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * App-wide units and currency preferences exposed via CompositionLocal so any
 * composable can read them without threading them through every ViewModel.
 */
val LocalUserUnits = staticCompositionLocalOf { UserUnits() }

@HiltViewModel
class UserPrefsViewModel @Inject constructor(
    appPreferences: AppPreferences,
) : ViewModel() {
    val units: StateFlow<UserUnits> = appPreferences.userUnits
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserUnits())
}

@Composable
fun ProvideUserPrefs(content: @Composable () -> Unit) {
    val viewModel: UserPrefsViewModel = hiltViewModel()
    val units by viewModel.units.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalUserUnits provides units) { content() }
}
