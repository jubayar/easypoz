package net.redngreen.easypos.feature.onboarding.ui.di

import net.redngreen.easypos.feature.onboarding.ui.OnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingUiModule = module {
    viewModelOf(::OnboardingViewModel)
}
