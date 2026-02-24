package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository

/**
 * Commits a pending [StockTransfer], atomically adjusting stock quantities.
 *
 * Validates that the transfer exists and is in [StockTransfer.Status.PENDING] state
 * before delegating the two-phase commit to [WarehouseRepository.commitTransfer].
 */
class CommitStockTransferUseCase(
    private val warehouseRepo: WarehouseRepository,
) {
    suspend operator fun invoke(
        transferId: String,
        confirmedBy: String,
    ): Result<Unit> {
        if (confirmedBy.isBlank()) {
            return Result.Error(ValidationException("Confirming user ID cannot be blank"))
        }
        val transfer = when (val r = warehouseRepo.getTransferById(transferId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        }
        if (transfer.status != StockTransfer.Status.PENDING) {
            return Result.Error(ValidationException("Transfer is already ${transfer.status}"))
        }
        return warehouseRepo.commitTransfer(transferId, confirmedBy)
    }
}
