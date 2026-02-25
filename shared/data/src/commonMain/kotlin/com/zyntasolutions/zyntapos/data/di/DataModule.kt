package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseMigrations
import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.api.KtorApiService
import com.zyntasolutions.zyntapos.data.remote.api.buildApiClient
import com.zyntasolutions.zyntapos.data.repository.AuditRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.AuthRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CategoryRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CouponRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerGroupRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerWalletRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ExpenseRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.InstallmentRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.LoyaltyRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.NotificationRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.OrderRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ProductRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.RegisterRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SettingsRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.StockRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SupplierRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SyncRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.TaxGroupRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.UnitGroupRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.UserRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.AccountingRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.AttendanceRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.BackupRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.EInvoiceRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.EmployeeRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.LeaveRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.MediaRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.PayrollRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ShiftRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SystemRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.WarehouseRackRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.WarehouseRepositoryImpl
import com.zyntasolutions.zyntapos.data.sync.ConflictResolver
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import com.zyntasolutions.zyntapos.data.sync.SyncEngine
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import org.koin.core.qualifier.named
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.repository.InstallmentRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import com.zyntasolutions.zyntapos.domain.repository.BackupRepository
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import com.zyntasolutions.zyntapos.domain.repository.MediaRepository
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import org.koin.dsl.module

/**
 * ZyntaPOS — :shared:data Koin DI Module (commonMain)
 *
 * Provides platform-agnostic bindings for the entire data layer.
 * Platform-specific bindings ([DatabaseDriverFactory], [DatabaseKeyProvider],
 * [PasswordHasher], [SecurePreferences], [NetworkMonitor]) must be registered
 * in the platform Koin modules (`androidDataModule` / `desktopDataModule`) and
 * merged at the application entry point.
 *
 * ## Full Module Graph (Sprint 6 — Steps 3.2 + 3.3 + 3.4)
 *
 * ```
 * DatabaseKeyProvider  ─┐
 *                        ├─► DatabaseFactory ─► ZyntaDatabase ─► all Repositories
 * DatabaseDriverFactory ─┘
 * DatabaseMigrations   ─┘
 *
 * PasswordHasher   ──► PasswordHasherAdapter ──► AuthRepositoryImpl, UserRepositoryImpl
 * SecurePreferences──► AuthRepositoryImpl, ApiClient, SyncEngine
 *
 * SyncEnqueuer  ─────► OrderRepositoryImpl, StockRepositoryImpl, (write-path repos)
 *
 * ApiClient (Ktor HttpClient) ──► ApiService (KtorApiService)
 *
 * NetworkMonitor ─────────────┐
 * ApiService ─────────────────┼─► SyncEngine
 * ZyntaDatabase ──────────────┘
 * SecurePreferences ──────────┘
 * ```
 *
 * ## Platform modules must additionally provide:
 * - `DatabaseDriverFactory`  (expect/actual — platform constructor args differ)
 * - `DatabaseKeyProvider`    (expect/actual — platform constructor args differ)
 * - `NetworkMonitor`         (expect/actual — Android needs Context; Desktop no-arg)
 * - `SecurePreferences`      (expect/actual — bound by securityModule; MERGED-D3: adapter classes removed 2026-02-21)
 * Note: `PasswordHashPort` is bound in `securityModule` (`:shared:security`) as
 *       `single<PasswordHashPort> { PasswordHasherAdapter() }`, NOT in this module.
 *       MERGED-F3 (2026-02-22): removed direct :shared:security dependency from :shared:data.
 *
 * ## Android WorkManager integration (SyncWorker)
 * `SyncEngine.runOnce()` is invoked from `SyncWorker` (androidMain) — no extra binding needed.
 * `SyncEngine.startPeriodicSync(scope)` is called from the Desktop application entry point.
 */
