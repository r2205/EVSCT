package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.SessionReceipt
import com.evsct.app.data.repository.SessionReceiptRepository
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.di.AppScope
import com.evsct.app.util.ReceiptImageStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-slot parking spot for the most recently deleted batch of sessions —
 * a single session from the edit screen's Delete, or several from the
 * log's multi-select — giving the charging log a window to offer "Undo".
 * The deleting ViewModel may be gone by the time the snackbar shows (the
 * edit screen pops), so the pending state lives here, app-scoped.
 *
 * Deleting sessions normally deletes their receipt files too; while an
 * undo is pending those files stay on disk (their DB rows are already
 * gone) and are only removed when the offer resolves — [finalize] on
 * dismiss/timeout, or kept alive by [undo], which reinstates the rows and
 * their receipt rows. A new [offer] finalizes the previous one, so at
 * most one batch's files are ever in limbo. If the process dies inside
 * the undo window the deferred files are orphaned on disk; the next
 * restore's orphan sweep reclaims them. Accepted trade-off for not
 * needing a soft-delete schema.
 */
@Singleton
class DeletedSessionUndoHolder @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionReceiptRepository: SessionReceiptRepository,
    private val receiptImageStore: ReceiptImageStore,
    @AppScope private val appScope: CoroutineScope,
) {

    data class Pending(
        val sessions: List<ChargingSession>,
        val receipts: List<SessionReceipt>,
    )

    private val _pending = MutableStateFlow<Pending?>(null)

    /** Non-null while an undo offer is live; the log screen shows the
     *  snackbar off this. */
    val pending: StateFlow<Pending?> = _pending

    /** Park a just-deleted batch (rows already gone, receipt files still
     *  on disk). Any previous offer is finalized first. */
    fun offer(sessions: List<ChargingSession>, receipts: List<SessionReceipt>) {
        if (sessions.isEmpty()) return
        finalize()
        _pending.value = Pending(sessions, receipts)
    }

    /** The offer lapsed (snackbar dismissed or timed out): delete the
     *  deferred receipt files for real. Safe to call when nothing pends. */
    fun finalize() {
        val p = _pending.value ?: return
        _pending.value = null
        if (p.receipts.isEmpty()) return
        appScope.launch {
            p.receipts.forEach { receiptImageStore.delete(it.filePath) }
        }
    }

    /** Reinstate the session rows (same ids) and fresh receipt rows
     *  pointing at the still-on-disk files. */
    fun undo() {
        val p = _pending.value ?: return
        _pending.value = null
        appScope.launch {
            runCatching {
                sessionRepository.restoreAll(p.sessions)
                if (p.receipts.isNotEmpty()) {
                    sessionReceiptRepository.insertAll(p.receipts.map { it.copy(id = 0) })
                }
            }.onFailure {
                // Couldn't reinstate (e.g. a session's vehicle was deleted
                // in the window and the FK refused). The files are
                // unreferenced now — drop them like a lapsed offer.
                p.receipts.forEach { receiptImageStore.delete(it.filePath) }
            }
        }
    }
}
