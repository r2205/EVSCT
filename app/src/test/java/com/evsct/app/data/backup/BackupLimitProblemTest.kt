package com.evsct.app.data.backup

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Export refuses a backup restore would refuse. Before this check the two
 * sides disagreed: a log past 2 GiB of receipts or 5,000 attachments saved
 * fine and then could not be restored, the pre-restore snapshot included.
 */
class BackupLimitProblemTest {

    private val mib = 1024L * 1024

    @Test
    fun `a backup at every limit is restorable`() {
        assertNull(backupLimitProblem(jsonBytes = 64 * mib, entryCount = 100_000, largestFileBytes = 25 * mib))
    }

    @Test
    fun `a large but ordinary log is restorable`() {
        // 20,000 sessions (~1 KB each in backup.json) and 6,000 receipts:
        // over both of the old caps.
        assertNull(backupLimitProblem(jsonBytes = 20_000 * 1_000L, entryCount = 6_001, largestFileBytes = 6 * mib))
    }

    @Test
    fun `each limit is reported just past it`() {
        val json = assertNotNull(backupLimitProblem(64 * mib + 1, 1, 0))
        assertTrue("65 MB" in json, json)
        assertNotNull(backupLimitProblem(1, 100_001, 0))
        val file = assertNotNull(backupLimitProblem(1, 2, 25 * mib + 1))
        assertTrue("26 MB" in file, file)
    }
}
