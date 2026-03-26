package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ProfitAndLossReport
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/**
 * Generates a Profit & Loss report for a store over a specific period.
 *
 * Aggregates sales revenue from [GenerateSalesReportUseCase] and approved expenses
 * from [ExpenseRepository] to compute gross profit, operating expense breakdown,
 * and net profit.
 *
 * @param expenseRepository   Source of expense data.
 * @param generateSalesReport Source of sales revenue data.
 */
class GenerateProfitAndLossUseCase(
    private val expenseRepository: ExpenseRepository,
    private val generateSalesReport: GenerateSalesReportUseCase,
) {

    /**
     * @param storeId     The store to generate the report for.
     * @param periodStart Epoch millis for the report period start (inclusive).
     * @param periodEnd   Epoch millis for the report period end (inclusive).
     * @return [Result.Success] with [ProfitAndLossReport] on success,
     *         [Result.Error] on validation or data failure.
     */
    suspend operator fun invoke(
        storeId: String,
        periodStart: Long,
        periodEnd: Long,
    ): Result<ProfitAndLossReport> {
        if (periodStart >= periodEnd) {
            return Result.Error(
                ValidationException(
                    message = "Period start must be before period end",
                    field = "periodStart",
                    rule = "DATE_RANGE",
                ),
            )
        }

        // Collect sales report (Flow → single emission via .first())
        val salesReport = generateSalesReport(
            from = Instant.fromEpochMilliseconds(periodStart),
            to = Instant.fromEpochMilliseconds(periodEnd),
            storeId = storeId,
        ).first()

        val totalRevenue = salesReport.totalSales

        // COGS: sum of (cost * qty) for top products — approximated as 0.0 since
        // cost-of-goods data is not available in the sales report. Future enhancement
        // will integrate with inventory cost tracking.
        val totalCostOfGoods = 0.0
        val grossProfit = totalRevenue - totalCostOfGoods

        // Get approved expenses for the period
        val expenseTotalResult = expenseRepository.getTotalByPeriod(periodStart, periodEnd)
        val totalExpenses = when (expenseTotalResult) {
            is Result.Success -> expenseTotalResult.data
            is Result.Error -> return expenseTotalResult
            is Result.Loading -> return Result.Loading
        }

        // Get expense breakdown by category
        val expenseList = expenseRepository.getByDateRange(periodStart, periodEnd).first()
        val approvedExpenses = expenseList.filter { it.status == Expense.Status.APPROVED }
        val operatingExpenses = approvedExpenses
            .groupBy { it.categoryId ?: "Uncategorized" }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

        val netProfit = grossProfit - totalExpenses

        return Result.Success(
            ProfitAndLossReport(
                totalRevenue = totalRevenue,
                totalCostOfGoods = totalCostOfGoods,
                grossProfit = grossProfit,
                totalExpenses = totalExpenses,
                operatingExpenses = operatingExpenses,
                netProfit = netProfit,
            ),
        )
    }
}
