package com.evsct.app.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evsct.app.R

/**
 * Top-app-bar title with the EVSCT banner lockup leading the screen name.
 *
 * Only the five standing headers use this (Log, Map, Stats, Trips, Settings).
 * Contextual bars (the Log's selection mode) and pushed detail screens keep
 * plain titles, so the lockup doubles as a "top of the app" cue.
 *
 * The lockup is drawn in two registered layers because one Icon tint would
 * recolour everything: the base (letters + road) tints to the bar's
 * titleContentColor and so tracks the title in both themes, while the accent
 * (the V and bolt) stays brand amber in both — the colour split that keeps
 * it reading as a logo rather than more header text.
 *
 * The lockup is 77dp wide, so the screens that show it use their short tab
 * labels ("Log", "Map") as titles — brand and long titles don't both fit
 * ahead of a full action row on 360dp-wide phones. Title and [subtitle] are
 * pinned to one line each: on narrow screens the Map subtitle can run out of
 * room, and an ellipsis beats wrapping inside the 64dp bar.
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
        Box {
            Icon(
                painter = painterResource(R.drawable.ic_evsct_lockup),
                // Decorative: the title text alongside carries the screen
                // name (for this and the accent layer below).
                contentDescription = null,
            )
            Icon(
                painter = painterResource(R.drawable.ic_evsct_lockup_accent),
                contentDescription = null,
                // Unspecified skips the tint filter, keeping the amber.
                tint = Color.Unspecified,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
