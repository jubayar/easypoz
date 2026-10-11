package net.redngreen.easypos.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import net.redngreen.easypos.core.data.entity.CashEntry
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.DrawerCount
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.Sale
import net.redngreen.easypos.core.data.entity.SaleLine
import net.redngreen.easypos.core.data.entity.Setting
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.entity.TenantConfig
import net.redngreen.easypos.core.data.entity.User

/**
 * Whole-dataset reads and clears for manual backup/restore (task 11.3).
 * Reads are used to serialize; clears are used before restoring in one
 * transaction. Deliberately excludes PIN hashes at the repository boundary.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tenant_config LIMIT 1")
    suspend fun tenantConfig(): TenantConfig?

    @Query("SELECT * FROM users")
    suspend fun users(): List<User>

    @Query("SELECT * FROM categories")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM products")
    suspend fun products(): List<Product>

    @Query("SELECT * FROM sales")
    suspend fun sales(): List<Sale>

    @Query("SELECT * FROM sale_lines")
    suspend fun saleLines(): List<SaleLine>

    @Query("SELECT * FROM stock_deltas")
    suspend fun stockDeltas(): List<StockDelta>

    @Query("SELECT * FROM cash_entries")
    suspend fun cashEntries(): List<CashEntry>

    @Query("SELECT * FROM drawer_counts")
    suspend fun drawerCounts(): List<DrawerCount>

    @Query("SELECT * FROM payment_types")
    suspend fun paymentTypes(): List<PaymentType>

    @Query("SELECT * FROM settings")
    suspend fun settings(): List<Setting>

    @Query("DELETE FROM tenant_config")
    suspend fun clearTenantConfig()

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM sale_lines")
    suspend fun clearSaleLines()

    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM stock_deltas")
    suspend fun clearStockDeltas()

    @Query("DELETE FROM cash_entries")
    suspend fun clearCashEntries()

    @Query("DELETE FROM drawer_counts")
    suspend fun clearDrawerCounts()

    @Query("DELETE FROM payment_types")
    suspend fun clearPaymentTypes()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()
}
