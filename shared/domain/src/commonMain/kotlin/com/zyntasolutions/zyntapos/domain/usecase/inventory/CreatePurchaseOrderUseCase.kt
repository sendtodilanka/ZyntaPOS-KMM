package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import com.zyntasolutions.zyntapos.domain.repository.PurchaseOrderRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import kotlin.time.Clock

/**
 * Creates a new [PurchaseOrder] from a list of line items (C1.5).
 *
 * **Validation rules:**
 * - [supplierId] must not be blank.
 * - [items] must not be empty.
 * - Each item's [PurchaseOrderItem.quantityOrdered] must be > 0.
 * - Each item's [PurchaseOrderItem.unitCost] must be >= 0.
 * - The referenced supplier must exist.
 *
 * @param purchaseOrderRepository Persistence contract for POs.
 * @param supplierRepository      Used to validate the supplier exists.
 */
class CreatePurchaseOrderUseCase(
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val supplierRepository: SupplierRepository,
) {

    /**
     * @param supplierId   ID of the supplier fulfilling the order.
     * @param orderNumber  Human-readable PO reference (e.g. "PO-2026-001").
     *                     Auto-generated if blank.
     * @param items        Line items to include in the PO.
     * @param expectedDate Optional epoch millis for expected delivery.
     * @param notes        Optional free-text notes.
     * @param createdBy    User ID of the staff member creating the PO.
     * @return [Result.Success] containing the created PO ID on success.
     */
    suspend operator fun invoke(
        supplierId: String,
        orderNumber: String,
        items: List<PurchaseOrderItem>,
        expectedDate: Long? = null,
        notes: String? = null,
        createdBy: String,
    ): Result<String> {
        if (supplierId.isBlank()) {
            return Result.Error(
                ValidationException("Supplier is required.", field = "supplierId", rule = "REQUIRED")
            )
        }

        if (supplierRepository.getById(supplierId) is Result.Error) {
            return Result.Error(
                ValidationException("Supplier not found.", field = "supplierId", rule = "NOT_FOUND")
            )
        }

        if (items.isEmpty()) {
            return Result.Error(
                ValidationException("At least one item is required.", field = "items", rule = "REQUIRED")
            )
        }

        for ((index, item) in items.withIndex()) {
            if (item.quantityOrdered <= 0.0) {
                return Result.Error(
                    ValidationException(
                        message = "Item ${index + 1}: quantity ordered must be greater than zero.",
                        field   = "items[$index].quantityOrdered",
                        rule    = "POSITIVE_REQUIRED",
                    )
                )
            }
            if (item.unitCost < 0.0) {
                return Result.Error(
                    ValidationException(
                        message = "Item ${index + 1}: unit cost cannot be negative.",
                        field   = "items[$index].unitCost",
                        rule    = "NON_NEGATIVE_REQUIRED",
                    )
                )
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val resolvedOrderNumber = orderNumber.trim().ifBlank {
            IdGenerator.newPrefixedId("PO")
        }

        val order = PurchaseOrder(
            id           = IdGenerator.newId(),
            supplierId   = supplierId,
            orderNumber  = resolvedOrderNumber,
            status       = PurchaseOrder.Status.PENDING,
            orderDate    = now,
            expectedDate = expectedDate,
            totalAmount  = items.sumOf { it.lineTotal },
            notes        = notes?.trim(),
            createdBy    = createdBy,
            items        = items,
        )

        return when (val result = purchaseOrderRepository.create(order)) {
            is Result.Success -> Result.Success(order.id)
            is Result.Error   -> result
        }
    }
}
