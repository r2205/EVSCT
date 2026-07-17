package com.evsct.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evsct.app.R

/**
 * Top-app-bar title with the EVSCT badge mark leading the screen name.
 *
 * Only the five standing headers use this (Log, Map, Stats, Trips, Settings).
 * Contextual bars (the Log's selection mode) and pushed detail screens keep
 * plain titles, so the mark doubles as a "top of the app" cue.
 *
 * The mark inherits the bar's titleContentColor through the Icon tint
 * default (LocalContentColor), so it tracks the title text in both themes:
 * white on deep emerald in light, dark green on mint in dark.
 *
 * [subtitle] adds the second bodySmall line the Map header shows; the mark
 * centers against the two-line block.
 */
@Composable
fun EvsctBarTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_evsct_mark),
            // Decorative: the title text alongside carries the screen name.
            contentDescription = null,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
