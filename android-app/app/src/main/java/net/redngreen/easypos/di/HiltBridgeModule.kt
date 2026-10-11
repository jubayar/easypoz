package net.redngreen.easypos.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.redngreen.easypos.feature.catalog.data.CatalogRepositoryImpl
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import net.redngreen.easypos.feature.sell.data.ReceiptRepositoryImpl
import net.redngreen.easypos.feature.sell.data.RoomSaleRepository
import net.redngreen.easypos.feature.sell.domain.ReceiptRepository
import net.redngreen.easypos.feature.sell.domain.SaleRepository
import net.redngreen.easypos.print.DaySummaryShare
import net.redngreen.easypos.print.PrintController
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Temporary Hilt bridge while ViewModels migrate to Koin one feature at a
 * time (restructure D9). Removed when the last Hilt ViewModel switches.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HiltBridgeModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindSaleRepository(impl: RoomSaleRepository): SaleRepository

    @Binds
    @Singleton
    abstract fun bindReceiptRepository(impl: ReceiptRepositoryImpl): ReceiptRepository
}

/** Print controllers living in :app (they orchestrate feature repositories). */
val printControllerModule = module {
    singleOf(::PrintController)
    singleOf(::DaySummaryShare)
}
