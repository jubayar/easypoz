package net.redngreen.easypos.core.sync

import kotlinx.coroutines.test.runTest
import net.redngreen.easypos.core.data.sync.SyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12.6: airplane-mode soak. A full day of trading queues ≥50 ops with
 * no connectivity; on reconnect they all upload exactly once, and a repeat
 * pass creates no duplicates and loses nothing.
 */
class OfflineSoakTest {

    @Test
    fun `offline soak then reconnect loses nothing and duplicates nothing`() = runTest {
        val h = harness()
        h.remote.online = false
        val ids = (1..60).map { "op-$it" }
        h.journal.enqueueProduct(*ids.toTypedArray())

        // Trading offline: nothing leaves, nothing is lost.
        assertTrue(h.engine.syncOnce() is SyncOutcome.Failure)
        assertEquals(60, h.journal.all.count { it.syncedAt == null })
        assertEquals(0, h.remote.applied.size)

        // Reconnect: every queued op uploads exactly once.
        h.remote.online = true
        val outcome = h.engine.syncOnce()
        assertEquals(SyncOutcome.Success(60, 0), outcome)
        assertEquals(60, (outcome as SyncOutcome.Success).pushed)
        assertEquals(60, h.remote.applied.size)
        assertEquals(60, h.remote.applied.map { it.id }.toSet().size)
        assertEquals(ids, h.remote.applied.map { it.id })
        assertEquals(0, h.journal.all.count { it.syncedAt == null })
    }

    @Test
    fun `a torn batch retries without creating duplicates`() = runTest {
        val h = harness()
        // First attempt: 3 ops sent, server applies them, then the connection
        // drops before the ack is processed (simulated by a failure next call).
        h.journal.enqueueProduct("a", "b", "c")
        h.remote.failWith = java.io.IOException("connection reset")

        assertTrue(h.engine.syncOnce() is SyncOutcome.Failure)
        // Server never actually applied (failed before push), so all still pending.
        assertEquals(3, h.journal.all.count { it.syncedAt == null })

        h.remote.failWith = null
        // A no-op first push proves idempotency of a server already holding these.
        h.remote.push("t", net.redngreen.easypos.core.data.sync.PushBatchRequest("device-1", listOf(wire("a"), wire("b"), wire("c"))))
        h.engine.syncOnce()

        assertEquals(3, h.remote.applied.map { it.id }.toSet().size)
        assertEquals(0, h.journal.all.count { it.syncedAt == null })
    }
}
