package net.redngreen.easypos.core.data.repo

import androidx.room.withTransaction
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.data.backup.BackupDocument
import net.redngreen.easypos.core.data.backup.BackupUser
import net.redngreen.easypos.core.database.dao.BackupDao
import net.redngreen.easypos.core.database.dao.CashEntryDao
import net.redngreen.easypos.core.database.dao.CategoryDao
import net.redngreen.easypos.core.database.dao.DrawerCountDao
import net.redngreen.easypos.core.database.dao.PaymentTypeDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.SaleDao
import net.redngreen.easypos.core.database.dao.SettingDao
import net.redngreen.easypos.core.database.dao.StockDeltaDao
import net.redngreen.easypos.core.database.dao.TenantConfigDao
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.core.database.db.EasyPosDatabase
import net.redngreen.easypos.core.database.entity.CashEntry
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.DrawerCount
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.database.entity.Sale
import net.redngreen.easypos.core.database.entity.SaleLine
import net.redngreen.easypos.core.database.entity.Setting
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.entity.TenantConfig
import net.redngreen.easypos.core.database.entity.User
import net.redngreen.easypos.core.database.journal.cashEntryPayload
import net.redngreen.easypos.core.database.journal.categoryPayload
import net.redngreen.easypos.core.database.journal.drawerCountPayload
import net.redngreen.easypos.core.database.journal.paymentTypePayload
import net.redngreen.easypos.core.database.journal.productPayload
import net.redngreen.easypos.core.database.journal.salePayload
import net.redngreen.easypos.core.database.journal.settingPayload
import net.redngreen.easypos.core.database.journal.stockDeltaPayload
import net.redngreen.easypos.core.database.journal.tenantConfigPayload

