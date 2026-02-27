package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeBackupRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSystemRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildBackupInfo
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPurgeResult
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildSystemHealth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Admin use cases:
 * [GetSystemHealthUseCase], [CreateBackupUseCase], [DeleteBackupUseCase],
 * [GetBackupsUseCase], [VacuumDatabaseUseCase], [PurgeExpiredDataUseCase].
 */
class AdminUseCasesTest {

    // ─── GetSystemHealthUseCase ───────────────────────────────────────────────

    @Test
    fun `getSystemHealth - returns health snapshot from repository`() = runTest {
        val expected = buildSystemHealth(pendingSyncCount = 3, isOnline = false)
        val repo = FakeSystemRepository().also { it.healthToReturn = expected }

        val result = GetSystemHealthUseCase(repo)()

        assertIs<Result.Success<*>>(result)
        val health = (result as Result.Success).data
        assertEquals(expected.pendingSyncCount, health.pendingSyncCount)
        assertEquals(expected.isOnline, health.isOnline)
        assertEquals(expected.appVersion, health.appVersion)
    }

    @Test
    fun `getSystemHealth - propagates repository error`() = runTest {
        val repo = FakeSystemRepository().also { it.shouldFailHealth = true }

        val result = GetSystemHealthUseCase(repo)()

        assertIs<Result.Error>(result)
    }

    @Test
    fun `getSystemHealth - returns database size from health snapshot`() = runTest {
        val expected = buildSystemHealth(databaseSizeBytes = 5_000_000L)
        val repo = FakeSystemRepository().also { it.healthToReturn = expected }

        val result = GetSystemHealthUseCase(repo)()

        assertIs<Result.Success<*>>(result)
        assertEquals(5_000_000L, (result as Result.Success).data.databaseSizeBytes)
    }

    // ─── CreateBackupUseCase ──────────────────────────────────────────────────

    @Test
    fun `createBackup - delegates to repository with backupId and timestamp`() = runTest {
        val repo = FakeBackupRepository()
        val backupId = "backup-xyz"
        val timestamp = 1_700_000_000_000L

        val result = CreateBackupUseCase(repo)(backupId, timestamp)

        assertIs<Result.Success<*>>(result)
        val info = (result as Result.Success).data
        assertEquals(backupId, info.id)
        assertEquals(timestamp, info.createdAt)
        assertTrue(repo.backups.any { it.id == backupId })
    }

