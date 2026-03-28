package com.zyntasolutions.zyntapos.feature.accounting.di

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.CloseAccountingPeriodUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.ConsolidatedFinancialReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.CreateAccountingEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.CreateAccountingPeriodUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeactivateAccountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeleteDraftEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountBalancesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountingPeriodsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetBalanceSheetUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GenerateProfitAndLossUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetCashFlowStatementUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetGeneralLedgerUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetJournalEntriesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetPeriodSummaryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetProfitAndLossUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetTrialBalanceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.LockAccountingPeriodUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostExpenseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostInventoryAdjustmentJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostPayrollJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostSaleJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.ReopenAccountingPeriodUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.ReverseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveAccountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveDraftJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.TrackBudgetSpendingUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SeedDefaultChartOfAccountsUseCase
import com.zyntasolutions.zyntapos.feature.accounting.AccountDetailViewModel
import com.zyntasolutions.zyntapos.feature.accounting.AccountingViewModel
import com.zyntasolutions.zyntapos.feature.accounting.ChartOfAccountsViewModel
import com.zyntasolutions.zyntapos.feature.accounting.FinancialStatementsViewModel
import com.zyntasolutions.zyntapos.feature.accounting.GeneralLedgerViewModel
import com.zyntasolutions.zyntapos.feature.accounting.JournalEntryDetailViewModel
import com.zyntasolutions.zyntapos.feature.accounting.JournalEntryListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:accounting`.
 *
 * ### Accounting Ledger Use Cases (Sprint 18)
 * - [GetPeriodSummaryUseCase]   — aggregated account balances by fiscal period
 * - [CreateAccountingEntryUseCase] — balanced double-entry journal entries
 *
 * ### Chart of Accounts Use Cases (Wave 3B)
 * - [GetAccountsUseCase], [SaveAccountUseCase], [DeactivateAccountUseCase],
 *   [SeedDefaultChartOfAccountsUseCase], [GetAccountBalancesUseCase]
 *
 * ### Journal Entry Use Cases (Wave 3B)
 * - [GetJournalEntriesUseCase], [PostJournalEntryUseCase], [SaveDraftJournalEntryUseCase],
 *   [ReverseJournalEntryUseCase], [DeleteDraftEntryUseCase]
 *
 * ### Accounting Period Use Cases (Wave 3B)
 * - [GetAccountingPeriodsUseCase], [CreateAccountingPeriodUseCase],
 *   [CloseAccountingPeriodUseCase], [LockAccountingPeriodUseCase], [ReopenAccountingPeriodUseCase]
 *
 * ### Financial Statement Use Cases (Wave 3B)
 * - [GetProfitAndLossUseCase], [GetBalanceSheetUseCase], [GetTrialBalanceUseCase],
 *   [GetGeneralLedgerUseCase]
 */
val accountingModule = module {

    // ── Legacy Accounting Ledger Use Cases (Sprint 18) ────────────────────────
    factoryOf(::GetPeriodSummaryUseCase)
    factoryOf(::CreateAccountingEntryUseCase)

    // ── Chart of Accounts Use Cases (Wave 3B) ─────────────────────────────────
    factoryOf(::GetAccountsUseCase)
    factoryOf(::SaveAccountUseCase)
    factoryOf(::DeactivateAccountUseCase)
    factoryOf(::SeedDefaultChartOfAccountsUseCase)
    factoryOf(::GetAccountBalancesUseCase)

    // ── Journal Entry Use Cases (Wave 3B) ─────────────────────────────────────
    factoryOf(::GetJournalEntriesUseCase)
    factoryOf(::SaveDraftJournalEntryUseCase)
    factoryOf(::PostJournalEntryUseCase)
    factoryOf(::ReverseJournalEntryUseCase)
    factoryOf(::DeleteDraftEntryUseCase)
    factoryOf(::PostSaleJournalEntryUseCase)
    factoryOf(::PostExpenseJournalEntryUseCase)
    factoryOf(::PostPayrollJournalEntryUseCase)
    factoryOf(::PostInventoryAdjustmentJournalEntryUseCase)

    // ── Accounting Period Use Cases (Wave 3B) ─────────────────────────────────
    factoryOf(::GetAccountingPeriodsUseCase)
    factoryOf(::CreateAccountingPeriodUseCase)
    factoryOf(::CloseAccountingPeriodUseCase)
    factoryOf(::LockAccountingPeriodUseCase)
    factoryOf(::ReopenAccountingPeriodUseCase)

    // ── Financial Statement Use Cases (Wave 3B) ───────────────────────────────
    factoryOf(::ConsolidatedFinancialReportUseCase)
    factoryOf(::GetProfitAndLossUseCase)
    factoryOf(::GetBalanceSheetUseCase)
    factoryOf(::GetTrialBalanceUseCase)
    factoryOf(::GetGeneralLedgerUseCase)
    factoryOf(::GetCashFlowStatementUseCase)

    // ── Budget & P&L Integration Use Cases (Phase 3) ────────────────────────
    factoryOf(::TrackBudgetSpendingUseCase)
    factoryOf(::GenerateProfitAndLossUseCase)

    // ── ViewModels ────────────────────────────────────────────────────────────
    // AuthRepository is injected directly into legacy VMs; storeId is resolved
    // lazily inside each ViewModel's init{} via viewModelScope.launch —
    // never blocks the main thread.

    viewModel {
        AccountingViewModel(
            getPeriodSummaryUseCase  = get(),
            getProfitAndLossUseCase  = get(),
            storeRepository          = get(),
            authRepository           = get(),
            settingsRepository       = get(),
            analytics                = get(),
        )
    }

    // ── Wave 3B ViewModels ────────────────────────────────────────────────────

    viewModel {
        ChartOfAccountsViewModel(
            getAccountsUseCase               = get(),
            deactivateAccountUseCase         = get(),
            seedDefaultChartOfAccountsUseCase = get(),
        )
    }

    viewModel {
        AccountDetailViewModel(
            getAccountsUseCase = get(),
            saveAccountUseCase = get(),
        )
    }

    viewModel {
        JournalEntryListViewModel(
            getJournalEntriesUseCase = get(),
            deleteDraftEntryUseCase  = get(),
        )
    }

    viewModel {
        JournalEntryDetailViewModel(
            getJournalEntriesUseCase      = get(),
            saveDraftJournalEntryUseCase  = get(),
            postJournalEntryUseCase       = get(),
            reverseJournalEntryUseCase    = get(),
        )
    }

    viewModel {
        FinancialStatementsViewModel(
            getProfitAndLossUseCase      = get(),
            getBalanceSheetUseCase       = get(),
            getTrialBalanceUseCase       = get(),
            getCashFlowStatementUseCase  = get(),
        )
    }

    viewModel {
        GeneralLedgerViewModel(
            getAccountsUseCase      = get(),
            getGeneralLedgerUseCase = get(),
        )
    }
}
