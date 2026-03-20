package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository

/**
 * Approves a PENDING inter-store transfer (C1.3 IST workflow).
 *
 * Transition: PENDING → APPROVED
 *
 * Preconditions:
 * - [approvedBy] must not be blank (manager identity required)
 * - Transfer must exist and be in PENDING status
 *
 * @param warehouseRepo Warehouse and stock transfer data access.
 */
class ApproveStockTransferUseCase(
    private val warehouseRepo: WarehouseRepository,
) {
    suspend operator fun invoke(
        transferId: String,
        approvedBy: String,
    ): Result<Unit> {
        if (transferId.isBlank()) {
            return Result.Error(ValidationException("Transfer ID cannot be blank"))
        }
        if (approvedBy.isBlank()) {
            return Result.Error(ValidationException("Approver user ID cannot be blank"))
        }

        val transfer = when (val r = warehouseRepo.getTransferById(transferId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        }

        if (transfer.status != StockTransfer.Status.PENDING) {
            return Result.Error(
                ValidationException("Transfer $transferId must be PENDING to approve (current: ${transfer.status})")
            )
        }

        return warehouseRepo.approveTransfer(transferId, approvedBy)
    }
}
