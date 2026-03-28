package com.zyntasolutions.zyntapos.data.di

import android.content.Context
import android.provider.Settings
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.data.email.EmailPortImpl
import com.zyntasolutions.zyntapos.data.backup.BackupFileManager
import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import com.zyntasolutions.zyntapos.domain.port.EmailPort
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ZyntaPOS — Android Data Koin Module
 *
 * Provides platform-specific actual bindings for Android:
 *
 * | Binding | Implementation | Notes |
 * |---------|---------------|-------|
 * | `String` named("deviceId") | `Settings.Secure.ANDROID_ID` + UUID fallback | Required by `securityModule` ([SecurityAuditLogger]) — MERGED-G1.1 |
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
@OptIn(ExperimentalUuidApi::class)
val androidDataModule = module {

    // ── Device ID (required by securityModule → SecurityAuditLogger) ──
    // Uses Settings.Secure.ANDROID_ID where available (stable per app
    // signing key + device). Falls back to a random UUID for emulators
    // or restricted builds where ANDROID_ID returns null/blank.
    // MERGED-G1.1 (2026-02-22).
    single(named("deviceId")) {
        val context: Context = androidContext()
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank()) androidId else Uuid.random().toString()
    }

    // ── Database (platform expect/actual) ─────────────────────────────
    single { DatabaseKeyProvider(androidContext()) }
    single { DatabaseDriverFactory(context = androidContext()) }

    // ── Backup file I/O (platform expect/actual) ──────────────────────────────
    // Android actual copies DB files to getExternalFilesDir("backups") with
    // a fallback to filesDir/backups when external storage is unavailable.
    single { BackupFileManager(context = androidContext()) }

    // ── Network Monitoring (platform expect/actual) ───────────────────
    // Android actual uses ConnectivityManager.NetworkCallback.
    // Call NetworkMonitor.start() from Application.onCreate() or the
    // app-level ViewModel initialization.
    single { NetworkMonitor(context = androidContext()) }

    // ── Email port (platform expect/actual) ───────────────────────────────────
    // Android actual opens the system email chooser via Intent.ACTION_SENDTO.
    // Application context is sufficient — FLAG_ACTIVITY_NEW_TASK is set by the impl.
    single<EmailPort> { EmailPortImpl(context = androidContext()) }

    // Note: SecurePreferences is bound by securityModule (canonical expect/actual).
    // Adapter class AndroidEncryptedSecurePreferences removed — MERGED-D3 (2026-02-21).
}
