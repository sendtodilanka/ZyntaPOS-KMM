package com.zyntasolutions.zyntapos.feature.admin

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.BackupStatus
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository
import com.zyntasolutions.zyntapos.domain.usecase.admin.CreateBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.DeleteBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetBackupsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetDatabaseStatsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetSystemHealthUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.RestoreBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// AdminViewModelTest
// Tests AdminViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake state ────────────────────────────────────────────────────────────

    private val backupsFlow = MutableStateFlow<List<BackupInfo>>(emptyList())
    private val auditFlow = MutableStateFlow<List<AuditEntry>>(emptyList())
    private var shouldFailHealth = false
    private var shouldFailVacuum = false
    private var shouldFailCreateBackup = false
    private var shouldFailRestoreBackup = false
    private var shouldFailDeleteBackup = false

    private val testBackup = BackupInfo(
        id = "backup-001",
        fileName = "backup_001.db",
        filePath = "/storage/backups/backup_001.db",
        sizeBytes = 1024L * 1024L,
        status = BackupStatus.SUCCESS,
        createdAt = System.currentTimeMillis(),
        schemaVersion = 1L,
        appVersion = "1.0.0",
    )

    private val fakeSystemHealth = SystemHealth(
        databaseSizeBytes = 1024L * 512L,
        totalMemoryBytes = 1024L * 1024L * 4L,
        usedMemoryBytes = 1024L * 1024L * 2L,
        pendingSyncCount = 0,
        appVersion = "1.0.0",
        buildNumber = 1,
        isOnline = true,
    )

    private val fakeDatabaseStats = DatabaseStats(
        totalRows = 500L,
        tables = emptyList(),
        sizeBytes = 1024L * 512L,
    )

    // ── Fake SystemRepository ─────────────────────────────────────────────────

    private val fakeSystemRepository = object : SystemRepository {
        override suspend fun getSystemHealth(): Result<SystemHealth> =
            if (shouldFailHealth) Result.Error(DatabaseException("Health check failed"))
            else Result.Success(fakeSystemHealth)

        override suspend fun getDatabaseStats(): Result<DatabaseStats> =
            Result.Success(fakeDatabaseStats)

        override suspend fun vacuumDatabase(): Result<PurgeResult> =
            if (shouldFailVacuum) Result.Error(DatabaseException("Vacuum failed"))
            else Result.Success(PurgeResult(bytesFreed = 1024L * 32L, durationMs = 150L, success = true))

        override suspend fun purgeExpiredData(olderThanMillis: Long): Result<PurgeResult> =
            Result.Success(PurgeResult(bytesFreed = 50L, durationMs = 80L, success = true))
    }

    // ── Fake BackupRepository ─────────────────────────────────────────────────

    private val fakeBackupRepository = object : BackupRepository {
        override fun getAll(): Flow<List<BackupInfo>> = backupsFlow

        override suspend fun getById(id: String): Result<BackupInfo> {
            val backup = backupsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Backup '$id' not found"))
            return Result.Success(backup)
        }

        override suspend fun createBackup(backupId: String, timestamp: Long): Result<BackupInfo> {
            if (shouldFailCreateBackup) return Result.Error(DatabaseException("Create backup failed"))
            val backup = testBackup.copy(id = backupId, createdAt = timestamp)
            backupsFlow.value = backupsFlow.value + backup
            return Result.Success(backup)
        }

        override suspend fun restoreBackup(backupId: String): Result<Unit> =
            if (shouldFailRestoreBackup) Result.Error(DatabaseException("Restore failed"))
            else Result.Success(Unit)

        override suspend fun deleteBackup(id: String): Result<Unit> {
            if (shouldFailDeleteBackup) return Result.Error(DatabaseException("Delete failed"))
            backupsFlow.value = backupsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override suspend fun exportBackup(id: String, exportPath: String): Result<Unit> =
            Result.Success(Unit)
    }

    // ── Fake AuditRepository ──────────────────────────────────────────────────

    private val fakeAuditRepository = object : AuditRepository {
        override suspend fun insert(entry: AuditEntry) {
            auditFlow.value = auditFlow.value + entry
        }

        override fun observeAll(): Flow<List<AuditEntry>> = auditFlow

        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> =
            MutableStateFlow(auditFlow.value.filter { it.userId == userId })
    }

    // ── Use cases wired to fakes ──────────────────────────────────────────────

    private val getSystemHealthUseCase = GetSystemHealthUseCase(fakeSystemRepository)
    private val getDatabaseStatsUseCase = GetDatabaseStatsUseCase(fakeSystemRepository)
    private val getBackupsUseCase = GetBackupsUseCase(fakeBackupRepository)
    private val createBackupUseCase = CreateBackupUseCase(fakeBackupRepository)
    private val restoreBackupUseCase = RestoreBackupUseCase(fakeBackupRepository)
    private val deleteBackupUseCase = DeleteBackupUseCase(fakeBackupRepository)

    private lateinit var viewModel: AdminViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        backupsFlow.value = emptyList()
        auditFlow.value = emptyList()
        shouldFailHealth = false
        shouldFailVacuum = false
        shouldFailCreateBackup = false
        shouldFailRestoreBackup = false
        shouldFailDeleteBackup = false
        viewModel = AdminViewModel(
            getSystemHealthUseCase = getSystemHealthUseCase,
            getDatabaseStatsUseCase = getDatabaseStatsUseCase,
            systemRepository = fakeSystemRepository,
            getBackupsUseCase = getBackupsUseCase,
            createBackupUseCase = createBackupUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            deleteBackupUseCase = deleteBackupUseCase,
            auditRepository = fakeAuditRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state starts on SYSTEM_HEALTH tab with no error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(AdminTab.SYSTEM_HEALTH, state.activeTab)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init auto-loads system health and database stats`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertNotNull(state.systemHealth)
        assertNotNull(state.databaseStats)
        assertEquals("1.0.0", state.systemHealth?.appVersion)
        assertEquals(500L, state.databaseStats?.totalRows)
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    @Test
    fun `SwitchTab updates activeTab in state`() = runTest {
        viewModel.dispatch(AdminIntent.SwitchTab(AdminTab.BACKUPS))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AdminTab.BACKUPS, viewModel.state.value.activeTab)
    }

    // ── System Health ─────────────────────────────────────────────────────────

    @Test
    fun `RefreshSystemHealth on failure sets error in state`() = runTest {
        shouldFailHealth = true
        viewModel.dispatch(AdminIntent.RefreshSystemHealth)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Health check failed"))
    }

    @Test
    fun `RunVacuum on success sets successMessage and lastVacuumResult`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(AdminIntent.RunVacuum)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("Vacuum completed"))
        assertNotNull(state.lastVacuumResult)
        assertTrue(state.lastVacuumResult!!.success)
    }

    @Test
    fun `RunVacuum on failure sets error in state`() = runTest {
        shouldFailVacuum = true
        viewModel.dispatch(AdminIntent.RunVacuum)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Vacuum failed"))
    }

    @Test
    fun `PurgeExpiredData on success sets successMessage with purge info`() = runTest {
        viewModel.dispatch(AdminIntent.PurgeExpiredData(olderThanDays = 30))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("Purge complete"))
    }

    // ── Backup CRUD ───────────────────────────────────────────────────────────

    @Test
    fun `CreateBackup on success adds backup to backups flow and sets successMessage`() = runTest {
        viewModel.dispatch(AdminIntent.CreateBackup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, backupsFlow.value.size)
        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(viewModel.state.value.successMessage!!.contains("Backup created"))
    }

    @Test
    fun `CreateBackup on failure sets error in state`() = runTest {
        shouldFailCreateBackup = true
        viewModel.dispatch(AdminIntent.CreateBackup)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(backupsFlow.value.isEmpty())
    }

    @Test
    fun `RestoreBackup sets showRestoreConfirm then ConfirmRestore emits RestartRequired`() = runTest {
        backupsFlow.value = listOf(testBackup)
        testDispatcher.scheduler.advanceUntilIdle()

        // Opening restore dialog sets showRestoreConfirm
        viewModel.dispatch(AdminIntent.RestoreBackup(testBackup))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showRestoreConfirm)
        assertEquals(testBackup.id, viewModel.state.value.showRestoreConfirm?.id)

        // Confirming restore should emit RestartRequired effect
        viewModel.effects.test {
            viewModel.dispatch(AdminIntent.ConfirmRestore)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is AdminEffect.RestartRequired)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.showRestoreConfirm)
        assertNotNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `CancelRestore clears showRestoreConfirm without triggering restore`() = runTest {
        viewModel.dispatch(AdminIntent.RestoreBackup(testBackup))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showRestoreConfirm)

        viewModel.dispatch(AdminIntent.CancelRestore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.showRestoreConfirm)
    }

    @Test
    fun `DeleteBackup then ConfirmDelete removes backup and sets successMessage`() = runTest {
        backupsFlow.value = listOf(testBackup)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(AdminIntent.DeleteBackup(testBackup))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showDeleteConfirm)

        viewModel.dispatch(AdminIntent.ConfirmDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(backupsFlow.value.isEmpty())
        assertNull(viewModel.state.value.showDeleteConfirm)
        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(viewModel.state.value.successMessage!!.contains("Backup deleted"))
    }

    @Test
    fun `CancelDelete clears showDeleteConfirm without deleting backup`() = runTest {
        backupsFlow.value = listOf(testBackup)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(AdminIntent.DeleteBackup(testBackup))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showDeleteConfirm)

        viewModel.dispatch(AdminIntent.CancelDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.showDeleteConfirm)
        assertEquals(1, backupsFlow.value.size) // backup not deleted
    }

    // ── Audit Log ─────────────────────────────────────────────────────────────

    @Test
    fun `audit entries from repository are reflected in state reactively`() = runTest {
        val entry = AuditEntry(
            id = "audit-001",
            eventType = AuditEventType.LOGIN_ATTEMPT,
            userId = "user-001",
            deviceId = "device-001",
            payload = "{}",
            success = true,
            createdAt = Clock.System.now(),
        )
        auditFlow.value = listOf(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.auditEntries.size)
        assertEquals("audit-001", viewModel.state.value.auditEntries.first().id)
    }

    @Test
    fun `FilterAuditByUser updates auditUserFilter in state`() = runTest {
        viewModel.dispatch(AdminIntent.FilterAuditByUser("user-007"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user-007", viewModel.state.value.auditUserFilter)
    }

    // ── UI Feedback ───────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error in state`() = runTest {
        shouldFailHealth = true
        viewModel.dispatch(AdminIntent.RefreshSystemHealth)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(AdminIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage in state`() = runTest {
        viewModel.dispatch(AdminIntent.CreateBackup)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.successMessage)

        viewModel.dispatch(AdminIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `RefreshDatabaseStats on success loads latest database stats`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(AdminIntent.RefreshDatabaseStats)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.databaseStats)
        assertEquals(500L, viewModel.state.value.databaseStats!!.totalRows)
    }
}
