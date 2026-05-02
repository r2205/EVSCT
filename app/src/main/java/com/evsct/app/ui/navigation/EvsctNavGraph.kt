package com.evsct.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.evsct.app.ui.map.MapScreen
import com.evsct.app.ui.sessions.SessionEditScreen
import com.evsct.app.ui.sessions.SessionListScreen
import com.evsct.app.ui.settings.SettingsScreen
import com.evsct.app.ui.stats.StatsScreen
import com.evsct.app.ui.trips.TripDetailScreen
import com.evsct.app.ui.trips.TripListScreen
import com.evsct.app.ui.vehicles.VehicleDetailScreen
import com.evsct.app.ui.vehicles.VehicleEditScreen
import com.evsct.app.ui.vehicles.VehicleListScreen

object Routes {
    const val SESSION_LIST = "sessions"
    const val SESSION_EDIT = "sessions/edit"
    const val SESSION_EDIT_ARG = "sessionId"
    const val SESSION_PRESELECT_VEHICLE_ARG = "preselectVehicleId"
    const val TRIP_LIST = "trips"
    const val TRIP_DETAIL = "trips/detail"
    const val TRIP_DETAIL_ARG = "tripId"
    const val VEHICLE_LIST = "vehicles"
    const val VEHICLE_DETAIL = "vehicles/detail"
    const val VEHICLE_DETAIL_ARG = "vehicleId"
    const val VEHICLE_EDIT = "vehicles/edit"
    const val VEHICLE_EDIT_ARG = "vehicleId"
    const val STATS = "stats"
    const val MAP = "map"
    const val SETTINGS = "settings"

    fun sessionEdit(id: Long? = null, preselectVehicleId: Long? = null): String {
        val sid = id ?: -1L
        val vid = preselectVehicleId ?: -1L
        return "$SESSION_EDIT?$SESSION_EDIT_ARG=$sid&$SESSION_PRESELECT_VEHICLE_ARG=$vid"
    }

    fun tripDetail(id: Long): String = "$TRIP_DETAIL/$id"

    fun vehicleEdit(id: Long? = null): String =
        if (id == null) "$VEHICLE_EDIT?$VEHICLE_EDIT_ARG=-1" else "$VEHICLE_EDIT?$VEHICLE_EDIT_ARG=$id"

    fun vehicleDetail(id: Long): String = "$VEHICLE_DETAIL/$id"
}

@Composable
fun EvsctNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SESSION_LIST) {
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onAddSession = { preselectVehicleId ->
                    navController.navigate(Routes.sessionEdit(preselectVehicleId = preselectVehicleId))
                },
                onEditSession = { id -> navController.navigate(Routes.sessionEdit(id)) },
                onOpenTrips = { navController.navigate(Routes.TRIP_LIST) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MAP) {
            MapScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.SESSION_EDIT}?${Routes.SESSION_EDIT_ARG}={${Routes.SESSION_EDIT_ARG}}" +
                "&${Routes.SESSION_PRESELECT_VEHICLE_ARG}={${Routes.SESSION_PRESELECT_VEHICLE_ARG}}",
            arguments = listOf(
                navArgument(Routes.SESSION_EDIT_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(Routes.SESSION_PRESELECT_VEHICLE_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
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
        composable(Routes.VEHICLE_LIST) {
            VehicleListScreen(
                onBack = { navController.popBackStack() },
                onAddVehicle = { navController.navigate(Routes.vehicleEdit()) },
                onOpenVehicle = { id -> navController.navigate(Routes.vehicleDetail(id)) },
            )
        }
        composable(
            route = "${Routes.VEHICLE_DETAIL}/{${Routes.VEHICLE_DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.VEHICLE_DETAIL_ARG) { type = NavType.LongType }),
        ) {
            VehicleDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.vehicleEdit(id)) },
                onEditSession = { id -> navController.navigate(Routes.sessionEdit(id)) },
            )
        }
        composable(
            route = "${Routes.VEHICLE_EDIT}?${Routes.VEHICLE_EDIT_ARG}={${Routes.VEHICLE_EDIT_ARG}}",
            arguments = listOf(
                navArgument(Routes.VEHICLE_EDIT_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            ),
        ) {
            VehicleEditScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenVehicles = { navController.navigate(Routes.VEHICLE_LIST) },
            )
        }
    }
}
