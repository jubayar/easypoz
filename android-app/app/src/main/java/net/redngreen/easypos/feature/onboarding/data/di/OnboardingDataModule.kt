package net.redngreen.easypos.feature.onboarding.data.di

import net.redngreen.easypos.feature.onboarding.data.SettingsOnboardingRepository
import net.redngreen.easypos.feature.onboarding.domain.OnboardingRepository
import org.koin.dsl.module

val onboardingDataModule = module {
    single<OnboardingRepository> {
        SettingsOnboardingRepository(
            settings = get(),
            tenantConfig = get(),
            catalog = get(),
            session = get(),
            printer = get(),
            printController = get(),
            appScope = get(),
        )
    }
}
