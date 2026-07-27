package com.evsct.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evsct.app.ui.theme.EvsctTheme

/*
 * The middle path of adaptive layout (#3), deliberately: phones in portrait
 * keep exactly the layout they have, and wider windows — a phone turned
 * sideways, or a tablet if one ever appears — stop pretending to be narrow
 * phones. Two tools and no more: [isWideWindow] flips the navigation chrome
 * to a side rail, and [readableFormWidth] stops form fields stretching.
 * Two-pane layouts and multi-column grids are the part of #3 not taken —
 * struck knowingly, like #11, because nothing in this app's real usage
 * justifies them yet.
 */

/**
 * True when the window is wider than Material's compact-width bucket — a
 * phone in landscape, or a tablet. 600dp is Material's own breakpoint for
 * that bucket, not a number invented here.
 *
 * Reads screenWidthDp, which ignores the font-scale setting: growing text
 * must never relocate the navigation.
 */
@Composable
fun isWideWindow(): Boolean =
    LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_MIN_DP

/** Public so tests and previews can sit exactly on the boundary by name. */
const val WIDE_WINDOW_MIN_DP = 600

/**
 * Caps a form column at a readable width and centres it, leaving narrow
 * windows untouched: below the cap this resolves to a plain fillMaxWidth,
 * so a phone in portrait renders bit-identically to before.
 *
 * The chain reads oddly and each link is load-bearing: fill claims the
 * window, wrapContent un-pins the minimum so the content may be narrower
 * and centres whatever it measures, widthIn caps that measurement, and the
 * second fill pushes the content back out to the cap so fields inside a
 * 900dp window get 640dp — not their intrinsic width.
 */
fun Modifier.readableFormWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = READABLE_FORM_MAX_WIDTH)
    .fillMaxWidth()

/** ~two text fields side by side; wide enough to never bind in portrait. */
val READABLE_FORM_MAX_WIDTH: Dp = 640.dp

/* ------------------------------- Previews -------------------------------- */

@Composable
private fun PreviewFormFields() {
    Column(
        modifier = Modifier.readableFormWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = "42.5",
            onValueChange = {},
            label = { androidx.compose.material3.Text("Energy (kWh)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = "24.50",
            onValueChange = {},
            label = { androidx.compose.material3.Text("Total cost") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The case the cap exists for: fields centre at 640dp instead of spanning
 *  the whole window. */
@Preview(name = "Form width — 800dp window", showBackground = true, widthDp = 800)
@Composable
private fun PreviewFormWidthWide() {
    EvsctTheme { PreviewFormFields() }
}

/** Below the cap the modifier must be invisible — this should look exactly
 *  like every form has always looked on a phone. */
@Preview(name = "Form width — 400dp window", showBackground = true, widthDp = 400)
@Composable
private fun PreviewFormWidthPhone() {
    EvsctTheme { PreviewFormFields() }
}
