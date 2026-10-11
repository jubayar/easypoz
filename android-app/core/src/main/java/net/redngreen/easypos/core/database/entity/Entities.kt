package net.redngreen.easypos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities per design D4. Invariants:
 * - IDs are UUID strings generated on-device; shop_id travels on every synced
 *   row (single-writer sync payload shape).
 * - Money is Long cents (sen) everywhere — never Double (design D3).
 * - Product.stockOnHand is a transactionally-maintained cache over
 *   StockDelta rows (the audit truth); kept denormalized so the sell grid
 *   never aggregates the delta table on the hot path.
 */

@Entity(tableName = "tenant_config")
data class TenantConfig(
    @PrimaryKey val shopId: String,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val logo: String? = null,
    val currency: String = "MYR",
    val language: String = "en",
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "users",
    indices = [Index("shopId")],
)
data class User(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val role: String, // ADMIN | MANAGER | CASHIER
    val pinSalt: String = "",
    val pinHash: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "categories", indices = [Index("shopId")])
data class Category(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "products",
    indices = [
        Index("shopId"),
        Index(value = ["shopId", "barcode"]),
        Index("categoryId"),
        Index(value = ["name"]),
    ],
)
data class Product(
    @PrimaryKey val id: String,
    val shopId: String,
    val categoryId: String? = null,
    val name: String,
    val barcode: String? = null,
    val sku: String? = null,
    val costCents: Long = 0L,
    val priceCents: Long = 0L,
    val taxRateBps: Int? = null,
    val active: Boolean = true,
    val lowStockThreshold: Int = 5,
    val emoji: String? = null,
    val color: String? = null,
    val photo: String? = null,
    val location: String? = null,
    /** Cache of SUM(stock_deltas.delta); mutated only inside the write transaction. */
    val stockOnHand: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "sales",
    indices = [Index(value = ["shopId", "localNo"], unique = true), Index("shopId", "at")],
)
data class Sale(
    @PrimaryKey val id: String,
    val shopId: String,
    val localNo: Long,
    val userId: String? = null,
    val at: Long,
    val totalCents: Long,
    val paymentTypeId: String? = null,
    val tenderedCents: Long? = null,
    val changeCents: Long? = null,
    val voided: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "sale_lines", indices = [Index("saleId"), Index("productId")])
data class SaleLine(
    @PrimaryKey val id: String,
    val shopId: String,
    val saleId: String,
    val productId: String? = null,
    val customName: String? = null,
    val qty: Int,
    val unitCents: Long,
    val lineCents: Long,
    val voidReason: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Parked cart — deliberately local-only, never synced (design D4). */
@Entity(tableName = "held_sales", indices = [Index("shopId")])
data class HeldSale(
    @PrimaryKey val id: String,
    val shopId: String,
    val userId: String? = null,
    val label: String? = null,
    val linesJson: String,
    val heldAt: Long,
)

@Entity(tableName = "stock_deltas", indices = [Index("productId"), Index("shopId", "productId")])
data class StockDelta(
    @PrimaryKey val id: String,
    val shopId: String,
    val productId: String,
    val delta: Long,
    val reason: String, // SALE | ADJUST | IMPORT
    /** Human reason for a manual adjustment ("Kiraan Fizikal", "Rosak", free text); null for SALE/IMPORT. */
    val note: String? = null,
    val refId: String? = null,
    val userId: String? = null,
    val at: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "cash_entries", indices = [Index("shopId", "at")])
data class CashEntry(
    @PrimaryKey val id: String,
    val shopId: String,
    val direction: String, // IN | OUT
    val amountCents: Long,
    val reason: String,
    val userId: String? = null,
    val at: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "drawer_counts", indices = [Index("shopId", "at")])
data class DrawerCount(
    @PrimaryKey val id: String,
    val shopId: String,
    val at: Long,
    val expectedCents: Long,
    val countedCents: Long,
    val varianceCents: Long,
    val userId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "payment_types", indices = [Index("shopId")])
data class PaymentType(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val kind: String, // CASH | OTHER
    val active: Boolean = true,
    val sort: Int = 0,
    val updatedAt: Long = 0L,
)

/** Shop-level synced settings (receipt footer, drawer kick, idle lock, …). */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = 0L,
)
