package net.redngreen.easypos.core.database.journal

import java.time.Instant
import kotlinx.serialization.Serializable
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.CashEntry
import net.redngreen.easypos.core.database.entity.DrawerCount
import net.redngreen.easypos.core.database.entity.PaymentType
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.database.entity.Sale
import net.redngreen.easypos.core.database.entity.SaleLine
import net.redngreen.easypos.core.database.entity.Setting
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.entity.TenantConfig
import net.redngreen.easypos.core.database.entity.User

/**
 * JournalOp payload DTOs — wire-compatible with the backend's Zod schemas
 * (src/sync/journal-op.schemas.ts). Timestamps travel as ISO 8601 strings;
 * money is integer cents; ids are UUID strings generated on-device.
 */

private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

@Serializable
data class UserPayload(
    val id: String,
    val name: String,
    val role: String,
    val pinSalt: String = "",
    val pinHash: String = "",
    val active: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
)

fun userPayload(user: User): UserPayload = UserPayload(
    id = user.id,
    name = user.name,
    role = user.role,
    pinSalt = user.pinSalt,
    pinHash = user.pinHash,
    active = user.active,
    createdAt = user.createdAt.toIso(),
    updatedAt = user.updatedAt.toIso(),
)

@Serializable
data class CategoryPayload(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: String,
)

fun categoryPayload(category: Category): CategoryPayload = CategoryPayload(
    id = category.id,
    name = category.name,
    sortOrder = category.sortOrder,
    updatedAt = category.updatedAt.toIso(),
)

@Serializable
data class ProductPayload(
    val id: String,
    val categoryId: String? = null,
    val name: String,
    val barcode: String? = null,
    val sku: String? = null,
    val costCents: Long = 0L,
    val priceCents: Long,
    val taxRateBps: Int? = null,
    val active: Boolean = true,
    val lowStockThreshold: Int = 5,
    val emoji: String? = null,
    val color: String? = null,
    val photo: String? = null,
    val location: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

fun productPayload(product: Product): ProductPayload = ProductPayload(
    id = product.id,
    categoryId = product.categoryId,
    name = product.name,
    barcode = product.barcode,
    sku = product.sku,
    costCents = product.costCents,
    priceCents = product.priceCents,
    taxRateBps = product.taxRateBps,
    active = product.active,
    lowStockThreshold = product.lowStockThreshold,
    emoji = product.emoji,
    color = product.color,
    photo = product.photo,
    location = product.location,
    createdAt = product.createdAt.toIso(),
    updatedAt = product.updatedAt.toIso(),
)

@Serializable
data class StockDeltaPayload(
    val id: String,
    val productId: String,
    val delta: Long,
    val reason: String, // SALE | ADJUST | IMPORT
    val note: String? = null,
    val refId: String? = null,
    val userId: String? = null,
    val at: String,
    val createdAt: String,
    val updatedAt: String,
)

fun stockDeltaPayload(delta: StockDelta): StockDeltaPayload = StockDeltaPayload(
    id = delta.id,
    productId = delta.productId,
    delta = delta.delta,
    reason = delta.reason,
    note = delta.note,
    refId = delta.refId,
    userId = delta.userId,
    at = delta.at.toIso(),
    createdAt = delta.createdAt.toIso(),
    updatedAt = delta.updatedAt.toIso(),
)

@Serializable
data class SaleLinePayload(
    val id: String,
    val productId: String? = null,
    val customName: String? = null,
    val qty: Int,
    val unitCents: Long,
    val lineCents: Long,
    val voidReason: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class SalePayload(
    val id: String,
    val localNo: Long,
    val userId: String? = null,
    val at: String,
    val totalCents: Long,
    val paymentTypeId: String? = null,
    val tenderedCents: Long? = null,
    val changeCents: Long? = null,
    val voided: Boolean = false,
    val lines: List<SaleLinePayload>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class PaymentTypePayload(
    val id: String,
    val name: String,
    val kind: String, // CASH | OTHER
    val active: Boolean = true,
    val sort: Int = 0,
    val updatedAt: String,
)

fun paymentTypePayload(paymentType: PaymentType): PaymentTypePayload = PaymentTypePayload(
    id = paymentType.id,
    name = paymentType.name,
    kind = paymentType.kind,
    active = paymentType.active,
    sort = paymentType.sort,
    updatedAt = paymentType.updatedAt.toIso(),
)

@Serializable
data class CashEntryPayload(
    val id: String,
    val direction: String, // IN | OUT
    val amountCents: Long,
    val reason: String,
    val userId: String? = null,
    val at: String,
    val createdAt: String,
    val updatedAt: String,
)

fun cashEntryPayload(entry: CashEntry): CashEntryPayload = CashEntryPayload(
    id = entry.id,
    direction = entry.direction,
    amountCents = entry.amountCents,
    reason = entry.reason,
    userId = entry.userId,
    at = entry.at.toIso(),
    createdAt = entry.createdAt.toIso(),
    updatedAt = entry.updatedAt.toIso(),
)

@Serializable
data class DrawerCountPayload(
    val id: String,
    val at: String,
    val expectedCents: Long,
    val countedCents: Long,
    val varianceCents: Long,
    val userId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

fun drawerCountPayload(count: DrawerCount): DrawerCountPayload = DrawerCountPayload(
    id = count.id,
    at = count.at.toIso(),
    expectedCents = count.expectedCents,
    countedCents = count.countedCents,
    varianceCents = count.varianceCents,
    userId = count.userId,
    createdAt = count.createdAt.toIso(),
    updatedAt = count.updatedAt.toIso(),
)

@Serializable
data class SettingPayload(
    val key: String,
    val value: String,
    val updatedAt: String,
)

fun settingPayload(setting: Setting): SettingPayload = SettingPayload(
    key = setting.key,
    value = setting.value,
    updatedAt = setting.updatedAt.toIso(),
)

@Serializable
data class TenantConfigPayload(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val logo: String? = null,
    val currency: String = "MYR",
    val language: String = "en",
    val updatedAt: String,
)

fun tenantConfigPayload(config: TenantConfig): TenantConfigPayload = TenantConfigPayload(
    name = config.name,
    address = config.address,
    phone = config.phone,
    logo = config.logo,
    currency = config.currency,
    language = config.language,
    updatedAt = config.updatedAt.toIso(),
)

fun salePayload(sale: Sale, lines: List<SaleLine>): SalePayload = SalePayload(    id = sale.id,
    localNo = sale.localNo,
    userId = sale.userId,
    at = sale.at.toIso(),
    totalCents = sale.totalCents,
    paymentTypeId = sale.paymentTypeId,
    tenderedCents = sale.tenderedCents,
    changeCents = sale.changeCents,
    voided = sale.voided,
    lines = lines.map { line ->
        SaleLinePayload(
            id = line.id,
            productId = line.productId,
            customName = line.customName,
            qty = line.qty,
            unitCents = line.unitCents,
            lineCents = line.lineCents,
            voidReason = line.voidReason,
            createdAt = line.createdAt.toIso(),
            updatedAt = line.updatedAt.toIso(),
        )
    },
    createdAt = sale.createdAt.toIso(),
    updatedAt = sale.updatedAt.toIso(),
)
