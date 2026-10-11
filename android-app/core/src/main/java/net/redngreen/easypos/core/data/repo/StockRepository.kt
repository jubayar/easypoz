package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.ProductDao
import net.redngreen.easypos.core.data.dao.StockDeltaDao
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.productPayload
import net.redngreen.easypos.core.data.journal.stockDeltaPayload

/**
 * Stock reads and manual adjustments (tasks 8.1–8.4). Product.stockOnHand is
 * a cache over StockDelta rows (the audit truth, design D4); every manual
 * change appends one journaled ADJUST delta carrying a mandatory human note
 * and moves the cache in the same transaction.
 */
@Singleton
class StockRepository @Inject constructor(
    private val productDao: ProductDao,
    private val stockDeltaDao: StockDeltaDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    /** All products (active and inactive) — the stock overview includes both. */
    fun observeProducts(): Flow<List<Product>> = productDao.observeAll()

    fun observeDeltas(productId: String): Flow<List<StockDelta>> =
        stockDeltaDao.observeForProduct(productId)

    /** Today's gross movement (design: "Pergerakan Hari Ini +N Masuk / −M Keluar"). */
    fun observeMovementToday(): Pair<Flow<Long>, Flow<Long>> {
        val startOfDay =
            java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return stockDeltaDao.observeMovementInSince(startOfDay) to
            stockDeltaDao.observeMovementOutSince(startOfDay)
    }

    /**
     * Manual adjustment (task 8.4): a single ADJUST delta with a mandatory
     * human note. Positive delta = receive, negative = remove. Journaled so it
     * syncs and appears in the product's stock history.
     */
    suspend fun adjustStock(
        productId: String,
        delta: Long,
        note: String,
        actorUserId: String?,
    ) {
        if (delta == 0L) return
        val trimmed = note.trim()
        require(trimmed.isNotEmpty()) { "reason is required" }

        val now = System.currentTimeMillis()
        val deltaRow = StockDelta(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            productId = productId,
            delta = delta,
            reason = REASON_ADJUST,
            note = trimmed,
            refId = null,
            userId = actorUserId,
            at = now,
            createdAt = now,
            updatedAt = now,
        )
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.STOCK_DELTA,
            opType = JournalEntities.INSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(stockDeltaPayload(deltaRow)),
        ) {
            stockDeltaDao.insert(deltaRow)
            productDao.bumpStock(productId, delta)
        }
    }

    /**
     * Set-count adjustment: the delta is computed against the current cache so
     * history still records how much moved (spec stocktake scenario).
     */
    suspend fun setStockCount(
        product: Product,
        newCount: Long,
        note: String,
        actorUserId: String?,
    ) {
        adjustStock(product.id, newCount - product.stockOnHand, note, actorUserId)
    }

    /** Per-product low-stock threshold (task 8.1) — a journaled product edit. */
    suspend fun updateThreshold(product: Product, threshold: Int, actorUserId: String?) {
        if (threshold < 0 || threshold == product.lowStockThreshold) return
        val updated = product.copy(
            lowStockThreshold = threshold,
            updatedAt = System.currentTimeMillis(),
        )
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.PRODUCT,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(productPayload(updated)),
        ) {
            productDao.upsert(updated)
        }
    }

    companion object {
        const val REASON_ADJUST = "ADJUST"
    }
}
