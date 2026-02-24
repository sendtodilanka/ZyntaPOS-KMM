# ZyntaPOS — Phase 3 Sprint 7: Admin + Accounting Repos + Navigation Extensions

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT7-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 7 of 24 | Week 7
> **Module(s):** `:shared:data`, `:composeApp:navigation`, `:shared:security`
> **Author:** Senior KMP Architect & Lead Engineer

---

## Goal

Implement repository classes for Admin, Accounting, Warehouse Racks, and E-Invoice; wire all Phase 3 navigation routes, permissions, and nav items; register all new Koin bindings in `DataModule` and `ZyntaApplication`.

---

## New Repository Implementation Files

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/repository/`

### `SystemRepositoryImpl.kt`
```kotlin
class SystemRepositoryImpl(
    private val db: ZyntaPosDatabase,
    private val syncQueueRepository: SyncQueueRepository,
    private val memoryInfoProvider: MemoryInfoProvider,   // expect/actual
    private val appVersion: String                         // from BuildConfig
) : SystemRepository {

    override suspend fun getSystemHealth(): SystemHealth {
        val dbFile = db.driver.getDatabaseFile()   // platform-specific
        val dbSizeMb = dbFile?.length()?.div(1024.0 * 1024.0) ?: 0.0
        val pendingSync = db.syncQueueQueries.countByStatus("PENDING").executeAsOne()
        val failedSync = db.syncQueueQueries.countByStatus("FAILED").executeAsOne()
        val lastSync = db.syncStateQueries.selectAll().executeAsList()
            .mapNotNull { it.last_sync_at }.maxOrNull()
        val memory = memoryInfoProvider.getMemoryInfo()
        return SystemHealth(
            dbSizeMb = dbSizeMb,
            walSizeMb = 0.0,
            pendingSyncCount = pendingSync.toInt(),
            failedSyncCount = failedSync.toInt(),
            lastSyncAt = lastSync,
            syncStatus = "IDLE",
            uptime = System.currentTimeMillis() - appStartTime,
            memoryUsedMb = memory.usedMb,
            memoryTotalMb = memory.totalMb,
            appVersion = appVersion,
            dbVersion = 7 // current DB version
        )
    }

    override suspend fun getDatabaseStats(): DatabaseStats {
        // Query sqlite_master for table count and row estimates
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        )
        // ... implementation
        return DatabaseStats(
            tableCount = tables.size,
            totalRows = 0L, // computed from each table
            indexCount = 0,
            dbVersion = 7,
            lastVacuumAt = null,
            walSizeMb = 0.0,
            dbSizeMb = 0.0,
            tables = emptyList()
        )
    }

    override suspend fun vacuumDatabase(): Result<Unit> = runCatching {
        db.execute(null, "VACUUM", 0)
    }

    override suspend fun purgeExpiredData(): Result<PurgeResult> = runCatching {
        val now = Clock.System.now()
        var deletedRows = 0
        // Purge sync_queue COMPLETED older than 90 days
        val cutoff90d = (now - 90.days).toString()
        db.syncQueueQueries.deleteCompletedBefore(cutoff90d)
        // Purge sessions expired > 30 days ago
        // Purge conflict_log older than 1 year
        val cutoff1y = (now - 365.days).toString()
        db.conflictLogQueries.deleteBefore(cutoff1y)
        PurgeResult(deletedRows = deletedRows, freedMb = 0.0, categories = mapOf())
    }

    companion object {
        private val appStartTime = System.currentTimeMillis()
    }
}
```

### `BackupRepositoryImpl.kt`
```kotlin
class BackupRepositoryImpl(
    private val backupDriver: BackupDriver,    // expect/actual
    private val cryptoManager: CryptoManager   // from :shared:security
) : BackupRepository {

    override fun listBackups(): Flow<List<BackupInfo>> = flow {
        emit(backupDriver.listBackupFiles().map { file ->
            BackupInfo(
                id = file.name.removeSuffix(".zyntabak"),
                fileName = file.name,
                filePath = file.path,
                sizeBytes = file.length,
                createdAt = file.createdAt,
                status = BackupStatus.SUCCESS,
                isEncrypted = true,
                dbVersion = 7
            )
        })
    }

    override suspend fun createBackup(): Result<BackupInfo> = runCatching {
        // 1. Copy SQLite DB file to temp location
        val rawDbPath = backupDriver.getDatabasePath()
        val tempPath = backupDriver.getTempPath("backup_raw.db")
        backupDriver.copyFile(rawDbPath, tempPath)

        // 2. Encrypt with CryptoManager
        val encryptedPath = backupDriver.getTempPath("backup_${System.currentTimeMillis()}.zyntabak")
        val rawBytes = backupDriver.readBytes(tempPath)
        val encrypted = cryptoManager.encrypt(rawBytes).getOrThrow()
        backupDriver.writeBytes(encryptedPath, encrypted)

        // 3. Move to backup directory
        val finalPath = backupDriver.moveToBackupDir(encryptedPath)
        val finalFile = backupDriver.getFileInfo(finalPath)

        BackupInfo(
            id = generateUuid(),
            fileName = finalFile.name,
            filePath = finalPath,
            sizeBytes = finalFile.length,
            createdAt = Clock.System.now().toString(),
            status = BackupStatus.SUCCESS,
            isEncrypted = true,
            dbVersion = 7
        )
    }

    override suspend fun restoreBackup(backupId: String): Result<Unit> = runCatching {
        val backups = backupDriver.listBackupFiles()
        val backup = backups.find { it.name.contains(backupId) }
            ?: throw IllegalArgumentException("Backup not found: $backupId")

        // 1. Read and decrypt
        val encrypted = backupDriver.readBytes(backup.path)
        val decrypted = cryptoManager.decrypt(encrypted).getOrThrow()

        // 2. Write to DB restore path
        val restorePath = backupDriver.getTempPath("restore.db")
        backupDriver.writeBytes(restorePath, decrypted)

        // 3. Close DB, copy, reopen — handled by platform
        backupDriver.restoreDatabase(restorePath)
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = runCatching {
        backupDriver.deleteBackup(backupId)
    }
}
```

**New HAL Interface — `BackupDriver.kt`:**
```kotlin
// shared/hal/src/commonMain/...
interface BackupDriver {
    suspend fun getDatabasePath(): String
    suspend fun listBackupFiles(): List<BackupFileInfo>
    suspend fun copyFile(from: String, to: String)
    suspend fun readBytes(path: String): ByteArray
    suspend fun writeBytes(path: String, bytes: ByteArray)
    suspend fun getTempPath(fileName: String): String
    suspend fun moveToBackupDir(path: String): String
    suspend fun getFileInfo(path: String): BackupFileInfo
    suspend fun restoreDatabase(restorePath: String)
    suspend fun deleteBackup(backupId: String)
}

data class BackupFileInfo(val name: String, val path: String, val length: Long, val createdAt: String)
```

### `AccountingRepositoryImpl.kt`
```kotlin
class AccountingRepositoryImpl(
    private val db: ZyntaPosDatabase
) : AccountingRepository {

    override fun getByStoreAndPeriod(storeId: String, fiscalPeriod: String): Flow<List<AccountingEntry>> =
        db.accountingEntriesQueries.selectByStoreAndPeriod(storeId, fiscalPeriod)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(AccountingMapper::toDomain) }

    override suspend fun getByReference(
        referenceType: AccountingReferenceType, referenceId: String
    ): List<AccountingEntry> =
        db.accountingEntriesQueries.selectByReference(referenceType.name, referenceId)
            .executeAsList().map(AccountingMapper::toDomain)

    override suspend fun insert(entry: AccountingEntry): Result<AccountingEntry> = runCatching {
        db.accountingEntriesQueries.insert(AccountingMapper.toDb(entry))
        entry
    }

    override suspend fun getAccountSummary(
        storeId: String, fromPeriod: String, toPeriod: String
    ): List<AccountSummary> =
        db.accountingEntriesQueries.sumByAccountForPeriod(storeId, fromPeriod, toPeriod)
            .executeAsList()
            .groupBy { it.account_code }
            .map { (code, rows) ->
                val debit = rows.filter { it.entry_type == "DEBIT" }.sumOf { it.total }
                val credit = rows.filter { it.entry_type == "CREDIT" }.sumOf { it.total }
                AccountSummary(
                    accountCode = code,
                    accountName = rows.first().account_name,
                    debitTotal = debit,
                    creditTotal = credit,
                    balance = debit - credit
                )
            }
}
```

### `WarehouseRackRepositoryImpl.kt`
```kotlin
class WarehouseRackRepositoryImpl(
    private val db: ZyntaPosDatabase
) : WarehouseRackRepository {
    override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>> =
        db.warehouseRacksQueries.selectByWarehouse(warehouseId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(WarehouseRackMapper::toDomain) }

    override suspend fun getById(id: String): WarehouseRack? =
        db.warehouseRacksQueries.selectById(id).executeAsOneOrNull()?.let(WarehouseRackMapper::toDomain)

    override suspend fun save(rack: WarehouseRack): Result<WarehouseRack> = runCatching {
        db.warehouseRacksQueries.upsert(WarehouseRackMapper.toDb(rack))
        rack
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        db.warehouseRacksQueries.softDelete(id, now, now)
    }
}
```

### `EInvoiceRepositoryImpl.kt` (stub — full impl in Sprint 19)
```kotlin
class EInvoiceRepositoryImpl(
    private val db: ZyntaPosDatabase
) : EInvoiceRepository {
    // Full IRD API client integration in Sprint 19
    // This sprint: DB CRUD only
    override fun getByStore(storeId: String): Flow<List<EInvoice>> = flow { emit(emptyList()) }
    override suspend fun getById(id: String): EInvoice? = null
    override suspend fun getByOrderId(orderId: String): EInvoice? = null
    override suspend fun save(invoice: EInvoice): Result<EInvoice> = Result.success(invoice)
    override suspend fun updateStatus(id: String, status: EInvoiceStatus, responseCode: String?, message: String?): Result<Unit> = Result.success(Unit)
}
```

---

## Navigation Extensions

### `ZyntaRoute.kt` additions

**File:** `composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/ZyntaRoute.kt`

```kotlin
// Phase 3 routes — Staff
@Serializable data object StaffList : ZyntaRoute()
@Serializable data class StaffDetail(val employeeId: String? = null) : ZyntaRoute()
@Serializable data class StaffAttendance(val employeeId: String) : ZyntaRoute()
@Serializable data class StaffShift(val employeeId: String? = null) : ZyntaRoute()
@Serializable data class StaffPayroll(val employeeId: String) : ZyntaRoute()
@Serializable data object StaffSchedule : ZyntaRoute()

