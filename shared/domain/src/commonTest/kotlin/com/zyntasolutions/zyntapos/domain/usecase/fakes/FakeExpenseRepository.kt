package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Expense Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildExpense(
    id: String = "exp-01",
    description: String = "Office Supplies",
    amount: Double = 500.0,
    status: Expense.Status = Expense.Status.PENDING,
    categoryId: String? = null,
    createdBy: String? = "user-01",
    approvedBy: String? = null,
    expenseDate: Long = Clock.System.now().toEpochMilliseconds(),
) = Expense(
    id = id, description = description, amount = amount,
    status = status, categoryId = categoryId, createdBy = createdBy,
    approvedBy = approvedBy, expenseDate = expenseDate,
)

fun buildExpenseCategory(
    id: String = "cat-01",
    name: String = "Office",
    parentId: String? = null,
) = ExpenseCategory(id = id, name = name, parentId = parentId)

// ─────────────────────────────────────────────────────────────────────────────
// Fake ExpenseRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [ExpenseRepository].
 */
class FakeExpenseRepository : ExpenseRepository {
    val expenses = mutableListOf<Expense>()
    val categories = mutableListOf<ExpenseCategory>()
    val recurring = mutableListOf<RecurringExpense>()
    var shouldFail = false

    private val _expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    private val _categoriesFlow = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    private val _recurringFlow = MutableStateFlow<List<RecurringExpense>>(emptyList())

    override fun getAll(): Flow<List<Expense>> = _expensesFlow

    override fun getByStatus(status: Expense.Status): Flow<List<Expense>> =
        MutableStateFlow(expenses.filter { it.status == status })

    override fun getByDateRange(from: Long, to: Long): Flow<List<Expense>> =
        MutableStateFlow(expenses.filter { it.expenseDate in from..to })

    override suspend fun getById(id: String): Result<Expense> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return expenses.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Expense not found: $id"))
    }

    override suspend fun getTotalByPeriod(from: Long, to: Long): Result<Double> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val total = expenses
            .filter { it.status == Expense.Status.APPROVED && it.expenseDate in from..to }
            .sumOf { it.amount }
        return Result.Success(total)
    }

    override suspend fun insert(expense: Expense): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        expenses.add(expense)
        _expensesFlow.value = expenses.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(expense: Expense): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        expenses.removeAll { it.id == expense.id }
        expenses.add(expense)
        _expensesFlow.value = expenses.toList()
        return Result.Success(Unit)
    }

    override suspend fun approve(id: String, approvedBy: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Expense not found: $id"))
        expenses[idx] = expenses[idx].copy(
            status = Expense.Status.APPROVED,
            approvedBy = approvedBy,
            approvedAt = Clock.System.now().toEpochMilliseconds(),
        )
        _expensesFlow.value = expenses.toList()
        return Result.Success(Unit)
    }

    override suspend fun reject(id: String, rejectedBy: String, reason: String?): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Expense not found: $id"))
        expenses[idx] = expenses[idx].copy(
            status = Expense.Status.REJECTED,
            approvedBy = rejectedBy,
            rejectReason = reason,
        )
        _expensesFlow.value = expenses.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        expenses.removeAll { it.id == id }
        _expensesFlow.value = expenses.toList()
        return Result.Success(Unit)
    }

    override fun getAllCategories(): Flow<List<ExpenseCategory>> = _categoriesFlow

    override suspend fun getCategoryById(id: String): Result<ExpenseCategory> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return categories.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Category not found: $id"))
    }

    override suspend fun saveCategory(category: ExpenseCategory): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        categories.removeAll { it.id == category.id }
        categories.add(category)
        _categoriesFlow.value = categories.toList()
        return Result.Success(Unit)
    }

    override suspend fun deleteCategory(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        categories.removeAll { it.id == id }
        _categoriesFlow.value = categories.toList()
        return Result.Success(Unit)
    }

    override fun getAllRecurring(): Flow<List<RecurringExpense>> = _recurringFlow

    override suspend fun getActiveRecurring(): Result<List<RecurringExpense>> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(recurring.filter { it.isActive })
    }

    override suspend fun saveRecurring(r: RecurringExpense): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        recurring.removeAll { it.id == r.id }
        recurring.add(r)
        _recurringFlow.value = recurring.toList()
        return Result.Success(Unit)
    }

    override suspend fun updateLastRun(id: String, lastRunMillis: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = recurring.indexOfFirst { it.id == id }
        if (idx >= 0) recurring[idx] = recurring[idx].copy(lastRun = lastRunMillis)
        return Result.Success(Unit)
    }

    override suspend fun deleteRecurring(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        recurring.removeAll { it.id == id }
        _recurringFlow.value = recurring.toList()
        return Result.Success(Unit)
    }
}
