package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingEntryType
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — AccountingRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AccountingRepositoryImpl] against a real in-memory SQLite database.
 * accounting_entries has no external FK constraints.
 *
 * Coverage:
 *  A. insertEntries → getByStoreAndPeriod returns all entries for that period
 *  B. insertEntries rejects unbalanced entries (DEBIT ≠ CREDIT)
 *  C. getByAccountAndPeriod filters by account code
 *  D. getByReference returns entries for a specific reference
 *  E. getSummaryForPeriodRange aggregates totals by account
 *  F. insertEntries with empty list returns Success (no-op)
 */
@Suppress("DEPRECATION")
class AccountingRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: AccountingRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = AccountingRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    /** Returns a balanced pair: one DEBIT + one CREDIT for the same amount. */
    private fun balancedEntries(
        idPrefix: String,
        storeId: String = "store-01",
        amount: Double = 10000.0,
        referenceId: String = "order-001",
        entryDate: String = "2026-04-01",
        fiscalPeriod: String = "2026-04",
    ) = listOf(
        AccountingEntry(
            id = "$idPrefix-dr",
            storeId = storeId,
            accountCode = "1010",
            accountName = "Cash",
            entryType = AccountingEntryType.DEBIT,
            amount = amount,
            referenceType = AccountingReferenceType.ORDER,
            referenceId = referenceId,
            description = "Cash received",
            entryDate = entryDate,
            fiscalPeriod = fiscalPeriod,
            createdBy = "user-01",
            createdAt = now,
        ),
        AccountingEntry(
            id = "$idPrefix-cr",
            storeId = storeId,
            accountCode = "4010",
            accountName = "Sales Revenue",
            entryType = AccountingEntryType.CREDIT,
            amount = amount,
            referenceType = AccountingReferenceType.ORDER,
            referenceId = referenceId,
            description = "Sales revenue",
            entryDate = entryDate,
            fiscalPeriod = fiscalPeriod,
            createdBy = "user-01",
            createdAt = now,
        ),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insertEntries then getByStoreAndPeriod returns all entries`() = runTest {
        val entries = balancedEntries("je-01", fiscalPeriod = "2026-04")
        val insertResult = repo.insertEntries(entries)
        assertIs<Result.Success<Unit>>(insertResult)

        val result = repo.getByStoreAndPeriod("store-01", "2026-04")
        assertIs<Result.Success<List<AccountingEntry>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.any { it.entryType == AccountingEntryType.DEBIT })
        assertTrue(result.data.any { it.entryType == AccountingEntryType.CREDIT })
    }

    @Test
    fun `B - insertEntries rejects unbalanced entries`() = runTest {
        val unbalanced = listOf(
            AccountingEntry(
                id = "ub-dr", storeId = "store-01", accountCode = "1010", accountName = "Cash",
                entryType = AccountingEntryType.DEBIT, amount = 10000.0,
                referenceType = AccountingReferenceType.ORDER, referenceId = "ord-01",
                description = null, entryDate = "2026-04-01", fiscalPeriod = "2026-04",
                createdBy = "user-01", createdAt = now,
            ),
            AccountingEntry(
                id = "ub-cr", storeId = "store-01", accountCode = "4010", accountName = "Sales",
                entryType = AccountingEntryType.CREDIT, amount = 5000.0, // Intentionally unbalanced
                referenceType = AccountingReferenceType.ORDER, referenceId = "ord-01",
                description = null, entryDate = "2026-04-01", fiscalPeriod = "2026-04",
                createdBy = "user-01", createdAt = now,
            ),
        )

        val result = repo.insertEntries(unbalanced)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `C - getByAccountAndPeriod filters by account code`() = runTest {
        repo.insertEntries(balancedEntries("je-01", fiscalPeriod = "2026-04"))
        repo.insertEntries(balancedEntries("je-02", fiscalPeriod = "2026-05",
            entryDate = "2026-05-01", referenceId = "order-002"))

        val result = repo.getByAccountAndPeriod("store-01", "1010", "2026-04")
        assertIs<Result.Success<List<AccountingEntry>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("1010", result.data.first().accountCode)
        assertEquals("2026-04", result.data.first().fiscalPeriod)
    }

    @Test
    fun `D - getByReference returns entries for specific reference`() = runTest {
        repo.insertEntries(balancedEntries("je-01", referenceId = "order-A"))
        repo.insertEntries(balancedEntries("je-02", referenceId = "order-B",
            entryDate = "2026-04-02"))

        val result = repo.getByReference(AccountingReferenceType.ORDER, "order-A")
        assertIs<Result.Success<List<AccountingEntry>>>(result)
        assertEquals(2, result.data.size)
        assertTrue(result.data.all { it.referenceId == "order-A" })
    }

    @Test
    fun `E - getSummaryForPeriodRange aggregates totals by account`() = runTest {
        repo.insertEntries(balancedEntries("je-01", amount = 10000.0, fiscalPeriod = "2026-04"))
        repo.insertEntries(balancedEntries("je-02", amount = 5000.0, fiscalPeriod = "2026-05",
            entryDate = "2026-05-01", referenceId = "order-002"))

        val result = repo.getSummaryForPeriodRange("store-01", "2026-04", "2026-05")
        assertIs<Result.Success<*>>(result)
        val summaries = result.data as List<*>
        assertTrue(summaries.isNotEmpty())
    }

    @Test
    fun `F - insertEntries with empty list returns Success`() = runTest {
        val result = repo.insertEntries(emptyList())
        assertIs<Result.Success<Unit>>(result)
    }
}
