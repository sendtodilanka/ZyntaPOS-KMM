package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Tax Group CRUD operations.
 *
 * [TaxGroup]s are referenced by products at the point of sale for tax
 * calculation. Soft-deletion is preferred over hard-deletion so that
 * historical order line items retain their original tax context.
 *
 * All write operations enqueue a sync operation for cloud replication.
 */
interface TaxGroupRepository {

    /** Emits all active [TaxGroup]s ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<TaxGroup>>

    /**
     * Returns a single [TaxGroup] by UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no tax group with that ID exists.
     */
    suspend fun getById(id: String): Result<TaxGroup>

    /**
     * Inserts a new [taxGroup].
     *
     * Validates that [TaxGroup.name] is unique before persisting.
     * Returns [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.ValidationException]
     * if a duplicate name exists.
     */
    suspend fun insert(taxGroup: TaxGroup): Result<Unit>

    /**
     * Updates all mutable fields of [taxGroup].
     *
     * Validates that the new name does not conflict with another group.
     */
    suspend fun update(taxGroup: TaxGroup): Result<Unit>

    /**
     * Soft-deletes (deactivates) the tax group identified by [id].
     *
     * Returns [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.ValidationException]
     * if any active product references this tax group.
     */
    suspend fun delete(id: String): Result<Unit>
}
