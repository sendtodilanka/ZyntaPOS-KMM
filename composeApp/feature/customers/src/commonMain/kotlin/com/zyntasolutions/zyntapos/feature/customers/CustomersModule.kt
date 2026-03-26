package com.zyntasolutions.zyntapos.feature.customers

import com.zyntasolutions.zyntapos.domain.usecase.crm.EarnRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.EvaluateCustomerSegmentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.ExpireLoyaltyPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.ExportCustomerDataUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.GetCustomerPurchaseHistoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.MergeCustomersUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.RedeemRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.SaveCustomerGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.WalletTopUpUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:customers`.
 *
 * Registers the following use cases and the [CustomerViewModel]:
 *
 * ### Use Cases (factory — new instance per injection)
 * - [SaveCustomerGroupUseCase]   — validates and persists customer groups
 * - [WalletTopUpUseCase]         — validates and applies wallet credit
 * - [EarnRewardPointsUseCase]    — awards loyalty points after a sale
 * - [RedeemRewardPointsUseCase]  — debits loyalty points at redemption
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `CustomerRepository`        — customer CRUD + FTS5 search
 * - `CustomerGroupRepository`   — group CRUD
 * - `CustomerWalletRepository`  — wallet balance + transactions
 * - `LoyaltyRepository`         — reward points ledger + tiers
 * - `AuthRepository`            — session resolver for currentUserId
 */
val customersModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::SaveCustomerGroupUseCase)
    factoryOf(::WalletTopUpUseCase)
    factoryOf(::EarnRewardPointsUseCase)
    factoryOf(::ExpireLoyaltyPointsUseCase)
    factoryOf(::RedeemRewardPointsUseCase)
    factoryOf(::ExportCustomerDataUseCase)
    factoryOf(::MergeCustomersUseCase)
    factoryOf(::GetCustomerPurchaseHistoryUseCase)

    // ── G: Customer Segmentation ────────────────────────────────────────────
    factoryOf(::EvaluateCustomerSegmentUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModel {
        CustomerViewModel(
            customerRepository       = get(),
            groupRepository          = get(),
            walletRepository         = get(),
            loyaltyRepository        = get(),
            saveGroupUseCase         = get(),
            walletTopUpUseCase       = get(),
            exportCustomerDataUseCase = get(),
            mergeCustomersUseCase    = get(),
            getPurchaseHistoryUseCase = get(),
            authRepository           = get(),
            analytics                = get(),
        )
    }
}
