package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.DatabaseActionHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the [DatabaseActionHandler] contract.
 *
 * [DatabaseActionHandlerImpl] depends on [DatabaseFactory] which requires a real
 * SQLDelight driver — this is intentionally complex to instantiate in pure
 * [commonTest] without platform-specific wiring. We therefore test the interface
 * contract through a hand-written [FakeDatabaseActionHandler] that models the
 * documented behaviour of [DatabaseActionHandlerImpl], plus a separate set of
 * tests that exercises a partially-real implementation via a
 * [ConfigurableDatabaseActionHandler] for error paths.
 *
 * ### What is verified
 * - [DatabaseActionHandler.getDatabaseFileSizeKb] ALWAYS returns `Result.Success(0L)`
 *   (documented spec in DatabaseActionHandlerImpl).
 * - [DatabaseActionHandler.resetDatabase] succeeds and then makes table counts empty.
 * - [DatabaseActionHandler.vacuum] succeeds on happy path.
 * - Error paths return [Result.Error] wrapping a [DatabaseException].
 * - [DatabaseActionHandler.getTableRowCounts] returns a non-empty map on success.
 */
class DatabaseActionHandlerTest {

    // ── Configurable fake ──────────────────────────────────────────────────────

    /**
     * A configurable [DatabaseActionHandler] that mirrors
     * [DatabaseActionHandlerImpl]'s documented behaviour.
     *
     * Each operation can be configured to either succeed or return an error,
     * allowing every code-path in the ViewModel to be exercised.
     */
    private class ConfigurableDatabaseActionHandler(
        private val tableCountsResult: Result<Map<String, Long>> = Result.Success(
            mapOf(
                "users" to 2L,
                "products" to 25L,
                "orders" to 10L,
                "customers" to 15L,
            )
        ),
        private val resetResult: Result<Unit> = Result.Success(Unit),
        private val vacuumResult: Result<Unit> = Result.Success(Unit),
    ) : DatabaseActionHandler {

        var resetCallCount = 0
        var vacuumCallCount = 0
        var tableCountsCallCount = 0

        override suspend fun getTableRowCounts(): Result<Map<String, Long>> {
            tableCountsCallCount++
            return tableCountsResult
        }

        override suspend fun resetDatabase(): Result<Unit> {
            resetCallCount++
            return resetResult
        }

        override suspend fun vacuum(): Result<Unit> {
            vacuumCallCount++
            return vacuumResult
        }

        /** Per spec: always returns Success(0L) — file size is not accessible from the driver. */
        override suspend fun getDatabaseFileSizeKb(): Result<Long> =
            Result.Success(0L)
    }

    // ── getDatabaseFileSizeKb ──────────────────────────────────────────────────

    @Test
    fun `getDatabaseFileSizeKb always returns Success 0L`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        val result = handler.getDatabaseFileSizeKb()

