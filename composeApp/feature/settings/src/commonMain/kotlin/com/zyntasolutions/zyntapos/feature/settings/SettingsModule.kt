package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.usecase.auth.GrantStoreAccessUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.RevokeStoreAccessUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.SetPinUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.GetAllFeatureConfigsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.IsFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.SetFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.DeleteCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.SaveCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.DeletePrinterProfileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.DeleteTaxOverrideUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetLabelPrinterConfigUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetPrinterProfilesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetTaxOverridesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveLabelPrinterConfigUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SavePrinterProfileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveTaxOverrideUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveUserUseCase
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementViewModel
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideViewModel
import com.zyntasolutions.zyntapos.feature.settings.screen.StoreUserAccessViewModel
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
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
    factoryOf(::GrantStoreAccessUseCase)
    factoryOf(::RevokeStoreAccessUseCase)
    factoryOf(::SaveCustomRoleUseCase)
    factoryOf(::DeleteCustomRoleUseCase)

    // ── Feature registry use cases (Edition Management) ───────────────────────
    // FeatureRegistryRepository is bound in :shared:data dataModule.
    factoryOf(::GetAllFeatureConfigsUseCase)
    factoryOf(::IsFeatureEnabledUseCase)
    factoryOf(::SetFeatureEnabledUseCase)

    // ── Tax override use cases ─────────────────────────────────────────────────
    factoryOf(::GetTaxOverridesUseCase)
    factoryOf(::SaveTaxOverrideUseCase)
    factoryOf(::DeleteTaxOverrideUseCase)

    // ── Hardware settings use cases ────────────────────────────────────────────
    factoryOf(::GetLabelPrinterConfigUseCase)
    factoryOf(::SaveLabelPrinterConfigUseCase)
    factoryOf(::GetPrinterProfilesUseCase)
    factoryOf(::SavePrinterProfileUseCase)
    factoryOf(::DeletePrinterProfileUseCase)

    // ── ViewModels ────────────────────────────────────────────────────────────
    // SettingsViewModel exceeds viewModelOf() arity limit — explicit wiring required.
    viewModel {
        SettingsViewModel(
            settingsRepository         = get(),
            taxGroupRepository         = get(),
            userRepository             = get(),
            roleRepository             = get(),
            saveTaxGroupUseCase        = get(),
            saveUserUseCase            = get(),
            setPinUseCase              = get(),
            saveCustomRoleUseCase      = get(),
            deleteCustomRoleUseCase    = get(),
            printTestPageUseCase       = get(),
            backupService              = get(),
            getLabelPrinterConfigUseCase  = get(),
            saveLabelPrinterConfigUseCase = get(),
            getPrinterProfilesUseCase  = get(),
            savePrinterProfileUseCase  = get(),
            deletePrinterProfileUseCase = get(),
            storeRepository            = get(),
            auditLogger                = get(),
            authRepository             = get(),
            analytics                  = get(),
            getTaxOverridesUseCase     = get(),
            saveTaxOverrideUseCase     = get(),
            deleteTaxOverrideUseCase   = get(),
        )
    }
    viewModelOf(::EditionManagementViewModel)
    viewModelOf(::RegionalTaxOverrideViewModel)
    viewModelOf(::StoreUserAccessViewModel)
}
