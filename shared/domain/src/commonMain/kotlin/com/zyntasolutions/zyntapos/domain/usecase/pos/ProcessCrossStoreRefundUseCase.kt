package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Processes a cross-store refund order (C4.1).
 *
 * Creates a REFUND order at the current store that references the original SALE
 * from potentially another store. The `originalOrderId` and `originalStoreId`
 * fields link the refund to the original transaction for accounting.
 *
 * Stock adjustment is NOT handled here — it should be managed separately based
 * on the store's return-to-stock policy (return to current store vs original store).
 */
class ProcessCrossStoreRefundUseCase(
    private val orderRepository: OrderRepository,
) {
    /**
     * @param originalOrder The original SALE order being returned.
     * @param returnItems Items being returned (subset of original order items).
     * @param currentStoreId The store where the return is being processed.
     * @param cashierId The cashier processing the return.
     * @param registerSessionId Current register session.
     * @param refundMethod How the refund will be issued.
     * @param notes Optional notes about the return.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        originalOrder: Order,
        returnItems: List<OrderItem>,
        currentStoreId: String,
        cashierId: String,
        registerSessionId: String,
        refundMethod: PaymentMethod,
        notes: String? = null,
    ): Result<Order> {
        // Validate original order
        if (originalOrder.type != OrderType.SALE) {
            return Result.Error(ValidationException("Can only refund SALE orders"))
        }
        if (originalOrder.status != OrderStatus.COMPLETED) {
            return Result.Error(ValidationException("Can only refund COMPLETED orders"))
        }
        if (returnItems.isEmpty()) {
            return Result.Error(ValidationException("At least one item must be returned"))
        }

        // Calculate refund totals
        val refundSubtotal = returnItems.sumOf { it.lineTotal }
        val refundTaxAmount = returnItems.sumOf { it.taxAmount }
        val refundTotal = refundSubtotal + refundTaxAmount

        val isCrossStore = originalOrder.storeId != currentStoreId

        val refundOrder = Order(
            id = Uuid.random().toString(),
            orderNumber = "R-${originalOrder.orderNumber}",
            type = OrderType.REFUND,
            status = OrderStatus.COMPLETED,
            items = returnItems,
            subtotal = refundSubtotal,
            taxAmount = refundTaxAmount,
            discountAmount = 0.0,
            total = refundTotal,
            paymentMethod = refundMethod,
            amountTendered = refundTotal,
            changeAmount = 0.0,
            customerId = originalOrder.customerId,
            cashierId = cashierId,
            storeId = currentStoreId,
            registerSessionId = registerSessionId,
            notes = notes ?: if (isCrossStore) "Cross-store return from ${originalOrder.storeId}" else null,
            reference = originalOrder.orderNumber,
            originalOrderId = originalOrder.id,
            originalStoreId = if (isCrossStore) originalOrder.storeId else null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            syncStatus = SyncStatus.PENDING,
        )

        return orderRepository.create(refundOrder)
    }
}
