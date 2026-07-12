package com.evsct.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.evsct.app.ui.map.MapPickerScreen
import com.evsct.app.ui.map.MapScreen
import com.evsct.app.ui.sessions.SessionEditScreen
import com.evsct.app.ui.sessions.SessionListScreen
import com.evsct.app.ui.settings.SettingsScreen
import com.evsct.app.ui.stats.StatsScreen
import com.evsct.app.ui.stats.YearRecapScreen
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
    const val YEAR_RECAP = "stats/recap"
    const val YEAR_RECAP_VEHICLE_ARG = "vehicleId"
    const val MAP = "map"
    const val MAP_PICKER = "map/picker"
    const val MAP_PICKER_LAT_ARG = "lat"
    const val MAP_PICKER_LNG_ARG = "lng"
    const val PICKED_LAT_KEY = "picked_lat"
    const val PICKED_LNG_KEY = "picked_lng"
    const val SETTINGS = "settings"

    /** SavedStateHandle keys the Stats screen writes onto the Log entry
     *  before switching tabs — the brand drill-down payload. The Log's
     *  composable reads them off the entry handle, applies both as a
     *  fresh filter set via the ViewModel, and clears them. Vehicle uses
     *  a -1 sentinel for "all vehicles" (same convention as
     *  [yearRecap]). */
    const val LOG_BRAND_FILTER_KEY = "log_brand_filter"
    const val LOG_BRAND_VEHICLE_KEY = "log_brand_vehicle"

    fun mapPicker(lat: Double?, lng: Double?): String {
        // Floats lose precision; pass via String query params and parse back.
        val latArg = lat?.toString().orEmpty()
        val lngArg = lng?.toString().orEmpty()
        return "$MAP_PICKER?$MAP_PICKER_LAT_ARG=$latArg&$MAP_PICKER_LNG_ARG=$lngArg"
    }

    fun sessionEdit(id: Long? = null, preselectVehicleId: Long? = null): String {
        val sid = id ?: -1L
        val vid = preselectVehicleId ?: -1L
        return "$SESSION_EDIT?$SESSION_EDIT_ARG=$sid&$SESSION_PRESELECT_VEHICLE_ARG=$vid"
    }

    fun tripDetail(id: Long): String = "$TRIP_DETAIL/$id"

    /** Year recap optionally scoped to a single vehicle. -1 sentinel means
     *  "all vehicles" so we can keep the nav argument typed as a primitive
     *  Long instead of a nullable String. */
    fun yearRecap(vehicleId: Long?): String =
        "$YEAR_RECAP?$YEAR_RECAP_VEHICLE_ARG=${vehicleId ?: -1L}"

    fun vehicleEdit(id: Long? = null): String =
        if (id == null) "$VEHICLE_EDIT?$VEHICLE_EDIT_ARG=-1" else "$VEHICLE_EDIT?$VEHICLE_EDIT_ARG=$id"

    fun vehicleDetail(id: Long): String = "$VEHICLE_DETAIL/$id"
}

/**
 * Run [block] only while this back-stack entry is RESUMED. An entry leaves
 * RESUMED the moment its exit transition starts, and an incoming entry only
 * reaches RESUMED once the transition settles — so taps that land
 * mid-transition are dropped instead of issuing a navigation call from a
 * screen that's no longer (or not yet) the settled destination. Without
 * this, saving a session and immediately tapping the spot where the list's
 * Settings gear appears fires navigate() during the pop transition and
 * corrupts the NavHost into a blank screen. Same idea as
 * androidx.lifecycle.compose.dropUnlessResumed, generalized to lambdas of
 * any arity.
 */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block()
}

/** A destination surfaced in the bottom navigation bar. */
private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.SESSION_LIST, "Log", Icons.Default.Bolt),
    TopLevelDestination(Routes.MAP, "Map", Icons.Default.Map),
    TopLevelDestination(Routes.STATS, "Stats", Icons.Default.BarChart),
    TopLevelDestination(Routes.TRIP_LIST, "Trips", Icons.Default.Route),
)

