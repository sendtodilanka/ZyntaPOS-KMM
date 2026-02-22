package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseMigrations
import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.api.KtorApiService
import com.zyntasolutions.zyntapos.data.remote.api.buildApiClient
import com.zyntasolutions.zyntapos.data.repository.AuditRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.AuthRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CategoryRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerRepositoryImpl
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
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import com.zyntasolutions.zyntapos.data.sync.SyncEngine
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
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
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
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

    // Sync queue: batch read + status transitions (PENDING→SYNCED/FAILED)
    single<SyncRepository> { SyncRepositoryImpl(db = get()) }

    // SyncRepositoryImpl is also bound directly (not just via interface) so that
    // the SyncEngine can call maintenance methods (pruneSynced, deduplicatePending,
    // markFailed) that are not part of the domain contract.
    single { get<SyncRepository>() as SyncRepositoryImpl }

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
}
