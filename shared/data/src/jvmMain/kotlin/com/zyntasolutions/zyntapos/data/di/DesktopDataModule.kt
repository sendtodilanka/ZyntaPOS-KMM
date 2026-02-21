package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import org.koin.dsl.module
import java.io.File

/**
 * ZyntaPOS — Desktop (JVM) Data Koin Module
 *
 * Provides platform-specific actual bindings for the Desktop JVM target:
 *
 * | Binding | Implementation | Notes |
 * |---------|---------------|-------|
 * | [DatabaseKeyProvider] | JCE PKCS12 KeyStore AES-256, machine-fingerprint password | — |
 * | [DatabaseDriverFactory] | JdbcSqliteDriver (WAL, 8 MB cache, 5s busy_timeout) | — |
 * | [NetworkMonitor] | Periodic InetAddress.isReachable() → StateFlow<Boolean> | Actual class — no-arg |
 *
 * Note: [SecurePreferences] is now bound directly by `securityModule` (canonical expect/actual).
 * Adapter class `DesktopAesSecurePreferences` deleted — MERGED-D3 (2026-02-21).
 * Note: [PasswordHasher] is now `expect object` in :shared:security — no binding needed here.
 *
 * The application data directory is resolved from the OS-specific user home:
 * - macOS/Linux: `~/.zyntapos/data/`
 * - Windows:     `%APPDATA%/ZyntaPOS/data/`
 *
 * Include this module alongside [dataModule] in the Desktop main entry-point:
 * ```kotlin
 * startKoin {
 *     modules(desktopDataModule, dataModule, coreModule, domainModule)
 * }
 * ```
 *
 * After Koin start, initialize networking:
 * ```kotlin
 * val monitor = get<NetworkMonitor>()
 * monitor.start()
 * val syncEngine = get<SyncEngine>()
 * syncEngine.startPeriodicSync(applicationScope)
 * ```
 *
 * ## Sprint 8 upgrade checklist (COMPLETED — MERGED-D2 + MERGED-D3)
 * - [x] Replace `InMemorySecurePreferences` with encrypted platform actual (Sprint 23)
 * - [x] Remove adapter classes; bind `securityModule.SecurePreferences` directly (MERGED-D3 2026-02-21)
 *
 * ## Production Deployment Note — SQLCipher Native Libs
 * For full AES-256 encryption bundle the native SQLCipher library:
 * - macOS  → `libsqlcipher.dylib` in `lib/` next to the JAR
 * - Linux  → `libsqlcipher.so`  in `lib/`
 * - Windows → `sqlcipher.dll` in app root
 * Set: `-Dorg.xerial.sqlite.lib.path=/path/to/lib`
 */
val desktopDataModule = module {

    // ── App data directory (OS-resolved) ──────────────────────────────
    single { resolveAppDataDir() }

    // ── Database (platform expect/actual) ─────────────────────────────
    single { DatabaseKeyProvider(appDataDir = get()) }
    single { DatabaseDriverFactory(appDataDir = get()) }

    // ── Network Monitoring (platform expect/actual) ───────────────────
    // Desktop actual uses periodic InetAddress.isReachable() polling.
    // Call NetworkMonitor.start() after Koin initialization.
    single { NetworkMonitor() }

    // Note: SecurePreferences is bound by securityModule (canonical expect/actual).
    // Adapter class DesktopAesSecurePreferences removed — MERGED-D3 (2026-02-21).
}

/**
 * Resolves the OS-appropriate application data directory for ZyntaPOS.
 * Creates the directory (and parents) if it does not already exist.
 */
private fun resolveAppDataDir(): String {
    val base = when {
        System.getProperty("os.name")?.lowercase()?.contains("win") == true ->
            System.getenv("APPDATA") ?: System.getProperty("user.home")
        else ->
            System.getProperty("user.home")
    }
    val dir = File(base, if (base == System.getenv("APPDATA")) "ZyntaPOS/data" else ".zyntapos/data")
    dir.mkdirs()
    return dir.absolutePath
}