// Phase 3 routes — Admin
@Serializable data object AdminDashboard : ZyntaRoute()
@Serializable data object AuditLogViewer : ZyntaRoute()
@Serializable data object DatabaseManagement : ZyntaRoute()
@Serializable data object BackupManagement : ZyntaRoute()
@Serializable data object ModuleManagement : ZyntaRoute()

// Phase 3 routes — Media
@Serializable data object MediaLibrary : ZyntaRoute()
@Serializable data class MediaPicker(val entityType: String, val entityId: String) : ZyntaRoute()
@Serializable data class MediaDetail(val mediaId: String) : ZyntaRoute()

// Phase 3 routes — E-Invoice
@Serializable data object EInvoiceSettings : ZyntaRoute()
@Serializable data object EInvoiceList : ZyntaRoute()
@Serializable data class EInvoiceDetail(val invoiceId: String) : ZyntaRoute()
@Serializable data object ComplianceReport : ZyntaRoute()

// Phase 3 routes — Warehouse Racks
@Serializable data class WarehouseRackList(val warehouseId: String) : ZyntaRoute()
@Serializable data class WarehouseRackDetail(val rackId: String? = null, val warehouseId: String) : ZyntaRoute()
@Serializable data class PickList(val warehouseId: String) : ZyntaRoute()

