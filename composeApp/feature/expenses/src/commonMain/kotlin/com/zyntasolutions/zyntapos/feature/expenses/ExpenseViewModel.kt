package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostExpenseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.ApproveExpenseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.SaveExpenseUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Expenses feature — Sprints 17–18.
 *
 * Manages:
 * - Expense list with optional status filter
 * - Expense create/edit form lifecycle
 * - Approve/reject workflow (STORE_MANAGER / ACCOUNTANT roles)
 * - Expense category management (CRUD in a modal sheet)
 *
 * @param expenseRepository              Expense CRUD, approval, and category operations.
 * @param saveExpenseUseCase             Validates and persists expenses.
 * @param approveExpenseUseCase          Approve/reject workflow.
 * @param authRepository                 Provides the active auth session for resolving currentUserId and storeId.
 * @param postExpenseJournalEntryUseCase Auto-posts a double-entry journal for each new expense (best-effort).
 */
class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val saveExpenseUseCase: SaveExpenseUseCase,
    private val approveExpenseUseCase: ApproveExpenseUseCase,
    private val authRepository: AuthRepository,
    private val postExpenseJournalEntryUseCase: PostExpenseJournalEntryUseCase,
    private val settingsRepository: SettingsRepository,
    private val auditLogger: SecurityAuditLogger,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<ExpenseState, ExpenseIntent, ExpenseEffect>(ExpenseState()) {

    private var currentUserId: String = "unknown"
    private var storeId: String = ""

    init {
        analytics.logScreenView("Expenses", "ExpenseViewModel")
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            currentUserId = session?.id ?: "unknown"
            storeId = session?.storeId ?: ""
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

            is ExpenseIntent.LoadBudgetData -> onLoadBudgetData()
            is ExpenseIntent.SetCategoryBudget -> onSetCategoryBudget(intent.categoryId, intent.amount)
            is ExpenseIntent.UpdateApprovalThreshold -> onUpdateApprovalThreshold(intent.amount)

            // ── Recurring Expenses (G13) ─────────────────────────────────────
            is ExpenseIntent.LoadRecurringExpenses -> onLoadRecurringExpenses()
            is ExpenseIntent.ShowRecurringDialog -> updateState {
                copy(showRecurringDialog = true, recurringForm = RecurringExpenseFormState())
            }
            is ExpenseIntent.DismissRecurringDialog -> updateState {
                copy(showRecurringDialog = false, recurringForm = RecurringExpenseFormState())
            }
            is ExpenseIntent.UpdateRecurringField -> onUpdateRecurringField(intent.field, intent.value)
            is ExpenseIntent.SetRecurringFrequency -> updateState {
                copy(recurringForm = recurringForm.copy(frequency = intent.frequency))
            }
            is ExpenseIntent.SaveRecurringExpense -> onSaveRecurringExpense()
            is ExpenseIntent.DeleteRecurringExpense -> onDeleteRecurringExpense(intent.id)
            is ExpenseIntent.ToggleRecurringExpense -> onToggleRecurringExpense(intent.id)

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
                // ── Best-effort journal entry posting (new expenses only) ───────
                if (!form.isEditing) {
                    val entryDate = Instant.fromEpochMilliseconds(expense.expenseDate)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date.toString()
                    postExpenseJournalEntryUseCase.execute(
                        storeId = storeId,
                        expenseId = expense.id,
                        amount = expense.amount,
                        expenseAccountCode = "6900",  // Miscellaneous Expense — default until category mapping is built
                        paymentAccountCode = "1010",  // Cash
                        createdBy = currentUserId,
                        description = expense.description,
                        entryDate = entryDate,
                        now = expense.expenseDate,
                    )
                    // Ignore result — do not fail the save if journal posting fails
                }
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
            is Result.Success -> {
                sendEffect(ExpenseEffect.ShowSuccess("Expense approved"))
                val amount = currentState.expenses.find { it.id == expenseId }?.amount ?: 0.0
                auditLogger.logExpenseApproved(currentUserId, expenseId, amount)
            }
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

    // ── Budget Tracking (G13) ─────────────────────────────────────────────

    private suspend fun onLoadBudgetData() {
        // Load saved budgets from settings
        val budgets = mutableMapOf<String, Double>()
        currentState.categories.forEach { cat ->
            val key = "expense.budget.${cat.id}"
            settingsRepository.get(key)?.toDoubleOrNull()?.let { budgets[cat.id] = it }
        }
        // Load approval threshold
        val threshold = settingsRepository.get("expense.approval_threshold")?.toDoubleOrNull() ?: 1000.0

        // Compute monthly spend per category from current expenses
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val currentMonth = now.toLocalDateTime(tz).monthNumber
        val currentYear = now.toLocalDateTime(tz).year

        val monthlySpend = mutableMapOf<String, Double>()
        currentState.expenses
            .filter { expense ->
                val dt = Instant.fromEpochMilliseconds(expense.expenseDate).toLocalDateTime(tz)
                dt.monthNumber == currentMonth && dt.year == currentYear
            }
            .forEach { expense ->
                val catId = expense.categoryId ?: return@forEach
                monthlySpend[catId] = (monthlySpend[catId] ?: 0.0) + expense.amount
            }

        updateState {
            copy(
                categoryBudgets = budgets,
                categorySpend = monthlySpend,
                approvalThreshold = threshold,
            )
        }
    }

    private suspend fun onSetCategoryBudget(categoryId: String, amount: Double) {
        settingsRepository.set("expense.budget.$categoryId", amount.toString())
        updateState {
            copy(categoryBudgets = categoryBudgets + (categoryId to amount))
        }
        sendEffect(ExpenseEffect.ShowSuccess("Budget updated"))
    }

    private suspend fun onUpdateApprovalThreshold(amount: Double) {
        settingsRepository.set("expense.approval_threshold", amount.toString())
        updateState { copy(approvalThreshold = amount) }
        sendEffect(ExpenseEffect.ShowSuccess("Approval threshold updated"))
    }

    // ── Recurring Expenses (G13) ────────────────────────────────────────────

    private suspend fun onLoadRecurringExpenses() {
        val entries = mutableListOf<RecurringExpenseEntry>()
        // Load all recurring expense templates stored via settings key pattern
        val categories = currentState.categories
        for (cat in categories) {
            settingsRepository.get("expense.recurring.${cat.id}")
                ?.split("|")
                ?.takeIf { it.size >= 7 }
                ?.let { parts ->
                    entries.add(
                        RecurringExpenseEntry(
                            id = parts[0],
                            description = parts[1],
                            amount = parts[2].toDoubleOrNull() ?: 0.0,
                            categoryId = cat.id,
                            categoryName = cat.name,
                            frequency = runCatching { RecurringFrequency.valueOf(parts[3]) }
                                .getOrDefault(RecurringFrequency.MONTHLY),
                            isActive = parts[4].toBooleanStrictOrNull() ?: true,
                            nextDueDate = parts[5],
                            vendorName = parts[6],
                        ),
                    )
                }
        }
        // Also check for non-category-specific entries
        for (i in 0 until 50) {
            settingsRepository.get("expense.recurring.entry.$i")
                ?.split("|")
                ?.takeIf { it.size >= 8 }
                ?.let { parts ->
                    entries.add(
                        RecurringExpenseEntry(
                            id = parts[0],
                            description = parts[1],
                            amount = parts[2].toDoubleOrNull() ?: 0.0,
                            categoryId = parts[3],
                            categoryName = categories.find { it.id == parts[3] }?.name ?: parts[3],
                            frequency = runCatching { RecurringFrequency.valueOf(parts[4]) }
                                .getOrDefault(RecurringFrequency.MONTHLY),
                            isActive = parts[5].toBooleanStrictOrNull() ?: true,
                            nextDueDate = parts[6],
                            vendorName = parts[7],
                        ),
                    )
                }
        }
        updateState { copy(recurringExpenses = entries) }
    }

    private fun onUpdateRecurringField(field: String, value: String) {
        updateState {
            copy(
                recurringForm = when (field) {
                    "description" -> recurringForm.copy(description = value)
                    "amount" -> recurringForm.copy(amount = value)
                    "categoryId" -> recurringForm.copy(categoryId = value)
                    "startDate" -> recurringForm.copy(startDate = value)
                    "vendorId" -> recurringForm.copy(vendorId = value)
                    "vendorName" -> recurringForm.copy(vendorName = value)
                    else -> recurringForm
                },
            )
        }
    }

    private suspend fun onSaveRecurringExpense() {
        val form = currentState.recurringForm
        val errors = mutableMapOf<String, String>()
        if (form.description.isBlank()) errors["description"] = "Description is required"
        val amount = form.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) errors["amount"] = "Amount must be positive"

        if (errors.isNotEmpty()) {
            updateState {
                copy(recurringForm = recurringForm.copy(validationErrors = errors))
            }
            return
        }

        val id = form.id ?: IdGenerator.newId()
        val entryIndex = currentState.recurringExpenses.size
        val key = "expense.recurring.entry.$entryIndex"
        val value = listOf(
            id,
            form.description,
            form.amount,
            form.categoryId,
            form.frequency.name,
            "true",
            form.startDate.ifBlank { Clock.System.now().toEpochMilliseconds().toString() },
            form.vendorName,
        ).joinToString("|")

        settingsRepository.set(key, value)

        updateState {
            copy(
                showRecurringDialog = false,
                recurringForm = RecurringExpenseFormState(),
                successMessage = "Recurring expense saved",
            )
        }
        onLoadRecurringExpenses()
    }

    private suspend fun onDeleteRecurringExpense(id: String) {
        val entries = currentState.recurringExpenses
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            settingsRepository.set("expense.recurring.entry.$index", "")
            onLoadRecurringExpenses()
            updateState { copy(successMessage = "Recurring expense deleted") }
        }
    }

    private suspend fun onToggleRecurringExpense(id: String) {
        val entries = currentState.recurringExpenses
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val entry = entries[index]
        val key = "expense.recurring.entry.$index"
        val value = listOf(
            entry.id,
            entry.description,
            entry.amount.toString(),
            entry.categoryId,
            entry.frequency.name,
            (!entry.isActive).toString(),
            entry.nextDueDate,
            entry.vendorName,
        ).joinToString("|")
        settingsRepository.set(key, value)
        onLoadRecurringExpenses()
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
