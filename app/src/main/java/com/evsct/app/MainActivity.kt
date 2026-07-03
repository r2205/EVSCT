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
import com.evsct.app.util.MissingMediaSweeper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var missingMediaSweeper: MissingMediaSweeper

    /** Pending deep-link route emitted when this activity is launched (or
     *  re-launched) by tapping the in-progress charge notification. The
     *  composition collects this and routes the NavController to the right
     *  session edit screen, then resets the value so the navigation only
     *  fires once per intent. */
    private val pendingDeepLinkRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Drop DB references to media files that don't exist on disk — the
        // aftermath of a cloud auto-restore, which carries the DB but not
        // receipts/ or vehicles/. Once per process, runs on the app scope.
        missingMediaSweeper.sweepInBackground()
        // Consume the launching intent exactly once per delivery. Three
        // arrival shapes need telling apart:
        //  - Fresh start (no saved state): a launcher open or a
        //    notification tap on a finished activity — consume normally.
        //  - In-process recreation (rotation, theme change): saved state
        //    present AND this process already examined the intent —
        //    getIntent() is the same, already-consumed object; refiring
        //    would hijack navigation on every config change.
        //  - Process-death restore: saved state present but this process
        //    has never seen the intent. When the restore was triggered by
        //    tapping the in-progress notification, this is the only
        //    delivery that tap gets — the old savedInstanceState-only
        //    guard silently dropped it and the user landed on whatever
        //    screen was restored. Consume it, but gated to the still-live
        //    tracked charge so a stale recents relaunch (whose root intent
        //    happens to be an old notification tap) doesn't hijack the
        //    restored screen once tracking has ended.
        when {
            savedInstanceState == null -> consumeIntentExtras(intent)
            !launchIntentExamined -> {
                val sessionId = intent
                    ?.getLongExtra(InProgressChargeNotifier.EXTRA_OPEN_SESSION_ID, -1L)
                    ?: -1L
                if (sessionId > 0) {
                    // Read the tracked id straight from DataStore — the
                    // notifier's in-memory copy restores asynchronously
                    // and may not be populated yet this early in startup.
                    lifecycleScope.launch {
                        if (appPreferences.trackedChargeSessionId() == sessionId) {
                            pendingDeepLinkRoute.value = Routes.sessionEdit(sessionId)
                        }
                    }
                }
            }
        }
        launchIntentExamined = true
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

    companion object {
        /** True once an activity instance in this process has examined its
         *  launching intent. Survives activity recreation (rotation, theme
         *  change) but not process death — exactly the boundary that
         *  separates "getIntent() was already consumed" from "restored
         *  task whose notification-tap intent was never delivered". */
        private var launchIntentExamined = false
    }
}