    @Test
    fun `createBackup - propagates repository error`() = runTest {
        val repo = FakeBackupRepository().also { it.shouldFailCreate = true }

        val result = CreateBackupUseCase(repo)("backup-01", 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `createBackup - stores backup in repository`() = runTest {
        val repo = FakeBackupRepository()

        CreateBackupUseCase(repo)("backup-01", 1_000L)
        CreateBackupUseCase(repo)("backup-02", 2_000L)

        assertEquals(2, repo.backups.size)
    }

    // ─── DeleteBackupUseCase ──────────────────────────────────────────────────

    @Test
    fun `deleteBackup - removes backup from repository`() = runTest {
        val repo = FakeBackupRepository()
        repo.backups.add(buildBackupInfo(id = "backup-to-delete"))
        repo.backups.add(buildBackupInfo(id = "backup-to-keep"))

        val result = DeleteBackupUseCase(repo)("backup-to-delete")

        assertIs<Result.Success<*>>(result)
        assertTrue(repo.backups.none { it.id == "backup-to-delete" })
        assertTrue(repo.backups.any { it.id == "backup-to-keep" })
    }

    @Test
    fun `deleteBackup - returns error when backup not found`() = runTest {
        val repo = FakeBackupRepository()

        val result = DeleteBackupUseCase(repo)("nonexistent-backup")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `deleteBackup - propagates repository error`() = runTest {
        val repo = FakeBackupRepository().also {
            it.backups.add(buildBackupInfo(id = "backup-01"))
            it.shouldFailDelete = true
        }

        val result = DeleteBackupUseCase(repo)("backup-01")

        assertIs<Result.Error>(result)
    }

    // ─── GetBackupsUseCase ────────────────────────────────────────────────────

    @Test
    fun `getBackups - emits all available backups from repository`() = runTest {
        val repo = FakeBackupRepository()
        repo.backups.add(buildBackupInfo(id = "backup-01"))
        repo.backups.add(buildBackupInfo(id = "backup-02"))
        // Manually sync the MutableStateFlow in the fake by triggering a create
        CreateBackupUseCase(repo)("backup-03", 3_000L)

        val flow = GetBackupsUseCase(repo)()
        val list = flow.first()

        // The flow reflects the current state of the internal MutableStateFlow
        assertTrue(list.any { it.id == "backup-03" })
    }

    @Test
    fun `getBackups - returns empty list when no backups exist`() = runTest {
        val repo = FakeBackupRepository()

        val flow = GetBackupsUseCase(repo)()
        val list = flow.first()

        assertTrue(list.isEmpty())
    }

    @Test
    fun `getBackups - flow updates after new backup is created`() = runTest {
        val repo = FakeBackupRepository()
        val useCase = GetBackupsUseCase(repo)

        val emptyList = useCase().first()
        assertTrue(emptyList.isEmpty())

        CreateBackupUseCase(repo)("backup-01", 1_000L)
        val updatedList = useCase().first()

        assertTrue(updatedList.any { it.id == "backup-01" })
    }

    // ─── VacuumDatabaseUseCase ────────────────────────────────────────────────

    @Test
    fun `vacuumDatabase - returns purge result from repository`() = runTest {
        val expected = buildPurgeResult(bytesFreed = 1_048_576L, durationMs = 500L, success = true)
        val repo = FakeSystemRepository().also { it.vacuumResultToReturn = expected }

        val result = VacuumDatabaseUseCase(repo)()

        assertIs<Result.Success<*>>(result)
        val purge = (result as Result.Success).data
        assertEquals(expected.bytesFreed, purge.bytesFreed)
        assertEquals(expected.durationMs, purge.durationMs)
        assertTrue(purge.success)
    }

    @Test
    fun `vacuumDatabase - propagates repository error`() = runTest {
        val repo = FakeSystemRepository().also { it.shouldFailVacuum = true }

        val result = VacuumDatabaseUseCase(repo)()

        assertIs<Result.Error>(result)
    }

    @Test
    fun `vacuumDatabase - success flag is true on successful vacuum`() = runTest {
        val repo = FakeSystemRepository().also {
            it.vacuumResultToReturn = buildPurgeResult(success = true)
        }

        val result = VacuumDatabaseUseCase(repo)()

        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.success)
    }

    // ─── PurgeExpiredDataUseCase ──────────────────────────────────────────────

    @Test
    fun `purgeExpiredData - delegates cutoff timestamp to repository`() = runTest {
        val cutoff = 1_690_000_000_000L
        val repo = FakeSystemRepository()

        PurgeExpiredDataUseCase(repo)(cutoff)

        assertEquals(cutoff, repo.lastPurgeThreshold)
    }

    @Test
    fun `purgeExpiredData - returns purge result from repository`() = runTest {
        val expected = buildPurgeResult(bytesFreed = 204_800L, durationMs = 100L, success = true)
        val repo = FakeSystemRepository().also { it.purgeResultToReturn = expected }

        val result = PurgeExpiredDataUseCase(repo)(olderThanMillis = 1_690_000_000_000L)

        assertIs<Result.Success<*>>(result)
        val purge = (result as Result.Success).data
        assertEquals(expected.bytesFreed, purge.bytesFreed)
        assertEquals(expected.durationMs, purge.durationMs)
    }

    @Test
    fun `purgeExpiredData - propagates repository error`() = runTest {
        val repo = FakeSystemRepository().also { it.shouldFailPurge = true }

        val result = PurgeExpiredDataUseCase(repo)(olderThanMillis = 1_000L)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `purgeExpiredData - zero threshold purges all records`() = runTest {
        val repo = FakeSystemRepository()

        PurgeExpiredDataUseCase(repo)(olderThanMillis = 0L)

        assertEquals(0L, repo.lastPurgeThreshold)
    }
}
