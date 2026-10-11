package net.redngreen.easypos.feature.catalog.domain

import kotlinx.coroutines.flow.Flow

/**
 * Product & category read/write (tasks 6.1–6.5). Role enforcement
 * (EDIT_CATALOG) is checked by callers; the implementation guards data
 * integrity: barcode uniqueness, non-blank names, valid money — everything
 * journaled through the core writer.
 */
interface CatalogRepository {
    fun observeProducts(): Flow<List<Product>>

    fun observeCategories(): Flow<List<Category>>

    /** Barcode-exact lookup restricted to active products (sell scan path). */
    suspend fun findActiveByBarcode(barcode: String): Product?

    /** Prefix search over name/barcode/SKU (unknown-scan affordance). */
    suspend fun searchMatching(query: String, limit: Int): List<Product>

    suspend fun getProduct(id: String): Product?

    /** Creates a category (fails on duplicate name) or returns the existing one. */
    suspend fun ensureCategory(name: String, actorUserId: String?): Category

    suspend fun renameCategory(id: String, newName: String, actorUserId: String?)

    /** Reorder: assigns sortOrder by list position; journals changed rows. */
    suspend fun reorderCategories(orderedIds: List<String>, actorUserId: String?)

    /** Creates a product; initial stock (when given) is journaled as ADJUST. */
    suspend fun createProduct(
        fields: ProductFields,
        initialStock: Long?,
        actorUserId: String?,
    ): Product

    suspend fun updateProduct(
        id: String,
        fields: ProductFields,
        actorUserId: String?,
    )

    /** Soft-deactivate — historical sales stay intact (spec). */
    suspend fun deactivateProduct(id: String, actorUserId: String?)

    /** Applies parsed rows per docs update semantics; per-row outcome report. */
    suspend fun importRows(rows: List<CatalogCsvRow>, actorUserId: String?): ImportSummary

    /** Full catalog (inactive included) in the canonical import format. */
    suspend fun exportCsv(): String
}
