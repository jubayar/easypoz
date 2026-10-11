package net.redngreen.easypos.feature.catalog.domain

/**
 * Catalog domain models — pure Kotlin mirrors of the storage entities
 * (restructure design D5: features never expose Room entities upward).
 */
data class Product(
    val id: String,
    val shopId: String,
    val categoryId: String?,
    val name: String,
    val barcode: String?,
    val sku: String?,
    val costCents: Long,
    val priceCents: Long,
    val taxRateBps: Int? = null,
    val active: Boolean = true,
    val lowStockThreshold: Int = 5,
    val emoji: String? = null,
    val color: String? = null,
    val photo: String? = null,
    val location: String? = null,
    val stockOnHand: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Category(
    val id: String,
    val shopId: String,
    val name: String,
    val sortOrder: Int,
    val updatedAt: Long = 0L,
)

/** Write model for create/update (core ProductFields). */
data class ProductFields(
    val name: String,
    val categoryId: String?,
    val barcode: String?,
    val sku: String?,
    val costCents: Long,
    val priceCents: Long,
    val taxRateBps: Int? = null,
    val active: Boolean = true,
    val lowStockThreshold: Int = 5,
    val emoji: String? = null,
    val color: String? = null,
    val photo: String? = null,
    val location: String? = null,
)

/** Data-integrity failures guarded by the repository (barcode uniqueness etc.). */
sealed class CatalogError(message: String) : Exception(message) {
    class InvalidName : CatalogError("name must not be blank")
    class InvalidPrice : CatalogError("sale price must be a positive amount")
    class BarcodeTaken(val otherName: String) :
        CatalogError("barcode already used by $otherName")
}

enum class ImportStatus { CREATED, UPDATED, FAILED }

data class ImportOutcome(
    val rowNumber: Int,
    val productName: String,
    val status: ImportStatus,
    val reason: String? = null,
)

data class ImportSummary(
    val created: Int,
    val updated: Int,
    val failed: Int,
    val outcomes: List<ImportOutcome>,
)

/** One successfully parsed CSV row, ready to apply (task 6.4). */
data class CatalogCsvRow(
    val rowNumber: Int,
    val name: String,
    val barcode: String?,
    val sku: String?,
    val category: String,
    val costCents: Long,
    val priceCents: Long,
    val active: Boolean,
    /** null = leave stock unchanged (docs: blank → unchanged). */
    val stock: Long?,
    val location: String?,
)
