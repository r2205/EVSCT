package com.evsct.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val trim: String? = null,
    val batteryCapacityKwh: Double? = null,
    val nominalRangeKm: Int? = null,
    val vin: String? = null,
    val notes: String? = null,
    /** Path relative to the app's filesDir (e.g. "vehicles/3.jpg"). Null if no image. */
    val imagePath: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayLabel: String
        get() {
            val parts = listOfNotNull(year?.toString(), make, model, trim).filter { it.isNotBlank() }
            return if (parts.isEmpty()) name else parts.joinToString(" ")
        }
}
