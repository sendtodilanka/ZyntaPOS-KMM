package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.expenses.ApproveExpenseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.SaveExpenseUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Expenses feature — Sprints 17–18.
 *
 * Manages:
 * - Expense list with optional status filter
 * - Expense create/edit form lifecycle
 * - Approve/reject workflow (STORE_MANAGER / ACCOUNTANT roles)
 * - Expense category management (CRUD in a modal sheet)
 *
 * @param expenseRepository   Expense CRUD, approval, and category operations.
 * @param saveExpenseUseCase  Validates and persists expenses.
 * @param approveExpenseUseCase  Approve/reject workflow.
 * @param authRepository      Provides the active auth session for resolving currentUserId.
 */
class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val saveExpenseUseCase: SaveExpenseUseCase,
    private val approveExpenseUseCase: ApproveExpenseUseCase,
    private val authRepository: AuthRepository,
) : BaseViewModel<ExpenseState, ExpenseIntent, ExpenseEffect>(ExpenseState()) {

    private var currentUserId: String = "unknown"

    init {
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
        observeExpenses()
        observeCategories()
    }

    private fun observeExpenses() {
        val filter = currentState.statusFilter
        val flow = if (filter != null) expenseRepository.getByStatus(filter) else expenseRepository.getAll()
        flow.onEach { expenses -> updateState { copy(expenses = expenses, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun observeCategories() {
        expenseRepository.getAllCategories()
            .onEach { categories -> updateState { copy(categories = categories) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: ExpenseIntent) {
        when (intent) {
            is ExpenseIntent.LoadExpenses -> updateState { copy(isLoading = true) }

            is ExpenseIntent.FilterByStatus -> {
                updateState { copy(statusFilter = intent.status) }
                observeExpenses()
            }

            is ExpenseIntent.SelectExpense -> onSelectExpense(intent.expenseId)
            is ExpenseIntent.UpdateFormField -> onUpdateFormField(intent.field, intent.value)
            is ExpenseIntent.SaveExpense -> onSaveExpense()
            is ExpenseIntent.DeleteExpense -> onDeleteExpense(intent.expenseId)

            is ExpenseIntent.ApproveExpense -> onApproveExpense(intent.expenseId)
            is ExpenseIntent.RejectExpense -> onRejectExpense(intent.expenseId, intent.reason)

            is ExpenseIntent.SelectCategory -> onSelectCategory(intent.categoryId)
            is ExpenseIntent.UpdateCategoryField -> onUpdateCategoryField(intent.field, intent.value)
            is ExpenseIntent.SaveCategory -> onSaveCategory()
            is ExpenseIntent.DeleteCategory -> onDeleteCategory(intent.categoryId)
            is ExpenseIntent.DismissCategoryDetail -> updateState {
                copy(showCategoryDetail = false, selectedCategory = null, categoryForm = CategoryFormState())
            }

            is ExpenseIntent.DismissMessage -> updateState { copy(error = null, successMessage = null) }
        }
    }

    // ── Expense CRUD ───────────────────────────────────────────────────────

    private suspend fun onSelectExpense(expenseId: String?) {
        if (expenseId == null) {
            updateState {
                copy(
                    selectedExpense = null,
                    expenseForm = ExpenseFormState(
                        expenseDate = Clock.System.now().toEpochMilliseconds().toString(),
                        isEditing = false,
                    ),
                )
            }
            sendEffect(ExpenseEffect.NavigateToDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = expenseRepository.getById(expenseId)) {
            is Result.Success -> {
                val e = result.data
                updateState {
                    copy(
                        selectedExpense = e,
                        isLoading = false,
                        expenseForm = ExpenseFormState(
                            id = e.id,
                            description = e.description,
                            amount = e.amount.toString(),
                            categoryId = e.categoryId ?: "",
                            expenseDate = e.expenseDate.toString(),
                            receiptUrl = e.receiptUrl ?: "",
                            isEditing = true,
                        ),
                    )
                }
                sendEffect(ExpenseEffect.NavigateToDetail(expenseId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Failed to load expense"))
            }
            is Result.Loading -> {}
        }
    }

    private fun onUpdateFormField(field: String, value: String) {
        updateState {
            copy(
                expenseForm = when (field) {
                    "description" -> expenseForm.copy(description = value, validationErrors = expenseForm.validationErrors - "description")
                    "amount" -> expenseForm.copy(amount = value, validationErrors = expenseForm.validationErrors - "amount")
                    "categoryId" -> expenseForm.copy(categoryId = value)
                    "expenseDate" -> expenseForm.copy(expenseDate = value, validationErrors = expenseForm.validationErrors - "expenseDate")
                    "receiptUrl" -> expenseForm.copy(receiptUrl = value)
                    else -> expenseForm
                },
            )
        }
    }

    private suspend fun onSaveExpense() {
        val form = currentState.expenseForm
        val errors = validateExpenseForm(form)
        if (errors.isNotEmpty()) {
            updateState { copy(expenseForm = expenseForm.copy(validationErrors = errors)) }
            return
        }
        updateState { copy(isLoading = true) }
        val expense = Expense(
            id = form.id ?: IdGenerator.newId(),
            description = form.description.trim(),
            amount = form.amount.toDoubleOrNull() ?: 0.0,
            categoryId = form.categoryId.trim().takeIf { it.isNotBlank() },
            expenseDate = form.expenseDate.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds(),
            receiptUrl = form.receiptUrl.trim().takeIf { it.isNotBlank() },
            createdBy = currentUserId,
        )
        when (val result = saveExpenseUseCase(expense, isNew = !form.isEditing)) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Expense updated" else "Expense logged"
                updateState { copy(isLoading = false, successMessage = msg, expenseForm = ExpenseFormState()) }
                sendEffect(ExpenseEffect.ShowSuccess(msg))
                sendEffect(ExpenseEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onDeleteExpense(expenseId: String) {
        updateState { copy(isLoading = true) }
        when (val result = expenseRepository.delete(expenseId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, selectedExpense = null) }
                sendEffect(ExpenseEffect.ShowSuccess("Expense deleted"))
                sendEffect(ExpenseEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Approval Workflow ─────────────────────────────────────────────────

    private suspend fun onApproveExpense(expenseId: String) {
        when (val result = approveExpenseUseCase.approve(expenseId, currentUserId)) {
            is Result.Success -> sendEffect(ExpenseEffect.ShowSuccess("Expense approved"))
            is Result.Error -> sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Approval failed"))
            is Result.Loading -> {}
        }
    }

    private suspend fun onRejectExpense(expenseId: String, reason: String?) {
        when (val result = approveExpenseUseCase.reject(expenseId, currentUserId, reason)) {
            is Result.Success -> sendEffect(ExpenseEffect.ShowSuccess("Expense rejected"))
            is Result.Error -> sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Rejection failed"))
            is Result.Loading -> {}
        }
    }

    // ── Category Management ────────────────────────────────────────────────

    private suspend fun onSelectCategory(categoryId: String?) {
        if (categoryId == null) {
            updateState {
                copy(
                    selectedCategory = null,
                    categoryForm = CategoryFormState(isEditing = false),
                    showCategoryDetail = true,
                )
            }
            return
        }
        when (val result = expenseRepository.getCategoryById(categoryId)) {
            is Result.Success -> {
                val c = result.data
                updateState {
                    copy(
                        selectedCategory = c,
                        showCategoryDetail = true,
                        categoryForm = CategoryFormState(
                            id = c.id,
                            name = c.name,
                            description = c.description ?: "",
                            parentId = c.parentId ?: "",
                            isEditing = true,
                        ),
                    )
                }
            }
            is Result.Error -> sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Failed to load category"))
            is Result.Loading -> {}
        }
    }

    private fun onUpdateCategoryField(field: String, value: String) {
        updateState {
            copy(
                categoryForm = when (field) {
                    "name" -> categoryForm.copy(name = value, validationErrors = categoryForm.validationErrors - "name")
                    "description" -> categoryForm.copy(description = value)
                    "parentId" -> categoryForm.copy(parentId = value)
                    else -> categoryForm
                },
            )
        }
    }

    private suspend fun onSaveCategory() {
        val form = currentState.categoryForm
        if (form.name.isBlank()) {
            updateState {
                copy(categoryForm = categoryForm.copy(validationErrors = mapOf("name" to "Name is required")))
            }
            return
        }
        val category = ExpenseCategory(
            id = form.id ?: IdGenerator.newId(),
            name = form.name.trim(),
            description = form.description.trim().takeIf { it.isNotBlank() },
            parentId = form.parentId.trim().takeIf { it.isNotBlank() },
        )
        when (val result = expenseRepository.saveCategory(category)) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Category updated" else "Category created"
                updateState {
                    copy(showCategoryDetail = false, selectedCategory = null, categoryForm = CategoryFormState(), successMessage = msg)
                }
                sendEffect(ExpenseEffect.ShowSuccess(msg))
            }
            is Result.Error -> sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Save category failed"))
            is Result.Loading -> {}
        }
    }

    private suspend fun onDeleteCategory(categoryId: String) {
        when (val result = expenseRepository.deleteCategory(categoryId)) {
            is Result.Success -> {
                updateState { copy(showCategoryDetail = false, selectedCategory = null) }
                sendEffect(ExpenseEffect.ShowSuccess("Category deleted"))
            }
            is Result.Error -> sendEffect(ExpenseEffect.ShowError(result.exception.message ?: "Delete failed"))
            is Result.Loading -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validateExpenseForm(form: ExpenseFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.description.isBlank()) errors["description"] = "Description is required"
        val amount = form.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) errors["amount"] = "Amount must be positive"
        if (form.expenseDate.toLongOrNull() == null) errors["expenseDate"] = "Date is required"
        return errors
    }
}
