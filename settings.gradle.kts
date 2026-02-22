// ============================================================
// ZyntaPOS — Settings Script
// ============================================================

rootProject.name = "ZyntaPOS"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ── Plugin Repositories ───────────────────────────────────────
pluginManagement {
    // Force KSP to use the Kotlin-2.3.x-compatible version across all sub-modules.
    // Mockative 3.0.1 internally applies ksp-2.0.21-1.0.26 which is incompatible
    // with Kotlin 2.3.0 (missing KotlinCompile.ClasspathSnapshotProperties API).
    // This resolution strategy ensures the correct KSP version is used regardless
    // of what the Mockative plugin declares as its own KSP dependency.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useVersion("2.3.4")
            }
        }
    }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // JetBrains Compose Multiplatform plugin releases (incl. hot-reload)
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        gradlePluginPortal()
    }
}

// ── Dependency Repositories ───────────────────────────────────
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Fail fast if any sub-module declares its own repositories.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // JetBrains Compose Multiplatform library artifacts (org.jetbrains.compose.*)
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        // SQLCipher Android (net.zetetic) lives on Maven Central.
        // JitPack fallback for any community libs not yet on Central.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

// ── Gradle Toolchains ─────────────────────────────────────────
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ════════════════════════════════════════════════════════════
// MODULE REGISTRY — 23 modules
// ════════════════════════════════════════════════════════════

// ── Application Shell ─────────────────────────────────────────
// Android-only application module. Depends on :composeApp (KMP library).
// Isolates com.android.application from KMP to comply with AGP 9.0.0+.
include(":androidApp")

// ── Compose Multiplatform Entry Point (KMP Library) ───────────
// Root KMP module: JVM Desktop entry point + Android library target.
// Provides App() composable and shared platform bootstrapping.
include(":composeApp")

// ── Shared — Tier 1: Core ─────────────────────────────────────
// Pure Kotlin utilities, MVI base, result types, extensions.
// No platform imports — compiles to all targets.
include(":shared:core")

// ── Shared — Tier 2: Domain ───────────────────────────────────
// Domain models, use-case interfaces, repository contracts.
// Business rules only — zero framework dependencies.
include(":shared:domain")

// ── Shared — Tier 3: Data ─────────────────────────────────────
// SQLDelight schema, DAOs, Ktor HTTP client, repository impls.
// Implements interfaces defined in :shared:domain.
include(":shared:data")

// ── Shared — Hardware Abstraction Layer ───────────────────────
// expect/actual for thermal printers, barcode scanners, cash drawers.
// Android actual: USB Host API + ML Kit.
// JVM actual: TCP/IP sockets + HID keyboard emulation.
include(":shared:hal")

// ── Shared — Security ─────────────────────────────────────────
// AES-256-GCM crypto, Keystore/JCE key storage, PBKDF2 PIN hashing,
// JWT token management, RBAC engine, session management.
include(":shared:security")

// ── ComposeApp — Core (Shared UI Infrastructure) ─────────────
// Generic MVI BaseViewModel<S,I,E>, shared Channel/StateFlow wiring,
// and any future cross-feature UI infrastructure.
// Every :composeApp:feature:* module depends on this.
include(":composeApp:core")

// ── ComposeApp — Design System ────────────────────────────────
// Material 3 theme tokens, typography, shape, spacing, reusable
// stateless components (ZentaButton, ZentaCard, NumericKeypad, …).
include(":composeApp:designsystem")

// ── ComposeApp — Navigation ────────────────────────────────────
// Type-safe NavHost, platform-adaptive nav shell (Rail vs Bottom Bar),
// RBAC-gated route filtering.
include(":composeApp:navigation")

// ── ComposeApp — Feature: Authentication ─────────────────────
// Login, PIN quick-switch, biometric, auto-lock screen.
include(":composeApp:feature:auth")

// ── ComposeApp — Feature: POS ─────────────────────────────────
// Product grid, cart, discount, payment, receipt, refund, held orders.
include(":composeApp:feature:pos")

// ── ComposeApp — Feature: Inventory ───────────────────────────
// Product CRUD, category management, stock levels, adjustments.
include(":composeApp:feature:inventory")

// ── ComposeApp — Feature: Cash Register ───────────────────────
// Session open/close, cash movements, EOD Z-report.
include(":composeApp:feature:register")

// ── ComposeApp — Feature: Reports ─────────────────────────────
// Sales summary, product performance, stock report, export (CSV/PDF).
include(":composeApp:feature:reports")

// ── ComposeApp — Feature: Settings ────────────────────────────
// Store profile, tax config, printer setup, user management,
// security policy, appearance, backup/restore.
include(":composeApp:feature:settings")

// ── ComposeApp — Feature: Customers (CRM) ────────────────────
// Customer directory, loyalty accounts, GDPR export/erase.
include(":composeApp:feature:customers")

// ── ComposeApp — Feature: Coupons & Promotions ────────────────
// Coupon CRUD, promotion rule engine (BOGO, % discount, threshold).
include(":composeApp:feature:coupons")

// ── ComposeApp — Feature: Expenses ────────────────────────────
// Expense recording, P&L report, cash-flow overview.
include(":composeApp:feature:expenses")

// ── ComposeApp — Feature: Staff / HR ──────────────────────────
// Employee profiles, shift scheduling, attendance, payroll.
include(":composeApp:feature:staff")

// ── ComposeApp — Feature: Multi-Store ────────────────────────
// Store selector, central dashboard, inter-store stock transfers.
include(":composeApp:feature:multistore")

// ── ComposeApp — Feature: Administration ─────────────────────
// System health, audit log viewer, DB maintenance, backup management.
include(":composeApp:feature:admin")

// ── ComposeApp — Feature: Media ───────────────────────────────
// Product image picker, crop, compression pipeline.
include(":composeApp:feature:media")
