package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.FinancialStatementLine
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository

/**
 * Aggregates Profit & Loss statements across multiple stores for a date range.
 *
 * Each store's P&L is fetched independently and the results are summed into a
 * consolidated view. Lines with the same accountId are merged (amounts summed);
 * distinct lines are kept separately.
 *
 * Multi-currency consolidation and inter-store elimination are deferred — all
 * amounts are assumed to be in the same base currency.
 *
 * @param financialStatementRepository Source for per-store P&L data.
 */
class ConsolidatedFinancialReportUseCase(
    private val financialStatementRepository: FinancialStatementRepository,
) {
    /**
     * @param storeIds List of store IDs to consolidate.
     * @param fromDate Start of the reporting window (ISO: YYYY-MM-DD).
     * @param toDate   End of the reporting window (ISO: YYYY-MM-DD).
     * @return [Result] wrapping the consolidated [FinancialStatement.PAndL], or failure if
     *         no stores provided or all per-store fetches fail.
     */
    suspend operator fun invoke(
        storeIds: List<String>,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.PAndL> {
        if (storeIds.isEmpty()) return Result.failure(IllegalArgumentException("No store IDs provided"))

        val perStorePandL = storeIds.mapNotNull { storeId ->
            financialStatementRepository.getProfitAndLoss(storeId, fromDate, toDate).getOrNull()
        }

        if (perStorePandL.isEmpty()) {
            return Result.failure(IllegalStateException("No P&L data available for any store"))
        }

        if (perStorePandL.size == 1) return Result.success(perStorePandL.first())

        // Aggregate totals across stores
        val totalRevenue = perStorePandL.sumOf { it.totalRevenue }
        val totalCogs = perStorePandL.sumOf { it.totalCogs }
        val totalExpenses = perStorePandL.sumOf { it.totalExpenses }
        val grossProfit = totalRevenue - totalCogs
        val netProfit = grossProfit - totalExpenses
        val grossMarginPct = if (totalRevenue > 0.0) grossProfit / totalRevenue * 100.0 else 0.0

        // Merge line items: group by accountId, sum amounts, keep first occurrence's metadata
        fun mergeLines(selector: (FinancialStatement.PAndL) -> List<FinancialStatementLine>): List<FinancialStatementLine> {
            val byAccountId = LinkedHashMap<String, FinancialStatementLine>()
            for (pnl in perStorePandL) {
                for (line in selector(pnl)) {
                    val existing = byAccountId[line.accountId]
                    byAccountId[line.accountId] = if (existing == null) {
                        line
                    } else {
                        existing.copy(amount = existing.amount + line.amount)
                    }
                }
            }
            return byAccountId.values.toList()
        }

        return Result.success(
            FinancialStatement.PAndL(
                dateFrom = fromDate,
                dateTo = toDate,
                revenueLines = mergeLines { it.revenueLines },
                cogsLines = mergeLines { it.cogsLines },
                expenseLines = mergeLines { it.expenseLines },
                totalRevenue = totalRevenue,
                totalCogs = totalCogs,
                grossProfit = grossProfit,
                totalExpenses = totalExpenses,
                netProfit = netProfit,
                grossMarginPct = grossMarginPct,
            )
        )
    }
}
