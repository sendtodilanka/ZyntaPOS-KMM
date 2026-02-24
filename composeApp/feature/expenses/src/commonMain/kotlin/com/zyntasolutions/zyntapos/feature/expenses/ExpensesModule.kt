package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.expenses.ApproveExpenseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.SaveExpenseUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:expenses`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [SaveExpenseUseCase]    — validates and persists expenses
 * - [ApproveExpenseUseCase] — approve/reject workflow
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `ExpenseRepository` — expense CRUD, approval, category management
 * - `AuthRepository`    — session resolver for currentUserId
 */
val expensesModule = module {
    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::SaveExpenseUseCase)
    factoryOf(::ApproveExpenseUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModel {
        val userId = runBlocking { get<AuthRepository>().getSession().first()?.id ?: "unknown" }
        ExpenseViewModel(
            expenseRepository = get(),
            saveExpenseUseCase = get(),
            approveExpenseUseCase = get(),
            currentUserId = userId,
        )
    }
}
