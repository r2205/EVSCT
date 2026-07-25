package com.evsct.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evsct.app.data.entity.Vehicle

/**
 * The vehicle tab strip shared by the Log and Stats. One tab per bucket that
 * holds sessions, plus "All"; guard the call with [needsVehiclePicker] so it
 * only appears when there's more than one bucket to choose between.
 *
 * Previously each screen kept its own private copy of this row. They were
 * identical, and adding [VehicleScope.Unassigned] would have meant editing the
 * same code twice — the copy-and-drift that left the Log's empty states looking
 * unlike everyone else's.
 */
@Composable
fun VehicleScopeTabs(
    vehicles: List<Vehicle>,
    includeUnassigned: Boolean,
    scope: VehicleScope,
    onSelect: (VehicleScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Explicit element type: left to builder inference, the first add() would
    // pin this to Pair<VehicleScope.All, String> and reject the others.
    val tabs = buildList<Pair<VehicleScope, String>> {
        add(VehicleScope.All to "All")
        vehicles.forEach { add(VehicleScope.One(it.id) to it.name) }
        // Last, after the real vehicles: it's the exception bucket, not a peer
        // of them.
        if (includeUnassigned) add(VehicleScope.Unassigned to "Unassigned")
    }
    val selectedIndex = tabs.indexOfFirst { it.first == scope }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 12.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        tabs.forEachIndexed { index, (tabScope, label) ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(tabScope) },
                text = {
                    Text(
                        label,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold
                        else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
