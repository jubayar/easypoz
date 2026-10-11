package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.data.LocalIdentity
import net.redngreen.easypos.core.data.dao.HeldSaleDao
import net.redngreen.easypos.core.data.dao.PaymentTypeDao
import net.redngreen.easypos.core.data.dao.ProductDao
import net.redngreen.easypos.core.data.dao.SaleDao
import net.redngreen.easypos.core.data.dao.StockDeltaDao
import net.redngreen.easypos.core.data.entity.HeldSale
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.entity.Sale
import net.redngreen.easypos.core.data.entity.SaleLine
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.journal.JournalEntities
import net.redngreen.easypos.core.data.journal.JournalizedWriter
import net.redngreen.easypos.core.data.journal.paymentTypePayload
import net.redngreen.easypos.core.data.journal.salePayload
import net.redngreen.easypos.core.data.journal.stockDeltaPayload
import net.redngreen.easypos.core.money.MoneyFormatter

/**
 * Sale completion + hold/resume (tasks 7.5–7.9). The commit is ONE Room
 * transaction: sale + lines + stock deltas + stock-cache bumps + every
 * journal op — fully offline, never blocked by network or license state
 * (the sell path is sacred, design D5 + invariants).
 */
@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val stockDeltaDao: StockDeltaDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val heldSaleDao: HeldSaleDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    /** A cart line at payment time — product or quick-sell custom item. */
    data class CheckoutLine(
        val productId: String?,
        val name: String,
        val qty: Int,
        val unitCents: Long,
    ) {
        val lineCents: Long get() = MoneyFormatter.multiply(qty, unitCents)
    }

    class EmptyCart : Exception("cart is empty")

    /**
     * Commits a sale atomically: sequential local number, lines, SALE stock
     * deltas with cache bumps, and the journal ops — or nothing at all.
     * Resumed carts pass [resumedHeldId] so the parked row dies here too.
     */
    suspend fun completeSale(
        lines: List<CheckoutLine>,
        paymentTypeId: String?,
        tenderedCents: Long?,
        actorUserId: String?,
        resumedHeldId: String? = null,
    ): Sale {
        if (lines.isEmpty()) throw EmptyCart()
        val shopId = identity.shopId()
        val deviceId = identity.deviceId()
        val now = System.currentTimeMillis()
        val saleId = UUID.randomUUID().toString()
        val totalCents = lines.sumOf { it.lineCents }
        val changeCents = tenderedCents?.let { MoneyFormatter.change(it, totalCents) }

        val sale = Sale(
            id = saleId,
            shopId = shopId,
            localNo = 0L, // assigned inside the transaction below
            userId = actorUserId,
            at = now,
            totalCents = totalCents,
            paymentTypeId = paymentTypeId,
            tenderedCents = tenderedCents,
            changeCents = changeCents,
            voided = false,
            createdAt = now,
            updatedAt = now,
        )
        val saleLines = lines.map { line ->
            SaleLine(
                id = UUID.randomUUID().toString(),
                shopId = shopId,
                saleId = saleId,
                productId = line.productId,
                customName = if (line.productId == null) line.name else null,
                qty = line.qty,
                unitCents = line.unitCents,
                lineCents = line.lineCents,
                voidReason = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        // One SALE delta per product line (quick-sell lines touch no stock).
        val deltas = lines
            .filter { it.productId != null }
            .groupBy { it.productId!! }
            .map { (productId, productLines) ->
                StockDelta(
                    id = UUID.randomUUID().toString(),
                    shopId = shopId,
                    productId = productId,
                    delta = -productLines.sumOf { it.qty.toLong() },
                    reason = REASON_SALE,
                    refId = saleId,
                    userId = actorUserId,
                    at = now,
                    createdAt = now,
                    updatedAt = now,
                )
            }

        writer.journalizedBatch(
            shopId = shopId,
            deviceId = deviceId,
            actorUserId = actorUserId,
            specs = buildList {
                add(
                    JournalizedWriter.OpSpec(
                        entity = JournalEntities.SALE,
                        opType = JournalEntities.INSERT,
                        payloadJson = writer.encode(salePayload(sale, saleLines)),
                    )
                )
                deltas.forEach { delta ->
                    add(
                        JournalizedWriter.OpSpec(
                            entity = JournalEntities.STOCK_DELTA,
                            opType = JournalEntities.INSERT,
                            payloadJson = writer.encode(stockDeltaPayload(delta)),
                        )
                    )
                }
            },
        ) {
            val numbered = sale.copy(localNo = saleDao.nextLocalNo(shopId))
            val numberedLines = saleLines // lines carry saleId already
            saleDao.insert(numbered)
            saleDao.insertLines(numberedLines)
            deltas.forEach { delta ->
                stockDeltaDao.insert(delta)
                productDao.bumpStock(delta.productId, delta.delta)
            }
            if (resumedHeldId != null) heldSaleDao.delete(resumedHeldId)
            numbered
        }
        // journalizedBatch's block returns Unit by signature; re-read the sale.
        return saleDao.getById(saleId) ?: sale
    }

    // ─── Hold & resume (task 7.9) ────────────────────────────────────────────

    /** Parks the cart — no sale, no stock movement, no number consumed. */
    suspend fun holdCart(
        lines: List<CheckoutLine>,
        label: String?,
        actorUserId: String?,
    ): HeldSale {
        val json = writer.encode(lines.map { HeldLine(it.productId, it.name, it.qty, it.unitCents) })
        val held = HeldSale(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            userId = actorUserId,
            label = label?.trim()?.ifEmpty { null },
            linesJson = json,
            heldAt = System.currentTimeMillis(),
        )
        heldSaleDao.upsert(held) // local-only: never journaled (design D4)
        return held
    }

    fun resumeLines(held: HeldSale): List<CheckoutLine> =
        writer.json.decodeFromString<List<HeldLine>>(held.linesJson)
            .map { CheckoutLine(it.productId, it.name, it.qty, it.unitCents) }

    fun observeHeld(): Flow<List<HeldSale>> = heldSaleDao.observeAll()

    suspend fun discardHeld(id: String) = heldSaleDao.delete(id)

    @kotlinx.serialization.Serializable
    data class HeldLine(
        val productId: String?,
        val name: String,
        val qty: Int,
        val unitCents: Long,
    )

    // ─── Payment types (task 7.5) ────────────────────────────────────────────

    fun observePaymentTypes(): Flow<List<PaymentType>> = paymentTypeDao.observeActive()

    /** Shop-configurable list; seeded with the spec defaults on first run. */
    suspend fun ensureDefaultPaymentTypes(actorUserId: String?) {
        if (paymentTypeDao.getAll().isNotEmpty()) return
        val now = System.currentTimeMillis()
        DEFAULT_PAYMENT_TYPES.forEachIndexed { index, (name, kind) ->
            val paymentType = PaymentType(
                id = UUID.randomUUID().toString(),
                shopId = identity.shopId(),
                name = name,
                kind = kind,
                active = true,
                sort = index,
                updatedAt = now,
            )
            writer.journalized(
                shopId = identity.shopId(),
                deviceId = identity.deviceId(),
                entity = JournalEntities.PAYMENT_TYPE,
                opType = JournalEntities.UPSERT,
                actorUserId = actorUserId,
                payloadJson = writer.encode(paymentTypePayload(paymentType)),
            ) {
                paymentTypeDao.upsert(paymentType)
            }
        }
    }

    companion object {
        const val REASON_SALE = "SALE"
        private val DEFAULT_PAYMENT_TYPES = listOf(
            "Cash" to "CASH",
            "DuitNow QR" to "OTHER",
            "Touch 'n Go" to "OTHER",
            "Card" to "OTHER",
        )
    }
}
