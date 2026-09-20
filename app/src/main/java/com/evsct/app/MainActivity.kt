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
import com.evsct.app.ui.theme.SystemBarIconsFollowTheme
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
        // Consume the launching intent exactly once per delivery, and only
        // when it is worth acting on. Two questions, in order:
        //
        // Has this process already examined this intent? Saved state plus
        // an examined intent means in-process recreation (rotation, theme
        // change): getIntent() is the same, already-consumed object, and
        // refiring would hijack navigation on every config change. Saved
        // state with an UNexamined intent is a process-death restore,
        // where this is the only delivery a notification tap gets —
        // dropping it lands the user on whatever screen was restored.
        //
        // Is the id worth honoring? Every path goes through the tracked
        // charge gate, because the intent is not trustworthy on its own:
        // a redelivered base intent (recents relaunch) can name a charge
        // that finished days ago, and this activity is exported, so any
        // installed app can send the same extra to open an arbitrary
        // session. A live notification always names the tracked charge,
        // so a genuine tap passes trivially.
        if (savedInstanceState == null || !launchIntentExamined) {
            consumeIntentExtrasIfStillTracked(intent)
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
                // Status/nav bar icon polarity, derived from the resolved
                // scheme rather than the system night flag enableEdgeToEdge()
                // reads — that flag can't see the themeMode override above,
                // so forcing light on a dark system (or the reverse) left the
                // icons inverted. Must sit inside EvsctTheme to read it.
                SystemBarIconsFollowTheme()
                ProvideUserPrefs {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        // Route any pending deep-link (from a tapped
                        // in-progress notification) once the NavController is
                        // ready. launchSingleTop reuses the existing edit
                        // screen when the user is already editing THIS
                        // session — without it, tapping the notification
                        // while editing pushes a duplicate destination on
                        // top, spinning up a fresh ViewModel that re-loads
                        // from the DB and silently discards anything the user
                        // typed but hadn't saved. But singleTop also swallows
                        // the navigation when a DIFFERENT session's edit
                        // screen is on top (same destination, so the new
                        // arguments are dropped) — the user would keep
                        // looking at the other session and enter the tracked
                        // charge's data into the wrong row. Push a fresh
                        // entry in that case; the entry underneath keeps its
                        // unsaved state.
                        LaunchedEffect(navController) {
                            pendingDeepLinkRoute.collect { route ->
                                if (route != null) {
                                    val current = navController.currentBackStackEntry
                                    val onOtherSessionEdit =
                                        current?.destination?.route
                                            ?.startsWith(Routes.SESSION_EDIT) == true &&
                                            current.arguments
                                                ?.getLong(Routes.SESSION_EDIT_ARG)
                                                ?.let { Routes.sessionEdit(it) != route } == true
                                    navController.navigate(route) {
                                        launchSingleTop = !onOtherSessionEdit
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
        // to the right edit screen — through the same gate as every other
        // delivery, since an exported activity can be started by anyone.
        consumeIntentExtrasIfStillTracked(intent)
    }

    /**
     * Route to a session's edit screen from an intent extra, but only when
     * that session is still the live tracked charge.
     *
     * The gate does two jobs. A redelivered base intent (recents relaunch,
     * process-death restore) can name a charge that finished days ago, and
     * MainActivity is exported, so any installed app can send this extra to
     * open an arbitrary session by id. Neither should move the user. The
     * notification only ever exists for the tracked charge, so a real tap
     * always passes.
     *
     * Reads the tracked id straight from DataStore — the notifier's
     * in-memory copy restores asynchronously and may not be populated yet
     * this early in startup.
     */
    private fun consumeIntentExtrasIfStillTracked(intent: Intent?) {
        val sessionId = intent
            ?.getLongExtra(InProgressChargeNotifier.EXTRA_OPEN_SESSION_ID, -1L)
            ?: -1L
        if (sessionId > 0) {
            lifecycleScope.launch {
                if (appPreferences.trackedChargeSessionId() == sessionId) {
                    pendingDeepLinkRoute.value = Routes.sessionEdit(sessionId)
                }
            }
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
