package net.redngreen.easypos.core.sync

import kotlinx.coroutines.test.runTest
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.sync.LwwDecision
import net.redngreen.easypos.core.sync.RemoteApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Last-write-wins resolution + conflict logging (task 12.2). */
class RemoteApplierTest {

    private fun product(id: String, updatedAt: Long) = Product(
        id = id,
        shopId = "shop-1",
        name = "Local",
        priceCents = 100,
        updatedAt = updatedAt,
    )

    private suspend fun applier(store: FakeLocalSyncStore) =
        RemoteApplier(store, LocalIdentity(FakeSyncStateDao()))

    @Test
    fun `decide picks the newer side`() {
        assertEquals(LwwDecision.NO_LOCAL, RemoteApplier.decide(null, 5))
        assertEquals(LwwDecision.REMOTE_WINS, RemoteApplier.decide(5, 6))
        assertEquals(LwwDecision.LOCAL_WINS, RemoteApplier.decide(5, 5))
        assertEquals(LwwDecision.LOCAL_WINS, RemoteApplier.decide(6, 5))
    }

    @Test
    fun `remote newer wins and is logged as a conflict`() = runTest {
        val store = FakeLocalSyncStore()
        store.products["p1"] = product("p1", updatedAt = 1_000L)

        applier(store).apply(pullOp("p1", updatedAt = "2026-01-01T00:00:05Z"))

        assertEquals(1_767_225_605_000L, store.products["p1"]!!.updatedAt)
        assertEquals(1, store.conflicts.size)
        assertEquals("REMOTE_WINS", store.conflicts.single().resolution)
    }

    @Test
    fun `local newer wins — remote write is dropped and logged`() = runTest {
        val store = FakeLocalSyncStore()
        store.products["p1"] = product("p1", updatedAt = 2_000_000_000_000L)

        applier(store).apply(pullOp("p1", updatedAt = "2026-01-01T00:00:05Z"))

        assertEquals(2_000_000_000_000L, store.products["p1"]!!.updatedAt)
        assertEquals("LOCAL_WINS", store.conflicts.single().resolution)
    }

    @Test
    fun `a brand-new remote row applies with no conflict logged`() = runTest {
        val store = FakeLocalSyncStore()

        applier(store).apply(pullOp("p1"))

        assertEquals("Milo", store.products["p1"]?.name)
        assertTrue(store.conflicts.isEmpty())
    }

    @Test
    fun `sale VOID flips the local sale voided`() = runTest {
        val store = FakeLocalSyncStore()
        val op = pullOp("s1", entity = "sale").copy(opType = "VOID")

        applier(store).apply(op)

        assertEquals(listOf("s1"), store.voided)
        assertNull(store.sales["s1"])
    }
}
