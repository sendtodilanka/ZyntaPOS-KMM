package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [TaxGroupRepository].
 *
 * ## Storage model
 * Tax groups are persisted in the `tax_groups` SQLite table. Soft-deletion is
 * used so that historical order line items retain their original tax context.
 * Before deletion the implementation checks that no active product references
 * the group (validation guard).
 *
 * ## Reactivity
 * [getAll] wraps SQLDelight's `asFlow()` on the `getAllTaxGroups` query so any
 * local write (insert / update / soft-delete) automatically re-emits.
 *
 * ## Thread-safety
 * All suspend functions switch to [Dispatchers.IO]. [getAll] emits on [Dispatchers.IO]
 * via `mapToList(Dispatchers.IO)`.
 *
 * @param db            Encrypted [ZyntaDatabase] singleton, provided by Koin.
 * @param syncEnqueuer  Writes a `pending_operations` row after every mutation.
 */
class TaxGroupRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : TaxGroupRepository {

    private val q get() = db.tax_groupsQueries

    // ── Reactive query ────────────────────────────────────────────────

    override fun getAll(): Flow<List<TaxGroup>> =
        q.getAllTaxGroups()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    TaxGroup(
                        id          = row.id,
                        name        = row.name,
                        rate        = row.rate,
                        isInclusive = row.is_inclusive == 1L,
                        isActive    = row.is_active == 1L,
                    )
                }
            }

    // ── One-shot read ─────────────────────────────────────────────────

    override suspend fun getById(id: String): Result<TaxGroup> = withContext(Dispatchers.IO) {
        runCatching {
            q.getTaxGroupById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("TaxGroup not found: $id", operation = "getTaxGroupById")
                )
        }.fold(
            onSuccess = { row ->
                Result.Success(
                    TaxGroup(
                        id          = row.id,
                        name        = row.name,
                        rate        = row.rate,
                        isInclusive = row.is_inclusive == 1L,
                        isActive    = row.is_active == 1L,
                    )
                )
            },
            onFailure = { t ->
                Result.Error(DatabaseException(t.message ?: "DB error", cause = t))
            },
        )
    }

    // ── Write operations ──────────────────────────────────────────────

    override suspend fun insert(taxGroup: TaxGroup): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertTaxGroup(
                    id           = taxGroup.id,
                    name         = taxGroup.name,
                    rate         = taxGroup.rate,
                    is_inclusive = if (taxGroup.isInclusive) 1L else 0L,
                    is_active    = if (taxGroup.isActive) 1L else 0L,
                    created_at   = now,
                    updated_at   = now,
                    deleted_at   = null,
                    sync_status  = "PENDING",
                )
                syncEnqueuer.enqueue("tax_group", taxGroup.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(t.message ?: "Insert failed", operation = "insertTaxGroup", cause = t)
                )
            },
        )
    }

    override suspend fun update(taxGroup: TaxGroup): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateTaxGroup(
                    name         = taxGroup.name,
                    rate         = taxGroup.rate,
                    is_inclusive = if (taxGroup.isInclusive) 1L else 0L,
                    is_active    = if (taxGroup.isActive) 1L else 0L,
                    updated_at   = now,
                    sync_status  = "PENDING",
                    id           = taxGroup.id,
                )
                syncEnqueuer.enqueue("tax_group", taxGroup.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(t.message ?: "Update failed", operation = "updateTaxGroup", cause = t)
                )
            },
        )
    }

    /**
     * Soft-deletes the tax group identified by [id].
     *
     * Guards against deleting a group still referenced by active products.
     * The product list is fetched via [db.productsQueries.getAllProducts] and
     * filtered in Kotlin because no named `getProductsByTaxGroup` query exists
     * in products.sq — avoids adding new SQL per the implementation contract.
     */
    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Guard: reject if any active product references this tax group
            val productCount = db.productsQueries.getAllProducts()
                .executeAsList()
                .count { it.tax_group_id == id }
            if (productCount > 0) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Cannot delete tax group: $productCount active product(s) still assigned",
                        field   = "taxGroupId",
                        rule    = "TAX_GROUP_IN_USE",
                    )
                )
            }
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.softDeleteTaxGroup(
                    deleted_at = now,
                    updated_at = now,
                    id         = id,
                )
                syncEnqueuer.enqueue("tax_group", id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                if (t is ValidationException) Result.Error(t)
                else Result.Error(
                    DatabaseException(t.message ?: "Delete failed", operation = "softDeleteTaxGroup", cause = t)
                )
            },
        )
    }
}
