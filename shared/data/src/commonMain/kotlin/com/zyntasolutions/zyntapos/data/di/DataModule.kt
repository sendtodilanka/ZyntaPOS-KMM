package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseMigrations
import org.koin.dsl.module

/**
 * ZentaPOS — :shared:data Koin DI Module (commonMain)
 *
 * Provides platform-agnostic bindings. Platform-specific bindings for
 * [DatabaseDriverFactory] and [DatabaseKeyProvider] are registered in
 * the platform Koin modules (`androidDataModule` / `desktopDataModule`),
 * which are included from the respective application entry points.
 *
 * ## Module Graph (Step 3.2 — SQLCipher)
 * ```
 * DatabaseKeyProvider  ─┐
 *                        ├─► DatabaseFactory ─► ZyntaDatabase
 * DatabaseDriverFactory ─┘
 * DatabaseMigrations   ─┘
 * ```
 *
 * ## Planned bindings (Step 3.3 — Repositories | Step 3.4 — Ktor + Sync)
 * - ProductRepositoryImpl, OrderRepositoryImpl, etc.
 * - ApiClient (Ktor)
 * - SyncEngine, NetworkMonitor
 */
val dataModule = module {

    // ── Schema Migration Manager ─────────────────────────────────────
    single { DatabaseMigrations() }

    // ── Database Factory (orchestrates key + driver + migrations) ─────
    // DatabaseDriverFactory & DatabaseKeyProvider are expect/actual — their
    // actual instances are bound in platform-specific modules:
    //   Android → androidDataModule (provides context-aware actuals)
    //   Desktop → desktopDataModule (provides path-aware actuals)
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
}
