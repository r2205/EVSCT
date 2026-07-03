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
        // = @ tab CR always defuse; + and - defuse when what follows isn't
        // a plain number. Quoting kicks in too because the apostrophe-
        // prefixed string still gets routed through the quote-needed check
        // (not strictly required for "=foo" but harmless).
        assertEquals("'=cmd|calc", Csv.encodeField("=cmd|calc"))
        assertEquals("'+cmd", Csv.encodeField("+cmd"))
        assertEquals("'-cmd", Csv.encodeField("-cmd"))
        assertEquals("'@evil", Csv.encodeField("@evil"))
        assertEquals("'=1234", Csv.encodeField("=1234")) // = defuses even for numbers
    }

    @Test
    fun `plain negative numbers are not prefixed`() {
        // Longitude west of Greenwich, negative costs, etc. must stay
        // numeric for Excel/Sheets — a pure number can't carry a payload.
        assertEquals("-79.38", Csv.encodeField("-79.38"))
        assertEquals("-5", Csv.encodeField("-5"))
        assertEquals("+1234", Csv.encodeField("+1234"))
        assertEquals("-1.5E-7", Csv.encodeField("-1.5E-7"))
    }

    @Test
    fun `sign followed by non-numeric content still defuses`() {
        assertEquals("'-79.38 west", Csv.encodeField("-79.38 west"))
        assertEquals("'-2+3+cmd|calc", Csv.encodeField("-2+3+cmd|calc"))
        assertEquals("'-", Csv.encodeField("-"))
        assertEquals("'-1..2", Csv.encodeField("-1..2"))
    }

    @Test
    fun `old exports with prefixed negative numbers still decode`() {
        // Files written before numbers were exempted contain '-79.38 —
        // the decoder must keep stripping those.
        assertEquals(listOf("-79.38", "-5"), Csv.parseLine("'-79.38,'-5"))
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
    fun `parseAll keeps rows separate when a field starts with a quote char`() {
        // A field whose *content* begins with a literal " encodes to a field
        // that opens with three quote chars ("""x"). The old trailing-quote
        // parity tracker counted the opening quote into the escaped pair,
        // desynced, and swallowed the row-terminating newline — merging rows.
        val rows = Csv.parseAll("a,\"\"\"x\"\nb,c")
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "\"x"), rows[0])
        assertEquals(listOf("b", "c"), rows[1])
    }

    @Test
    fun `parseAll keeps rows separate when a field is a lone quote char`() {
        val rows = Csv.parseAll("\"\"\"\"\nb")
        assertEquals(2, rows.size)
        assertEquals(listOf("\""), rows[0])
        assertEquals(listOf("b"), rows[1])
    }

    @Test
    fun `multi-row round-trip with leading-quote field content`() {
        val row1 = listOf("\"broken stall", "Calgary", "note with \"quotes\"")
        val row2 = listOf("plain", "Banff", "ok")
        val text = Csv.encodeRow(row1) + "\n" + Csv.encodeRow(row2)
        val rows = Csv.parseAll(text)
        assertEquals(2, rows.size)
        assertEquals(row1, rows[0])
        assertEquals(row2, rows[1])
    }

    @Test
    fun `encode then parse a row preserves field boundaries`() {
        val fields = listOf("Tesla", "Calgary, AB", "notes with \"quotes\"", "=hi")
        val encoded = Csv.encodeRow(fields)
        val parsed = Csv.parseLine(encoded)
        assertEquals(fields, parsed)
    }
}