// Phase 3 routes — Advanced Analytics
@Serializable data object AdvancedReports : ZyntaRoute()
@Serializable data object SalesTrendReport : ZyntaRoute()
@Serializable data object HourlyHeatmapReport : ZyntaRoute()
@Serializable data object ProductPerformanceReport : ZyntaRoute()

// Phase 3 routes — Settings extensions
@Serializable data object RoleList : ZyntaRoute()
@Serializable data class RoleEditor(val roleId: String? = null) : ZyntaRoute()
@Serializable data object SecurityPolicySettings : ZyntaRoute()
@Serializable data object DataRetentionSettings : ZyntaRoute()
@Serializable data object AuditPolicySettings : ZyntaRoute()
```

### New Permissions

**File:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/Permission.kt`

Add to existing enum/sealed class:
```kotlin
// Phase 3 — Staff
MANAGE_STAFF,
VIEW_STAFF,
MANAGE_ATTENDANCE,
APPROVE_LEAVE,
MANAGE_PAYROLL,
VIEW_PAYROLL,

// Phase 3 — Admin
VIEW_ADMIN_PANEL,
MANAGE_BACKUPS,
VIEW_AUDIT_LOGS,
MANAGE_DATABASE,
MANAGE_MODULES,

// Phase 3 — Media
MANAGE_MEDIA,
VIEW_MEDIA,

// Phase 3 — E-Invoice
MANAGE_EINVOICE,
VIEW_EINVOICE,

// Phase 3 — Accounting
VIEW_ACCOUNTING,
MANAGE_ACCOUNTING,

// Phase 3 — RBAC
MANAGE_ROLES,
```

