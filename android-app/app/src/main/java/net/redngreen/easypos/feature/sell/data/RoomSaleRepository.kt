package net.redngreen.easypos.feature.sell.data

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.HeldSaleDao
import net.redngreen.easypos.core.database.dao.PaymentTypeDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.SaleDao
import net.redngreen.easypos.core.database.dao.StockDeltaDao
import net.redngreen.easypos.core.database.entity.HeldSale
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.database.entity.Sale
import net.redngreen.easypos.core.database.entity.SaleLine
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.journal.JournalEntities
import net.redngreen.easypos.core.database.journal.JournalizedWriter
import net.redngreen.easypos.core.database.journal.paymentTypePayload
import net.redngreen.easypos.core.database.journal.salePayload
import net.redngreen.easypos.core.database.journal.stockDeltaPayload
import net.redngreen.easypos.core.money.MoneyFormatter
import kotlinx.serialization.json.Json
import net.redngreen.easypos.feature.sell.domain.CheckoutLine
import net.redngreen.easypos.feature.sell.domain.CompletedSale
import net.redngreen.easypos.feature.sell.domain.HeldCart
import net.redngreen.easypos.feature.sell.domain.PaymentOption
import net.redngreen.easypos.feature.sell.domain.SaleRepository

/**
 * Sale completion + hold/resume over Room (tasks 7.5–7.9). Room entities
 * stay inside this class; the domain sees mapped models only (design D5).
 */
class RoomSaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val stockDeltaDao: StockDeltaDao,
    private val paymentTypeDao: PaymentTypeDao,
    private val heldSaleDao: HeldSaleDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) : SaleRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun completeSale(
        lines: List<CheckoutLine>,
        paymentTypeId: String?,
        tenderedCents: Long?,
        actorUserId: String?,
        resumedHeldId: String?,
    ): CompletedSale {
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
            saleDao.insert(numbered)
            saleDao.insertLines(saleLines)
            deltas.forEach { delta ->
                stockDeltaDao.insert(delta)
                productDao.bumpStock(delta.productId, delta.delta)
            }
            if (resumedHeldId != null) heldSaleDao.delete(resumedHeldId)
            numbered
        }
        // journalizedBatch's block returns Unit by signature; re-read the sale.
        return (saleDao.getById(saleId) ?: sale).toDomain()
    }

    // ─── Hold & resume (task 7.9) ────────────────────────────────────────────

    override suspend fun holdCart(
        lines: List<CheckoutLine>,
        label: String?,
        actorUserId: String?,
    ): HeldCart {
        val jsonText = json.encodeToString(lines.map { HeldLine(it.productId, it.name, it.qty, it.unitCents) })
        val held = HeldSale(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            userId = actorUserId,
            label = label?.trim()?.ifEmpty { null },
            linesJson = jsonText,
            heldAt = System.currentTimeMillis(),
        )
        heldSaleDao.upsert(held) // local-only: never journaled (design D4)
        return held.toDomain()
    }

    override fun resumeLines(held: HeldCart): List<CheckoutLine> = held.lines

    override fun observeHeld(): Flow<List<HeldCart>> =
        heldSaleDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun discardHeld(id: String) = heldSaleDao.delete(id)

    // ─── Payment types (task 7.5) ────────────────────────────────────────────

    override fun observePaymentTypes(): Flow<List<PaymentOption>> =
        paymentTypeDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureDefaultPaymentTypes(actorUserId: String?) {
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

    // ─── Mappers (entity ↔ domain, design D5) ────────────────────────────────

    private fun Sale.toDomain() = CompletedSale(
        id = id,
        shopId = shopId,
        localNo = localNo,
        userId = userId,
        at = at,
        totalCents = totalCents,
        paymentTypeId = paymentTypeId,
        tenderedCents = tenderedCents,
        changeCents = changeCents,
        voided = voided,
    )

    private fun HeldSale.toDomain() = HeldCart(
        id = id,
        userId = userId,
        label = label,
        lines = json.decodeFromString<List<HeldLine>>(linesJson)
            .map { CheckoutLine(it.productId, it.name, it.qty, it.unitCents) },
        heldAt = heldAt,
    )

    private fun PaymentType.toDomain() = PaymentOption(id = id, name = name, kind = kind)

    @kotlinx.serialization.Serializable
    data class HeldLine(
        val productId: String?,
        val name: String,
        val qty: Int,
        val unitCents: Long,
    )

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

class EmptyCart : Exception("cart is empty")
