package com.evsct.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A card's stat columns: side by side while they fit, one per line when they
 * don't.
 *
 * Both the Log's summary card and the Stats headline lay out two or three stat
 * columns. They used to be plain `Row`s, which clipped the last column at large
 * font scale; #50 made them `FlowRow`s, which stopped the clipping and stopped
 * there. Wrapping is not the same as looking right — a row that breaks 2-then-1
 * leaves the last column centred under the *gap* between the two above it,
 * lining up with nothing. The Stats headline's second row is the sharper case,
 * holding only two stats: break that and each floats alone on its own line.
 *
 * Past [STACK_STATS_FONT_SCALE] the honest layout is one stat per line. The
 * `FlowRow` stays underneath as the fallback for what font scale can't predict,
 * mainly a long mixed-currency total at normal size.
 *
 * [content] receives the modifier its stats should carry — full width when
 * stacked so each centres across the card, nothing when laid out in a row. It's
 * handed down rather than left to the caller so that the container and the
 * modifier can't disagree, which is the bug this indirection is worth avoiding.
 * Being a plain `@Composable () -> Unit` rather than a scoped lambda means stats
 * can't use `weight` or `align` — neither of which would mean the same thing in
 * both branches anyway.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatColumns(
    modifier: Modifier = Modifier,
    content: @Composable (statModifier: Modifier) -> Unit,
) {
    if (statsShouldStack()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceAround,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content(Modifier)
        }
    }
}

@Composable
private fun statsShouldStack(): Boolean =
    LocalDensity.current.fontScale >= STACK_STATS_FONT_SCALE

/**
 * 1.5 rather than a width measurement, because what breaks the row is the text
 * growing, and Android's accessibility font sizes step 1.0 / 1.15 / 1.3 / 1.5 /
 * 1.8 / 2.0 — so this splits at a step boundary instead of part-way through one.
 * The two smaller steps still fit three columns across on a phone.
 *
 * Public so previews can render either side of it by name instead of repeating
 * the number.
 */
const val STACK_STATS_FONT_SCALE = 1.5f
