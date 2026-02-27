package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AccountingEntryType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAccountingRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAccountingEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [CreateAccountingEntryUseCase].
 *
 * Validates double-entry business rules:
 * 1. Empty entry list → REQUIRED validation error
 * 2. No CREDIT entries (debit-only) → DOUBLE_ENTRY validation error
 * 3. No DEBIT entries (credit-only) → DOUBLE_ENTRY validation error
 * 4. Debit total ≠ Credit total → UNBALANCED validation error
 * 5. Balanced debit/credit → delegates to repository
 */
class CreateAccountingEntryUseCaseTest {

    // ─── REQUIRED rule ────────────────────────────────────────────────────────

    @Test
    fun `empty entries list - returns ValidationException with REQUIRED rule`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)

        val result = useCase(emptyList())

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("entries", ex.field)
        assertTrue(repo.insertedEntries.isEmpty())
    }

    // ─── DOUBLE_ENTRY rule ────────────────────────────────────────────────────

    @Test
    fun `only DEBIT entries - returns ValidationException with DOUBLE_ENTRY rule`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 100.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.DEBIT, amount = 50.0),
        )

        val result = useCase(entries)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("DOUBLE_ENTRY", ex.rule)
        assertEquals("entries", ex.field)
        assertTrue(repo.insertedEntries.isEmpty())
    }

    @Test
    fun `only CREDIT entries - returns ValidationException with DOUBLE_ENTRY rule`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.CREDIT, amount = 100.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 50.0),
        )

        val result = useCase(entries)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("DOUBLE_ENTRY", ex.rule)
        assertEquals("entries", ex.field)
        assertTrue(repo.insertedEntries.isEmpty())
    }

    // ─── UNBALANCED rule ──────────────────────────────────────────────────────

    @Test
    fun `debit total not equal to credit total - returns ValidationException with UNBALANCED rule`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 150.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("UNBALANCED", ex.rule)
        assertEquals("entries", ex.field)
        assertTrue(repo.insertedEntries.isEmpty())
    }

    @Test
    fun `debit total exceeds credit total by more than tolerance - returns UNBALANCED error`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        // Difference of 1.0 exceeds the 0.005 tolerance
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 101.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Error>(result)
        assertEquals("UNBALANCED", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── Balanced entries → success path ──────────────────────────────────────

    @Test
    fun `balanced debit and credit entries - delegates to repository and returns success`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 100.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Success<*>>(result)
        assertEquals(2, repo.insertedEntries.size)
    }

    @Test
    fun `multiple balanced debit and credit lines - persists all entries`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        // Debit: 200 + 50 = 250; Credit: 150 + 100 = 250
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 200.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.DEBIT, amount = 50.0),
            buildAccountingEntry(id = "e-03", entryType = AccountingEntryType.CREDIT, amount = 150.0),
            buildAccountingEntry(id = "e-04", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Success<*>>(result)
        assertEquals(4, repo.insertedEntries.size)
    }

    @Test
    fun `balanced entries within floating-point tolerance - returns success`() = runTest {
        val repo = FakeAccountingRepository()
        val useCase = CreateAccountingEntryUseCase(repo)
        // Difference is 0.001, within the 0.005 tolerance
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 100.001),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `balanced entries - propagates repository error when insert fails`() = runTest {
        val repo = FakeAccountingRepository().also { it.shouldFailInsert = true }
        val useCase = CreateAccountingEntryUseCase(repo)
        val entries = listOf(
            buildAccountingEntry(id = "e-01", entryType = AccountingEntryType.DEBIT, amount = 100.0),
            buildAccountingEntry(id = "e-02", entryType = AccountingEntryType.CREDIT, amount = 100.0),
        )

        val result = useCase(entries)

        assertIs<Result.Error>(result)
        // Entries were NOT stored because the repository rejected them
        assertTrue(repo.insertedEntries.isEmpty())
    }
}
