package com.zyntasolutions.zyntapos.feature.customers

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.usecase.crm.SaveCustomerGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.WalletTopUpUseCase
import com.zyntasolutions.zyntapos.domain.validation.UserValidator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the Customers CRM feature — Sprint 9–11.
 *
 * Manages:
 * - Customer directory listing with live search and group filtering
 * - Customer create/edit form lifecycle
 * - Customer group management (CRUD)
 * - Customer wallet view and manual top-up
 * - Reward points history display
 *
 * All state mutations go through [handleIntent]; UI observes [state] and
 * one-shot navigation/feedback through [effects].
 *
 * @param customerRepository  CRUD + FTS5 search for customer records.
 * @param groupRepository     Customer group CRUD.
 * @param walletRepository    Wallet balance + transaction history.
 * @param loyaltyRepository   Reward points ledger.
 * @param saveGroupUseCase    Validates and persists customer groups.
 * @param walletTopUpUseCase  Validates and applies wallet top-up.
 * @param currentUserId       Resolved from the active auth session at DI time.
 */
@OptIn(FlowPreview::class)
class CustomerViewModel(
    private val customerRepository: CustomerRepository,
    private val groupRepository: CustomerGroupRepository,
    private val walletRepository: CustomerWalletRepository,
    private val loyaltyRepository: LoyaltyRepository,
    private val saveGroupUseCase: SaveCustomerGroupUseCase,
    private val walletTopUpUseCase: WalletTopUpUseCase,
    private val currentUserId: String,
) : BaseViewModel<CustomerState, CustomerIntent, CustomerEffect>(CustomerState()) {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGroupId = MutableStateFlow<String?>(null)

    init {
        observeGroups()
        observeCustomers()
    }

    private fun observeGroups() {
        groupRepository.getAll()
            .onEach { groups -> updateState { copy(customerGroups = groups) } }
            .launchIn(viewModelScope)
    }

    private fun observeCustomers() {
        combine(_searchQuery.debounce(300L), _selectedGroupId) { q, gid -> q to gid }
            .distinctUntilChanged()
            .flatMapLatest { (query, groupId) ->
                when {
                    groupId != null -> customerRepository.getAll().map { list -> list.filter { it.groupId == groupId } }
                    query.isNotBlank() -> customerRepository.search(query)
                    else -> customerRepository.getAll()
                }
            }
            .onEach { customers ->
                val sorted = applySorting(customers)
                updateState { copy(customers = sorted, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: CustomerIntent) {
        when (intent) {
            is CustomerIntent.LoadCustomers -> updateState { copy(isLoading = true) }

            is CustomerIntent.SearchQueryChanged -> {
                _searchQuery.value = intent.query
                updateState { copy(searchQuery = intent.query) }
            }

            is CustomerIntent.FilterByGroup -> {
                _selectedGroupId.value = intent.groupId
                updateState { copy(selectedGroupId = intent.groupId) }
            }

            is CustomerIntent.SortByColumn -> onSortByColumn(intent.columnKey)

            is CustomerIntent.SelectCustomer -> onSelectCustomer(intent.customerId)
            is CustomerIntent.UpdateFormField -> onUpdateFormField(intent.field, intent.value)
            is CustomerIntent.UpdateCreditEnabled -> updateState {
                copy(editFormState = editFormState.copy(creditEnabled = intent.enabled))
            }
            is CustomerIntent.UpdateIsWalkIn -> updateState {
                copy(editFormState = editFormState.copy(isWalkIn = intent.isWalkIn))
            }
            is CustomerIntent.SaveCustomer -> onSaveCustomer()
            is CustomerIntent.DeleteCustomer -> onDeleteCustomer(intent.customerId)
            is CustomerIntent.DismissMessage -> updateState { copy(error = null, successMessage = null) }

            is CustomerIntent.SelectGroup -> onSelectGroup(intent.groupId)
            is CustomerIntent.UpdateGroupField -> onUpdateGroupField(intent.field, intent.value)
            is CustomerIntent.SaveGroup -> onSaveGroup()
            is CustomerIntent.DeleteGroup -> onDeleteGroup(intent.groupId)
            is CustomerIntent.DismissGroupDetail -> updateState {
                copy(showGroupDetail = false, selectedGroup = null, groupFormState = GroupFormState())
            }

            is CustomerIntent.LoadWallet -> onLoadWallet(intent.customerId)
            is CustomerIntent.TopUpWallet -> onTopUpWallet(intent.customerId, intent.amount, intent.note)
        }
    }

    // ── Customer CRUD ─────────────────────────────────────────────────────────

    private fun onSortByColumn(columnKey: String) {
        val current = currentState
        val newDir = if (current.sortColumn == columnKey && current.sortDirection == SortDir.ASC) {
            SortDir.DESC
        } else {
            SortDir.ASC
        }
        val sorted = applySorting(current.customers, columnKey, newDir)
        updateState { copy(sortColumn = columnKey, sortDirection = newDir, customers = sorted) }
    }

    private suspend fun onSelectCustomer(customerId: String?) {
        if (customerId == null) {
            updateState {
                copy(
                    selectedCustomer = null,
                    editFormState = CustomerFormState(isEditing = false),
                )
            }
            sendEffect(CustomerEffect.NavigateToDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = customerRepository.getById(customerId)) {
            is Result.Success -> {
                val c = result.data
                updateState {
                    copy(
                        selectedCustomer = c,
                        isLoading = false,
                        editFormState = CustomerFormState(
                            id = c.id,
                            name = c.name,
                            phone = c.phone,
                            email = c.email ?: "",
                            address = c.address ?: "",
                            groupId = c.groupId ?: "",
                            notes = c.notes ?: "",
                            gender = c.gender ?: "",
                            birthday = c.birthday ?: "",
                            creditLimit = c.creditLimit.toString(),
                            creditEnabled = c.creditEnabled,
                            isWalkIn = c.isWalkIn,
                            isEditing = true,
                        ),
                    )
                }
                sendEffect(CustomerEffect.NavigateToDetail(customerId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Failed to load customer"))
            }
            is Result.Loading -> {}
        }
    }

    private fun onUpdateFormField(field: String, value: String) {
        updateState {
            copy(
                editFormState = when (field) {
                    "name" -> editFormState.copy(name = value, validationErrors = editFormState.validationErrors - "name")
                    "phone" -> editFormState.copy(phone = value, validationErrors = editFormState.validationErrors - "phone")
                    "email" -> editFormState.copy(email = value, validationErrors = editFormState.validationErrors - "email")
                    "address" -> editFormState.copy(address = value)
                    "groupId" -> editFormState.copy(groupId = value)
                    "notes" -> editFormState.copy(notes = value)
                    "gender" -> editFormState.copy(gender = value)
                    "birthday" -> editFormState.copy(birthday = value)
                    "creditLimit" -> editFormState.copy(creditLimit = value, validationErrors = editFormState.validationErrors - "creditLimit")
                    else -> editFormState
                },
            )
        }
    }

    private suspend fun onSaveCustomer() {
        val form = currentState.editFormState
        val errors = validateCustomerForm(form)
        if (errors.isNotEmpty()) {
            updateState { copy(editFormState = editFormState.copy(validationErrors = errors)) }
            return
        }

        updateState { copy(isLoading = true) }
        val customer = Customer(
            id = form.id ?: IdGenerator.newId(),
            name = form.name.trim(),
            phone = form.phone.trim(),
            email = form.email.trim().takeIf { it.isNotBlank() },
            address = form.address.trim().takeIf { it.isNotBlank() },
            groupId = form.groupId.trim().takeIf { it.isNotBlank() },
            notes = form.notes.trim().takeIf { it.isNotBlank() },
            gender = form.gender.trim().takeIf { it.isNotBlank() },
            birthday = form.birthday.trim().takeIf { it.isNotBlank() },
            creditLimit = form.creditLimit.toDoubleOrNull() ?: 0.0,
            creditEnabled = form.creditEnabled,
            isWalkIn = form.isWalkIn,
        )

        val result = if (form.isEditing) {
            customerRepository.update(customer)
        } else {
            customerRepository.insert(customer)
        }

        when (result) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        successMessage = if (form.isEditing) "Customer updated" else "Customer created",
                        editFormState = CustomerFormState(),
                        selectedCustomer = null,
                    )
                }
                sendEffect(CustomerEffect.ShowSuccess(if (form.isEditing) "Customer updated" else "Customer created"))
                sendEffect(CustomerEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onDeleteCustomer(customerId: String) {
        updateState { copy(isLoading = true) }
        when (val result = customerRepository.delete(customerId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, selectedCustomer = null) }
                sendEffect(CustomerEffect.ShowSuccess("Customer deleted"))
                sendEffect(CustomerEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Group Management ──────────────────────────────────────────────────────

    private suspend fun onSelectGroup(groupId: String?) {
        if (groupId == null) {
            updateState {
                copy(
                    selectedGroup = null,
                    groupFormState = GroupFormState(isEditing = false),
                    showGroupDetail = true,
                )
            }
            return
        }
        when (val result = groupRepository.getById(groupId)) {
            is Result.Success -> {
                val g = result.data
                updateState {
                    copy(
                        selectedGroup = g,
                        showGroupDetail = true,
                        groupFormState = GroupFormState(
                            id = g.id, name = g.name,
                            description = g.description ?: "",
                            discountType = g.discountType?.name ?: "",
                            discountValue = g.discountValue.toString(),
                            priceType = g.priceType.name,
                            isEditing = true,
                        ),
                    )
                }
            }
            is Result.Error -> sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Failed to load group"))
            is Result.Loading -> {}
        }
    }

    private fun onUpdateGroupField(field: String, value: String) {
        updateState {
            copy(
                groupFormState = when (field) {
                    "name" -> groupFormState.copy(name = value, validationErrors = groupFormState.validationErrors - "name")
                    "description" -> groupFormState.copy(description = value)
                    "discountType" -> groupFormState.copy(discountType = value)
                    "discountValue" -> groupFormState.copy(discountValue = value, validationErrors = groupFormState.validationErrors - "discountValue")
                    "priceType" -> groupFormState.copy(priceType = value)
                    else -> groupFormState
                },
            )
        }
    }

    private suspend fun onSaveGroup() {
        val form = currentState.groupFormState
        if (form.name.isBlank()) {
            updateState {
                copy(groupFormState = groupFormState.copy(validationErrors = mapOf("name" to "Name is required")))
            }
            return
        }
        val group = CustomerGroup(
            id = form.id ?: IdGenerator.newId(),
            name = form.name.trim(),
            description = form.description.trim().takeIf { it.isNotBlank() },
            discountType = runCatching { DiscountType.valueOf(form.discountType.trim()) }.getOrNull(),
            discountValue = form.discountValue.toDoubleOrNull() ?: 0.0,
            priceType = runCatching { CustomerGroup.PriceType.valueOf(form.priceType) }
                .getOrDefault(CustomerGroup.PriceType.RETAIL),
        )
        when (val result = saveGroupUseCase(group, isNew = !form.isEditing)) {
            is Result.Success -> {
                updateState {
                    copy(
                        showGroupDetail = false,
                        selectedGroup = null,
                        groupFormState = GroupFormState(),
                        successMessage = if (form.isEditing) "Group updated" else "Group created",
                    )
                }
                sendEffect(CustomerEffect.ShowSuccess(if (form.isEditing) "Group updated" else "Group created"))
            }
            is Result.Error -> sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Save group failed"))
            is Result.Loading -> {}
        }
    }

    private suspend fun onDeleteGroup(groupId: String) {
        when (val result = groupRepository.delete(groupId)) {
            is Result.Success -> {
                updateState { copy(showGroupDetail = false, selectedGroup = null) }
                sendEffect(CustomerEffect.ShowSuccess("Group deleted"))
            }
            is Result.Error -> sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Delete failed"))
            is Result.Loading -> {}
        }
    }

    // ── Wallet & Loyalty ──────────────────────────────────────────────────────

    private suspend fun onLoadWallet(customerId: String) {
        updateState { copy(isWalletLoading = true) }
        val walletResult = walletRepository.getOrCreate(customerId)
        val loyaltyBalance = loyaltyRepository.getBalance(customerId)
        when (walletResult) {
            is Result.Success -> {
                val wallet = walletResult.data
                val transactions = walletRepository.getTransactions(wallet.id).first()
                val history = loyaltyRepository.getPointsHistory(customerId).first()
                updateState {
                    copy(
                        isWalletLoading = false,
                        wallet = wallet,
                        pointsBalance = (loyaltyBalance as? Result.Success)?.data ?: 0,
                        rewardHistory = history,
                        walletTransactions = transactions,
                    )
                }
            }
            is Result.Error -> {
                updateState { copy(isWalletLoading = false) }
                sendEffect(CustomerEffect.ShowError(walletResult.exception.message ?: "Failed to load wallet"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onTopUpWallet(customerId: String, amount: Double, note: String) {
        updateState { copy(isWalletLoading = true) }
        when (val result = walletTopUpUseCase(customerId, amount, note)) {
            is Result.Success -> {
                sendEffect(CustomerEffect.ShowSuccess("Wallet topped up successfully"))
                onLoadWallet(customerId)
            }
            is Result.Error -> {
                updateState { copy(isWalletLoading = false) }
                sendEffect(CustomerEffect.ShowError(result.exception.message ?: "Top-up failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applySorting(
        customers: List<Customer>,
        column: String = currentState.sortColumn,
        dir: SortDir = currentState.sortDirection,
    ): List<Customer> {
        val comparator: Comparator<Customer> = when (column) {
            "name" -> compareBy { it.name }
            "phone" -> compareBy { it.phone }
            "email" -> compareBy { it.email ?: "" }
            "points" -> compareBy { it.loyaltyPoints }
            else -> compareBy { it.name }
        }
        return if (dir == SortDir.ASC) customers.sortedWith(comparator)
        else customers.sortedWith(comparator.reversed())
    }

    private fun validateCustomerForm(form: CustomerFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        UserValidator.validateName(form.name)?.let  { errors["name"]  = it }
        UserValidator.validatePhone(form.phone)?.let { errors["phone"] = it }
        val creditLimit = form.creditLimit.toDoubleOrNull()
        if (creditLimit == null || creditLimit < 0) errors["creditLimit"] = "Must be 0 or positive"
        return errors
    }
}
