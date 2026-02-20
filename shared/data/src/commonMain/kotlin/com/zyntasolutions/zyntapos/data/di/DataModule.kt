package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseMigrations
import com.zyntasolutions.zyntapos.data.local.security.PasswordHasher
import com.zyntasolutions.zyntapos.data.local.security.SecurePreferences
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
import org.koin.dsl.module

/**
 * ZentaPOS — :shared:data Koin DI Module (commonMain)
 *
 * Provides platform-agnostic bindings for the entire data layer.
 * Platform-specific bindings ([DatabaseDriverFactory], [DatabaseKeyProvider],
 * [PasswordHasher], [SecurePreferences]) must be registered in the platform Koin
 * modules (`androidDataModule` / `desktopDataModule`) and merged at the application
 * entry point.
 *
 * ## Full Module Graph (Sprint 6 — Steps 3.2 + 3.3)
 *
 * ```
 * DatabaseKeyProvider  ─┐
 *                        ├─► DatabaseFactory ─► ZyntaDatabase ─► all Repositories
 * DatabaseDriverFactory ─┘
 * DatabaseMigrations   ─┘
 *
 * PasswordHasher   ──► AuthRepositoryImpl
 * SecurePreferences──► AuthRepositoryImpl
 *
 * SyncEnqueuer  ─────► OrderRepositoryImpl
 *               ─────► StockRepositoryImpl
 *               ─────► (other write-path repos)
 * ```
 *
 * ## Planned additions (Sprint 6 Step 3.4 — Ktor + SyncEngine)
 * - `ApiService` (Ktor HttpClient)
 * - `NetworkMonitor` (connectivity observer)
 * - `SyncEngine` (orchestrates push/pull cycle)
 */
val dataModule = module {

    // ── Schema Migration Manager ─────────────────────────────────────
    single { DatabaseMigrations() }

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

    // Auth: local BCrypt verification + SecurePreferences JWT cache
    // PasswordHasher & SecurePreferences are platform expect/actual — injected
    // from the platform-specific modules.
    single<AuthRepository> {
        AuthRepositoryImpl(
            db            = get(),
            passwordHasher = get<PasswordHasher>(),
            securePrefs   = get<SecurePreferences>(),
        )
    }

    // Settings: typed key-value wrappers over the `settings` SQLite table
    single<SettingsRepository> { SettingsRepositoryImpl(db = get()) }

    // Sync queue: batch read + status transitions (PENDING→SYNCED/FAILED)
    single<SyncRepository> { SyncRepositoryImpl(db = get()) }

    // SyncRepositoryImpl is also bound directly (not just via interface) so that
    // the SyncEngine can call maintenance methods (pruneSynced, deduplicatePending,
    // markFailed) that are not part of the domain contract.
    single { get<SyncRepository>() as SyncRepositoryImpl }
}
