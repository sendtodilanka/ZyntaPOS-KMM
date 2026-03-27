package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.model.OperationalLog
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — OperationalLogRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [OperationalLogRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on operational_logs — no pre-seeding required.
 *
 * Coverage:
 *  A. insert OperationalLog → count increments
 *  B. insert with individual fields → getPage returns it
 *  C. getPage filters by level
 *  D. getPage filters by tag
 *  E. getPage respects time range
 *  F. purgeByLevelOlderThan removes only matching level and old entries
 *  G. purgeAllOlderThan removes all entries before cutoff
 */
class OperationalLogRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: OperationalLogRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = OperationalLogRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeLog(
        level: LogLevel = LogLevel.INFO,
        tag: String = "TestTag",
        message: String = "Test message",
        stackTrace: String? = null,
        threadName: String? = "main",
        sessionId: String? = "session-01",
        metadata: String? = null,
        createdAt: Long = now,
    ) = OperationalLog(
        id = 0L,
        level = level,
        tag = tag,
        message = message,
        stackTrace = stackTrace,
        threadName = threadName,
        sessionId = sessionId,
        metadata = metadata,
        createdAt = createdAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert OperationalLog increments count`() = runTest {
        assertEquals(0L, repo.count())

        repo.insert(makeLog(message = "First log"))
        repo.insert(makeLog(message = "Second log"))

        assertEquals(2L, repo.count())
    }

    @Test
    fun `B - insert with fields then getPage returns the entry`() = runTest {
        val ts = now
        repo.insert(
            level = LogLevel.ERROR,
            tag = "CrashTag",
            message = "Something went wrong",
            stackTrace = "at com.example.Foo.bar(Foo.kt:42)",
            threadName = "main",
            sessionId = "sess-01",
            metadata = """{"userId":"user-01"}""",
        )

        val page = repo.getPage(
            level = LogLevel.ERROR,
            tag = null,
            fromEpochMillis = ts - 10_000L,
            toEpochMillis = ts + 10_000L,
            pageSize = 10L,
            offset = 0L,
        )
        assertEquals(1, page.size)
        val entry = page.first()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("CrashTag", entry.tag)
        assertEquals("Something went wrong", entry.message)
        assertEquals("at com.example.Foo.bar(Foo.kt:42)", entry.stackTrace)
        assertEquals("main", entry.threadName)
        assertEquals("sess-01", entry.sessionId)
        assertEquals("""{"userId":"user-01"}""", entry.metadata)
    }

    @Test
    fun `C - getPage filters by level`() = runTest {
        val ts = now
        repo.insert(makeLog(level = LogLevel.DEBUG, message = "debug msg", createdAt = ts))
        repo.insert(makeLog(level = LogLevel.ERROR, message = "error msg", createdAt = ts))
        repo.insert(makeLog(level = LogLevel.INFO, message = "info msg", createdAt = ts))

        val errorOnly = repo.getPage(
            level = LogLevel.ERROR,
            tag = null,
            fromEpochMillis = ts - 1_000L,
            toEpochMillis = ts + 1_000L,
            pageSize = 10L,
            offset = 0L,
        )
        assertEquals(1, errorOnly.size)
        assertEquals(LogLevel.ERROR, errorOnly.first().level)
    }

    @Test
    fun `D - getPage filters by tag`() = runTest {
        val ts = now
        repo.insert(makeLog(tag = "NetworkTag", message = "net 1", createdAt = ts))
        repo.insert(makeLog(tag = "NetworkTag", message = "net 2", createdAt = ts))
        repo.insert(makeLog(tag = "DBTag", message = "db 1", createdAt = ts))

        val networkLogs = repo.getPage(
            level = null,
            tag = "NetworkTag",
            fromEpochMillis = ts - 1_000L,
            toEpochMillis = ts + 1_000L,
            pageSize = 10L,
            offset = 0L,
        )
        assertEquals(2, networkLogs.size)
        assertTrue(networkLogs.all { it.tag == "NetworkTag" })
    }

    @Test
    fun `E - getPage respects time range boundaries`() = runTest {
        val base = now
        repo.insert(makeLog(message = "old", createdAt = base - 10_000L))
        repo.insert(makeLog(message = "current", createdAt = base))
        repo.insert(makeLog(message = "future", createdAt = base + 10_000L))

        val inRange = repo.getPage(
            level = null,
            tag = null,
            fromEpochMillis = base - 5_000L,
            toEpochMillis = base + 5_000L,
            pageSize = 10L,
            offset = 0L,
        )
        assertEquals(1, inRange.size)
        assertEquals("current", inRange.first().message)
    }

    @Test
    fun `F - purgeByLevelOlderThan removes matching level and old entries only`() = runTest {
        val cutoff = now
        val old = cutoff - 100_000L

        repo.insert(makeLog(level = LogLevel.DEBUG, message = "old debug", createdAt = old))
        repo.insert(makeLog(level = LogLevel.DEBUG, message = "new debug", createdAt = cutoff + 1_000L))
        repo.insert(makeLog(level = LogLevel.ERROR, message = "old error", createdAt = old))

        repo.purgeByLevelOlderThan(LogLevel.DEBUG, cutoff)

        val remaining = repo.getPage(
            level = null,
            tag = null,
            fromEpochMillis = 0L,
            toEpochMillis = cutoff + 1_000_000L,
            pageSize = 100L,
            offset = 0L,
        )
        // old debug removed; new debug and old error remain
        assertEquals(2, remaining.size)
        assertTrue(remaining.none { it.level == LogLevel.DEBUG && it.message == "old debug" })
    }

    @Test
    fun `G - purgeAllOlderThan removes all entries before cutoff`() = runTest {
        val cutoff = now
        val old = cutoff - 100_000L

        repo.insert(makeLog(message = "old info", createdAt = old))
        repo.insert(makeLog(level = LogLevel.ERROR, message = "old error", createdAt = old))
        repo.insert(makeLog(message = "new info", createdAt = cutoff + 1_000L))

        repo.purgeAllOlderThan(cutoff)

        assertEquals(1L, repo.count())
        val remaining = repo.getPage(
            level = null,
            tag = null,
            fromEpochMillis = 0L,
            toEpochMillis = cutoff + 1_000_000L,
            pageSize = 100L,
            offset = 0L,
        )
        assertEquals("new info", remaining.first().message)
    }
}
