package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.DiagnosticsActionHandlerImpl
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [DiagnosticsActionHandlerImpl].
 *
 * Covers:
 * - [DiagnosticsActionHandlerImpl.getAuditLog] happy and error paths.
 * - [DiagnosticsActionHandlerImpl.getLogLines] initial empty state.
 * - [DiagnosticsActionHandlerImpl.appendLog] appends correctly.
 * - Ring buffer: capped at 500 entries (oldest dropped when full).
 * - FIFO ordering of the ring buffer.
 */
class DiagnosticsActionHandlerTest {

    private val now = Clock.System.now()

    // ── Test data ─────────────────────────────────────────────────────────────

    private fun makeAuditEntry(id: String) = AuditEntry(
        id        = id,
        eventType = AuditEventType.SETTINGS_CHANGED,
        userId    = "u-admin",
        deviceId  = "debug-console",
        payload   = """{"action":"test"}""",
        success   = true,
        createdAt = now,
    )

    // ── Stub AuditRepository ───────────────────────────────────────────────────

    private class StubAuditRepository(
        private val entries: List<AuditEntry> = emptyList(),
        private val throwOnObserveAll: Boolean = false,
    ) : AuditRepository {
        private val flow = MutableStateFlow(entries)
        val insertedEntries = mutableListOf<AuditEntry>()

        override suspend fun insert(entry: AuditEntry) {
            insertedEntries += entry
        }

        override fun observeAll(): Flow<List<AuditEntry>> {
            if (throwOnObserveAll) throw RuntimeException("SQLDelight cursor error")
            return flow
        }

        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flow
    }

    // ── SUT builder ───────────────────────────────────────────────────────────

    private fun buildHandler(auditRepo: AuditRepository = StubAuditRepository()) =
        DiagnosticsActionHandlerImpl(auditRepo)

    // ── getAuditLog ────────────────────────────────────────────────────────────

