package com.evsct.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picker-supplied receipt larger than this is refused — guards against a
 *  malicious app handing back a 5 GB "PDF" via SAF that would silently fill
 *  the user's storage. A normal photo or PDF receipt sits well below. */
private const val MAX_RECEIPT_BYTES: Long = 25L * 1024 * 1024

/**
 * Persists session receipts (images or PDFs) into the app's private files dir
 * under `receipts/<uuid>.<ext>`. The extension preserves whether the receipt
 * is a picture (`.jpg`) or a PDF document (`.pdf`) so the UI knows whether to
 * render an inline preview or hand off to an external viewer.
 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    suspend fun copyFromUri(source: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(source)
        val ext = if (mime == "application/pdf") "pdf" else "jpg"
        val name = "${UUID.randomUUID()}.$ext"
        val target = File(dir, name)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    input.copyBoundedTo(output, MAX_RECEIPT_BYTES)
                }
            } ?: error("Could not read receipt source")
        } catch (e: Throwable) {
            // Don't leave a partial file on disk if the copy aborted (size cap
            // hit, source stream broke, etc.). The caller's cleanup tracker
            // wouldn't know about it since we never returned the path.
            target.delete()
            throw e
        }
        "receipts/$name"
    }

    private fun InputStream.copyBoundedTo(out: OutputStream, limit: Long) {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) {
                throw IOException("Receipt exceeds the $limit byte size cap.")
            }
            out.write(buf, 0, n)
        }
    }

    fun absoluteFile(relativePath: String?): File? =
        relativePath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        absoluteFile(relativePath)?.takeIf { it.exists() }?.delete()
    }

    companion object {
        fun isPdf(relativePath: String?): Boolean =
            relativePath?.endsWith(".pdf", ignoreCase = true) == true

        fun mimeType(relativePath: String?): String =
            if (isPdf(relativePath)) "application/pdf" else "image/jpeg"
    }
}
