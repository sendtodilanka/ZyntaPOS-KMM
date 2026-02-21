package com.zyntasolutions.zyntapos.data.di

import android.content.Context
import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * ZyntaPOS — Android Data Koin Module
 *
 * Provides platform-specific actual bindings for Android:
 *
 * | Binding | Implementation | Notes |
 * |---------|---------------|-------|
 * | [DatabaseKeyProvider] | Envelope encryption: DEK wrapped by Android Keystore KEK | Replaces never after Sprint 8 |
 * | [DatabaseDriverFactory] | SQLCipher + AndroidSqliteDriver (WAL, 8 MB cache) | — |
 * | [NetworkMonitor] | ConnectivityManager.NetworkCallback → StateFlow<Boolean> | Actual class — needs Context |
 *
 * Note: [SecurePreferences] is now bound directly by `securityModule` (canonical expect/actual).
 * Adapter class `AndroidEncryptedSecurePreferences` deleted — MERGED-D3 (2026-02-21).
 * Note: [PasswordHasher] is now `expect object` in :shared:security — no binding needed here.
 *
 * Include this module alongside [dataModule] in the Android Application's
 * ```kotlin
 * startKoin {
 *     androidContext(this)
 *     modules(androidDataModule, dataModule, coreModule, domainModule)
 * }
 * ```
 *
 * ## Sprint 8 upgrade checklist (COMPLETED — MERGED-D2 + MERGED-D3)
 * - [x] Replace `InMemorySecurePreferences` with encrypted platform actual (Sprint 23)
 * - [x] Remove adapter classes; bind `securityModule.SecurePreferences` directly (MERGED-D3 2026-02-21)
 */
val androidDataModule = module {

    // ── Database (platform expect/actual) ─────────────────────────────
    single { DatabaseKeyProvider(androidContext()) }
    single { DatabaseDriverFactory(context = androidContext()) }

    // ── Network Monitoring (platform expect/actual) ───────────────────
    // Android actual uses ConnectivityManager.NetworkCallback.
    // Call NetworkMonitor.start() from Application.onCreate() or the
    // app-level ViewModel initialization.
    single { NetworkMonitor(context = androidContext()) }

    // Note: SecurePreferences is bound by securityModule (canonical expect/actual).
    // Adapter class AndroidEncryptedSecurePreferences removed — MERGED-D3 (2026-02-21).
}
