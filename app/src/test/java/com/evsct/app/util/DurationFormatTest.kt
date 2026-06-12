package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationFormatTest {

    @Test
    fun `bare integer is minutes`() {
        assertEquals(11 * 60L, DurationFormat.parse("11"))
    }

    @Test
    fun `two-part colon is hours and minutes`() {
        assertEquals(1 * 3600L + 25 * 60L, DurationFormat.parse("1:25"))
    }

    @Test
    fun `three-part colon is exact`() {
        assertEquals(11 * 60L, DurationFormat.parse("0:11:00"))
    }

    @Test
    fun `pretty forms parse`() {
        assertEquals(1 * 3600L + 25 * 60L + 30, DurationFormat.parse("1h 25m 30s"))
        assertEquals(25 * 60L, DurationFormat.parse("25m"))
        assertEquals(30L, DurationFormat.parse("30s"))
    }

    @Test
    fun `negative inputs are rejected`() {
        // toLongOrNull accepts a sign; a duration field must not store
        // negative seconds ("-5m 00s" used to render in the log).
        assertNull(DurationFormat.parse("-5"))
        assertNull(DurationFormat.parse("-1:30"))
        assertNull(DurationFormat.parse("1:-30"))
        assertNull(DurationFormat.parse("-0:11:00"))
    }

    @Test
    fun `blank and garbage are rejected`() {
        assertNull(DurationFormat.parse(""))
        assertNull(DurationFormat.parse("abc"))
        assertNull(DurationFormat.parse("1:2:3:4"))
    }
}
