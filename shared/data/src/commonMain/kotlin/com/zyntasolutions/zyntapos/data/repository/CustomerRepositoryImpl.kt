package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.CustomerMapper
import com.zyntasolutions.zyntapos.data.remote.dto.CustomerDto
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Concrete implementation of [CustomerRepository].
 *
 * FTS5 search via `searchCustomers` matches on name, phone, and email —
 * each token gets a prefix wildcard (e.g. "john*") for partial match support.
 * Soft-delete sets `is_active = 0` to preserve order history integrity.
 */
class CustomerRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CustomerRepository {

    private val q get() = db.customersQueries

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    override fun getAll(): Flow<List<Customer>> =
        q.getAllCustomers()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(CustomerMapper::toDomain) }

    override suspend fun getById(id: String): Result<Customer> = withContext(Dispatchers.IO) {
        runCatching {
            q.getCustomerById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Customer not found: $id", operation = "getCustomerById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(CustomerMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    // ── Sync (server-originated) ────────────────────────────────────────

    /**
     * Applies a server-authoritative customer snapshot from a sync delta payload.
     *
     * Fields not present in [CustomerDto] (credit_limit, gender, etc.) are preserved
     * from the existing local row on update, or defaulted to null/false on insert.
     * Does NOT enqueue a [SyncOperation] — server data must not be re-pushed.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<CustomerDto>(payload)
        val existing = q.getCustomerById(dto.id).executeAsOneOrNull()
        val isActive = if (dto.isActive) 1L else 0L
        val loyaltyPoints = dto.loyaltyPoints.toLong()
        if (existing != null) {
            // Preserve extended fields from local row; server sends core fields only
            q.updateCustomer(
                name = dto.name, phone = dto.phone, email = dto.email,
                address = dto.address, group_id = dto.groupId,
                loyalty_points = loyaltyPoints, notes = dto.notes,
                is_active = isActive,
                credit_limit = existing.credit_limit,
                credit_enabled = existing.credit_enabled,
                gender = existing.gender, birthday = existing.birthday,
                is_walk_in = existing.is_walk_in, store_id = existing.store_id,
                updated_at = dto.updatedAt, sync_status = "SYNCED", id = dto.id,
            )
        } else {
            val now = Clock.System.now().toEpochMilliseconds()
            q.insertCustomer(
                id = dto.id, name = dto.name, phone = dto.phone, email = dto.email,
                address = dto.address, group_id = dto.groupId,
                loyalty_points = loyaltyPoints, notes = dto.notes,
                is_active = isActive,
                credit_limit = null, credit_enabled = 0L,
                gender = null, birthday = null,
                is_walk_in = 0L, store_id = null,
                created_at = now, updated_at = dto.updatedAt,
                sync_status = "SYNCED",
            )
        }
    }

    override fun search(query: String): Flow<List<Customer>> =
        if (query.isBlank()) getAll()
        else {
            val ftsQuery = toFtsQuery(query)
            q.searchCustomers(ftsQuery)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(CustomerMapper::toDomain) }
        }

    /**
     * Converts a user-typed search string into an FTS5 query with prefix matching.
     * "john sm" → "john* sm*"
     */
    private fun toFtsQuery(raw: String): String =
        raw.trim()
            .replace("\"", "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }

    override suspend fun insert(customer: Customer): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = CustomerMapper.toInsertParams(customer)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertCustomer(
                    id = p.id, name = p.name, phone = p.phone, email = p.email,
                    address = p.address, group_id = p.groupId,
                    loyalty_points = p.loyaltyPoints, notes = p.notes,
                    is_active = p.isActive,
                    credit_limit = p.creditLimit, credit_enabled = p.creditEnabled,
                    gender = p.gender, birthday = p.birthday,
                    is_walk_in = p.isWalkIn, store_id = p.storeId,
                    created_at = now, updated_at = now,
                    sync_status = p.syncStatus,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER, p.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(customer: Customer): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = CustomerMapper.toInsertParams(customer)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateCustomer(
                    name = p.name, phone = p.phone, email = p.email,
                    address = p.address, group_id = p.groupId,
                    loyalty_points = p.loyaltyPoints, notes = p.notes,
                    is_active = p.isActive,
                    credit_limit = p.creditLimit, credit_enabled = p.creditEnabled,
                    gender = p.gender, birthday = p.birthday,
                    is_walk_in = p.isWalkIn, store_id = p.storeId,
                    updated_at = now, sync_status = "PENDING", id = p.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER, p.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val row = q.getCustomerById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Customer not found: $id", operation = "deleteCustomer")
                )
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateCustomer(
                    name = row.name, phone = row.phone, email = row.email,
                    address = row.address, group_id = row.group_id,
                    loyalty_points = row.loyalty_points, notes = row.notes,
                    is_active = 0L,
                    credit_limit = row.credit_limit, credit_enabled = row.credit_enabled,
                    gender = row.gender, birthday = row.birthday,
                    is_walk_in = row.is_walk_in, store_id = row.store_id,
                    updated_at = now, sync_status = "PENDING", id = id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER, id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }
}