val dataModule = module {

    // ── Schema Migration Manager ─────────────────────────────────────
    single { DatabaseMigrations() }

    // ── Secure-Preferences Key Migration (MERGED-F1) ──────────────────
    // Must be resolved and .migrate() called ONCE at application startup,
    // before any auth operation. Call sites: ZyntaApplication.onCreate()
    // and the Desktop main() function — both invoke getKoin().get<SecurePreferencesKeyMigration>().migrate()
    // immediately after startKoin{} returns.
    // migrate() is idempotent: safe to call on every launch.
    single { SecurePreferencesKeyMigration(prefs = get()) }

    // ── Database Factory (orchestrates key + driver + migrations) ─────
    // DatabaseDriverFactory & DatabaseKeyProvider are expect/actual.
    // Their concrete instances are bound in platform-specific modules.
    single {
        DatabaseFactory(
            keyProvider   = get(),
            driverFactory = get(),
            migrations    = get(),
        )
    }

    // ── ZyntaDatabase singleton ───────────────────────────────────────
    // Lazy-opened on first use; backed by AES-256 SQLCipher + WAL mode.
    single { get<DatabaseFactory>().openDatabase() }

    // ── Sync Enqueuer (shared write-path utility) ─────────────────────
    // Lightweight helper that writes a pending_operations row after every
    // local mutation. Injected into all write-path repository impls.
    single { SyncEnqueuer(db = get()) }

    // ─────────────────────────────────────────────────────────────────
    // ── Repositories (Step 3.3)  ──────────────────────────────────────
    // Each implementation is bound to its domain interface so callers
    // (use-cases, ViewModels) depend only on the interface contract.
    // ─────────────────────────────────────────────────────────────────

    // Product catalog + FTS5 search
    single<ProductRepository> { ProductRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Category tree (recursive CTE)
    single<CategoryRepository> { CategoryRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Order lifecycle — transactional order + items creation
    single<OrderRepository> { OrderRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Customer CRUD + FTS5 search
    single<CustomerRepository> { CustomerRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Cash register session lifecycle + cash movement recording
    single<RegisterRepository> { RegisterRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Stock adjustments + low-stock alert upserts
    single<StockRepository> { StockRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Supplier CRUD
    single<SupplierRepository> { SupplierRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Auth: local BCrypt verification + SecurePreferences JWT cache.
    // PasswordHashPort is provided by securityModule (PasswordHasherAdapter) — MERGED-F3.
    single<AuthRepository> {
        AuthRepositoryImpl(
            db             = get(),
            securePrefs    = get<SecureStoragePort>(),
            passwordHasher = get(),
        )
    }

    // Settings: typed key-value wrappers over the `settings` SQLite table
    single<SettingsRepository> { SettingsRepositoryImpl(db = get()) }

    // Tax groups: CRUD + soft-delete (SQLDelight queries tracked in MERGED-D2)
    single<TaxGroupRepository> { TaxGroupRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Unit-of-Measure CRUD + base-unit promotion (SQLDelight queries tracked in MERGED-D2)
    single<UnitGroupRepository> { UnitGroupRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Security audit log: append-only; no remote sync in Phase 1
    single<AuditRepository> { AuditRepositoryImpl(db = get()) }

    // User accounts: CRUD + password lifecycle.
    // PasswordHashPort injected via securityModule binding — MERGED-F3.
    single<UserRepository> {
        UserRepositoryImpl(
            db             = get(),
            syncEnqueuer   = get(),
            passwordHasher = get(),
        )
    }

    // Sync queue: batch read + status transitions (PENDING→SYNCED/FAILED).
    // apiService is injected so pushToServer/pullFromServer make real Ktor HTTP calls.
    single<SyncRepository> { SyncRepositoryImpl(db = get(), apiService = get()) }

    // SyncRepositoryImpl is also bound directly (not just via interface) so that
    // the SyncEngine can call maintenance methods (pruneSynced, deduplicatePending,
    // markFailed) that are not part of the domain contract.
    single { get<SyncRepository>() as SyncRepositoryImpl }

    // CRDT ConflictResolver — LWW with deviceId tiebreaker + PRODUCT field-level merge.
    // localDeviceId resolves from the named "deviceId" String binding registered by the
    // platform data modules (androidDataModule / desktopDataModule) at startup — the
    // same value used by SecurityAuditLogger.
    single {
        ConflictResolver(
            localDeviceId = get(named("deviceId")),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 2 CRM Repositories ─────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Customer groups: pricing tiers, group discounts, price type per group
    single<CustomerGroupRepository> { CustomerGroupRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Customer wallets: pre-paid balance with atomic credit/debit operations
    single<CustomerWalletRepository> { CustomerWalletRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Loyalty points ledger + tier management; benefits stored as JSON
    single<LoyaltyRepository> { LoyaltyRepositoryImpl(db = get()) }

    // Installment plans + payment schedule lifecycle
    single<InstallmentRepository> { InstallmentRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 2 Coupons & Promotions ─────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Coupons: CRUD, redemption tracking, customer-coupon assignments, promotions
    single<CouponRepository> { CouponRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 2 Expenses ─────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Expenses: CRUD, approval workflow, categories, recurring schedules
    single<ExpenseRepository> { ExpenseRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 2 Multi-Store / Warehouses ─────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Warehouses: locations per store, default warehouse, two-phase stock transfers
    single<WarehouseRepository> { WarehouseRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 2 Notifications ────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // In-app notifications: insert, mark-read, prune old; no remote sync queue
    single<NotificationRepository> { NotificationRepositoryImpl(db = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Network & Sync Layer (Step 3.4) ──────────────────────────────────────
    // NetworkMonitor is an expect/actual class with platform-specific constructors;
    // its binding MUST be registered in the platform modules (androidDataModule /
    // desktopDataModule). The commonMain module resolves it via `get<NetworkMonitor>()`.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ktor [HttpClient] configured with:
     * - ContentNegotiation (kotlinx.serialization JSON)
     * - Bearer Auth (tokens from [SecurePreferences])
     * - HttpTimeout (connect 10s / request 30s / socket 30s)
     * - Retry (3 attempts, exponential backoff 1s/2s/4s)
     * - Logging (Kermit-backed, DEBUG only)
     *
     * The client is **not** exposed directly — callers receive [ApiService].
     */
    single { buildApiClient(prefs = get()) }

    /**
     * [ApiService] implementation backed by the configured [HttpClient].
     * Bound as both the interface and the concrete [KtorApiService] type so that
     * test code can inject a [MockEngine]-backed variant when needed.
     */
    single<ApiService> { KtorApiService(client = get()) }
    single { get<ApiService>() as KtorApiService }

    /**
     * [SyncEngine] — offline-first push/pull coordinator.
     *
     * Scheduling:
     * - Android: [SyncWorker] (WorkManager CoroutineWorker) calls [SyncEngine.runOnce]
     * - Desktop: Application entry-point calls [SyncEngine.startPeriodicSync]
     */
    single {
        SyncEngine(
            db             = get(),
            api            = get(),
            prefs          = get(),
            networkMonitor = get(),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 3 Staff / HR Repositories ──────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Employee profiles: CRUD, active toggle, soft-delete, name/email search
    single<EmployeeRepository> { EmployeeRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Clock-in/out attendance records; summary aggregation computed in-memory
    single<AttendanceRepository> { AttendanceRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Leave requests: submit, approve, reject; pending flow by store
    single<LeaveRepository> { LeaveRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Payroll records: generate, calculate, mark-paid; store/period summary
    single<PayrollRepository> { PayrollRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Shift scheduling: weekly view, upsert, delete by employee+date
    single<ShiftRepository> { ShiftRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 3 Media Repositories ───────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Media files: polymorphic entity attachments, primary flag, pending upload queue
    single<MediaRepository> { MediaRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Phase 3 Infrastructure Repositories ──────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    // Warehouse racks: physical shelf locations per warehouse
    single<WarehouseRackRepository> { WarehouseRackRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // Accounting ledger: double-entry insert with DEBIT==CREDIT validation
    single<AccountingRepository> { AccountingRepositoryImpl(db = get(), syncEnqueuer = get()) }

    // System health: DB stats, memory metrics, VACUUM, soft-delete purge
    single<SystemRepository> { SystemRepositoryImpl(db = get()) }

    // Backup: in-memory registry (Phase 3 stub; file I/O added in Sprint 13)
    single<BackupRepository> { BackupRepositoryImpl(db = get()) }

    // E-Invoice: SQLDelight-backed (Sprint 18 — replaces in-memory stub)
    single<EInvoiceRepository> { EInvoiceRepositoryImpl(db = get(), syncEnqueuer = get()) }
}
