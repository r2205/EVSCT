package com.evsct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.evsct.app.ui.navigation.EvsctNavGraph
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.util.BackupReminderNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backupReminderNotifier: BackupReminderNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EvsctTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    EvsctNavGraph(navController = navController)
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
