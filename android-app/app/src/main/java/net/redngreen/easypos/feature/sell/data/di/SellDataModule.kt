package net.redngreen.easypos.feature.sell.data.di

import net.redngreen.easypos.feature.sell.data.ReceiptRepositoryImpl
import net.redngreen.easypos.feature.sell.data.RoomSaleRepository
import net.redngreen.easypos.feature.sell.domain.ReceiptRepository
import net.redngreen.easypos.feature.sell.domain.SaleRepository
import org.koin.dsl.module

val sellDataModule = module {
    single<SaleRepository> { RoomSaleRepository(get(), get(), get(), get(), get(), get(), get()) }
    single<ReceiptRepository> { ReceiptRepositoryImpl(get(), get(), get(), get(), get(), get()) }
}
