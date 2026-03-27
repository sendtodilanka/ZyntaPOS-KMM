package com.zyntasolutions.zyntapos.domain.usecase.admin

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeBackupRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSystemRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildBackupInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeConflictRepo : ConflictLogRepository {
    private val _unresolved = MutableStateFlow<List<SyncConflict>>(emptyList())
    val resolvedCalls = mutableListOf<Triple<String, SyncConflict.Resolution, String>>()

    fun seed(vararg conflicts: SyncConflict) {
        _unresolved.value = conflicts.toList()
    }

    override fun getUnresolved(): Flow<List<SyncConflict>> = _unresolved
    override fun getByEntity(entityType: String, entityId: String): Flow<List<SyncConflict>> =
        flowOf(_unresolved.value.filter { it.entityType == entityType })

    override suspend fun getUnresolvedCount(): Result<Int> =
        Result.Success(_unresolved.value.count { !it.isResolved })

    override suspend fun insert(conflict: SyncConflict): Result<Unit> {
        _unresolved.value = _unresolved.value + conflict
        return Result.Success(Unit)
    }

    override suspend fun resolve(
        id: String,
        resolvedBy: SyncConflict.Resolution,
        resolution: String,
        resolvedAt: Long,
    ): Result<Unit> {
        resolvedCalls.add(Triple(id, resolvedBy, resolution))
        _unresolved.value = _unresolved.value.filter { it.id != id }
        return Result.Success(Unit)
    }

    override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> = Result.Success(Unit)
}

private class FakeAuditRepo(private val entries: List<AuditEntry> = emptyList()) : AuditRepository {
    override suspend fun insert(entry: AuditEntry) {}
    override fun observeAll(): Flow<List<AuditEntry>> = flowOf(entries)
    override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = flowOf(emptyList())
    override suspend fun getAllChronological(): List<AuditEntry> = entries
    override suspend fun getLatestHash(): String? = entries.lastOrNull()?.hash
    override suspend fun countEntries(): Long = entries.size.toLong()
    override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
}

private fun buildConflict(
    id: String = "c1",
    resolved: Boolean = false,
) = SyncConflict(
    id = id,
    entityType = "products",
    entityId = "p1",
    fieldName = "price",
    localValue = "100",
    serverValue = "110",
    resolvedAt = if (resolved) 1_000_000L else null,
    createdAt = 500_000L,
)

private fun buildAuditEntry(
    id: String,
    hash: String,
    previousHash: String,
) = AuditEntry(
    id = id,
    eventType = AuditEventType.LOGIN_ATTEMPT,
    userId = "user-01",
    userName = "Test User",
    userRole = Role.CASHIER,
    deviceId = "device-01",
    entityType = null,
    entityId = null,
    payload = "{}",
    previousValue = null,
    newValue = null,
    success = true,
    ipAddress = null,
    hash = hash,
    previousHash = previousHash,
    createdAt = Instant.fromEpochSeconds(0),
)

