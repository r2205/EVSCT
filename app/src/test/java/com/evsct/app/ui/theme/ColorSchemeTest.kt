package com.evsct.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the neutral half of the palette.
 *
 * [EvsctLightScheme] and [EvsctDarkScheme] name every role explicitly, and the
 * reason is that the omissions are invisible in this file: an unset role
 * silently takes Material's baseline neutral, which is toned off the baseline
 * purple. `surfaceContainer` arriving as #F3EDF7 is what put a lavender bar
 * under the gesture pill, and the rest of the ramp reaches cards, dialogs,
 * menus, sheets, and filled text fields.
 *
 * So these tests assert the property, not the constants — restating hex values
 * would only prove the file parses. What matters is that no role that should
 * carry the tint is blue-dominant, and that a role added later can't quietly
 * reintroduce one.
 */
class ColorSchemeTest {

    /** WCAG contrast, the ratio Material's own tonal spacing is built around. */
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Every role that should carry the palette's green tint, by name so a
     * failure says which one drifted. Mostly the neutral family, plus
     * `inversePrimary` — not a neutral, but it was one of the roles sitting at
     * a baseline default (#D0BCFF), which is what this sweep is for.
     */
    private fun ColorScheme.greenTintedRoles(): List<Pair<String, Color>> = listOf(
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "inversePrimary" to inversePrimary,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
    )

    /* --------------------------- No purple left --------------------------- */

    // Green-tinted neutrals put green at or above the other two channels;
    // Material's baseline neutrals put blue on top. Testing "blue never
    // exceeds green" catches a role left at its default without pinning the
    // exact tone — and it passes for a true grey or white, which are neutral
    // in any palette. It can't run over the whole scheme, though: `tertiary`
    // is deliberately an electric blue-grey and would fail on purpose.
    @Test
    fun `no tinted role in the light scheme is blue-dominant`() {
        EvsctLightScheme.greenTintedRoles().forEach { (name, color) ->
            assertTrue(
                color.blue <= color.green,
                "$name is bluer than it is green — likely left at a Material default",
            )
        }
    }

    @Test
    fun `no tinted role in the dark scheme is blue-dominant`() {
        EvsctDarkScheme.greenTintedRoles().forEach { (name, color) ->
            assertTrue(
                color.blue <= color.green,
                "$name is bluer than it is green — likely left at a Material default",
            )
        }
    }

    /* ---------------------------- Ramp ordering --------------------------- */

    // The container roles carry elevation by lightness alone, so a transposed
    // pair would flatten the stacking without changing any single color enough
    // to notice. Listed in tone order, which runs opposite ways per scheme.
    @Test
    fun `light container ramp darkens in tone order`() {
        val ramp = with(EvsctLightScheme) {
            listOf(
                "surfaceContainerLowest" to surfaceContainerLowest,
                "surfaceBright" to surfaceBright,
                "surfaceContainerLow" to surfaceContainerLow,
                "surfaceContainer" to surfaceContainer,
                "surfaceContainerHigh" to surfaceContainerHigh,
                "surfaceContainerHighest" to surfaceContainerHighest,
                "surfaceDim" to surfaceDim,
            )
        }
        ramp.zipWithNext { (loName, lo), (hiName, hi) ->
            assertTrue(
                lo.luminance() > hi.luminance(),
                "$loName should be lighter than $hiName",
            )
        }
    }

    @Test
    fun `dark container ramp lightens in tone order`() {
        val ramp = with(EvsctDarkScheme) {
            listOf(
                "surfaceContainerLowest" to surfaceContainerLowest,
                "surfaceDim" to surfaceDim,
                "surfaceContainerLow" to surfaceContainerLow,
                "surfaceContainer" to surfaceContainer,
                "surfaceContainerHigh" to surfaceContainerHigh,
                "surfaceContainerHighest" to surfaceContainerHighest,
                "surfaceBright" to surfaceBright,
            )
        }
        ramp.zipWithNext { (loName, lo), (hiName, hi) ->
            assertTrue(
                lo.luminance() < hi.luminance(),
                "$loName should be darker than $hiName",
            )
        }
    }

    /* ------------------------- Downstream contracts ----------------------- */

    // SystemBarIconsFollowTheme derives gesture-pill icon polarity from
    // surfaceContainer's luminance against a 0.5 threshold. Retoning the role
    // moved that value; this pins that it stayed on the same side of the line
    // in both schemes, so the icons don't invert.
    @Test
    fun `surfaceContainer keeps gesture-pill icons on the right polarity`() {
        assertTrue(
            EvsctLightScheme.surfaceContainer.luminance() > 0.5f,
            "light navigation bar must be light enough for dark icons",
        )
        assertTrue(
            EvsctDarkScheme.surfaceContainer.luminance() < 0.5f,
            "dark navigation bar must be dark enough for light icons",
        )
    }

    // The dark nav bar carries extra chroma so the green actually registers at
    // near-black, and the thing that caps how far it can go is the selected-tab
    // pill: NavigationBarItem draws that in secondaryContainer, which reads as
    // a pill only by sitting slightly lighter than the bar behind it. Greening
    // the bar walks it toward the pill's own tone.
    //
    // The floor is 1.5:1, which is not a standards figure — WCAG would want 3:1
    // for a UI boundary and Material's own pairing here only manages 1.73:1, so
    // there is no spec to lean on. It is set just under the shipped 1.72:1 to
    // catch a bar retoned far enough that the selected tab stops being visible:
    // a deep emerald measures 1.02:1, and it takes the glyph colors with it,
    // since `onSecondaryContainer` and `onSurfaceVariant` are both pale greens
    // that only separate against a dark bar.
    //
    // Dark only. The light pill is a pale #D2E8CD on a near-white bar and
    // manages 1.14:1 — it measured 1.13:1 against the baseline container too,
    // so this is the palette's own pairing rather than anything the retone did,
    // and pinning it would assert a number no one chose. Light mode leans on
    // the glyph instead: #0D1F0E against #414940 separates by 1.85:1, which is
    // not much on its own but is the selected item reading as markedly darker
    // rather than as a different hue — and it is what the emerald case loses,
    // where the two glyph colors close to 1.31:1.
    @Test
    fun `the selected tab stays visible against the dark bar`() {
        val ratio = contrastRatio(
            EvsctDarkScheme.secondaryContainer,
            EvsctDarkScheme.surfaceContainer,
        )
        assertTrue(ratio >= 1.5f, "pill against bar was $ratio")
    }

    // surfaceContainerHighest is the default container for filled text fields
    // and unstyled Cards, both of which draw onSurface text on top.
    @Test
    fun `onSurface stays readable on the highest container`() {
        listOf("light" to EvsctLightScheme, "dark" to EvsctDarkScheme)
            .forEach { (name, scheme) ->
                val ratio = contrastRatio(scheme.onSurface, scheme.surfaceContainerHighest)
                assertTrue(ratio >= 4.5f, "$name scheme: onSurface contrast was $ratio")
            }
    }

    // Snackbar reads all three inverse roles, so a retoning that touched one
    // scheme alone would show up as a snackbar that no longer matches itself
    // across a theme switch. Two of the pairings are exact swaps and pin
    // cheaply; the dark tone shared by both schemes is the third.
    //
    // Only `inverseSurface` crosses over exactly. `inverseOnSurface` does not:
    // it is tone 95 in the light scheme against the dark scheme's tone 90, so
    // asserting a full mirror here would be asserting something untrue.
    @Test
    fun `the inverse roles line up across the two schemes`() {
        assertEquals(EvsctLightScheme.inverseSurface, EvsctDarkScheme.inverseOnSurface)
        assertEquals(EvsctLightScheme.inversePrimary, EvsctDarkScheme.primary)
        assertEquals(EvsctDarkScheme.inversePrimary, EvsctLightScheme.primary)
    }

    // Snackbar puts inversePrimary on inverseSurface for its action label. The
    // floor is 3:1 rather than 4.5:1 on purpose: Material's own pairing here is
    // a tone-40 primary on a tone-90 surface, which lands just under 5:1, and
    // asserting nearer to that would make this a tripwire for retoning rather
    // than for a genuine regression.
    @Test
    fun `the snackbar action label stays readable`() {
        listOf("light" to EvsctLightScheme, "dark" to EvsctDarkScheme)
            .forEach { (name, scheme) ->
                val ratio = contrastRatio(scheme.inversePrimary, scheme.inverseSurface)
                assertTrue(ratio >= 3.0f, "$name scheme: action label contrast was $ratio")
            }
    }
}
