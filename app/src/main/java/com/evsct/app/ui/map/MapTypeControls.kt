package com.evsct.app.ui.map

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.evsct.app.R
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

private data class MapTypeOption(val key: String, @StringRes val labelRes: Int)

private val MAP_TYPE_OPTIONS = listOf(
    MapTypeOption("NORMAL", R.string.maptype_default),
    MapTypeOption("SATELLITE", R.string.maptype_satellite),
    MapTypeOption("HYBRID", R.string.maptype_hybrid),
    MapTypeOption("TERRAIN", R.string.maptype_terrain),
)

/**
 * Layers dropdown. The four basemap rows are always present; the optional
 * enabled/onToggle pairs each surface a display-mode row beneath them
 * (heatmap, trip routes, trip colors, clustering) — everything about how
 * the map LOOKS lives here, while the filter sheet decides what SHOWS.
 * The location picker passes none of them and gets just the basemaps.
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
    /** When false, the Trip routes row is shown grayed-out and not clickable.
     *  Its checkmark state is preserved so flipping heatmap off restores the
     *  previous polyline preference without the user having to re-toggle. */
    polylinesAvailable: Boolean = true,
    colorByTripEnabled: Boolean? = null,
    onToggleColorByTrip: ((Boolean) -> Unit)? = null,
    clusteringEnabled: Boolean? = null,
    onToggleClustering: ((Boolean) -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MAP_TYPE_OPTIONS.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                onClick = { onSelect(option.key) },
                trailingIcon = {
                    if (option.key == current) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.maptype_selected))
                    }
                },
            )
        }
        val hasHeatmap = heatmapEnabled != null && onToggleHeatmap != null
        val hasPolylines = polylinesEnabled != null && onTogglePolylines != null
        val hasColorByTrip = colorByTripEnabled != null && onToggleColorByTrip != null
        val hasClustering = clusteringEnabled != null && onToggleClustering != null
        if (hasHeatmap || hasPolylines || hasColorByTrip || hasClustering) {
            HorizontalDivider()
        }
        if (hasHeatmap) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.maptype_heatmap)) },
                onClick = { onToggleHeatmap(!heatmapEnabled!!) },
                trailingIcon = {
                    if (heatmapEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.maptype_heatmap_on))
                    }
                },
            )
        }
        if (hasPolylines) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.maptype_trip_routes)) },
                enabled = polylinesAvailable,
                onClick = { onTogglePolylines(!polylinesEnabled!!) },
                trailingIcon = {
                    if (polylinesEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.maptype_trip_routes_on))
                    }
                },
            )
        }
        if (hasColorByTrip) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.maptype_color_pins_by_trip)) },
                onClick = { onToggleColorByTrip(!colorByTripEnabled!!) },
                trailingIcon = {
                    if (colorByTripEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.maptype_trip_colors_on))
                    }
                },
            )
        }
        if (hasClustering) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.maptype_cluster_nearby_pins)) },
                onClick = { onToggleClustering(!clusteringEnabled!!) },
                trailingIcon = {
                    if (clusteringEnabled == true) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.maptype_clustering_on))
                    }
                },
            )
        }
    }
}
