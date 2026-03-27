package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — JournalRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [JournalRepositoryImpl] against a real in-memory SQLite database.
 * Requires chart_of_accounts seeded to satisfy journal_entry_lines.account_id FK.
 *
 * Coverage:
 *  A. saveDraftEntry → getById round-trip preserves header and lines
 *  B. saveDraftEntry rejects unbalanced entry (debit ≠ credit)
 *  C. getEntriesByDateRange via Turbine filters by date range
 *  D. getUnpostedEntries via Turbine returns only unposted
 *  E. postEntry marks entry as posted
 *  F. unpostEntry marks entry as unposted
 *  G. deleteEntry removes header and all lines
 *  H. getNextEntryNumber increments correctly
 *  I. reverseEntry creates reversal with swapped debit/credit
 */
class JournalRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: JournalRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = JournalRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed chart_of_accounts required by journal_entry_lines.account_id FK
        db.chart_of_accountsQueries.insertAccount(
            id = "acc-cash",
            account_code = "1010",
            account_name = "Cash",
            account_type = "ASSET",
            sub_category = "Current Assets",
            description = null,
            normal_balance = "DEBIT",
            parent_account_id = null,
            is_system_account = 1L,
            is_active = 1L,
            is_header_account = 0L,
            allow_transactions = 1L,
            created_at = now,
            updated_at = now,
        )
        db.chart_of_accountsQueries.insertAccount(
            id = "acc-sales",
            account_code = "4010",
            account_name = "Sales Revenue",
            account_type = "INCOME",
            sub_category = "Revenue",
            description = null,
            normal_balance = "CREDIT",
            parent_account_id = null,
            is_system_account = 0L,
            is_active = 1L,
            is_header_account = 0L,
            allow_transactions = 1L,
            created_at = now,
            updated_at = now,
        )
        db.chart_of_accountsQueries.insertAccount(
            id = "acc-expense",
            account_code = "5010",
            account_name = "Rent Expense",
            account_type = "EXPENSE",
            sub_category = "Operating Expenses",
            description = null,
            normal_balance = "DEBIT",
            parent_account_id = null,
            is_system_account = 0L,
            is_active = 1L,
            is_header_account = 0L,
            allow_transactions = 1L,
            created_at = now,
            updated_at = now,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeEntry(
        id: String = "je-01",
        entryNumber: Int = 1,
        storeId: String = "store-01",
        entryDate: String = "2026-04-15",
        description: String = "Cash sale",
        referenceType: JournalReferenceType = JournalReferenceType.SALE,
        isPosted: Boolean = false,
        lines: List<JournalEntryLine> = defaultBalancedLines(id),
    ) = JournalEntry(
        id = id,
        entryNumber = entryNumber,
        storeId = storeId,
        entryDate = entryDate,
        entryTime = now,
        description = description,
        referenceType = referenceType,
        referenceId = null,
        isPosted = isPosted,
        createdBy = "user-01",
        createdAt = now,
        updatedAt = now,
        postedAt = null,
        memo = null,
        syncStatus = "PENDING",
        lines = lines,
    )

    /** Returns a balanced set of lines: Cash DR 10000, Sales CR 10000 */
    private fun defaultBalancedLines(entryId: String) = listOf(
        JournalEntryLine(
            id = "$entryId-line-1",
            journalEntryId = entryId,
            accountId = "acc-cash",
            debitAmount = 10000.0,
            creditAmount = 0.0,
            lineDescription = "Cash received",
            lineOrder = 1,
            createdAt = now,
            accountCode = null,
            accountName = null,
        ),
        JournalEntryLine(
            id = "$entryId-line-2",
            journalEntryId = entryId,
            accountId = "acc-sales",
            debitAmount = 0.0,
            creditAmount = 10000.0,
            lineDescription = "Sales revenue",
            lineOrder = 2,
            createdAt = now,
            accountCode = null,
            accountName = null,
        ),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - saveDraftEntry then getById round-trip preserves header and lines`() = runTest {
        val entry = makeEntry(id = "je-01", entryNumber = 1, entryDate = "2026-04-15",
            description = "Cash sale entry")
        val saveResult = repo.saveDraftEntry(entry)
        assertIs<Result.Success<Unit>>(saveResult)

        val fetchResult = repo.getById("je-01")
        assertIs<Result.Success<JournalEntry?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("je-01", fetched.id)
        assertEquals(1, fetched.entryNumber)
        assertEquals("store-01", fetched.storeId)
        assertEquals("2026-04-15", fetched.entryDate)
        assertEquals("Cash sale entry", fetched.description)
        assertFalse(fetched.isPosted)
        assertEquals(2, fetched.lines.size)
        val dr = fetched.lines.find { it.debitAmount > 0 }
        assertNotNull(dr)
        assertEquals(10000.0, dr.debitAmount)
        assertEquals("acc-cash", dr.accountId)
    }

    @Test
    fun `B - saveDraftEntry rejects unbalanced entry`() = runTest {
        val unbalancedLines = listOf(
            JournalEntryLine(
                id = "je-bad-line-1",
                journalEntryId = "je-bad",
                accountId = "acc-cash",
                debitAmount = 5000.0,
                creditAmount = 0.0,
                lineDescription = "Partial debit",
                lineOrder = 1,
                createdAt = now,
                accountCode = null,
                accountName = null,
            ),
            JournalEntryLine(
                id = "je-bad-line-2",
                journalEntryId = "je-bad",
                accountId = "acc-sales",
                debitAmount = 0.0,
                creditAmount = 3000.0,  // intentionally unbalanced
                lineDescription = "Partial credit",
                lineOrder = 2,
                createdAt = now,
                accountCode = null,
                accountName = null,
            ),
        )
        val entry = makeEntry(id = "je-bad", lines = unbalancedLines)

        val result = repo.saveDraftEntry(entry)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `C - getEntriesByDateRange via Turbine filters by date range`() = runTest {
        repo.saveDraftEntry(makeEntry(id = "je-jan", entryNumber = 1, entryDate = "2026-01-15"))
        repo.saveDraftEntry(makeEntry(id = "je-apr", entryNumber = 2, entryDate = "2026-04-01"))
        repo.saveDraftEntry(makeEntry(id = "je-dec", entryNumber = 3, entryDate = "2026-12-20"))

        repo.getEntriesByDateRange("store-01", "2026-03-01", "2026-06-30").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("je-apr", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getUnpostedEntries via Turbine returns only unposted entries`() = runTest {
        repo.saveDraftEntry(makeEntry(id = "je-draft", entryNumber = 1, isPosted = false))
        repo.saveDraftEntry(makeEntry(id = "je-posted", entryNumber = 2, entryDate = "2026-04-16",
            isPosted = false))
        // Post je-posted
        repo.postEntry("je-posted", now)

        repo.getUnpostedEntries("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("je-draft", list.first().id)
            assertFalse(list.first().isPosted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - postEntry marks entry as posted`() = runTest {
        repo.saveDraftEntry(makeEntry(id = "je-01", isPosted = false))

        val postResult = repo.postEntry("je-01", now)
        assertIs<Result.Success<Unit>>(postResult)

        val fetched = (repo.getById("je-01") as Result.Success).data
        assertNotNull(fetched)
        assertTrue(fetched.isPosted)
        assertNotNull(fetched.postedAt)
    }

    @Test
    fun `F - unpostEntry marks entry as unposted`() = runTest {
        repo.saveDraftEntry(makeEntry(id = "je-01", isPosted = false))
        repo.postEntry("je-01", now)

        val unpostResult = repo.unpostEntry("je-01")
        assertIs<Result.Success<Unit>>(unpostResult)

        val fetched = (repo.getById("je-01") as Result.Success).data
        assertNotNull(fetched)
        assertFalse(fetched.isPosted)
    }

    @Test
    fun `G - deleteEntry removes header and all lines`() = runTest {
        repo.saveDraftEntry(makeEntry(id = "je-01"))

        val deleteResult = repo.deleteEntry("je-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val fetched = (repo.getById("je-01") as Result.Success).data
        assertNull(fetched)
    }

    @Test
    fun `H - getNextEntryNumber increments after each insert`() = runTest {
        val first = (repo.getNextEntryNumber("store-01") as Result.Success).data
        assertEquals(1, first)

        repo.saveDraftEntry(makeEntry(id = "je-01", entryNumber = 1))
        val second = (repo.getNextEntryNumber("store-01") as Result.Success).data
        assertEquals(2, second)

        repo.saveDraftEntry(makeEntry(id = "je-02", entryNumber = 2, entryDate = "2026-04-16"))
        val third = (repo.getNextEntryNumber("store-01") as Result.Success).data
        assertEquals(3, third)
    }

    @Test
    fun `I - reverseEntry creates reversal with swapped debit and credit`() = runTest {
        // Insert and post the original entry
        repo.saveDraftEntry(makeEntry(id = "je-01", entryNumber = 1))
        repo.postEntry("je-01", now)

        val reverseResult = repo.reverseEntry(
            originalEntryId = "je-01",
            reversalDate = "2026-05-01",
            createdBy = "manager-01",
            now = now,
        )
        assertIs<Result.Success<JournalEntry>>(reverseResult)
        val reversal = reverseResult.data
        assertNotNull(reversal)
        assertFalse(reversal.isPosted)
        assertEquals(2, reversal.lines.size)

        // Swapped: original DR cash 10000, CR sales 10000
        // Reversal: DR sales 10000, CR cash 10000
        val cashLine = reversal.lines.find { it.accountId == "acc-cash" }
        val salesLine = reversal.lines.find { it.accountId == "acc-sales" }
        assertNotNull(cashLine)
        assertNotNull(salesLine)
        assertEquals(0.0, cashLine.debitAmount)
        assertEquals(10000.0, cashLine.creditAmount)
        assertEquals(10000.0, salesLine.debitAmount)
        assertEquals(0.0, salesLine.creditAmount)
    }
}
