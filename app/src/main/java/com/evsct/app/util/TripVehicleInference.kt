package com.evsct.app.util

/**
 * The vehicle a trip that predates trip vehicles most likely belongs to,
 * worked out from what its sessions say. The same rule
 * [com.evsct.app.data.db.EvsctDatabase.MIGRATION_14_15] applies to trips
 * already on the device, kept here for trips restored from a backup made
 * before trips carried a vehicle:
 *
 *  - every session that names a known car names the same one → that car;
 *  - no session names a known car and there's exactly one car → that car;
 *  - otherwise (mixed cars, or nothing to go on) → null, for the user to
 *    pick in the trip editor.
 *
 * Ids naming no known car are ignored, as if the session named none.
 */
object TripVehicleInference {

    fun infer(sessionVehicleIds: Collection<Long?>, knownVehicleIds: Collection<Long>): Long? {
        val cars = sessionVehicleIds.filterNotNull().filterTo(mutableSetOf()) { it in knownVehicleIds }
        return when {
            cars.size == 1 -> cars.single()
            cars.isEmpty() && knownVehicleIds.size == 1 -> knownVehicleIds.single()
            else -> null
        }
    }
}