// ─────────────────────────────────────────────────────────────────────────────
// GetConflictCountUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetConflictCountUseCaseTest {

    @Test
    fun `noConflicts_returnsZero`() = runTest {
        val result = GetConflictCountUseCase(FakeConflictRepo()).invoke()
        assertIs<Result.Success<*>>(result)
        assertEquals(0, (result as Result.Success).data)
    }

    @Test
    fun `twoUnresolved_returnsTwo`() = runTest {
        val repo = FakeConflictRepo()
        repo.seed(buildConflict("c1"), buildConflict("c2"))
        val result = GetConflictCountUseCase(repo).invoke()
        assertIs<Result.Success<*>>(result)
        assertEquals(2, (result as Result.Success).data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetUnresolvedConflictsUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetUnresolvedConflictsUseCaseTest {

    @Test
    fun `emitsUnresolvedConflicts`() = runTest {
        val repo = FakeConflictRepo()
        repo.seed(buildConflict("c1"), buildConflict("c2"))

        GetUnresolvedConflictsUseCase(repo).invoke().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emptyRepo_emitsEmptyList`() = runTest {
        GetUnresolvedConflictsUseCase(FakeConflictRepo()).invoke().test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ResolveConflictUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ResolveConflictUseCaseTest {

    @Test
    fun `resolveManually_delegatesToRepo`() = runTest {
        val repo = FakeConflictRepo()
        repo.seed(buildConflict("c1"))

        val result = ResolveConflictUseCase(repo).invoke("c1", SyncConflict.Resolution.MANUAL, "110")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.resolvedCalls.size)
        val (id, strategy, value) = repo.resolvedCalls.first()
        assertEquals("c1", id)
        assertEquals(SyncConflict.Resolution.MANUAL, strategy)
        assertEquals("110", value)
    }

    @Test
    fun `resolveWithServer_delegatesServerStrategy`() = runTest {
        val repo = FakeConflictRepo()
        repo.seed(buildConflict("c2"))

        ResolveConflictUseCase(repo).invoke("c2", SyncConflict.Resolution.SERVER, "110")
        assertEquals(SyncConflict.Resolution.SERVER, repo.resolvedCalls.first().second)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetDatabaseStatsUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetDatabaseStatsUseCaseTest {

    @Test
    fun `invoke_returnsDatabaseStats`() = runTest {
        val result = GetDatabaseStatsUseCase(FakeSystemRepository()).invoke()
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.totalRows >= 0L)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RestoreBackupUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class RestoreBackupUseCaseTest {

    @Test
    fun `existingBackup_restoresSuccessfully`() = runTest {
        val repo = FakeBackupRepository()
        repo.backups.add(buildBackupInfo(id = "backup-01"))

        val result = RestoreBackupUseCase(repo).invoke("backup-01")
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `nonExistingBackup_returnsError`() = runTest {
        val result = RestoreBackupUseCase(FakeBackupRepository()).invoke("missing")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `repoFailure_propagatesError`() = runTest {
        val repo = FakeBackupRepository().also {
            it.backups.add(buildBackupInfo(id = "backup-01"))
            it.shouldFailRestore = true
        }
        val result = RestoreBackupUseCase(repo).invoke("backup-01")
        assertIs<Result.Error>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VerifyAuditIntegrityUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class VerifyAuditIntegrityUseCaseTest {

    private fun makeUseCase(entries: List<AuditEntry>): VerifyAuditIntegrityUseCase =
        VerifyAuditIntegrityUseCase(FakeAuditRepo(entries)) { entry, _ -> entry.hash }

    @Test
    fun `emptyLog_reportsIntact`() = runTest {
        val report = makeUseCase(emptyList()).invoke()
        assertTrue(report.isIntact)
        assertEquals(0L, report.totalEntries)
        assertEquals(0, report.violations)
    }

    @Test
    fun `validChain_reportsIntact`() = runTest {
        val entries = listOf(
            buildAuditEntry("e1", hash = "hash01", previousHash = "GENESIS"),
            buildAuditEntry("e2", hash = "hash02", previousHash = "hash01"),
        )
        val report = makeUseCase(entries).invoke()
        assertTrue(report.isIntact)
        assertEquals(2L, report.totalEntries)
        assertEquals(0, report.violations)
    }

    @Test
    fun `brokenPreviousHashChain_reportsViolation`() = runTest {
        val entries = listOf(
            buildAuditEntry("e1", hash = "hash01", previousHash = "GENESIS"),
            buildAuditEntry("e2", hash = "hash02", previousHash = "WRONG"), // chain broken
        )
        val report = makeUseCase(entries).invoke()
        assertFalse(report.isIntact)
        assertTrue(report.violations > 0)
    }

    @Test
    fun `legacyEmptyHashEntries_skipped`() = runTest {
        val entries = listOf(buildAuditEntry("e1", hash = "", previousHash = ""))
        val report = makeUseCase(entries).invoke()
        assertTrue(report.isIntact)
        assertEquals(0, report.violations)
    }
}
