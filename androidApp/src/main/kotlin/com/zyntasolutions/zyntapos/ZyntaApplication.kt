package com.zyntasolutions.zyntapos

// CANARY:ZyntaPOS-android-app-i9j0k1l2

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.android.core.SentryAndroid
import com.zyntasolutions.zyntapos.core.platform.AndroidAppInfoProvider
import com.zyntasolutions.zyntapos.core.platform.AppInfoProvider
import com.zyntasolutions.zyntapos.core.di.coreModule
import com.zyntasolutions.zyntapos.debug.debugModule
import com.zyntasolutions.zyntapos.seed.seedModule
import com.zyntasolutions.zyntapos.data.di.androidDataModule
import com.zyntasolutions.zyntapos.data.di.dataModule
import com.zyntasolutions.zyntapos.feature.dashboard.dashboardModule
import com.zyntasolutions.zyntapos.feature.onboarding.onboardingModule
import com.zyntasolutions.zyntapos.feature.admin.adminModule
import com.zyntasolutions.zyntapos.feature.diagnostic.di.diagnosticModule
import com.zyntasolutions.zyntapos.feature.auth.authModule
import com.zyntasolutions.zyntapos.feature.coupons.couponsModule
import com.zyntasolutions.zyntapos.feature.customers.customersModule
import com.zyntasolutions.zyntapos.feature.expenses.expensesModule
import com.zyntasolutions.zyntapos.feature.inventory.inventoryModule
import com.zyntasolutions.zyntapos.feature.inventory.di.androidInventoryLabelModule
import com.zyntasolutions.zyntapos.feature.media.mediaModule
import com.zyntasolutions.zyntapos.feature.multistore.multistoreModule
import com.zyntasolutions.zyntapos.feature.pos.posModule
import com.zyntasolutions.zyntapos.feature.register.registerModule
import com.zyntasolutions.zyntapos.feature.reports.androidReportsModule
import com.zyntasolutions.zyntapos.feature.reports.reportsModule
import com.zyntasolutions.zyntapos.feature.settings.androidSettingsModule
import com.zyntasolutions.zyntapos.feature.settings.settingsModule
import com.zyntasolutions.zyntapos.feature.staff.staffModule
import com.zyntasolutions.zyntapos.feature.accounting.di.accountingModule
import com.zyntasolutions.zyntapos.hal.di.halModule
import com.zyntasolutions.zyntapos.navigation.navigationModule
import com.zyntasolutions.zyntapos.security.di.securityModule
import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
import com.zyntasolutions.zyntapos.data.job.AuditIntegrityJob
import com.zyntasolutions.zyntapos.data.job.AuditIntegrityWorker
import com.zyntasolutions.zyntapos.data.job.FulfillmentExpiryWorker
import com.zyntasolutions.zyntapos.data.job.LogRetentionJob
import com.zyntasolutions.zyntapos.data.job.LogRetentionWorker
import com.zyntasolutions.zyntapos.data.job.LowStockNotificationJob
import com.zyntasolutions.zyntapos.data.job.SlaAlertJob
import com.zyntasolutions.zyntapos.data.sync.NetworkMonitor
import com.zyntasolutions.zyntapos.data.sync.SyncWorker
import com.zyntasolutions.zyntapos.data.logging.KermitSqliteAdapter
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.seed.DefaultSeedDataSet
import com.zyntasolutions.zyntapos.seed.SeedRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android [Application] entry point for ZyntaPOS.
 *
 * Bootstraps the Koin dependency injection graph with every module discovered
 * across the project. The load order below is intentional — each tier can only
 * depend on modules already registered above it:
 *
 * ```
 * core → security → hal → data (platform + common) → navigation → feature modules
 * ```
 *
 * ### Excluded (not Koin modules — internal object placeholders pending implementation)
 * - `:shared:domain`        → `DomainModule`      (internal object, no bindings yet)
 * - `:composeApp:designsystem` → `DesignSystemModule` (internal object, no bindings yet)
 *
 * ### Platform variants wired here (Android-specific)
 * - `androidDataModule`       — SQLCipher + AndroidSqliteDriver + ConnectivityManager
 * - `androidReportsModule`    — [AndroidReportExporter] for PDF/CSV export
 *
 * MERGED-A1: Initial Koin bootstrap fix.
 */
class ZyntaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── Firebase Analytics + Crashlytics — MUST init before Koin (TODO-011) ─
        // google-services.json is CI-injected from GOOGLE_SERVICES_JSON secret.
        FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // ── Sentry crash reporter — MUST init before Koin (ADR-011 rule #4) ───
        // EU ingest endpoint via .ingest.de.sentry.io DSN.
        // DSN injected via Secrets Gradle Plugin (ZYNTA_SENTRY_DSN → BuildConfig).
        SentryAndroid.init(this) { options ->
            options.dsn         = BuildConfig.ZYNTA_SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.release     = "com.zyntasolutions.zyntapos@${BuildConfig.APP_VERSION_NAME}+${BuildConfig.APP_VERSION_CODE}"
            options.isEnableAutoSessionTracking = true
            options.isAnrEnabled = true
        }

        val koin = startKoin {
            androidContext(this@ZyntaApplication)

            modules(
                // Load order: core → security → hal → data → domain → feature modules

                // ── Tier 1: Core infrastructure ──────────────────────────────
                coreModule,          // Logger, CurrencyFormatter, Dispatchers

                // ── Tier 2: Security ──────────────────────────────────────────
                securityModule,      // Encryption, KeyManager, RBAC, JWT, PIN

                // ── Tier 3: Hardware Abstraction Layer ────────────────────────
                halModule(),         // expect/actual: printer port + PrinterManager

                // ── Tier 4: Data — platform bindings BEFORE common bindings ──
                androidDataModule,   // Android: SQLCipher driver, Keystore, NetworkMonitor
                dataModule,          // Common: Repositories, SyncEngine, ApiService

                // ── Tier 5: Navigation ────────────────────────────────────────
                navigationModule,    // RbacNavFilter

                // ── Tier 6: Feature modules ───────────────────────────────────
                dashboardModule,     // DashboardViewModel (:composeApp:feature:dashboard)
                onboardingModule,    // OnboardingViewModel (:composeApp:feature:onboarding)
                authModule,          // LoginUseCase, SessionManager, AuthViewModel
                posModule,           // Cart use cases, PosViewModel
                inventoryModule,              // Product/Category/Stock use cases, InventoryViewModel
                androidInventoryLabelModule,  // AndroidLabelPdfRenderer (Android-only)
                adminModule,         // (placeholder — bindings added per sprint)
                customersModule,     // (placeholder — bindings added per sprint)
                couponsModule,       // (placeholder — bindings added per sprint)
                expensesModule,      // (placeholder — bindings added per sprint)
                mediaModule,         // (placeholder — bindings added per sprint)
                multistoreModule,    // (placeholder — bindings added per sprint)
                registerModule,      // Register session use cases, RegisterViewModel
                reportsModule,       // Sales/Stock report use cases, ReportsViewModel
                androidReportsModule(this@ZyntaApplication), // AndroidReportExporter
                settingsModule,      // SettingsViewModel
                androidSettingsModule(this@ZyntaApplication), // AndroidBackupService
                staffModule,         // Employee HR, attendance, payroll
                accountingModule,    // E-Invoice / IRD submission (Sprint 18-24)
                diagnosticModule,    // Remote diagnostic consent (ENTERPRISE, TODO-006)
            )
        }

        // ── MERGED-F1: One-time key migration ────────────────────────────────────
        // Rewrites any auth tokens stored under legacy bare-key literals
        // ("access_token", "refresh_token", …) into the canonical dotted-namespace
        // keys ("auth.access_token", …) introduced in the Sprint 8 canonical-key
        // upgrade.  Must run BEFORE any auth operation.
        // migrate() is idempotent — safe to call on every launch.
        koin.koin.get<SecurePreferencesKeyMigration>().migrate()

        // ── Feature registry default seeding ─────────────────────────────────────
        // Ensures all 23 ZyntaFeature rows exist in the local DB on every launch.
        // initDefaults() uses INSERT OR IGNORE so existing rows are never overwritten.
        // Runs on IO to avoid blocking the main thread during startup.
        CoroutineScope(Dispatchers.IO).launch {
            koin.koin.get<FeatureRegistryRepository>()
                .initDefaults(Clock.System.now().toEpochMilliseconds())
        }

        // ── Initialise AppInfoProvider with Android BuildConfig values ────────
        val appInfo = koin.koin.get<AppInfoProvider>()
        (appInfo as? AndroidAppInfoProvider)?.init(
            version     = BuildConfig.APP_VERSION_NAME,
            buildNumber = BuildConfig.APP_BUILD_NUMBER,
            buildDate   = BuildConfig.BUILD_DATE,
            debug       = BuildConfig.DEBUG,
        )

        // ── Kermit → SQLite bridge ──────────────────────────────────────────────
        // Routes all Kermit log events to the operational_logs table for diagnostic
        // queries via the Admin debug console. Must run after dataModule is loaded.
        Logger.addLogWriter(koin.koin.get<KermitSqliteAdapter>())

        // ── Background jobs (WorkManager — battery-efficient, survives process death) ──
        // LogRetentionWorker: daily purge of expired operational_logs (3/14/30/90-day policy)
        // AuditIntegrityWorker: daily SHA-256 hash chain verification of audit_entries
        // On Android, WorkManager replaces coroutine while-loops for reliable scheduling.
        // Desktop still uses LogRetentionJob.start() / AuditIntegrityJob.start() coroutine loops.
        LogRetentionWorker.schedule(this)
        AuditIntegrityWorker.schedule(this)
        FulfillmentExpiryWorker.schedule(this)

        // ── C6.2: Offline-first sync bootstrap ─────────────────────────────────
        // Start network monitoring for real-time connectivity state.
        // Schedule WorkManager periodic sync (15-min interval, requires network).
        koin.koin.get<NetworkMonitor>().start()
        SyncWorker.schedule(this, requireWifi = false)

        // ── Cross-store monitoring jobs (coroutine-based, need reactive Flow collection) ──
        koin.koin.get<LowStockNotificationJob>().start()
        koin.koin.get<SlaAlertJob>().start()

        // ── Tier 7: Debug tools — loaded only in debug builds ─────────────────
        // seedModule    — registers SeedRunner (55+ products, 25 customers, etc.)
        // debugModule   — registers DebugViewModel + 6-tab console action handlers
        // devModules    — overrides ApiService with DevApiService (no-op sync stub)
        //                 so the app runs without a real backend during dev testing.
        //                 src/debug/DevModules.kt provides the override list;
        //                 src/release/DevModules.kt returns emptyList() for release.
        // allowOverride — required so devDataModule can replace KtorApiService.
        if (BuildConfig.DEBUG) {
            koin.koin.loadModules(listOf(seedModule, debugModule) + devModules, allowOverride = true)
            triggerAutoSeedIfNeeded(
                settingsRepository = koin.koin.get(),
                seedRunner         = koin.koin.get(),
            )
        }
    }

    /**
     * Auto-seeds the Demo grocery dataset the first time a developer runs the app
     * after completing onboarding on a clean install.
     *
     * Conditions (both must be true):
     * 1. `onboarding.completed == "true"` — the admin account and store name exist.
     * 2. `debug.auto_seeded` is not `"true"` — prevents re-seeding on every launch.
     *
     * Runs on [Dispatchers.IO] — non-blocking relative to the main thread.
     * Failures are swallowed; they are non-fatal in a debug context.
     *
     * Debug-build only — gated by [BuildConfig.DEBUG] in [onCreate].
     */
    private fun triggerAutoSeedIfNeeded(
        settingsRepository: SettingsRepository,
        seedRunner: SeedRunner,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isOnboarded   = settingsRepository.get("onboarding.completed") == "true"
                val alreadySeeded = settingsRepository.get("debug.auto_seeded")    == "true"
                if (isOnboarded && !alreadySeeded) {
                    seedRunner.run(DefaultSeedDataSet.build())
                    settingsRepository.set("debug.auto_seeded", "true")
                }
            } catch (_: Exception) {
                // Auto-seed is best-effort in debug builds — log via debug console if needed
            }
        }
    }
}
