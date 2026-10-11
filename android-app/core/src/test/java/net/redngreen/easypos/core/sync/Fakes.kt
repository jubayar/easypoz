package net.redngreen.easypos.core.sync

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.redngreen.easypos.core.data.dao.ConflictLogDao
import net.redngreen.easypos.core.data.dao.JournalDao
import net.redngreen.easypos.core.data.dao.SyncStateDao
import net.redngreen.easypos.core.data.entity.CashEntry
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.ConflictLog
import net.redngreen.easypos.core.data.entity.DrawerCount
import net.redngreen.easypos.core.data.entity.JournalOp
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.Sale
import net.redngreen.easypos.core.data.entity.SaleLine
import net.redngreen.easypos.core.data.entity.Setting
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.entity.SyncState
import net.redngreen.easypos.core.data.entity.TenantConfig
import net.redngreen.easypos.core.data.entity.User
import net.redngreen.easypos.core.data.sync.AuthRemoteDataSource
import net.redngreen.easypos.core.data.sync.AuthTokens
import net.redngreen.easypos.core.data.sync.DeviceRegisterResponse
import net.redngreen.easypos.core.data.sync.LocalSyncStore
import net.redngreen.easypos.core.data.sync.OpAck
import net.redngreen.easypos.core.data.sync.PullOpWire
import net.redngreen.easypos.core.data.sync.PullResponse
import net.redngreen.easypos.core.data.sync.PushBatchRequest
import net.redngreen.easypos.core.data.sync.PushResponse
import net.redngreen.easypos.core.data.sync.RequestCodeResponse
import net.redngreen.easypos.core.data.sync.SyncOpWire
import net.redngreen.easypos.core.data.sync.SyncRemoteDataSource
import net.redngreen.easypos.core.data.sync.TokenPair
import net.redngreen.easypos.core.data.sync.TokenStore
import net.redngreen.easypos.core.data.sync.VerifyResponse

/** In-memory [JournalDao]; mirrors the Room queries the engine relies on. */
class FakeJournalDao : JournalDao {
    private val ops = mutableListOf<JournalOp>()
    private val pendingCount = MutableStateFlow(0)
    private var nextSeq = 1L

    override suspend fun append(op: JournalOp): Long {
        val seq = if (op.seq > 0L) op.seq else nextSeq
        nextSeq = maxOf(nextSeq, seq + 1)
        ops.add(op.copy(seq = seq))
        refresh()
        return seq
    }

    override suspend fun pending(limit: Int): List<JournalOp> =
        ops.filter { it.syncedAt == null }.sortedBy { it.seq }.take(limit)

    override fun observePendingCount(): Flow<Int> = pendingCount

    override suspend fun markSynced(opIds: List<String>, at: Long) {
        val ids = opIds.toSet()
        for (i in ops.indices) {
            if (ops[i].id in ids) ops[i] = ops[i].copy(syncedAt = at)
        }
        refresh()
    }

    override suspend fun exists(id: String): Boolean = ops.any { it.id == id }

    val all: List<JournalOp> get() = ops.toList()

    private fun refresh() {
        pendingCount.value = ops.count { it.syncedAt == null }
    }
}

class FakeSyncStateDao : SyncStateDao {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun get(key: String): String? = values.value[key]

    override fun observe(key: String): Flow<String?> = values.map { it[key] }

    override suspend fun put(state: SyncState) {
        values.value = values.value + (state.key to state.value)
    }
}

class FakeConflictLogDao : ConflictLogDao {
    val rows = mutableListOf<ConflictLog>()

    override suspend fun insert(conflict: ConflictLog) {
        rows.add(conflict)
    }

    override fun observeRecent(): Flow<List<ConflictLog>> = MutableStateFlow(rows.toList())
}

/** In-memory [LocalSyncStore] used by the applier + engine tests. */
class FakeLocalSyncStore : LocalSyncStore {
    val products = mutableMapOf<String, Product>()
    val categories = mutableMapOf<String, Category>()
    val users = mutableMapOf<String, User>()
    val paymentTypes = mutableMapOf<String, PaymentType>()
    var tenantConfig: TenantConfig? = null
    val settings = mutableMapOf<String, Setting>()
    val sales = mutableMapOf<String, Sale>()
    val saleLines = mutableMapOf<String, List<SaleLine>>()
    val stockDeltas = mutableMapOf<String, StockDelta>()
    val cashEntries = mutableMapOf<String, CashEntry>()
    val drawerCounts = mutableMapOf<String, DrawerCount>()
    val conflicts = mutableListOf<ConflictLog>()
    val voided = mutableListOf<String>()
    val knownJournalOpIds = mutableSetOf<String>()
    private var cursorValue = 0L

    override suspend fun journalExists(opId: String): Boolean = opId in knownJournalOpIds

