package com.evsct.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Icon polarity for the system status and navigation bars.
 *
 * The app draws edge-to-edge, so the system's clock, battery, and gesture pill
 * sit on top of whatever the app paints there — and whether they render light
 * or dark is a window flag, not something the color scheme reaches.
 *
 * What sits under them is the root Scaffold's own container, *not* the
 * screen's top app bar. `EvsctNavGraph`'s Scaffold declares no `topBar`, so it
 * offsets the NavHost below the status bar and consumes those insets, which
 * leaves each screen's `TopAppBar` starting underneath the status bar rather
 * than painting behind it. The green bar is a red herring: the strip behind
 * the clock is `background`, and behind the gesture pill is the navigation
 * bar's container.
 *
 * `enableEdgeToEdge()`'s default polarity gets that right, but only while the
 * app's theme agrees with the system's: it reads the system night setting and
 * never hears about the in-app override in Settings → Appearance. Forcing
 * light while the system is dark (or the reverse) left the icons inverted
 * against the scheme the app actually drew. Deriving the flag from the
 * resolved scheme fixes that case, and keeps holding if the palette moves.
 */

/** True when this color is light enough to need dark icons on top of it. */
private fun Color.needsDarkIcons(): Boolean = luminance() > 0.5f

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Null under `@Preview`, which composes without a window to configure. */
@Composable
private fun rememberBarsController(): WindowInsetsControllerCompat? {
    val view = LocalView.current
    if (view.isInEditMode) return null
    return remember(view) {
        val window = view.context.findActivity()?.window ?: return@remember null
        WindowCompat.getInsetsController(window, view)
    }
}

/**
 * Applied once near the root, against the two colors the system bars actually
 * overlay. Re-applies whenever the scheme changes — a theme toggle in Settings
 * or a system dark-mode flip.
 */
@Composable
fun SystemBarIconsFollowTheme() {
    val controller = rememberBarsController() ?: return
    val statusBackdrop = MaterialTheme.colorScheme.background
    val navBackdrop = MaterialTheme.colorScheme.surfaceContainer
    // Keyed on the backdrops rather than SideEffect, so this runs when the
    // colors actually change instead of on every incidental recomposition.
    DisposableEffect(controller, statusBackdrop, navBackdrop) {
        controller.isAppearanceLightStatusBars = statusBackdrop.needsDarkIcons()
        controller.isAppearanceLightNavigationBars = navBackdrop.needsDarkIcons()
        onDispose { }
    }
}
