package com.zyntasolutions.zyntapos

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.zyntasolutions.zyntapos.core.di.coreModule
import com.zyntasolutions.zyntapos.data.di.dataModule
import com.zyntasolutions.zyntapos.data.di.desktopDataModule
import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
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
import com.zyntasolutions.zyntapos.feature.reports.jvmReportsModule
import com.zyntasolutions.zyntapos.feature.reports.reportsModule
import com.zyntasolutions.zyntapos.feature.settings.jvmSettingsModule
import com.zyntasolutions.zyntapos.feature.settings.settingsModule
import com.zyntasolutions.zyntapos.feature.staff.staffModule
import com.zyntasolutions.zyntapos.hal.di.halModule
import com.zyntasolutions.zyntapos.navigation.navigationModule
import com.zyntasolutions.zyntapos.security.di.securityModule
import org.koin.core.context.startKoin

/**
 * Desktop (JVM) entry point for ZyntaPOS.
 *
 * [startKoin] is called as the FIRST statement so that every composable that
 * runs inside [application] can safely resolve its Koin dependencies.
 *
 * The load order below mirrors [ZyntaApplication] on Android — each tier can
 * only depend on modules already registered above it:
 *
 * ```
 * core → security → hal → data (platform + common) → navigation → feature modules
 * ```
 *
 * ### Platform variants wired here (JVM-specific)
 * - `desktopDataModule`  — JdbcSqliteDriver + PKCS12 KeyStore + InetAddress NetworkMonitor
 * - `jvmReportsModule`   — [JvmReportExporter] for CSV/PDF export
 *
 * MERGED-A1: Initial Koin bootstrap fix.
 */
fun main() {
    // Load order: core → security → hal → data → domain → feature modules
    val koin = startKoin {
        modules(
            // ── Tier 1: Core infrastructure ──────────────────────────────────
            coreModule,          // Logger, CurrencyFormatter, Dispatchers

            // ── Tier 2: Security ──────────────────────────────────────────────
            securityModule,      // Encryption, KeyManager, RBAC, JWT, PIN

            // ── Tier 3: Hardware Abstraction Layer ────────────────────────────
            halModule(),         // expect/actual: printer port + PrinterManager

            // ── Tier 4: Data — platform bindings BEFORE common bindings ──────
            desktopDataModule,   // JVM: JdbcSqliteDriver, PKCS12 keystore, NetworkMonitor
            dataModule,          // Common: Repositories, SyncEngine, ApiService

            // ── Tier 5: Navigation ────────────────────────────────────────────
            navigationModule,    // RbacNavFilter

            // ── Tier 6: Feature modules ───────────────────────────────────────
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
            jvmReportsModule,    // JvmReportExporter (JVM-only)
            settingsModule,      // SettingsViewModel
            jvmSettingsModule,   // JvmBackupService (JVM-only)
            staffModule,         // (placeholder — bindings added per sprint)
        )
    }

    // ── MERGED-F1: One-time key migration ────────────────────────────────────
    // Resolves SecurePreferencesKeyMigration directly from the KoinApplication
    // returned by startKoin — avoids GlobalContext (Service Locator antipattern).
    // migrate() is idempotent — safe to call on every launch.
    koin.koin.get<SecurePreferencesKeyMigration>().migrate()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ZyntaPOS",
        ) {
            App()
        }
    }
}
