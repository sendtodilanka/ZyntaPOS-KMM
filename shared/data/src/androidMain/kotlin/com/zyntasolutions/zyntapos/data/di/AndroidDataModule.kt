package com.zyntasolutions.zyntapos.data.di

import android.content.Context
import android.provider.Settings
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.data.analytics.AnalyticsService
import com.zyntasolutions.zyntapos.data.backup.BackupFileManager
import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import com.zyntasolutions.zyntapos.data.remote.ird.IrdApiClient
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
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

    // ── IRD e-Invoice API client (mTLS, platform expect/actual) ───────────────
    // Reads endpoint + cert config from AppConfig (set at app startup from
    // BuildConfig.ZYNTA_IRD_* secrets injected by the Gradle Secrets Plugin).
    single {
        IrdApiClient(
            endpoint     = AppConfig.IRD_API_ENDPOINT,
            certPath     = AppConfig.IRD_CLIENT_CERT_PATH,
            certPassword = AppConfig.IRD_CLIENT_CERT_PASSWORD,
        )
    }

    // ── Network Monitoring (platform expect/actual) ───────────────────
    // Android actual uses ConnectivityManager.NetworkCallback.
    // Call NetworkMonitor.start() from Application.onCreate() or the
    // app-level ViewModel initialization.
    single { NetworkMonitor(context = androidContext()) }

    // ── Analytics (platform expect/actual) ──────────────────────────────
    // Android actual uses Firebase Analytics SDK.
    // Bound as both concrete type and AnalyticsTracker interface so feature
    // modules can depend on the interface from :shared:core.
    single { AnalyticsService(context = androidContext()) }
    single<AnalyticsTracker> { get<AnalyticsService>() }

    // Note: SecurePreferences is bound by securityModule (canonical expect/actual).
    // Adapter class AndroidEncryptedSecurePreferences removed — MERGED-D3 (2026-02-21).
}
