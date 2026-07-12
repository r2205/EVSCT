package com.evsct.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Defaulting [dynamicColor] to false means the app keeps its hand-tuned
 * EV-green palette regardless of the device wallpaper. Pixel wallpaper
 * tones often reduce the scheme to greys.
 */
@Composable
fun EvsctTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> EvsctDarkScheme
        else -> EvsctLightScheme
    }
    // Charging-type accents follow dark/light alongside the scheme — the
    // fixed light trios washed out on dark surfaces.
    val accents = if (darkTheme) DarkEvAccents else LightEvAccents
    CompositionLocalProvider(LocalEvAccents provides accents) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
