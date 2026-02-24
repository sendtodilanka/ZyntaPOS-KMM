package com.zyntasolutions.zyntapos.feature.expenses

/**
 * One-shot side effects for the Expenses feature.
 */
sealed interface ExpenseEffect {
    data class NavigateToDetail(val expenseId: String?) : ExpenseEffect
    data object NavigateToList : ExpenseEffect
    data class ShowError(val message: String) : ExpenseEffect
    data class ShowSuccess(val message: String) : ExpenseEffect
}
