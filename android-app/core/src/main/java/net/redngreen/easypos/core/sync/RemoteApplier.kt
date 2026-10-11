package net.redngreen.easypos.core.sync

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.entity.CashEntry
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.ConflictLog
import net.redngreen.easypos.core.database.entity.DrawerCount
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.database.entity.Sale
import net.redngreen.easypos.core.database.entity.SaleLine
import net.redngreen.easypos.core.database.entity.Setting
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.entity.TenantConfig
import net.redngreen.easypos.core.database.entity.User
import net.redngreen.easypos.core.database.journal.CashEntryPayload
import net.redngreen.easypos.core.database.journal.CategoryPayload
import net.redngreen.easypos.core.database.journal.DrawerCountPayload
import net.redngreen.easypos.core.database.journal.PaymentTypePayload
import net.redngreen.easypos.core.database.journal.ProductPayload
import net.redngreen.easypos.core.database.journal.SalePayload
import net.redngreen.easypos.core.database.journal.SettingPayload
import net.redngreen.easypos.core.database.journal.StockDeltaPayload
import net.redngreen.easypos.core.database.journal.TenantConfigPayload
import net.redngreen.easypos.core.database.journal.UserPayload

/** Outcome of comparing a pulled op's timestamp with the local row. */
enum class LwwDecision { NO_LOCAL, REMOTE_WINS, LOCAL_WINS }

/**
 * Applies one pulled op to local storage (design D5). Snapshot entities
 * (product/category/user/payment type/settings/tenant config) resolve by
 * last-write-wins on `updated_at`; ledger rows (sale/stock delta/cash entry/
 * drawer count) are insert-only and immutable. Every genuine conflict is
 * logged for review in settings; a dropped remote write never goes silent.
 *
 * Self-echo ops (ones this device pushed and that come back on pull) are
 * filtered upstream in the engine and never reach here.
 */
