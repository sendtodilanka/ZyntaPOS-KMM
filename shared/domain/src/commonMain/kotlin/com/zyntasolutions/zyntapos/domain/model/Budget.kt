package com.zyntasolutions.zyntapos.domain.model

/**
 * A budget allocation for a store or specific expense category within a period.
 *
 * @property id Unique identifier (UUID v4).
 * @property storeId The store this budget belongs to.
 * @property categoryId FK to expense category. Null means whole-store budget (not category-specific).
 * @property periodStart ISO-8601 date string for the budget period start (inclusive).
 * @property periodEnd ISO-8601 date string for the budget period end (inclusive).
 * @property budgetAmount The total allocated budget amount for the period.
 * @property spentAmount The cumulative amount spent against this budget so far.
 * @property name Human-readable budget name (e.g., "Q1 2026 Marketing").
 * @property createdAt Epoch millis when the budget was created.
 * @property updatedAt Epoch millis when the budget was last modified.
 */
data class Budget(
    val id: String,
    val storeId: String,
    val categoryId: String? = null,
    val periodStart: String,
    val periodEnd: String,
    val budgetAmount: Double,
    val spentAmount: Double = 0.0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(budgetAmount > 0.0) { "Budget amount must be positive" }
        require(spentAmount >= 0.0) { "Spent amount cannot be negative" }
    }
}
