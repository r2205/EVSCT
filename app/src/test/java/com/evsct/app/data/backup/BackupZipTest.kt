package com.evsct.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupZipTest {

    private val tempDir: File = Files.createTempDirectory("backupzip-test").toFile()

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    @Test
    fun `happy path returns json and extracts media`() {
        val json = scanToTemp(
            zipOf(
                "backup.json" to """{"schemaVersion":5}""".toByteArray(),
                "vehicles/car.jpg" to byteArrayOf(1, 2, 3),
                "receipts/r1.pdf" to byteArrayOf(4, 5),
            ),
        )
        assertEquals("""{"schemaVersion":5}""", json?.toString(Charsets.UTF_8))
        assertEquals(listOf(1.toByte(), 2, 3), File(tempDir, "vehicles/car.jpg").readBytes().toList())
        assertEquals(listOf(4.toByte(), 5), File(tempDir, "receipts/r1.pdf").readBytes().toList())
    }

    @Test
    fun `zip without json returns null`() {
        assertNull(scanToTemp(zipOf("vehicles/car.jpg" to byteArrayOf(1))))
    }

    @Test
    fun `small unknown entry is tolerated and not extracted`() {
        val json = scanToTemp(
            zipOf(
                "future-metadata.bin" to ByteArray(64),
                "backup.json" to "{}".toByteArray(),
            ),
        )
        assertEquals("{}", json?.toString(Charsets.UTF_8))
        assertFalse(File(tempDir, "future-metadata.bin").exists())
    }

    @Test
    fun `unknown entry larger than the per-entry cap throws`() {
        // Before the fix this entry contributed 0 to the accounting and
        // closeEntry() inflated all of it outside every cap.
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "bomb.bin" to ByteArray(5_000),
                    "backup.json" to "{}".toByteArray(),
                ),
                maxEntryBytes = 1_024,
            )
        }
    }

    @Test
    fun `unknown entries count toward the inflation guard`() {
        // Drained, never written — but inflating them is the work a bomb
        // makes the restore do, so they're metered all the same.
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "a.bin" to ByteArray(700),
                    "b.bin" to ByteArray(700),
                ),
                maxEntryBytes = 10_000,
                inflationAllowanceBytes = 1_000,
                maxInflationRatio = 0,
            )
        }
    }

    @Test
    fun `highly compressible media beyond the inflation ratio throws`() {
        // 512 KiB of zeros deflates to well under 1 KiB: a bomb's shape.
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf("receipts/r.jpg" to ByteArray(512 * 1024)),
                inflationAllowanceBytes = 64 * 1024,
                maxInflationRatio = 20,
            )
        }
    }

    @Test
    fun `incompressible media passes the inflation guard`() {
        // Photos and PDFs barely deflate, so a real backup's media sits at a
        // ratio near 1, far inside the guard.
        scanToTemp(
            zipOf(
                "backup.json" to "{}".toByteArray(),
                "receipts/r.jpg" to randomBytes(512 * 1024),
            ),
            inflationAllowanceBytes = 64 * 1024,
            maxInflationRatio = 20,
        )
        assertEquals(512 * 1024L, File(tempDir, "receipts/r.jpg").length())
    }

    @Test
    fun `a backup larger than the old fixed total cap is not refused for its size`() {
        // Stand-in for a multi-GiB receipt collection: well over this test's
        // allowance, but incompressible, so only free space could stop it.
        scanToTemp(
            zipOf(*Array(8) { "receipts/r$it.jpg" to randomBytes(256 * 1024) }),
            spaceBudgetBytes = 4L * 1024 * 1024,
            inflationAllowanceBytes = 64 * 1024,
        )
        assertEquals(8, File(tempDir, "receipts").listFiles()?.size)
    }

    @Test
    fun `media beyond the free-space budget throws`() {
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "receipts/a.jpg" to randomBytes(64 * 1024),
                    "receipts/b.jpg" to randomBytes(64 * 1024),
                ),
                spaceBudgetBytes = 100 * 1024,
            )
        }
    }

    @Test
    fun `json and drained entries don't spend the free-space budget`() {
        // backup.json is read into memory and unknown entries are discarded:
        // neither lands on disk.
        val json = scanToTemp(
            zipOf(
                "backup.json" to randomBytes(64 * 1024),
                "future-metadata.bin" to randomBytes(64 * 1024),
            ),
            spaceBudgetBytes = 1_024,
        )
        assertEquals(64 * 1024, json?.size)
    }

    @Test
    fun `directory-named entry with attached data is metered`() {
        // A hostile zip can attach a deflate stream to a name ending in `/`.
        // These used to skip both the entry count and the byte accounting.
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf("evil/" to ByteArray(5_000)),
                maxEntryBytes = 1_024,
            )
        }
    }

    @Test
    fun `directory entries count toward the entry cap`() {
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "d1/" to ByteArray(0),
                    "d2/" to ByteArray(0),
                    "d3/" to ByteArray(0),
                ),
                maxEntries = 2,
            )
        }
    }

    @Test
    fun `dropped duplicate basename is still metered`() {
        // Second entry reduces to the same basename; it's dropped (first
        // wins) but its bytes must still be drained under the cap.
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "vehicles/a.jpg" to byteArrayOf(9),
                    "vehicles/sub/a.jpg" to ByteArray(5_000),
                ),
                maxEntryBytes = 1_024,
            )
        }
    }

    @Test
    fun `first entry wins for duplicate basenames`() {
        scanToTemp(
            zipOf(
                "backup.json" to "{}".toByteArray(),
                "vehicles/a.jpg" to byteArrayOf(9),
                "vehicles/sub/a.jpg" to byteArrayOf(1, 2, 3),
            ),
        )
        assertEquals(listOf(9.toByte()), File(tempDir, "vehicles/a.jpg").readBytes().toList())
    }

    @Test
    fun `oversized json throws`() {
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf("backup.json" to ByteArray(5_000)),
                maxJsonBytes = 1_024,
            )
        }
    }

    @Test
    fun `too many entries throws`() {
        assertFailsWith<IOException> {
            scanToTemp(
                zipOf(
                    "a.bin" to ByteArray(1),
                    "b.bin" to ByteArray(1),
                    "c.bin" to ByteArray(1),
                ),
                maxEntries = 2,
            )
        }
    }

    @Test
    fun `zip-slip names are confined to their target directory`() {
        scanToTemp(
            zipOf("vehicles/../../escaped.bin" to byteArrayOf(7)),
        )
        // Basename-only: lands inside vehicles/, never outside tempDir.
        assertTrue(File(tempDir, "vehicles/escaped.bin").exists())
        assertFalse(File(tempDir.parentFile, "escaped.bin").exists())
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }

    private fun scanToTemp(
        stream: ByteArrayInputStream,
        spaceBudgetBytes: Long = 4L * 1024 * 1024,
        maxJsonBytes: Long = 1L * 1024 * 1024,
        maxEntryBytes: Long = 1L * 1024 * 1024,
        maxEntries: Int = 100,
        inflationAllowanceBytes: Long = 4L * 1024 * 1024,
        maxInflationRatio: Long = 20,
    ): ByteArray? = BackupZip.scanToTemp(
        stream, tempDir,
        spaceBudgetBytes = spaceBudgetBytes,
        maxJsonBytes = maxJsonBytes,
        maxEntryBytes = maxEntryBytes,
        maxEntries = maxEntries,
        inflationAllowanceBytes = inflationAllowanceBytes,
        maxInflationRatio = maxInflationRatio,
    )
}
