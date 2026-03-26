package com.zyntasolutions.zyntapos.feature.expenses

import com.zyntasolutions.zyntapos.domain.model.Expense

/**
 * All user actions for the Expenses feature.
 */
sealed interface ExpenseIntent {

    // ── List ──────────────────────────────────────────────────────────────
    data object LoadExpenses : ExpenseIntent
    data class FilterByStatus(val status: Expense.Status?) : ExpenseIntent

    // ── Detail Navigation ─────────────────────────────────────────────────
    data class SelectExpense(val expenseId: String?) : ExpenseIntent

    // ── Form Editing ──────────────────────────────────────────────────────
    data class UpdateFormField(val field: String, val value: String) : ExpenseIntent
    data object SaveExpense : ExpenseIntent
    data class DeleteExpense(val expenseId: String) : ExpenseIntent

    // ── Approval Workflow ─────────────────────────────────────────────────
    data class ApproveExpense(val expenseId: String) : ExpenseIntent
    data class RejectExpense(val expenseId: String, val reason: String?) : ExpenseIntent

    // ── Category Management ───────────────────────────────────────────────
    data class SelectCategory(val categoryId: String?) : ExpenseIntent
    data class UpdateCategoryField(val field: String, val value: String) : ExpenseIntent
    data object SaveCategory : ExpenseIntent
    data class DeleteCategory(val categoryId: String) : ExpenseIntent
    data object DismissCategoryDetail : ExpenseIntent

    // ── Budget Tracking (G13) ──────────────────────────────────────────────
    /** Load monthly budgets and compute spend per category. */
    data object LoadBudgetData : ExpenseIntent
    /** Set/update the monthly budget for a specific category. */
    data class SetCategoryBudget(val categoryId: String, val amount: Double) : ExpenseIntent
    /** Update the global approval threshold for high-value expenses. */
    data class UpdateApprovalThreshold(val amount: Double) : ExpenseIntent

    // ── Recurring Expenses (G13) ────────────────────────────────────────────
    /** Load all recurring expense templates. */
    data object LoadRecurringExpenses : ExpenseIntent
    /** Show the recurring expense dialog. */
    data object ShowRecurringDialog : ExpenseIntent
    /** Dismiss the recurring expense dialog. */
    data object DismissRecurringDialog : ExpenseIntent
    /** Update a field in the recurring expense form. */
    data class UpdateRecurringField(val field: String, val value: String) : ExpenseIntent
    /** Set the recurring frequency. */
    data class SetRecurringFrequency(val frequency: RecurringFrequency) : ExpenseIntent
    /** Save the recurring expense template. */
    data object SaveRecurringExpense : ExpenseIntent
    /** Delete a recurring expense template. */
    data class DeleteRecurringExpense(val id: String) : ExpenseIntent
    /** Toggle a recurring expense active/inactive. */
    data class ToggleRecurringExpense(val id: String) : ExpenseIntent

    // ── Global ─────────────────────────────────────────────────────────────
    data object DismissMessage : ExpenseIntent
}
