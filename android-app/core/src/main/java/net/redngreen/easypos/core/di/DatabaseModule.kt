package net.redngreen.easypos.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.redngreen.easypos.core.database.dao.ActivityLogDao
import net.redngreen.easypos.core.database.dao.BackupDao
import net.redngreen.easypos.core.database.dao.CashEntryDao
import net.redngreen.easypos.core.database.dao.CategoryDao
import net.redngreen.easypos.core.database.dao.ConflictLogDao
import net.redngreen.easypos.core.database.dao.DrawerCountDao
import net.redngreen.easypos.core.database.dao.HeldSaleDao
import net.redngreen.easypos.core.database.dao.JournalDao
import net.redngreen.easypos.core.database.dao.PaymentTypeDao
import net.redngreen.easypos.core.database.dao.ProductDao
import net.redngreen.easypos.core.database.dao.ReportsDao
import net.redngreen.easypos.core.database.dao.SaleDao
import net.redngreen.easypos.core.database.dao.SettingDao
import net.redngreen.easypos.core.database.dao.StockDeltaDao
import net.redngreen.easypos.core.database.dao.SyncStateDao
import net.redngreen.easypos.core.database.dao.TenantConfigDao
import net.redngreen.easypos.core.database.dao.UserDao
import net.redngreen.easypos.core.database.db.EasyPosDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EasyPosDatabase =
        Room.databaseBuilder(context, EasyPosDatabase::class.java, EasyPosDatabase.NAME)
            .addMigrations(EasyPosDatabase.MIGRATION_1_2, EasyPosDatabase.MIGRATION_2_3)
            .build()

    /** Application-lifetime scope for fire-and-forget background writes. */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides fun provideTenantConfigDao(db: EasyPosDatabase): TenantConfigDao = db.tenantConfigDao()
    @Provides fun provideUserDao(db: EasyPosDatabase): UserDao = db.userDao()
    @Provides fun provideCategoryDao(db: EasyPosDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideProductDao(db: EasyPosDatabase): ProductDao = db.productDao()
    @Provides fun provideSaleDao(db: EasyPosDatabase): SaleDao = db.saleDao()
    @Provides fun provideStockDeltaDao(db: EasyPosDatabase): StockDeltaDao = db.stockDeltaDao()
    @Provides fun provideCashEntryDao(db: EasyPosDatabase): CashEntryDao = db.cashEntryDao()
    @Provides fun provideDrawerCountDao(db: EasyPosDatabase): DrawerCountDao = db.drawerCountDao()
    @Provides fun providePaymentTypeDao(db: EasyPosDatabase): PaymentTypeDao = db.paymentTypeDao()
    @Provides fun provideSettingDao(db: EasyPosDatabase): SettingDao = db.settingDao()
    @Provides fun provideJournalDao(db: EasyPosDatabase): JournalDao = db.journalDao()
    @Provides fun provideConflictLogDao(db: EasyPosDatabase): ConflictLogDao = db.conflictLogDao()
    @Provides fun provideSyncStateDao(db: EasyPosDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun provideActivityLogDao(db: EasyPosDatabase): ActivityLogDao = db.activityLogDao()
    @Provides fun provideHeldSaleDao(db: EasyPosDatabase): HeldSaleDao = db.heldSaleDao()
    @Provides fun provideBackupDao(db: EasyPosDatabase): BackupDao = db.backupDao()
    @Provides fun provideReportsDao(db: EasyPosDatabase): ReportsDao = db.reportsDao()
}
