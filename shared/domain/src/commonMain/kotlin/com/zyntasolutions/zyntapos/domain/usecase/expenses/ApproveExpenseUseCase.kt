package com.zyntasolutions.zyntapos.domain.usecase.expenses

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository

/**
 * Approves or rejects a [Expense.Status.PENDING] expense.
 *
 * Only PENDING expenses can be approved or rejected. Already approved/rejected
 * expenses return a [ValidationException].
 */
class ApproveExpenseUseCase(
    private val repo: ExpenseRepository,
) {
    suspend fun approve(id: String, approvedBy: String): Result<Unit> {
        val expense = when (val r = repo.getById(id)) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        if (expense.status != Expense.Status.PENDING) {
            return Result.Error(ValidationException("Expense is already ${expense.status}"))
        }
        return repo.approve(id, approvedBy)
    }

    suspend fun reject(id: String, rejectedBy: String, reason: String?): Result<Unit> {
        val expense = when (val r = repo.getById(id)) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        if (expense.status != Expense.Status.PENDING) {
            return Result.Error(ValidationException("Expense is already ${expense.status}"))
        }
        return repo.reject(id, rejectedBy, reason)
    }
}
