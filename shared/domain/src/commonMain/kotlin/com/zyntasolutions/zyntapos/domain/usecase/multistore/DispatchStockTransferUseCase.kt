package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository

/**
 * Dispatches an APPROVED inter-store transfer (C1.3 IST workflow).
 *
 * Transition: APPROVED → IN_TRANSIT
 *
 * Side effect: Records a TRANSFER_OUT stock adjustment at the source warehouse
 * and decrements global stock (goods leave source but haven't arrived yet).
 *
 * Preconditions:
 * - [dispatchedBy] must not be blank
 * - Transfer must exist and be in APPROVED status
 * - Source must have sufficient stock
 *
 * @param warehouseRepo Warehouse and stock transfer data access.
 */
class DispatchStockTransferUseCase(
    private val warehouseRepo: WarehouseRepository,
) {
    suspend operator fun invoke(
        transferId: String,
        dispatchedBy: String,
    ): Result<Unit> {
        if (transferId.isBlank()) {
            return Result.Error(ValidationException("Transfer ID cannot be blank"))
        }
        if (dispatchedBy.isBlank()) {
            return Result.Error(ValidationException("Dispatcher user ID cannot be blank"))
        }

        val transfer = when (val r = warehouseRepo.getTransferById(transferId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        }

        if (transfer.status != StockTransfer.Status.APPROVED) {
            return Result.Error(
                ValidationException("Transfer $transferId must be APPROVED to dispatch (current: ${transfer.status})")
            )
        }

        return warehouseRepo.dispatchTransfer(transferId, dispatchedBy)
    }
}
