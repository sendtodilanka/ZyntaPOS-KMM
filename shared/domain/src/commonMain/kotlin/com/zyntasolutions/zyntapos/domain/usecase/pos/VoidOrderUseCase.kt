package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import kotlin.time.Clock

/**
 * Voids a completed order, reversing stock decrements and marking the order VOIDED.
 *
 * ### Business Rules
 * 1. The requesting user must hold [Permission.VOID_ORDER]. Typically CASHIER+ but
 *    configurable via the RBAC matrix.
 * 2. Only orders with status [OrderStatus.COMPLETED] may be voided.
 *    Already-voided orders return a [ValidationException] with rule `"ALREADY_VOIDED"`.
 * 3. For each [Order.items], a compensating [StockAdjustment.Type.INCREASE] is recorded.
 * 4. [reason] must not be blank — it is stored in the audit log.
 *
 * @param orderRepository    Loads and updates order records.
 * @param stockRepository    Records compensating stock adjustments.
 * @param checkPermissionUseCase RBAC evaluator.
 */
class VoidOrderUseCase(
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository,
    private val checkPermissionUseCase: CheckPermissionUseCase,
) {
    /**
     * @param orderId  The UUID of the order to void.
     * @param reason   Mandatory explanation (must not be blank).
     * @param userId   The user requesting the void (permission check applied).
     * @return [Result.Success] with [Unit] on success, or [Result.Error] on any violation.
     */
    suspend operator fun invoke(
        orderId: String,
        reason: String,
        userId: String,
    ): Result<Unit> {
        if (!checkPermissionUseCase(userId, Permission.VOID_ORDER)) {
            return Result.Error(
                ValidationException(
                    "User '$userId' does not have VOID_ORDER permission.",
                    field = "userId",
                    rule = "PERMISSION_DENIED",
                ),
            )
        }

        if (reason.isBlank()) {
            return Result.Error(
                ValidationException("Void reason must not be blank.", field = "reason", rule = "REQUIRED"),
            )
        }

        val orderResult = orderRepository.getById(orderId)
        if (orderResult is Result.Error) return orderResult
        val order = (orderResult as Result.Success).data

        if (order.status == OrderStatus.VOIDED) {
            return Result.Error(
                ValidationException(
                    "Order '${order.orderNumber}' is already voided.",
                    field = "orderId",
                    rule = "ALREADY_VOIDED",
                ),
            )
        }

        if (order.status != OrderStatus.COMPLETED) {
            return Result.Error(
                ValidationException(
                    "Only COMPLETED orders can be voided. Current status: ${order.status}.",
                    field = "orderId",
                    rule = "INVALID_STATUS_FOR_VOID",
                ),
            )
        }

        val now = Clock.System.now()

        // Reverse stock for each item
        for (item in order.items) {
            val adjustResult = stockRepository.adjustStock(
                StockAdjustment(
                    id = IdGenerator.newId(),
                    productId = item.productId,
                    type = StockAdjustment.Type.INCREASE,
                    quantity = item.quantity,
                    reason = "Void: $reason",
                    adjustedBy = userId,
                    timestamp = now,
                    syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
                ),
            )
            if (adjustResult is Result.Error) return adjustResult
        }

        return orderRepository.void(orderId, reason)
    }
}
