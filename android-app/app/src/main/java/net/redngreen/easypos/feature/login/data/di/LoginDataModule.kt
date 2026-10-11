package net.redngreen.easypos.feature.login.data.di

import net.redngreen.easypos.feature.login.data.SessionLoginRepository
import net.redngreen.easypos.feature.login.domain.LoginRepository
import org.koin.dsl.module

val loginDataModule = module {
    single<LoginRepository> { SessionLoginRepository(get(), get(), get(), get()) }
}
