package com.evsct.app.data.backup

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SanitizedBasenameTest {

    @Test
    fun `plain basename passes through`() {
        assertEquals("a.jpg", sanitizedBasename("a.jpg"))
    }

    @Test
    fun `path components are stripped`() {
        assertEquals("a.jpg", sanitizedBasename("../../a.jpg"))
        assertEquals("a.jpg", sanitizedBasename("/etc/a.jpg"))
        assertEquals("a.jpg", sanitizedBasename("receipts/sub/a.jpg"))
    }

    @Test
    fun `dot and dot-dot are rejected rather than kept as a name`() {
        assertNull(sanitizedBasename("."))
        assertNull(sanitizedBasename(".."))
        assertNull(sanitizedBasename("receipts/.."))
        assertNull(sanitizedBasename("vehicles/."))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(sanitizedBasename(""))
        assertNull(sanitizedBasename("   "))
    }

    @Test
    fun `a trailing slash yields the last component, as File does`() {
        // Not a rejection: "receipts" is an ordinary (if odd) filename.
        assertEquals("receipts", sanitizedBasename("receipts/"))
    }

    @Test
    fun `a dotfile with a real name survives`() {
        assertEquals("..pdf", sanitizedBasename("..pdf"))
        assertEquals(".hidden", sanitizedBasename(".hidden"))
    }
}
