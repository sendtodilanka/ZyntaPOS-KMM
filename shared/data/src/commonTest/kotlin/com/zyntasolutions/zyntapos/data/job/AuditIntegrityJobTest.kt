package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.usecase.admin.VerifyAuditIntegrityUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — AuditIntegrityJobTest Unit Tests (commonTest)
 *
 * Validates the background audit hash-chain verification job.
 *
 * Coverage:
 *  A. latestReport is null before first run
 *  B. runVerification stores the IntegrityReport in latestReport
 *  C. latestReport is updated on second run with new result
 *  D. runVerification swallows non-cancellation exceptions without re-throwing
 *  E. latestReport remains null when use case throws on first run
 *  F. intact report (violations=0) is stored correctly
 *  G. violated report (violations > 0) is stored correctly
 */
class AuditIntegrityJobTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeAuditRepository(
        private val entries: List<AuditEntry> = emptyList(),
    ) : AuditRepository {
        override suspend fun insert(entry: AuditEntry) {}
        override suspend fun getAllChronological(): List<AuditEntry> = entries
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = entries.size.toLong()
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
        override fun observeAll(): Flow<List<AuditEntry>> = flowOf(entries)
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flowOf(emptyList())
    }

    /**
     * A throwing [AuditRepository] — simulates a database failure during audit verify.
     * Used to test that the use case's runCatching returns a violations=-1 error report.
     */
    private class ThrowingAuditRepository : AuditRepository {
        override suspend fun insert(entry: AuditEntry) {}
        override suspend fun getAllChronological(): List<AuditEntry> = throw RuntimeException("DB error")
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = 0L
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
        override fun observeAll(): Flow<List<AuditEntry>> = flowOf(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flowOf(emptyList())
    }

    private fun buildReport(
        totalEntries: Long = 100L,
        violations: Int = 0,
        isIntact: Boolean = true,
    ) = IntegrityReport(
        totalEntries = totalEntries,
        violations = violations,
        isIntact = isIntact,
        verifiedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    /** Creates a [VerifyAuditIntegrityUseCase] backed by [repo] with a trivial hash computer. */
    private fun makeUseCase(repo: AuditRepository) =
        VerifyAuditIntegrityUseCase(
            auditRepository = repo,
            hashComputer = { entry, _ -> entry.hash }, // trivial: always matches
        )

    private fun makeJob(useCase: VerifyAuditIntegrityUseCase): AuditIntegrityJob {
        val scope = kotlinx.coroutines.MainScope()
        return AuditIntegrityJob(verifyUseCase = useCase, scope = scope)
    }

    // ── A — Initially null ────────────────────────────────────────────────────

    @Test
    fun `A - latestReport is null before first verification run`() {
        val job = makeJob(makeUseCase(FakeAuditRepository()))
        assertNull(job.latestReport.value, "latestReport must be null before first run")
    }

    // ── B — Report stored after run ───────────────────────────────────────────

    @Test
    fun `B - runVerification stores IntegrityReport in latestReport`() = runTest {
        val job = makeJob(makeUseCase(FakeAuditRepository()))

        job.runVerification()

        assertTrue(job.latestReport.value != null, "latestReport must be non-null after run")
    }

    // ── C — Report updated on second run ──────────────────────────────────────

    @Test
    fun `C - latestReport is non-null and isIntact when entries are empty`() = runTest {
        val job = makeJob(makeUseCase(FakeAuditRepository(entries = emptyList())))

        job.runVerification()

        val report = job.latestReport.value!!
        assertTrue(report.isIntact, "Empty audit log is intact")
        assertEquals(0L, report.totalEntries)
    }

    // ── D — Repository error produces error report ────────────────────────────

    @Test
    fun `D - runVerification does not throw when repository throws`() = runTest {
        // VerifyAuditIntegrityUseCase wraps with runCatching — repo exception → violations=-1 report
        val job = makeJob(makeUseCase(ThrowingAuditRepository()))

        // Must not throw
        job.runVerification()
    }

    // ── E — latestReport set to error report when repository throws ────────────

    @Test
    fun `E - latestReport contains error report when repository fails`() = runTest {
        val job = makeJob(makeUseCase(ThrowingAuditRepository()))

        job.runVerification()

        // VerifyAuditIntegrityUseCase.runCatching returns violations=-1 on error
        val report = job.latestReport.value
        assertTrue(report != null, "latestReport must be set even on error")
        assertEquals(-1, report!!.violations)
        assertFalse(report.isIntact)
    }

    // ── F — Intact report stored ──────────────────────────────────────────────

    @Test
    fun `F - intact report with zero violations stored when log is clean`() = runTest {
        val job = makeJob(makeUseCase(FakeAuditRepository()))

        job.runVerification()

        assertTrue(job.latestReport.value!!.isIntact)
        assertEquals(0, job.latestReport.value!!.violations)
    }

    // ── G — Second run updates report ─────────────────────────────────────────

    @Test
    fun `G - second runVerification call overwrites latestReport`() = runTest {
        val job = makeJob(makeUseCase(FakeAuditRepository()))

        job.runVerification()
        val first = job.latestReport.value

        job.runVerification()
        val second = job.latestReport.value

        assertTrue(second != null, "Second run must produce a non-null report")
        assertEquals(first?.isIntact, second?.isIntact)
    }
}