### RbacEngine Updates

**File:** `shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/RbacEngine.kt`

Update role-permission maps:
```kotlin
// ADMIN gets all Phase 3 permissions
Role.ADMIN -> existingAdminPerms + setOf(
    Permission.MANAGE_STAFF, Permission.VIEW_STAFF, Permission.MANAGE_ATTENDANCE,
    Permission.APPROVE_LEAVE, Permission.MANAGE_PAYROLL, Permission.VIEW_PAYROLL,
    Permission.VIEW_ADMIN_PANEL, Permission.MANAGE_BACKUPS, Permission.VIEW_AUDIT_LOGS,
    Permission.MANAGE_DATABASE, Permission.MANAGE_MODULES,
    Permission.MANAGE_MEDIA, Permission.VIEW_MEDIA,
    Permission.MANAGE_EINVOICE, Permission.VIEW_EINVOICE,
    Permission.VIEW_ACCOUNTING, Permission.MANAGE_ACCOUNTING,
    Permission.MANAGE_ROLES
)

// MANAGER gets staff view, approve leave, view accounting
Role.MANAGER -> existingManagerPerms + setOf(
    Permission.VIEW_STAFF, Permission.MANAGE_ATTENDANCE, Permission.APPROVE_LEAVE,
    Permission.VIEW_PAYROLL, Permission.VIEW_ACCOUNTING, Permission.MANAGE_MEDIA,
    Permission.VIEW_EINVOICE
)

// REPORTER gets view-only accounting and staff reports
Role.REPORTER -> existingReporterPerms + setOf(
    Permission.VIEW_STAFF, Permission.VIEW_PAYROLL,
    Permission.VIEW_ACCOUNTING, Permission.VIEW_AUDIT_LOGS
)
```

### Navigation Items Update

**File:** `composeApp/navigation/src/commonMain/.../NavigationItems.kt`

```kotlin
// Phase 3 nav items
NavItem(
    route = StaffList,
    label = "Staff",
    icon = Icons.Default.People,
    requiredPermission = Permission.VIEW_STAFF
),
NavItem(
    route = AdminDashboard,
    label = "Admin",
    icon = Icons.Default.AdminPanelSettings,
    requiredPermission = Permission.VIEW_ADMIN_PANEL
),
NavItem(
    route = MediaLibrary,
    label = "Media",
    icon = Icons.Default.PhotoLibrary,
    requiredPermission = Permission.VIEW_MEDIA
),
```