/**
 * Manual full backup + restore to a single file (task 11.3). Export serializes
 * the shop dataset to JSON; restore replaces local data in one transaction.
 * PIN hashes and sync tokens are excluded by construction — useful for safety
 * and for moving a shop without shipping credentials.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: EasyPosDatabase,
    private val backupDao: BackupDao,
    private val identity: LocalIdentity,
    private val tenantConfigDao: TenantConfigDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val stockDeltaDao: StockDeltaDao,
    private val cashEntryDao: CashEntryDao,
    private val drawerCountDao: DrawerCountDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val settingDao: SettingDao,
) {
    data class RestoreSummary(val products: Int, val sales: Int, val users: Int)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(doc: BackupDocument): String = json.encodeToString(doc)

    suspend fun export(): BackupDocument = BackupDocument(
        exportedAt = Instant.now().toString(),
        tenantConfig = backupDao.tenantConfig()?.let { tenantConfigPayload(it) },
        users = backupDao.users().map {
            BackupUser(
                id = it.id,
                name = it.name,
                role = it.role,
                active = it.active,
                createdAt = it.createdAt.toIso(),
                updatedAt = it.updatedAt.toIso(),
            )
        },
        categories = backupDao.categories().map { categoryPayload(it) },
        products = backupDao.products().map { productPayload(it) },
        sales = backupDao.sales().map { sale ->
            salePayload(sale, backupDao.saleLines().filter { it.saleId == sale.id })
        },
        stockDeltas = backupDao.stockDeltas().map { stockDeltaPayload(it) },
        cashEntries = backupDao.cashEntries().map { cashEntryPayload(it) },
        drawerCounts = backupDao.drawerCounts().map { drawerCountPayload(it) },
        paymentTypes = backupDao.paymentTypes().map { paymentTypePayload(it) },
        settings = backupDao.settings().map { settingPayload(it) },
    )

    suspend fun exportJson(): String = encode(export())

    /** Parses and applies a backup; throws on malformed input. */
    suspend fun restore(jsonText: String): RestoreSummary {
        val doc = json.decodeFromString<BackupDocument>(jsonText)
        val shopId = identity.shopId()

        // Rebuild product stock cache from the delta ledger (the audit truth).
        val stockByProduct = doc.stockDeltas.groupBy { it.productId }
            .mapValues { (_, deltas) -> deltas.sumOf { it.delta } }

        db.withTransaction {
            backupDao.clearSaleLines()
            backupDao.clearSales()
            backupDao.clearStockDeltas()
            backupDao.clearCashEntries()
            backupDao.clearDrawerCounts()
            backupDao.clearPaymentTypes()
            backupDao.clearCategories()
            backupDao.clearProducts()
            backupDao.clearUsers()
            backupDao.clearSettings()
            backupDao.clearTenantConfig()

            tenantConfigDao.upsert(
                TenantConfig(
                    shopId = shopId,
                    name = doc.tenantConfig?.name.orEmpty(),
                    address = doc.tenantConfig?.address.orEmpty(),
                    phone = doc.tenantConfig?.phone.orEmpty(),
                    logo = doc.tenantConfig?.logo,
                    currency = doc.tenantConfig?.currency ?: "MYR",
                    language = doc.tenantConfig?.language ?: "en",
                    updatedAt = doc.tenantConfig?.updatedAt?.toEpochMillis() ?: System.currentTimeMillis(),
                ),
            )

            doc.users.forEach { u ->
                userDao.upsert(
                    User(
                        id = u.id,
                        shopId = shopId,
                        name = u.name,
                        role = u.role,
                        pinSalt = "",
                        pinHash = "",
                        active = u.active,
                        createdAt = u.createdAt.toEpochMillis(),
                        updatedAt = u.updatedAt.toEpochMillis(),
                    ),
                )
            }
            doc.categories.forEach { c ->
                categoryDao.upsert(Category(c.id, shopId, c.name, c.sortOrder, c.updatedAt.toEpochMillis()))
            }
            doc.products.forEach { p ->
                productDao.upsert(
                    Product(
                        id = p.id,
                        shopId = shopId,
                        categoryId = p.categoryId,
                        name = p.name,
                        barcode = p.barcode,
                        sku = p.sku,
                        costCents = p.costCents,
                        priceCents = p.priceCents,
                        taxRateBps = p.taxRateBps,
                        active = p.active,
                        lowStockThreshold = p.lowStockThreshold,
                        emoji = p.emoji,
                        color = p.color,
                        photo = p.photo,
                        location = p.location,
                        stockOnHand = stockByProduct[p.id] ?: 0L,
                        createdAt = p.createdAt.toEpochMillis(),
                        updatedAt = p.updatedAt.toEpochMillis(),
                    ),
                )
            }
            doc.sales.forEach { s ->
                saleDao.insert(
                    Sale(
                        id = s.id,
                        shopId = shopId,
                        localNo = s.localNo,
                        userId = s.userId,
                        at = s.at.toEpochMillis(),
                        totalCents = s.totalCents,
                        paymentTypeId = s.paymentTypeId,
                        tenderedCents = s.tenderedCents,
                        changeCents = s.changeCents,
                        voided = s.voided,
                        createdAt = s.createdAt.toEpochMillis(),
                        updatedAt = s.updatedAt.toEpochMillis(),
                    ),
                )
                saleDao.insertLines(
                    s.lines.map { line ->
                        SaleLine(
                            id = line.id,
                            shopId = shopId,
                            saleId = s.id,
                            productId = line.productId,
                            customName = line.customName,
                            qty = line.qty,
                            unitCents = line.unitCents,
                            lineCents = line.lineCents,
                            voidReason = line.voidReason,
                            createdAt = line.createdAt.toEpochMillis(),
                            updatedAt = line.updatedAt.toEpochMillis(),
                        )
                    },
                )
            }
            doc.stockDeltas.forEach { d ->
                stockDeltaDao.insert(
                    StockDelta(
                        id = d.id,
                        shopId = shopId,
                        productId = d.productId,
                        delta = d.delta,
                        reason = d.reason,
                        note = d.note,
                        refId = d.refId,
                        userId = d.userId,
                        at = d.at.toEpochMillis(),
                        createdAt = d.createdAt.toEpochMillis(),
                        updatedAt = d.updatedAt.toEpochMillis(),
                    ),
                )
            }
            doc.cashEntries.forEach { c ->
                cashEntryDao.insert(
                    CashEntry(c.id, shopId, c.direction, c.amountCents, c.reason, c.userId, c.at.toEpochMillis(), c.createdAt.toEpochMillis(), c.updatedAt.toEpochMillis()),
                )
            }
            doc.drawerCounts.forEach { d ->
                drawerCountDao.insert(
                    DrawerCount(d.id, shopId, d.at.toEpochMillis(), d.expectedCents, d.countedCents, d.varianceCents, d.userId, d.createdAt.toEpochMillis(), d.updatedAt.toEpochMillis()),
                )
            }
            doc.paymentTypes.forEach { p ->
                paymentTypeDao.upsert(PaymentType(p.id, shopId, p.name, p.kind, p.active, p.sort, p.updatedAt.toEpochMillis()))
            }
            doc.settings.forEach { s ->
                settingDao.upsert(Setting(s.key, s.value, s.updatedAt.toEpochMillis()))
            }
        }
        return RestoreSummary(
            products = doc.products.size,
            sales = doc.sales.size,
            users = doc.users.size,
        )
    }

    private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

    private fun String.toEpochMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }
        .getOrDefault(0L)
}
