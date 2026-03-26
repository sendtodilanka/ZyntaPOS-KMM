package com.zyntasolutions.zyntapos.domain.model

/**
 * An aggregated Profit & Loss statement for a store over a specific period.
 *
 * @property totalRevenue Total sales revenue (sum of completed order totals).
 * @property totalCostOfGoods Total cost of goods sold (COGS).
 * @property grossProfit Revenue minus COGS ([totalRevenue] - [totalCostOfGoods]).
 * @property totalExpenses Sum of all approved expenses in the period.
 * @property operatingExpenses Breakdown of expenses by category name (categoryName -> amount).
 * @property netProfit Gross profit minus total expenses ([grossProfit] - [totalExpenses]).
 */
data class ProfitAndLossReport(
    val totalRevenue: Double,
    val totalCostOfGoods: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val operatingExpenses: Map<String, Double>,
    val netProfit: Double,
)
