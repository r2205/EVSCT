package com.evsct.app.util

import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the export filename contract. Two properties matter beyond "it
 * contains the version": the timestamp stays immediately after the prefix
 * (a file manager sorting by name must still sort chronologically — pushing
 * the build tag in front would group by build instead), and nothing that
 * reaches a name can carry a path separator.
 *
 * Every case passes the clock, zone and build tag explicitly, so these never
 * depend on the machine running them or on which commit built the test.
 */
class ExportNamingTest {

    private val zone = ZoneId.of("America/Toronto")

    // 2026-07-31T18:32:05Z == 14:32 local in America/Toronto (UTC-4 in July).
    private val at = Instant.parse("2026-07-31T18:32:05Z")

    @Test
    fun `name is prefix, then timestamp, then build tag`() {
        assertEquals(
            "evsct-backup-2026-07-31-1432-b247-a1b2c3d.zip",
            ExportNaming.fileName("evsct-backup", "zip", at, zone, "b247-a1b2c3d"),
        )
    }

    @Test
    fun `timestamp is local to the given zone, not UTC`() {
        // Same instant, a zone a day behind — the date field must move with it.
        assertEquals(
            "2026-07-31-1432",
            ExportNaming.timestamp(at, zone),
        )
        assertEquals(
            "2026-08-01-0332",
            ExportNaming.timestamp(at, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `build tag combines version code and sha`() {
        assertEquals("b247-a1b2c3d", ExportNaming.buildTag(247, "a1b2c3d"))
    }

    @Test
    fun `dirty builds keep the marker`() {
        // A dirty build isn't reproducible from any commit — that's exactly
        // what a support report needs to know, so the suffix survives.
        assertEquals("b247-a1b2c3d-dirty", ExportNaming.buildTag(247, "a1b2c3d-dirty"))
    }

    @Test
    fun `no git falls back to the version code alone`() {
        // gitSha is the literal "unknown" when the build ran outside a
        // checkout — appending it would be noise, not provenance.
        assertEquals("b1", ExportNaming.buildTag(1, "unknown"))
        assertEquals("b1", ExportNaming.buildTag(1, ""))
        assertEquals("b1", ExportNaming.buildTag(1, "   "))
    }

    @Test
    fun `path separators never reach a filename`() {
        val name = ExportNaming.fileName(
            "evsct-backup",
            "zip",
            at,
            zone,
            ExportNaming.buildTag(9, "../../etc/passwd"),
        )
        assertTrue('/' !in name, "separator survived into: $name")
        assertTrue(".." !in name, "traversal survived into: $name")
        assertEquals("evsct-backup-2026-07-31-1432-b9-etc-passwd.zip", name)
    }

    @Test
    fun `both convenience names carry a build tag and the right extension`() {
        val backup = ExportNaming.backupFileName()
        val csv = ExportNaming.csvFileName()

        assertTrue(backup.startsWith("${ExportNaming.BACKUP_PREFIX}-"), backup)
        assertTrue(backup.endsWith(".zip"), backup)
        assertTrue(csv.startsWith("${ExportNaming.CSV_PREFIX}-"), csv)
        assertTrue(csv.endsWith(".csv"), csv)

        // …-yyyy-MM-dd-HHmm-b<code>[-<sha>].<ext>. The CSV picker used to
        // emit raw epoch millis here; this is what stops that regressing.
        val shape = Regex("""^evsct-(backup|export)-\d{4}-\d{2}-\d{2}-\d{4}-b\d+(-[A-Za-z0-9-]+)?\.(zip|csv)$""")
        assertTrue(shape.matches(backup), backup)
        assertTrue(shape.matches(csv), csv)
    }
}
