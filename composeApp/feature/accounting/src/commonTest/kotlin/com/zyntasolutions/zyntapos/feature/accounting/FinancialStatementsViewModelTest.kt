package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetBalanceSheetUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetCashFlowStatementUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetProfitAndLossUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetTrialBalanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [FinancialStatementsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinancialStatementsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockPandL = FinancialStatement.PAndL(
        dateFrom = "2026-03-01",
        dateTo = "2026-03-31",
        revenueLines = emptyList(),
        cogsLines = emptyList(),
        expenseLines = emptyList(),
        totalRevenue = 50_000.0,
        totalCogs = 20_000.0,
        grossProfit = 30_000.0,
        grossMarginPct = 60.0,
        totalExpenses = 10_000.0,
        netProfit = 20_000.0,
    )

    private val mockBalanceSheet = FinancialStatement.BalanceSheet(
        asOfDate = "2026-03-31",
        assetLines = emptyList(),
        liabilityLines = emptyList(),
        equityLines = emptyList(),
        totalAssets = 100_000.0,
        totalLiabilities = 40_000.0,
        totalEquity = 60_000.0,
        retainedEarnings = 20_000.0,
    )

    private val mockTrialBalance = FinancialStatement.TrialBalance(
        asOfDate = "2026-03-31",
        lines = emptyList(),
        totalDebits = 150_000.0,
        totalCredits = 150_000.0,
        isBalanced = true,
    )

    private val mockCashFlow = FinancialStatement.CashFlow(
        dateFrom = "2026-03-01",
        dateTo = "2026-03-31",
        operatingLines = emptyList(),
        investingLines = emptyList(),
        financingLines = emptyList(),
        netOperating = 15_000.0,
        netInvesting = -5_000.0,
        netFinancing = 0.0,
        netChange = 10_000.0,
        openingCash = 5_000.0,
        closingCash = 15_000.0,
    )

    private var pandlResult: Result<FinancialStatement.PAndL> = Result.Success(mockPandL)
    private var balanceSheetResult: Result<FinancialStatement.BalanceSheet> = Result.Success(mockBalanceSheet)
    private var trialBalanceResult: Result<FinancialStatement.TrialBalance> = Result.Success(mockTrialBalance)
    private var cashFlowResult: Result<FinancialStatement.CashFlow> = Result.Success(mockCashFlow)

    private val fakeStatementRepo = object : FinancialStatementRepository {
        override suspend fun getProfitAndLoss(
            storeId: String, fromDate: String, toDate: String
        ): Result<FinancialStatement.PAndL> = pandlResult

        override suspend fun getBalanceSheet(
            storeId: String, asOfDate: String
        ): Result<FinancialStatement.BalanceSheet> = balanceSheetResult

        override suspend fun getTrialBalance(
            storeId: String, asOfDate: String
        ): Result<FinancialStatement.TrialBalance> = trialBalanceResult

        override suspend fun getCashFlowStatement(
            storeId: String, fromDate: String, toDate: String
        ): Result<FinancialStatement.CashFlow> = cashFlowResult

        override suspend fun getGeneralLedger(
            storeId: String, accountId: String, fromDate: String, toDate: String
        ): Result<List<GeneralLedgerEntry>> = Result.Success(emptyList())

        override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> = Result.Success(Unit)
        override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: FinancialStatementsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FinancialStatementsViewModel(
            getProfitAndLossUseCase = GetProfitAndLossUseCase(fakeStatementRepo),
            getBalanceSheetUseCase = GetBalanceSheetUseCase(fakeStatementRepo),
            getTrialBalanceUseCase = GetTrialBalanceUseCase(fakeStatementRepo),
            getCashFlowStatementUseCase = GetCashFlowStatementUseCase(fakeStatementRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has PROFIT_LOSS as active tab`() {
        assertEquals(FinancialStatementTab.PROFIT_LOSS, viewModel.state.value.activeTab)
    }

    @Test
    fun `initial state has default dates set`() {
        val state = viewModel.state.value
        assertTrue(state.fromDate.isNotBlank())
        assertTrue(state.toDate.isNotBlank())
        assertTrue(state.asOfDate.isNotBlank())
    }

    @Test
    fun `initial state has no loaded statements`() {
        val state = viewModel.state.value
        assertNull(state.pAndL)
        assertNull(state.balanceSheet)
        assertNull(state.trialBalance)
        assertNull(state.cashFlow)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    // ── LoadPandL ──────────────────────────────────────────────────────────────

    @Test
    fun `LoadPandL sets pAndL in state on success`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.pAndL)
        assertEquals(50_000.0, viewModel.state.value.pAndL!!.totalRevenue)
    }

    @Test
    fun `LoadPandL updates storeId and dates in state`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("store-001", viewModel.state.value.storeId)
        assertEquals("2026-03-01", viewModel.state.value.fromDate)
        assertEquals("2026-03-31", viewModel.state.value.toDate)
    }

    @Test
    fun `LoadPandL clears isLoading after success`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoadPandL failure emits ShowError effect`() = runTest {
        pandlResult = Result.Error(DatabaseException("P&L computation failed"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShowError)
            assertTrue((effect as FinancialStatementsEffect.ShowError).message.contains("P&L computation"))
        }
    }

    @Test
    fun `LoadPandL failure sets error in state`() = runTest {
        pandlResult = Result.Error(DatabaseException("P&L computation failed"))
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    // ── LoadBalanceSheet ───────────────────────────────────────────────────────

    @Test
    fun `LoadBalanceSheet sets balanceSheet in state on success`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadBalanceSheet("store-001", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.balanceSheet)
        assertEquals(100_000.0, viewModel.state.value.balanceSheet!!.totalAssets)
    }

    @Test
    fun `LoadBalanceSheet updates asOfDate in state`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadBalanceSheet("store-001", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2026-03-31", viewModel.state.value.asOfDate)
    }

    @Test
    fun `LoadBalanceSheet failure emits ShowError effect`() = runTest {
        balanceSheetResult = Result.Error(DatabaseException("Balance sheet computation failed"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                FinancialStatementsIntent.LoadBalanceSheet("store-001", "2026-03-31")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShowError)
        }
    }

    // ── LoadTrialBalance ───────────────────────────────────────────────────────

    @Test
    fun `LoadTrialBalance sets trialBalance in state on success`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadTrialBalance("store-001", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.trialBalance)
        assertTrue(viewModel.state.value.trialBalance!!.isBalanced)
    }

    @Test
    fun `LoadTrialBalance failure emits ShowError effect`() = runTest {
        trialBalanceResult = Result.Error(DatabaseException("Trial balance computation failed"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                FinancialStatementsIntent.LoadTrialBalance("store-001", "2026-03-31")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShowError)
        }
    }

    // ── LoadCashFlow ───────────────────────────────────────────────────────────

    @Test
    fun `LoadCashFlow sets cashFlow in state on success`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadCashFlow("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.cashFlow)
        assertEquals(15_000.0, viewModel.state.value.cashFlow!!.closingCash)
    }

    @Test
    fun `LoadCashFlow failure emits ShowError effect`() = runTest {
        cashFlowResult = Result.Error(DatabaseException("Cash flow computation failed"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                FinancialStatementsIntent.LoadCashFlow("store-001", "2026-03-01", "2026-03-31")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShowError)
        }
    }

    // ── SwitchTab ──────────────────────────────────────────────────────────────

    @Test
    fun `SwitchTab updates activeTab in state`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.BALANCE_SHEET))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(FinancialStatementTab.BALANCE_SHEET, viewModel.state.value.activeTab)
    }

    @Test
    fun `SwitchTab to BALANCE_SHEET triggers lazy load`() = runTest {
        // Load pAndL first to set storeId
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch to balance sheet tab — should lazily load it
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.BALANCE_SHEET))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.balanceSheet)
    }

    @Test
    fun `SwitchTab to already-loaded tab does not re-fetch`() = runTest {
        // Load pAndL
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val firstPandL = viewModel.state.value.pAndL

        // Switch away and back to PROFIT_LOSS — pAndL is already cached, should not re-fetch
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.BALANCE_SHEET))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.PROFIT_LOSS))
        testDispatcher.scheduler.advanceUntilIdle()

        // pAndL should still be the same (not null)
        assertNotNull(viewModel.state.value.pAndL)
        assertEquals(firstPandL!!.totalRevenue, viewModel.state.value.pAndL!!.totalRevenue)
    }

    // ── SetDateRange ───────────────────────────────────────────────────────────

    @Test
    fun `SetDateRange updates fromDate and toDate`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.SetDateRange("2026-02-01", "2026-02-28"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("2026-02-01", viewModel.state.value.fromDate)
        assertEquals("2026-02-28", viewModel.state.value.toDate)
    }

    @Test
    fun `SetDateRange invalidates pAndL cache`() = runTest {
        // Load P&L first
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.pAndL)

        // Change date range — pAndL should be cleared then re-fetched
        viewModel.handleIntentForTest(FinancialStatementsIntent.SetDateRange("2026-02-01", "2026-02-28"))
        testDispatcher.scheduler.advanceUntilIdle()

        // After advanceUntilIdle, pAndL is re-fetched and set again
        assertNotNull(viewModel.state.value.pAndL)
        assertEquals("2026-02-01", viewModel.state.value.fromDate)
    }

    // ── SetAsOfDate ────────────────────────────────────────────────────────────

    @Test
    fun `SetAsOfDate updates asOfDate`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(FinancialStatementsIntent.SetAsOfDate("2026-02-28"))
            val updated = awaitItem()
            assertEquals("2026-02-28", updated.asOfDate)
        }
    }

    @Test
    fun `SetAsOfDate invalidates balanceSheet and trialBalance caches`() = runTest {
        // Load balance sheet first
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadBalanceSheet("store-001", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.balanceSheet)

        // Verify that SetAsOfDate clears the balanceSheet (pAndL tab is active, so re-fetch is skipped)
        viewModel.state.test {
            awaitItem() // current state
            viewModel.handleIntentForTest(FinancialStatementsIntent.SetAsOfDate("2026-02-28"))
            val cleared = awaitItem()
            assertNull(cleared.balanceSheet)
            assertNull(cleared.trialBalance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Error cleared on success ───────────────────────────────────────────────

    @Test
    fun `LoadPandL clears error on success`() = runTest {
        pandlResult = Result.Error(DatabaseException("error"))
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        pandlResult = Result.Success(mockPandL)
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    // ── ShowDatePicker / HideDatePicker ────────────────────────────────────────

    @Test
    fun `ShowDatePicker FROM sets activeDatePicker to FROM`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.ShowDatePicker(DatePickerField.FROM))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DatePickerField.FROM, viewModel.state.value.activeDatePicker)
    }

    @Test
    fun `ShowDatePicker TO sets activeDatePicker to TO`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.ShowDatePicker(DatePickerField.TO))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DatePickerField.TO, viewModel.state.value.activeDatePicker)
    }

    @Test
    fun `ShowDatePicker AS_OF sets activeDatePicker to AS_OF`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.ShowDatePicker(DatePickerField.AS_OF))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DatePickerField.AS_OF, viewModel.state.value.activeDatePicker)
    }

    @Test
    fun `HideDatePicker resets activeDatePicker to NONE`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.ShowDatePicker(DatePickerField.FROM))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DatePickerField.FROM, viewModel.state.value.activeDatePicker)

        viewModel.handleIntentForTest(FinancialStatementsIntent.HideDatePicker)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(DatePickerField.NONE, viewModel.state.value.activeDatePicker)
    }

    @Test
    fun `initial activeDatePicker is NONE`() {
        assertEquals(DatePickerField.NONE, viewModel.state.value.activeDatePicker)
    }

    // ── ExportCsv ──────────────────────────────────────────────────────────────

    @Test
    fun `ExportCsv on PROFIT_LOSS with data emits ShareExport effect`() = runTest {
        viewModel.handleIntentForTest(
            FinancialStatementsIntent.LoadPandL("store-001", "2026-03-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(FinancialStatementsIntent.ExportCsv)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShareExport)
            val shareEffect = effect as FinancialStatementsEffect.ShareExport
            assertTrue(shareEffect.fileName.startsWith("pnl_"))
            assertTrue(shareEffect.fileName.endsWith(".csv"))
            assertTrue(shareEffect.content.contains("section,accountCode,accountName,amount"))
        }
    }

    @Test
    fun `ExportCsv on BALANCE_SHEET with data emits ShareExport effect`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.BALANCE_SHEET))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(FinancialStatementsIntent.ExportCsv)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShareExport)
            val shareEffect = effect as FinancialStatementsEffect.ShareExport
            assertTrue(shareEffect.fileName.startsWith("balance_sheet_"))
            assertTrue(shareEffect.content.contains("Total Assets"))
        }
    }

    @Test
    fun `ExportCsv on TRIAL_BALANCE with data emits ShareExport effect`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.TRIAL_BALANCE))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(FinancialStatementsIntent.ExportCsv)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShareExport)
            val shareEffect = effect as FinancialStatementsEffect.ShareExport
            assertTrue(shareEffect.fileName.startsWith("trial_balance_"))
            assertTrue(shareEffect.content.contains("accountCode,accountName"))
        }
    }

    @Test
    fun `ExportCsv on CASH_FLOW with data emits ShareExport effect`() = runTest {
        viewModel.handleIntentForTest(FinancialStatementsIntent.SwitchTab(FinancialStatementTab.CASH_FLOW))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(FinancialStatementsIntent.ExportCsv)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShareExport)
            val shareEffect = effect as FinancialStatementsEffect.ShareExport
            assertTrue(shareEffect.fileName.startsWith("cash_flow_"))
            assertTrue(shareEffect.content.contains("Closing Cash Balance"))
        }
    }

    @Test
    fun `ExportCsv on PROFIT_LOSS without data emits ShowError effect`() = runTest {
        // pAndL is null (never loaded)
        viewModel.effects.test {
            viewModel.handleIntentForTest(FinancialStatementsIntent.ExportCsv)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is FinancialStatementsEffect.ShowError)
        }
    }

    // ── Date helper functions ──────────────────────────────────────────────────

    @Test
    fun `toEpochMillisOrNull returns millis for valid ISO date`() {
        val millis = "2026-03-25".toEpochMillisOrNull()
        assertNotNull(millis)
        assertTrue(millis!! > 0L)
    }

    @Test
    fun `toEpochMillisOrNull returns null for invalid date`() {
        assertNull("not-a-date".toEpochMillisOrNull())
        assertNull("".toEpochMillisOrNull())
        assertNull("2026-13-01".toEpochMillisOrNull())
    }

    @Test
    fun `toLocalDateString round-trips with toEpochMillisOrNull`() {
        val original = "2026-03-25"
        val millis = original.toEpochMillisOrNull()
        assertNotNull(millis)
        assertEquals(original, millis!!.toLocalDateString())
    }

    @Test
    fun `toLocalDateString formats epoch zero as 1970-01-01`() {
        assertEquals("1970-01-01", 0L.toLocalDateString())
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun FinancialStatementsViewModel.handleIntentForTest(intent: FinancialStatementsIntent) =
    dispatch(intent)
