package com.evsct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.ui.ProvideUserPrefs
import com.evsct.app.ui.navigation.EvsctNavGraph
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.util.BackupReminderNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backupReminderNotifier: BackupReminderNotifier

    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Resolve the user's theme preference here (above EvsctTheme) so
            // the override applies to the whole composition. SYSTEM falls
            // back to the OS dark-mode flag.
            val themeMode by appPreferences.themeMode.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            EvsctTheme(darkTheme = darkTheme) {
                ProvideUserPrefs {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        EvsctNavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate the backup reminder on every foregrounding so the
        // notification appears once the threshold is crossed and clears
        // automatically after a fresh backup or settings change.
        lifecycleScope.launch { backupReminderNotifier.refresh() }
    }
}
