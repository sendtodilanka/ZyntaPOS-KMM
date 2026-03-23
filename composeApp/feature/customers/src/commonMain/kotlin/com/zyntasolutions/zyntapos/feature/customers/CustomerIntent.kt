package com.zyntasolutions.zyntapos.feature.customers

/**
 * All user actions that can be dispatched to [CustomerViewModel].
 *
 * Follows strict MVI — UI components dispatch intents, never mutate state directly.
 */
sealed interface CustomerIntent {

    // ── List Screen ───────────────────────────────────────────────────────────
    /** Trigger initial load of customers and groups. */
    data object LoadCustomers : CustomerIntent

    /** Update the live search query (debounced in ViewModel). */
    data class SearchQueryChanged(val query: String) : CustomerIntent

    /** Filter list by customer group; null = show all. */
    data class FilterByGroup(val groupId: String?) : CustomerIntent

    /** Sort the customer list by a column key. */
    data class SortByColumn(val columnKey: String) : CustomerIntent

    // ── Detail / Edit ─────────────────────────────────────────────────────────
    /** Load an existing customer for detail/edit; null = start create flow. */
    data class SelectCustomer(val customerId: String?) : CustomerIntent

    /** Update a single text field in the customer edit form. */
    data class UpdateFormField(val field: String, val value: String) : CustomerIntent

    /** Toggle the credit enabled flag on the edit form. */
    data class UpdateCreditEnabled(val enabled: Boolean) : CustomerIntent

    /** Toggle the walk-in flag on the edit form. */
    data class UpdateIsWalkIn(val isWalkIn: Boolean) : CustomerIntent

    /** Persist the current form — inserts if new, updates if existing. */
    data object SaveCustomer : CustomerIntent

    /** Soft-delete the customer with the given ID. */
    data class DeleteCustomer(val customerId: String) : CustomerIntent

    /** Dismiss error or success message. */
    data object DismissMessage : CustomerIntent

    // ── Group Management ──────────────────────────────────────────────────────
    /** Select a group for detail/edit; null = create new group. */
    data class SelectGroup(val groupId: String?) : CustomerIntent

    /** Update a single field in the group edit form. */
    data class UpdateGroupField(val field: String, val value: String) : CustomerIntent

    /** Persist the current group form. */
    data object SaveGroup : CustomerIntent

    /** Soft-delete the group with the given ID. */
    data class DeleteGroup(val groupId: String) : CustomerIntent

    /** Dismiss the group detail panel. */
    data object DismissGroupDetail : CustomerIntent

    // ── Wallet & Loyalty ──────────────────────────────────────────────────────
    /** Load wallet and transaction history for [customerId]. */
    data class LoadWallet(val customerId: String) : CustomerIntent

    /** Top up the customer wallet by [amount] (manual credit). */
    data class TopUpWallet(val customerId: String, val amount: Double, val note: String) : CustomerIntent

    // ── C4.3: Cross-Store Operations ─────────────────────────────────────────
    /** Export all customer data as JSON for GDPR compliance. */
    data class ExportCustomerData(val customerId: String) : CustomerIntent

    /** Merge [sourceId] into [targetId] — combines points, wallet, and contact info. */
    data class MergeCustomers(val targetId: String, val sourceId: String) : CustomerIntent

    /** Load purchase history for a customer across all stores. */
    data class LoadPurchaseHistory(val customerId: String) : CustomerIntent

    /** Promote a store-specific customer to global scope. */
    data class MakeCustomerGlobal(val customerId: String) : CustomerIntent
}
