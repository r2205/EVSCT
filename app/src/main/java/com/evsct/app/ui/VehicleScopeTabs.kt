package com.evsct.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.evsct.app.R
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.ui.theme.EvsctTheme

/**
 * The vehicle tab strip shared by the Log and Stats. One tab per bucket that
 * holds sessions, plus "All"; guard the call with [needsVehiclePicker] so it
 * only appears when there's more than one bucket to choose between.
 *
 * Previously each screen kept its own private copy of this row. They were
 * identical, and adding [VehicleScope.Unassigned] would have meant editing the
 * same code twice — the copy-and-drift that left the Log's empty states looking
 * unlike everyone else's.
 *
 * [onManageVehicles] pins a car icon to the strip's end that opens the
 * Vehicles screen — item #5's answer to that screen living only behind the
 * Settings gear: reachable from exactly the place vehicles are already on
 * screen, at no cost to the nav bar. It sits outside the scrollable strip so
 * a long garage can't push it out of reach, and it's an icon rather than a
 * tab because it navigates instead of filtering — a tab that never shows as
 * selected would break the strip's own selection model.
 */
@Composable
fun VehicleScopeTabs(
    vehicles: List<Vehicle>,
    includeUnassigned: Boolean,
    scope: VehicleScope,
    onSelect: (VehicleScope) -> Unit,
    modifier: Modifier = Modifier,
    onManageVehicles: (() -> Unit)? = null,
) {
    // Explicit element type: left to builder inference, the first add() would
    // pin this to Pair<VehicleScope.All, String> and reject the others.
    // Each tab carries a display label and a separate testTag token. The
    // label localizes; the token deliberately does not — a tag that rotated
    // with the device language would make every test and tag reference
    // locale-dependent. Vehicle names are user data, identical in both roles.
    val tabs = buildList<Triple<VehicleScope, String, String>> {
        add(Triple(VehicleScope.All, stringResource(R.string.tabs_all), "All"))
        vehicles.forEach { add(Triple(VehicleScope.One(it.id), it.name, it.name)) }
        // Last, after the real vehicles: it's the exception bucket, not a peer
        // of them.
        if (includeUnassigned) {
            add(Triple(VehicleScope.Unassigned, stringResource(R.string.tabs_unassigned), "Unassigned"))
        }
    }
    val selectedIndex = tabs.indexOfFirst { it.first == scope }.coerceAtLeast(0)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEachIndexed { index, (tabScope, label, tag) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(tabScope) },
                    // Tagged by label, not index: a test that says "tap
                    // Unassigned" shouldn't care how many vehicles precede it —
                    // that count is exactly what varies between the cases worth
                    // testing.
                    modifier = Modifier.testTag("scopeTab:$tag"),
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
        if (onManageVehicles != null) {
            IconButton(onClick = onManageVehicles) {
                Icon(
                    Icons.Default.DirectionsCar,
                    // Also the TalkBack name — the affordance is icon-only.
                    contentDescription = stringResource(R.string.tabs_manage_vehicles),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ------------------------------- Previews -------------------------------- */

/*
 * The bucket rule from #51 is unit-tested in VehicleScopeTest — what tabs exist,
 * and when a picker is worth showing at all. What the tests can't say is whether
 * the row reads right, and the combinations are a nuisance to reach for real:
 * seeing "1 vehicle + Unassigned" means owning exactly one car and holding on to
 * an unassigned session.
 *
 * Unassigned sits last, after the real vehicles, because it's the exception
 * bucket rather than a peer. That ordering is the thing to check by eye.
 */

private fun previewVehicles(vararg names: String) =
    names.mapIndexed { i, name -> Vehicle(id = i + 1L, name = name) }

@Preview(name = "Tabs — two vehicles", showBackground = true, widthDp = 400)
@Composable
private fun PreviewTabsTwoVehicles() {
    EvsctTheme {
        VehicleScopeTabs(
            vehicles = previewVehicles("EV6", "Mach-E"),
            includeUnassigned = false,
            scope = VehicleScope.All,
            onSelect = {},
            onManageVehicles = {},
        )
    }
}

/** The case that used to render nothing at all: one vehicle plus orphans. The
 *  old rule required 2+ vehicles, so a lone car and some unassigned sessions
 *  showed one undifferentiated list with no headings. */
@Preview(name = "Tabs — one vehicle + Unassigned", showBackground = true, widthDp = 400)
@Composable
private fun PreviewTabsOneVehiclePlusUnassigned() {
    EvsctTheme {
        VehicleScopeTabs(
            vehicles = previewVehicles("EV6"),
            includeUnassigned = true,
            scope = VehicleScope.Unassigned,
            onSelect = {},
        )
    }
}

/** Enough tabs to scroll, with Unassigned selected at the far end — the check
 *  that the selected tab is reachable rather than stranded off-screen. */
@Preview(name = "Tabs — scrolling, Unassigned selected", showBackground = true, widthDp = 400)
@Composable
private fun PreviewTabsScrolling() {
    EvsctTheme {
        VehicleScopeTabs(
            vehicles = previewVehicles("EV6", "Mach-E", "Ioniq 5", "Model Y"),
            includeUnassigned = true,
            scope = VehicleScope.Unassigned,
            onSelect = {},
        )
    }
}

/** Vehicle names are free text, so one long enough to dominate the row is a
 *  case the app permits and nobody has looked at. */
@Preview(name = "Tabs — long name, 1.5x font", showBackground = true, widthDp = 400, fontScale = 1.5f)
@Composable
private fun PreviewTabsLongName() {
    EvsctTheme {
        VehicleScopeTabs(
            vehicles = previewVehicles("Volkswagen ID.4 Pro S AWD", "EV6"),
            includeUnassigned = true,
            scope = VehicleScope.One(1L),
            onSelect = {},
        )
    }
}
