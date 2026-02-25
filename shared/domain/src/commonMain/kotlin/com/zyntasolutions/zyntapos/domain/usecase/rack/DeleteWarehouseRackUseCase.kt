package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository

/**
 * Soft-deletes a warehouse rack by ID.
 *
 * The rack record is retained in the database with a `deleted_at` timestamp
 * for audit purposes; it no longer appears in normal queries.
 */
class DeleteWarehouseRackUseCase(
    private val warehouseRackRepository: WarehouseRackRepository,
) {
    suspend operator fun invoke(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> =
        warehouseRackRepository.delete(id, deletedAt, updatedAt)
}
