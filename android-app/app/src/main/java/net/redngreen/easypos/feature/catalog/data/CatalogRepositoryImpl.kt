package net.redngreen.easypos.feature.catalog.data

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.dao.CategoryDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.StockDeltaDao
import net.redngreen.easypos.core.database.entity.Category as CategoryEntity
import net.redngreen.easypos.core.database.entity.Product as ProductEntity
import net.redngreen.easypos.core.database.entity.StockDelta
import net.redngreen.easypos.core.database.journal.JournalEntities
import net.redngreen.easypos.core.database.journal.JournalizedWriter
import net.redngreen.easypos.core.database.journal.categoryPayload
import net.redngreen.easypos.core.database.journal.productPayload
import net.redngreen.easypos.core.database.journal.stockDeltaPayload
import net.redngreen.easypos.core.money.MoneyFormatter
import net.redngreen.easypos.feature.catalog.domain.CatalogCsvRow
import net.redngreen.easypos.feature.catalog.domain.CatalogError
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import net.redngreen.easypos.feature.catalog.domain.Category
import net.redngreen.easypos.feature.catalog.domain.ImportOutcome
import net.redngreen.easypos.feature.catalog.domain.ImportStatus
import net.redngreen.easypos.feature.catalog.domain.ImportSummary
import net.redngreen.easypos.feature.catalog.domain.Product
import net.redngreen.easypos.feature.catalog.domain.ProductFields

/**
 * Product & category mutations (tasks 6.1–6.5). Everything synced goes
 * through JournalizedWriter — one transaction per logical change, compound
 * changes (product + stock delta) share one transaction. Room entities stay
 * inside this class; the domain sees mapped models only (design D5).
 */
class CatalogRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val stockDeltaDao: StockDeltaDao,
    private val identity: LocalIdentity,
    private val writer: JournalizedWriter,
) : CatalogRepository {

    // ─── Observers ───────────────────────────────────────────────────────────

    override fun observeProducts(): Flow<List<Product>> =
        productDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findActiveByBarcode(barcode: String): Product? =
        productDao.getActiveByBarcode(barcode)?.toDomain()

    override suspend fun searchMatching(query: String, limit: Int): List<Product> =
        productDao.search(query, limit).map { it.toDomain() }

    override suspend fun getProduct(id: String): Product? =
        productDao.getById(id)?.toDomain()

    // ─── Categories (task 6.1) ───────────────────────────────────────────────

    /** Creates a category (fails on duplicate name) or returns the existing one. */
    override suspend fun ensureCategory(name: String, actorUserId: String?): Category {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        categoryDao.getByName(trimmed)?.let { return it.toDomain() }
        val now = System.currentTimeMillis()
        val category = CategoryEntity(
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
            payloadJson = writer.encode(categoryPayload(category)),
        ) {
            categoryDao.upsert(category)
        }
        return category.toDomain()
    }

    override suspend fun renameCategory(id: String, newName: String, actorUserId: String?) {
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
            payloadJson = writer.encode(categoryPayload(updated)),
        ) {
            categoryDao.upsert(updated)
        }
    }

    /** Reorder: assigns sortOrder by list position; journals changed rows. */
    override suspend fun reorderCategories(orderedIds: List<String>, actorUserId: String?) {
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
                    payloadJson = writer.encode(categoryPayload(updated)),
                ) {
                    categoryDao.upsert(updated)
                }
            }
        }
    }

    private suspend fun nextSortOrder(): Int =
        (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1

    // ─── Products (task 6.2) ─────────────────────────────────────────────────

    /** Creates a product; initial stock (when given) is journaled as ADJUST. */
    override suspend fun createProduct(
        fields: ProductFields,
        initialStock: Long?,
        actorUserId: String?,
    ): Product {
        val trimmed = fields.name.trim()
        if (trimmed.isEmpty()) throw CatalogError.InvalidName()
        if (fields.priceCents < 0) throw CatalogError.InvalidPrice()
        validateBarcodeFree(fields.barcode, exceptId = null)

        val now = System.currentTimeMillis()
        val product = ProductEntity(
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
        return product.toDomain()
    }

    override suspend fun updateProduct(
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
    override suspend fun deactivateProduct(id: String, actorUserId: String?) {
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
        product: ProductEntity,
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

    private suspend fun writeJournalizedProduct(product: ProductEntity, actorUserId: String?) {
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

    /**
     * Applies parsed rows per docs update semantics: barcode match, else
     * blank-barcode + exact name&category match, else create. Categories are
     * auto-created; stock is set via an ADJUST delta; duplicate barcodes
     * (in-file or in-DB) fail their row naming the conflict.
     */
    override suspend fun importRows(
        rows: List<CatalogCsvRow>,
        actorUserId: String?,
    ): ImportSummary {
        val outcomes = mutableListOf<ImportOutcome>()
        val seenBarcodes = mutableMapOf<String, Pair<Int, String>>() // barcode → (row, name)
        val categoryCache = mutableMapOf<String, CategoryEntity>()

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
        row: CatalogCsvRow,
        actorUserId: String?,
        seenBarcodes: MutableMap<String, Pair<Int, String>>,
        categoryCache: MutableMap<String, CategoryEntity>,
    ): ImportOutcome? {
        suspend fun fail(reason: String) = ImportOutcome(row.rowNumber, row.name, ImportStatus.FAILED, reason)

        // In-file duplicate barcode.
        if (row.barcode != null) {
            seenBarcodes[row.barcode]?.let { (firstRow, firstName) ->
                return fail("barcode already used by '$firstName' (row $firstRow)")
            }
        }

        // Category (auto-created, cached).
        val category: CategoryEntity = categoryCache[row.category] ?: run {
            val resolved = ensureCategory(row.category, actorUserId)
            CategoryEntity(
                id = resolved.id,
                shopId = resolved.shopId,
                name = resolved.name,
                sortOrder = resolved.sortOrder,
                updatedAt = resolved.updatedAt,
            ).also { categoryCache[row.category] = it }
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
        existing: ProductEntity,
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
    override suspend fun exportCsv(): String {
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

    // ─── Mappers (entity ↔ domain, design D5) ────────────────────────────────

    private fun ProductEntity.toDomain() = Product(
        id = id,
        shopId = shopId,
        categoryId = categoryId,
        name = name,
        barcode = barcode,
        sku = sku,
        costCents = costCents,
        priceCents = priceCents,
        taxRateBps = taxRateBps,
        active = active,
        lowStockThreshold = lowStockThreshold,
        emoji = emoji,
        color = color,
        photo = photo,
        location = location,
        stockOnHand = stockOnHand,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        shopId = shopId,
        name = name,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
    )

    companion object {
        const val REASON_ADJUST = "ADJUST"
        const val REF_CSV = "csv-import"
    }
}
