package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Aggregates the active customer base into a snapshot report.
 *
 * ### Report contents
 * - **totalCustomers:** Count of all active customer records.
 * - **registeredCustomers:** Customers with a persistent profile (`isWalkIn = false`).
 * - **walkInCustomers:** One-time walk-in entries (`isWalkIn = true`).
 * - **creditEnabledCustomers:** Customers with credit/instalment payments enabled.
 * - **totalLoyaltyPoints:** Sum of all loyalty points outstanding across the base.
 * - **topByLoyaltyPoints:** Top 10 customers sorted by `loyaltyPoints` descending.
 * - **byGroup:** Customer count per group ID (null = no group assigned).
 *
 * The report is delivered as a [Flow] that re-emits whenever the customer table changes,
 * so the report screen stays live without manual refresh.
 *
 * @param customerRepository Source of active customer records.
 */
class GenerateCustomerReportUseCase(
    private val customerRepository: CustomerRepository,
) {

    /**
     * Immutable report value object emitted by this use case.
     *
     * @property totalCustomers       Total active customer count.
     * @property registeredCustomers  Customers with `isWalkIn = false`.
     * @property walkInCustomers      Customers with `isWalkIn = true`.
     * @property creditEnabledCustomers Customers with `creditEnabled = true`.
     * @property totalLoyaltyPoints   Sum of `loyaltyPoints` across all customers.
     * @property topByLoyaltyPoints   Top 10 customers by loyalty points (descending).
     * @property byGroup              Map of groupId (nullable) → customer count.
     */
    data class CustomerReport(
        val totalCustomers: Int,
        val registeredCustomers: Int,
        val walkInCustomers: Int,
        val creditEnabledCustomers: Int,
        val totalLoyaltyPoints: Long,
        val topByLoyaltyPoints: List<Customer>,
        val byGroup: Map<String?, Int>,
    )

    /**
     * @param storeId Optional store filter — null means all stores (G6 multi-store consolidation).
     * @return A [Flow] emitting a fresh [CustomerReport] on every customer table change.
     */
    operator fun invoke(storeId: String? = null): Flow<CustomerReport> =
        customerRepository.getAll().map { allCustomers ->
            val customers = if (storeId != null) {
                allCustomers.filter { it.storeId == storeId }
            } else {
                allCustomers
            }
            val registered = customers.count { !it.isWalkIn }
            val walkIn = customers.count { it.isWalkIn }
            val creditEnabled = customers.count { it.creditEnabled }
            val totalPoints = customers.sumOf { it.loyaltyPoints.toLong() }
            val topByPoints = customers.sortedByDescending { it.loyaltyPoints }.take(10)
            val byGroup = customers.groupBy { it.groupId }.mapValues { (_, list) -> list.size }

            CustomerReport(
                totalCustomers = customers.size,
                registeredCustomers = registered,
                walkInCustomers = walkIn,
                creditEnabledCustomers = creditEnabled,
                totalLoyaltyPoints = totalPoints,
                topByLoyaltyPoints = topByPoints,
                byGroup = byGroup,
            )
        }
}
