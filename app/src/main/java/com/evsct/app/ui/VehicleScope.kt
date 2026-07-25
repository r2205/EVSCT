package com.evsct.app.ui

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle

/**
 * Which bucket of the log a screen is scoped to. Shared by the Log, Stats, and
 * the Map, all of which filter the same sessions by vehicle.
 *
 * [Unassigned] earns its place because a session's vehicle is nullable —
 * sessions imported from CSV, or logged before any vehicle was set up, carry
 * none at all. These filters used to be a plain `Long?`, which can only express
 * "everything" or "this one vehicle", so those sessions were reachable only
 * under [All], mixed into the whole set: no way to see them as a group, and on
 * the Log therefore no practical way to go assign them.
 *
 * Deliberately not a `-1L` sentinel on the old `Long?`. That would have been a
 * smaller change, but the value flows out of the Log to the Add-session and
 * Track-charge preselect, where it would land as a vehicle id and only behave
 * by coincidence with the nav layer's own -1 convention.
 */
sealed interface VehicleScope {
    data object All : VehicleScope
    data object Unassigned : VehicleScope
    data class One(val id: Long) : VehicleScope

    /** Does [session] belong in this bucket? */
    fun matches(session: ChargingSession): Boolean = when (this) {
        All -> true
        Unassigned -> session.vehicleId == null
        is One -> session.vehicleId == id
    }
}

/**
 * Fall back to [VehicleScope.All] when this scope has nothing behind it any
 * more — a vehicle that has since been deleted, or [VehicleScope.Unassigned]
 * after the last such session was finally given one. Without this a screen
 * would sit empty under a bucket that no longer exists in the picker above it.
 */
fun VehicleScope.orAllIfEmpty(
    vehicles: List<Vehicle>,
    hasUnassigned: Boolean,
): VehicleScope = when (this) {
    VehicleScope.All -> VehicleScope.All
    VehicleScope.Unassigned -> if (hasUnassigned) this else VehicleScope.All
    is VehicleScope.One -> if (vehicles.any { it.id == id }) this else VehicleScope.All
}

/**
 * Is a vehicle picker worth showing at all? Only once there's more than one
 * bucket to choose between — a lone vehicle with nothing unassigned leaves
 * nothing to pick, so the chrome is pure noise.
 */
fun needsVehiclePicker(vehicleCount: Int, hasUnassigned: Boolean): Boolean =
    vehicleCount + (if (hasUnassigned) 1 else 0) >= 2

/**
 * Encoding for carrying a scope across a navigation boundary — the Year Recap
 * route and the Stats → Log brand drill-down relay.
 *
 * Both used to carry a plain `Long?` with -1 for "all vehicles", which had no
 * room for [VehicleScope.Unassigned]: opening the recap from the Unassigned tab
 * silently widened it to every session, and the recap screen shows no scope
 * label, so nothing on screen contradicted it. A three-state bucket needs three
 * states in the argument, and a token says which one it is instead of leaving
 * the reader to decode magic numbers.
 */
fun VehicleScope.toToken(): String = when (this) {
    VehicleScope.All -> "all"
    VehicleScope.Unassigned -> "unassigned"
    is VehicleScope.One -> id.toString()
}

/** Inverse of [toToken]. Anything unrecognized reads as [VehicleScope.All] —
 *  a scope arriving malformed should widen the view, never blank it. */
fun vehicleScopeFromToken(token: String?): VehicleScope = when (token) {
    null, "", "all" -> VehicleScope.All
    "unassigned" -> VehicleScope.Unassigned
    else -> token.toLongOrNull()
        ?.takeIf { it >= 0 }
        ?.let { VehicleScope.One(it) }
        ?: VehicleScope.All
}
