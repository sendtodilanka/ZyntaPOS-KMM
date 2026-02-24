package com.zyntasolutions.zyntapos

import android.app.Application
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
import com.zyntasolutions.zyntapos.feature.auth.authModule
import com.zyntasolutions.zyntapos.feature.coupons.couponsModule
import com.zyntasolutions.zyntapos.feature.customers.customersModule
import com.zyntasolutions.zyntapos.feature.expenses.expensesModule
import com.zyntasolutions.zyntapos.feature.inventory.inventoryModule
import com.zyntasolutions.zyntapos.feature.media.mediaModule
import com.zyntasolutions.zyntapos.feature.multistore.multistoreModule
import com.zyntasolutions.zyntapos.feature.pos.posModule
import com.zyntasolutions.zyntapos.feature.register.registerModule
import com.zyntasolutions.zyntapos.feature.reports.androidReportsModule
import com.zyntasolutions.zyntapos.feature.reports.reportsModule
import com.zyntasolutions.zyntapos.feature.settings.androidSettingsModule
import com.zyntasolutions.zyntapos.feature.settings.settingsModule
import com.zyntasolutions.zyntapos.feature.staff.staffModule
import com.zyntasolutions.zyntapos.hal.di.halModule
import com.zyntasolutions.zyntapos.navigation.navigationModule
import com.zyntasolutions.zyntapos.security.di.securityModule
import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
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
                inventoryModule,     // Product/Category/Stock use cases, InventoryViewModel
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
                staffModule,         // (placeholder — bindings added per sprint)
            )
        }

        // ── MERGED-F1: One-time key migration ────────────────────────────────────
        // Rewrites any auth tokens stored under legacy bare-key literals
        // ("access_token", "refresh_token", …) into the canonical dotted-namespace
        // keys ("auth.access_token", …) introduced in the Sprint 8 canonical-key
        // upgrade.  Must run BEFORE any auth operation.
        // migrate() is idempotent — safe to call on every launch.
        koin.koin.get<SecurePreferencesKeyMigration>().migrate()

        // ── Initialise AppInfoProvider with Android BuildConfig values ────────
        val appInfo = koin.koin.get<AppInfoProvider>()
        (appInfo as? AndroidAppInfoProvider)?.init(
            version     = BuildConfig.APP_VERSION_NAME,
            buildNumber = BuildConfig.APP_BUILD_NUMBER,
            buildDate   = BuildConfig.BUILD_DATE,
            debug       = BuildConfig.DEBUG,
        )

        // ── Tier 7: Debug tools — loaded only in debug builds ─────────────────
        // seedModule registers SeedRunner; debugModule registers action handlers
        // and DebugViewModel. Both are loaded AFTER dataModule so all repository
        // bindings are already in the Koin graph.
        if (BuildConfig.DEBUG) {
            koin.koin.loadModules(listOf(seedModule, debugModule))
        }
    }
}
