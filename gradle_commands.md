# ZyntaPOS — Gradle Command Reference
> **Project Root:** `ZyntaPOS-KMM/` (run all commands from repository root)
> **Wrapper:** Always use `./gradlew` (never bare `gradle`) to ensure the correct Gradle version.
> **Last Updated:** 2026-03-18

---

## Table of Contents
1. [Environment & Wrapper](#1-environment--wrapper)
2. [Build Commands](#2-build-commands)
3. [Run Commands](#3-run-commands)
4. [Test Commands — Full Suite](#4-test-commands--full-suite)
5. [Test Commands — Per Module](#5-test-commands--per-module)
6. [Test Commands — Per Platform](#6-test-commands--per-platform)
7. [Code Quality & Analysis](#7-code-quality--analysis)
8. [Dependency Management](#8-dependency-management)
9. [SQLDelight Code Generation](#9-sqldelight-code-generation)
10. [Android APK & AAB Packaging](#10-android-apk--aab-packaging)
11. [Desktop JVM Packaging](#11-desktop-jvm-packaging)
12. [Clean Commands](#12-clean-commands)
13. [Composite / CI Pipeline Commands](#13-composite--ci-pipeline-commands)
14. [Module Quick Reference Map](#14-module-quick-reference-map)

---

## 1. Environment & Wrapper

```bash
# Verify Gradle wrapper version
./gradlew --version

# Show JVM info used by Gradle
./gradlew --info help | grep "JVM"

# Print all available tasks (project-wide)
./gradlew tasks --all

# Print tasks for a specific module
./gradlew :shared:core:tasks --all
./gradlew :composeApp:tasks --all

# Show project dependency graph
./gradlew dependencies

# Show dependencies for a specific module
./gradlew :shared:domain:dependencies
```

---

## 2. Build Commands

### Full Project Build
```bash
# Assemble ALL modules (debug)
./gradlew assemble

# Assemble ALL modules (release)
./gradlew assembleRelease

# Build (assemble + test) — full verification
./gradlew build

# Build with stacktrace (for debugging failures)
./gradlew build --stacktrace

# Build with full info logging
./gradlew build --info

# Build with scan (requires Gradle Enterprise or free scans.gradle.com)
./gradlew build --scan
```

### Individual Module Builds
```bash
# Shared modules
./gradlew :shared:core:assemble
./gradlew :shared:domain:assemble
./gradlew :shared:data:assemble
./gradlew :shared:hal:assemble
./gradlew :shared:security:assemble

# ComposeApp root
./gradlew :composeApp:assemble

# ComposeApp sub-modules
./gradlew :composeApp:core:assemble
./gradlew :composeApp:designsystem:assemble
./gradlew :composeApp:navigation:assemble

# Feature modules (17 total)
./gradlew :composeApp:feature:auth:assemble
./gradlew :composeApp:feature:dashboard:assemble
./gradlew :composeApp:feature:onboarding:assemble
./gradlew :composeApp:feature:pos:assemble
./gradlew :composeApp:feature:inventory:assemble
./gradlew :composeApp:feature:register:assemble
./gradlew :composeApp:feature:reports:assemble
./gradlew :composeApp:feature:settings:assemble
./gradlew :composeApp:feature:customers:assemble
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:feature:admin:assemble
./gradlew :composeApp:feature:coupons:assemble
./gradlew :composeApp:feature:expenses:assemble
./gradlew :composeApp:feature:media:assemble
./gradlew :composeApp:feature:multistore:assemble
./gradlew :composeApp:feature:accounting:assemble
./gradlew :composeApp:feature:diagnostic:assemble
```

### Compile Only (faster — no packaging)
```bash
# Compile commonMain across all KMP modules
./gradlew compileKotlinMetadata

# Compile Android main sources
./gradlew compileDebugKotlin

# Compile JVM/Desktop main sources
./gradlew compileKotlinJvm

# Compile a single module (Android)
./gradlew :shared:domain:compileDebugKotlin

# Compile a single module (JVM)
./gradlew :shared:domain:compileKotlinJvm
```

---

## 3. Run Commands

### Android (requires emulator or connected device)
```bash
# Install & launch debug build on connected Android device/emulator
./gradlew :composeApp:installDebug

# Run Android app directly (Android Gradle Plugin shortcut)
./gradlew :androidApp:installDebug
```

### Desktop (JVM)
```bash
# Run Desktop app directly (Compose for Desktop task)
./gradlew :composeApp:run

# Run with custom JVM arguments
./gradlew :composeApp:run --args="--debug"
```

---

## 4. Test Commands — Full Suite

```bash
# ✅ Run ALL tests across ALL modules (recommended before any commit)
./gradlew test

# Run all tests with parallel execution (faster on multi-core)
./gradlew test --parallel

# Run all tests + generate coverage reports
./gradlew test jacocoTestReport

# Run all tests, continue even if some fail (get full report)
./gradlew test --continue

# Run all tests with full verbose output
./gradlew test --info --continue

# Run all tests and open HTML report automatically (macOS)
./gradlew test && open build/reports/tests/test/index.html
```

---

## 5. Test Commands — Per Module

### Shared Modules
```bash
# :shared:core
./gradlew :shared:core:test
./gradlew :shared:core:testDebugUnitTest        # Android unit tests
./gradlew :shared:core:testReleaseUnitTest
./gradlew :shared:core:jvmTest                  # Desktop/JVM tests

# :shared:domain
./gradlew :shared:domain:test
./gradlew :shared:domain:testDebugUnitTest
./gradlew :shared:domain:jvmTest

# :shared:data
./gradlew :shared:data:test
./gradlew :shared:data:testDebugUnitTest
./gradlew :shared:data:jvmTest

# :shared:hal
./gradlew :shared:hal:test
./gradlew :shared:hal:jvmTest

# :shared:security
./gradlew :shared:security:test
./gradlew :shared:security:testDebugUnitTest
./gradlew :shared:security:jvmTest
```

### ComposeApp Core Modules
```bash
# :composeApp:core
./gradlew :composeApp:core:test
./gradlew :composeApp:core:jvmTest

# :composeApp:designsystem
./gradlew :composeApp:designsystem:test
./gradlew :composeApp:designsystem:jvmTest

# :composeApp:navigation
./gradlew :composeApp:navigation:test
./gradlew :composeApp:navigation:jvmTest
```

### Feature Module Tests
```bash
# Auth
./gradlew :composeApp:feature:auth:test
./gradlew :composeApp:feature:auth:testDebugUnitTest
./gradlew :composeApp:feature:auth:jvmTest

# POS (critical — highest coverage priority)
./gradlew :composeApp:feature:pos:test
./gradlew :composeApp:feature:pos:testDebugUnitTest
./gradlew :composeApp:feature:pos:jvmTest

# Inventory
./gradlew :composeApp:feature:inventory:test
./gradlew :composeApp:feature:inventory:jvmTest

# Register
./gradlew :composeApp:feature:register:test
./gradlew :composeApp:feature:register:jvmTest

# Reports
./gradlew :composeApp:feature:reports:test
./gradlew :composeApp:feature:reports:jvmTest

# Settings
./gradlew :composeApp:feature:settings:test
./gradlew :composeApp:feature:settings:jvmTest

# Customers
./gradlew :composeApp:feature:customers:test

# Staff
./gradlew :composeApp:feature:staff:test

# Admin
./gradlew :composeApp:feature:admin:test

# Coupons
./gradlew :composeApp:feature:coupons:test

# Expenses
./gradlew :composeApp:feature:expenses:test

# Media
./gradlew :composeApp:feature:media:test

# Multi-store
./gradlew :composeApp:feature:multistore:test

# Accounting
./gradlew :composeApp:feature:accounting:test

# Dashboard
./gradlew :composeApp:feature:dashboard:test

# Onboarding
./gradlew :composeApp:feature:onboarding:test

# Diagnostic
./gradlew :composeApp:feature:diagnostic:test
```

### Run a Single Test Class
```bash
# Pattern: ./gradlew :<module>:test --tests "<fully.qualified.TestClass>"

./gradlew :shared:domain:test --tests "com.zyntasolutions.zyntapos.domain.usecase.CalculateOrderTotalsUseCaseTest"
./gradlew :shared:data:jvmTest --tests "com.zyntasolutions.zyntapos.data.repository.ProductRepositoryTest"
./gradlew :composeApp:feature:pos:test --tests "com.zyntasolutions.zyntapos.feature.pos.viewmodel.PosViewModelTest"
```

### Run a Single Test Method
```bash
# Pattern: --tests "<ClassName>.<methodName>"
./gradlew :shared:domain:test --tests "*.CalculateOrderTotalsUseCaseTest.calculateTax_inclusiveRate_returnsCorrectTotal"
```

---

## 6. Test Commands — Per Platform

```bash
# Common (shared KMP) tests only
./gradlew allTests                     # All KMP target tests
./gradlew :shared:domain:allTests

# Android Unit Tests only (all modules)
./gradlew testDebugUnitTest

# Android Release Unit Tests
./gradlew testReleaseUnitTest

# JVM / Desktop tests only (all modules)
./gradlew jvmTest

# Android Instrumented Tests (requires device/emulator)
./gradlew connectedAndroidTest
./gradlew :composeApp:connectedDebugAndroidTest

# Android Instrumented Tests — specific module
./gradlew :composeApp:feature:pos:connectedDebugAndroidTest
```

---

## 7. Code Quality & Analysis

### Lint
```bash
# Run Android Lint on all modules
./gradlew lint

# Lint on specific module
./gradlew :composeApp:feature:pos:lint
./gradlew :shared:domain:lint

# Lint check only (no HTML report, CI-friendly)
./gradlew lintDebug

# Generate lint report
./gradlew lintReport
```

### Detekt (Static Analysis — if configured)
```bash
# Run Detekt across entire project
./gradlew detekt

# Run Detekt on single module
./gradlew :shared:core:detekt

# Run Detekt with auto-format (requires detektCreateBaseline)
./gradlew detektGenerateConfig
./gradlew detektBaseline
```

### Ktlint (if configured)
```bash
# Check formatting
./gradlew ktlintCheck

# Auto-fix formatting
./gradlew ktlintFormat
```

### Jacoco Coverage Reports
```bash
# Generate coverage report (after running tests)
./gradlew jacocoTestReport

# Per-module coverage
./gradlew :shared:domain:jacocoTestReport
./gradlew :shared:security:jacocoTestReport

# Coverage verification (fails if below threshold)
./gradlew jacocoTestCoverageVerification
```

---

## 8. Dependency Management

```bash
# Show full dependency tree for entire project
./gradlew dependencies

# Show dependencies for a specific module + configuration
./gradlew :shared:data:dependencies --configuration commonMainImplementation
./gradlew :composeApp:feature:pos:dependencies --configuration androidDebugRuntimeClasspath

# Check for dependency updates (requires ben-manes/gradle-versions-plugin)
./gradlew dependencyUpdates

# Resolve and lock dependency versions
./gradlew dependencies --write-locks

# Show why a specific dependency is included
./gradlew :shared:core:dependencyInsight --dependency kotlinx-coroutines-core
./gradlew :shared:data:dependencyInsight --dependency sqldelight-runtime

# List all modules in the project
./gradlew projects
```

---

## 9. SQLDelight Code Generation

```bash
# Generate all SQLDelight database interfaces
./gradlew generateSqlDelightInterface

# Generate for specific module
./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface

# Verify SQLDelight migrations
./gradlew verifySqlDelightMigration

# Squash all migrations into one baseline
./gradlew squashSqlDelightMigrations
```

---

## 10. Android APK & AAB Packaging

```bash
# Build debug APK
./gradlew :composeApp:assembleDebug
# Output: composeApp/build/outputs/apk/debug/

# Build release APK (requires signing config in build.gradle.kts)
./gradlew :composeApp:assembleRelease
# Output: composeApp/build/outputs/apk/release/

# Build Android App Bundle (AAB — for Play Store)
./gradlew :composeApp:bundleRelease
# Output: composeApp/build/outputs/bundle/release/

# Build release APK with specific signing
./gradlew :composeApp:assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=YOUR_STORE_PASS \
  -Pandroid.injected.signing.key.alias=YOUR_KEY_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASS

# Install debug APK to connected device
./gradlew :composeApp:installDebug

# Uninstall from device
./gradlew :composeApp:uninstallDebug
```

---

## 11. Desktop JVM Packaging

```bash
# Run desktop app directly (development)
./gradlew :composeApp:run

# Package desktop distributable (all formats for current OS)
./gradlew :composeApp:packageDistributionForCurrentOS

# Package as standalone executable (no JRE bundled)
./gradlew :composeApp:packageUberJarForCurrentOS

# Package installers per platform (run on respective OS)
./gradlew :composeApp:packageMsi          # Windows MSI installer
./gradlew :composeApp:packageDmg          # macOS DMG installer
./gradlew :composeApp:packageDeb          # Linux DEB package
./gradlew :composeApp:packageRpm          # Linux RPM package

# Package executable without installer (portable)
./gradlew :composeApp:packageExe          # Windows .exe
./gradlew :composeApp:packageApp          # macOS .app bundle

# Release packaging (signed)
./gradlew :composeApp:packageReleaseDmg
./gradlew :composeApp:packageReleaseMsi

# Output location: composeApp/build/compose/binaries/main/
```

---

## 12. Clean Commands

```bash
# Clean ALL build outputs (full rebuild will be required)
./gradlew clean

# Clean specific module
./gradlew :shared:core:clean
./gradlew :shared:data:clean
./gradlew :composeApp:clean

# Clean + rebuild (most reliable fix for weird build errors)
./gradlew clean build

# Clean + rebuild with stacktrace
./gradlew clean build --stacktrace

# Delete Gradle caches (nuclear option — use when wrapper/plugin issues occur)
rm -rf ~/.gradle/caches/

# Delete local .gradle folder
rm -rf .gradle/
```

---

## 13. Composite / CI Pipeline Commands

These are the recommended command sequences for CI/CD and pre-commit validation.

### 🔵 Pre-Commit Check (fast — ~2 min)
```bash
./gradlew :shared:core:test \
          :shared:domain:test \
          :shared:security:test \
          --parallel --continue
```

### 🟢 Full Verification (before merging a PR — ~8 min)
```bash
./gradlew clean \
          test \
          lint \
          --parallel \
          --continue \
          --stacktrace
```

### 🟡 Domain & Business Logic Deep Test (critical path coverage)
```bash
./gradlew :shared:domain:test \
          :shared:data:test \
          :shared:security:test \
          :composeApp:feature:pos:test \
          :composeApp:feature:auth:test \
          --parallel --continue
```

### 🔴 Full CI Pipeline (mirrors GitHub Actions — ~15 min)
```bash
./gradlew clean \
          test \
          testDebugUnitTest \
          jvmTest \
          lint \
          assembleDebug \
          :composeApp:packageUberJarForCurrentOS \
          --parallel \
          --continue \
          --stacktrace
```

### 🏁 Release Build Pipeline (Android + Desktop)
```bash
# Step 1: Clean
./gradlew clean

# Step 2: Run all tests
./gradlew test --parallel --continue

# Step 3: Build Android release
./gradlew :composeApp:assembleRelease

# Step 4: Build Android App Bundle
./gradlew :composeApp:bundleRelease

# Step 5: Build Desktop distributable
./gradlew :composeApp:packageDistributionForCurrentOS
```

---

## 14. Module Quick Reference Map

| Gradle Path | Type | Description |
|---|---|---|
| `:shared:core` | KMP Library | Constants, extensions, Result type, logging |
| `:shared:domain` | KMP Library | Entities, use cases, repo interfaces |
| `:shared:data` | KMP Library | SQLDelight, Ktor, repository impls |
| `:shared:hal` | KMP Library | Hardware abstraction (printer, scanner, image) |
| `:shared:security` | KMP Library | Encryption, JWT, PIN hashing, RBAC |
| `:shared:seed` | KMP Library | Debug-only seed data (debugImplementation) |
| `:composeApp` | KMP App | Root app module (Android + Desktop) |
| `:composeApp:core` | KMP Library | BaseViewModel MVI base class |
| `:composeApp:designsystem` | KMP Library | ZyntaTheme, Zynta* components, tokens |
| `:composeApp:navigation` | KMP Library | NavGraph, type-safe routes, RBAC gating |
| `:composeApp:feature:auth` | KMP Library | Login, session, PIN lock, RBAC |
| `:composeApp:feature:dashboard` | KMP Library | Home KPI, charts, low-stock alerts |
| `:composeApp:feature:onboarding` | KMP Library | First-run wizard |
| `:composeApp:feature:pos` | KMP Library | POS screen, cart, barcode, payment |
| `:composeApp:feature:inventory` | KMP Library | Product CRUD, categories, stock |
| `:composeApp:feature:register` | KMP Library | Shift open/close, cash drawer |
| `:composeApp:feature:reports` | KMP Library | Sales, stock, CSV/PDF export |
| `:composeApp:feature:settings` | KMP Library | Store config, printer, tax, users |
| `:composeApp:feature:customers` | KMP Library | Customer management, loyalty, GDPR |
| `:composeApp:feature:staff` | KMP Library | Staff & role management |
| `:composeApp:feature:admin` | KMP Library | System health, audit log, DB maintenance |
| `:composeApp:feature:coupons` | KMP Library | Coupon & discount engine |
| `:composeApp:feature:expenses` | KMP Library | Expense tracking |
| `:composeApp:feature:media` | KMP Library | Image/media management |
| `:composeApp:feature:multistore` | KMP Library | Multi-store sync & switching |
| `:composeApp:feature:accounting` | KMP Library | E-Invoice, GL, chart of accounts, IRD |
| `:composeApp:feature:diagnostic` | KMP Library | Remote diagnostic session (scaffold) |
| `:androidApp` | Android App | Android application shell (AGP 9.0+ required) |
| `:tools:debug` | KMP Library | 6-tab in-app debug console |

---

## Tips & Troubleshooting

**Gradle daemon stuck?**
```bash
./gradlew --stop   # Stop all daemons
```

**Out of memory during build?**
```bash
# Add to gradle.properties:
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
```

**Parallel builds causing flaky tests?**
```bash
# Disable parallel for test runs if needed
./gradlew test --no-parallel
```

**Force re-download dependencies:**
```bash
./gradlew build --refresh-dependencies
```

**Check which tasks will run without executing (dry run):**
```bash
./gradlew build --dry-run
```

**Profile build performance:**
```bash
./gradlew build --profile
# Report: build/reports/profile/
```
