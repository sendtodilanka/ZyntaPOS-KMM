package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow

/**
 * Retrieves the complete purchase history for a customer across ALL stores (C4.3).
 *
 * Unlike store-scoped order queries, this returns every order linked to
 * [customerId] regardless of which store originated the sale. Useful for
 * centralized customer profile views and GDPR data exports.
 */
class GetCustomerPurchaseHistoryUseCase(
    private val orderRepository: OrderRepository,
) {

    /**
     * @param customerId UUID of the customer.
     * @return Flow of all orders placed by this customer, newest first.
     */
    operator fun invoke(customerId: String): Flow<List<Order>> =
        orderRepository.getAll(mapOf("customer_id" to customerId))
}
