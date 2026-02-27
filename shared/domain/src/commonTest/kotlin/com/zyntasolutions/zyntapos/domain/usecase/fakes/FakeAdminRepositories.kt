package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.BackupStatus
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a [BackupInfo] with sensible defaults. */
fun buildBackupInfo(
    id: String = "backup-01",
    fileName: String = "zyntapos_backup_01.db",
    filePath: String = "/backups/zyntapos_backup_01.db",
    sizeBytes: Long = 1_048_576L,
    status: BackupStatus = BackupStatus.SUCCESS,
    createdAt: Long = 1_700_000_000_000L,
    completedAt: Long? = 1_700_000_005_000L,
    schemaVersion: Long = 1L,
    appVersion: String = "1.0.0",
) = BackupInfo(
    id = id, fileName = fileName, filePath = filePath, sizeBytes = sizeBytes,
    status = status, createdAt = createdAt, completedAt = completedAt,
    schemaVersion = schemaVersion, appVersion = appVersion,
)

/** Builds a [SystemHealth] snapshot with sensible defaults. */
fun buildSystemHealth(
    databaseSizeBytes: Long = 2_097_152L,
    totalMemoryBytes: Long = 4_294_967_296L,
    usedMemoryBytes: Long = 1_073_741_824L,
    pendingSyncCount: Int = 0,
    lastSyncAt: Long? = null,
    appVersion: String = "1.0.0",
    buildNumber: Int = 1,
    isOnline: Boolean = true,
) = SystemHealth(
    databaseSizeBytes = databaseSizeBytes,
    totalMemoryBytes = totalMemoryBytes,
    usedMemoryBytes = usedMemoryBytes,
    pendingSyncCount = pendingSyncCount,
    lastSyncAt = lastSyncAt,
    appVersion = appVersion,
    buildNumber = buildNumber,
    isOnline = isOnline,
)

/** Builds a [PurgeResult] with sensible defaults. */
fun buildPurgeResult(
    bytesFreed: Long = 524_288L,
    durationMs: Long = 250L,
    success: Boolean = true,
) = PurgeResult(bytesFreed = bytesFreed, durationMs = durationMs, success = success)

