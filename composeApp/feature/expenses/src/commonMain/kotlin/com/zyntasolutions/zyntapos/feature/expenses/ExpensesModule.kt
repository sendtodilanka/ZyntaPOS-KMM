package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostExpenseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.ApproveExpenseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.SaveExpenseUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:expenses`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [SaveExpenseUseCase]              — validates and persists expenses
 * - [ApproveExpenseUseCase]           — approve/reject workflow
 * - [PostExpenseJournalEntryUseCase]  — auto-posts double-entry journal on new expense (best-effort)
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `ExpenseRepository`        — expense CRUD, approval, category management
 * - `AuthRepository`           — session resolver for currentUserId and storeId
 * - `JournalRepository`        — journal entry persistence (via PostExpenseJournalEntryUseCase)
 * - `AccountRepository`        — account lookup (via PostExpenseJournalEntryUseCase)
 * - `AccountingPeriodRepository` — period validation (via PostExpenseJournalEntryUseCase)
 */
val expensesModule = module {
    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::SaveExpenseUseCase)
    factoryOf(::ApproveExpenseUseCase)
    factory {
        PostExpenseJournalEntryUseCase(
            journalRepository = get(),
            accountRepository = get(),
            periodRepository = get(),
        )
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModel {
        ExpenseViewModel(
            expenseRepository = get(),
            saveExpenseUseCase = get(),
            approveExpenseUseCase = get(),
            authRepository = get(),
            postExpenseJournalEntryUseCase = get(),
            settingsRepository = get(),
            auditLogger = get(),
            analytics = get(),
        )
    }
}
