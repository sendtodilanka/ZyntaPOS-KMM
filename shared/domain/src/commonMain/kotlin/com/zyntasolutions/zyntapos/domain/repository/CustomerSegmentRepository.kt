package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import kotlinx.coroutines.flow.Flow

/**
 * Contract for customer segment CRUD operations.
 *
 * Segments define rule-based criteria for grouping customers by purchase behaviour,
 * loyalty tier, and demographics — used for targeted promotions and analytics.
 */
interface CustomerSegmentRepository {

    /** Emits all segments ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<CustomerSegment>>

    /**
     * Returns a single [CustomerSegment] by its [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no segment with that ID exists.
     */
    suspend fun getById(id: String): Result<CustomerSegment>

    /**
     * Returns a single [CustomerSegment] by its [name].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         if no segment with that name exists.
     */
    suspend fun getByName(name: String): Result<CustomerSegment>

    /** Inserts a new [segment] and enqueues a sync operation. */
    suspend fun insert(segment: CustomerSegment): Result<Unit>

    /** Updates all mutable fields of [segment] and enqueues a sync operation. */
    suspend fun update(segment: CustomerSegment): Result<Unit>

    /** Hard-deletes the segment identified by [id]. */
    suspend fun delete(id: String): Result<Unit>
}
