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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

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

    @Serializable
    private data class SupplierSyncPayload(
        @SerialName("id")             val id: String,
        @SerialName("name")           val name: String,
        @SerialName("contact_person") val contactPerson: String? = null,
        @SerialName("phone")          val phone: String? = null,
        @SerialName("email")          val email: String? = null,
        @SerialName("address")        val address: String? = null,
        @SerialName("notes")          val notes: String? = null,
        @SerialName("is_active")      val isActive: Boolean = true,
        @SerialName("updated_at")     val updatedAt: Long,
    )

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    // ── Sync (server-originated) ────────────────────────────────────────

    /**
     * Applies a server-authoritative supplier snapshot from a sync delta payload.
     * Does NOT enqueue a [SyncOperation] — server data must not be re-pushed.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<SupplierSyncPayload>(payload)
        val exists = q.getSupplierById(dto.id).executeAsOneOrNull() != null
        val isActive = if (dto.isActive) 1L else 0L
        val now = Clock.System.now().toEpochMilliseconds()
        if (exists) {
            q.updateSupplier(
                name = dto.name, contact_person = dto.contactPerson,
                phone = dto.phone, email = dto.email, address = dto.address,
                notes = dto.notes, is_active = isActive,
                updated_at = dto.updatedAt, sync_status = "SYNCED", id = dto.id,
            )
        } else {
            q.insertSupplier(
                id = dto.id, name = dto.name, contact_person = dto.contactPerson,
                phone = dto.phone, email = dto.email, address = dto.address,
                notes = dto.notes, is_active = isActive,
                created_at = now, updated_at = dto.updatedAt, sync_status = "SYNCED",
            )
        }
    }

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
