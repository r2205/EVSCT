package com.evsct.app.ui.sessions

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.ui.theme.EvsctTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The session form's folding rules, from #50. These are the four guarantees
 * that PR shipped as prose — a filled group is open, a collapsed group says
 * what it holds, a manual collapse sticks, a hint forces its group open — and
 * until now nothing re-verified them.
 *
 * Runs on the JVM via Robolectric inside testDebugUnitTest, so CI executes
 * these on every PR with no emulator involved.
 *
 * The content probe is a bare Text rather than a real field: what's under test
 * is the fold, and the assertion "probe exists" is exactly "the fold is open",
 * because AnimatedVisibility removes a collapsed section's content from the
 * tree entirely rather than hiding it.
 */
@RunWith(AndroidJUnit4::class)
class CollapsibleSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val header = "sectionHeader:Battery & wait"

    private fun setSection(
        filledCount: Int,
        startExpanded: Boolean = false,
        demandsAttention: Boolean = false,
    ) {
        compose.setContent {
            EvsctTheme {
                CollapsibleSection(
                    title = "Battery & wait",
                    filledCount = filledCount,
                    startExpanded = startExpanded,
                    demandsAttention = demandsAttention,
                ) {
                    Text("content probe")
                }
            }
        }
    }

    @Test
    fun `an empty group starts collapsed, with no badge`() {
        setSection(filledCount = 0)
        compose.onNodeWithText("content probe").assertDoesNotExist()
        compose.onNodeWithText("0 set").assertDoesNotExist()
    }

    @Test
    fun `startExpanded opens an empty group — the entering-a-charge mode`() {
        setSection(filledCount = 0, startExpanded = true)
        compose.onNodeWithText("content probe").assertExists()
    }

    @Test
    fun `a group holding values is open on arrival`() {
        setSection(filledCount = 2)
        compose.onNodeWithText("content probe").assertExists()
    }

    @Test
    fun `collapsing a filled group shows the n-set badge`() {
        setSection(filledCount = 2)
        compose.onNodeWithTag(header).performClick()
        compose.onNodeWithText("content probe").assertDoesNotExist()
        // The state writing the previews said no human had likely ever seen:
        // reachable only through a manual collapse, never on arrival.
        compose.onNodeWithText("2 set").assertExists()
    }

    @Test
    fun `reopening after a manual collapse hides the badge again`() {
        setSection(filledCount = 2)
        compose.onNodeWithTag(header).performClick()
        compose.onNodeWithTag(header).performClick()
        compose.onNodeWithText("content probe").assertExists()
        compose.onNodeWithText("2 set").assertDoesNotExist()
    }

    @Test
    fun `a manual collapse sticks even as values keep arriving`() {
        // The auto-open re-check fires when filledCount changes; userToggled
        // is what stops it fighting the user on every keystroke.
        var filled by mutableStateOf(1)
        compose.setContent {
            EvsctTheme {
                CollapsibleSection(title = "Battery & wait", filledCount = filled) {
                    Text("content probe")
                }
            }
        }
        compose.onNodeWithTag(header).performClick()
        compose.onNodeWithText("content probe").assertDoesNotExist()

        compose.runOnIdle { filled = 2 }
        compose.onNodeWithText("content probe").assertDoesNotExist()
        compose.onNodeWithText("2 set").assertExists()
    }

    @Test
    fun `a validation hint forces the group open, overriding a manual collapse`() {
        // Without this, the hint card names a problem with no way to reach the
        // field. It must beat userToggled — the one thing that outranks the
        // user's own collapse.
        var attention by mutableStateOf(false)
        compose.setContent {
            EvsctTheme {
                CollapsibleSection(
                    title = "Battery & wait",
                    filledCount = 1,
                    demandsAttention = attention,
                ) {
                    Text("content probe")
                }
            }
        }
        compose.onNodeWithTag(header).performClick()
        compose.onNodeWithText("content probe").assertDoesNotExist()

        compose.runOnIdle { attention = true }
        compose.onNodeWithText("content probe").assertExists()
    }
}
