package com.evsct.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read receipt source")
        "receipts/$name"
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
