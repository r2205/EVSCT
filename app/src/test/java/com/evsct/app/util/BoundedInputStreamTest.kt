package com.evsct.app.util

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BoundedInputStreamTest {

    private fun bounded(bytes: ByteArray, limit: Long) =
        BoundedInputStream(ByteArrayInputStream(bytes), limit) { IOException("too big") }

    @Test
    fun `a stream within the limit reads through unchanged`() {
        val data = ByteArray(100) { it.toByte() }
        assertContentEquals(data, bounded(data, 100).readBytes())
    }

    @Test
    fun `one byte over the limit throws`() {
        val data = ByteArray(101)
        val e = assertFailsWith<IOException> { bounded(data, 100).readBytes() }
        assertEquals("too big", e.message)
    }

    @Test
    fun `single-byte reads are counted too`() {
        val stream = bounded(ByteArray(3), 2)
        stream.read()
        stream.read()
        assertFailsWith<IOException> { stream.read() }
    }

    @Test
    fun `skipped bytes count against the limit`() {
        val stream = bounded(ByteArray(10), 5)
        assertFailsWith<IOException> { stream.skip(10) }
    }

    @Test
    fun `mark is not supported so a reader cannot rewind past the count`() {
        assertFalse(bounded(ByteArray(1), 1).markSupported())
    }
}
