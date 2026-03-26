package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory

/**
 * Immutable UI state for the Expenses feature screens.
 *
 * Consumed by [ExpenseListScreen], [ExpenseDetailScreen], and [ExpenseCategoryListScreen].
 *
 * @property expenses Filtered expense list.
 * @property categories All expense categories for assignment.
 * @property statusFilter Active status filter; null = show all.
 * @property selectedExpense Expense loaded for detail/edit.
 * @property expenseForm Draft for create/edit expense form.
 * @property selectedCategory Category loaded for detail/edit.
 * @property categoryForm Draft for create/edit category form.
 * @property showCategoryDetail Whether the category bottom sheet is visible.
 * @property isLoading True while an async operation is in flight.
 * @property error Transient error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 */
data class ExpenseState(
    // ── List ──────────────────────────────────────────────────────────────
    val expenses: List<Expense> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val statusFilter: Expense.Status? = null,

    // ── Detail / Edit ─────────────────────────────────────────────────────
    val selectedExpense: Expense? = null,
    val expenseForm: ExpenseFormState = ExpenseFormState(),

    // ── Category Management ───────────────────────────────────────────────
    val selectedCategory: ExpenseCategory? = null,
    val categoryForm: CategoryFormState = CategoryFormState(),
    val showCategoryDetail: Boolean = false,

    // ── Budget Tracking (G13) ──────────────────────────────────────────────
    /** Monthly budget per category: categoryId → budget amount. */
    val categoryBudgets: Map<String, Double> = emptyMap(),
    /** Monthly spend per category: categoryId → total spent this month. */
    val categorySpend: Map<String, Double> = emptyMap(),
    /** Manager approval threshold — expenses above this amount require manager sign-off. */
    val approvalThreshold: Double = 1000.0,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** Mutable form fields for expense create/edit operations. */
data class ExpenseFormState(
    val id: String? = null,
    val description: String = "",
    val amount: String = "",
    val categoryId: String = "",
    val expenseDate: String = "",
    val receiptUrl: String = "",
    val notes: String = "",
    /** Vendor/supplier ID associated with this expense. Empty string = no vendor. */
    val vendorId: String = "",
    /** Vendor/supplier display name (read-only, populated from vendorId lookup). */
    val vendorName: String = "",
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

/** Mutable form fields for expense category create/edit operations. */
data class CategoryFormState(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val parentId: String = "",
    /** Monthly budget limit for this category. Empty string = no budget set. */
    val monthlyBudget: String = "",
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)
