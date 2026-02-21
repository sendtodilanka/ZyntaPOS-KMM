package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Supplier
import kotlinx.coroutines.flow.Flow

/**
 * Contract for supplier CRUD operations.
 *
 * Suppliers are referenced on purchase orders and goods-received notes.
 * Soft-deletion is preferred over hard-deletion to preserve historical records.
 */
interface SupplierRepository {

    /** Emits the full list of active suppliers ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<Supplier>>

    /**
     * Returns a single [Supplier] by UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no supplier with that ID exists.
     */
    suspend fun getById(id: String): Result<Supplier>

    /** Inserts a new [supplier] and enqueues a sync operation. */
    suspend fun insert(supplier: Supplier): Result<Unit>

    /** Persists all mutable fields of [supplier] and enqueues a sync operation. */
    suspend fun update(supplier: Supplier): Result<Unit>

    /**
     * Soft-deletes the supplier identified by [id] (`is_active = false`).
     *
     * The data layer must verify no pending purchase orders reference this supplier
     * and return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.ValidationException] if they do.
     */
    suspend fun delete(id: String): Result<Unit>
}
