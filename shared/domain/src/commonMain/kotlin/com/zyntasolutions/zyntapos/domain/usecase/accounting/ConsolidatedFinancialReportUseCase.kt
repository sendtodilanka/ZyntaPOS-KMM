package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.result.getOrNull
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
 * When [invoke] is called with `eliminateInterStore = true`, line items whose
 * [FinancialStatementLine.accountId] matches an inter-store transfer pattern
 * (contains "inter-store" or "IST", case-insensitive) are removed from the
 * consolidated lines and their amounts are excluded from the totals. This
 * prevents double-counting of internal inventory movements (e.g., Store A
 * records inter-store revenue while Store B records the corresponding COGS).
 *
 * Multi-currency consolidation is deferred — all amounts are assumed to be in
 * the same base currency.
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
     * @param eliminateInterStore When `true`, removes inter-store transfer lines
     *        (accountId containing "inter-store" or "IST") from the consolidated
     *        result and adjusts totals accordingly. Defaults to `false`.
     * @return [Result] wrapping the consolidated [FinancialStatement.PAndL], or [Result.Error] if
     *         no stores provided or all per-store fetches fail.
     */
    suspend operator fun invoke(
        storeIds: List<String>,
        fromDate: String,
        toDate: String,
        eliminateInterStore: Boolean = false,
    ): Result<FinancialStatement.PAndL> {
        if (storeIds.isEmpty()) {
            return Result.Error(ValidationException("No store IDs provided", field = "storeIds", rule = "REQUIRED"))
        }

        val perStorePandL = mutableListOf<FinancialStatement.PAndL>()
        for (storeId in storeIds) {
            financialStatementRepository.getProfitAndLoss(storeId, fromDate, toDate).getOrNull()
                ?.let { perStorePandL.add(it) }
        }

        if (perStorePandL.isEmpty()) {
            return Result.Error(ValidationException("No P&L data available for any store", field = "storeIds", rule = "NO_DATA"))
        }

        if (perStorePandL.size == 1) {
            return if (eliminateInterStore) {
                Result.Success(eliminateInterStoreLines(perStorePandL.first()))
            } else {
                Result.Success(perStorePandL.first())
            }
        }

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

        val mergedRevenueLines = mergeLines { it.revenueLines }
        val mergedCogsLines = mergeLines { it.cogsLines }
        val mergedExpenseLines = mergeLines { it.expenseLines }

        // Apply inter-store elimination if requested
        val revenueLines: List<FinancialStatementLine>
        val cogsLines: List<FinancialStatementLine>
        val expenseLines: List<FinancialStatementLine>

        if (eliminateInterStore) {
            revenueLines = mergedRevenueLines.filterNot { it.isInterStoreTransfer() }
            cogsLines = mergedCogsLines.filterNot { it.isInterStoreTransfer() }
            expenseLines = mergedExpenseLines.filterNot { it.isInterStoreTransfer() }
        } else {
            revenueLines = mergedRevenueLines
            cogsLines = mergedCogsLines
            expenseLines = mergedExpenseLines
        }

        // Aggregate totals from the (possibly filtered) lines
        val totalRevenue = revenueLines.sumOf { it.amount }
        val totalCogs = cogsLines.sumOf { it.amount }
        val totalExpenses = expenseLines.sumOf { it.amount }
        val grossProfit = totalRevenue - totalCogs
        val netProfit = grossProfit - totalExpenses
        val grossMarginPct = if (totalRevenue > 0.0) grossProfit / totalRevenue * 100.0 else 0.0

        return Result.Success(
            FinancialStatement.PAndL(
                dateFrom = fromDate,
                dateTo = toDate,
                revenueLines = revenueLines,
                cogsLines = cogsLines,
                expenseLines = expenseLines,
                totalRevenue = totalRevenue,
                totalCogs = totalCogs,
                grossProfit = grossProfit,
                totalExpenses = totalExpenses,
                netProfit = netProfit,
                grossMarginPct = grossMarginPct,
            )
        )
    }

    /**
     * Removes inter-store transfer lines from a single P&L and recalculates totals.
     */
    private fun eliminateInterStoreLines(pnl: FinancialStatement.PAndL): FinancialStatement.PAndL {
        val revenueLines = pnl.revenueLines.filterNot { it.isInterStoreTransfer() }
        val cogsLines = pnl.cogsLines.filterNot { it.isInterStoreTransfer() }
        val expenseLines = pnl.expenseLines.filterNot { it.isInterStoreTransfer() }

        val totalRevenue = revenueLines.sumOf { it.amount }
        val totalCogs = cogsLines.sumOf { it.amount }
        val totalExpenses = expenseLines.sumOf { it.amount }
        val grossProfit = totalRevenue - totalCogs
        val netProfit = grossProfit - totalExpenses
        val grossMarginPct = if (totalRevenue > 0.0) grossProfit / totalRevenue * 100.0 else 0.0

        return pnl.copy(
            revenueLines = revenueLines,
            cogsLines = cogsLines,
            expenseLines = expenseLines,
            totalRevenue = totalRevenue,
            totalCogs = totalCogs,
            grossProfit = grossProfit,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            grossMarginPct = grossMarginPct,
        )
    }

}

/**
 * Regex matching inter-store transfer account IDs.
 * Matches account IDs containing "inter-store" or "IST" (case-insensitive).
 * Examples: "inter-store-revenue", "IST-COGS-001", "revenue-inter-store-transfer".
 */
private val INTER_STORE_PATTERN = Regex("(?i)(inter-store|\\bIST\\b)")

/**
 * Returns `true` if this line represents an inter-store transfer that should be
 * eliminated during consolidation.
 */
private fun FinancialStatementLine.isInterStoreTransfer(): Boolean =
    INTER_STORE_PATTERN.containsMatchIn(accountId)
