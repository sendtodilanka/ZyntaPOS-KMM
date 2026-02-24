package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

/**
 * Contract for expense management — CRUD, approval workflow, and recurring schedules.
 */
interface ExpenseRepository {

    // ── Expenses ──────────────────────────────────────────────────────────────

    /** Emits all expense records, most recent first. Re-emits on change. */
    fun getAll(): Flow<List<Expense>>

    /** Emits expenses filtered by [status]. */
    fun getByStatus(status: Expense.Status): Flow<List<Expense>>

    /** Emits expenses with expense_date within [[from], [to]] epoch millis. */
    fun getByDateRange(from: Long, to: Long): Flow<List<Expense>>

    /** Returns a single expense by [id]. */
    suspend fun getById(id: String): Result<Expense>

    /** Returns the total approved expense amount within a date range. */
    suspend fun getTotalByPeriod(from: Long, to: Long): Result<Double>

    /** Inserts a new expense and enqueues a sync operation. */
    suspend fun insert(expense: Expense): Result<Unit>

    /** Updates a pending expense (only allowed before approval). */
    suspend fun update(expense: Expense): Result<Unit>

    /** Approves a pending expense. Sets status = APPROVED. */
    suspend fun approve(id: String, approvedBy: String): Result<Unit>

    /** Rejects a pending expense with an optional [reason]. Sets status = REJECTED. */
    suspend fun reject(id: String, rejectedBy: String, reason: String? = null): Result<Unit>

    /** Hard-deletes an expense record. Only valid for PENDING expenses. */
    suspend fun delete(id: String): Result<Unit>

    // ── Expense Categories ────────────────────────────────────────────────────

    /** Emits all expense categories ordered by name. Re-emits on change. */
    fun getAllCategories(): Flow<List<ExpenseCategory>>

    /** Returns a category by [id]. */
    suspend fun getCategoryById(id: String): Result<ExpenseCategory>

    /** Inserts or updates an expense category. */
    suspend fun saveCategory(category: ExpenseCategory): Result<Unit>

    /** Hard-deletes a category. Fails if expenses reference it. */
    suspend fun deleteCategory(id: String): Result<Unit>

    // ── Recurring Expenses ────────────────────────────────────────────────────

    /** Emits all recurring expense rules. Re-emits on change. */
    fun getAllRecurring(): Flow<List<RecurringExpense>>

    /** Returns all active recurring rules due for generation. */
    suspend fun getActiveRecurring(): Result<List<RecurringExpense>>

    /** Inserts or updates a recurring expense rule. */
    suspend fun saveRecurring(recurring: RecurringExpense): Result<Unit>

    /** Marks [lastRun] on a recurring rule after an expense is generated. */
    suspend fun updateLastRun(id: String, lastRunMillis: Long): Result<Unit>

    /** Hard-deletes a recurring rule. */
    suspend fun deleteRecurring(id: String): Result<Unit>
}
