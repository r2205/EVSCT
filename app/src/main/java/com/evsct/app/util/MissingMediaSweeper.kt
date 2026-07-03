package com.evsct.app.util

import com.evsct.app.data.repository.SessionReceiptRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.di.AppScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Startup sweep that reconciles media references in the database against
 * the files actually on disk.
 *
 * Android's cloud auto-backup restores the database and DataStore prefs
 * but deliberately not `receipts/` or `vehicles/` (the 25 MB quota
 * trade-off documented in data_extraction_rules.xml). On a new phone the
 * restored rows then reference files that never arrived: paperclip icons
 * and receipt tiles that open nothing, vehicle photos that render blank —
 * and without this sweep those dead references persisted forever. Dropping
 * them matches reality: the files are gone and nothing in-app can bring
 * them back (the recovery path that DOES carry media is the full backup
 * zip).
 *
 * Safe against racing normal writes because every attach flow copies the
 * file to disk BEFORE inserting the row that references it — a row whose
 * file is missing is genuinely dead, never in-flight. Restore installs
 * its media after its transaction commits, but the sweep runs once at
 * process start, long before a restore can be initiated.
 */
@Singleton
class MissingMediaSweeper @Inject constructor(
    private val sessionReceiptRepository: SessionReceiptRepository,
    private val vehicleRepository: VehicleRepository,
    private val receiptImageStore: ReceiptImageStore,
    private val vehicleImageStore: VehicleImageStore,
    @AppScope private val appScope: CoroutineScope,
) {
    private val ran = AtomicBoolean(false)

    /** Kick off the sweep once per process; subsequent calls no-op. Runs
     *  on the app scope (IO-backed) so an activity recreation mid-sweep
     *  can't cancel it halfway. */
    fun sweepInBackground() {
        if (!ran.compareAndSet(false, true)) return
        appScope.launch {
            sessionReceiptRepository.findAll().forEach { receipt ->
                val file = receiptImageStore.absoluteFile(receipt.filePath)
                if (file == null || !file.exists()) {
                    sessionReceiptRepository.delete(receipt)
                }
            }
            vehicleRepository.observeAll().first()
                .filter { it.imagePath != null }
                .forEach { vehicle ->
                    val file = vehicleImageStore.absoluteFile(vehicle.imagePath)
                    if (file == null || !file.exists()) {
                        vehicleRepository.upsert(vehicle.copy(imagePath = null))
                    }
                }
        }
    }
}
