package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.domain.model.LogLevel
import com.zyntasolutions.zyntapos.domain.model.OperationalLog
import com.zyntasolutions.zyntapos.domain.repository.OperationalLogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — LogRetentionJob Unit Tests (commonTest)
 *
 * Validates log retention policy enforcement for `operational_logs` purging.
 *
 * Coverage:
 *  A. runRetention calls purgeVerboseDebugOlderThan (3-day policy)
 *  B. runRetention calls purgeByLevelOlderThan(INFO) (14-day policy)
 *  C. runRetention calls purgeByLevelOlderThan(WARN) (30-day policy)
 *  D. runRetention calls purgeByLevelOlderThan(ERROR) (90-day policy)
 *  E. runRetention calls purgeByLevelOlderThan(FATAL) (90-day policy)
 *  F. runRetention calls all 5 purge methods in one pass
 *  G. runRetention swallows non-cancellation exceptions without re-throwing
 *  H. cutoff timestamps are in the past (relative to now)
 */
class LogRetentionJobTest {

    // ── Fake ──────────────────────────────────────────────────────────────────

    private class FakeOperationalLogRepository : OperationalLogRepository {
        val verboseDebugCutoffs = mutableListOf<Long>()
        val purgedByLevel = mutableListOf<Pair<LogLevel, Long>>()
        var shouldThrow: Boolean = false

        override suspend fun insert(log: OperationalLog) {}
        override suspend fun insert(
            level: LogLevel,
            tag: String,
            message: String,
            stackTrace: String?,
            threadName: String?,
            sessionId: String?,
            metadata: String?,
        ) {}
        override suspend fun getPage(
            level: LogLevel?,
            tag: String?,
            fromEpochMillis: Long,
            toEpochMillis: Long,
            pageSize: Long,
            offset: Long,
        ): List<OperationalLog> = emptyList()
        override suspend fun count(): Long = 0L
        override suspend fun purgeAllOlderThan(olderThanEpochMillis: Long) {}

        override suspend fun purgeByLevelOlderThan(level: LogLevel, olderThanEpochMillis: Long) {
            if (shouldThrow) throw RuntimeException("purge failed")
            purgedByLevel.add(level to olderThanEpochMillis)
        }

        override suspend fun purgeVerboseDebugOlderThan(olderThanEpochMillis: Long) {
            if (shouldThrow) throw RuntimeException("purge failed")
            verboseDebugCutoffs.add(olderThanEpochMillis)
        }
    }

    private fun makeJob(repo: FakeOperationalLogRepository): LogRetentionJob {
        // Use a no-op scope — we call runRetention() directly in tests
        val scope = kotlinx.coroutines.MainScope()
        return LogRetentionJob(repository = repo, scope = scope)
    }

    // ── A — purgeVerboseDebug called ──────────────────────────────────────────

    @Test
    fun `A - runRetention calls purgeVerboseDebugOlderThan for 3-day policy`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        assertEquals(1, repo.verboseDebugCutoffs.size, "Expected exactly one purgeVerboseDebug call")
    }

    // ── B — INFO purge called ─────────────────────────────────────────────────

    @Test
    fun `B - runRetention calls purgeByLevelOlderThan for INFO 14-day policy`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        val infoCalls = repo.purgedByLevel.filter { it.first == LogLevel.INFO }
        assertEquals(1, infoCalls.size, "Expected exactly one purgeByLevelOlderThan(INFO) call")
    }

    // ── C — WARN purge called ─────────────────────────────────────────────────

    @Test
    fun `C - runRetention calls purgeByLevelOlderThan for WARN 30-day policy`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        val warnCalls = repo.purgedByLevel.filter { it.first == LogLevel.WARN }
        assertEquals(1, warnCalls.size, "Expected exactly one purgeByLevelOlderThan(WARN) call")
    }

    // ── D — ERROR purge called ────────────────────────────────────────────────

    @Test
    fun `D - runRetention calls purgeByLevelOlderThan for ERROR 90-day policy`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        val errorCalls = repo.purgedByLevel.filter { it.first == LogLevel.ERROR }
        assertEquals(1, errorCalls.size, "Expected exactly one purgeByLevelOlderThan(ERROR) call")
    }

    // ── E — FATAL purge called ────────────────────────────────────────────────

    @Test
    fun `E - runRetention calls purgeByLevelOlderThan for FATAL 90-day policy`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        val fatalCalls = repo.purgedByLevel.filter { it.first == LogLevel.FATAL }
        assertEquals(1, fatalCalls.size, "Expected exactly one purgeByLevelOlderThan(FATAL) call")
    }

    // ── F — All 5 purge calls made ────────────────────────────────────────────

    @Test
    fun `F - runRetention makes all 5 purge calls in one pass`() = runTest {
        val repo = FakeOperationalLogRepository()
        makeJob(repo).runRetention()

        // 1 verboseDebug + 4 level-specific (INFO, WARN, ERROR, FATAL)
        assertEquals(1, repo.verboseDebugCutoffs.size)
        assertEquals(4, repo.purgedByLevel.size, "Expected 4 level-specific purge calls")

        val levels = repo.purgedByLevel.map { it.first }.toSet()
        assertTrue(LogLevel.INFO in levels)
        assertTrue(LogLevel.WARN in levels)
        assertTrue(LogLevel.ERROR in levels)
        assertTrue(LogLevel.FATAL in levels)
    }

    // ── G — Exception swallowed ───────────────────────────────────────────────

    @Test
    fun `G - runRetention swallows non-cancellation exceptions without re-throwing`() = runTest {
        val repo = FakeOperationalLogRepository().apply { shouldThrow = true }

        // Must not throw
        makeJob(repo).runRetention()
    }

    // ── H — Cutoffs are in the past ───────────────────────────────────────────

    @Test
    fun `H - all purge cutoff timestamps are in the past relative to now`() = runTest {
        val repo = FakeOperationalLogRepository()
        val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()

        makeJob(repo).runRetention()

        repo.verboseDebugCutoffs.forEach { cutoff ->
            assertTrue(cutoff < nowMs, "Verbose/Debug cutoff $cutoff must be before now ($nowMs)")
        }
        repo.purgedByLevel.forEach { (level, cutoff) ->
            assertTrue(cutoff < nowMs, "$level cutoff $cutoff must be before now ($nowMs)")
        }
    }
}
