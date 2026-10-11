package net.redngreen.easypos.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import net.redngreen.easypos.core.data.dao.CashEntryDao
import net.redngreen.easypos.core.data.dao.CategoryDao
import net.redngreen.easypos.core.data.dao.ConflictLogDao
import net.redngreen.easypos.core.data.dao.DrawerCountDao
import net.redngreen.easypos.core.data.dao.JournalDao
import net.redngreen.easypos.core.data.dao.PaymentTypeDao
import net.redngreen.easypos.core.data.dao.ProductDao
import net.redngreen.easypos.core.data.dao.SaleDao
import net.redngreen.easypos.core.data.dao.SettingDao
import net.redngreen.easypos.core.data.dao.StockDeltaDao
import net.redngreen.easypos.core.data.dao.SyncStateDao
import net.redngreen.easypos.core.data.dao.TenantConfigDao
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.CashEntry
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.ConflictLog
import net.redngreen.easypos.core.data.entity.DrawerCount
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.Sale
import net.redngreen.easypos.core.data.entity.SaleLine
import net.redngreen.easypos.core.data.entity.Setting
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.entity.SyncState
import net.redngreen.easypos.core.data.entity.TenantConfig
import net.redngreen.easypos.core.data.entity.User

/**
 * Everything the pull applier needs from local storage, behind one seam so
 * the reconciliation logic stays pure and unit-testable (design D5). The
 * Room-backed implementation is the only production wiring.
 */
interface LocalSyncStore {
    /** Self-echo guard: an op we ourselves pushed comes back on pull. */
    suspend fun journalExists(opId: String): Boolean

    /** Local `updated_at` for an entity row, or null when absent/not tracked. */
    suspend fun currentUpdatedAt(entity: String, entityId: String): Long?

    suspend fun putProduct(product: Product)

    suspend fun putCategory(category: Category)

    suspend fun putUser(user: User)

    suspend fun putPaymentType(paymentType: PaymentType)

    suspend fun putTenantConfig(config: TenantConfig)

    suspend fun putSetting(setting: Setting)

    suspend fun putSale(sale: Sale, lines: List<SaleLine>)

    suspend fun putStockDelta(delta: StockDelta)

    suspend fun putCashEntry(entry: CashEntry)

    suspend fun putDrawerCount(count: DrawerCount)

    suspend fun voidSale(id: String, at: Long)

    suspend fun logConflict(conflict: ConflictLog)

    suspend fun cursor(): Long

    suspend fun setCursor(cursor: Long)
}

@Singleton
class RoomLocalSyncStore @Inject constructor(
    private val journalDao: JournalDao,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val userDao: UserDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val tenantConfigDao: TenantConfigDao,
    private val settingDao: SettingDao,
    private val saleDao: SaleDao,
    private val stockDeltaDao: StockDeltaDao,
    private val cashEntryDao: CashEntryDao,
    private val drawerCountDao: DrawerCountDao,
    private val conflictLogDao: ConflictLogDao,
    private val syncStateDao: SyncStateDao,
) : LocalSyncStore {

    override suspend fun journalExists(opId: String): Boolean = journalDao.exists(opId)

    override suspend fun currentUpdatedAt(entity: String, entityId: String): Long? =
        when (entity) {
            ENTITY_PRODUCT -> productDao.getById(entityId)?.updatedAt
            ENTITY_CATEGORY -> categoryDao.getById(entityId)?.updatedAt
            ENTITY_USER -> userDao.getById(entityId)?.updatedAt
            ENTITY_PAYMENT_TYPE -> paymentTypeDao.getById(entityId)?.updatedAt
            ENTITY_TENANT_CONFIG -> tenantConfigDao.get()?.updatedAt
            ENTITY_SETTING -> settingDao.getSetting(entityId)?.updatedAt
            ENTITY_SALE -> saleDao.getById(entityId)?.updatedAt
            else -> null
        }

    override suspend fun putProduct(product: Product) {
        // stockOnHand is a cache over deltas: preserve a live value, otherwise
        // recompute from whatever deltas have already arrived (restore order is
        // the server's change-log order, so deltas may precede the product row).
        val existing = productDao.getById(product.id)
        val onHand = existing?.stockOnHand ?: stockDeltaDao.sumFor(product.id)
        productDao.upsert(product.copy(stockOnHand = onHand))
    }

    override suspend fun putCategory(category: Category) {
        categoryDao.upsert(category)
    }

    override suspend fun putUser(user: User) {
        userDao.upsert(user)
    }

    override suspend fun putPaymentType(paymentType: PaymentType) {
        paymentTypeDao.upsert(paymentType)
    }

    override suspend fun putTenantConfig(config: TenantConfig) {
        tenantConfigDao.upsert(config)
    }

    override suspend fun putSetting(setting: Setting) {
        settingDao.upsert(setting)
    }

    override suspend fun putSale(sale: Sale, lines: List<SaleLine>) {
        if (saleDao.getById(sale.id) != null) return // insert-only ledger row
        saleDao.insert(sale)
        saleDao.insertLines(lines)
    }

    override suspend fun putStockDelta(delta: StockDelta) {
        if (stockDeltaDao.getById(delta.id) != null) return
        stockDeltaDao.insert(delta)
        // Keep the denormalized on-hand cache warm as deltas land.
        productDao.bumpStock(delta.productId, delta.delta)
    }

    override suspend fun putCashEntry(entry: CashEntry) {
        cashEntryDao.insert(entry)
    }

    override suspend fun putDrawerCount(count: DrawerCount) {
        drawerCountDao.insert(count)
    }

    override suspend fun voidSale(id: String, at: Long) {
        saleDao.markVoided(id, at)
    }

    override suspend fun logConflict(conflict: ConflictLog) {
        conflictLogDao.insert(conflict)
    }

    override suspend fun cursor(): Long =
        syncStateDao.get(KEY_CURSOR)?.toLongOrNull() ?: 0L

    override suspend fun setCursor(cursor: Long) {
        syncStateDao.put(SyncState(KEY_CURSOR, cursor.toString()))
    }

    companion object {
        const val KEY_CURSOR = "pull_cursor"
        const val ENTITY_PRODUCT = "product"
        const val ENTITY_CATEGORY = "category"
        const val ENTITY_USER = "user"
        const val ENTITY_PAYMENT_TYPE = "payment_type"
        const val ENTITY_TENANT_CONFIG = "tenant_config"
        const val ENTITY_SETTING = "setting"
        const val ENTITY_SALE = "sale"
    }
}
