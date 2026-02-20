// ============================================================
// ZyntaPOS — Settings Script
// ============================================================

rootProject.name = "ZyntaPOS"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ── Plugin Repositories ───────────────────────────────────────
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
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
// MODULE REGISTRY
// ════════════════════════════════════════════════════════════

// ── Application Shell ─────────────────────────────────────────
// Root Compose Multiplatform module (Android + JVM desktop).
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
