package com.evsct.app.data.db

import com.evsct.app.data.entity.ChargingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the default (interface-body) methods on [ChargingSessionDao]
 * that keep `continuesPrevious` honest when its attested predecessor is
 * deleted or moved. The fake below implements the abstract query methods
 * in memory with the same semantics as their SQL, so the inherited
 * default methods run their real logic.
 */
class ChargingSessionDaoContinuityTest {

    // --- delete ---

    @Test
    fun `deleting a session clears the flag on its immediate same-vehicle follower`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.deleteAndClearStaleContinuity(a, now = 999)

        assertNull(dao.findById(a.id))
        assertFalse(dao.findById(b.id)!!.continuesPrevious)
        assertEquals(999, dao.findById(b.id)!!.updatedAt)
    }

    @Test
    fun `deleting a session leaves an unflagged follower untouched`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = false))
        val bBefore = dao.findById(b.id)!!

        dao.deleteAndClearStaleContinuity(a, now = 999)

        assertEquals(bBefore, dao.findById(b.id))
    }

    @Test
    fun `deleting the last session on a timeline does nothing else`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1, continuesPrevious = true))
        val b = dao.put(session(t = 200, vehicleId = 1))

        dao.deleteAndClearStaleContinuity(b, now = 999)

        assertNull(dao.findById(b.id))
        // The deleted session's own flag pointed backwards; a's flag is its
        // own attestation and stays.
        assertTrue(dao.findById(a.id)!!.continuesPrevious)
    }

    @Test
    fun `delete healing follows the vehicle timeline, not raw time order`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val otherCar = dao.put(session(t = 150, vehicleId = 2, continuesPrevious = true))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.deleteAndClearStaleContinuity(a, now = 999)

        // Vehicle 2's session is nearer in time but on a different timeline.
        assertTrue(dao.findById(otherCar.id)!!.continuesPrevious)
        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `null vehicleId sessions form their own timeline`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = null))
        dao.put(session(t = 150, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = null, continuesPrevious = true))

        dao.deleteAndClearStaleContinuity(a, now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    // --- update ---

    @Test
    fun `update without moving the session keeps the follower's flag`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(a.copy(notes = "edited"), now = 999)

        assertEquals("edited", dao.findById(a.id)!!.notes)
        assertTrue(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `nudging the time while staying directly before the follower keeps the flag`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(a.copy(sessionStart = 150), now = 999)

        assertTrue(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving the session past its follower clears the follower's flag`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(a.copy(sessionStart = 300), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving the session to another vehicle clears the follower's flag`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(a.copy(vehicleId = 2), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving the session earlier so another session slots between clears the flag`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 50, vehicleId = 1))
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        // a moves before the t=50 session; b's immediate predecessor is now
        // the t=50 session, which a never attested anything about.
        dao.updateAndClearStaleContinuity(a.copy(sessionStart = 10), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving a session in front of a flagged session does not clear that flag`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))
        val far = dao.put(session(t = 900, vehicleId = 1))

        // `far` backfills into the a→b gap: the gap only narrows, the
        // attestation "nothing untracked in between" still holds.
        dao.updateAndClearStaleContinuity(far.copy(sessionStart = 150), now = 999)

        assertTrue(dao.findById(b.id)!!.continuesPrevious)
    }

    // --- update: the moved session's own flag ---

    @Test
    fun `moving a flagged session under a new predecessor clears its own flag`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))
        dao.put(session(t = 300, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        // b attested continuity against the t=100 session; after the move its
        // predecessor is the t=300 session, which it never attested against.
        dao.updateAndClearStaleContinuity(b.copy(sessionStart = 400), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving a flagged session while keeping its predecessor keeps its own flag`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        // Still directly after the t=100 session — the attestation holds.
        dao.updateAndClearStaleContinuity(b.copy(sessionStart = 150), now = 999)

        assertTrue(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving a flagged session to another vehicle clears its own flag`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(b.copy(vehicleId = 2), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `moving a flagged session to the front of its timeline clears its own flag`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))

        dao.updateAndClearStaleContinuity(b.copy(sessionStart = 50), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `same-timestamp ties use id order when deciding the predecessor`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))                                    // id 1
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = true))  // id 2
        dao.put(session(t = 200, vehicleId = 1))                                    // id 3, same-day import

        // In (start, id) timeline order b's predecessor is the t=100 session;
        // after the move it is the same-timestamp id-3 session — an
        // attestation b never made. A bare <=/>= comparison saw id 3 as the
        // predecessor on both sides and kept the stale flag.
        dao.updateAndClearStaleContinuity(b.copy(sessionStart = 300), now = 999)

        assertFalse(dao.findById(b.id)!!.continuesPrevious)
    }

    @Test
    fun `deleting a same-timestamp later session does not heal the earlier one`() = runBlocking {
        val dao = FakeSessionDao()
        val a = dao.put(session(t = 100, vehicleId = 1, continuesPrevious = true))
        val b = dao.put(session(t = 100, vehicleId = 1))

        // b sits AFTER a in (start, id) order, so a is not b's follower and
        // a's own attestation (about whatever preceded a) must survive.
        dao.deleteAndClearStaleContinuity(b, now = 999)

        assertTrue(dao.findById(a.id)!!.continuesPrevious)
    }

    @Test
    fun `ticking the flag in the same edit that moves the session keeps it`() = runBlocking {
        val dao = FakeSessionDao()
        dao.put(session(t = 100, vehicleId = 1))
        dao.put(session(t = 300, vehicleId = 1))
        val b = dao.put(session(t = 200, vehicleId = 1, continuesPrevious = false))

        // The user both moved the session and ticked "continues previous" in
        // one save — the attestation is about the NEW position and stands.
        dao.updateAndClearStaleContinuity(
            b.copy(sessionStart = 400, continuesPrevious = true),
            now = 999,
        )

        assertTrue(dao.findById(b.id)!!.continuesPrevious)
    }

    // --- chunked bulk updates ---

    @Test
    fun `assignTripToIdsChunked reaches every id across chunk boundaries`() = runBlocking {
        val dao = FakeSessionDao()
        val ids = (1..2001).map { dao.put(session(t = it.toLong(), vehicleId = 1)).id }

        val updated = dao.assignTripToIdsChunked(ids, tripId = 7, now = 999)

        assertEquals(2001, updated)
        assertTrue(ids.all { dao.findById(it)!!.tripId == 7L })
    }

    @Test
    fun `setCoordinatesForIdsChunked reaches every id across chunk boundaries`() = runBlocking {
        val dao = FakeSessionDao()
        val ids = (1..901).map { dao.put(session(t = it.toLong(), vehicleId = 1)).id }

        val updated = dao.setCoordinatesForIdsChunked(ids, lat = 43.6, lng = -79.4, now = 999)

        assertEquals(901, updated)
        assertTrue(ids.all { dao.findById(it)!!.latitude == 43.6 })
    }

    private fun session(
        t: Long,
        vehicleId: Long?,
        continuesPrevious: Boolean = false,
    ): ChargingSession = ChargingSession(
        sessionStart = t,
        vehicleId = vehicleId,
        continuesPrevious = continuesPrevious,
        createdAt = 0,
        updatedAt = 0,
    )
}

/** In-memory stand-in whose query methods mirror their SQL semantics. */
private class FakeSessionDao : ChargingSessionDao {

    private val rows = linkedMapOf<Long, ChargingSession>()
    private var nextId = 1L

    /** Insert helper that returns the stored row (with its assigned id). */
    suspend fun put(session: ChargingSession): ChargingSession {
        val id = insert(session)
        return rows.getValue(id)
    }

    override fun observeAll(): Flow<List<ChargingSession>> = flowOf(rows.values.toList())

    override fun observeForTrip(tripId: Long): Flow<List<ChargingSession>> =
        flowOf(rows.values.filter { it.tripId == tripId })

    override fun observeBrands(): Flow<List<String>> = flowOf(emptyList())

    override fun observeCities(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun findById(id: Long): ChargingSession? = rows[id]

    override suspend fun insert(session: ChargingSession): Long {
        val id = if (session.id == 0L) nextId++ else session.id
        rows[id] = session.copy(id = id)
        return id
    }

    override suspend fun insertAll(sessions: List<ChargingSession>): List<Long> =
        sessions.map { insert(it) }

    override suspend fun update(session: ChargingSession) {
        if (rows.containsKey(session.id)) rows[session.id] = session
    }

    override suspend fun delete(session: ChargingSession) {
        rows.remove(session.id)
    }

    override suspend fun assignTripToIds(ids: List<Long>, tripId: Long?, now: Long): Int {
        var n = 0
        ids.forEach { id ->
            rows[id]?.let { rows[id] = it.copy(tripId = tripId, updatedAt = now); n++ }
        }
        return n
    }

    override suspend fun setCoordinatesForIds(ids: List<Long>, lat: Double, lng: Double, now: Long): Int {
        var n = 0
        ids.forEach { id ->
            rows[id]?.let { rows[id] = it.copy(latitude = lat, longitude = lng, updatedAt = now); n++ }
        }
        return n
    }

    override suspend fun deleteAll() = rows.clear()

    // Mirrors the SQL: adjacency in (sessionStart, id) lexicographic order,
    // excluding :excludeId so a moved row can't match its own old position.
    override suspend fun firstAfter(vehicleId: Long?, start: Long, excludeId: Long): ChargingSession? =
        rows.values
            .filter {
                it.vehicleId == vehicleId && it.id != excludeId &&
                    (it.sessionStart > start || (it.sessionStart == start && it.id > excludeId))
            }
            .minWithOrNull(compareBy({ it.sessionStart }, { it.id }))

    override suspend fun lastBefore(vehicleId: Long?, start: Long, excludeId: Long): ChargingSession? =
        rows.values
            .filter {
                it.vehicleId == vehicleId && it.id != excludeId &&
                    (it.sessionStart < start || (it.sessionStart == start && it.id < excludeId))
            }
            .maxWithOrNull(compareBy({ it.sessionStart }, { it.id }))

    override suspend fun clearContinuesPrevious(id: Long, now: Long) {
        rows[id]?.let { rows[id] = it.copy(continuesPrevious = false, updatedAt = now) }
    }
}
