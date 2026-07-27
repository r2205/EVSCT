package com.evsct.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evsct.app.ui.theme.EvsctTheme
import com.evsct.app.util.Format

/**
 * Horizontal bar list shared by the Stats screen and the Year Recap. Each
 * row is `[label] [bar———] [value]`, bars normalized against the largest
 * value in [items]. Any non-zero value renders at least [MIN_BAR_FRACTION]
 * of the track so small-but-real entries don't disappear next to a big
 * outlier. When [onRowClick] is set, rows become tappable and grow a
 * trailing chevron (how Stats' brand drill-down advertises itself).
 *
 * [labelWidth] and [valueWidth] name widths at normal font scale; internally
 * both are multiplied by the user's font scale. They hold text, which is sized
 * in sp and grows with the font setting, while dp does not — at 2x that gap
 * broke "$148.20 CAD" mid-number, leaving "0 CAD" alone on a second line. The
 * bar track absorbs what the scaled columns take, equally in every row, so
 * bars stay comparable across rows; at large scales they get short, which is
 * the right trade — the numbers are the data, the bars are the glance.
 */
@Composable
fun BarList(
    items: List<Pair<String, Double>>,
    labelWidth: Dp,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    // 96 fits "$1,284.50 CAD" — a four-digit total with grouping — on one
    // line; the old 82 held only eleven-odd characters, so real Stats data
    // could word-wrap "CAD" onto a second line at normal font scale.
    valueWidth: Dp = 96.dp,
    onRowClick: ((String) -> Unit)? = null,
) {
    val maxValue = items.maxOfOrNull { it.second } ?: 0.0
    val fontScale = LocalDensity.current.fontScale
    val scaledLabelWidth = labelWidth * fontScale
    val scaledValueWidth = valueWidth * fontScale
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // One merged TalkBack node per row, phrased as
                    // "label: value" — without this the bar is an opaque
                    // colored box between two loose text fragments.
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$label: ${formatValue(value)}"
                    }
                    .then(
                        if (onRowClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onRowClick(label) }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(scaledLabelWidth),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        // outlineVariant, not surfaceVariant: in light mode
                        // surfaceVariant sits at nearly the card's own tone
                        // and the empty track vanished (same fix as the
                        // heatmap's zero cells); in dark the two slots are
                        // the same color, so nothing changes there.
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    val frac = if (maxValue > 0) (value / maxValue).toFloat() else 0f
                    val target = if (frac > 0f) frac.coerceAtLeast(MIN_BAR_FRACTION) else 0f
                    // Bars grow into place (and re-flow when the data set
                    // changes, e.g. flipping months ↔ years on Stats).
                    val animatedFrac by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 350),
                        label = "barFill",
                    )
                    if (animatedFrac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFrac)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatValue(value),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(scaledValueWidth),
                )
                if (onRowClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Floor for a non-zero bar: a $2 top-up next to a $400 month should still
 *  paint a visible sliver, not read as zero. */
private const val MIN_BAR_FRACTION = 0.02f

/* ------------------------------- Previews -------------------------------- */

/*
 * Two reasons this one is worth previewing rather than trusting.
 *
 * The 2x preview below asked whether fixed-dp columns survive scaled text, and
 * the answer was no — "January" wrapped, and "$148.20 CAD" broke mid-number.
 * The columns scale with fontScale now, so its job has flipped from question
 * to regression watch: if it ever shows a wrapped label or a split value
 * again, the scaling has been lost.
 *
 * And the tappable variant is where #11 was struck. The brand drill-down's rows
 * measure ~17dp against a 48dp guideline, and the 48dp minimum was reverted in
 * 520af92 because it roughly doubled the card's height. The note in that commit
 * says any future fix needs the target without the height — capping the row
 * count, or making the card the affordance. These previews are where that gets
 * judged instead of guessed at.
 */

private val previewBrands = listOf(
    "Petro-Canada" to 486.20,
    "Electrify Canada" to 312.75,
    "Flo" to 208.40,
    "ChargePoint" to 96.10,
    "Tesla" to 41.85,
)

@Preview(name = "Bars — brands by spend", showBackground = true, widthDp = 400)
@Composable
private fun PreviewBarListBrands() {
    EvsctTheme {
        BarList(
            items = previewBrands,
            labelWidth = 96.dp,
            formatValue = { Format.money(it, "CAD") },
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Tappable rows, which is the brand drill-down. The thing to judge is whether
 *  a row is a plausible tap target at this height — the question #11 left open. */
@Preview(name = "Bars — tappable rows", showBackground = true, widthDp = 400)
@Composable
private fun PreviewBarListTappable() {
    EvsctTheme {
        BarList(
            items = previewBrands,
            labelWidth = 96.dp,
            formatValue = { Format.money(it, "CAD") },
            modifier = Modifier.padding(12.dp),
            onRowClick = {},
        )
    }
}

/** Stats' real labelWidth of 64.dp at 2x font. Before the columns scaled with
 *  fontScale this wrapped the months and split the values mid-number; now it's
 *  the watch that they hold one line, with the bars giving the ground. */
@Preview(
    name = "Bars — 64dp labels at 2x font",
    showBackground = true,
    widthDp = 400,
    fontScale = 2f,
)
@Composable
private fun PreviewBarListLargeFont() {
    EvsctTheme {
        BarList(
            items = listOf(
                "January" to 148.20,
                "February" to 96.75,
                "March" to 210.40,
            ),
            labelWidth = 64.dp,
            formatValue = { Format.money(it, "CAD") },
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Degenerate data the screens can genuinely produce: one row, where the single
 *  value is also the maximum and the bar fills completely, and a set of zeros,
 *  where maxValue is 0 and every bar collapses to the empty track. */
@Preview(name = "Bars — single row and all zeros", showBackground = true, widthDp = 400)
@Composable
private fun PreviewBarListDegenerate() {
    EvsctTheme {
        Column(modifier = Modifier.padding(12.dp)) {
            BarList(
                items = listOf("Petro-Canada" to 486.20),
                labelWidth = 96.dp,
                formatValue = { Format.money(it, "CAD") },
            )
            Spacer(Modifier.height(16.dp))
            BarList(
                items = listOf("April" to 0.0, "May" to 0.0),
                labelWidth = 96.dp,
                formatValue = { Format.money(it, "CAD") },
            )
        }
    }
}
