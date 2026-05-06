package com.evsct.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picker-supplied vehicle photo larger than this is refused. Matches the
 *  receipt cap so the limit is consistent across pickers — and high enough
 *  that pre-existing larger photos restored from a backup can be re-picked
 *  if the user wants to swap them. */
private const val MAX_VEHICLE_IMAGE_BYTES: Long = 25L * 1024 * 1024

/**
 * Persists vehicle profile images into the app's private files dir. Photo Picker
 * URIs are short-lived and can't be reliably persisted, so we copy the bytes once
 * at pick-time and store only the relative path.
 */
@Singleton
class VehicleImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "vehicles").apply { mkdirs() }

    suspend fun copyFromUri(source: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir, name)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    input.copyBoundedTo(output, MAX_VEHICLE_IMAGE_BYTES)
                }
            } ?: error("Could not read image source")
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        "vehicles/$name"
    }

    private fun InputStream.copyBoundedTo(out: OutputStream, limit: Long) {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw FileTooLargeException(limit)
            out.write(buf, 0, n)
        }
    }

    fun absoluteFile(relativePath: String?): File? =
        relativePath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }

    suspend fun delete(relativePath: String?) = withContext(Dispatchers.IO) {
        absoluteFile(relativePath)?.takeIf { it.exists() }?.delete()
    }
}
