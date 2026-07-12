package com.evsct.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* ----------------------------- Light tones ----------------------------- */

private val md_primary_light = Color(0xFF1F6F43)            // deep emerald
private val md_onPrimary_light = Color(0xFFFFFFFF)
private val md_primaryContainer_light = Color(0xFFA8F5BF)
private val md_onPrimaryContainer_light = Color(0xFF00210E)

private val md_secondary_light = Color(0xFF4F634D)          // sage
private val md_onSecondary_light = Color(0xFFFFFFFF)
private val md_secondaryContainer_light = Color(0xFFD2E8CD)
private val md_onSecondaryContainer_light = Color(0xFF0D1F0E)

private val md_tertiary_light = Color(0xFF3A6470)           // electric blue-grey
private val md_onTertiary_light = Color(0xFFFFFFFF)
private val md_tertiaryContainer_light = Color(0xFFBEE9F8)
private val md_onTertiaryContainer_light = Color(0xFF001F26)

private val md_error_light = Color(0xFFBA1A1A)
private val md_onError_light = Color(0xFFFFFFFF)
private val md_errorContainer_light = Color(0xFFFFDAD6)
private val md_onErrorContainer_light = Color(0xFF410002)

private val md_background_light = Color(0xFFF7FBF3)
private val md_onBackground_light = Color(0xFF181D17)
private val md_surface_light = Color(0xFFF7FBF3)
private val md_onSurface_light = Color(0xFF181D17)
private val md_surfaceVariant_light = Color(0xFFDDE5D8)
private val md_onSurfaceVariant_light = Color(0xFF414940)
private val md_outline_light = Color(0xFF727970)
private val md_outlineVariant_light = Color(0xFFC1C9BD)

/* ----------------------------- Dark tones ------------------------------ */

private val md_primary_dark = Color(0xFF8DD8A4)             // bright mint, pops on dark
private val md_onPrimary_dark = Color(0xFF00391C)
private val md_primaryContainer_dark = Color(0xFF00532D)
private val md_onPrimaryContainer_dark = Color(0xFFA8F5BF)

private val md_secondary_dark = Color(0xFFB6CCB1)
private val md_onSecondary_dark = Color(0xFF223521)
private val md_secondaryContainer_dark = Color(0xFF384B36)
private val md_onSecondaryContainer_dark = Color(0xFFD2E8CD)

private val md_tertiary_dark = Color(0xFFA2CDDB)
private val md_onTertiary_dark = Color(0xFF033541)
private val md_tertiaryContainer_dark = Color(0xFF214C58)
private val md_onTertiaryContainer_dark = Color(0xFFBEE9F8)

private val md_error_dark = Color(0xFFFFB4AB)
private val md_onError_dark = Color(0xFF690005)
private val md_errorContainer_dark = Color(0xFF93000A)
private val md_onErrorContainer_dark = Color(0xFFFFDAD6)

private val md_background_dark = Color(0xFF10140E)
private val md_onBackground_dark = Color(0xFFDFE4DA)
private val md_surface_dark = Color(0xFF10140E)
private val md_onSurface_dark = Color(0xFFDFE4DA)
private val md_surfaceVariant_dark = Color(0xFF414940)
private val md_onSurfaceVariant_dark = Color(0xFFC1C9BD)
private val md_outline_dark = Color(0xFF8B938A)
private val md_outlineVariant_dark = Color(0xFF414940)

val EvsctLightScheme: ColorScheme = lightColorScheme(
    primary = md_primary_light,
    onPrimary = md_onPrimary_light,
    primaryContainer = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    secondary = md_secondary_light,
    onSecondary = md_onSecondary_light,
    secondaryContainer = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary = md_tertiary_light,
    onTertiary = md_onTertiary_light,
    tertiaryContainer = md_tertiaryContainer_light,
    onTertiaryContainer = md_onTertiaryContainer_light,
    error = md_error_light,
    onError = md_onError_light,
    errorContainer = md_errorContainer_light,
    onErrorContainer = md_onErrorContainer_light,
    background = md_background_light,
    onBackground = md_onBackground_light,
    surface = md_surface_light,
    onSurface = md_onSurface_light,
    surfaceVariant = md_surfaceVariant_light,
    onSurfaceVariant = md_onSurfaceVariant_light,
    outline = md_outline_light,
    outlineVariant = md_outlineVariant_light,
)

val EvsctDarkScheme: ColorScheme = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary = md_tertiary_dark,
    onTertiary = md_onTertiary_dark,
    tertiaryContainer = md_tertiaryContainer_dark,
    onTertiaryContainer = md_onTertiaryContainer_dark,
    error = md_error_dark,
    onError = md_onError_dark,
    errorContainer = md_errorContainer_dark,
    onErrorContainer = md_onErrorContainer_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
    outlineVariant = md_outlineVariant_dark,
)

/* ------------------- Domain accents (charging types) ------------------- */

/** One charging type's color trio: the saturated [accent] for bars, heatmap
 *  cells, and row stripes; a soft [container] fill for badges; and the
 *  [onContainer] text color that stays readable on that fill. */
@Immutable
data class TypeAccent(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/** Accent trios for the three charging types. The light values washed out
 *  (blue/purple) or seared (light containers) against dark surfaces, so the
 *  palette is theme-scoped: [EvsctTheme] provides the matching set through
 *  [LocalEvAccents]. */
@Immutable
data class EvAccentPalette(
    val dcFast: TypeAccent,
    val acL2: TypeAccent,
    val acL1: TypeAccent,
)

val LightEvAccents = EvAccentPalette(
    dcFast = TypeAccent(                   // amber – fast/hot
        accent = Color(0xFFFFA000),
        container = Color(0xFFFFE0B2),
        onContainer = Color(0xFF3B2400),
    ),
    acL2 = TypeAccent(                     // blue – steady AC
        accent = Color(0xFF1976D2),
        container = Color(0xFFBBDEFB),
        onContainer = Color(0xFF002B57),
    ),
    acL1 = TypeAccent(                     // purple – slow trickle
        accent = Color(0xFF7E57C2),
        container = Color(0xFFD1C4E9),
        onContainer = Color(0xFF200052),
    ),
)

/** Same hues re-toned for dark surfaces: accents lifted two Material tones
 *  so charts keep their pop, containers dropped to deep fills with pale
 *  content on top. */
val DarkEvAccents = EvAccentPalette(
    dcFast = TypeAccent(
        accent = Color(0xFFFFB74D),
        container = Color(0xFF4A3500),
        onContainer = Color(0xFFFFDEA8),
    ),
    acL2 = TypeAccent(
        accent = Color(0xFF90CAF9),
        container = Color(0xFF0D3A5E),
        onContainer = Color(0xFFD3E4FD),
    ),
    acL1 = TypeAccent(
        accent = Color(0xFFB39DDB),
        container = Color(0xFF36275A),
        onContainer = Color(0xFFE8DEF8),
    ),
)

val LocalEvAccents = staticCompositionLocalOf { LightEvAccents }
