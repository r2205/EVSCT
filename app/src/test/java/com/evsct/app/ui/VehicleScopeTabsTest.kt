package com.evsct.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.ui.theme.EvsctTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The interaction half of the tab strip. VehicleScopeTest already pins which
 * buckets exist; this covers what a click actually reports — the seam the Log,
 * Stats, and the Map all hang their filtering on.
 *
 * Tabs are found by their label tag ("scopeTab:Unassigned"), not by position,
 * because how many vehicles precede a tab is exactly what varies between the
 * cases worth testing.
 */
@RunWith(AndroidJUnit4::class)
class VehicleScopeTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setTabs(
        vehicles: List<Vehicle>,
        includeUnassigned: Boolean,
        scope: VehicleScope = VehicleScope.All,
        onSelect: (VehicleScope) -> Unit = {},
        onManageVehicles: (() -> Unit)? = null,
    ) {
        compose.setContent {
            EvsctTheme {
                VehicleScopeTabs(
                    vehicles = vehicles,
                    includeUnassigned = includeUnassigned,
                    scope = scope,
                    onSelect = onSelect,
                    onManageVehicles = onManageVehicles,
                )
            }
        }
    }

    private val twoVehicles = listOf(Vehicle(id = 1L, name = "EV6"), Vehicle(id = 2L, name = "Mach-E"))

    @Test
    fun `every bucket gets a tab`() {
        setTabs(twoVehicles, includeUnassigned = true)
        compose.onNodeWithTag("scopeTab:All").assertExists()
        compose.onNodeWithTag("scopeTab:EV6").assertExists()
        compose.onNodeWithTag("scopeTab:Mach-E").assertExists()
        compose.onNodeWithTag("scopeTab:Unassigned").assertExists()
    }

    @Test
    fun `no orphan sessions means no Unassigned tab`() {
        setTabs(twoVehicles, includeUnassigned = false)
        compose.onNodeWithTag("scopeTab:Unassigned").assertDoesNotExist()
    }

    @Test
    fun `tapping a vehicle reports One with that vehicle's id`() {
        var selected: VehicleScope? = null
        setTabs(twoVehicles, includeUnassigned = true, onSelect = { selected = it })
        compose.onNodeWithTag("scopeTab:Mach-E").performClick()
        compose.runOnIdle { assertEquals(VehicleScope.One(2L), selected) }
    }

    @Test
    fun `tapping Unassigned reports the Unassigned bucket, not a vehicle`() {
        // The bucket the -1L sentinel could never express — the reason
        // VehicleScope exists at all.
        var selected: VehicleScope? = null
        setTabs(twoVehicles, includeUnassigned = true, onSelect = { selected = it })
        compose.onNodeWithTag("scopeTab:Unassigned").performClick()
        compose.runOnIdle { assertEquals(VehicleScope.Unassigned, selected) }
    }

    @Test
    fun `tapping All reports All`() {
        var selected: VehicleScope? = null
        setTabs(
            twoVehicles,
            includeUnassigned = true,
            scope = VehicleScope.One(1L),
            onSelect = { selected = it },
        )
        compose.onNodeWithTag("scopeTab:All").performClick()
        compose.runOnIdle { assertEquals(VehicleScope.All, selected) }
    }

    /* --------------------------- Manage affordance --------------------------- */

    // Item #5: the Vehicles screen's second front door, on the strip where
    // vehicles are already on screen. Optional because the strip shouldn't
    // grow chrome in contexts that can't navigate (previews, future callers).

    @Test
    fun `a manage handler puts the affordance on the strip, and clicks reach it`() {
        var opened = false
        setTabs(twoVehicles, includeUnassigned = false, onManageVehicles = { opened = true })
        compose.onNodeWithContentDescription("Manage vehicles").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun `no handler, no affordance`() {
        setTabs(twoVehicles, includeUnassigned = false, onManageVehicles = null)
        compose.onNodeWithContentDescription("Manage vehicles").assertDoesNotExist()
    }

    @Test
    fun `the affordance stays put while the strip scrolls`() {
        // It sits outside the ScrollableTabRow precisely so a long garage
        // can't push it out of reach — displayed without any scrolling even
        // when the last tab is off-screen.
        setNarrowTabs(scope = VehicleScope.All, onManageVehicles = {})
        compose.onNodeWithTag("scopeTab:Unassigned").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Manage vehicles").assertIsDisplayed()
    }

    /* ---------------------------- Overflow behavior --------------------------- */

    // ScrollableTabRow composes every tab and scrolls the strip, which a static
    // preview cannot show — it renders one clipped frame, indistinguishable
    // from a strip that can't scroll at all. This was verified by hand once (on
    // a phone, with dummy vehicles added to force the overflow); these two pin
    // that session's findings so nobody has to repeat it.

    private val manyVehicles = listOf(
        Vehicle(id = 1L, name = "EV6"),
        Vehicle(id = 2L, name = "Mach-E"),
        Vehicle(id = 3L, name = "Ioniq 5"),
        Vehicle(id = 4L, name = "Model Y"),
    )

    private fun setNarrowTabs(
        scope: VehicleScope,
        onSelect: (VehicleScope) -> Unit = {},
        onManageVehicles: (() -> Unit)? = null,
    ) {
        compose.setContent {
            EvsctTheme {
                // Narrow enough that six tabs must overflow: the strip's
                // minimum tab width alone exceeds 300dp several times over.
                Box(Modifier.width(300.dp)) {
                    VehicleScopeTabs(
                        vehicles = manyVehicles,
                        includeUnassigned = true,
                        scope = scope,
                        onSelect = onSelect,
                        onManageVehicles = onManageVehicles,
                    )
                }
            }
        }
    }

    @Test
    fun `Unassigned stays reachable by scrolling when the tabs overflow`() {
        var selected: VehicleScope? = null
        setNarrowTabs(scope = VehicleScope.All, onSelect = { selected = it })
        // Off-screen first, deliberately: without this a strip that shrank its
        // tabs to fit would pass the rest without ever scrolling.
        compose.onNodeWithTag("scopeTab:Unassigned").assertIsNotDisplayed()
        compose.onNodeWithTag("scopeTab:Unassigned").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(VehicleScope.Unassigned, selected) }
    }

    @Test
    fun `arriving with the last tab selected scrolls it into view`() {
        // The other half of what a still frame can't show. If this regressed,
        // the Log would open scoped to Unassigned with the selection invisible
        // off the right edge — a screen that looks like nothing is selected.
        setNarrowTabs(scope = VehicleScope.Unassigned)
        compose.onNodeWithTag("scopeTab:Unassigned").assertIsDisplayed()
    }
}
