package net.redngreen.easypos.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
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

@Dao
interface TenantConfigDao {
    @Upsert
    suspend fun upsert(config: TenantConfig)

    @Query("SELECT * FROM tenant_config LIMIT 1")
    fun observe(): Flow<TenantConfig?>

    @Query("SELECT * FROM tenant_config LIMIT 1")
    suspend fun get(): TenantConfig?
}

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: User)

    @Upsert
    suspend fun upsertAll(users: List<User>)

    @Query("SELECT * FROM users WHERE active = 1 ORDER BY name")
    fun observeActive(): Flow<List<User>>

    /** Admin CRUD view — includes deactivated users. */
    @Query("SELECT * FROM users ORDER BY name")
    fun observeAll(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<User?>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    /** Lockout guard — there must always be one activatable Admin. */
    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1")
    suspend fun countActiveAdmins(): Int
}

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: Category)

    @Upsert
    suspend fun upsertAll(categories: List<Category>)

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Category?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>
}

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsert(product: Product)

    @Upsert
    suspend fun upsertAll(products: List<Product>)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): Product?

    @Query("SELECT * FROM products WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Product>

    @Query(
        "SELECT * FROM products WHERE barcode = :barcode AND active = 1 LIMIT 1"
    )
    suspend fun getActiveByBarcode(barcode: String): Product?

    /** Uniqueness check — any product, active or not (spec: rejects the save). */
    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): Product?

    /** CSV update path: blank-barcode rows match on exact name + category. */
    @Query(
        "SELECT * FROM products WHERE name = :name COLLATE NOCASE AND categoryId = :categoryId"
    )
    suspend fun getByNameAndCategory(name: String, categoryId: String?): List<Product>

    @Query(
        """
        SELECT * FROM products
        WHERE active = 1 AND (
            name LIKE '%' || :query || '%'
            OR barcode LIKE :query || '%'
            OR sku LIKE :query || '%'
        )
        ORDER BY (barcode = :query) DESC, name
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 50): List<Product>

    @Query("SELECT * FROM products WHERE active = 1 AND categoryId = :categoryId ORDER BY name")
    fun observeActiveByCategory(categoryId: String?): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name")
    suspend fun getAll(): List<Product>

    @Query("SELECT COUNT(*) FROM products")
    fun observeCount(): Flow<Int>

    /** Sell-path stock cache bump — only ever called inside the sale transaction. */
    @Query("UPDATE products SET stockOnHand = stockOnHand + :delta WHERE id = :productId")
    suspend fun bumpStock(productId: String, delta: Long)

    @Query("UPDATE products SET stockOnHand = :count WHERE id = :productId")
    suspend fun setStock(productId: String, count: Long)
}

data class SaleWithLines(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "saleId")
    val lines: List<SaleLine>,
)

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLines(lines: List<SaleLine>)

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getById(id: String): Sale?

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getWithLines(id: String): SaleWithLines?

    @Query("SELECT COALESCE(MAX(localNo), 0) + 1 FROM sales WHERE shopId = :shopId")
    suspend fun nextLocalNo(shopId: String): Long

    @Query("SELECT * FROM sales WHERE shopId = :shopId AND at BETWEEN :from AND :to ORDER BY at DESC")
    fun observeBetween(shopId: String, from: Long, to: Long): Flow<List<Sale>>

    /** Cash sales to date (payment type kind = CASH) — the live drawer's sales side. */
    @Query(
        """
        SELECT COALESCE(SUM(s.totalCents), 0) FROM sales s
        JOIN payment_types p ON s.paymentTypeId = p.id
        WHERE s.voided = 0 AND p.kind = 'CASH'
        """
    )
    fun observeCashSalesCents(): Flow<Long>

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    suspend fun linesFor(saleId: String): List<SaleLine>

    /** Item counts for the history list, one query for the whole day. */
    @Query(
        "SELECT saleId, COUNT(*) as lineCount FROM sale_lines WHERE saleId IN (:saleIds) GROUP BY saleId"
    )
    suspend fun lineCounts(saleIds: List<String>): List<SaleLineCount>

    @Query("UPDATE sales SET voided = 1, updatedAt = :at WHERE id = :id")
    suspend fun markVoided(id: String, at: Long)
}

data class SaleLineCount(
    val saleId: String,
    val lineCount: Int,
)

@Dao
interface StockDeltaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delta: StockDelta)

    @Query("SELECT * FROM stock_deltas WHERE id = :id")
    suspend fun getById(id: String): StockDelta?

    /** Authoritative on-hand for a product — the pull applier recomputes its cache. */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_deltas WHERE productId = :productId")
    suspend fun sumFor(productId: String): Long

    @Query("SELECT * FROM stock_deltas WHERE productId = :productId ORDER BY at DESC")
    fun observeForProduct(productId: String): Flow<List<StockDelta>>

    /** Gross units moved in (receives) since a timestamp — "Masuk" chip. */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_deltas WHERE at >= :from AND delta > 0")
    fun observeMovementInSince(from: Long): Flow<Long>

    /** Gross units moved out (sales/removals) since a timestamp — "Keluar" chip. */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM stock_deltas WHERE at >= :from AND delta < 0")
    fun observeMovementOutSince(from: Long): Flow<Long>
}

@Dao
interface CashEntryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: CashEntry)

    @Query("SELECT * FROM cash_entries WHERE id = :id")
    suspend fun getById(id: String): CashEntry?

    @Query("SELECT * FROM cash_entries ORDER BY at DESC")
    fun observeAll(): Flow<List<CashEntry>>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN direction = 'IN' THEN amountCents ELSE -amountCents END
        ), 0) FROM cash_entries
        """
    )
    suspend fun netCashCents(): Long

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN direction = 'IN' THEN amountCents ELSE -amountCents END
        ), 0) FROM cash_entries
        """
    )
    fun observeNetCashCents(): Flow<Long>
}

@Dao
interface DrawerCountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(count: DrawerCount)

    @Query("SELECT * FROM drawer_counts WHERE id = :id")
    suspend fun getById(id: String): DrawerCount?
}

@Dao
interface PaymentTypeDao {
    @Upsert
    suspend fun upsert(paymentType: PaymentType)

    @Upsert
    suspend fun upsertAll(paymentTypes: List<PaymentType>)

    @Query("SELECT * FROM payment_types WHERE active = 1 ORDER BY sort, name")
    fun observeActive(): Flow<List<PaymentType>>

    @Query("SELECT * FROM payment_types ORDER BY sort, name")
    fun observeAll(): Flow<List<PaymentType>>

    @Query("SELECT * FROM payment_types ORDER BY sort, name")
    suspend fun getAll(): List<PaymentType>

    @Query("SELECT * FROM payment_types WHERE kind = 'CASH' AND active = 1 ORDER BY sort LIMIT 1")
    suspend fun getDefaultCash(): PaymentType?

    @Query("SELECT * FROM payment_types WHERE id = :id")
    suspend fun getById(id: String): PaymentType?
}

@Dao
interface SettingDao {
    @Upsert
    suspend fun upsert(setting: Setting)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    /** Full row (value + updatedAt) — the pull applier's LWW key for settings. */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): Setting?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<Setting>
}
