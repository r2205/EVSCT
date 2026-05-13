package com.evsct.app.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.google.maps.android.compose.MapType

/** Shared between [MapScreen] and [MapPickerScreen] — both expose the same
 *  layers menu so satellite / terrain choices feel consistent. The selected
 *  value is the persisted DataStore string, not the Maps Compose enum, so
 *  flipping the toggle on one screen affects the other. */

/** Convert the persisted map-type string into the Maps Compose enum. Falls
 *  back to NORMAL on anything unrecognised. */
internal fun mapTypeOf(value: String): MapType = when (value) {
    "SATELLITE" -> MapType.SATELLITE
    "HYBRID" -> MapType.HYBRID
    "TERRAIN" -> MapType.TERRAIN
    else -> MapType.NORMAL
}

private data class MapTypeOption(val key: String, val label: String)

private val MAP_TYPE_OPTIONS = listOf(
    MapTypeOption("NORMAL", "Default"),
    MapTypeOption("SATELLITE", "Satellite"),
    MapTypeOption("HYBRID", "Hybrid"),
    MapTypeOption("TERRAIN", "Terrain"),
)

/**
 * Layers dropdown. The four basemap rows are always present; pass
 * [heatmapEnabled] + [onToggleHeatmap] (and optionally [polylinesEnabled] +
 * [onTogglePolylines]) to additionally surface display-mode rows beneath
 * them. Used by the charging-map screen, not the location picker.
 */
@Composable
internal fun MapTypeMenu(
    expanded: Boolean,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    heatmapEnabled: Boolean? = null,
    onToggleHeatmap: ((Boolean) -> Unit)? = null,
    polylinesEnabled: Boolean? = null,
    onTogglePolylines: ((Boolean) -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MAP_TYPE_OPTIONS.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(option.key) },
                trailingIcon = {
                    if (option.key == current) {
                        Icon(Icons.Default.Check, contentDescription = "Selected")
                    }
                },
            )
        }
        val hasHeatmap = heatmapEnabled != null && onToggleHeatmap != null
        val hasPolylines = polylinesEnabled != null && onTogglePolylines != null
        if (hasHeatmap || hasPolylines) {
            HorizontalDivider()
        }
        if (hasHeatmap) {
            DropdownMenuItem(
                text = { Text("Heatmap") },
                onClick = { onToggleHeatmap(!heatmapEnabled!!) },
                trailingIcon = {
                    if (heatmapEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = "Heatmap on")
                    }
                },
            )
        }
        if (hasPolylines) {
            DropdownMenuItem(
                text = { Text("Trip routes") },
                onClick = { onTogglePolylines(!polylinesEnabled!!) },
                trailingIcon = {
                    if (polylinesEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = "Trip routes on")
                    }
                },
            )
        }
    }
}
