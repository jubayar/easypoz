package net.redngreen.easypos.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.redngreen.easypos.core.data.dao.ActivityLogDao
import net.redngreen.easypos.core.data.dao.BackupDao
import net.redngreen.easypos.core.data.dao.CashEntryDao
import net.redngreen.easypos.core.data.dao.CategoryDao
import net.redngreen.easypos.core.data.dao.ConflictLogDao
import net.redngreen.easypos.core.data.dao.DrawerCountDao
import net.redngreen.easypos.core.data.dao.HeldSaleDao
import net.redngreen.easypos.core.data.dao.JournalDao
import net.redngreen.easypos.core.data.dao.PaymentTypeDao
import net.redngreen.easypos.core.data.dao.ProductDao
import net.redngreen.easypos.core.data.dao.ReportsDao
import net.redngreen.easypos.core.data.dao.SaleDao
import net.redngreen.easypos.core.data.dao.SettingDao
import net.redngreen.easypos.core.data.dao.StockDeltaDao
import net.redngreen.easypos.core.data.dao.SyncStateDao
import net.redngreen.easypos.core.data.dao.TenantConfigDao
import net.redngreen.easypos.core.data.dao.UserDao
import net.redngreen.easypos.core.data.entity.ActivityLog
import net.redngreen.easypos.core.data.entity.CashEntry
import net.redngreen.easypos.core.data.entity.Category
import net.redngreen.easypos.core.data.entity.ConflictLog
import net.redngreen.easypos.core.data.entity.DrawerCount
import net.redngreen.easypos.core.data.entity.HeldSale
import net.redngreen.easypos.core.data.entity.JournalOp
import net.redngreen.easypos.core.data.entity.PaymentType
import net.redngreen.easypos.core.data.entity.Product
import net.redngreen.easypos.core.data.entity.Sale
import net.redngreen.easypos.core.data.entity.SaleLine
import net.redngreen.easypos.core.data.entity.Setting
import net.redngreen.easypos.core.data.entity.StockDelta
import net.redngreen.easypos.core.data.entity.SyncState
import net.redngreen.easypos.core.data.entity.TenantConfig
import net.redngreen.easypos.core.data.entity.User

@Database(
    entities = [
        TenantConfig::class,
        User::class,
        Category::class,
        Product::class,
        Sale::class,
        SaleLine::class,
        HeldSale::class,
        StockDelta::class,
        CashEntry::class,
        DrawerCount::class,
        PaymentType::class,
        Setting::class,
        JournalOp::class,
        ConflictLog::class,
        SyncState::class,
        ActivityLog::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class EasyPosDatabase : RoomDatabase() {
    abstract fun tenantConfigDao(): TenantConfigDao
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun stockDeltaDao(): StockDeltaDao
    abstract fun cashEntryDao(): CashEntryDao
    abstract fun drawerCountDao(): DrawerCountDao
    abstract fun paymentTypeDao(): PaymentTypeDao
    abstract fun settingDao(): SettingDao
    abstract fun journalDao(): JournalDao
    abstract fun conflictLogDao(): ConflictLogDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun heldSaleDao(): HeldSaleDao
    abstract fun backupDao(): BackupDao
    abstract fun reportsDao(): ReportsDao

    companion object {
        const val NAME = "easypos.db"

        /** v2: activity_log for denials, logins, and action attribution. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT,
                        `userName` TEXT,
                        `type` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `detail` TEXT,
                        `at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_log_userId` ON `activity_log` (`userId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_log_at` ON `activity_log` (`at`)"
                )
            }
        }

        /** v3: StockDelta.note — human reason for a manual stock adjustment. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stock_deltas` ADD COLUMN `note` TEXT")
            }
        }
    }
}
