package com.evsct.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.evsct.app.ui.sessions.SessionEditScreen
import com.evsct.app.ui.sessions.SessionListScreen
import com.evsct.app.ui.settings.SettingsScreen
import com.evsct.app.ui.trips.TripDetailScreen
import com.evsct.app.ui.trips.TripListScreen

object Routes {
    const val SESSION_LIST = "sessions"
    const val SESSION_EDIT = "sessions/edit"
    const val SESSION_EDIT_ARG = "sessionId"
    const val TRIP_LIST = "trips"
    const val TRIP_DETAIL = "trips/detail"
    const val TRIP_DETAIL_ARG = "tripId"
    const val SETTINGS = "settings"

    fun sessionEdit(id: Long? = null): String =
        if (id == null) "$SESSION_EDIT?$SESSION_EDIT_ARG=-1" else "$SESSION_EDIT?$SESSION_EDIT_ARG=$id"

    fun tripDetail(id: Long): String = "$TRIP_DETAIL/$id"
}

@Composable
fun EvsctNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SESSION_LIST) {
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onAddSession = { navController.navigate(Routes.sessionEdit()) },
                onEditSession = { id -> navController.navigate(Routes.sessionEdit(id)) },
                onOpenTrips = { navController.navigate(Routes.TRIP_LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = "${Routes.SESSION_EDIT}?${Routes.SESSION_EDIT_ARG}={${Routes.SESSION_EDIT_ARG}}",
            arguments = listOf(
                navArgument(Routes.SESSION_EDIT_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            ),
        ) {
            SessionEditScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.TRIP_LIST) {
            TripListScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { id -> navController.navigate(Routes.tripDetail(id)) },
            )
        }
        composable(
            route = "${Routes.TRIP_DETAIL}/{${Routes.TRIP_DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.TRIP_DETAIL_ARG) { type = NavType.LongType }),
        ) {
            TripDetailScreen(
                onBack = { navController.popBackStack() },
                onEditSession = { id -> navController.navigate(Routes.sessionEdit(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
