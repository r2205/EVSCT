package com.evsct.app.ui.map

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.evsct.app.R
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Fixed palette of map-pin colors users can assign to trips. Stored on
 * [com.evsct.app.data.entity.Trip.pinColor] as the enum name so the value
 * survives backups and renames safely.
 */
enum class TripPinColor(
    @StringRes val labelRes: Int,
    val swatch: Color,
    val mapsHue: Float,
) {
    RED(R.string.pin_red, Color(0xFFE53935), BitmapDescriptorFactory.HUE_RED),
    ORANGE(R.string.pin_orange, Color(0xFFFB8C00), BitmapDescriptorFactory.HUE_ORANGE),
    YELLOW(R.string.pin_yellow, Color(0xFFFDD835), BitmapDescriptorFactory.HUE_YELLOW),
    GREEN(R.string.pin_green, Color(0xFF43A047), BitmapDescriptorFactory.HUE_GREEN),
    CYAN(R.string.pin_cyan, Color(0xFF00ACC1), BitmapDescriptorFactory.HUE_CYAN),
    AZURE(R.string.pin_azure, Color(0xFF1E88E5), BitmapDescriptorFactory.HUE_AZURE),
    BLUE(R.string.pin_blue, Color(0xFF1A237E), BitmapDescriptorFactory.HUE_BLUE),
    VIOLET(R.string.pin_violet, Color(0xFF8E24AA), BitmapDescriptorFactory.HUE_VIOLET),
    MAGENTA(R.string.pin_magenta, Color(0xFFD81B60), BitmapDescriptorFactory.HUE_MAGENTA),
    ROSE(R.string.pin_rose, Color(0xFFEC407A), BitmapDescriptorFactory.HUE_ROSE),
    ;

    /** "#RRGGBB" form of [swatch], for embedding in the HTML recap map where
     *  pins are SVG circles rather than Maps markers. */
    val hex: String get() = String.format("#%06X", 0xFFFFFF and swatch.toArgb())

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
