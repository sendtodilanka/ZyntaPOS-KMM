package com.zyntasolutions.zyntapos.feature.customers

import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction

/**
 * Immutable UI state for the Customers CRM screens.
 *
 * Consumed by [CustomerListScreen], [CustomerDetailScreen],
 * [CustomerGroupScreen], and [CustomerWalletScreen].
 *
 * @property customers Filtered and sorted customer list.
 * @property customerGroups All available customer groups for assignment.
 * @property searchQuery Live text from the customer search bar.
 * @property selectedGroupId Active group filter; null = show all.
 * @property sortColumn Active sort column key.
 * @property sortDirection Sort direction.
 * @property selectedCustomer Customer loaded for detail/edit; null = list mode.
 * @property editFormState Mutable draft for create/edit customer form.
 * @property selectedGroup Group loaded for detail/edit in group management.
 * @property groupFormState Mutable draft for create/edit group form.
 * @property wallet Wallet for the currently selected customer.
 * @property walletTransactions Transaction history for the selected customer wallet.
 * @property rewardHistory Reward points ledger for the selected customer.
 * @property isLoading True while an async operation is in flight.
 * @property isWalletLoading True while wallet data is loading.
 * @property error Transient error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 */
data class CustomerState(
    // ── List View ──────────────────────────────────────────────────────────
    val customers: List<Customer> = emptyList(),
    val customerGroups: List<CustomerGroup> = emptyList(),
    val searchQuery: String = "",
    val selectedGroupId: String? = null,
    val sortColumn: String = "name",
    val sortDirection: SortDir = SortDir.ASC,

    // ── Detail / Edit ─────────────────────────────────────────────────────
    val selectedCustomer: Customer? = null,
    val editFormState: CustomerFormState = CustomerFormState(),

    // ── Group Management ──────────────────────────────────────────────────
    val selectedGroup: CustomerGroup? = null,
    val groupFormState: GroupFormState = GroupFormState(),
    val showGroupDetail: Boolean = false,

    // ── Wallet & Loyalty ──────────────────────────────────────────────────
    val wallet: CustomerWallet? = null,
    val walletTransactions: List<WalletTransaction> = emptyList(),
    val rewardHistory: List<RewardPoints> = emptyList(),
    val pointsBalance: Int = 0,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val isWalletLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** Sort direction for table columns. */
enum class SortDir { ASC, DESC }

/**
 * Mutable form fields for customer create/edit operations.
 */
data class CustomerFormState(
    val id: String? = null,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val groupId: String = "",
    val notes: String = "",
    val gender: String = "",
    val birthday: String = "",
    val creditLimit: String = "0",
    val creditEnabled: Boolean = false,
    val isWalkIn: Boolean = false,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

/**
 * Mutable form fields for customer group create/edit operations.
 */
data class GroupFormState(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val discountType: String = "",
    val discountValue: String = "0",
    val priceType: String = CustomerGroup.PriceType.RETAIL.name,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)
