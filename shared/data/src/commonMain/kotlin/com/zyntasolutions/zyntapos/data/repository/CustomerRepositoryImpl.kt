package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.CustomerMapper
import com.zyntasolutions.zyntapos.data.remote.dto.CustomerDto
import com.zyntasolutions.zyntapos.data.util.SyncJson
import com.zyntasolutions.zyntapos.data.util.dbCall
import com.zyntasolutions.zyntapos.data.util.toFtsQuery
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

    override suspend fun getById(id: String): Result<Customer> = dbCall("getById") {
        val row = q.getCustomerById(id).executeAsOneOrNull()
            ?: throw DatabaseException("Customer not found: $id", operation = "getCustomerById")
        CustomerMapper.toDomain(row)
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
        val dto = SyncJson.decodeFromString<CustomerDto>(payload)
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
                credit_limit = 0.0, credit_enabled = 0L,
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
            q.searchCustomers(query.toFtsQuery())
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(CustomerMapper::toDomain) }
        }

    override suspend fun insert(customer: Customer): Result<Unit> = dbCall("insertCustomer") {
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
    }

    override suspend fun update(customer: Customer): Result<Unit> = dbCall("updateCustomer") {
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
    }

    // ── C4.3: Cross-Store Customer Operations ─────────────────────────────

    override fun searchGlobal(query: String): Flow<List<Customer>> =
        if (query.isBlank()) {
            q.getAllCustomers()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(CustomerMapper::toDomain) }
        } else {
            q.searchCustomersGlobal(query.toFtsQuery())
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map(CustomerMapper::toDomain) }
        }

    override fun getByStore(storeId: String): Flow<List<Customer>> =
        q.getCustomersByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(CustomerMapper::toDomain) }

    override fun getGlobalCustomers(): Flow<List<Customer>> =
        q.getGlobalCustomers()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(CustomerMapper::toDomain) }

    override suspend fun makeGlobal(customerId: String): Result<Unit> = dbCall("makeCustomerGlobal") {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            q.makeCustomerGlobal(updated_at = now, id = customerId)
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER, customerId, SyncOperation.Operation.UPDATE)
        }
    }

    override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> = dbCall("updateLoyaltyPoints") {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            q.updateLoyaltyPoints(
                loyalty_points = points.toLong(),
                updated_at = now,
                id = customerId,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER, customerId, SyncOperation.Operation.UPDATE)
        }
    }

    // ── Paginated ──────────────────────────────────────────────────────────────

    override suspend fun getPage(
        pageRequest: PageRequest,
        searchQuery: String?,
    ): PaginatedResult<Customer> = withContext(Dispatchers.IO) {
        val limit = pageRequest.limit.toLong()
        val offset = pageRequest.offset.toLong()

        val (rows, totalCount) = if (searchQuery != null && searchQuery.isNotBlank()) {
            val ftsQuery = searchQuery.toFtsQuery()
            val items = q.searchCustomersPage(ftsQuery, limit, offset).executeAsList()
            val count = q.countSearchCustomers(ftsQuery).executeAsOne()
            items to count
        } else {
            val items = q.getCustomersPage(limit, offset).executeAsList()
            val count = q.countCustomers().executeAsOne()
            items to count
        }

        PaginatedResult(
            items = rows.map(CustomerMapper::toDomain),
            totalCount = totalCount,
            hasMore = (pageRequest.offset + rows.size) < totalCount,
        )
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    override suspend fun delete(id: String): Result<Unit> = dbCall("deleteCustomer") {
        val row = q.getCustomerById(id).executeAsOneOrNull()
            ?: throw DatabaseException("Customer not found: $id", operation = "deleteCustomer")
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
    }
}
