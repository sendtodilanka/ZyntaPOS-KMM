package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import kotlinx.coroutines.flow.Flow

/**
 * Contract for customer profile CRUD and FTS-backed search.
 *
 * [Customer] records are never hard-deleted to preserve order history integrity.
 * [delete] performs a soft-delete (`is_active = false`).
 */
interface CustomerRepository {

    /** Emits the full list of active customers, ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<Customer>>

    /**
     * Returns a single [Customer] by UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no customer with that ID exists.
     */
    suspend fun getById(id: String): Result<Customer>

    /**
     * Emits customers whose name, phone, or email match [query] (FTS5).
     *
     * Passing an empty [query] is equivalent to [getAll].
     * Re-emits on any write that affects the matching rows.
     */
    fun search(query: String): Flow<List<Customer>>

    /** Inserts a new [customer] and enqueues a sync operation. */
    suspend fun insert(customer: Customer): Result<Unit>

    /** Persists all mutable fields of [customer] and enqueues a sync operation. */
    suspend fun update(customer: Customer): Result<Unit>

    /**
     * Soft-deletes the customer identified by [id] (`is_active = false`).
     *
     * Existing order records referencing this customer remain unaffected.
     */
    suspend fun delete(id: String): Result<Unit>

    // ── C4.3: Cross-Store Customer Operations ───────────────────────────────

    /**
     * Emits customers whose name, phone, or email match [query] across ALL stores.
     *
     * Unlike [search], this includes inactive customers and ignores store scope.
     * Intended for admin/support cross-store customer lookup.
     */
    fun searchGlobal(query: String): Flow<List<Customer>>

    /**
     * Returns customers belonging to a specific [storeId].
     */
    fun getByStore(storeId: String): Flow<List<Customer>>

    /**
     * Returns customers with no store assignment (global/shared customers).
     */
    fun getGlobalCustomers(): Flow<List<Customer>>

    /**
     * Promotes a store-specific customer to a global customer by clearing store_id.
     */
    suspend fun makeGlobal(customerId: String): Result<Unit>

    /**
     * Updates the loyalty points for a customer.
     * Used during customer merge operations.
     */
    suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit>
}
