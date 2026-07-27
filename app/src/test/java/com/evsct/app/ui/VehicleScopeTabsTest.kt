package com.evsct.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    ) {
        compose.setContent {
            EvsctTheme {
                VehicleScopeTabs(
                    vehicles = vehicles,
                    includeUnassigned = includeUnassigned,
                    scope = scope,
                    onSelect = onSelect,
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
}
