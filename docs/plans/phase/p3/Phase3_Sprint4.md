# ZyntaPOS — Phase 3 Sprint 4: Media + Admin + E-Invoice Domain Models & Use Case Interfaces

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT4-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 4 of 24 | Week 4
> **Module(s):** `:shared:domain`, `:shared:security`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ZYNTA-ER-DIAGRAM-v1.0

---

## Goal

Create all domain models, enum types, repository interfaces, and use case interfaces for: Media Manager (M20), System Administration (M19), E-Invoicing (Sri Lanka IRD), Warehouse Racks, and Accounting entries. Also add `EInvoiceCertificateConfig` to `:shared:security`.

---

## New Domain Model Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`

### Media Manager Models

#### `MediaFile.kt`
```kotlin
data class MediaFile(
    val id: String,
    val fileName: String,
    val filePath: String,           // Local device path
    val remoteUrl: String?,         // Backend URL after upload
    val fileType: MediaFileType,
    val mimeType: String,
    val fileSize: Long,             // Bytes
    val thumbnailPath: String?,
    val entityType: String?,        // 'Product', 'Category', 'Store', 'Employee'
    val entityId: String?,
    val isPrimary: Boolean,
    val uploadedBy: String,
    val uploadStatus: MediaUploadStatus,
    val createdAt: String,
    val updatedAt: String
) {
    val fileSizeKb: Long get() = fileSize / 1024
    val fileSizeMb: Double get() = fileSize / (1024.0 * 1024.0)
    val isImage: Boolean get() = fileType == MediaFileType.IMAGE
    val displayUrl: String get() = remoteUrl ?: filePath
}
```

### Warehouse & Admin Models

#### `WarehouseRack.kt`
```kotlin
data class WarehouseRack(
    val id: String,
    val warehouseId: String,
    val name: String,               // 'A1', 'B3-Shelf-2'
    val description: String?,
    val capacity: Int?,             // Null = unlimited
    val createdAt: String,
    val updatedAt: String
)
```

#### `ProductLocation.kt`
```kotlin
data class ProductLocation(
    val productId: String,
    val productName: String,
    val warehouseId: String,
    val warehouseName: String,
    val rackId: String?,
    val rackName: String?,
    val quantity: Double,
    val lastUpdated: String
)
```

#### `PickListItem.kt`
```kotlin
data class PickListItem(
    val productId: String,
    val productName: String,
    val sku: String?,
    val requestedQuantity: Double,
    val availableQuantity: Double,
    val rackName: String?,
    val warehouseId: String,
    val warehouseName: String
) {
    val canFulfill: Boolean get() = availableQuantity >= requestedQuantity
}
```

#### `AccountingEntry.kt`
```kotlin
data class AccountingEntry(
    val id: String,
    val storeId: String,
    val accountCode: String,
    val accountName: String,
    val entryType: AccountingEntryType,
    val amount: Double,
    val referenceType: AccountingReferenceType,
    val referenceId: String,
    val description: String?,
    val entryDate: String,          // ISO date: YYYY-MM-DD
    val fiscalPeriod: String,       // 'YYYY-MM'
    val createdBy: String,
    val createdAt: String
)
```

### System Administration Models

#### `SystemHealth.kt`
```kotlin
data class SystemHealth(
    val dbSizeMb: Double,
    val walSizeMb: Double,
    val pendingSyncCount: Int,
    val failedSyncCount: Int,
    val lastSyncAt: String?,
    val syncStatus: String,        // 'IDLE', 'SYNCING', 'ERROR'
    val uptime: Long,              // Milliseconds since app start
    val memoryUsedMb: Long,
    val memoryTotalMb: Long,
    val appVersion: String,
    val dbVersion: Int
) {
    val memoryUsagePercent: Float
        get() = if (memoryTotalMb > 0) (memoryUsedMb.toFloat() / memoryTotalMb * 100) else 0f
    val isSyncHealthy: Boolean
        get() = syncStatus != "ERROR" && failedSyncCount < 5
}
```

#### `BackupInfo.kt`
```kotlin
data class BackupInfo(
    val id: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val createdAt: String,
    val status: BackupStatus,
    val isEncrypted: Boolean,
    val dbVersion: Int          // DB schema version at time of backup
) {
    val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
}
```

