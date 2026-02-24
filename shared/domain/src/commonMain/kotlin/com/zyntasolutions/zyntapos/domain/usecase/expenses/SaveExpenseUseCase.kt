package com.zyntasolutions.zyntapos.domain.usecase.expenses

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository

/**
 * Inserts or updates an [Expense] record after field validation.
 */
class SaveExpenseUseCase(
    private val repo: ExpenseRepository,
) {
    suspend operator fun invoke(expense: Expense, isNew: Boolean): Result<Unit> {
        if (expense.description.isBlank()) {
            return Result.Error(ValidationException("Expense description cannot be blank"))
        }
        if (expense.amount <= 0.0) {
            return Result.Error(ValidationException("Expense amount must be positive"))
        }
        return if (isNew) repo.insert(expense) else repo.update(expense)
    }
}
