package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [UnitGroupRepository].
 *
 * ## Storage model
 * Units are persisted in the `units_of_measure` SQLite table. Soft-deletion
 * is used so that historical order line items retain their original unit
 * context. Before deletion the implementation checks that:
 *   1. No active product references the unit.
 *   2. The unit is not the designated base unit of its group.
 *
 * ## Base-unit promotion
 * When [insert] or [update] marks a unit as the base unit
 * ([UnitOfMeasure.isBaseUnit] = true), the implementation demotes any
 * existing base unit in the same transaction via `demoteBaseUnit`.
 *
 * ## Reactivity
 * [getAll] wraps SQLDelight's `asFlow()` on the `getAllUnits` query so any
 * local write automatically re-emits.
 *
 * ## Thread-safety
 * All suspend functions switch to [Dispatchers.IO].
 *
 * @param db            Encrypted [ZyntaDatabase] singleton, provided by Koin.
 * @param syncEnqueuer  Writes a `pending_operations` row after every mutation.
 */
class UnitGroupRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : UnitGroupRepository {

    private val q get() = db.unitsOfMeasureQueries

    // ── Reactive query ────────────────────────────────────────────────

    /**
     * Emits all [UnitOfMeasure]s ordered by base-unit first then name,
     * re-emitting on any change.
     */
    override fun getAll(): Flow<List<UnitOfMeasure>> =
        q.getAllUnits()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    UnitOfMeasure(
                        id             = row.id,
                        name           = row.name,
                        abbreviation   = row.abbreviation,
                        isBaseUnit     = row.is_base_unit == 1L,
                        conversionRate = row.conversion_rate,
                    )
                }
            }

    // ── One-shot read ─────────────────────────────────────────────────

    /**
     * Returns the [UnitOfMeasure] identified by [id].
     *
     * @return [Result.Error] with [DatabaseException] if the row is absent.
     */
    override suspend fun getById(id: String): Result<UnitOfMeasure> = withContext(Dispatchers.IO) {
        runCatching {
            q.getUnitById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("UnitOfMeasure not found: $id", operation = "getUnitById")
                )
        }.fold(
            onSuccess = { row ->
                Result.Success(
                    UnitOfMeasure(
                        id             = row.id,
                        name           = row.name,
                        abbreviation   = row.abbreviation,
                        isBaseUnit     = row.is_base_unit == 1L,
                        conversionRate = row.conversion_rate,
                    )
                )
            },
            onFailure = { t ->
                Result.Error(DatabaseException(t.message ?: "DB error", cause = t))
            },
        )
    }

    // ── Write operations ──────────────────────────────────────────────

    /**
     * Inserts a new [unit].
     *
     * Validates [UnitOfMeasure.conversionRate] > 0. If [UnitOfMeasure.isBaseUnit]
     * is true, demotes any existing base unit in the same transaction via
     * `demoteBaseUnit`.
     */
    override suspend fun insert(unit: UnitOfMeasure): Result<Unit> = withContext(Dispatchers.IO) {
        if (unit.conversionRate <= 0.0) {
            return@withContext Result.Error(
                ValidationException(
                    message = "conversionRate must be > 0, got ${unit.conversionRate}",
                    field   = "conversionRate",
                    rule    = "CONVERSION_RATE_POSITIVE",
                )
            )
        }
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                if (unit.isBaseUnit) {
                    // Demote any existing base unit before promoting this one
                    q.demoteBaseUnit(updated_at = now, id = unit.id)
                }
                q.insertUnit(
                    id              = unit.id,
                    name            = unit.name,
                    abbreviation    = unit.abbreviation,
                    is_base_unit    = if (unit.isBaseUnit) 1L else 0L,
                    conversion_rate = unit.conversionRate,
                    created_at      = now,
                    updated_at      = now,
                    deleted_at      = null,
                    sync_status     = "PENDING",
                )
                syncEnqueuer.enqueue("unit_of_measure", unit.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(t.message ?: "Insert failed", operation = "insertUnit", cause = t)
                )
            },
        )
    }

    /**
     * Updates the mutable fields of [unit].
     *
     * Base-unit promotion rules apply (same as [insert]).
     * [UnitOfMeasure.conversionRate] must be > 0; validation is enforced here.
     */
    override suspend fun update(unit: UnitOfMeasure): Result<Unit> = withContext(Dispatchers.IO) {
        if (unit.conversionRate <= 0.0) {
            return@withContext Result.Error(
                ValidationException(
                    message = "conversionRate must be > 0, got ${unit.conversionRate}",
                    field   = "conversionRate",
                    rule    = "CONVERSION_RATE_POSITIVE",
                )
            )
        }
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                if (unit.isBaseUnit) {
                    // Demote any existing base unit before promoting this one
                    q.demoteBaseUnit(updated_at = now, id = unit.id)
                }
                q.updateUnit(
                    name            = unit.name,
                    abbreviation    = unit.abbreviation,
                    is_base_unit    = if (unit.isBaseUnit) 1L else 0L,
                    conversion_rate = unit.conversionRate,
                    updated_at      = now,
                    sync_status     = "PENDING",
                    id              = unit.id,
                )
                syncEnqueuer.enqueue("unit_of_measure", unit.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(t.message ?: "Update failed", operation = "updateUnit", cause = t)
                )
            },
        )
    }

    /**
     * Soft-deletes the unit identified by [id].
     *
     * Returns [Result.Error] with [ValidationException] if:
     * - Any active product references this unit (guard via in-memory filter of
     *   `getAllProducts` — no named `getProductsByUnit` query exists in products.sq).
     * - The unit is the designated base unit of its group.
     */
    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Guard 1: reject if the unit is a base unit
            val row = q.getUnitById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("UnitOfMeasure not found: $id", operation = "softDeleteUnit")
                )
            if (row.is_base_unit == 1L) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Cannot delete base unit '$id' — demote it first",
                        field   = "isBaseUnit",
                        rule    = "BASE_UNIT_DELETE_FORBIDDEN",
                    )
                )
            }
            // Guard 2: reject if any active product references this unit
            val productCount = db.productsQueries.getAllProducts()
                .executeAsList()
                .count { it.unit_id == id }
            if (productCount > 0) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Cannot delete unit: $productCount active product(s) still assigned",
                        field   = "unitId",
                        rule    = "UNIT_IN_USE",
                    )
                )
            }
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.softDeleteUnit(
                    deleted_at = now,
                    updated_at = now,
                    id         = id,
                )
                syncEnqueuer.enqueue("unit_of_measure", id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                if (t is ValidationException) Result.Error(t)
                else Result.Error(
                    DatabaseException(t.message ?: "Delete failed", operation = "softDeleteUnit", cause = t)
                )
            },
        )
    }
}
