package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import kotlin.time.Clock

/**
 * GDPR-compliant customer data export use case (S4-10).
 *
 * Produces a [CustomerDataExport] containing all PII and transactional data
 * associated with a given customer ID. The output can be serialised to JSON
 * and delivered to the customer as required by GDPR Article 20 (right to
 * data portability).
 *
 * ## Scope
 * - Customer profile (name, email, phone, address, loyalty points, etc.)
 * - All orders placed by the customer (including line items)
 *
 * ## Thread safety
 * This use case is safe to invoke from any coroutine dispatcher.
 */
class ExportCustomerDataUseCase(
    private val customerRepo: CustomerRepository,
    private val orderRepo: OrderRepository,
) {

    /**
     * Exports all data associated with [customerId].
     *
     * @param customerId UUID of the customer whose data to export.
     * @return [Result.Success] with a [CustomerDataExport] if the customer exists,
     *         [Result.Error] if the customer was not found.
     */
    suspend operator fun invoke(customerId: String): Result<CustomerDataExport> {
        val customerResult = customerRepo.getById(customerId)
        val customer = when (customerResult) {
            is Result.Success -> customerResult.data
            is Result.Error -> return customerResult
            is Result.Loading -> return Result.Error(
                DatabaseException("Unexpected loading state while fetching customer")
            )
        }

        // Collect all orders for this customer
        val orders = mutableListOf<Order>()
        orderRepo.getAll(mapOf("customer_id" to customerId)).collect { orderList ->
            orders.addAll(orderList)
        }

        return Result.Success(
            CustomerDataExport(
                customer = customer,
                orders = orders,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }
}

/**
 * Container for all customer-related PII and transactional data.
 *
 * Serialise to JSON for GDPR data portability delivery.
 */
data class CustomerDataExport(
    val customer: Customer,
    val orders: List<Order>,
    val exportedAt: Long,
)
