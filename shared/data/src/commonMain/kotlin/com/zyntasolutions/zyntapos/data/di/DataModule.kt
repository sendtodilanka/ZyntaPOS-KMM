package com.zyntasolutions.zyntapos.data.di

/**
 * ZentaPOS — :shared:data Koin DI Module
 *
 * Placeholder for Sprint 6 (Step 3.4.6) where real bindings are added:
 * - DatabaseDriverFactory (platform-specific, via expect/actual)
 * - All RepositoryImpl bindings (ProductRepositoryImpl, OrderRepositoryImpl, etc.)
 * - ApiClient (Ktor)
 * - SyncEngine
 * - NetworkMonitor (platform-specific)
 *
 * Registered in the root Koin graph via `startKoin { modules(dataModule) }`.
 */
internal object DataModule
