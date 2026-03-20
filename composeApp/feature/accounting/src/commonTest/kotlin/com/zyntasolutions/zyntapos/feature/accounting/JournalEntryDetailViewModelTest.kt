package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetJournalEntriesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.ReverseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveDraftJournalEntryUseCase
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
 * Unit tests for [JournalEntryDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEntryDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val debitLine = JournalEntryLine(
        id = "line-001",
        journalEntryId = "je-001",
        accountId = "acct-001",
        debitAmount = 5_000.0,
        creditAmount = 0.0,
        lineOrder = 1,
        createdAt = 1_700_000_000_000L,
    )

    private val creditLine = JournalEntryLine(
        id = "line-002",
        journalEntryId = "je-001",
        accountId = "acct-002",
        debitAmount = 0.0,
        creditAmount = 5_000.0,
        lineOrder = 2,
        createdAt = 1_700_000_000_000L,
    )

    private val existingEntry = JournalEntry(
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
        lines = listOf(debitLine, creditLine),
    )

    private val reversalEntry = existingEntry.copy(
        id = "je-reversal-001",
        entryNumber = 2,
        description = "Reversal of je-001",
        lines = listOf(
            debitLine.copy(id = "rev-line-001", debitAmount = 0.0, creditAmount = 5_000.0),
            creditLine.copy(id = "rev-line-002", debitAmount = 5_000.0, creditAmount = 0.0),
        ),
    )

    private var getByIdResult: Result<JournalEntry?> = Result.Success(existingEntry)
    private var saveDraftResult: Result<Unit> = Result.Success(Unit)
    private var postResult: Result<Unit> = Result.Success(Unit)
    private var reverseResult: Result<JournalEntry> = Result.Success(reversalEntry)

    private val fakeJournalRepo = object : JournalRepository {
        override fun getEntriesByDateRange(
            storeId: String, fromDate: String, toDate: String
        ): Flow<List<JournalEntry>> = MutableStateFlow(listOf(existingEntry))

        override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> =
            MutableStateFlow(listOf(existingEntry))

        override suspend fun getById(id: String): Result<JournalEntry?> = getByIdResult

        override suspend fun getByReference(
            referenceType: JournalReferenceType, referenceId: String
        ): Result<List<JournalEntry>> = Result.Success(emptyList())

        override suspend fun getNextEntryNumber(storeId: String): Result<Int> = Result.Success(3)
        override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> = saveDraftResult
        override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> = postResult
        override suspend fun unpostEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun reverseEntry(
            originalEntryId: String, reversalDate: String, createdBy: String, now: Long,
        ): Result<JournalEntry> = reverseResult
    }

    private lateinit var viewModel: JournalEntryDetailViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = JournalEntryDetailViewModel(
            getJournalEntriesUseCase = GetJournalEntriesUseCase(fakeJournalRepo),
            saveDraftJournalEntryUseCase = SaveDraftJournalEntryUseCase(fakeJournalRepo),
            postJournalEntryUseCase = PostJournalEntryUseCase(fakeJournalRepo),
            reverseJournalEntryUseCase = ReverseJournalEntryUseCase(fakeJournalRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is blank`() {
        val state = viewModel.state.value
        assertNull(state.entry)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
        assertFalse(state.isPosting)
        assertFalse(state.isReversing)
        assertTrue(state.lines.isEmpty())
        assertFalse(state.isBalanced)
        assertNull(state.error)
    }

    // ── Load ───────────────────────────────────────────────────────────────────

    @Test
    fun `Load populates form fields from existing entry`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.entry)
        assertEquals("Opening cash entry", state.description)
        assertEquals("2026-03-01", state.entryDate)
        assertEquals(JournalReferenceType.MANUAL, state.referenceType)
        assertEquals("store-001", state.storeId)
    }

    @Test
    fun `Load populates lines from existing entry`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.lines.size)
    }

    @Test
    fun `Load sets isBalanced true for balanced entry`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isBalanced)
    }

    @Test
    fun `Load clears isLoading after success`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `Load sets error when entry not found`() = runTest {
        getByIdResult = Result.Success(null)
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-999"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `Load emits ShowError when entry not found`() = runTest {
        getByIdResult = Result.Success(null)

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-999"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowError)
        }
    }

    @Test
    fun `Load emits ShowError on repository error`() = runTest {
        getByIdResult = Result.Error(DatabaseException("DB error"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowError)
        }
    }

    // ── NewEntry ───────────────────────────────────────────────────────────────

    @Test
    fun `NewEntry resets state with storeId and createdBy`() = runTest {
        // Load first to populate state
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Load("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset for new entry
        viewModel.handleIntentForTest(JournalEntryDetailIntent.NewEntry("store-002", "user-002"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.entry)
        assertEquals("store-002", state.storeId)
        assertEquals("user-002", state.createdBy)
        assertEquals("", state.description)
        assertTrue(state.lines.isEmpty())
    }

    @Test
    fun `NewEntry sets today as entry date`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.NewEntry("store-001", "user-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.entryDate.isNotBlank())
    }

    // ── Field update intents ───────────────────────────────────────────────────

    @Test
    fun `UpdateDescription updates description and clears error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryDetailIntent.UpdateDescription("New description"))
            val updated = awaitItem()
            assertEquals("New description", updated.description)
            assertNull(updated.error)
        }
    }

    @Test
    fun `UpdateDate updates entryDate and clears error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryDetailIntent.UpdateDate("2026-04-01"))
            val updated = awaitItem()
            assertEquals("2026-04-01", updated.entryDate)
            assertNull(updated.error)
        }
    }

    @Test
    fun `UpdateReferenceType updates referenceType`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryDetailIntent.UpdateReferenceType(JournalReferenceType.EXPENSE))
            val updated = awaitItem()
            assertEquals(JournalReferenceType.EXPENSE, updated.referenceType)
        }
    }

    // ── AddLine / RemoveLine ───────────────────────────────────────────────────

    @Test
    fun `AddLine appends line to lines list`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(debitLine))
            val updated = awaitItem()
            assertEquals(1, updated.lines.size)
            assertEquals("line-001", updated.lines.first().id)
        }
    }

    @Test
    fun `AddLine updates isBalanced when debit and credit match`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(debitLine))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isBalanced) // Only one side

        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(creditLine))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isBalanced) // Now balanced
    }

    @Test
    fun `RemoveLine removes the correct line`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(debitLine))
        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(creditLine))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.state.value.lines.size)

        viewModel.handleIntentForTest(JournalEntryDetailIntent.RemoveLine("line-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.lines.size)
        assertEquals("line-002", viewModel.state.value.lines.first().id)
    }

    @Test
    fun `RemoveLine updates isBalanced`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(debitLine))
        viewModel.handleIntentForTest(JournalEntryDetailIntent.AddLine(creditLine))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isBalanced)

        viewModel.handleIntentForTest(JournalEntryDetailIntent.RemoveLine("line-002"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isBalanced)
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    @Test
    fun `Save success emits ShowSuccess effect`() = runTest {
        saveDraftResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Save)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowSuccess)
            assertTrue((effect as JournalEntryDetailEffect.ShowSuccess).message.contains("saved", ignoreCase = true))
        }
    }

    @Test
    fun `Save failure emits ShowError effect`() = runTest {
        saveDraftResult = Result.Error(DatabaseException("DB constraint violation"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Save)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowError)
            assertTrue((effect as JournalEntryDetailEffect.ShowError).message.contains("DB constraint"))
        }
    }

    @Test
    fun `Save clears isSaving after completion`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Save)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isSaving)
    }

    // ── Post ───────────────────────────────────────────────────────────────────

    @Test
    fun `Post success emits ShowSuccess and NavigateBack effects`() = runTest {
        postResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Post("je-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val first = awaitItem()
            assertTrue(first is JournalEntryDetailEffect.ShowSuccess)
            val second = awaitItem()
            assertTrue(second is JournalEntryDetailEffect.NavigateBack)
        }
    }

    @Test
    fun `Post failure emits ShowError effect`() = runTest {
        postResult = Result.Error(DatabaseException("Entry is not balanced"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(JournalEntryDetailIntent.Post("je-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowError)
            assertTrue((effect as JournalEntryDetailEffect.ShowError).message.contains("not balanced"))
        }
    }

    @Test
    fun `Post clears isPosting after success`() = runTest {
        viewModel.handleIntentForTest(JournalEntryDetailIntent.Post("je-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isPosting)
    }

    // ── Reverse ────────────────────────────────────────────────────────────────

    @Test
    fun `Reverse success emits ShowSuccess and NavigateToEntry effects`() = runTest {
        reverseResult = Result.Success(reversalEntry)

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                JournalEntryDetailIntent.Reverse("je-001", "2026-04-01")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val first = awaitItem()
            assertTrue(first is JournalEntryDetailEffect.ShowSuccess)
            val second = awaitItem()
            assertTrue(second is JournalEntryDetailEffect.NavigateToEntry)
            assertEquals("je-reversal-001", (second as JournalEntryDetailEffect.NavigateToEntry).entryId)
        }
    }

    @Test
    fun `Reverse failure emits ShowError effect`() = runTest {
        reverseResult = Result.Error(DatabaseException("Entry is not posted"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(
                JournalEntryDetailIntent.Reverse("je-001", "2026-04-01")
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is JournalEntryDetailEffect.ShowError)
        }
    }

    @Test
    fun `Reverse clears isReversing after completion`() = runTest {
        viewModel.handleIntentForTest(
            JournalEntryDetailIntent.Reverse("je-001", "2026-04-01")
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isReversing)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun JournalEntryDetailViewModel.handleIntentForTest(intent: JournalEntryDetailIntent) =
    dispatch(intent)
