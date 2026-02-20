package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.SupplierMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [SupplierRepository].
 *
 * Standard CRUD; soft-delete preserves historical procurement references.
 * No FTS5 — supplier list is small enough for SQLite LIKE-based search
 * (already implemented in `searchSuppliers` query in `suppliers.sq`).
 */
class SupplierRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : SupplierRepository {

    private val q get() = db.suppliersQueries

    override fun getAll(): Flow<List<Supplier>> =
        q.getAllSuppliers()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(SupplierMapper::toDomain) }

    override suspend fun getById(id: String): Result<Supplier> = withContext(Dispatchers.IO) {
        runCatching {
            q.getSupplierById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Supplier not found: $id", operation = "getSupplierById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(SupplierMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(supplier: Supplier): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = SupplierMapper.toInsertParams(supplier)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertSupplier(
                    id = p.id, name = p.name, contact_person = p.contactPerson,
                    phone = p.phone, email = p.email, address = p.address,
                    notes = p.notes, is_active = p.isActive,
                    created_at = now, updated_at = now, sync_status = p.syncStatus,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.SUPPLIER, p.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(supplier: Supplier): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = SupplierMapper.toInsertParams(supplier)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateSupplier(
                    name = p.name, contact_person = p.contactPerson,
                    phone = p.phone, email = p.email, address = p.address,
                    notes = p.notes, is_active = p.isActive,
                    updated_at = now, sync_status = "PENDING", id = p.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.SUPPLIER, p.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val row = q.getSupplierById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Supplier not found: $id", operation = "deleteSupplier")
                )
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateSupplier(
                    name = row.name, contact_person = row.contact_person,
                    phone = row.phone, email = row.email, address = row.address,
                    notes = row.notes, is_active = 0L,
                    updated_at = now, sync_status = "PENDING", id = id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.SUPPLIER, id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }
}