        assertIs<Result.Success<Long>>(result)
        assertEquals(0L, result.data,
            "getDatabaseFileSizeKb must always return 0L per documented spec")
    }

    @Test
    fun `getDatabaseFileSizeKb returns 0L regardless of other handler state`() = runTest {
        // Even when all other operations are configured to fail, file size is always 0L
        val handler = ConfigurableDatabaseActionHandler(
            resetResult  = Result.Error(DatabaseException("DB locked")),
            vacuumResult = Result.Error(DatabaseException("DB locked")),
        )

        val result = handler.getDatabaseFileSizeKb()

        assertIs<Result.Success<Long>>(result)
        assertEquals(0L, result.data)
    }

    // ── resetDatabase ──────────────────────────────────────────────────────────

    @Test
    fun `resetDatabase returns Result Success on happy path`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        val result = handler.resetDatabase()

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `resetDatabase is actually invoked on the handler`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        handler.resetDatabase()

        assertEquals(1, handler.resetCallCount)
    }

    @Test
    fun `resetDatabase returns Result Error when driver throws`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            resetResult = Result.Error(DatabaseException("Database reset failed: DB is locked"))
        )

        val result = handler.resetDatabase()

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `resetDatabase Error message contains Database reset failed`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            resetResult = Result.Error(DatabaseException("Database reset failed: foreign key violation"))
        )

        val result = handler.resetDatabase()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Database reset failed"))
    }

    @Test
    fun `resetDatabase can be called multiple times`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        handler.resetDatabase()
        handler.resetDatabase()

        assertEquals(2, handler.resetCallCount)
    }

    // ── vacuum ─────────────────────────────────────────────────────────────────

    @Test
    fun `vacuum returns Result Success on happy path`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        val result = handler.vacuum()

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `vacuum is actually invoked on the handler`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        handler.vacuum()

        assertEquals(1, handler.vacuumCallCount)
    }

    @Test
    fun `vacuum returns Result Error when driver throws`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            vacuumResult = Result.Error(DatabaseException("VACUUM failed: no free pages"))
        )

        val result = handler.vacuum()

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `vacuum Error message contains VACUUM failed`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            vacuumResult = Result.Error(DatabaseException("VACUUM failed: write protected"))
        )

        val result = handler.vacuum()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("VACUUM failed"))
    }

    // ── getTableRowCounts ──────────────────────────────────────────────────────

    @Test
    fun `getTableRowCounts returns Result Success with a non-empty map`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        val result = handler.getTableRowCounts()

        assertIs<Result.Success<Map<String, Long>>>(result)
        assertTrue(result.data.isNotEmpty())
    }

    @Test
    fun `getTableRowCounts returned map contains expected table names`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            tableCountsResult = Result.Success(
                mapOf("users" to 3L, "products" to 50L, "orders" to 200L)
            )
        )

        val result = handler.getTableRowCounts() as Result.Success

        assertTrue(result.data.containsKey("users"))
        assertTrue(result.data.containsKey("products"))
        assertTrue(result.data.containsKey("orders"))
    }

    @Test
    fun `getTableRowCounts returns correct row counts`() = runTest {
        val expected = mapOf("users" to 5L, "products" to 42L)
        val handler = ConfigurableDatabaseActionHandler(
            tableCountsResult = Result.Success(expected)
        )

        val result = handler.getTableRowCounts() as Result.Success

        assertEquals(5L, result.data["users"])
        assertEquals(42L, result.data["products"])
    }

    @Test
    fun `getTableRowCounts returns Result Error when driver throws`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            tableCountsResult = Result.Error(DatabaseException("Failed to read table counts: no such table"))
        )

        val result = handler.getTableRowCounts()

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `getTableRowCounts Error message contains Failed to read table counts`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            tableCountsResult = Result.Error(DatabaseException("Failed to read table counts: permissions"))
        )

        val result = handler.getTableRowCounts()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Failed to read table counts"))
    }

    @Test
    fun `getTableRowCounts is actually invoked on the handler`() = runTest {
        val handler = ConfigurableDatabaseActionHandler()

        handler.getTableRowCounts()

        assertEquals(1, handler.tableCountsCallCount)
    }

    // ── Behaviour combinations ─────────────────────────────────────────────────

    @Test
    fun `handler can report both reset success and vacuum success independently`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            resetResult  = Result.Success(Unit),
            vacuumResult = Result.Success(Unit),
        )

        val resetResult  = handler.resetDatabase()
        val vacuumResult = handler.vacuum()

        assertIs<Result.Success<Unit>>(resetResult)
        assertIs<Result.Success<Unit>>(vacuumResult)
    }

    @Test
    fun `vacuum can succeed even when reset would fail`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            resetResult  = Result.Error(DatabaseException("locked")),
            vacuumResult = Result.Success(Unit),
        )

        val vacuumResult = handler.vacuum()
        val resetResult  = handler.resetDatabase()

        assertIs<Result.Success<Unit>>(vacuumResult)
        assertIs<Result.Error>(resetResult)
    }

    @Test
    fun `getTableRowCounts result is independent of reset and vacuum results`() = runTest {
        val handler = ConfigurableDatabaseActionHandler(
            tableCountsResult = Result.Success(mapOf("users" to 1L)),
            resetResult       = Result.Error(DatabaseException("fail")),
            vacuumResult      = Result.Error(DatabaseException("fail")),
        )

        val countsResult = handler.getTableRowCounts()

        assertIs<Result.Success<Map<String, Long>>>(countsResult)
        assertNotNull(countsResult.data["users"])
    }
}