    override suspend fun currentUpdatedAt(entity: String, entityId: String): Long? = when (entity) {
        "product" -> products[entityId]?.updatedAt
        "category" -> categories[entityId]?.updatedAt
        "user" -> users[entityId]?.updatedAt
        "payment_type" -> paymentTypes[entityId]?.updatedAt
        "tenant_config" -> tenantConfig?.updatedAt
        "setting" -> settings[entityId]?.updatedAt
        "sale" -> sales[entityId]?.updatedAt
        else -> null
    }

    override suspend fun putProduct(product: Product) { products[product.id] = product }
    override suspend fun putCategory(category: Category) { categories[category.id] = category }
    override suspend fun putUser(user: User) { users[user.id] = user }
    override suspend fun putPaymentType(paymentType: PaymentType) { paymentTypes[paymentType.id] = paymentType }
    override suspend fun putTenantConfig(config: TenantConfig) { tenantConfig = config }
    override suspend fun putSetting(setting: Setting) { settings[setting.key] = setting }
    override suspend fun putSale(sale: Sale, lines: List<SaleLine>) {
        sales[sale.id] = sale
        saleLines[sale.id] = lines
    }
    override suspend fun putStockDelta(delta: StockDelta) { stockDeltas[delta.id] = delta }
    override suspend fun putCashEntry(entry: CashEntry) { cashEntries[entry.id] = entry }
    override suspend fun putDrawerCount(count: DrawerCount) { drawerCounts[count.id] = count }
    override suspend fun voidSale(id: String, at: Long) { voided.add(id) }
    override suspend fun logConflict(conflict: ConflictLog) { conflicts.add(conflict) }
    override suspend fun cursor(): Long = cursorValue
    override suspend fun setCursor(cursor: Long) { cursorValue = cursor }
}

/**
 * Fake backend: dedupes ops by id (server idempotency), records what it
 * received, and can simulate being offline. Pull returns whatever the test
 * seeds.
 */
class FakeSyncRemote : SyncRemoteDataSource {
    var online = true
    var failWith: Exception? = null
    var pullOps: List<PullOpWire> = emptyList()
    var pullCursor = 0L
    var hasMore = false

    /** Op ids the server should reject as malformed (terminal). */
    val rejectIds = mutableSetOf<String>()

    /** Ops the server actually applied (first time seen), in order. */
    val applied = mutableListOf<SyncOpWire>()
    val receivedBatches = mutableListOf<List<SyncOpWire>>()
    private val seenIds = mutableSetOf<String>()

    override suspend fun push(accessToken: String, batch: PushBatchRequest): PushResponse {
        if (!online) throw IOException("offline")
        failWith?.let { throw it }
        receivedBatches.add(batch.ops)
        return PushResponse(
            acks = batch.ops.map { op ->
                when {
                    op.id in rejectIds -> OpAck(op.id, "rejected")
                    seenIds.add(op.id) -> {
                        applied.add(op)
                        OpAck(op.id, "applied")
                    }
                    else -> OpAck(op.id, "duplicate")
                }
            },
        )
    }

    override suspend fun pull(accessToken: String, since: Long): PullResponse {
        if (!online) throw IOException("offline")
        failWith?.let { throw it }
        val page = pullOps.filter { it.seq > since }
        val cursor = page.maxOfOrNull { it.seq } ?: since
        return PullResponse(ops = page, cursor = cursor.toString(), hasMore = hasMore)
    }
}

class FakeAuth : AuthRemoteDataSource {
    override suspend fun requestCode(identifier: String, shopName: String?): RequestCodeResponse =
        RequestCodeResponse(isNewAccount = true, devCode = "123456")

    override suspend fun verify(identifier: String, code: String, shopName: String?): VerifyResponse =
        VerifyResponse(
            shopId = "shop-1",
            accessToken = "access-1",
            refreshToken = "refresh-1",
            accessExpiresAt = FAR_FUTURE,
            refreshExpiresAt = FAR_FUTURE,
        )

    override suspend fun refresh(refreshToken: String): TokenPair =
        TokenPair(accessToken = "access-2", refreshToken = "refresh-2", accessExpiresAt = FAR_FUTURE, refreshExpiresAt = FAR_FUTURE)

    override suspend fun registerDevice(accessToken: String, deviceName: String?): DeviceRegisterResponse =
        DeviceRegisterResponse(
            deviceId = "device-1",
            demotedCount = 1,
            accessToken = "access-dev",
            refreshToken = "refresh-dev",
            accessExpiresAt = FAR_FUTURE,
            refreshExpiresAt = FAR_FUTURE,
        )

    companion object {
        const val FAR_FUTURE = "2099-01-01T00:00:00Z"
    }
}

class FakeTokenStore : TokenStore {
    var tokens: AuthTokens? = null

    fun signedIn(shopId: String = "shop-1") {
        tokens = AuthTokens(
            shopId = shopId,
            accessToken = "access-1",
            refreshToken = "refresh-1",
            accessExpiresAt = Long.MAX_VALUE,
            refreshExpiresAt = Long.MAX_VALUE,
        )
    }

    override suspend fun load(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
