package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeleteDraftEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetJournalEntriesUseCase
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
 * Unit tests for [JournalEntryListViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEntryListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val draftEntry = JournalEntry(
        id = "je-001",
        entryNumber = 1,
        storeId = "store-001",
        entryDate = "2026-03-01",
        entryTime = 1_700_000_000_000L,
        description = "Opening cash entry",
        referenceType = JournalReferenceType.MANUAL,
        isPosted = false,
        createdBy = "user-001",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val postedEntry = JournalEntry(
        id = "je-002",
        entryNumber = 2,
        storeId = "store-001",
        entryDate = "2026-03-02",
        entryTime = 1_700_000_001_000L,
        description = "Sales entry",
        referenceType = JournalReferenceType.SALE,
        isPosted = true,
        createdBy = "user-001",
        createdAt = 1_700_000_001_000L,
        updatedAt = 1_700_000_001_000L,
    )

    private val dateRangeFlow = MutableStateFlow(listOf(draftEntry, postedEntry))
    private val unpostedFlow = MutableStateFlow(listOf(draftEntry))
    private var deleteResult: Result<Unit> = Result.Success(Unit)

    private val fakeJournalRepo = object : JournalRepository {
        override fun getEntriesByDateRange(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>> =
            dateRangeFlow

        override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> = unpostedFlow

        override suspend fun getById(id: String): Result<JournalEntry?> {
            val all = listOf(draftEntry, postedEntry)
            return Result.Success(all.firstOrNull { it.id == id })
        }

        override suspend fun getByReference(referenceType: JournalReferenceType, referenceId: String): Result<List<JournalEntry>> =
            Result.Success(emptyList())

        override suspend fun getNextEntryNumber(storeId: String): Result<Int> = Result.Success(3)
        override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> = Result.Success(Unit)
        override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun unpostEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = deleteResult
        override suspend fun reverseEntry(
            originalEntryId: String, reversalDate: String, createdBy: String, now: Long,
        ): Result<JournalEntry> = Result.Success(draftEntry)
    }

    private lateinit var viewModel: JournalEntryListViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = JournalEntryListViewModel(
            getJournalEntriesUseCase = GetJournalEntriesUseCase(fakeJournalRepo),
            deleteDraftEntryUseCase = DeleteDraftEntryUseCase(fakeJournalRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty entries and no error`() {
        val state = viewModel.currentState
        assertTrue(state.entries.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.showUnpostedOnly)
    }

    // ── Load ───────────────────────────────────────────────────────────────────

    @Test
    fun `Load sets storeId and date range in state`() = runTest {
        viewModel.handleIntentForTest(
            JournalEntryListIntent.Load("store-001", "2026-01-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertEquals("store-001", state.storeId)
        assertEquals("2026-01-01", state.fromDate)
        assertEquals("2026-03-31", state.toDate)
    }

    @Test
    fun `Load populates entries from date range flow`() = runTest {
        viewModel.handleIntentForTest(
            JournalEntryListIntent.Load("store-001", "2026-01-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.currentState.entries.size)
    }

    @Test
    fun `Load resets showUnpostedOnly to false`() = runTest {
        // First set showUnpostedOnly via LoadUnposted
        viewModel.handleIntentForTest(JournalEntryListIntent.LoadUnposted("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.showUnpostedOnly)

        // Load with date range resets it
        viewModel.handleIntentForTest(
            JournalEntryListIntent.Load("store-001", "2026-01-01", "2026-03-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentState.showUnpostedOnly)
    }

    // ── LoadUnposted ───────────────────────────────────────────────────────────

    @Test
    fun `LoadUnposted sets showUnpostedOnly to true`() = runTest {
        viewModel.handleIntentForTest(JournalEntryListIntent.LoadUnposted("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentState.showUnpostedOnly)
    }

    @Test
    fun `LoadUnposted shows only draft entries`() = runTest {
        viewModel.handleIntentForTest(JournalEntryListIntent.LoadUnposted("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.currentState.entries.size)
        assertFalse(viewModel.currentState.entries.first().isPosted)
    }

    // ── SetDateRange ───────────────────────────────────────────────────────────

    @Test
    fun `SetDateRange updates fromDate and toDate`() = runTest {
        viewModel.handleIntentForTest(
            JournalEntryListIntent.Load("store-001", "2026-01-01", "2026-01-31")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(
                JournalEntryListIntent.SetDateRange("2026-02-01", "2026-02-28")
            )
            val updated = awaitItem()
            assertEquals("2026-02-01", updated.fromDate)
            assertEquals("2026-02-28", updated.toDate)
        }
    }

    // ── ToggleUnpostedFilter ───────────────────────────────────────────────────

    @Test
    fun `ToggleUnpostedFilter true switches to unposted view`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryListIntent.ToggleUnpostedFilter(true))
            val updated = awaitItem()
            assertTrue(updated.showUnpostedOnly)
        }
    }

    @Test
    fun `ToggleUnpostedFilter false switches to date range view`() = runTest {
        viewModel.handleIntentForTest(JournalEntryListIntent.LoadUnposted("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryListIntent.ToggleUnpostedFilter(false))
            val updated = awaitItem()
            assertFalse(updated.showUnpostedOnly)
        }
    }

    // ── DeleteDraft ────────────────────────────────────────────────────────────

    @Test
    fun `DeleteDraft success emits ShowSuccess effect`() = runTest {
        deleteResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryListIntent.DeleteDraft("je-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryListEffect.ShowSuccess)
            assertTrue((effect as JournalEntryListEffect.ShowSuccess).message.contains("deleted", ignoreCase = true))
        }
    }

    @Test
    fun `DeleteDraft of posted entry emits ShowError effect`() = runTest {
        // je-002 is posted — DeleteDraftEntryUseCase rejects it
        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryListIntent.DeleteDraft("je-002"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryListEffect.ShowError)
        }
    }

    @Test
    fun `DeleteDraft clears isLoading after success`() = runTest {
        deleteResult = Result.Success(Unit)
        viewModel.handleIntentForTest(JournalEntryListIntent.DeleteDraft("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.isLoading)
    }

    @Test
    fun `DeleteDraft failure emits ShowError effect`() = runTest {
        deleteResult = Result.Error(Exception("DB constraint violation"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryListIntent.DeleteDraft("je-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryListEffect.ShowError)
            assertTrue((effect as JournalEntryListEffect.ShowError).message.contains("DB constraint"))
        }
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private suspend fun JournalEntryListViewModel.handleIntentForTest(intent: JournalEntryListIntent) =
    handleIntent(intent)
