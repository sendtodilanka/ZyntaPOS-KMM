package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PickList
import com.zyntasolutions.zyntapos.domain.model.PickListItem
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import kotlin.time.Clock

/**
 * Generates a warehouse pick list for an approved inter-store transfer (P3-B1).
 *
 * The pick list enriches transfer items with:
 * - Product name, SKU, and unit of measure from [ProductRepository]
 * - Rack/bin locations from [WarehouseRepository] (warehouse_racks + rack_products)
 *
 * Items are sorted by rack location (alphabetically) so warehouse staff can
 * walk the aisles in order, minimising travel time.
 *
 * Preconditions:
 * - Transfer must exist
 * - Transfer must be in APPROVED status (ready for picking)
 *
 * @param warehouseRepo Product rack location and transfer data access.
 * @param productRepo   Product detail lookup (name, SKU, unit).
 */
class GeneratePickListUseCase(
    private val warehouseRepo: WarehouseRepository,
    private val productRepo: ProductRepository,
) {
    suspend operator fun invoke(transferId: String): Result<PickList> {
        if (transferId.isBlank()) {
            return Result.Error(ValidationException("Transfer ID cannot be blank"))
        }

        // 1. Fetch the transfer
        val transfer = when (val r = warehouseRepo.getTransferById(transferId)) {
            is Result.Success -> r.data
            is Result.Error -> return Result.Error(r.exception)
            is Result.Loading -> return Result.Loading
        }

        if (transfer.status != StockTransfer.Status.APPROVED) {
            return Result.Error(
                ValidationException(
                    "Pick list can only be generated for APPROVED transfers (current: ${transfer.status})"
                )
            )
        }

        // 2. Resolve warehouse names
        val sourceWarehouse = when (val r = warehouseRepo.getById(transfer.sourceWarehouseId)) {
            is Result.Success -> r.data
            is Result.Error -> return Result.Error(r.exception)
            is Result.Loading -> return Result.Loading
        }
        val destWarehouse = when (val r = warehouseRepo.getById(transfer.destWarehouseId)) {
            is Result.Success -> r.data
            is Result.Error -> return Result.Error(r.exception)
            is Result.Loading -> return Result.Loading
        }

        // 3. Resolve product details
        val product = when (val r = productRepo.getById(transfer.productId)) {
            is Result.Success -> r.data
            is Result.Error -> return Result.Error(r.exception)
            is Result.Loading -> return Result.Loading
        }

        // 4. Look up rack location in the source warehouse
        val (rackName, binLocation) = when (
            val r = warehouseRepo.getRackLocationForProduct(
                productId = transfer.productId,
                warehouseId = transfer.sourceWarehouseId,
            )
        ) {
            is Result.Success -> r.data
            is Result.Error -> null to null // Rack location not found — non-fatal
            is Result.Loading -> null to null
        }

        // 5. Build the pick list item
        val item = PickListItem(
            productId = product.id,
            productName = product.name,
            sku = product.sku ?: "",
            quantity = transfer.quantity,
            rackLocation = rackName,
            binLocation = binLocation,
        )

        // 6. Assemble pick list — sorted by rack location for efficient picking
        val items = listOf(item).sortedBy { it.rackLocation ?: "ZZZ" }

        return Result.Success(
            PickList(
                transferId = transfer.id,
                sourceStoreName = sourceWarehouse.name,
                destinationStoreName = destWarehouse.name,
                items = items,
                generatedAt = Clock.System.now(),
                notes = transfer.notes,
            )
        )
    }
}
