package com.evsct.app.ui.map

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Fixed palette of map-pin colors users can assign to trips. Stored on
 * [com.evsct.app.data.entity.Trip.pinColor] as the enum name so the value
 * survives backups and renames safely.
 */
enum class TripPinColor(
    val displayName: String,
    val swatch: Color,
    val mapsHue: Float,
) {
    RED("Red", Color(0xFFE53935), BitmapDescriptorFactory.HUE_RED),
    ORANGE("Orange", Color(0xFFFB8C00), BitmapDescriptorFactory.HUE_ORANGE),
    YELLOW("Yellow", Color(0xFFFDD835), BitmapDescriptorFactory.HUE_YELLOW),
    GREEN("Green", Color(0xFF43A047), BitmapDescriptorFactory.HUE_GREEN),
    CYAN("Cyan", Color(0xFF00ACC1), BitmapDescriptorFactory.HUE_CYAN),
    AZURE("Azure", Color(0xFF1E88E5), BitmapDescriptorFactory.HUE_AZURE),
    BLUE("Blue", Color(0xFF1A237E), BitmapDescriptorFactory.HUE_BLUE),
    VIOLET("Violet", Color(0xFF8E24AA), BitmapDescriptorFactory.HUE_VIOLET),
    MAGENTA("Magenta", Color(0xFFD81B60), BitmapDescriptorFactory.HUE_MAGENTA),
    ROSE("Rose", Color(0xFFEC407A), BitmapDescriptorFactory.HUE_ROSE),
    ;

    companion object {
        fun fromKey(key: String?): TripPinColor? =
            key?.let { runCatching { valueOf(it) }.getOrNull() }

        /**
         * Pick the next palette entry in round-robin order based on how many
         * trips already use a color. Falls back to the first entry when the
         * list is empty.
         */
        fun nextDefault(usedKeys: Collection<String?>): TripPinColor {
            val ordered = entries.toList()
            // Count how many trips use each entry, so we hand out the
            // currently-least-used color to spread variety.
            val counts = ordered.associateWith { c ->
                usedKeys.count { it == c.name }
            }
            return counts.minByOrNull { it.value }?.key ?: ordered.first()
        }
    }
}
