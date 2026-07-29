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
    fun `two-part colon is minutes and seconds`() {
        assertEquals(32 * 60L + 14, DurationFormat.parse("32:14"))
        assertEquals(1 * 60L + 25, DurationFormat.parse("1:25"))
    }

    @Test
    fun `three-part colon is exact`() {
        assertEquals(11 * 60L, DurationFormat.parse("0:11:00"))
        assertEquals(1 * 3600L + 25 * 60L, DurationFormat.parse("1:25:00"))
    }

    @Test
    fun `editable drops the hours part under an hour`() {
        assertEquals("32:14", DurationFormat.editable(32 * 60L + 14))
        assertEquals("0:45", DurationFormat.editable(45))
        assertEquals("1:25:00", DurationFormat.editable(1 * 3600L + 25 * 60L))
    }

    @Test
    fun `roundedMinutes rounds to the nearest whole minute`() {
        assertEquals(32, DurationFormat.roundedMinutes(32 * 60L + 14))
        assertEquals(33, DurationFormat.roundedMinutes(32 * 60L + 44))
        assertEquals(1, DurationFormat.roundedMinutes(30))
        assertEquals(0, DurationFormat.roundedMinutes(29))
        assertEquals(75, DurationFormat.roundedMinutes(75 * 60L))
    }

    @Test
    fun `editable output round-trips through parse`() {
        for (sec in listOf(45L, 32 * 60L + 14, 3600L, 1 * 3600L + 25 * 60L + 30)) {
            assertEquals(sec, DurationFormat.parse(DurationFormat.editable(sec)))
        }
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
        // negative seconds ("-5m 00s" used to render in the log). "-0" is
        // the sneaky case: its parsed value is 0, so a value-based guard
        // misses it — parts must be digits-only.
        assertNull(DurationFormat.parse("-5"))
        assertNull(DurationFormat.parse("-1:30"))
        assertNull(DurationFormat.parse("1:-30"))
        assertNull(DurationFormat.parse("-0:11:00"))
        assertNull(DurationFormat.parse("-0"))
        assertNull(DurationFormat.parse("+5"))
    }

    @Test
    fun `blank and garbage are rejected`() {
        assertNull(DurationFormat.parse(""))
        assertNull(DurationFormat.parse("abc"))
        assertNull(DurationFormat.parse("1:2:3:4"))
    }
}
