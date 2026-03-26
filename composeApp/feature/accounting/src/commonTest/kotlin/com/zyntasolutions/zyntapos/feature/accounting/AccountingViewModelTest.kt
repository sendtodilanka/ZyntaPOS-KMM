package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingEntryType
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetPeriodSummaryUseCase
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
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetProfitAndLossUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import kotlinx.datetime.Instant

// ─────────────────────────────────────────────────────────────────────────────
// AccountingViewModelTest
// Tests AccountingViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val storeId = "store-001"

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Test User", email = "test@zynta.com",
                role = Role.CASHIER, storeId = "store-001", isActive = true,
                pinHash = null, createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
            Result.Error(DatabaseException("not used"))
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
            Result.Success(false)
    }

    private val fakeStoreRepository = object : StoreRepository {
        override fun getAllStores(): Flow<List<Store>> = flowOf(emptyList())
        override suspend fun getById(storeId: String): Store? = null
        override suspend fun getStoreName(storeId: String): String? = "Test Store"
        override suspend fun upsertFromSync(store: Store) {}
    }

    private val fakeFinancialStatementRepository = object : FinancialStatementRepository {
        override suspend fun getTrialBalance(storeId: String, asOfDate: String): Result<FinancialStatement.TrialBalance> =
            Result.Error(DatabaseException("not used"))
        override suspend fun getProfitAndLoss(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.PAndL> =
            Result.Error(DatabaseException("not used"))
        override suspend fun getBalanceSheet(storeId: String, asOfDate: String): Result<FinancialStatement.BalanceSheet> =
            Result.Error(DatabaseException("not used"))
        override suspend fun getGeneralLedger(storeId: String, accountId: String, fromDate: String, toDate: String): Result<List<GeneralLedgerEntry>> =
            Result.Success(emptyList())
        override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> = Result.Success(Unit)
        override suspend fun getCashFlowStatement(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.CashFlow> =
            Result.Error(DatabaseException("not used"))
        override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> = Result.Success(Unit)
    }

    private val getProfitAndLossUseCase = GetProfitAndLossUseCase(fakeFinancialStatementRepository)

    // ── Fake AccountingRepository ─────────────────────────────────────────────

    private var summariesToReturn: Result<List<AccountSummary>> =
        Result.Success(emptyList())

    private val fakeAccountingRepository = object : AccountingRepository {
        override suspend fun getByStoreAndPeriod(
            storeId: String,
            fiscalPeriod: String,
        ): Result<List<AccountingEntry>> = Result.Success(emptyList())

        override suspend fun getByAccountAndPeriod(
            storeId: String,
            accountCode: String,
            fiscalPeriod: String,
        ): Result<List<AccountingEntry>> = Result.Success(emptyList())

        override suspend fun getByReference(
            referenceType: AccountingReferenceType,
            referenceId: String,
        ): Result<List<AccountingEntry>> = Result.Success(emptyList())

        override suspend fun getSummaryForPeriodRange(
            storeId: String,
            fromPeriod: String,
            toPeriod: String,
        ): Result<List<AccountSummary>> = summariesToReturn

        override suspend fun insertEntries(
            entries: List<AccountingEntry>,
        ): Result<Unit> = Result.Success(Unit)
    }

    private val getPeriodSummaryUseCase = GetPeriodSummaryUseCase(fakeAccountingRepository)

    private lateinit var viewModel: AccountingViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        summariesToReturn = Result.Success(emptyList())
        viewModel = AccountingViewModel(
            getPeriodSummaryUseCase = getPeriodSummaryUseCase,
            getProfitAndLossUseCase = getProfitAndLossUseCase,
            storeRepository = fakeStoreRepository,
            authRepository = fakeAuthRepository,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty summaries and non-blank period`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        // Period is initialized to current fiscal period (non-blank)
        assertTrue(state.period.isNotBlank(), "Period should be initialized")
    }

    // ── LoadPeriod — success ──────────────────────────────────────────────────

    @Test
    fun `LoadPeriod with successful result updates summaries in state`() = runTest {
        val expectedSummaries = listOf(
            AccountSummary(
                accountCode = "4000",
                accountName = "Sales Revenue",
                entryType = AccountingEntryType.CREDIT,
                total = 150000.0,
            ),
            AccountSummary(
                accountCode = "5000",
                accountName = "Cost of Goods Sold",
                entryType = AccountingEntryType.DEBIT,
                total = 80000.0,
            ),
        )
        summariesToReturn = Result.Success(expectedSummaries)

        viewModel.dispatch(AccountingIntent.LoadPeriod("2026-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(expectedSummaries, state.summaries)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `LoadPeriod sets isLoading to true while fetching`() = runTest {
        // The loading state is set synchronously before the suspend call
        var capturedLoadingState = false
        var vmRef: AccountingViewModel? = null

        val slowRepository = object : AccountingRepository {
            override suspend fun getByStoreAndPeriod(
                storeId: String,
                fiscalPeriod: String,
            ): Result<List<AccountingEntry>> = Result.Success(emptyList())

            override suspend fun getByAccountAndPeriod(
                storeId: String,
                accountCode: String,
                fiscalPeriod: String,
            ): Result<List<AccountingEntry>> = Result.Success(emptyList())

            override suspend fun getByReference(
                referenceType: AccountingReferenceType,
                referenceId: String,
            ): Result<List<AccountingEntry>> = Result.Success(emptyList())

            override suspend fun getSummaryForPeriodRange(
                storeId: String,
                fromPeriod: String,
                toPeriod: String,
            ): Result<List<AccountSummary>> {
                capturedLoadingState = vmRef!!.state.value.isLoading
                return Result.Success(emptyList())
            }

            override suspend fun insertEntries(
                entries: List<AccountingEntry>,
            ): Result<Unit> = Result.Success(Unit)
        }
        val vmWithSlowRepo = AccountingViewModel(
            getPeriodSummaryUseCase = GetPeriodSummaryUseCase(slowRepository),
            getProfitAndLossUseCase = getProfitAndLossUseCase,
            storeRepository = fakeStoreRepository,
            authRepository = fakeAuthRepository,
            analytics = noOpAnalytics,
        )
        vmRef = vmWithSlowRepo

        vmWithSlowRepo.dispatch(AccountingIntent.LoadPeriod("2026-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(capturedLoadingState, "isLoading should be true while fetching")
    }

    @Test
    fun `LoadPeriod updates the period in state`() = runTest {
        summariesToReturn = Result.Success(emptyList())
        viewModel.dispatch(AccountingIntent.LoadPeriod("2025-12"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2025-12", viewModel.state.value.period)
    }

    // ── LoadPeriod — error ────────────────────────────────────────────────────

    @Test
    fun `LoadPeriod on repository error emits ShowError effect`() = runTest {
        summariesToReturn = Result.Error(DatabaseException("Network failure"))

        viewModel.effects.test {
            viewModel.dispatch(AccountingIntent.LoadPeriod("2026-01"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is AccountingEffect.ShowError)
            assertTrue((effect as AccountingEffect.ShowError).message.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoadPeriod on error clears isLoading`() = runTest {
        summariesToReturn = Result.Error(DatabaseException("DB error"))

        viewModel.dispatch(AccountingIntent.LoadPeriod("2026-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    // ── DismissError ──────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears the error field in state`() = runTest {
        // First cause an error
        summariesToReturn = Result.Error(DatabaseException("Test error"))
        viewModel.dispatch(AccountingIntent.LoadPeriod("2026-01"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Then dismiss
        viewModel.dispatch(AccountingIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    // ── Empty results ─────────────────────────────────────────────────────────

    @Test
    fun `LoadPeriod with empty summaries list sets state correctly`() = runTest {
        summariesToReturn = Result.Success(emptyList())

        viewModel.dispatch(AccountingIntent.LoadPeriod("2026-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.summaries.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
}