@Singleton
class RemoteApplier @Inject constructor(
    private val store: LocalSyncStore,
    private val identity: LocalIdentity,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun apply(op: PullOpWire) {
        when (op.entity) {
            ENTITY_PRODUCT -> applySnapshot(
                entity = op.entity,
                entityId = idOf(op),
                remoteUpdatedAt = isoMillis(op.payloadJsonValue("updatedAt")),
            ) {
                val payload = decode(ProductPayload.serializer(), op)
                store.putProduct(
                    Product(
                        id = payload.id,
                        shopId = identity.shopId(),
                        categoryId = payload.categoryId,
                        name = payload.name,
                        barcode = payload.barcode,
                        sku = payload.sku,
                        costCents = payload.costCents,
                        priceCents = payload.priceCents,
                        taxRateBps = payload.taxRateBps,
                        active = payload.active,
                        lowStockThreshold = payload.lowStockThreshold,
                        emoji = payload.emoji,
                        color = payload.color,
                        photo = payload.photo,
                        location = payload.location,
                        stockOnHand = 0L, // recomputed/kept by the store
                        createdAt = isoMillis(payload.createdAt),
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_CATEGORY -> applySnapshot(
                entity = op.entity,
                entityId = idOf(op),
                remoteUpdatedAt = isoMillis(op.payloadJsonValue("updatedAt")),
            ) {
                val payload = decode(CategoryPayload.serializer(), op)
                store.putCategory(
                    Category(
                        id = payload.id,
                        shopId = identity.shopId(),
                        name = payload.name,
                        sortOrder = payload.sortOrder,
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_USER -> applySnapshot(
                entity = op.entity,
                entityId = idOf(op),
                remoteUpdatedAt = isoMillis(op.payloadJsonValue("updatedAt")),
            ) {
                val payload = decode(UserPayload.serializer(), op)
                store.putUser(
                    User(
                        id = payload.id,
                        shopId = identity.shopId(),
                        name = payload.name,
                        role = payload.role,
                        pinSalt = payload.pinSalt,
                        pinHash = payload.pinHash,
                        active = payload.active,
                        createdAt = isoMillis(payload.createdAt),
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_PAYMENT_TYPE -> applySnapshot(
                entity = op.entity,
                entityId = idOf(op),
                remoteUpdatedAt = isoMillis(op.payloadJsonValue("updatedAt")),
            ) {
                val payload = decode(PaymentTypePayload.serializer(), op)
                store.putPaymentType(
                    PaymentType(
                        id = payload.id,
                        shopId = identity.shopId(),
                        name = payload.name,
                        kind = payload.kind,
                        active = payload.active,
                        sort = payload.sort,
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_TENANT_CONFIG -> applySnapshot(
                entity = op.entity,
                entityId = identity.shopId(),
                remoteUpdatedAt = isoMillis(op.payloadJsonValue("updatedAt")),
            ) {
                val payload = decode(TenantConfigPayload.serializer(), op)
                store.putTenantConfig(
                    TenantConfig(
                        shopId = identity.shopId(),
                        name = payload.name,
                        address = payload.address,
                        phone = payload.phone,
                        logo = payload.logo,
                        currency = payload.currency,
                        language = payload.language,
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_SETTING -> {
                val payload = decode(SettingPayload.serializer(), op)
                applySnapshot(
                    entity = op.entity,
                    entityId = payload.key,
                    remoteUpdatedAt = isoMillis(payload.updatedAt),
                ) {
                    store.putSetting(
                        Setting(
                            key = payload.key,
                            value = payload.value,
                            updatedAt = isoMillis(payload.updatedAt),
                        )
                    )
                }
            }

            ENTITY_SALE -> when (op.opType) {
                OPTYPE_VOID -> store.voidSale(idOf(op), isoMillis(op.payloadJsonValue("updatedAt")))
                else -> {
                    val payload = decode(SalePayload.serializer(), op)
                    val shopId = identity.shopId()
                    store.putSale(
                        Sale(
                            id = payload.id,
                            shopId = shopId,
                            localNo = payload.localNo,
                            userId = payload.userId,
                            at = isoMillis(payload.at),
                            totalCents = payload.totalCents,
                            paymentTypeId = payload.paymentTypeId,
                            tenderedCents = payload.tenderedCents,
                            changeCents = payload.changeCents,
                            voided = payload.voided,
                            createdAt = isoMillis(payload.createdAt),
                            updatedAt = isoMillis(payload.updatedAt),
                        ),
                        payload.lines.map { line ->
                            SaleLine(
                                id = line.id,
                                shopId = shopId,
                                saleId = payload.id,
                                productId = line.productId,
                                customName = line.customName,
                                qty = line.qty,
                                unitCents = line.unitCents,
                                lineCents = line.lineCents,
                                voidReason = line.voidReason,
                                createdAt = isoMillis(line.createdAt),
                                updatedAt = isoMillis(line.updatedAt),
                            )
                        },
                    )
                }
            }

            ENTITY_STOCK_DELTA -> {
                val payload = decode(StockDeltaPayload.serializer(), op)
                store.putStockDelta(
                    StockDelta(
                        id = payload.id,
                        shopId = identity.shopId(),
                        productId = payload.productId,
                        delta = payload.delta,
                        reason = payload.reason,
                        note = payload.note,
                        refId = payload.refId,
                        userId = payload.userId,
                        at = isoMillis(payload.at),
                        createdAt = isoMillis(payload.createdAt),
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_CASH_ENTRY -> {
                val payload = decode(CashEntryPayload.serializer(), op)
                store.putCashEntry(
                    CashEntry(
                        id = payload.id,
                        shopId = identity.shopId(),
                        direction = payload.direction,
                        amountCents = payload.amountCents,
                        reason = payload.reason,
                        userId = payload.userId,
                        at = isoMillis(payload.at),
                        createdAt = isoMillis(payload.createdAt),
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }

            ENTITY_DRAWER_COUNT -> {
                val payload = decode(DrawerCountPayload.serializer(), op)
                store.putDrawerCount(
                    DrawerCount(
                        id = payload.id,
                        shopId = identity.shopId(),
                        at = isoMillis(payload.at),
                        expectedCents = payload.expectedCents,
                        countedCents = payload.countedCents,
                        varianceCents = payload.varianceCents,
                        userId = payload.userId,
                        createdAt = isoMillis(payload.createdAt),
                        updatedAt = isoMillis(payload.updatedAt),
                    )
                )
            }
            // Unknown entity: ignored rather than fatal so a newer backend
            // entity can't wedge sync on an older client.
        }
    }

    /** LWW wrapper for snapshot entities: apply or drop, always log a conflict. */
    private suspend fun applySnapshot(
        entity: String,
        entityId: String,
        remoteUpdatedAt: Long,
        apply: suspend () -> Unit,
    ) {
        val localUpdatedAt = store.currentUpdatedAt(entity, entityId)
        when (decide(localUpdatedAt, remoteUpdatedAt)) {
            LwwDecision.NO_LOCAL -> apply()
            LwwDecision.REMOTE_WINS -> {
                store.logConflict(conflict(entity, entityId, localUpdatedAt!!, remoteUpdatedAt, RESOLUTION_REMOTE))
                apply()
            }
            LwwDecision.LOCAL_WINS -> {
                store.logConflict(conflict(entity, entityId, localUpdatedAt!!, remoteUpdatedAt, RESOLUTION_LOCAL))
            }
        }
    }

    private fun conflict(
        entity: String,
        entityId: String,
        local: Long,
        remote: Long,
        resolution: String,
    ) = ConflictLog(
        entity = entity,
        entityId = entityId,
        localUpdatedAt = local,
        remoteUpdatedAt = remote,
        resolution = resolution,
        at = System.currentTimeMillis(),
    )

    private fun idOf(op: PullOpWire): String =
        op.entityId ?: op.payloadJsonValue("id")

    private fun <T> decode(
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        op: PullOpWire,
    ): T = json.decodeFromJsonElement(serializer, op.payload)

    private fun PullOpWire.payloadJsonValue(field: String): String =
        (payload as? JsonObject)?.get(field)?.jsonPrimitive?.content ?: ""

    companion object {
        const val ENTITY_PRODUCT = "product"
        const val ENTITY_CATEGORY = "category"
        const val ENTITY_USER = "user"
        const val ENTITY_PAYMENT_TYPE = "payment_type"
        const val ENTITY_TENANT_CONFIG = "tenant_config"
        const val ENTITY_SETTING = "setting"
        const val ENTITY_SALE = "sale"
        const val ENTITY_STOCK_DELTA = "stock_delta"
        const val ENTITY_CASH_ENTRY = "cash_entry"
        const val ENTITY_DRAWER_COUNT = "drawer_count"
        const val OPTYPE_VOID = "VOID"
        const val RESOLUTION_LOCAL = "LOCAL_WINS"
        const val RESOLUTION_REMOTE = "REMOTE_WINS"

        /** Pure LWW decision — extracted so it can be unit-tested directly. */
        fun decide(localUpdatedAt: Long?, remoteUpdatedAt: Long): LwwDecision = when {
            localUpdatedAt == null -> LwwDecision.NO_LOCAL
            remoteUpdatedAt > localUpdatedAt -> LwwDecision.REMOTE_WINS
            else -> LwwDecision.LOCAL_WINS
        }
    }
}

/** ISO 8601 → epoch millis; 0 on anything unparseable (never blocks a pull). */
internal fun isoMillis(value: String): Long =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
