package com.zyntasolutions.zyntapos.feature.accounting.di

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetPeriodSummaryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.CreateAccountingEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CancelEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CreateEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoiceByOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoicesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.SubmitEInvoiceToIrdUseCase
import com.zyntasolutions.zyntapos.feature.accounting.AccountingViewModel
import com.zyntasolutions.zyntapos.feature.accounting.EInvoiceViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:accounting`.
 *
 * ### E-Invoice Use Cases
 * - [GetEInvoicesUseCase], [GetEInvoiceByOrderUseCase], [CreateEInvoiceUseCase],
 *   [SubmitEInvoiceToIrdUseCase], [CancelEInvoiceUseCase]
 *
 * ### Accounting Ledger Use Cases (Sprint 18)
 * - [GetPeriodSummaryUseCase]   — aggregated account balances by fiscal period
 * - [CreateAccountingEntryUseCase] — balanced double-entry journal entries
 */
val accountingModule = module {

    // ── E-Invoice Use Cases ───────────────────────────────────────────────────
    factoryOf(::GetEInvoicesUseCase)
    factoryOf(::GetEInvoiceByOrderUseCase)
    factoryOf(::CreateEInvoiceUseCase)
    factoryOf(::SubmitEInvoiceToIrdUseCase)
    factoryOf(::CancelEInvoiceUseCase)

    // ── Accounting Ledger Use Cases ───────────────────────────────────────────
    factoryOf(::GetPeriodSummaryUseCase)
    factoryOf(::CreateAccountingEntryUseCase)

    // ── ViewModels ────────────────────────────────────────────────────────────
    // AuthRepository is injected directly; storeId is resolved lazily inside
    // each ViewModel's init{} via viewModelScope.launch — never blocks the main thread.
    viewModel {
        EInvoiceViewModel(
            getEInvoicesUseCase        = get(),
            submitEInvoiceToIrdUseCase = get(),
            cancelEInvoiceUseCase      = get(),
            authRepository             = get(),
        )
    }

    viewModel {
        AccountingViewModel(
            getPeriodSummaryUseCase = get(),
            authRepository          = get(),
        )
    }
}
