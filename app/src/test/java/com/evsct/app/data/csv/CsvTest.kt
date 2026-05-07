package com.evsct.app.data.csv

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CsvTest {

    @Test
    fun `plain text encodes unchanged`() {
        assertEquals("hello", Csv.encodeField("hello"))
    }

    @Test
    fun `null encodes to empty`() {
        assertEquals("", Csv.encodeField(null))
    }

    @Test
    fun `comma forces quoting`() {
        assertEquals("\"a,b\"", Csv.encodeField("a,b"))
    }

    @Test
    fun `embedded quote is doubled and field is quoted`() {
        assertEquals("\"He said \"\"hi\"\"\"", Csv.encodeField("He said \"hi\""))
    }

    @Test
    fun `newline forces quoting`() {
        assertEquals("\"line1\nline2\"", Csv.encodeField("line1\nline2"))
    }

    @Test
    fun `formula triggers get prefixed and quoted`() {
        // = + - @ tab CR — all defuse with a leading apostrophe.
        // Quoting kicks in too because the apostrophe-prefixed string still
        // gets routed through the quote-needed check (not strictly required
        // for "=foo" but harmless).
        assertEquals("'=cmd|calc", Csv.encodeField("=cmd|calc"))
        assertEquals("'+1234", Csv.encodeField("+1234"))
        assertEquals("'-cmd", Csv.encodeField("-cmd"))
        assertEquals("'@evil", Csv.encodeField("@evil"))
    }

    @Test
    fun `non-trigger leading characters do not get prefixed`() {
        assertEquals("Tesla", Csv.encodeField("Tesla"))
        assertEquals("'Tesla", Csv.encodeField("'Tesla"))  // user's own apostrophe stays
        assertEquals("123", Csv.encodeField("123"))
    }

    @Test
    fun `parseLine strips defusing apostrophe in front of trigger char`() {
        val parsed = Csv.parseLine("'=cmd|calc,plain,\"'+1234\"")
        assertEquals(listOf("=cmd|calc", "plain", "+1234"), parsed)
    }

    @Test
    fun `parseLine preserves user-typed apostrophe before non-trigger`() {
        // A legitimate "'Tesla" must round-trip — the second char isn't a
        // trigger so the apostrophe stays.
        val parsed = Csv.parseLine("'Tesla,'don't trust this")
        assertEquals(listOf("'Tesla", "'don't trust this"), parsed)
    }

    @Test
    fun `formula-injection round-trip is lossless for the user's value`() {
        val original = "=SUM(A1:A10)"
        val encoded = Csv.encodeField(original)
        val decoded = Csv.parseLine(encoded).first()
        assertEquals(original, decoded)
        // Sanity check: the encoded form is NOT just the original (defense
        // actually applied something).
        assertNotEquals(original, encoded)
    }

    @Test
    fun `parseAll handles embedded newlines inside quoted fields`() {
        val csv = "a,\"b\nstill b\",c\nd,e,f"
        val rows = Csv.parseAll(csv)
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b\nstill b", "c"), rows[0])
        assertEquals(listOf("d", "e", "f"), rows[1])
    }

    @Test
    fun `encode then parse a row preserves field boundaries`() {
        val fields = listOf("Tesla", "Calgary, AB", "notes with \"quotes\"", "=hi")
        val encoded = Csv.encodeRow(fields)
        val parsed = Csv.parseLine(encoded)
        assertEquals(fields, parsed)
    }
}
