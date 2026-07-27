package com.evsct.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.evsct.app.ui.theme.EvsctTheme

/**
 * Friendly empty-state placeholder for screens with no data yet — first-run
 * onboarding, an empty filter result, or a screen that depends on data from
 * another screen.
 *
 * Pass [actionLabel] + [onAction] together to render a "Go to X" button that
 * routes the user to the screen they need to populate first.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/* ------------------------------- Previews -------------------------------- */

/*
 * #51 gave Trips and Vehicles an action button and routed the Log's hand-written
 * copy through here, and none of it was ever looked at outside a running app —
 * which is the awkward part of an empty state: reaching it means deleting your
 * data.
 *
 * The pairs below are chosen to cover the two shapes callers actually use
 * (with and without an action) and the two ways the layout can go wrong: a body
 * long enough to wrap, and a font scale large enough to push the button off.
 */

@Preview(name = "Empty — with action", showBackground = true, widthDp = 400, heightDp = 340)
@Composable
private fun PreviewEmptyStateWithAction() {
    EvsctTheme {
        EmptyState(
            icon = Icons.Default.DirectionsCar,
            title = "No vehicles yet",
            body = "Add the car you charge so sessions can be tracked against it.",
            actionLabel = "Add vehicle",
            onAction = {},
        )
    }
}

/** The Stats variant: no action, because the route out is another screen. */
@Preview(name = "Empty — no action", showBackground = true, widthDp = 400, heightDp = 340)
@Composable
private fun PreviewEmptyStateNoAction() {
    EvsctTheme {
        EmptyState(
            icon = Icons.Default.QueryStats,
            title = "No sessions yet",
            body = "Stats appear here once you've logged at least one charging session.",
        )
    }
}

/**
 * The case #51 actually fixed. The old hand-written copy in SessionListScreen
 * lacked centred text, so a wrapping body hugged the left inside a centred
 * block. Two wrapped lines is what makes that visible, so it's worth a preview
 * even now that the copy is gone.
 */
@Preview(name = "Empty — wrapping body", showBackground = true, widthDp = 400, heightDp = 380)
@Composable
private fun PreviewEmptyStateWrappingBody() {
    EvsctTheme {
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = "No matching sessions",
            body = "Nothing matches the filters and search text currently applied. " +
                "Clearing them brings the whole log back.",
            actionLabel = "Clear filters",
            onAction = {},
        )
    }
}

/** Fixed 72dp disc, growing text, and a button below it — the combination most
 *  likely to overflow the height it's given. */
@Preview(
    name = "Empty — 2x font",
    showBackground = true,
    widthDp = 400,
    heightDp = 520,
    fontScale = 2f,
)
@Composable
private fun PreviewEmptyStateLargeFont() {
    EvsctTheme {
        EmptyState(
            icon = Icons.Default.DirectionsCar,
            title = "No vehicles yet",
            body = "Add the car you charge so sessions can be tracked against it.",
            actionLabel = "Add vehicle",
            onAction = {},
        )
    }
}
