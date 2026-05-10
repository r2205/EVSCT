package com.evsct.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.ui.ProvideUserPrefs
import com.evsct.app.ui.navigation.EvsctNavGraph
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.util.BackupReminderScheduler
import com.evsct.app.util.InProgressChargeNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    @Inject lateinit var appPreferences: AppPreferences

    /** Pending deep-link route emitted when this activity is launched (or
     *  re-launched) by tapping the in-progress charge notification. The
     *  composition collects this and routes the NavController to the right
     *  session edit screen, then resets the value so the navigation only
     *  fires once per intent. */
    private val pendingDeepLinkRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only consume the launching intent on a fresh process start. Config
        // changes (rotation, theme) reuse the same intent — without this
        // guard we'd re-navigate to the deep-link target on every rotation.
        if (savedInstanceState == null) consumeIntentExtras(intent)
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
                        // Route any pending deep-link (from a tapped
                        // in-progress notification) once the NavController is
                        // ready. launchSingleTop reuses the existing edit
                        // screen when the user is already there — without it,
                        // tapping the notification while editing pushes a
                        // duplicate destination on top, spinning up a fresh
                        // ViewModel that re-loads from the DB and silently
                        // discards anything the user typed but hadn't saved.
                        LaunchedEffect(navController) {
                            pendingDeepLinkRoute.collect { route ->
                                if (route != null) {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                    pendingDeepLinkRoute.value = null
                                }
                            }
                        }
                        EvsctNavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Already-running case: the activity is brought to the foreground by
        // the notification's PendingIntent. Push the new intent's session id
        // through the deep-link channel so the existing composition routes
        // to the right edit screen.
        consumeIntentExtras(intent)
    }

    private fun consumeIntentExtras(intent: Intent?) {
        val sessionId = intent
            ?.getLongExtra(InProgressChargeNotifier.EXTRA_OPEN_SESSION_ID, -1L)
            ?: -1L
        if (sessionId > 0) {
            pendingDeepLinkRoute.value = Routes.sessionEdit(sessionId)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate the backup reminder on every foregrounding so the
        // notification appears once the threshold is crossed and clears
        // automatically after a fresh backup or settings change. Also
        // re-arms the WorkManager check so the daily nag chain keeps
        // running while the app is closed.
        lifecycleScope.launch { backupReminderScheduler.refresh() }
    }
}
