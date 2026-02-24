package com.zyntasolutions.zyntapos.feature.onboarding

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for the `:composeApp:feature:onboarding` first-run wizard.
 *
 * Registered alongside other feature modules in [ZyntaApplication] (Android)
 * and `main()` (Desktop). The ViewModel depends on:
 * - [com.zyntasolutions.zyntapos.domain.repository.UserRepository] (from data module)
 * - [com.zyntasolutions.zyntapos.domain.repository.SettingsRepository] (from data module)
 */
val onboardingModule = module {
    viewModelOf(::OnboardingViewModel)
}
