package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import kotlinx.coroutines.flow.Flow

/**
 * Contract for warehouse rack location management.
 */
interface WarehouseRackRepository {

    /** Emits all racks for [warehouseId] ordered by name. Re-emits on change. */
    fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>>

    /** Returns a single rack by [id]. */
    suspend fun getById(id: String): Result<WarehouseRack>

    /** Inserts a new rack. Fails if a rack with the same name exists in [warehouseId]. */
    suspend fun insert(rack: WarehouseRack): Result<Unit>

    /** Updates an existing rack. */
    suspend fun update(rack: WarehouseRack): Result<Unit>

    /** Soft-deletes a rack. */
    suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit>
}