    @Test
    fun `getAuditLog returns Success with empty list when no entries exist`() = runTest {
        val handler = buildHandler(StubAuditRepository(entries = emptyList()))

        val result = handler.getAuditLog()

        assertIs<Result.Success<List<AuditEntry>>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getAuditLog returns Success with all entries from repository`() = runTest {
        val entries = listOf(makeAuditEntry("a1"), makeAuditEntry("a2"), makeAuditEntry("a3"))
        val handler = buildHandler(StubAuditRepository(entries = entries))

        val result = handler.getAuditLog()

        assertIs<Result.Success<List<AuditEntry>>>(result)
        assertEquals(3, result.data.size)
    }

    @Test
    fun `getAuditLog preserves entry IDs`() = runTest {
        val entries = listOf(makeAuditEntry("entry-001"), makeAuditEntry("entry-002"))
        val handler = buildHandler(StubAuditRepository(entries = entries))

        val result = handler.getAuditLog() as Result.Success
        val ids = result.data.map { it.id }

        assertTrue(ids.contains("entry-001"))
        assertTrue(ids.contains("entry-002"))
    }

    @Test
    fun `getAuditLog preserves AuditEventType of each entry`() = runTest {
        val entry = makeAuditEntry("a1").copy(eventType = AuditEventType.LOGIN_ATTEMPT)
        val handler = buildHandler(StubAuditRepository(entries = listOf(entry)))

        val result = handler.getAuditLog() as Result.Success

        assertEquals(AuditEventType.LOGIN_ATTEMPT, result.data.first().eventType)
    }

    @Test
    fun `getAuditLog returns Result Error when observeAll throws`() = runTest {
        val handler = buildHandler(StubAuditRepository(throwOnObserveAll = true))

        val result = handler.getAuditLog()

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `getAuditLog Error message contains Failed to load audit log`() = runTest {
        val handler = buildHandler(StubAuditRepository(throwOnObserveAll = true))

        val result = handler.getAuditLog()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Failed to load audit log"))
    }

    @Test
    fun `getAuditLog can be called multiple times returning the same entries`() = runTest {
        val entries = listOf(makeAuditEntry("a1"))
        val handler = buildHandler(StubAuditRepository(entries = entries))

        val result1 = handler.getAuditLog() as Result.Success
        val result2 = handler.getAuditLog() as Result.Success

        assertEquals(result1.data, result2.data)
    }

    // ── getLogLines initial state ──────────────────────────────────────────────

    @Test
    fun `getLogLines returns empty list on freshly created handler`() {
        val handler = buildHandler()

        val lines = handler.getLogLines()

        assertTrue(lines.isEmpty(), "Expected empty log buffer on new handler")
    }

    // ── appendLog ─────────────────────────────────────────────────────────────

    @Test
    fun `appendLog adds a single line to the buffer`() {
        val handler = buildHandler()

        handler.appendLog("INFO: application started")

        val lines = handler.getLogLines()
        assertEquals(1, lines.size)
        assertEquals("INFO: application started", lines.first())
    }

    @Test
    fun `appendLog adds multiple lines preserving FIFO order`() {
        val handler = buildHandler()

        handler.appendLog("line-1")
        handler.appendLog("line-2")
        handler.appendLog("line-3")

        val lines = handler.getLogLines()
        assertEquals(listOf("line-1", "line-2", "line-3"), lines)
    }

    @Test
    fun `appendLog with exactly 500 entries does not drop any`() {
        val handler = buildHandler()

        repeat(500) { i -> handler.appendLog("line-$i") }

        assertEquals(500, handler.getLogLines().size)
    }

    @Test
    fun `appendLog with 501 entries caps buffer at 500 - oldest is dropped`() {
        val handler = buildHandler()

        repeat(501) { i -> handler.appendLog("line-$i") }

        val lines = handler.getLogLines()
        assertEquals(500, lines.size,
            "Buffer must be capped at 500 entries")
        // "line-0" (the oldest) should have been evicted
        assertTrue(
            !lines.contains("line-0"),
            "The first entry 'line-0' should have been dropped when the buffer overflowed",
        )
    }

    @Test
    fun `appendLog with 501 entries retains the newest 500 entries`() {
        val handler = buildHandler()

        repeat(501) { i -> handler.appendLog("line-$i") }

        val lines = handler.getLogLines()
        // The most recently added line should be the last in the buffer
        assertEquals("line-500", lines.last())
        // Line-1 onwards up to line-500 should all be present
        assertEquals("line-1", lines.first())
    }

    @Test
    fun `appendLog buffer maintains FIFO order after overflow`() {
        val handler = buildHandler()

        // Fill buffer to capacity, then add 10 more to trigger eviction
        repeat(510) { i -> handler.appendLog("msg-$i") }

        val lines = handler.getLogLines()
        // After 510 inserts, the first 10 (msg-0 through msg-9) should be gone
        // First retained line should be msg-10
        assertEquals("msg-10", lines.first(),
            "After 510 inserts with a 500-cap buffer, the first 10 entries should be evicted")
        assertEquals("msg-509", lines.last())
    }

    @Test
    fun `getLogLines returns a snapshot that does not change when buffer is modified later`() {
        val handler = buildHandler()

        handler.appendLog("first")
        val snapshot = handler.getLogLines()

        handler.appendLog("second")

        // The snapshot was taken before "second" was added;
        // getLogLines returns a new list each time
        assertEquals(1, snapshot.size,
            "Snapshot taken before second append should have 1 entry")
        assertEquals(2, handler.getLogLines().size,
            "Handler buffer now has 2 entries")
    }

    @Test
    fun `appendLog with empty string is stored as-is`() {
        val handler = buildHandler()

        handler.appendLog("")

        val lines = handler.getLogLines()
        assertEquals(1, lines.size)
        assertEquals("", lines.first())
    }

    @Test
    fun `appendLog with exactly 500 entries then one more removes only the oldest`() {
        val handler = buildHandler()

        repeat(500) { i -> handler.appendLog("old-$i") }
        handler.appendLog("new-entry")

        val lines = handler.getLogLines()
        assertEquals(500, lines.size)
        assertEquals("old-1", lines.first(),
            "Second-oldest entry should now be first after oldest was evicted")
        assertEquals("new-entry", lines.last())
    }

    // ── Combined: getAuditLog + getLogLines ────────────────────────────────────

    @Test
    fun `getAuditLog result is independent of log buffer state`() = runTest {
        val entries = listOf(makeAuditEntry("a1"))
        val handler = buildHandler(StubAuditRepository(entries = entries))

        handler.appendLog("some log line")
        handler.appendLog("another line")

        val auditResult = handler.getAuditLog() as Result.Success
        assertEquals(1, auditResult.data.size)
        assertEquals(2, handler.getLogLines().size)
    }
}
