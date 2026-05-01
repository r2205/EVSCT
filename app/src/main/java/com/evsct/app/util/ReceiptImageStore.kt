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
 * Persists session receipt photos into the app's private files dir under
 * `receipts/<uuid>.jpg`. Mirrors VehicleImageStore so each image kind has its
 * own subdirectory and lifecycle.
 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    suspend fun copyFromUri(source: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir, name)
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read image source")
        "receipts/$name"
    }

    fun absoluteFile(relativePath: String?): File? =
        relativePath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        absoluteFile(relativePath)?.takeIf { it.exists() }?.delete()
    }
}