#### `DatabaseStats.kt`
```kotlin
data class DatabaseStats(
    val tableCount: Int,
    val totalRows: Long,
    val indexCount: Int,
    val dbVersion: Int,
    val lastVacuumAt: String?,
    val walSizeMb: Double,
    val dbSizeMb: Double,
    val tables: List<TableStats>
)

data class TableStats(
    val tableName: String,
    val rowCount: Long,
    val estimatedSizeMb: Double
)
```

### E-Invoice Models

#### `EInvoice.kt`
```kotlin
data class EInvoice(
    val id: String,
    val orderId: String,
    val invoiceNumber: String,          // 'IRD-{storeCode}-{YYYYMMDD}-{seq}'
    val storeId: String,
    val buyerName: String,
    val buyerVatNumber: String?,
    val lineItems: List<EInvoiceLineItem>,
    val subtotal: Double,
    val taxTotal: Double,
    val total: Double,
    val currency: String,
    val status: EInvoiceStatus,
    val submittedAt: String?,
    val irdResponseCode: String?,
    val irdResponseMessage: String?,
    val signatureHash: String?,
    val createdAt: String,
    val updatedAt: String
)
```

#### `EInvoiceLineItem.kt`
```kotlin
data class EInvoiceLineItem(
    val lineNumber: Int,
    val productCode: String,           // SKU or barcode
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val taxRate: Double,               // e.g. 15.0 for 15% VAT
    val taxAmount: Double,
    val lineTotal: Double              // quantity × unitPrice + taxAmount
)
```

#### `IrdSubmissionResult.kt`
```kotlin
data class IrdSubmissionResult(
    val success: Boolean,
    val invoiceId: String?,
    val responseCode: String,
    val message: String,
    val timestamp: String
)
```

---

## New Enum Type Files

```kotlin
// MediaFileType.kt
enum class MediaFileType { IMAGE, DOCUMENT }

// MediaUploadStatus.kt
enum class MediaUploadStatus { LOCAL, UPLOADING, UPLOADED, FAILED }

// AccountingEntryType.kt
enum class AccountingEntryType { DEBIT, CREDIT }

// AccountingReferenceType.kt
enum class AccountingReferenceType {
    ORDER, EXPENSE, PAYMENT, CASH_MOVEMENT, ADJUSTMENT, PAYROLL
}

// BackupStatus.kt
enum class BackupStatus { CREATING, SUCCESS, FAILED, RESTORING }

// EInvoiceStatus.kt
enum class EInvoiceStatus { DRAFT, SUBMITTED, ACCEPTED, REJECTED, CANCELLED }
```

---

## New Repository Interface Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/repository/`

```kotlin
// MediaRepository.kt
interface MediaRepository {
    fun getForEntity(entityType: String, entityId: String): Flow<List<MediaFile>>
    fun getPrimaryForEntity(entityType: String, entityId: String): Flow<MediaFile?>
    fun getAll(): Flow<List<MediaFile>>
    suspend fun save(mediaFile: MediaFile): Result<MediaFile>
    suspend fun delete(id: String): Result<Unit>
    suspend fun updateUploadStatus(id: String, status: MediaUploadStatus, remoteUrl: String?): Result<Unit>
}

// WarehouseRackRepository.kt
interface WarehouseRackRepository {
    fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>>
    suspend fun getById(id: String): WarehouseRack?
    suspend fun save(rack: WarehouseRack): Result<WarehouseRack>
    suspend fun delete(id: String): Result<Unit>
}

// AccountingRepository.kt
interface AccountingRepository {
    fun getByStoreAndPeriod(storeId: String, fiscalPeriod: String): Flow<List<AccountingEntry>>
    suspend fun getByReference(referenceType: AccountingReferenceType, referenceId: String): List<AccountingEntry>
    suspend fun insert(entry: AccountingEntry): Result<AccountingEntry>
    suspend fun getAccountSummary(storeId: String, fromPeriod: String, toPeriod: String): List<AccountSummary>
}

// SystemRepository.kt
interface SystemRepository {
    suspend fun getSystemHealth(): SystemHealth
    suspend fun getDatabaseStats(): DatabaseStats
    suspend fun vacuumDatabase(): Result<Unit>
    suspend fun purgeExpiredData(): Result<PurgeResult>
}

// BackupRepository.kt
interface BackupRepository {
    fun listBackups(): Flow<List<BackupInfo>>
    suspend fun createBackup(): Result<BackupInfo>
    suspend fun restoreBackup(backupId: String): Result<Unit>
    suspend fun deleteBackup(backupId: String): Result<Unit>
}

// EInvoiceRepository.kt
interface EInvoiceRepository {
    fun getByStore(storeId: String): Flow<List<EInvoice>>
    suspend fun getById(id: String): EInvoice?
    suspend fun getByOrderId(orderId: String): EInvoice?
    suspend fun save(invoice: EInvoice): Result<EInvoice>
    suspend fun updateStatus(id: String, status: EInvoiceStatus, responseCode: String?, message: String?): Result<Unit>
}
```

