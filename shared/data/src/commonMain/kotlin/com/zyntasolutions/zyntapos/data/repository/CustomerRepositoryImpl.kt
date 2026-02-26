package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.CustomerMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