/** Builds an [AccountingEntry] with sensible defaults. */
fun buildAccountingEntry(
    id: String = "entry-01",
    storeId: String = "store-01",
    accountCode: String = "4000",
    accountName: String = "Sales Revenue",
    entryType: com.zyntasolutions.zyntapos.domain.model.AccountingEntryType =
        com.zyntasolutions.zyntapos.domain.model.AccountingEntryType.CREDIT,
    amount: Double = 100.0,
    referenceType: AccountingReferenceType = AccountingReferenceType.ORDER,
    referenceId: String = "order-01",
    entryDate: String = "2026-02-27",
    fiscalPeriod: String = "2026-02",
    createdBy: String = "user-01",
    createdAt: Long = 1_700_000_000_000L,
) = AccountingEntry(
    id = id, storeId = storeId, accountCode = accountCode, accountName = accountName,
    entryType = entryType, amount = amount, referenceType = referenceType,
    referenceId = referenceId, entryDate = entryDate, fiscalPeriod = fiscalPeriod,
    createdBy = createdBy, createdAt = createdAt,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeBackupRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeBackupRepository : BackupRepository {
    val backups = mutableListOf<BackupInfo>()
    private val _backups = MutableStateFlow<List<BackupInfo>>(emptyList())
    var shouldFailCreate: Boolean = false
    var shouldFailDelete: Boolean = false
    var shouldFailRestore: Boolean = false
    var shouldFailExport: Boolean = false

    override fun getAll(): Flow<List<BackupInfo>> = _backups

    override suspend fun getById(id: String): Result<BackupInfo> {
        val backup = backups.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Backup '$id' not found"))
        return Result.Success(backup)
    }

    override suspend fun createBackup(backupId: String, timestamp: Long): Result<BackupInfo> {
        if (shouldFailCreate) return Result.Error(DatabaseException("Backup creation failed"))
        val info = buildBackupInfo(id = backupId, createdAt = timestamp)
        backups.add(info)
        _backups.value = backups.toList()
        return Result.Success(info)
    }

    override suspend fun restoreBackup(backupId: String): Result<Unit> {
        if (shouldFailRestore) return Result.Error(DatabaseException("Restore failed"))
        val exists = backups.any { it.id == backupId }
        if (!exists) return Result.Error(DatabaseException("Backup '$backupId' not found"))
        return Result.Success(Unit)
    }

    override suspend fun deleteBackup(id: String): Result<Unit> {
        if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
        val removed = backups.removeAll { it.id == id }
        if (!removed) return Result.Error(DatabaseException("Backup '$id' not found"))
        _backups.value = backups.toList()
        return Result.Success(Unit)
    }

    override suspend fun exportBackup(id: String, exportPath: String): Result<Unit> {
        if (shouldFailExport) return Result.Error(DatabaseException("Export failed"))
        val exists = backups.any { it.id == id }
        if (!exists) return Result.Error(DatabaseException("Backup '$id' not found"))
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeSystemRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeSystemRepository : SystemRepository {
    var healthToReturn: SystemHealth = buildSystemHealth()
    var purgeResultToReturn: PurgeResult = buildPurgeResult()
    var vacuumResultToReturn: PurgeResult = buildPurgeResult()
    var shouldFailHealth: Boolean = false
    var shouldFailVacuum: Boolean = false
    var shouldFailPurge: Boolean = false
    var lastPurgeThreshold: Long? = null

    override suspend fun getSystemHealth(): Result<SystemHealth> {
        if (shouldFailHealth) return Result.Error(DatabaseException("Health check failed"))
        return Result.Success(healthToReturn)
    }

    override suspend fun getDatabaseStats(): Result<DatabaseStats> =
        Result.Success(DatabaseStats(totalRows = 1000L, tables = emptyList(), sizeBytes = 2_097_152L))

    override suspend fun vacuumDatabase(): Result<PurgeResult> {
        if (shouldFailVacuum) return Result.Error(DatabaseException("VACUUM failed"))
        return Result.Success(vacuumResultToReturn)
    }

    override suspend fun purgeExpiredData(olderThanMillis: Long): Result<PurgeResult> {
        if (shouldFailPurge) return Result.Error(DatabaseException("Purge failed"))
        lastPurgeThreshold = olderThanMillis
        return Result.Success(purgeResultToReturn)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeAccountingRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeAccountingRepository : AccountingRepository {
    val insertedEntries = mutableListOf<AccountingEntry>()
    var shouldFailInsert: Boolean = false

    override suspend fun getByStoreAndPeriod(
        storeId: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>> =
        Result.Success(insertedEntries.filter { it.storeId == storeId && it.fiscalPeriod == fiscalPeriod })

    override suspend fun getByAccountAndPeriod(
        storeId: String,
        accountCode: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>> =
        Result.Success(
            insertedEntries.filter {
                it.storeId == storeId && it.accountCode == accountCode && it.fiscalPeriod == fiscalPeriod
            },
        )

    override suspend fun getByReference(
        referenceType: AccountingReferenceType,
        referenceId: String,
    ): Result<List<AccountingEntry>> =
        Result.Success(
            insertedEntries.filter {
                it.referenceType == referenceType && it.referenceId == referenceId
            },
        )

    override suspend fun getSummaryForPeriodRange(
        storeId: String,
        fromPeriod: String,
        toPeriod: String,
    ): Result<List<AccountSummary>> = Result.Success(emptyList())

    override suspend fun insertEntries(entries: List<AccountingEntry>): Result<Unit> {
        if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
        insertedEntries.addAll(entries)
        return Result.Success(Unit)
    }
}
