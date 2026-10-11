package net.redngreen.easypos.feature.catalog.ui.di

import net.redngreen.easypos.feature.catalog.ui.CategoriesViewModel
import net.redngreen.easypos.feature.catalog.ui.CsvImportViewModel
import net.redngreen.easypos.feature.catalog.ui.ProductEditViewModel
import net.redngreen.easypos.feature.catalog.ui.ProductsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val catalogUiModule = module {
    viewModelOf(::ProductsViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::ProductEditViewModel)
    viewModelOf(::CsvImportViewModel)
}
