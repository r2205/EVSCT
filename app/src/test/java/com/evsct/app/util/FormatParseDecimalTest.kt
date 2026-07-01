package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatParseDecimalTest {

    @Test
    fun `plain dot decimal parses`() {
        assertEquals(12.5, Format.parseDecimal("12.5"))
        assertEquals(7.0, Format.parseDecimal("7"))
    }

    @Test
    fun `comma decimal separator parses`() {
        // KeyboardType.Decimal surfaces ',' on comma-locale keyboards.
        assertEquals(12.5, Format.parseDecimal("12,5"))
        assertEquals(62.1, Format.parseDecimal("62,1"))
    }

    @Test
    fun `comma alongside dot is a thousands separator`() {
        assertEquals(1234.5, Format.parseDecimal("1,234.5"))
        assertEquals(1234567.89, Format.parseDecimal("1,234,567.89"))
    }

    @Test
    fun `european dot-thousands comma-decimal parses`() {
        assertEquals(1234.56, Format.parseDecimal("1.234,56"))
        assertEquals(1234567.89, Format.parseDecimal("1.234.567,89"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(7.0, Format.parseDecimal(" 7 "))
    }

    @Test
    fun `blank and garbage return null`() {
        assertNull(Format.parseDecimal(""))
        assertNull(Format.parseDecimal("   "))
        assertNull(Format.parseDecimal("abc"))
        assertNull(Format.parseDecimal("1.2.3"))
    }
}
