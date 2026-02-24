package com.zyntasolutions.zyntapos.domain.model

/**
 * A single expense record logged by staff.
 *
 * @property id Unique identifier (UUID v4).
 * @property storeId The store this expense belongs to. Null = current store.
 * @property categoryId FK to [ExpenseCategory]. Null = uncategorized.
 * @property amount Expense amount in the store's operating currency.
 * @property description Short description of what was purchased.
 * @property expenseDate Epoch millis when the expense was incurred.
 * @property receiptUrl Optional URL to a receipt image.
 * @property isRecurring True if generated from a [RecurringExpense] schedule.
 * @property status Approval workflow status.
 * @property approvedBy User ID of the approver.
 * @property approvedAt Epoch millis of approval/rejection.
 * @property rejectReason Explanation provided when rejecting.
 * @property createdBy User ID of the staff who logged the expense.
 */
data class Expense(
    val id: String,
    val storeId: String? = null,
    val categoryId: String? = null,
    val amount: Double,
    val description: String,
    val expenseDate: Long,
    val receiptUrl: String? = null,
    val isRecurring: Boolean = false,
    val status: Status = Status.PENDING,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val rejectReason: String? = null,
    val createdBy: String? = null,
) {
    enum class Status { PENDING, APPROVED, REJECTED }

    init {
        require(amount > 0.0) { "Expense amount must be positive" }
    }
}

/**
 * A hierarchical expense category (chart-of-accounts node).
 *
 * @property id Unique identifier (UUID v4).
 * @property name Category name (e.g., "Utilities", "Office Supplies").
 * @property description Optional description.
 * @property parentId FK to the parent category for sub-category grouping.
 */
data class ExpenseCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val parentId: String? = null,
)

/**
 * A recurring expense rule that auto-generates [Expense] records on a schedule.
 *
 * @property id Unique identifier (UUID v4).
 * @property storeId Store scope. Null = current store.
 * @property categoryId FK to [ExpenseCategory].
 * @property amount Default amount per recurrence.
 * @property description Default description text for generated expenses.
 * @property frequency Recurrence interval.
 * @property startDate Epoch millis of the first occurrence.
 * @property endDate Epoch millis after which no more expenses are generated.
 * @property isActive Whether new expenses will continue to be generated.
 * @property lastRun Epoch millis of the most recent successful generation.
 */
data class RecurringExpense(
    val id: String,
    val storeId: String? = null,
    val categoryId: String? = null,
    val amount: Double,
    val description: String,
    val frequency: Frequency = Frequency.MONTHLY,
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val lastRun: Long? = null,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY }

    init {
        require(amount > 0.0) { "Recurring expense amount must be positive" }
    }
}