@Composable
fun EvsctNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The bar lives on the four top-level screens only. Sub-screens
    // (edit forms, detail pages, settings, the map picker) keep their
    // full height and their own back semantics.
    val showBottomBar = TOP_LEVEL_DESTINATIONS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            // Slide the bar out under sub-screens instead of blinking away —
            // enter/exit mirror each other so tab↔detail transitions feel
            // like one continuous surface.
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar {
                    TOP_LEVEL_DESTINATIONS.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                // Same mid-transition guard as every other
                                // navigation lambda in this graph.
                                backStackEntry?.ifResumed {
                                    navController.navigate(dest.route) {
                                        // Canonical tab behavior: one stack
                                        // segment per tab, saved when you
                                        // leave and restored when you return,
                                        // with back always landing on the Log.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            // Label text carries the name for TalkBack; the
                            // icon is decorative alongside it.
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SESSION_LIST,
            // consumeWindowInsets: the outer Scaffold already accounts for
            // the bar; without consuming, each screen's own Scaffold would
            // re-add bottom system-bar padding above it.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
        composable(Routes.SESSION_LIST) { entry ->
            // The Stats brand drill-down parks its payload on this entry's
            // SavedStateHandle before switching tabs (same relay the map
            // picker uses). It must be read HERE and handed to the screen:
            // this handle belongs to the nav entry, and is a different
            // object from the SavedStateHandle Hilt injects into the
            // screen's ViewModel — values written here never appear there.
            val handle = entry.savedStateHandle
            SessionListScreen(
                onAddSession = { preselectVehicleId ->
                    entry.ifResumed {
                        navController.navigate(Routes.sessionEdit(preselectVehicleId = preselectVehicleId))
                    }
                },
                onStartTrackedSession = { sessionId ->
                    entry.ifResumed { navController.navigate(Routes.sessionEdit(sessionId)) }
                },
                onEditSession = { id ->
                    entry.ifResumed { navController.navigate(Routes.sessionEdit(id)) }
                },
                onOpenSettings = { entry.ifResumed { navController.navigate(Routes.SETTINGS) } },
                requestedBrandFilter = handle.get<String>(Routes.LOG_BRAND_FILTER_KEY),
                requestedBrandVehicleId = handle.get<Long>(Routes.LOG_BRAND_VEHICLE_KEY),
                onBrandFilterRequestConsumed = {
                    handle.remove<String>(Routes.LOG_BRAND_FILTER_KEY)
                    handle.remove<Long>(Routes.LOG_BRAND_VEHICLE_KEY)
                },
            )
        }
        composable(Routes.STATS) { entry ->
            StatsScreen(
                onOpenYearRecap = { vehicleId ->
                    entry.ifResumed { navController.navigate(Routes.yearRecap(vehicleId)) }
                },
                onOpenLogForBrand = { brand, vehicleId ->
                    entry.ifResumed {
                        // Hand the Log its drill-down payload, then switch
                        // tabs with the same stack-preserving pattern as the
                        // bottom bar. The Log is the start destination, so
                        // its entry is always on the stack; the runCatching
                        // just downgrades a surprise to "switch without
                        // filter" instead of a crash.
                        runCatching { navController.getBackStackEntry(Routes.SESSION_LIST) }
                            .getOrNull()
                            ?.savedStateHandle
                            ?.let { handle ->
                                handle[Routes.LOG_BRAND_VEHICLE_KEY] = vehicleId ?: -1L
                                handle[Routes.LOG_BRAND_FILTER_KEY] = brand
                            }
                        navController.navigate(Routes.SESSION_LIST) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
        composable(
            route = "${Routes.YEAR_RECAP}?${Routes.YEAR_RECAP_VEHICLE_ARG}={${Routes.YEAR_RECAP_VEHICLE_ARG}}",
            arguments = listOf(
                navArgument(Routes.YEAR_RECAP_VEHICLE_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            YearRecapScreen(onBack = { entry.ifResumed { navController.popBackStack() } })
        }
        composable(Routes.MAP) { entry ->
            MapScreen(
                onEditSession = { id ->
                    entry.ifResumed { navController.navigate(Routes.sessionEdit(id)) }
                },
            )
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
        ) { backStackEntry ->
            // The map picker (a sibling route) writes its result into our
            // SavedStateHandle and pops back. Read those keys here so the
            // edit screen can apply them to the form state once.
            val handle = backStackEntry.savedStateHandle
            SessionEditScreen(
                onDone = { backStackEntry.ifResumed { navController.popBackStack() } },
                onPickLocation = { lat, lng ->
                    backStackEntry.ifResumed { navController.navigate(Routes.mapPicker(lat, lng)) }
                },
                pickedLat = handle.get<Double>(Routes.PICKED_LAT_KEY),
                pickedLng = handle.get<Double>(Routes.PICKED_LNG_KEY),
                onPickedConsumed = {
                    handle.remove<Double>(Routes.PICKED_LAT_KEY)
                    handle.remove<Double>(Routes.PICKED_LNG_KEY)
                },
            )
        }
        composable(
            route = "${Routes.MAP_PICKER}?${Routes.MAP_PICKER_LAT_ARG}={${Routes.MAP_PICKER_LAT_ARG}}" +
                "&${Routes.MAP_PICKER_LNG_ARG}={${Routes.MAP_PICKER_LNG_ARG}}",
            arguments = listOf(
                navArgument(Routes.MAP_PICKER_LAT_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.MAP_PICKER_LNG_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val initialLat = backStackEntry.arguments?.getString(Routes.MAP_PICKER_LAT_ARG)
                ?.toDoubleOrNull()
            val initialLng = backStackEntry.arguments?.getString(Routes.MAP_PICKER_LNG_ARG)
                ?.toDoubleOrNull()
            MapPickerScreen(
                initialLat = initialLat,
                initialLng = initialLng,
                onCancel = { backStackEntry.ifResumed { navController.popBackStack() } },
                onConfirm = { lat, lng ->
                    backStackEntry.ifResumed {
                        navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                            handle[Routes.PICKED_LAT_KEY] = lat
                            handle[Routes.PICKED_LNG_KEY] = lng
                        }
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(Routes.TRIP_LIST) { entry ->
            TripListScreen(
                onOpenTrip = { id ->
                    entry.ifResumed { navController.navigate(Routes.tripDetail(id)) }
                },
            )
        }
        composable(
            route = "${Routes.TRIP_DETAIL}/{${Routes.TRIP_DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.TRIP_DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            TripDetailScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onEditSession = { id ->
                    entry.ifResumed { navController.navigate(Routes.sessionEdit(id)) }
                },
            )
        }
        composable(Routes.VEHICLE_LIST) { entry ->
            VehicleListScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onAddVehicle = { entry.ifResumed { navController.navigate(Routes.vehicleEdit()) } },
                onOpenVehicle = { id ->
                    entry.ifResumed { navController.navigate(Routes.vehicleDetail(id)) }
                },
            )
        }
        composable(
            route = "${Routes.VEHICLE_DETAIL}/{${Routes.VEHICLE_DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.VEHICLE_DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            VehicleDetailScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onEdit = { id ->
                    entry.ifResumed { navController.navigate(Routes.vehicleEdit(id)) }
                },
                onEditSession = { id ->
                    entry.ifResumed { navController.navigate(Routes.sessionEdit(id)) }
                },
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
        ) { entry ->
            VehicleEditScreen(onDone = { entry.ifResumed { navController.popBackStack() } })
        }
        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                onBack = { entry.ifResumed { navController.popBackStack() } },
                onOpenVehicles = { entry.ifResumed { navController.navigate(Routes.VEHICLE_LIST) } },
            )
        }
        }
    }
}
