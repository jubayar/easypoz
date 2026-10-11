package net.redngreen.easypos.core.sync

import kotlinx.coroutines.test.runTest
import net.redngreen.easypos.core.sync.PushBatchRequest
import net.redngreen.easypos.core.sync.SyncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Push/pull orchestration (task 12.1) over the fake backend. */
class SyncEngineTest {

    @Test
    fun `push sends pending ops in seq order and marks them synced`() = runTest {
        val h = harness()
        h.journal.enqueueProduct("a", "b", "c")

        val outcome = h.engine.syncOnce()

        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(3, (outcome as SyncOutcome.Success).pushed)
        assertEquals(listOf("a", "b", "c"), h.remote.applied.map { it.id })
        assertTrue(h.journal.all.all { it.syncedAt != null })
    }

    @Test
    fun `push batches are capped at 200 ops, in order`() = runTest {
        val h = harness()
        h.journal.enqueueProduct(*((1..250).map { "op-$it" }.toTypedArray()))

        h.engine.syncOnce()

        assertEquals(listOf(200, 50), h.remote.receivedBatches.map { it.size })
        assertEquals("op-1", h.remote.receivedBatches.first().first().id)
    }

    @Test
    fun `offline leaves everything queued and reports a failure`() = runTest {
        val h = harness()
        h.remote.online = false
        h.journal.enqueueProduct("a", "b")

        val outcome = h.engine.syncOnce()

        assertTrue(outcome is SyncOutcome.Failure)
        assertEquals(2, h.journal.all.count { it.syncedAt == null })
        assertTrue(h.remote.applied.isEmpty())
    }

    @Test
    fun `not signed in is a no-op`() = runTest {
        val h = harness(tokens = FakeTokenStore())
        h.journal.enqueueProduct("a")

        assertEquals(SyncOutcome.NotSignedIn, h.engine.syncOnce())
        assertEquals(1, h.journal.all.count { it.syncedAt == null })
    }

    @Test
    fun `replayed push is idempotent — no duplicate apply`() = runTest {
        val h = harness()
        h.journal.enqueueProduct("a")
        // Simulate a crash after the server applied but before we marked synced.
        h.remote.push("access-1", PushBatchRequest("device-1", listOf(wire("a"))))

        h.engine.syncOnce()

        assertEquals(1, h.remote.applied.size)
        assertTrue(h.journal.all.all { it.syncedAt != null })
    }

    @Test
    fun `rejected ops drain the queue instead of wedging it`() = runTest {
        val h = harness()
        h.remote.rejectIds += "a"
        h.journal.enqueueProduct("a")

        h.engine.syncOnce()

        assertTrue(h.journal.all.all { it.syncedAt != null })
        assertTrue(h.remote.applied.isEmpty())
    }

    @Test
    fun `pull applies a remote op and advances the cursor`() = runTest {
        val h = harness()
        h.remote.pullOps = listOf(pullOp("p1", seq = 7))
        h.remote.pullCursor = 7

        h.engine.syncOnce()

        assertEquals("Milo", h.store.products["p1"]?.name)
        assertEquals(7L, h.store.cursor())
    }

    @Test
    fun `pull skips self-echo ops that this device pushed`() = runTest {
        val h = harness()
        h.remote.pullOps = listOf(pullOp("p1", journalOpId = "echo-1", seq = 3))
        h.remote.pullCursor = 3
        h.store.knownJournalOpIds += "echo-1"

        h.engine.syncOnce()

        assertTrue(h.store.products.isEmpty())
        assertEquals(3L, h.store.cursor())
    }
}
