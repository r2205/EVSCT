package com.evsct.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Pins which field decides the rail-versus-bar switch and where the boundary
 * sits. Small on purpose: the real mistake class here is reading height where
 * width was meant — a landscape phone is wide *and* short, so a width/height
 * mix-up would put the rail exactly where the bottom bar belongs.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun wideAt(widthDp: Int, heightDp: Int): Boolean {
        var result = false
        compose.setContent {
            val forced = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = widthDp
                screenHeightDp = heightDp
            }
            CompositionLocalProvider(LocalConfiguration provides forced) {
                result = isWideWindow()
            }
        }
        return result
    }

    @Test
    fun `a landscape phone is wide despite being short`() {
        assertEquals(true, wideAt(widthDp = 800, heightDp = 360))
    }

    @Test
    fun `a portrait phone is narrow despite being tall`() {
        assertEquals(false, wideAt(widthDp = 400, heightDp = 800))
    }

    @Test
    fun `the boundary is inclusive at the named breakpoint`() {
        assertEquals(true, wideAt(widthDp = WIDE_WINDOW_MIN_DP, heightDp = 400))
        assertEquals(false, wideAt(widthDp = WIDE_WINDOW_MIN_DP - 1, heightDp = 400))
    }
}
