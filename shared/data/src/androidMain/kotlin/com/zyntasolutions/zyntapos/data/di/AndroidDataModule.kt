package com.zyntasolutions.zyntapos.data.di

import android.content.Context
import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import com.zyntasolutions.zyntapos.data.local.security.InMemorySecurePreferences
import com.zyntasolutions.zyntapos.data.local.security.SecurePreferences
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
 * | [SecurePreferences] | [InMemorySecurePreferences] (Sprint 6 stub) | **Replace in Sprint 8** with EncryptedSharedPreferences |
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
 * ## Sprint 8 upgrade checklist
 * - [ ] Replace `InMemorySecurePreferences` with `EncryptedSharedPreferences` actual
 * - [ ] Remove Sprint 6 scaffold imports
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

    // ── Security Scaffolds (Sprint 6 — replace in Sprint 8) ──────────
    // ⚠️  These are NOT encrypted. For development / testing only.
    single<SecurePreferences> { InMemorySecurePreferences() }
}
