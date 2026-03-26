package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Budgets
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Budget
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.BudgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class BudgetRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : BudgetRepository {

    private val bq get() = db.budgetsQueries

    override suspend fun getById(id: String): Result<Budget> = withContext(Dispatchers.IO) {
        runCatching {
            bq.getBudgetById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Budget not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getByStore(storeId: String): Flow<List<Budget>> =
        bq.getBudgetsByStore(storeId).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map(::toDomain) }

    override suspend fun getByStoreAndPeriod(storeId: String, date: String): Result<List<Budget>> =
        withContext(Dispatchers.IO) {
            runCatching {
                bq.getBudgetsByStoreAndPeriod(storeId, date, date).executeAsList().map(::toDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun insert(budget: Budget): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            bq.insertBudget(
                id = budget.id,
                store_id = budget.storeId,
                category_id = budget.categoryId,
                period_start = budget.periodStart,
                period_end = budget.periodEnd,
                budget_amount = budget.budgetAmount,
                spent_amount = budget.spentAmount,
                name = budget.name,
                created_at = now,
                updated_at = now,
                sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.BUDGET, budget.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updateSpent(id: String, spentAmount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            bq.updateBudgetSpent(spent_amount = spentAmount, updated_at = now, id = id)
            syncEnqueuer.enqueue(SyncOperation.EntityType.BUDGET, id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { bq.deleteBudget(id) }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    private fun toDomain(row: Budgets) = Budget(
        id = row.id,
        storeId = row.store_id,
        categoryId = row.category_id,
        periodStart = row.period_start,
        periodEnd = row.period_end,
        budgetAmount = row.budget_amount,
        spentAmount = row.spent_amount,
        name = row.name,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
