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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal bar list shared by the Stats screen and the Year Recap. Each
 * row is `[label] [bar———] [value]`, bars normalized against the largest
 * value in [items]. Any non-zero value renders at least [MIN_BAR_FRACTION]
 * of the track so small-but-real entries don't disappear next to a big
 * outlier. When [onRowClick] is set, rows become tappable and grow a
 * trailing chevron (how Stats' brand drill-down advertises itself).
 */
@Composable
fun BarList(
    items: List<Pair<String, Double>>,
    labelWidth: Dp,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 82.dp,
    onRowClick: ((String) -> Unit)? = null,
) {
    val maxValue = items.maxOfOrNull { it.second } ?: 0.0
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
                    modifier = Modifier.width(labelWidth),
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
                    modifier = Modifier.width(valueWidth),
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
