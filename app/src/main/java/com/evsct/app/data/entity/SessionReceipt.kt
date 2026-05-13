package com.evsct.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One file (photo or PDF) attached to a charging session. A session can have
 * many — e.g. a transaction receipt plus a Tesla-app screenshot. The actual
 * file lives at `filesDir/<filePath>`; cascade-delete cleans the DB row when
 * the parent session is removed, but file-on-disk cleanup is the caller's
 * job (see [com.evsct.app.util.ReceiptImageStore.delete]).
 */
@Entity(
    tableName = "session_receipts",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Path relative to `filesDir` — e.g. `receipts/<uuid>.jpg`. */
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
)
