package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.CashFlowLine
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ── Fake ───────────────────────────────────────────────────────────────────────

private class FakeCashFlowRepo(
    private val stubResult: Result<FinancialStatement.CashFlow>,
) : FinancialStatementRepository {

    override suspend fun getTrialBalance(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.TrialBalance> = error("not used in this test suite")

    override suspend fun getProfitAndLoss(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.PAndL> = error("not used in this test suite")

    override suspend fun getBalanceSheet(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.BalanceSheet> = error("not used in this test suite")

    override suspend fun getCashFlowStatement(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.CashFlow> = stubResult

    override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> =
        error("not used in this test suite")

    override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> =
        error("not used in this test suite")

    override suspend fun getGeneralLedger(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ): Result<List<GeneralLedgerEntry>> = error("not used in this test suite")
}

// ── Fixture helpers ────────────────────────────────────────────────────────────

private fun line(label: String, inflow: Double, outflow: Double) = CashFlowLine(
    label = label,
    inflow = inflow,
    outflow = outflow,
    net = inflow - outflow,
)

private fun cashFlow(
    operatingLines: List<CashFlowLine> = emptyList(),
    investingLines: List<CashFlowLine> = emptyList(),
    financingLines: List<CashFlowLine> = emptyList(),
    openingCash: Double = 0.0,
): FinancialStatement.CashFlow {
    val netOp = operatingLines.sumOf { it.net }
    val netInv = investingLines.sumOf { it.net }
    val netFin = financingLines.sumOf { it.net }
    val netChange = netOp + netInv + netFin
    return FinancialStatement.CashFlow(
        dateFrom = "2026-01-01",
        dateTo = "2026-01-31",
        operatingLines = operatingLines,
        investingLines = investingLines,
        financingLines = financingLines,
        netOperating = netOp,
        netInvesting = netInv,
        netFinancing = netFin,
        netChange = netChange,
        openingCash = openingCash,
        closingCash = openingCash + netChange,
    )
}

private fun useCase(result: FinancialStatement.CashFlow) =
    GetCashFlowStatementUseCase(FakeCashFlowRepo(Result.Success(result)))

// ── Test class ─────────────────────────────────────────────────────────────────

class GetCashFlowStatementUseCaseTest {

    /**
     * Operating section: sales inflow 5 000, expense outflow 1 200 → netOperating = 3 800.
     */
    @Test
    fun test_operating_activities_sum_correctly() = runTest {
        val cf = cashFlow(
            operatingLines = listOf(
                line("Sales receipts", inflow = 5_000.0, outflow = 0.0),
                line("Expense payments", inflow = 0.0, outflow = 1_200.0),
            ),
        )
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        assertEquals(3_800.0, result.data.netOperating, absoluteTolerance = 0.005)
        assertEquals(2, result.data.operatingLines.size)
    }

    /**
     * Investing section: purchase outflow 3 000, return inflow 200 → netInvesting = -2 800.
     */
    @Test
    fun test_investing_activities_sum_correctly() = runTest {
        val cf = cashFlow(
            investingLines = listOf(
                line("Inventory purchases", inflow = 0.0, outflow = 3_000.0),
                line("Supplier returns", inflow = 200.0, outflow = 0.0),
            ),
        )
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        assertEquals(-2_800.0, result.data.netInvesting, absoluteTolerance = 0.005)
    }

    /**
     * netChange must equal the arithmetic sum of all three section nets.
     * closingCash must equal openingCash + netChange.
     */
    @Test
    fun test_net_change_equals_sum_of_all_sections() = runTest {
        val cf = cashFlow(
            operatingLines = listOf(line("Sales receipts", inflow = 10_000.0, outflow = 0.0)),
            investingLines = listOf(line("Inventory purchases", inflow = 0.0, outflow = 4_000.0)),
            financingLines = listOf(line("Payroll disbursements", inflow = 0.0, outflow = 2_500.0)),
            openingCash = 1_500.0,
        )
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        val data = result.data
        val expectedNetChange = data.netOperating + data.netInvesting + data.netFinancing
        assertEquals(expectedNetChange, data.netChange, absoluteTolerance = 0.005)
        assertEquals(1_500.0 + expectedNetChange, data.closingCash, absoluteTolerance = 0.005)
    }

    /**
     * An empty period (no transactions) produces zero net change and
     * closingCash equal to openingCash.
     */
    @Test
    fun test_empty_period_returns_zero_net_change() = runTest {
        val cf = cashFlow(openingCash = 2_000.0)
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        val data = result.data
        assertTrue(data.operatingLines.isEmpty())
        assertTrue(data.investingLines.isEmpty())
        assertTrue(data.financingLines.isEmpty())
        assertEquals(0.0, data.netChange, absoluteTolerance = 0.005)
        assertEquals(2_000.0, data.closingCash, absoluteTolerance = 0.005)
    }

    /**
     * Financing section: loan drawdown inflow 20 000, repayment outflow 5 000
     * → netFinancing = 15 000.
     */
    @Test
    fun test_financing_activities_sum_correctly() = runTest {
        val cf = cashFlow(
            financingLines = listOf(
                line("Loan drawdown", inflow = 20_000.0, outflow = 0.0),
                line("Loan repayment", inflow = 0.0, outflow = 5_000.0),
            ),
        )
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        assertEquals(15_000.0, result.data.netFinancing, absoluteTolerance = 0.005)
        assertEquals(2, result.data.financingLines.size)
    }

    /**
     * When net outflows exceed inflows across all sections, netChange is negative
     * and closingCash is less than openingCash.
     */
    @Test
    fun test_negative_net_change_reduces_closing_cash() = runTest {
        val cf = cashFlow(
            operatingLines = listOf(line("Sales receipts", inflow = 1_000.0, outflow = 0.0)),
            investingLines = listOf(line("Equipment purchase", inflow = 0.0, outflow = 8_000.0)),
            openingCash = 5_000.0,
        )
        val result = useCase(cf).execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Success<FinancialStatement.CashFlow>>(result)
        val data = result.data
        assertTrue(data.netChange < 0.0)
        assertEquals(5_000.0 + data.netChange, data.closingCash, absoluteTolerance = 0.005)
    }

    /**
     * Repository error is propagated as [Result.Error].
     */
    @Test
    fun test_repository_error_propagated() = runTest {
        val errorRepo = FakeCashFlowRepo(Result.Error(DatabaseException("DB offline")))
        val useCase = GetCashFlowStatementUseCase(errorRepo)

        val result = useCase.execute("store-1", "2026-01-01", "2026-01-31")

        assertIs<Result.Error>(result)
    }

    /**
     * Individual line net values equal inflow − outflow.
     */
    @Test
    fun test_individual_line_net_equals_inflow_minus_outflow() = runTest {
        val l = line("Test line", inflow = 3_000.0, outflow = 1_200.0)
        assertEquals(1_800.0, l.net, absoluteTolerance = 0.005)
    }
}
