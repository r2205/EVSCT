package com.evsct.app.util

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Passes [delegate] through until more than [limit] bytes have been read,
 * then throws the [IOException] that [onExceeded] builds. For the import
 * paths that hand a user-picked stream to a parser which slurps it whole
 * (the CSV reader, POI's workbook loader): without a bound, a mis-picked
 * multi-hundred-megabyte file is an out-of-memory crash rather than an
 * error message. The backup restore path meters its zip entries the same
 * way; this is the streaming equivalent for readers we don't control.
 */
class BoundedInputStream(
    delegate: InputStream,
    private val limit: Long,
    private val onExceeded: () -> IOException,
) : FilterInputStream(delegate) {

    private var consumed = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) count(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) count(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) count(skipped)
        return skipped
    }

    // Rewinding would let a reader re-consume bytes already counted.
    override fun markSupported(): Boolean = false

    private fun count(n: Long) {
        consumed += n
        if (consumed > limit) throw onExceeded()
    }
}
