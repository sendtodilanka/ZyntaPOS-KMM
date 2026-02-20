package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository

/**
 * Retrieves a previously-held order and converts it back to a [List] of [CartItem]s
 * so the cashier can continue the sale.
 *
 * ### Business Rules
 * 1. The held order record remains with status HELD in the database until the cashier
 *    finalises it via [ProcessPaymentUseCase] or explicitly voids it.
 * 2. The returned [CartItem]s reflect the **snapshot** prices and quantities stored
 *    at hold time — live price/stock changes are NOT re-applied here.
 *
 * @param orderRepository Source for held orders.
 */
class RetrieveHeldOrderUseCase(
    private val orderRepository: OrderRepository,
) {
    /**
     * @param holdId The hold ID returned by [HoldOrderUseCase].
     * @return [Result.Success] with a list of [CartItem]s restored from the held order,
     *         or [Result.Error] if the hold ID does not exist.
     */
    suspend operator fun invoke(holdId: String): Result<List<CartItem>> {
        return when (val result = orderRepository.retrieveHeld(holdId)) {
            is Result.Error -> result
            is Result.Loading -> Result.Loading
            is Result.Success -> {
                val cartItems = result.data.items.map { item ->
                    CartItem(
                        productId = item.productId,
                        productName = item.productName,
                        unitPrice = item.unitPrice,
                        quantity = item.quantity,
                        discount = item.discount,
                        discountType = item.discountType,
                        taxRate = item.taxRate,
                        lineTotal = item.lineTotal,
                    )
                }
                Result.Success(cartItems)
            }
        }
    }
}
