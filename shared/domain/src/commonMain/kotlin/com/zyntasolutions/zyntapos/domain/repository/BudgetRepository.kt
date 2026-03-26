package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Contract for budget management — CRUD and spend tracking per store/category.
 */
interface BudgetRepository {

    /** Returns a single budget by [id]. */
    suspend fun getById(id: String): Result<Budget>

    /** Emits all budgets for a [storeId], most recent period first. Re-emits on change. */
    fun getByStore(storeId: String): Flow<List<Budget>>

    /** Returns budgets for a store that overlap the given [date] (ISO-8601 date string). */
    suspend fun getByStoreAndPeriod(storeId: String, date: String): Result<List<Budget>>

    /** Inserts a new budget record and enqueues a sync operation. */
    suspend fun insert(budget: Budget): Result<Unit>

    /** Updates the spent amount on an existing budget. */
    suspend fun updateSpent(id: String, spentAmount: Double): Result<Unit>

    /** Hard-deletes a budget record. */
    suspend fun delete(id: String): Result<Unit>
}
