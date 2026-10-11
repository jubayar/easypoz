package net.redngreen.easypos.core.di

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.redngreen.easypos.core.database.LocalIdentity
import net.redngreen.easypos.core.database.db.EasyPosDatabase
import net.redngreen.easypos.core.database.journal.JournalizedWriter
import net.redngreen.easypos.core.network.DataStoreTokenStore
import net.redngreen.easypos.core.network.HttpClientFactory
import net.redngreen.easypos.core.network.TokenStore
import net.redngreen.easypos.core.print.BluetoothPrinter
import net.redngreen.easypos.core.print.DaySummaryShare
import net.redngreen.easypos.core.print.LogoRasterizer
import net.redngreen.easypos.core.print.PdfReceiptWriter
import net.redngreen.easypos.core.print.PdfTextWriter
import net.redngreen.easypos.core.print.PrintController
import net.redngreen.easypos.core.security.PinHasher
import net.redngreen.easypos.core.session.SessionManager
import net.redngreen.easypos.core.data.repo.BackupRepository
import net.redngreen.easypos.core.data.repo.CatalogRepository
import net.redngreen.easypos.core.data.repo.CashRepository
import net.redngreen.easypos.core.data.repo.PaymentTypeRepository
import net.redngreen.easypos.core.data.repo.ReceiptRepository
import net.redngreen.easypos.core.data.repo.ReportsRepository
import net.redngreen.easypos.core.data.repo.SaleRepository
import net.redngreen.easypos.core.data.repo.SettingsRepository
import net.redngreen.easypos.core.data.repo.StockRepository
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.data.repo.UserRepository
import net.redngreen.easypos.core.sync.AccountSyncRepository
import net.redngreen.easypos.core.sync.AuthRemoteDataSource
import net.redngreen.easypos.core.sync.HttpBackendApi
import net.redngreen.easypos.core.sync.LocalSyncStore
import net.redngreen.easypos.core.sync.RemoteApplier
import net.redngreen.easypos.core.sync.RoomLocalSyncStore
import net.redngreen.easypos.core.sync.SyncConfig
import net.redngreen.easypos.core.sync.SyncEngine
import net.redngreen.easypos.core.sync.SyncRemoteDataSource
import net.redngreen.easypos.core.sync.SyncScheduler
import net.redngreen.easypos.core.sync.SyncStatusRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val networkModule = module {
    single<HttpClient> { HttpClientFactory.create(OkHttp.create()) }
    singleOf(::DataStoreTokenStore)
    single<TokenStore> { get<DataStoreTokenStore>() }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), EasyPosDatabase::class.java, EasyPosDatabase.NAME)
            .addMigrations(EasyPosDatabase.MIGRATION_1_2, EasyPosDatabase.MIGRATION_2_3)
            .build()
    }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    factory { get<EasyPosDatabase>().tenantConfigDao() }
    factory { get<EasyPosDatabase>().userDao() }
    factory { get<EasyPosDatabase>().categoryDao() }
    factory { get<EasyPosDatabase>().productDao() }
    factory { get<EasyPosDatabase>().saleDao() }
    factory { get<EasyPosDatabase>().stockDeltaDao() }
    factory { get<EasyPosDatabase>().cashEntryDao() }
    factory { get<EasyPosDatabase>().drawerCountDao() }
    factory { get<EasyPosDatabase>().paymentTypeDao() }
    factory { get<EasyPosDatabase>().settingDao() }
    factory { get<EasyPosDatabase>().journalDao() }
    factory { get<EasyPosDatabase>().conflictLogDao() }
    factory { get<EasyPosDatabase>().syncStateDao() }
    factory { get<EasyPosDatabase>().activityLogDao() }
    factory { get<EasyPosDatabase>().heldSaleDao() }
    factory { get<EasyPosDatabase>().backupDao() }
    factory { get<EasyPosDatabase>().reportsDao() }
    singleOf(::JournalizedWriter)
}

val syncModule = module {
    singleOf(::SyncConfig)
    single { HttpBackendApi(get(), get()) }
    single<SyncRemoteDataSource> { get<HttpBackendApi>() }
    single<AuthRemoteDataSource> { get<HttpBackendApi>() }
    single<LocalSyncStore> { get<RoomLocalSyncStore>() }
    singleOf(::RoomLocalSyncStore)
    singleOf(::RemoteApplier)
    singleOf(::SyncStatusRepository)
    singleOf(::SyncEngine)
    singleOf(::AccountSyncRepository)
    singleOf(::SyncScheduler)
}

val sessionModule = module {
    singleOf(::SessionManager)
    singleOf(::PinHasher)
    singleOf(::LocalIdentity)
}

val printModule = module {
    singleOf(::BluetoothPrinter)
    singleOf(::LogoRasterizer)
    singleOf(::PdfReceiptWriter)
    singleOf(::PdfTextWriter)
    singleOf(::DaySummaryShare)
    singleOf(::PrintController)
}

/**
 * Temporary bridge: repositories that group 4 migrations will move into
 * feature data layers. Entries disappear from here as features adopt them.
 */
val bridgeRepoModule = module {
    singleOf(::CatalogRepository)
    singleOf(::SaleRepository)
    singleOf(::StockRepository)
    singleOf(::CashRepository)
    singleOf(::UserRepository)
    singleOf(::SettingsRepository)
    singleOf(::TenantConfigRepository)
    singleOf(::PaymentTypeRepository)
    singleOf(::ReceiptRepository)
    singleOf(::ReportsRepository)
    singleOf(::BackupRepository)
}
