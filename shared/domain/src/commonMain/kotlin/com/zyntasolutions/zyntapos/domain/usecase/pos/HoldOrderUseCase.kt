package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository

/**
 * Serialises the current cart into a [com.zyntasolutions.zyntapos.domain.model.Order]
 * with status [com.zyntasolutions.zyntapos.domain.model.OrderStatus.HELD] and persists it.
 *
 * ### Business Rules
 * 1. The cart must not be empty.
 * 2. The returned [holdId] is the generated Order ID; pass it to
 *    [RetrieveHeldOrderUseCase] to restore the cart.
 * 3. Hold orders are excluded from sales totals and Z-report calculations.
 *
 * @param orderRepository Persistence layer for held orders.
 */
class HoldOrderUseCase(
    private val orderRepository: OrderRepository,
) {
    /**
     * @param items Current cart items to hold.
     * @return [Result.Success] with the hold ID string, or [Result.Error] if cart is empty.
     */
    suspend operator fun invoke(
        items: List<CartItem>,
    ): Result<String> {
        if (items.isEmpty()) {
            return Result.Error(
                ValidationException("Cannot hold an empty cart.", field = "items", rule = "EMPTY_CART"),
            )
        }
        return orderRepository.holdOrder(items)
    }
}
