package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import kotlinx.coroutines.flow.Flow

/**
 * Contract for customer group CRUD operations.
 */
interface CustomerGroupRepository {

    /** Emits the full list of active customer groups, ordered by name. Re-emits on any change. */
    fun getAll(): Flow<List<CustomerGroup>>

    /** Returns a single [CustomerGroup] by [id]. */
    suspend fun getById(id: String): Result<CustomerGroup>

    /** Inserts a new customer group and enqueues a sync operation. */
    suspend fun insert(group: CustomerGroup): Result<Unit>

    /** Persists mutable fields of the [group] and enqueues a sync operation. */
    suspend fun update(group: CustomerGroup): Result<Unit>

    /** Soft-deletes the group. Customers in the group remain but lose their group affiliation. */
    suspend fun delete(id: String): Result<Unit>
}
