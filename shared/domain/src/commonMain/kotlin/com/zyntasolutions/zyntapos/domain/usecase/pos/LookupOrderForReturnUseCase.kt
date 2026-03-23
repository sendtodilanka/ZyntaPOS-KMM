package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository

/**
 * Looks up a completed SALE order by ID or order number for return processing (C4.1).
 *
 * This enables cross-store returns by searching across all locally synced orders,
 * not just the current store's orders.
 *
 * @return [Result.Success] with the [Order] if found and eligible for return,
 *         or [Result.Error] if not found, already voided, or not a SALE.
 */
class LookupOrderForReturnUseCase(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(orderIdOrNumber: String): Result<Order> {
        val orderResult = orderRepository.getById(orderIdOrNumber)
        val order = when (orderResult) {
            is Result.Success -> orderResult.data
            is Result.Error -> return orderResult
        }

        if (order.type != OrderType.SALE) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.ValidationException("Only SALE orders can be returned"),
            )
        }

        if (order.status != OrderStatus.COMPLETED) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.ValidationException("Only COMPLETED orders can be returned"),
            )
        }

        return Result.Success(order)
    }
}
