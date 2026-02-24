package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Expense_categories
import com.zyntasolutions.zyntapos.db.Expenses
import com.zyntasolutions.zyntapos.db.Recurring_expenses
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ExpenseRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : ExpenseRepository {

    private val eq get() = db.expensesQueries
    private val ecq get() = db.expense_categoriesQueries
    private val req get() = db.expensesQueries

    override fun getAll(): Flow<List<Expense>> =
        eq.getAllExpenses().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toExpenseDomain) }

    override fun getByStatus(status: Expense.Status): Flow<List<Expense>> =
        eq.getExpensesByStatus(status.name).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toExpenseDomain) }

    override fun getByDateRange(from: Long, to: Long): Flow<List<Expense>> =
        eq.getExpensesByDateRange(from, to).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toExpenseDomain) }

    override suspend fun getById(id: String): Result<Expense> = withContext(Dispatchers.IO) {
        runCatching {
            eq.getExpenseById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Expense not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toExpenseDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getTotalByPeriod(from: Long, to: Long): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            eq.getTotalExpensesByPeriod(from, to).executeAsOne()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(expense: Expense): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            eq.insertExpense(
                id = expense.id, store_id = expense.storeId, category_id = expense.categoryId,
                amount = expense.amount, description = expense.description,
                expense_date = expense.expenseDate, receipt_url = expense.receiptUrl,
                is_recurring = if (expense.isRecurring) 1L else 0L,
                status = expense.status.name, created_by = expense.createdBy,
                created_at = now, updated_at = now, sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.EXPENSE, expense.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(expense: Expense): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            eq.updateExpense(
                category_id = expense.categoryId, amount = expense.amount,
                description = expense.description, expense_date = expense.expenseDate,
                receipt_url = expense.receiptUrl, updated_at = now, sync_status = "PENDING", id = expense.id,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.EXPENSE, expense.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun approve(id: String, approvedBy: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            eq.approveExpense(approved_by = approvedBy, approved_at = now, updated_at = now, id = id)
            syncEnqueuer.enqueue(SyncOperation.EntityType.EXPENSE, id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Approve failed", cause = t)) },
        )
    }

    override suspend fun reject(id: String, rejectedBy: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            eq.rejectExpense(reject_reason = reason, approved_by = rejectedBy, approved_at = now, updated_at = now, id = id)
            syncEnqueuer.enqueue(SyncOperation.EntityType.EXPENSE, id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Reject failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { eq.deleteExpense(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    override fun getAllCategories(): Flow<List<ExpenseCategory>> =
        ecq.getAllExpenseCategories().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toCategoryDomain) }

    override suspend fun getCategoryById(id: String): Result<ExpenseCategory> = withContext(Dispatchers.IO) {
        runCatching {
            ecq.getExpenseCategoryById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("ExpenseCategory not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toCategoryDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun saveCategory(category: ExpenseCategory): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = ecq.getExpenseCategoryById(category.id).executeAsOneOrNull()
            val now = Clock.System.now().toEpochMilliseconds()
            if (existing == null) {
                ecq.insertExpenseCategory(
                    id = category.id, name = category.name,
                    description = category.description, parent_id = category.parentId,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
            } else {
                ecq.updateExpenseCategory(
                    name = category.name, description = category.description,
                    parent_id = category.parentId, updated_at = now, sync_status = "PENDING", id = category.id,
                )
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.EXPENSE_CATEGORY, category.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Save failed", cause = t)) },
        )
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { ecq.deleteExpenseCategory(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    override fun getAllRecurring(): Flow<List<RecurringExpense>> =
        req.getAllRecurringExpenses().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toRecurringDomain) }

    override suspend fun getActiveRecurring(): Result<List<RecurringExpense>> = withContext(Dispatchers.IO) {
        runCatching {
            req.getActiveRecurringExpenses().executeAsList().map(::toRecurringDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun saveRecurring(recurring: RecurringExpense): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = req.getRecurringExpenseById(recurring.id).executeAsOneOrNull()
            val now = Clock.System.now().toEpochMilliseconds()
            if (existing == null) {
                req.insertRecurringExpense(
                    id = recurring.id, store_id = recurring.storeId, category_id = recurring.categoryId,
                    amount = recurring.amount, description = recurring.description,
                    frequency = recurring.frequency.name, start_date = recurring.startDate,
                    end_date = recurring.endDate, is_active = if (recurring.isActive) 1L else 0L,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
            } else {
                req.updateRecurringExpense(
                    category_id = recurring.categoryId, amount = recurring.amount,
                    description = recurring.description, frequency = recurring.frequency.name,
                    end_date = recurring.endDate, is_active = if (recurring.isActive) 1L else 0L,
                    last_run = recurring.lastRun, updated_at = now, sync_status = "PENDING", id = recurring.id,
                )
            }
            syncEnqueuer.enqueue(SyncOperation.EntityType.RECURRING_EXPENSE, recurring.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Save failed", cause = t)) },
        )
    }

    override suspend fun updateLastRun(id: String, lastRunMillis: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = req.getRecurringExpenseById(id).executeAsOneOrNull() ?: return@withContext Result.Error(DatabaseException("Not found: $id"))
            val now = Clock.System.now().toEpochMilliseconds()
            req.updateRecurringExpense(
                category_id = existing.category_id, amount = existing.amount,
                description = existing.description, frequency = existing.frequency,
                end_date = existing.end_date, is_active = existing.is_active,
                last_run = lastRunMillis, updated_at = now, sync_status = "PENDING", id = id,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun deleteRecurring(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { req.deleteRecurringExpense(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    private fun toExpenseDomain(row: Expenses) = Expense(
        id = row.id, storeId = row.store_id, categoryId = row.category_id,
        amount = row.amount, description = row.description, expenseDate = row.expense_date,
        receiptUrl = row.receipt_url, isRecurring = row.is_recurring == 1L,
        status = runCatching { Expense.Status.valueOf(row.status) }.getOrDefault(Expense.Status.PENDING),
        approvedBy = row.approved_by, approvedAt = row.approved_at,
        rejectReason = row.reject_reason, createdBy = row.created_by,
    )

    private fun toCategoryDomain(row: Expense_categories) = ExpenseCategory(
        id = row.id, name = row.name, description = row.description, parentId = row.parent_id,
    )

    private fun toRecurringDomain(row: Recurring_expenses) = RecurringExpense(
        id = row.id, storeId = row.store_id, categoryId = row.category_id,
        amount = row.amount, description = row.description,
        frequency = runCatching { RecurringExpense.Frequency.valueOf(row.frequency) }.getOrDefault(RecurringExpense.Frequency.MONTHLY),
        startDate = row.start_date, endDate = row.end_date,
        isActive = row.is_active == 1L, lastRun = row.last_run,
    )
}
