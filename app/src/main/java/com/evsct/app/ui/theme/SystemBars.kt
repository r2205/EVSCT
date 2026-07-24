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
 * The app draws edge-to-edge, so the system's clock, battery, and gesture
 * pill sit directly on whatever the app paints beneath them — and their
 * light/dark polarity is a window flag, not something the color scheme
 * reaches. `enableEdgeToEdge()`'s default derives that flag from the system
 * night setting, which is wrong here twice over:
 *
 *  - Every top app bar is painted `primary` (see any screen's
 *    `topAppBarColors`), which is a *dark* emerald in the light scheme and a
 *    *light* mint in the dark one — the inverse of what the night setting
 *    implies. Dark mode was putting white status icons on `#8DD8A4` at about
 *    1.7:1, right next to a near-black title on the same bar.
 *  - The in-app theme override (Settings → Appearance) can disagree with the
 *    system setting outright, and the flag never learned about it.
 *
 * So polarity is derived from the color actually underneath each bar.
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
 * App-wide default, applied once near the root: status-bar icons contrast
 * with the `primary` top app bar, navigation-bar icons with the `surface`
 * that content scrolls on. Re-applies whenever the scheme changes — a theme
 * toggle in Settings or a system dark-mode flip.
 */
@Composable
fun SystemBarIconsFollowTheme() {
    val controller = rememberBarsController() ?: return
    val statusBackdrop = MaterialTheme.colorScheme.primary
    val navBackdrop = MaterialTheme.colorScheme.surface
    // DisposableEffect keyed on the backdrops, not SideEffect: this must
    // re-apply only when the colors actually change, so an incidental
    // recomposition up here can't clobber a nested [StatusBarIconsFor]
    // override that's currently in effect.
    DisposableEffect(controller, statusBackdrop, navBackdrop) {
        controller.isAppearanceLightStatusBars = statusBackdrop.needsDarkIcons()
        controller.isAppearanceLightNavigationBars = navBackdrop.needsDarkIcons()
        onDispose { }
    }
}

/**
 * Screen-scoped override for a contextual top bar that isn't `primary` — the
 * Log's selection mode, whose `secondaryContainer` runs the opposite polarity
 * in both schemes. Restores the theme default on the way out, so leaving
 * selection mode needs no matching call.
 */
@Composable
fun StatusBarIconsFor(backdrop: Color) {
    val controller = rememberBarsController() ?: return
    val default = MaterialTheme.colorScheme.primary
    DisposableEffect(controller, backdrop, default) {
        controller.isAppearanceLightStatusBars = backdrop.needsDarkIcons()
        onDispose {
            controller.isAppearanceLightStatusBars = default.needsDarkIcons()
        }
    }
}
