package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository

/**
 * Receives an IN_TRANSIT inter-store transfer at the destination (C1.3 IST workflow).
 *
 * Transition: IN_TRANSIT → RECEIVED
 *
 * Side effect: Records a TRANSFER_IN stock adjustment at the destination warehouse
 * and restores global stock (goods arrive at destination).
 *
 * Preconditions:
 * - [receivedBy] must not be blank
 * - Transfer must exist and be in IN_TRANSIT status
 *
 * @param warehouseRepo Warehouse and stock transfer data access.
 */
class ReceiveStockTransferUseCase(
    private val warehouseRepo: WarehouseRepository,
) {
    suspend operator fun invoke(
        transferId: String,
        receivedBy: String,
    ): Result<Unit> {
        if (transferId.isBlank()) {
            return Result.Error(ValidationException("Transfer ID cannot be blank"))
        }
        if (receivedBy.isBlank()) {
            return Result.Error(ValidationException("Receiver user ID cannot be blank"))
        }

        val transfer = when (val r = warehouseRepo.getTransferById(transferId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Loading
        }

        if (transfer.status != StockTransfer.Status.IN_TRANSIT) {
            return Result.Error(
                ValidationException("Transfer $transferId must be IN_TRANSIT to receive (current: ${transfer.status})")
            )
        }

        return warehouseRepo.receiveTransfer(transferId, receivedBy)
    }
}
