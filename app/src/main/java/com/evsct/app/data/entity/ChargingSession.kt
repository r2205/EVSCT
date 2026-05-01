package com.evsct.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ChargingType { DC_FAST, AC_L2, AC_L1 }

enum class PricingModel { PER_KWH, PER_MINUTE, FLAT, FREE, HYBRID }

@Entity(
    tableName = "charging_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tripId"), Index("vehicleId"), Index("sessionStart")],
)
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Epoch millis at session start. */
    val sessionStart: Long,

    /** Total session duration in seconds (null when not recorded). */
    val durationSeconds: Long? = null,

    val odometerKm: Double? = null,
    val energyKwh: Double? = null,

    /** Total amount paid in [currency]. Null when unrecorded; 0.0 means free. */
    val totalCost: Double? = null,
    val currency: String = "CAD",

    val postedEnergyPricePerKwh: Double? = null,
    val postedTimeRatePerMin: Double? = null,
    val postedMaxPowerKw: Double? = null,

    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,

    val chargingType: ChargingType = ChargingType.DC_FAST,
    val pricingModel: PricingModel = PricingModel.PER_KWH,

    val brand: String? = null,
    val locationCity: String? = null,
    val locationProvince: String? = null,
    val locationAddress: String? = null,
    val stationName: String? = null,
    val stallName: String? = null,

    val tripId: Long? = null,
    val vehicleId: Long? = null,
    val notes: String? = null,
    /** Optional path under filesDir to an attached receipt image. */
    val receiptImagePath: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
