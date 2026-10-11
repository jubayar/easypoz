package net.redngreen.easypos.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.redngreen.easypos.feature.catalog.data.CatalogRepositoryImpl
import net.redngreen.easypos.feature.catalog.domain.CatalogRepository

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
}
