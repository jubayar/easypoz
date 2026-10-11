package net.redngreen.easypos.core.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.CategoryDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.StockDeltaDao
import net.redngreen.easypos.core.database.entity.Category
import net.redngreen.easypos.core.database.entity.Product
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.journal.JournalEntities
import net.redngreen.easypos.core.database.journal.JournalizedWriter
import net.redngreen.easypos.core.database.journal.categoryPayload
import net.redngreen.easypos.core.database.journal.productPayload
import net.redngreen.easypos.core.database.journal.stockDeltaPayload
import net.redngreen.easypos.core.csv.CsvCatalogParser
import net.redngreen.easypos.core.money.MoneyFormatter

/**
 * Product & category mutations (tasks 6.1–6.5). Everything synced goes
 * through JournalizedWriter — one transaction per logical change, compound
 * changes (product + stock delta) share one transaction. Role enforcement
 * (EDIT_CATALOG) is checked by callers via SessionManager; this repo guards
 * data integrity: barcode uniqueness, non-blank names, valid money.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val stockDeltaDao: StockDeltaDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) {
    // ─── Observers ───────────────────────────────────────────────────────────

    fun observeProducts(): Flow<List<Product>> = productDao.observeAll()

    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeProductsByCategory(categoryId: String?): Flow<List<Product>> =
        productDao.observeActiveByCategory(categoryId)

    // ─── Errors ──────────────────────────────────────────────────────────────

    sealed class CatalogError(message: String) : Exception(message) {
        class InvalidName : CatalogError("name must not be blank")
        class InvalidPrice : CatalogError("sale price must be a positive amount")
        class BarcodeTaken(val otherName: String) :
            CatalogError("barcode already used by $otherName")
    }

    // ─── Categories (task 6.1) ───────────────────────────────────────────────

    /** Creates a category (fails on duplicate name) or returns the existing one. */
    suspend fun ensureCategory(name: String, actorUserId: String?): Category {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        categoryDao.getByName(trimmed)?.let { return it }
        val now = System.currentTimeMillis()
        val category = Category(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            name = trimmed,
            sortOrder = nextSortOrder(),
            updatedAt = now,
        )
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.CATEGORY,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(
                categoryPayload(category)
            ),
        ) {
            categoryDao.upsert(category)
        }
        return category
    }

    suspend fun renameCategory(id: String, newName: String, actorUserId: String?) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        val existing = categoryDao.getById(id) ?: return
        // Merge guard: a rename onto an existing name would fork the tabs.
        categoryDao.getByName(trimmed)?.let { clash ->
            if (clash.id != id) throw CatalogError.InvalidName()
        }
        val updated = existing.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.CATEGORY,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(
                categoryPayload(updated)
            ),
        ) {
            categoryDao.upsert(updated)
        }
    }

    /** Reorder: assigns sortOrder by list position; journals changed rows. */
    suspend fun reorderCategories(orderedIds: List<String>, actorUserId: String?) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            val category = categoryDao.getById(id) ?: return@forEachIndexed
            if (category.sortOrder != index) {
                val updated = category.copy(sortOrder = index, updatedAt = now)
                writer.journalized(
                    shopId = identity.shopId(),
                    deviceId = identity.deviceId(),
                    entity = JournalEntities.CATEGORY,
                    opType = JournalEntities.UPSERT,
                    actorUserId = actorUserId,
                    payloadJson = writer.encode(
                        categoryPayload(updated)
                    ),
                ) {
                    categoryDao.upsert(updated)
                }
            }
        }
    }

    private suspend fun nextSortOrder(): Int =
        (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1

    // ─── Products (task 6.2) ─────────────────────────────────────────────────

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

    /** Creates a product; initial stock (when given) is journaled as ADJUST. */
    suspend fun createProduct(
        fields: ProductFields,
        initialStock: Long?,
        actorUserId: String?,
    ): Product {
        val trimmed = fields.name.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        if (fields.priceCents < 0) throw CatalogError.InvalidPrice()
        validateBarcodeFree(fields.barcode, exceptId = null)

        val now = System.currentTimeMillis()
        val product = Product(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            categoryId = fields.categoryId,
            name = trimmed,
            barcode = fields.barcode?.trim()?.ifEmpty { null },
            sku = fields.sku?.trim()?.ifEmpty { null },
            costCents = fields.costCents,
            priceCents = fields.priceCents,
            taxRateBps = fields.taxRateBps,
            active = fields.active,
            lowStockThreshold = fields.lowStockThreshold,
            emoji = fields.emoji?.ifEmpty { null },
            color = fields.color?.ifEmpty { null },
            photo = fields.photo,
            location = fields.location?.trim()?.ifEmpty { null },
            stockOnHand = initialStock ?: 0L,
            createdAt = now,
            updatedAt = now,
        )
        writeProductWithOptionalStock(product, initialStock, previousStock = 0L, actorUserId)
        return product
    }

    suspend fun updateProduct(
        id: String,
        fields: ProductFields,
        actorUserId: String?,
    ) {
        val existing = productDao.getById(id) ?: throw CatalogError.InvalidName()
        val trimmed = fields.name.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        if (fields.priceCents < 0) throw CatalogError.InvalidPrice()
        validateBarcodeFree(fields.barcode, exceptId = id)

        val updated = existing.copy(
            categoryId = fields.categoryId,
            name = trimmed,
            barcode = fields.barcode?.trim()?.ifEmpty { null },
            sku = fields.sku?.trim()?.ifEmpty { null },
            costCents = fields.costCents,
            priceCents = fields.priceCents,
            taxRateBps = fields.taxRateBps,
            active = fields.active,
            lowStockThreshold = fields.lowStockThreshold,
            emoji = fields.emoji?.ifEmpty { null },
            color = fields.color?.ifEmpty { null },
            photo = fields.photo,
            location = fields.location?.trim()?.ifEmpty { null },
            updatedAt = System.currentTimeMillis(),
        )
        writeProductWithOptionalStock(updated, initialStock = null, previousStock = existing.stockOnHand, actorUserId)
    }

    /** Soft-deactivate — historical sales stay intact (spec). */
    suspend fun deactivateProduct(id: String, actorUserId: String?) {
        val existing = productDao.getById(id) ?: return
        if (!existing.active) return
        val updated = existing.copy(active = false, updatedAt = System.currentTimeMillis())
        writeJournalizedProduct(updated, actorUserId)
    }

    private suspend fun validateBarcodeFree(barcode: String?, exceptId: String?) {
        val trimmed = barcode?.trim()
        if (trimmed.isNullOrEmpty()) return
        productDao.getByBarcode(trimmed)?.let { clash ->
            if (clash.id != exceptId) throw CatalogError.BarcodeTaken(clash.name)
        }
    }

    private suspend fun writeProductWithOptionalStock(
        product: Product,
        initialStock: Long?,
        previousStock: Long,
        actorUserId: String?,
    ) {
        val shopId = identity.shopId()
        val deviceId = identity.deviceId()
        val specs = mutableListOf(
            JournalizedWriter.OpSpec(
                entity = JournalEntities.PRODUCT,
                opType = JournalEntities.UPSERT,
                payloadJson = writer.encode(productPayload(product)),
            )
        )
        if (initialStock != null && initialStock != previousStock) {
            val delta = StockDelta(
                id = UUID.randomUUID().toString(),
                shopId = shopId,
                productId = product.id,
                delta = initialStock - previousStock,
                reason = REASON_ADJUST,
                refId = null,
                userId = actorUserId,
                at = System.currentTimeMillis(),
                createdAt = product.updatedAt,
                updatedAt = product.updatedAt,
            )
            specs += JournalizedWriter.OpSpec(
                entity = JournalEntities.STOCK_DELTA,
                opType = JournalEntities.INSERT,
                payloadJson = writer.encode(stockDeltaPayload(delta)),
            )
            writer.journalizedBatch(shopId, deviceId, actorUserId, specs) {
                productDao.upsert(product.copy(stockOnHand = initialStock))
                stockDeltaDao.insert(delta)
            }
        } else {
            writeJournalizedProduct(product, actorUserId)
        }
    }

    private suspend fun writeJournalizedProduct(product: Product, actorUserId: String?) {
        writer.journalized(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            entity = JournalEntities.PRODUCT,
            opType = JournalEntities.UPSERT,
            actorUserId = actorUserId,
            payloadJson = writer.encode(productPayload(product)),
        ) {
            productDao.upsert(product)
        }
    }

    // ─── CSV import (task 6.4) ───────────────────────────────────────────────

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

    /**
     * Applies parsed rows per docs update semantics: barcode match, else
     * blank-barcode + exact name&category match, else create. Categories are
     * auto-created; stock is set via an ADJUST delta; duplicate barcodes
     * (in-file or in-DB) fail their row naming the conflict.
     */
    suspend fun importRows(rows: List<CsvCatalogParser.Row>, actorUserId: String?): ImportSummary {
        val outcomes = mutableListOf<ImportOutcome>()
        val seenBarcodes = mutableMapOf<String, Pair<Int, String>>() // barcode → (row, name)
        val categoryCache = mutableMapOf<String, Category>()

        for (row in rows) {
            val outcome = importRow(row, actorUserId, seenBarcodes, categoryCache)
            if (outcome != null) outcomes += outcome
        }
        return ImportSummary(
            created = outcomes.count { it.status == ImportStatus.CREATED },
            updated = outcomes.count { it.status == ImportStatus.UPDATED },
            failed = outcomes.count { it.status == ImportStatus.FAILED },
            outcomes = outcomes,
        )
    }

    private suspend fun importRow(
        row: CsvCatalogParser.Row,
        actorUserId: String?,
        seenBarcodes: MutableMap<String, Pair<Int, String>>,
        categoryCache: MutableMap<String, Category>,
    ): ImportOutcome? {
        suspend fun fail(reason: String) = ImportOutcome(row.rowNumber, row.name, ImportStatus.FAILED, reason)

        // In-file duplicate barcode.
        if (row.barcode != null) {
            seenBarcodes[row.barcode]?.let { (firstRow, firstName) ->
                return fail("barcode already used by '$firstName' (row $firstRow)")
            }
        }

        // Category (auto-created, cached).
        val category = categoryCache[row.category] ?: run {
            val resolved = ensureCategory(row.category, actorUserId)
            categoryCache[row.category] = resolved
            resolved
        }

        // Existing-product resolution.
        val byBarcode = row.barcode?.let { productDao.getByBarcode(it) }
        val existing = byBarcode ?: if (row.barcode == null) {
            val matches = productDao.getByNameAndCategory(row.name, category.id)
            if (matches.size == 1) matches.first() else null
        } else {
            null
        }

        try {
            if (existing == null) {
                val product = createProduct(
                    fields = ProductFields(
                        name = row.name,
                        categoryId = category.id,
                        barcode = row.barcode,
                        sku = row.sku,
                        costCents = row.costCents,
                        priceCents = row.priceCents,
                        active = row.active,
                        location = row.location,
                    ),
                    initialStock = row.stock,
                    actorUserId = actorUserId,
                )
                row.barcode?.let { seenBarcodes[it] = row.rowNumber to product.name }
                return ImportOutcome(row.rowNumber, product.name, ImportStatus.CREATED)
            } else {
                val fields = ProductFields(
                    name = row.name,
                    categoryId = category.id,
                    barcode = row.barcode,
                    sku = row.sku,
                    costCents = row.costCents,
                    priceCents = row.priceCents,
                    active = row.active,
                    location = row.location,
                )
                if (row.stock != null && row.stock != existing.stockOnHand) {
                    setStock(existing, row.stock, actorUserId, fields)
                } else {
                    updateProduct(existing.id, fields, actorUserId)
                }
                row.barcode?.let { seenBarcodes[it] = row.rowNumber to existing.name }
                return ImportOutcome(row.rowNumber, row.name, ImportStatus.UPDATED)
            }
        } catch (e: CatalogError) {
            return fail(e.message ?: "row failed")
        }
    }

    /** CSV stock set: product upsert + ADJUST delta in one transaction. */
    private suspend fun setStock(
        existing: Product,
        newStock: Long,
        actorUserId: String?,
        fields: ProductFields,
    ) {
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            categoryId = fields.categoryId,
            name = fields.name.trim(),
            barcode = fields.barcode?.trim()?.ifEmpty { null },
            sku = fields.sku?.trim()?.ifEmpty { null },
            costCents = fields.costCents,
            priceCents = fields.priceCents,
            active = fields.active,
            location = fields.location?.trim()?.ifEmpty { null },
            stockOnHand = newStock,
            updatedAt = now,
        )
        val delta = StockDelta(
            id = UUID.randomUUID().toString(),
            shopId = identity.shopId(),
            productId = existing.id,
            delta = newStock - existing.stockOnHand,
            reason = REASON_ADJUST,
            refId = REF_CSV,
            userId = actorUserId,
            at = now,
            createdAt = now,
            updatedAt = now,
        )
        writer.journalizedBatch(
            shopId = identity.shopId(),
            deviceId = identity.deviceId(),
            actorUserId = actorUserId,
            specs = listOf(
                JournalizedWriter.OpSpec(
                    entity = JournalEntities.PRODUCT,
                    opType = JournalEntities.UPSERT,
                    payloadJson = writer.encode(productPayload(updated)),
                ),
                JournalizedWriter.OpSpec(
                    entity = JournalEntities.STOCK_DELTA,
                    opType = JournalEntities.INSERT,
                    payloadJson = writer.encode(stockDeltaPayload(delta)),
                ),
            ),
        ) {
            productDao.upsert(updated)
            stockDeltaDao.insert(delta)
        }
    }

    // ─── CSV export (task 6.5) ───────────────────────────────────────────────

    /** Full catalog (inactive included) in the canonical import format. */
    suspend fun exportCsv(): String {
        val products = productDao.getAll()
        val categoriesById = categoryDao.getAll().associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("name,barcode,sku,category,cost,price,active,stock,location")
        for (product in products) {
            val categoryName = categoriesById[product.categoryId]?.name
                ?: CsvCatalogParser.DEFAULT_CATEGORY
            val cells = listOf(
                product.name,
                product.barcode ?: "",
                product.sku ?: "",
                categoryName,
                MoneyFormatter.formatPlain(product.costCents),
                MoneyFormatter.formatPlain(product.priceCents),
                product.active.toString(),
                product.stockOnHand.toString(),
                product.location ?: "",
            )
            sb.appendLine(cells.joinToString(",") { csvEscape(it) })
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    companion object {
        const val REASON_ADJUST = "ADJUST"
        const val REF_CSV = "csv-import"
    }
}
