package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.usecase.auth.SetPinUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.GetAllFeatureConfigsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.IsFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.SetFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.DeleteCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.SaveCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveUserUseCase
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementViewModel
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// ─────────────────────────────────────────────────────────────────────────────
// settingsModule — Koin DI module for :composeApp:feature:settings.
//
// Registers SettingsViewModel with all required use-case and repository
// dependencies. Downstream repositories & use-cases are expected to be
// provided by :shared:data and :shared:domain modules.
// Sprint 23 — Step 13.1.10
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Feature-scoped Koin module for the Settings feature.
 *
 * Depends on (must be loaded before this module):
 * - `:shared:data`   — provides [SettingsRepository], [TaxGroupRepository], [UserRepository]
 * - `:shared:domain` — provides [SaveTaxGroupUseCase], [SaveUserUseCase], [PrintTestPageUseCase]
 */
val settingsModule = module {
    // ── Use-case bindings ────────────────────────────────────────────────────
    // PrinterManager is provided by :shared:hal; get() resolves it from the
    // merged Koin graph at runtime.
    factory<PrintTestPageUseCase> { PrintTestPageUseCaseImpl(get()) }
    factoryOf(::SaveUserUseCase)
    factoryOf(::SetPinUseCase)
    factoryOf(::SaveCustomRoleUseCase)
    factoryOf(::DeleteCustomRoleUseCase)

    // ── Feature registry use cases (Edition Management) ───────────────────────
    // FeatureRegistryRepository is bound in :shared:data dataModule.
    factoryOf(::GetAllFeatureConfigsUseCase)
    factoryOf(::IsFeatureEnabledUseCase)
    factoryOf(::SetFeatureEnabledUseCase)

    // ── ViewModels ────────────────────────────────────────────────────────────
    viewModelOf(::SettingsViewModel)
    viewModelOf(::EditionManagementViewModel)
}
