package com.evsct.app.util

import java.util.Locale
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
        assertEquals(12.34, Format.parseDecimal("12,34"))
        // Leading zero can't be a thousands group — this is a fraction.
        assertEquals(0.485, Format.parseDecimal("0,485"))
    }

    @Test
    fun `dot-decimal locale reads comma-only strict thousands grouping as grouped`() {
        // A dot-decimal keyboard has no comma to type: "12,345" was pasted,
        // most likely a US-formatted odometer.
        assertEquals(1234.0, Format.parseDecimal("1,234", decimalSeparator = '.'))
        assertEquals(12345.0, Format.parseDecimal("12,345", decimalSeparator = '.'))
        assertEquals(45678.0, Format.parseDecimal("45,678", decimalSeparator = '.'))
        assertEquals(1234567.0, Format.parseDecimal("1,234,567", decimalSeparator = '.'))
        assertEquals(-1234.0, Format.parseDecimal("-1,234", decimalSeparator = '.'))
    }

    @Test
    fun `comma-decimal locale reads one comma and three digits as a decimal`() {
        // What a fr-CA or German keyboard types for a 3-decimal reading,
        // e.g. a charger receipt's "45,678 kWh". This used to save 45,678.
        assertEquals(45.678, Format.parseDecimal("45,678", decimalSeparator = ','))
        assertEquals(1.234, Format.parseDecimal("1,234", decimalSeparator = ','))
        assertEquals(-1.234, Format.parseDecimal("-1,234", decimalSeparator = ','))
        assertEquals(0.485, Format.parseDecimal("0,485", decimalSeparator = ','))
    }

    @Test
    fun `comma-decimal locale still reads several comma groups as thousands`() {
        // Two decimal commas are impossible, so this can only be grouping.
        assertEquals(1234567.0, Format.parseDecimal("1,234,567", decimalSeparator = ','))
    }

    @Test
    fun `both separators ignore the locale`() {
        assertEquals(1234.5, Format.parseDecimal("1,234.5", decimalSeparator = ','))
        assertEquals(1234.56, Format.parseDecimal("1.234,56", decimalSeparator = '.'))
    }

    @Test
    fun `the default separator follows the device locale`() {
        val original = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.CANADA_FRENCH)
            assertEquals(45.678, Format.parseDecimal("45,678"))
            Locale.setDefault(Locale.Category.FORMAT, Locale.US)
            assertEquals(45678.0, Format.parseDecimal("45,678"))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original)
        }
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

    @Test
    fun `non-finite values return null`() {
        // toDoubleOrNull accepts all of these; a stored NaN/Infinity poisons
        // every aggregation and makes the JSON backup writer throw.
        assertNull(Format.parseDecimal("NaN"))
        assertNull(Format.parseDecimal("Infinity"))
        assertNull(Format.parseDecimal("-Infinity"))
        assertNull(Format.parseDecimal("1e999"))
    }
}
