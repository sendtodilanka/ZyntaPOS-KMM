package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Aggregates expense records over a date range into a period report.
 *
 * ### Report contents
 * - **totalApproved:** Sum of all [Expense.Status.APPROVED] amounts in range.
 * - **totalPending:** Sum of all [Expense.Status.PENDING] amounts in range.
 * - **totalRejected:** Sum of all [Expense.Status.REJECTED] amounts in range.
 * - **approvedCount / pendingCount / rejectedCount:** Respective record counts.
 * - **byCategory:** Map of categoryId → total *approved* amount per category (null = uncategorised).
 *
 * Only APPROVED expenses are included in the `byCategory` breakdown so the category view
 * reflects actual realised spend, not pending or rejected entries.
 *
 * The report re-emits whenever the underlying expense table changes (live Flow).
 *
 * @param expenseRepository Source of expense records.
 */
class GenerateExpenseReportUseCase(
    private val expenseRepository: ExpenseRepository,
) {

    /**
     * Immutable report value object emitted by this use case.
     *
     * @property from           Report start epoch millis (inclusive).
     * @property to             Report end epoch millis (inclusive).
     * @property totalApproved  Sum of APPROVED expense amounts.
     * @property totalPending   Sum of PENDING expense amounts.
     * @property totalRejected  Sum of REJECTED expense amounts.
     * @property approvedCount  Count of APPROVED expenses.
     * @property pendingCount   Count of PENDING expenses.
     * @property rejectedCount  Count of REJECTED expenses.
     * @property byCategory     Approved totals grouped by categoryId (null = uncategorised).
     */
    data class ExpenseReport(
        val from: Long,
        val to: Long,
        val totalApproved: Double,
        val totalPending: Double,
        val totalRejected: Double,
        val approvedCount: Int,
        val pendingCount: Int,
        val rejectedCount: Int,
        val byCategory: Map<String?, Double>,
    )

    /**
     * @param from Start of the reporting window in epoch milliseconds.
     * @param to   End of the reporting window in epoch milliseconds.
     * @return A [Flow] emitting a new [ExpenseReport] whenever the underlying expense data changes.
     */
    operator fun invoke(from: Long, to: Long): Flow<ExpenseReport> =
        expenseRepository.getByDateRange(from, to).map { expenses ->
            val approved = expenses.filter { it.status == Expense.Status.APPROVED }
            val pending  = expenses.filter { it.status == Expense.Status.PENDING }
            val rejected = expenses.filter { it.status == Expense.Status.REJECTED }

            val byCategory = approved
                .groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            ExpenseReport(
                from = from,
                to = to,
                totalApproved = approved.sumOf { it.amount },
                totalPending  = pending.sumOf  { it.amount },
                totalRejected = rejected.sumOf { it.amount },
                approvedCount = approved.size,
                pendingCount  = pending.size,
                rejectedCount = rejected.size,
                byCategory    = byCategory,
            )
        }
}
