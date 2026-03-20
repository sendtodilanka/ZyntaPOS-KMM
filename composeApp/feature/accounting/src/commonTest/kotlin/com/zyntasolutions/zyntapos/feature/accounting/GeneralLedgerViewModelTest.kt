package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetGeneralLedgerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Unit tests for [GeneralLedgerViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralLedgerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val cashAccount = Account(
        id = "acct-001",
        accountCode = "1010",
        accountName = "Cash",
        accountType = AccountType.ASSET,
        subCategory = "Current Assets",
        normalBalance = NormalBalance.DEBIT,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val mockLedgerEntry = GeneralLedgerEntry(
        lineId = "line-001",
        journalEntryId = "je-001",
        entryDate = "2026-03-01",
        description = "Opening cash",
        referenceType = "MANUAL",
        debit = 5000.0,
        credit = 0.0,
        runningBalance = 5000.0,
        isPosted = true,
    )

    private val accountsFlow = MutableStateFlow(listOf(cashAccount))
    private var ledgerResult: Result<List<GeneralLedgerEntry>> = Result.Success(listOf(mockLedgerEntry))

    private val fakeAccountRepo = object : AccountRepository {
        override fun getAll(storeId: String): Flow<List<Account>> = accountsFlow
        override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
            MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Account?> = Result.Success(null)
        override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
            Result.Success(null)
        override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
            Result.Success(null)
        override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
            MutableStateFlow(emptyList())
        override suspend fun create(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun update(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> =
            Result.Success(false)
        override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> = Result.Success(Unit)
    }

    private val fakeStatementRepo = object : FinancialStatementRepository {
        override suspend fun getGeneralLedger(
            storeId: String, accountId: String, fromDate: String, toDate: String
        ): Result<List<GeneralLedgerEntry>> = ledgerResult

        override suspend fun getTrialBalance(storeId: String, asOfDate: String): Result<FinancialStatement.TrialBalance> =
            Result.Success(FinancialStatement.TrialBalance(asOfDate = asOfDate, lines = emptyList(), totalDebits = 0.0, totalCredits = 0.0, isBalanced = true))
        override suspend fun getProfitAndLoss(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.PAndL> =
            Result.Success(FinancialStatement.PAndL(dateFrom = fromDate, dateTo = toDate, revenueLines = emptyList(), cogsLines = emptyList(), expenseLines = emptyList(), grossProfit = 0.0, grossMarginPct = 0.0, netProfit = 0.0, totalRevenue = 0.0, totalCogs = 0.0, totalExpenses = 0.0))
        override suspend fun getBalanceSheet(storeId: String, asOfDate: String): Result<FinancialStatement.BalanceSheet> =
            Result.Success(FinancialStatement.BalanceSheet(asOfDate = asOfDate, assetLines = emptyList(), liabilityLines = emptyList(), equityLines = emptyList(), totalAssets = 0.0, totalLiabilities = 0.0, totalEquity = 0.0, retainedEarnings = 0.0))
        override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> = Result.Success(Unit)
        override suspend fun getCashFlowStatement(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.CashFlow> =
            Result.Success(FinancialStatement.CashFlow(dateFrom = fromDate, dateTo = toDate, operatingLines = emptyList(), investingLines = emptyList(), financingLines = emptyList(), netOperating = 0.0, netInvesting = 0.0, netFinancing = 0.0, netChange = 0.0, openingCash = 0.0, closingCash = 0.0))
        override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: GeneralLedgerViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GeneralLedgerViewModel(
            getAccountsUseCase = GetAccountsUseCase(fakeAccountRepo),
            getGeneralLedgerUseCase = GetGeneralLedgerUseCase(fakeStatementRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty accounts and no selection`() {
        val state = viewModel.state.value
        assertTrue(state.accounts.isEmpty())
        assertNull(state.selectedAccountId)
        assertTrue(state.entries.isEmpty())
        assertNull(state.error)
    }

    // ── LoadAccounts ───────────────────────────────────────────────────────────

    @Test
    fun `LoadAccounts populates accounts from repository`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.accounts.size)
        assertEquals("Cash", viewModel.state.value.accounts.first().accountName)
    }

    @Test
    fun `LoadAccounts sets storeId in state`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("store-001", viewModel.state.value.storeId)
    }

    @Test
    fun `LoadAccounts sets default date range`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Default dates should be set to current month
        assertTrue(viewModel.state.value.fromDate.isNotBlank())
        assertTrue(viewModel.state.value.toDate.isNotBlank())
    }

    // ── SelectAccount ──────────────────────────────────────────────────────────

    @Test
    fun `SelectAccount sets selectedAccountId in state`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("acct-001", viewModel.state.value.selectedAccountId)
    }

    @Test
    fun `SelectAccount loads ledger entries`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.entries.size)
    }

    @Test
    fun `SelectAccount clears entries first then reloads`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem() // current state
            viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
            val intermediate = awaitItem()
            // State where entries are cleared
            assertTrue(intermediate.entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SelectAccount ledger failure emits ShowError effect`() = runTest {
        ledgerResult = Result.Error(DatabaseException("Ledger computation failed"))
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is GeneralLedgerEffect.ShowError)
            assertTrue((effect as GeneralLedgerEffect.ShowError).message.contains("Ledger computation"))
        }
    }

    // ── SetDateRange ───────────────────────────────────────────────────────────

    @Test
    fun `SetDateRange updates fromDate and toDate`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(GeneralLedgerIntent.SetDateRange("2026-01-01", "2026-01-31"))
            val updated = awaitItem()
            assertEquals("2026-01-01", updated.fromDate)
            assertEquals("2026-01-31", updated.toDate)
        }
    }

    @Test
    fun `SetDateRange re-fetches entries when account is selected`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Change range
        viewModel.handleIntentForTest(GeneralLedgerIntent.SetDateRange("2026-02-01", "2026-02-28"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2026-02-01", viewModel.state.value.fromDate)
        assertEquals("2026-02-28", viewModel.state.value.toDate)
        assertEquals(1, viewModel.state.value.entries.size) // fetched again
    }

    // ── Refresh ────────────────────────────────────────────────────────────────

    @Test
    fun `Refresh with selected account reloads entries`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(GeneralLedgerIntent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.entries.size)
    }

    @Test
    fun `Refresh without selected account is a no-op`() = runTest {
        viewModel.handleIntentForTest(GeneralLedgerIntent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    // ── Error state cleared on success ────────────────────────────────────────

    @Test
    fun `SelectAccount clears error on success`() = runTest {
        ledgerResult = Result.Error(DatabaseException("error"))
        viewModel.handleIntentForTest(GeneralLedgerIntent.LoadAccounts("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        ledgerResult = Result.Success(listOf(mockLedgerEntry))
        viewModel.handleIntentForTest(GeneralLedgerIntent.SelectAccount("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun GeneralLedgerViewModel.handleIntentForTest(intent: GeneralLedgerIntent) =
    dispatch(intent)
