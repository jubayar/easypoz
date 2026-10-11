package net.redngreen.easypos.feature.login.ui.di

import net.redngreen.easypos.feature.login.ui.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val loginUiModule = module {
    viewModelOf(::LoginViewModel)
}
