package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.BudgetRepository

/**
 * Adds a spending amount to an existing budget's [spentAmount].
 *
 * Validates that the amount is positive, loads the current budget,
 * and persists the updated spent total.
 *
 * @param budgetRepository Source of budget data.
 */
class TrackBudgetSpendingUseCase(
    private val budgetRepository: BudgetRepository,
) {

    /**
     * @param budgetId The budget to update.
     * @param amount   The additional spending amount to record (must be > 0).
     * @return [Result.Success] on success, [Result.Error] on validation or DB failure.
     */
    suspend operator fun invoke(budgetId: String, amount: Double): Result<Unit> {
        if (amount <= 0.0) {
            return Result.Error(
                ValidationException(
                    message = "Spending amount must be positive",
                    field = "amount",
                    rule = "POSITIVE",
                ),
            )
        }

        return when (val budgetResult = budgetRepository.getById(budgetId)) {
            is Result.Success -> {
                val budget = budgetResult.data
                val newSpent = budget.spentAmount + amount
                budgetRepository.updateSpent(budgetId, newSpent)
            }
            is Result.Error -> budgetResult
            is Result.Loading -> Result.Loading
        }
    }
}
