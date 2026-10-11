package net.redngreen.easypos.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.entity.JournalOp
import net.redngreen.easypos.core.sync.PullOpWire
import net.redngreen.easypos.core.sync.RemoteApplier
import net.redngreen.easypos.core.sync.SyncEngine
import net.redngreen.easypos.core.sync.SyncOpWire
import net.redngreen.easypos.core.sync.SyncStatusRepository

internal class SyncHarness(
    val journal: FakeJournalDao,
    val store: FakeLocalSyncStore,
    val remote: FakeSyncRemote,
    val identity: LocalIdentity,
    val engine: SyncEngine,
)

/** Builds a fully-wired engine over in-memory fakes and a signed-in token. */
internal fun harness(
    journal: FakeJournalDao = FakeJournalDao(),
    store: FakeLocalSyncStore = FakeLocalSyncStore(),
    remote: FakeSyncRemote = FakeSyncRemote(),
    tokens: FakeTokenStore = FakeTokenStore().apply { signedIn() },
): SyncHarness {
    val identity = LocalIdentity(FakeSyncStateDao())
    val status = SyncStatusRepository(journal, CoroutineScope(UnconfinedTestDispatcher()))
    val engine = SyncEngine(
        journalDao = journal,
        store = store,
        remote = remote,
        auth = FakeAuth(),
        tokens = tokens,
        identity = identity,
        applier = RemoteApplier(store, identity),
        status = status,
        clock = { 1_000L },
    )
    return SyncHarness(journal, store, remote, identity, engine)
}

internal fun productJson(id: String, updatedAt: String = "2026-01-01T00:00:00Z"): String =
    """{"id":"$id","name":"Milo","priceCents":100,""" +
        """"createdAt":"2026-01-01T00:00:00Z","updatedAt":"$updatedAt"}"""

internal suspend fun FakeJournalDao.enqueueProduct(vararg ids: String) {
    ids.forEach { id ->
        append(
            JournalOp(
                id = id,
                shopId = "shop-1",
                deviceId = "device-1",
                entity = "product",
                opType = "UPSERT",
                payloadJson = productJson(id),
                actorUserId = null,
                createdAt = 1_000L,
            )
        )
    }
}

internal fun wire(id: String, entity: String = "product"): SyncOpWire = SyncOpWire(
    id = id,
    seq = 0,
    entity = entity,
    opType = "UPSERT",
    payload = Json.parseToJsonElement(productJson(id)),
    createdAt = "2026-01-01T00:00:00Z",
)

internal fun pullOp(
    entityId: String,
    journalOpId: String = "j-$entityId",
    seq: Long = 1,
    updatedAt: String = "2026-01-01T00:00:05Z",
    entity: String = "product",
): PullOpWire = PullOpWire(
    seq = seq,
    journalOpId = journalOpId,
    entity = entity,
    entityId = entityId,
    opType = "UPSERT",
    payload = Json.parseToJsonElement(productJson(entityId, updatedAt = updatedAt)),
    createdAt = "2026-01-01T00:00:00Z",
)
