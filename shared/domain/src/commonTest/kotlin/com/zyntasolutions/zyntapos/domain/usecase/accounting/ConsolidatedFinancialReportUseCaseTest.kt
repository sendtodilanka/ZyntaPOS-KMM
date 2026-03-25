package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.FinancialStatementLine
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun fakeLine(
    accountId: String,
    accountName: String,
    amount: Double,
    accountType: AccountType = AccountType.INCOME,
) = FinancialStatementLine(
    accountId = accountId,
    accountCode = "ACC-$accountId",
    accountName = accountName,
    accountType = accountType,
    subCategory = "Test",
    amount = amount,
)

private fun fakePandL(
    totalRevenue: Double = 1000.0,
    totalCogs: Double = 400.0,
    totalExpenses: Double = 200.0,
    revenueLines: List<FinancialStatementLine> = listOf(fakeLine("r1", "Sales", totalRevenue)),
    cogsLines: List<FinancialStatementLine> = listOf(fakeLine("c1", "COGS", totalCogs, AccountType.COGS)),
    expenseLines: List<FinancialStatementLine> = listOf(fakeLine("e1", "Expenses", totalExpenses, AccountType.EXPENSE)),
) = FinancialStatement.PAndL(
    dateFrom = "2026-01-01",
    dateTo = "2026-03-31",
    revenueLines = revenueLines,
    cogsLines = cogsLines,
    expenseLines = expenseLines,
    totalRevenue = totalRevenue,
    totalCogs = totalCogs,
    grossProfit = totalRevenue - totalCogs,
    totalExpenses = totalExpenses,
    netProfit = totalRevenue - totalCogs - totalExpenses,
    grossMarginPct = if (totalRevenue > 0) (totalRevenue - totalCogs) / totalRevenue * 100.0 else 0.0,
)

private class FakeFinancialStatementRepo(
    private val results: Map<String, FinancialStatement.PAndL>,
) : FinancialStatementRepository {

    override suspend fun getTrialBalance(storeId: String, asOfDate: String) =
        Result.Success(FinancialStatement.TrialBalance(asOfDate, emptyList(), 0.0, 0.0, true))

    override suspend fun getProfitAndLoss(storeId: String, fromDate: String, toDate: String) =
        results[storeId]?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("No data for store $storeId", operation = "getProfitAndLoss"))

    override suspend fun getBalanceSheet(storeId: String, asOfDate: String) =
        Result.Success(FinancialStatement.BalanceSheet(asOfDate, emptyList(), emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0))

    override suspend fun getGeneralLedger(
        storeId: String, accountId: String, fromDate: String, toDate: String,
    ): Result<List<GeneralLedgerEntry>> = Result.Success(emptyList())

    override suspend fun upsertBalance(balance: AccountBalance) = Result.Success(Unit)

    override suspend fun getCashFlowStatement(storeId: String, fromDate: String, toDate: String) =
        Result.Success(FinancialStatement.CashFlow(fromDate, toDate, emptyList(), emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))

    override suspend fun rebuildAllBalances(storeId: String, periodId: String) = Result.Success(Unit)
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class ConsolidatedFinancialReportUseCaseTest {

    @Test
    fun `returns error when no store IDs provided`() = runTest {
        val useCase = ConsolidatedFinancialReportUseCase(FakeFinancialStatementRepo(emptyMap()))
        val result = useCase(emptyList(), "2026-01-01", "2026-03-31")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `returns error when all stores have no data`() = runTest {
        val useCase = ConsolidatedFinancialReportUseCase(FakeFinancialStatementRepo(emptyMap()))
        val result = useCase(listOf("store-1", "store-2"), "2026-01-01", "2026-03-31")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `returns single store data unchanged when only one store`() = runTest {
        val pnl = fakePandL()
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to pnl))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        assertEquals(pnl, result.data)
    }

    @Test
    fun `sums totals across two stores`() = runTest {
        val store1 = fakePandL(totalRevenue = 1000.0, totalCogs = 400.0, totalExpenses = 200.0)
        val store2 = fakePandL(totalRevenue = 2000.0, totalCogs = 800.0, totalExpenses = 300.0)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to store1, "store-2" to store2))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1", "store-2"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        val consolidated = result.data
        assertEquals(3000.0, consolidated.totalRevenue, 0.01)
        assertEquals(1200.0, consolidated.totalCogs, 0.01)
        assertEquals(1800.0, consolidated.grossProfit, 0.01)
        assertEquals(500.0, consolidated.totalExpenses, 0.01)
        assertEquals(1300.0, consolidated.netProfit, 0.01)
    }

    @Test
    fun `calculates grossMarginPct correctly for single store`() = runTest {
        val pnl = fakePandL(totalRevenue = 1000.0, totalCogs = 500.0, totalExpenses = 100.0)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to pnl))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        assertEquals(50.0, result.data.grossMarginPct, 0.01)
    }

    @Test
    fun `merges revenue lines by accountId when stores share same accounts`() = runTest {
        val revenueLines1 = listOf(fakeLine("r1", "Sales", 1000.0))
        val revenueLines2 = listOf(fakeLine("r1", "Sales", 500.0))
        val store1 = fakePandL(totalRevenue = 1000.0, revenueLines = revenueLines1)
        val store2 = fakePandL(totalRevenue = 500.0, revenueLines = revenueLines2)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to store1, "store-2" to store2))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1", "store-2"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        val lines = result.data.revenueLines
        assertEquals(1, lines.size, "Shared accountId should be merged into one line")
        assertEquals(1500.0, lines[0].amount, 0.01)
    }

    @Test
    fun `keeps distinct lines when stores have different accounts`() = runTest {
        val revenueLines1 = listOf(fakeLine("r1", "Sales Store A", 1000.0))
        val revenueLines2 = listOf(fakeLine("r2", "Sales Store B", 500.0))
        val store1 = fakePandL(totalRevenue = 1000.0, revenueLines = revenueLines1)
        val store2 = fakePandL(totalRevenue = 500.0, revenueLines = revenueLines2)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to store1, "store-2" to store2))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1", "store-2"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        assertEquals(2, result.data.revenueLines.size, "Distinct accountIds should produce separate lines")
    }

    @Test
    fun `gracefully excludes stores with no data when others have data`() = runTest {
        val store1 = fakePandL(totalRevenue = 1000.0, totalCogs = 400.0, totalExpenses = 200.0)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to store1))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        // store-2 has no data in the repo — should be silently excluded
        val result = useCase(listOf("store-1", "store-2"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        assertEquals(1000.0, result.data.totalRevenue, 0.01)
    }

    @Test
    fun `zero grossMarginPct when total revenue is zero`() = runTest {
        val pnl = fakePandL(totalRevenue = 0.0, totalCogs = 0.0, totalExpenses = 0.0)
        val repo = FakeFinancialStatementRepo(mapOf("store-1" to pnl))
        val useCase = ConsolidatedFinancialReportUseCase(repo)
        val result = useCase(listOf("store-1"), "2026-01-01", "2026-03-31")
        assertIs<Result.Success<FinancialStatement.PAndL>>(result)
        assertEquals(0.0, result.data.grossMarginPct, 0.001)
    }
}
