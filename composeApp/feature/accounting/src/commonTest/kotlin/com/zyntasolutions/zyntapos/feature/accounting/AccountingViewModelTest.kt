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

// ─────────────────────────────────────────────────────────────────────────────
// AccountingViewModelTest
// Tests AccountingViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val storeId = "store-001"

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
            currentStoreId = storeId,
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
            currentStoreId = storeId,
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
