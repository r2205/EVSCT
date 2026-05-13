package com.evsct.app.data.repository

import com.evsct.app.data.db.SessionReceiptDao
import com.evsct.app.data.db.SessionReceiptCount
import com.evsct.app.data.entity.SessionReceipt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionReceiptRepository @Inject constructor(
    private val dao: SessionReceiptDao,
) {
    fun observeForSession(sessionId: Long): Flow<List<SessionReceipt>> =
        dao.observeForSession(sessionId)

    suspend fun findForSession(sessionId: Long): List<SessionReceipt> =
        dao.findForSession(sessionId)

    suspend fun findAll(): List<SessionReceipt> = dao.findAll()

    fun observeCountsBySession(): Flow<List<SessionReceiptCount>> =
        dao.observeCountsBySession()

    suspend fun insert(receipt: SessionReceipt): Long = dao.insert(receipt)

    suspend fun insertAll(receipts: List<SessionReceipt>): List<Long> = dao.insertAll(receipts)

    suspend fun updateName(id: Long, name: String?) = dao.updateName(id, name)

    suspend fun delete(receipt: SessionReceipt) = dao.delete(receipt)
}