### Supporting Value Objects

```kotlin
data class AccountSummary(
    val accountCode: String,
    val accountName: String,
    val debitTotal: Double,
    val creditTotal: Double,
    val balance: Double          // DEBIT - CREDIT (positive = debit balance)
)

data class PurgeResult(
    val deletedRows: Int,
    val freedMb: Double,
    val categories: Map<String, Int>   // e.g. "sync_queue" -> 1250
)
```

---

## New Use Case Interface Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/`

### Media (`usecase/media/`)
```kotlin
fun interface UploadMediaUseCase {
    suspend operator fun invoke(localPath: String, entityType: String, entityId: String): Result<MediaFile>
}

fun interface DeleteMediaUseCase {
    suspend operator fun invoke(mediaId: String): Result<Unit>
}

fun interface GetMediaForEntityUseCase {
    operator fun invoke(entityType: String, entityId: String): Flow<List<MediaFile>>
}
```

### Warehouse Racks (`usecase/warehouse/`)
```kotlin
fun interface GetWarehouseRacksUseCase {
    operator fun invoke(warehouseId: String): Flow<List<WarehouseRack>>
}

fun interface SaveWarehouseRackUseCase {
    suspend operator fun invoke(rack: WarehouseRack): Result<WarehouseRack>
}

fun interface DeleteWarehouseRackUseCase {
    suspend operator fun invoke(id: String): Result<Unit>
}

fun interface GetProductLocationUseCase {
    suspend operator fun invoke(productId: String, warehouseId: String): ProductLocation?
}

fun interface GeneratePickListUseCase {
    suspend operator fun invoke(
        requests: List<Pair<String, Double>>,  // productId to quantity
        warehouseId: String
    ): List<PickListItem>
}
```

### Accounting (`usecase/accounting/`)
```kotlin
fun interface CreateAccountingEntryUseCase {
    suspend operator fun invoke(entry: AccountingEntry): Result<AccountingEntry>
}

fun interface GetAccountingEntriesUseCase {
    operator fun invoke(storeId: String, fiscalPeriod: String): Flow<List<AccountingEntry>>
}

fun interface GenerateAccountingReportUseCase {
    suspend operator fun invoke(storeId: String, fromPeriod: String, toPeriod: String): List<AccountSummary>
}
```

### System / Admin (`usecase/admin/`)
```kotlin
fun interface GetSystemHealthUseCase {
    suspend operator fun invoke(): SystemHealth
}

fun interface GetDatabaseStatsUseCase {
    suspend operator fun invoke(): DatabaseStats
}

fun interface VacuumDatabaseUseCase {
    suspend operator fun invoke(): Result<Unit>
}

fun interface PurgeExpiredDataUseCase {
    suspend operator fun invoke(): Result<PurgeResult>
}
```

### Backup (`usecase/admin/`)
```kotlin
fun interface CreateBackupUseCase {
    suspend operator fun invoke(): Result<BackupInfo>
}

fun interface RestoreBackupUseCase {
    suspend operator fun invoke(backupId: String): Result<Unit>
}

fun interface ListBackupsUseCase {
    operator fun invoke(): Flow<List<BackupInfo>>
}

fun interface DeleteBackupUseCase {
    suspend operator fun invoke(backupId: String): Result<Unit>
}
```

