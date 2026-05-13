package com.evsct.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.evsct.app.data.entity.SessionReceipt
import kotlinx.coroutines.flow.Flow

/** Per-session counts surfaced to the log row so it can show the
 *  paperclip icon whenever a session carries at least one receipt. */
data class SessionReceiptCount(val sessionId: Long, val count: Int)

@Dao
interface SessionReceiptDao {
    @Query("SELECT * FROM session_receipts WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeForSession(sessionId: Long): Flow<List<SessionReceipt>>

    @Query("SELECT * FROM session_receipts WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun findForSession(sessionId: Long): List<SessionReceipt>

    /** Returns all receipt rows ordered by session then chronologically;
     *  used by the backup exporter. */
    @Query("SELECT * FROM session_receipts ORDER BY sessionId ASC, createdAt ASC, id ASC")
    suspend fun findAll(): List<SessionReceipt>

    /** Receipt counts grouped by session id. Drives the log-row paperclip
     *  visibility without forcing every list row to query individually. */
    @Query("SELECT sessionId, COUNT(*) AS count FROM session_receipts GROUP BY sessionId")
    fun observeCountsBySession(): Flow<List<SessionReceiptCount>>

    @Insert
    suspend fun insert(receipt: SessionReceipt): Long

    @Insert
    suspend fun insertAll(receipts: List<SessionReceipt>): List<Long>

    @Delete
    suspend fun delete(receipt: SessionReceipt)
}
