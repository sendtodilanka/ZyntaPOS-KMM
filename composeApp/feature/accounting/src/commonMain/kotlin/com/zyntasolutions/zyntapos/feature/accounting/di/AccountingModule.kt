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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    viewModel {
        val session = runBlocking { get<AuthRepository>().getSession().first() }
        EInvoiceViewModel(
            getEInvoicesUseCase = get(),
            submitEInvoiceToIrdUseCase = get(),
            cancelEInvoiceUseCase = get(),
            currentStoreId = session?.storeId ?: "default",
        )
    }

    viewModel {
        val session = runBlocking { get<AuthRepository>().getSession().first() }
        AccountingViewModel(
            getPeriodSummaryUseCase = get(),
            currentStoreId = session?.storeId ?: "default",
        )
    }
}