### ZyntaApplication Update

**File:** `androidApp/src/main/kotlin/.../ZyntaApplication.kt` (and Desktop equivalent)

Add to Koin initialization Tier 7 (feature modules list):
```kotlin
staffModule,
adminModule,
mediaModule,
```

---

## DataModule Additions

**File:** `shared/data/src/commonMain/kotlin/.../data/di/DataModule.kt`

```kotlin
// Phase 3 — Admin / Backup
single<SystemRepository>        { SystemRepositoryImpl(get(), get(), get(), BuildConfig.VERSION_NAME) }
single<BackupRepository>        { BackupRepositoryImpl(get(), get()) }
single<AccountingRepository>    { AccountingRepositoryImpl(get()) }
single<WarehouseRackRepository> { WarehouseRackRepositoryImpl(get()) }
single<EInvoiceRepository>      { EInvoiceRepositoryImpl(get()) }
```

**`BackupDriver` bindings** go in the platform DI modules:
- `androidDataModule`: `single<BackupDriver> { AndroidBackupDriver(androidContext()) }`
- `desktopDataModule`: `single<BackupDriver> { DesktopBackupDriver() }`

---

## Tasks

- [ ] **7.1** Implement `SystemRepositoryImpl` (DB size, sync queue counts, vacuum, purge)
- [ ] **7.2** Define `BackupDriver` interface in `:shared:hal`
- [ ] **7.3** Implement `BackupRepositoryImpl` (encrypt/decrypt via CryptoManager)
- [ ] **7.4** Implement `AccountingRepositoryImpl` (CRUD + account summary aggregation)
- [ ] **7.5** Implement `WarehouseRackRepositoryImpl` (CRUD + soft delete)
- [ ] **7.6** Create `EInvoiceRepositoryImpl` stub (DB CRUD only — API wired in Sprint 19)
- [ ] **7.7** Create mapper files: `AccountingMapper.kt`, `WarehouseRackMapper.kt`, `EInvoiceMapper.kt`
- [ ] **7.8** Add all 5 Phase 3 routes groups to `ZyntaRoute.kt`
- [ ] **7.9** Add new Permission entries to `Permission.kt`
- [ ] **7.10** Update `RbacEngine.kt` with Phase 3 permissions for ADMIN, MANAGER, REPORTER
- [ ] **7.11** Add Phase 3 nav items to `NavigationItems.kt`
- [ ] **7.12** Wire Phase 3 sub-graph stubs in `MainNavGraph.kt` (composable stubs — screens built Sprints 8–23)
- [ ] **7.13** Add screen stubs to `MainNavScreens.kt`
- [ ] **7.14** Update `DataModule.kt` with all Phase 3 Koin bindings
- [ ] **7.15** Update `ZyntaApplication` to load `staffModule`, `adminModule`, `mediaModule`
- [ ] **7.16** Run `./gradlew assemble` — full project compiles

---

## Verification

```bash
# Full project compilation
./gradlew assemble

# Navigation module compiles
./gradlew :composeApp:navigation:assemble

# Security module (RbacEngine) compiles
./gradlew :shared:security:assemble

# Static analysis
./gradlew detekt
```

---

## Definition of Done

- [ ] All 5 repository implementations created + DataModule bindings registered
- [ ] `BackupDriver` HAL interface created
- [ ] All Phase 3 navigation routes added
- [ ] Phase 3 permissions added + RbacEngine updated
- [ ] Phase 3 nav items added
- [ ] `./gradlew assemble` passes (full project compiles with stub screens)
- [ ] Commit: `feat(data): implement Admin, Accounting, Racks, EInvoice stub repositories`
  And: `feat(navigation): add Phase 3 routes, permissions, and nav items`