### E-Invoice (`usecase/einvoice/`)
```kotlin
fun interface GenerateEInvoiceUseCase {
    suspend operator fun invoke(orderId: String): Result<EInvoice>
}

fun interface SubmitEInvoiceUseCase {
    suspend operator fun invoke(invoiceId: String): Result<IrdSubmissionResult>
}

fun interface GetEInvoiceStatusUseCase {
    suspend operator fun invoke(invoiceId: String): Result<EInvoiceStatus>
}

fun interface CancelEInvoiceUseCase {
    suspend operator fun invoke(invoiceId: String, reason: String): Result<Unit>
}

fun interface GetComplianceReportUseCase {
    suspend operator fun invoke(storeId: String, fromDate: String, toDate: String): ComplianceReport
}
```

#### `ComplianceReport.kt`
```kotlin
data class ComplianceReport(
    val storeId: String,
    val fromDate: String,
    val toDate: String,
    val totalInvoices: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val pendingCount: Int,
    val totalTaxCollected: Double,
    val taxBreakdown: List<TaxBreakdownItem>
)

data class TaxBreakdownItem(
    val taxGroupName: String,
    val taxRate: Double,
    val taxableAmount: Double,
    val taxAmount: Double
)
```

---

## Security Module Addition

**Location:** `shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/`

#### `EInvoiceCertificateConfig.kt`
```kotlin
package com.zyntasolutions.zyntapos.security

/**
 * Configuration for the IRD e-invoice certificate (.p12 format).
 * Values are injected from BuildConfig via the Gradle Secrets Plugin.
 *
 * @property certificatePath Absolute path to the .p12 certificate file.
 *   From BuildConfig.ZYNTA_IRD_CLIENT_CERTIFICATE_PATH (local.properties).
 * @property certificatePassword Password for the .p12 keystore.
 *   From BuildConfig.ZYNTA_IRD_CERTIFICATE_PASSWORD (local.properties).
 * @property apiEndpoint IRD API base URL.
 *   From BuildConfig.ZYNTA_IRD_API_ENDPOINT (local.properties).
 */
data class EInvoiceCertificateConfig(
    val certificatePath: String,
    val certificatePassword: String,
    val apiEndpoint: String
)
```

This config is provided by the platform DI module (`androidDataModule` / `desktopDataModule`) reading from `BuildConfig`.

---

## Tasks

- [ ] **4.1** Create all Media domain model files: `MediaFile.kt`, `WarehouseRack.kt`, `ProductLocation.kt`, `PickListItem.kt`
- [ ] **4.2** Create all Admin domain model files: `AccountingEntry.kt`, `SystemHealth.kt`, `BackupInfo.kt`, `DatabaseStats.kt`, `TableStats.kt`
- [ ] **4.3** Create all E-Invoice domain model files: `EInvoice.kt`, `EInvoiceLineItem.kt`, `IrdSubmissionResult.kt`
- [ ] **4.4** Create supporting value objects: `AccountSummary.kt`, `PurgeResult.kt`, `ComplianceReport.kt`, `TaxBreakdownItem.kt`
- [ ] **4.5** Create all 6 enum type files
- [ ] **4.6** Create all 6 repository interfaces
- [ ] **4.7** Write all use case interfaces: 3 Media + 5 Racks + 3 Accounting + 4 Admin + 4 Backup + 5 E-Invoice = **24 use case interfaces**
- [ ] **4.8** Create `EInvoiceCertificateConfig.kt` in `:shared:security`
- [ ] **4.9** Verify: `./gradlew :shared:domain:assemble && ./gradlew :shared:security:assemble`
- [ ] **4.10** Run `./gradlew :shared:domain:test`

---

## Verification

```bash
./gradlew :shared:domain:assemble
./gradlew :shared:security:assemble
./gradlew :shared:domain:test
./gradlew :shared:domain:detekt
```

---

## Definition of Done

- [ ] All domain model files created (ADR-002 compliant)
- [ ] All 6 enum types created
- [ ] All 6 repository interfaces created
- [ ] All 24 use case interfaces created (SAM-compatible `fun interface`, no defaults)
- [ ] `EInvoiceCertificateConfig` added to `:shared:security`
- [ ] Both modules assemble without errors
- [ ] Commit: `feat(domain): add Media, Admin, E-Invoice domain models and use case contracts`
