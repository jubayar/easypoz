package net.redngreen.easypos.feature.catalog.data.di

import net.redngreen.easypos.feature.catalog.data.CatalogRepositoryImpl
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository
import org.koin.dsl.module

val catalogDataModule = module {
    single<CatalogRepository> {
        CatalogRepositoryImpl(
            productDao = get(),
            categoryDao = get(),
            stockDeltaDao = get(),
            identity = get(),
            writer = get(),
        )
    }
}
