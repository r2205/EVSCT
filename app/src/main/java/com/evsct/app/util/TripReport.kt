package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle

/**
 * What a trip's own readings and its sessions say about the drive, read
 * against the one car the trip belongs to ([Trip.vehicleId]). Built by
 * [of]; the trip detail shows it in full.
 */
data class TripReport(
    /** The trip's car, when it has one that still exists. */
    val vehicle: Vehicle?,
    val distanceKm: Double,
    val legs: List<DrivingLeg>,
    val excluded: List<ExcludedPair>,
    /**
     * Sessions tagged to the trip but logged on a different car. A trip is
     * one car, and these readings describe another one, so they sit out the
     * distance and efficiency (they still count toward the charging totals,
     * which are about what was paid and delivered) and the detail flags
     * them so the tag or the trip's car can be fixed.
     */
    val otherCarSessions: List<ChargingSession>,
    /** Tagged sessions with no vehicle set, taken to be on the trip's car —
     *  see [isOnTripCar]. Surfaced so the detail can say so. */
    val unassignedSessions: List<ChargingSession>,
) {
    /** Distance over energy across every measured leg — totals over totals,
     *  so a long leg weighs more than a short one. */
    val avgKmPerKwh: Double? get() {
        val km = legs.sumOf { it.distanceKm }
        val kwh = legs.sumOf { it.energyUsedKwh }
        return if (km > 0 && kwh > 0) km / kwh else null
    }

    /**
     * Estimated energy the drive took out of the battery: each measured
     * leg's battery drop × the car's capacity, summed. Not the same thing
     * as the energy charged, which is what the chargers put in (a trip with
     * no charging stops charged nothing but still used energy). Null when
     * no leg could be measured.
     */
    val energyUsedKwh: Double? get() = legs.takeIf { it.isNotEmpty() }?.sumOf { it.energyUsedKwh }

    /** How far the measured legs cover — all of [distanceKm] when every
     *  drive between readings could be measured. */
    val measuredDistanceKm: Double get() = legs.sumOf { it.distanceKm }

    /** True when [energyUsedKwh] covers only part of the trip: some drive
     *  between two readings couldn't be measured, so the total understates
     *  the whole trip. The ratio in [avgKmPerKwh] is still sound. */
    val energyUsedIsPartial: Boolean
        get() = energyUsedKwh != null && measuredDistanceKm < distanceKm - DISTANCE_SLACK_KM

    companion object {

        /** Legs are differences of the same odometer readings the distance
         *  uses, so full coverage matches it to rounding error; anything
         *  short of it by more than this means a drive went unmeasured. */
        private const val DISTANCE_SLACK_KM = 0.1

        /**
         * Whether [session], tagged to [trip], counts as driven in the
         * trip's car. A session logged on it does; so does one with no
         * vehicle set, since a trip is one car and a session on it that
         * names none can only have been that car. With no car on the trip
         * there's nothing to compare against, so every session counts.
         */
        fun isOnTripCar(trip: Trip, session: ChargingSession): Boolean =
            trip.vehicleId == null || session.vehicleId == null || session.vehicleId == trip.vehicleId

        /**
         * [sessions] are the trip's own; [allSessions] is the whole log, so
         * a charge that happened mid-trip without being tagged to it is
         * caught instead of silently distorting a leg (see
         * [EfficiencyAnalysis.analyze]).
         *
         * The trip's start/end battery and odometer readings anchor its
         * first and last legs — and the whole trip, when there were no
         * charging stops — using the trip car's battery capacity. A trip
         * without a car can't place those readings, so it only measures
         * what its sessions can, per car they name.
         */
        fun of(
            trip: Trip,
            sessions: List<ChargingSession>,
            allSessions: List<ChargingSession>,
            vehicles: List<Vehicle>,
        ): TripReport {
            val distance = TripDistance.km(trip, sessions)
            val carId = trip.vehicleId
            if (carId == null) {
                val legs = mutableListOf<DrivingLeg>()
                val excluded = mutableListOf<ExcludedPair>()
                for ((vehicleId, group) in sessions.groupBy { it.vehicleId }) {
                    val report = EfficiencyAnalysis.analyze(
                        group,
                        vehicles.firstOrNull { it.id == vehicleId },
                        allSessions.filter { it.vehicleId == vehicleId },
                    )
                    legs += report.legs
                    excluded += report.excluded
                }
                return TripReport(
                    vehicle = null,
                    distanceKm = distance,
                    legs = legs.sortedBy { it.to.sessionStart },
                    excluded = excluded.sortedBy { it.to.sessionStart },
                    otherCarSessions = emptyList(),
                    unassignedSessions = emptyList(),
                )
            }

            val vehicle = vehicles.firstOrNull { it.id == carId }
            val (onCar, otherCar) = sessions.partition { isOnTripCar(trip, it) }
            val report = EfficiencyAnalysis.analyze(
                onCar,
                vehicle,
                allSessions.filter { it.vehicleId == carId },
                tripStart = TripAnchor(trip.startOdometerKm, trip.startBatteryPct, trip.startDate)
                    .takeIf { it.hasData },
                tripEnd = TripAnchor(trip.endOdometerKm, trip.endBatteryPct, trip.endDate)
                    .takeIf { it.hasData },
            )
            return TripReport(
                vehicle = vehicle,
                distanceKm = distance,
                legs = report.legs.sortedBy { it.to.sessionStart },
                excluded = report.excluded.sortedBy { it.to.sessionStart },
                otherCarSessions = otherCar,
                unassignedSessions = onCar.filter { it.vehicleId == null },
            )
        }
    }
}
