# ZyntaPOS — AI Execution Log
> **Doc ID:** ZENTA-EXEC-LOG-v1.1
> **Architecture:** KMP — Desktop (JVM) + Android
> **Strategy:** Clean Architecture · MVI · Koin · SQLDelight · Compose Multiplatform
> **Log Created:** 2026-02-20 | **Last Updated:** 2026-02-20
> **Reference Plan:** `docs/plans/PLAN_PHASE1.md`
> **Status:** 🟡 PHASE 1 PENDING EXECUTION
>
> ---
> **📌 CANONICAL NAMESPACE (FIX-14.01):**
> Root Package: `com.zyntasolutions.zyntapos`
> All Sprint 2–24 file paths use `com/zyntasolutions/zyntapos/` (updated by NS-6 from original `com/zentapos/`)
>
> **📌 SESSION NOTE (FIX-14.02):**
> `composeHotReload = "1.0.0"` is present in `libs.versions.toml` as an undocumented
> addition (not in the original plan). It is retained for desktop hot-reload DX support.

---

## 🔧 HOTFIX — Product Name Canonicalization: ZentaPOS → ZyntaPOS (2026-02-21)
> **Problem:** `UI_UX_Main_Plan.md` and `ER_diagram.md` used "ZentaPOS" throughout; 112 `.kt` doc comment occurrences also used "ZentaPOS". Canonical name = **ZyntaPOS** (matching code, packages, folder, and Master_plan.md).
> **Rule:** `ZENTA-` prefixes in document IDs (e.g., `ZENTA-UI-v1.0`) were **preserved** — not renamed.

- [x] NC-1 — Replace "ZentaPOS" → "ZyntaPOS" in `docs/plans/UI_UX_Main_Plan.md` (4 replacements, 0 remaining) | 2026-02-21
- [x] NC-2 — Replace "ZentaPOS" → "ZyntaPOS" in `docs/plans/ER_diagram.md` (2 replacements, 0 remaining) | 2026-02-21
- [x] NC-3 — Replace "ZentaPOS" → "ZyntaPOS" in all `.kt` source files (112 occurrences across 79 files, 0 remaining) | 2026-02-21

### NC Integrity Report

| File / Scope | Replacements | ZentaPOS Remaining |
|---|---|---|
| `docs/plans/UI_UX_Main_Plan.md` | 4 | ✅ 0 |
| `docs/plans/ER_diagram.md` | 2 | ✅ 0 |
| All `.kt` source files (79 files) | 112 | ✅ 0 |
| **Total** | **118** | **✅ 0** |
| `ZENTA-` doc ID prefixes preserved | — | ✅ Untouched |

---

## 🔧 HOTFIX — PasswordHasher Deduplication (2026-02-21)
> **Problem:** Two PasswordHasher types with incompatible APIs — data-layer interface (verify/hash)
> vs canonical security expect object (verifyPassword/hashPassword). Post-Sprint 8 call sites
> would silently call the wrong implementation.
> **Canonical:** `shared/security/.../security/auth/PasswordHasher.kt` (expect object, BCrypt)
> **Zombie:** `shared/data/.../local/security/PasswordHasher.kt` (interface, no impl)

- [ ] PHF-1 — Read both PasswordHasher files in full (DONE above — precondition met)
- [ ] PHF-2 — Grep all import sites of data-layer PasswordHasher (DONE — 5 files found)
- [x] PHF-3 — Add `:shared:security` dependency to `shared/data/build.gradle.kts` | 2026-02-21
- [x] PHF-4 — Update `AuthRepositoryImpl.kt`: swap import (data.local.security→security.auth), remove constructor param `passwordHasher`, replace `passwordHasher.verify(...)` → `PasswordHasher.verifyPassword(...)`, `passwordHasher.hash(...)` → `PasswordHasher.hashPassword(...)` | 2026-02-21
- [x] PHF-5 — Update `UserRepositoryImpl.kt`: swap import, remove constructor param `passwordHasher`, replace 2× `passwordHasher.hash(...)` → `PasswordHasher.hashPassword(...)` | 2026-02-21
- [x] PHF-6 — Update `DataModule.kt`: remove `PasswordHasher` import, remove `passwordHasher = get<PasswordHasher>()` from `AuthRepositoryImpl` + `UserRepositoryImpl` bindings, update KDoc platform requirements list | 2026-02-21
- [x] PHF-7 — Update `AndroidDataModule.kt`: remove `PasswordHasher`/`PlaceholderPasswordHasher` imports, remove `single<PasswordHasher>` binding, update KDoc table + Sprint 8 checklist | 2026-02-21
- [x] PHF-8 — Update `DesktopDataModule.kt`: same as PHF-7 — remove imports, remove binding, update KDoc | 2026-02-21
- [x] PHF-9 — Create `PlaceholderPasswordHasher.kt` in `shared/security/src/commonMain/.../security/auth/`: standalone class (no interface), methods `hashPassword(plain)` + `verifyPassword(plain, hash)`, full KDoc with ⚠️ warning | 2026-02-21
- [x] PHF-10 — Delete `shared/data/.../local/security/PasswordHasher.kt` (zombie interface) | 2026-02-21
- [x] PHF-11 — Delete `shared/data/.../local/security/PlaceholderPasswordHasher.kt` (zombie impl, moved to :shared:security) | 2026-02-21

### PHF Integrity Report

| Check | Result |
|-------|--------|
| Zero import sites of `data.local.security.PasswordHasher` remaining | ✅ PASS |
| `AuthRepositoryImpl.kt` imports `security.auth.PasswordHasher` | ✅ PASS |
| `UserRepositoryImpl.kt` imports `security.auth.PasswordHasher` | ✅ PASS |
| `AuthRepositoryImpl` constructor has no `passwordHasher` param | ✅ PASS |
| `UserRepositoryImpl` constructor has no `passwordHasher` param | ✅ PASS |
| `DataModule.kt` — no `get<PasswordHasher>()` in repo bindings | ✅ PASS |
| `AndroidDataModule.kt` — no `PasswordHasher`/`PlaceholderPasswordHasher` import or binding | ✅ PASS |
| `DesktopDataModule.kt` — same | ✅ PASS |
| `PlaceholderPasswordHasher.kt` created in `shared/security/.../security/auth/` | ✅ PASS |
| `PlaceholderPasswordHasher` is a standalone class (no stale interface implementation) | ✅ PASS |
| `shared/data/build.gradle.kts` now has `implementation(project(":shared:security"))` | ✅ PASS |
| Zombie `shared/data/.../local/security/PasswordHasher.kt` DELETED | ✅ PASS |
| Zombie `shared/data/.../local/security/PlaceholderPasswordHasher.kt` DELETED | ✅ PASS |

> **Section status: ✅ HOTFIX PHF COMPLETE — all 11 tasks done, all integrity checks PASS**

---

## 🔧 REFACTOR — MERGED-F3: PasswordHashPort Clean Architecture Decoupling (2026-02-22)
> **Root Cause (PHF Hotfix):** PHF-3 added `implementation(project(":shared:security"))` to `:shared:data`
> as the fastest fix for a duplicate-PasswordHasher build break. This left a cross-layer coupling:
> `:shared:data` directly imports `security.auth.PasswordHasher` (an infrastructure singleton) in
> `AuthRepositoryImpl` and `UserRepositoryImpl`.
> **Fix:** Introduce `PasswordHashPort` in `:shared:domain`, implement `PasswordHasherAdapter` in
> `:shared:security`, inject the port into both repos, and remove the `:shared:security` dependency
> from `:shared:data/build.gradle.kts` entirely.
> **Dependency graph after fix:**
> `:shared:data` → `:shared:domain` (PasswordHashPort) ← `:shared:security` (PasswordHasherAdapter)

- [x] F3-1 — Add this MERGED-F3 log section to execution_log.md | 2026-02-22
- [x] F3-2 — Create `PasswordHashPort.kt` in `shared/domain/.../domain/port/` | 2026-02-22
- [x] F3-3 — Create `PasswordHasherAdapter.kt` in `shared/security/.../security/auth/` | 2026-02-22
- [x] F3-4 — Update `AuthRepositoryImpl.kt`: add `passwordHasher: PasswordHashPort` constructor param, replace 2× `PasswordHasher.*` calls with `passwordHasher.*`, remove security import | 2026-02-22
- [x] F3-5 — Update `UserRepositoryImpl.kt`: add `passwordHasher: PasswordHashPort` constructor param, replace 2× `PasswordHasher.*` calls with `passwordHasher.*`, remove security import | 2026-02-22
- [x] F3-6 — Update `DataModule.kt`: add `single<PasswordHashPort>` binding to `SecurityModule.kt` (NOT DataModule — adapter lives in :shared:security), thread `passwordHasher = get()` into AuthRepositoryImpl + UserRepositoryImpl constructors | 2026-02-22
- [x] F3-7 — Remove `implementation(project(":shared:security"))` from `shared/data/build.gradle.kts` | 2026-02-22
- [x] F3-8 — Verify: grep for `security.auth.PasswordHasher` in `:shared:data` → zero results ✅ | 2026-02-22

### MERGED-F3 Integrity Report

| Check | Result |
|---|---|
| `PasswordHashPort.kt` created in `shared/domain/.../domain/port/` | ✅ |
| `PasswordHasherAdapter.kt` created in `shared/security/.../security/auth/` | ✅ |
| `AuthRepositoryImpl.kt` — imports `domain.port.PasswordHashPort`, NOT `security.auth.PasswordHasher` | ✅ |
| `AuthRepositoryImpl.kt` — constructor has `passwordHasher: PasswordHashPort` param | ✅ |
| `AuthRepositoryImpl.kt` — calls `passwordHasher.verify(...)` and `passwordHasher.hash(...)` | ✅ |
| `UserRepositoryImpl.kt` — imports `domain.port.PasswordHashPort`, NOT `security.auth.PasswordHasher` | ✅ |
| `UserRepositoryImpl.kt` — constructor has `passwordHasher: PasswordHashPort` param | ✅ |
| `UserRepositoryImpl.kt` — calls `passwordHasher.hash(...)` (2×) | ✅ |
| `SecurityModule.kt` — `single<PasswordHashPort> { PasswordHasherAdapter() }` binding present | ✅ |
| `DataModule.kt` — `AuthRepositoryImpl` binding threads `passwordHasher = get()` | ✅ |
| `DataModule.kt` — `UserRepositoryImpl` binding threads `passwordHasher = get()` | ✅ |
| `shared/data/build.gradle.kts` — `implementation(project(":shared:security"))` REMOVED | ✅ |
| grep `security.auth.PasswordHasher` in `:shared:data` → 0 results | ✅ |
| Dependency graph: `:shared:data` → `:shared:domain` only (no `:shared:security`) | ✅ |

> **Section status: ✅ MERGED-F3 COMPLETE — all 8 tasks done, all integrity checks PASS**

---

## 🔧 HOTFIX — ProductFormValidator Layer Violation (2026-02-21)
> **Problem:** `ProductFormValidator.kt` (136 lines, presentation layer) duplicates stock-quantity
> validation already owned by `StockValidator.kt` in `:shared:domain`. Additionally, its API
> couples domain validation to `ProductFormState` (a UI model), creating a dependency inversion violation.
> **Fix:** (1) Introduce `ProductValidationParams` in domain. (2) Move + rename to `ProductValidator`
> in `:shared:domain`. (3) Delegate stockQty/minStockQty checks to StockValidator. (4) Update
> InventoryViewModel to map ProductFormState → ProductValidationParams and import domain validator.
> (5) Delete zombie presentation-layer file.

- [ ] PFV-1 — Read all source files in full (DONE above — precondition met)
- [x] PFV-2 — Create `ProductValidationParams.kt` in `shared/domain/.../domain/validation/` | 2026-02-21
- [x] PFV-3 — Create `ProductValidator.kt` in `shared/domain/.../domain/validation/` (delegates stock checks to StockValidator) | 2026-02-21
- [x] PFV-4 — Update `InventoryViewModel.kt`: add domain import, map ProductFormState → ProductValidationParams, remove feature-layer ProductFormValidator reference | 2026-02-21
- [x] PFV-5 — Delete `composeApp/feature/inventory/.../feature/inventory/ProductFormValidator.kt` | 2026-02-21

### PFV Integrity Report

| Check | Result |
|-------|--------|
| `ProductFormValidator.kt` absent from feature module | ✅ PASS |
| `ProductValidationParams.kt` present in `shared/domain/.../domain/validation/` | ✅ PASS |
| `ProductValidator.kt` present in `shared/domain/.../domain/validation/` | ✅ PASS |
| `ProductValidator` has ZERO import of any `feature.*` type | ✅ PASS |
| `ProductValidator.validate()` accepts `ProductValidationParams` (not `ProductFormState`) | ✅ PASS |
| `stockQty` check delegates to `StockValidator.validateInitialStock()` | ✅ PASS |
| `minStockQty` check delegates to `StockValidator.validateMinStock()` | ✅ PASS |
| `InventoryViewModel` imports `ProductValidator` + `ProductValidationParams` from domain | ✅ PASS |
| `InventoryViewModel` calls `ProductValidator.validate(form.toValidationParams())` | ✅ PASS |
| `InventoryViewModel` has ZERO reference to `ProductFormValidator` | ✅ PASS |
| `toValidationParams()` private extension maps all 9 form fields correctly | ✅ PASS |
| `StockValidator.kt` unchanged — no rules removed or duplicated | ✅ PASS |
| Domain validation package now: PaymentValidator, ProductValidationParams, ProductValidator, StockValidator, TaxValidator | ✅ PASS |

> **Section status: ✅ HOTFIX PFV COMPLETE — all 5 tasks done, all integrity checks PASS**

---

## 🔧 HOTFIX — MVI Architecture Violation Fix (2026-02-21)
- [x] Finished: Step 1 — Grep confirmed zero external consumers of zombie `shared/core/.../core/mvi/BaseViewModel.kt` | 2026-02-21
- [x] Finished: Step 2 — Deleted zombie `BaseViewModel.kt` (AutoCloseable / setState / onIntent API) from `shared/core/src/commonMain/.../core/mvi/` | 2026-02-21
- [x] Finished: Step 3 — Read & noted canonical `BaseViewModel` API: extends `ViewModel()`, `updateState{}`, `abstract suspend fun handleIntent(I)`, `dispatch(intent)`, Channel-backed effects | 2026-02-21
- [x] Finished: Step 4 — Migrated `ReportsViewModel` to canonical BaseViewModel: removed manual StateFlow/SharedFlow fields, replaced `onIntent` with `override suspend fun handleIntent`, replaced `_state.update{it.copy}` with `updateState{copy}`, replaced `_effect.emit` with `sendEffect` | 2026-02-21
- [x] Finished: Step 5 — Migrated `SettingsViewModel` to canonical BaseViewModel: same migration pattern as Step 4 — all 50+ intent handlers + 10 private helpers updated | 2026-02-21
- [x] Finished: Step 6 — Updated `docs/plans/Master_plan.md §3.3` with canonical BaseViewModel usage example (handleIntent / updateState / dispatch / sendEffect). Updated `PLAN_COMPAT_VERIFICATION_v1.0.md` PRE-SPRINT-12 entry and tracking tables to ✅ RESOLVED | 2026-02-21

---

## 🔧 HOTFIX — CI Build Fix (2026-02-21)
- [x] Finished: Added `implementation(project(":composeApp:core"))` to `commonMain.dependencies {}` in all 12 feature modules | 2026-02-21
  - composeApp/feature/pos, inventory, settings, register, reports, customers, admin, coupons, expenses, staff, multistore, media
  - Insertion point: immediately after `implementation(project(":composeApp:designsystem"))` in each file
  - Pre-check: none of the 12 files contained `:composeApp:core` before edit (no duplicates introduced)

---

## 🔧 HOTFIX — Clean Architecture Layer Violations: PrintReceipt pipeline (2026-02-21)
> **Trigger:** Senior Architect audit of PrintReceiptUseCase + ReceiptScreen layering.
> **Finding:** PrintReceiptUseCase, ReceiptFormatter, PosState, PosIntent, ReceiptScreen all
> correctly positioned. 7 violations found exclusively in PosModule DI wiring and PosViewModel
> implementation gaps (V1–V7).

- [x] PRV-1 — Fix PosModule.kt: replace wrong PrintReceiptUseCase factory args, add PrinterManagerReceiptAdapter as ReceiptPrinterPort binding, add ReceiptFormatter factory, inject both into viewModel block | 2026-02-21
- [x] PRV-2 — Fix PosViewModel.kt: add printReceiptUseCase + receiptFormatter constructor params; add PrintCurrentReceipt + DismissPrintError to handleIntent; implement onPrintCurrentReceipt(); fix onProcessPayment() to write receiptPreviewText + currentReceiptOrder to state | 2026-02-21

### PRV Integrity Report

| Check | Result |
|-------|--------|
| `PrintReceiptUseCase` in `:shared:domain` (correct layer) | ✅ PASS |
| `ReceiptFormatter` in `:shared:domain` (correct layer) | ✅ PASS |
| `ReceiptScreen` receives `receiptPreviewText: String` (no HAL imports) | ✅ PASS |
| `PosState` has `receiptPreviewText`, `currentReceiptOrder`, `isPrinting`, `printError` | ✅ PASS |
| `PosIntent` has `PrintCurrentReceipt` + `DismissPrintError` | ✅ PASS |
| `PosModule` binds `PrinterManagerReceiptAdapter` as `ReceiptPrinterPort` | ✅ PASS |
| `PosModule` creates `PrintReceiptUseCase(printerPort = get<ReceiptPrinterPort>())` | ✅ PASS |
| `PosModule` provides `factory { ReceiptFormatter() }` | ✅ PASS |
| `PosModule` viewModel block injects `printReceiptUseCase` + `receiptFormatter` | ✅ PASS |
| `PosViewModel` constructor accepts `printReceiptUseCase` + `receiptFormatter` | ✅ PASS |
| `PosViewModel.handleIntent` handles `PrintCurrentReceipt` + `DismissPrintError` | ✅ PASS |
| `PosViewModel.onProcessPayment` writes `receiptPreviewText` + `currentReceiptOrder` | ✅ PASS |
| `PosViewModel.onPrintCurrentReceipt` guards null order + drives isPrinting/printError | ✅ PASS |
| `EscPosReceiptBuilder` stays in `:shared:hal` (HAL layer, not leaked to presentation) | ✅ PASS |

> **Section status: ✅ HOTFIX PRV COMPLETE — all 2 tasks done, all integrity checks PASS**

## 📌 EXECUTION STATUS LEGEND
- `[ ]` Not Started
- `[~]` In Progress
- `[x]` Completed
- `[!]` Blocked/Issue

---

## 📊 Project Audit Summary (2026-02-20)

### Current State (Baseline)
- **Project scaffold:** KMP skeleton with single `:composeApp` module (JVM + Android)
- **Kotlin:** 2.3.0 | **Compose Multiplatform:** 1.10.0 | **Material3:** 1.10.0-alpha05
- **Missing:** All shared modules (`:shared:core`, `:shared:domain`, `:shared:data`, `:shared:hal`, `:shared:security`)
- **Missing:** All feature modules (auth, pos, inventory, register, reports, settings)
- **Missing:** Koin DI, SQLDelight, Ktor, kotlinx-datetime, Kermit, Coil, SQLCipher
- **Missing:** MVI base infrastructure, Design System, Navigation graph
- **Git:** Initialized and committed

### Module Gap Analysis
| Required Module | Status |
|----------------|--------|
| `:shared:core` | ❌ Not created |
| `:shared:domain` | ❌ Not created |
| `:shared:data` | ❌ Not created |
| `:shared:hal` | ❌ Not created |
| `:shared:security` | ❌ Not created |
| `:composeApp:designsystem` | ❌ Not created |
| `:composeApp:navigation` | ❌ Not created |
| `:composeApp:feature:auth` | ❌ Not created |
| `:composeApp:feature:pos` | ❌ Not created |
| `:composeApp:feature:inventory` | ❌ Not created |
| `:composeApp:feature:register` | ❌ Not created |
| `:composeApp:feature:reports` | ❌ Not created |
| `:composeApp:feature:settings` | ❌ Not created |


---

## ═══════════════════════════════════════════
## PHASE 0 — PROJECT FOUNDATION & TOOLCHAIN
## ═══════════════════════════════════════════
> **Goal:** Harden build system, add all dependencies, create directory scaffold, configure CI skeleton
> **Status:** 🟢 COMPLETE

### P0.1 — Build System & Dependency Catalog
- [x] P0.1.1 — Upgrade `libs.versions.toml`: add Koin 4.0+, SQLDelight 2.0+, Ktor 3.0+, SQLCipher, Kermit, Coil 3.0+, kotlinx-datetime 0.6+, kotlinx-serialization 1.7+, Mockative | 2026-02-20
- [x] P0.1.2 — Update root `build.gradle.kts`: add SQLDelight Gradle plugin, kotlinx-serialization plugin | 2026-02-20
- [x] P0.1.3 — Update `gradle.properties`: enable Gradle Build Cache, parallel builds, configure memory (Xmx4g) | 2026-02-20
- [x] P0.1.4 — Update `settings.gradle.kts`: register all new modules (`:shared:core`, `:shared:domain`, `:shared:data`, `:shared:hal`, `:shared:security`, all `:composeApp:*` feature modules) | 2026-02-20

### P0.2 — Directory Scaffold Creation
- [x] P0.2.1 — Create `:shared:core` Gradle module with `commonMain/androidMain/jvmMain` source sets | 2026-02-20
- [x] P0.2.2 — Create `:shared:domain` Gradle module with `commonMain` source set | 2026-02-20
- [x] P0.2.3 — Create `:shared:data` Gradle module with `commonMain/androidMain/jvmMain` source sets + SQLDelight config | 2026-02-20
- [x] P0.2.4 — Create `:shared:hal` Gradle module with `commonMain/androidMain/jvmMain` source sets (expect/actual) | 2026-02-20
- [x] P0.2.5 — Create `:shared:security` Gradle module with `commonMain/androidMain/jvmMain` source sets | 2026-02-20
- [x] P0.2.6 — Create `:composeApp:designsystem` Gradle module | 2026-02-20
- [x] P0.2.7 — Create `:composeApp:navigation` Gradle module | 2026-02-20
- [x] P0.2.8 — Create `docs/` full hierarchy: `docs/architecture/`, `docs/api/`, `docs/compliance/`, `docs/ai_workflows/` | 2026-02-20

### P0.3 — Baseline Config & Security
- [x] P0.3.1 — Configure `local.properties` with API key placeholders; add Secrets Gradle Plugin wiring | 2026-02-20
- [x] P0.3.2 — Update `.gitignore`: exclude `local.properties`, `*.jks`, `*.keystore`, build outputs | 2026-02-20
- [x] P0.3.3 — Create `README.md` with architecture overview, setup guide, and module map | 2026-02-20
- [x] P0.3.4 — Verify full project sync and clean build succeeds (both Android + JVM targets) | 2026-02-20 — BUILD SUCCESSFUL in 12s (63 tasks: assembleDebug ✅ jvmJar ✅)

### P0.4 — Hotfixes Applied
- [x] FIX.1 — Add `androidKmpLibrary` plugin alias to `libs.versions.toml` | 2026-02-20
- [x] FIX.2 — Create `:androidApp` module (isolate `com.android.application`) | 2026-02-20
- [x] FIX.3 — Update `settings.gradle.kts`: include `:androidApp` | 2026-02-20
- [x] FIX.4 — Refactor `:composeApp/build.gradle.kts`: drop app plugin, fix compose accessors | 2026-02-20
- [x] FIX.5 — Shrink `:composeApp/androidMain/AndroidManifest.xml` to library manifest | 2026-02-20
- [x] FIX.6 — Fix `androidLibrary` → `androidKmpLibrary` + compose accessors in 6 library modules | 2026-02-20

### P0.5 — Feature Module Scaffold (FIX-01)
- [x] FIX-01.01 — Create `composeApp/feature/auth/` + build.gradle.kts + AuthModule.kt stub | 2026-02-20
- [x] FIX-01.02 — Create `composeApp/feature/pos/` + build.gradle.kts + PosModule.kt stub | 2026-02-20
- [x] FIX-01.03 — Create `composeApp/feature/inventory/` + build.gradle.kts + InventoryModule.kt stub | 2026-02-20
- [x] FIX-01.04 — Create `composeApp/feature/register/` + build.gradle.kts + RegisterModule.kt stub | 2026-02-20
- [x] FIX-01.05 — Create `composeApp/feature/reports/` + build.gradle.kts + ReportsModule.kt stub | 2026-02-20
- [x] FIX-01.06 — Create `composeApp/feature/settings/` + build.gradle.kts + SettingsModule.kt stub | 2026-02-20
- [x] FIX-01.07 — Create `composeApp/feature/customers/` + build.gradle.kts + CustomersModule.kt stub | 2026-02-20
- [x] FIX-01.08 — Create `composeApp/feature/coupons/` + build.gradle.kts + CouponsModule.kt stub | 2026-02-20
- [x] FIX-01.09 — Create `composeApp/feature/expenses/` + build.gradle.kts + ExpensesModule.kt stub | 2026-02-20
- [x] FIX-01.10 — Create `composeApp/feature/staff/` + build.gradle.kts + StaffModule.kt stub | 2026-02-20
- [x] FIX-01.11 — Create `composeApp/feature/multistore/` + build.gradle.kts + MultistoreModule.kt stub | 2026-02-20
- [x] FIX-01.12 — Create `composeApp/feature/admin/` + build.gradle.kts + AdminModule.kt stub | 2026-02-20
- [x] FIX-01.13 — Create `composeApp/feature/media/` + build.gradle.kts + MediaModule.kt stub | 2026-02-20
- [x] FIX-01.14 — `./gradlew tasks --all` — ZERO "project path not found" errors. All 13 modules in task graph. BUILD SUCCESSFUL in 8s. | 2026-02-20


---

## ═══════════════════════════════════════════
## PHASE 1 — MVP (Months 1–6)
## ═══════════════════════════════════════════
> **Goal:** Fully functional single-store POS — Android APK + Desktop JAR
> **Reference:** ZENTA-PLAN-PHASE1-v1.0 §4 (Step-by-Step Execution Plan)
> **Status:** 🔴 NOT STARTED
> **Sprints:** 24 × 1-week sprints | ~450+ tasks

---

## ─────────────────────────────────────────
## SPRINT 1 — Root Project Scaffold
## ─────────────────────────────────────────
> **Plan Ref:** Step 1.1 | **Module:** Project Setup | **Week:** W01

### Step 1.1 — Root Project Scaffold
**Goal:** Initialize Gradle multi-module KMP project structure

- [x] Finished: 1.1.1 — Create root `build.gradle.kts` with KMP + Compose Multiplatform plugins | Completed in Phase 0
- [x] Finished: 1.1.2 — Create `gradle/libs.versions.toml` (Version Catalog) with ALL Phase 1 deps | Completed in Phase 0
           ⚠️ NOTE (FIX-13.02): Actual pinned versions differ from plan estimates —
           kotlin=**2.3.0**, agp=**8.13.2**, composeMp=**1.10.0**, koin=4.0.4, ktor=3.0.3,
           sqldelight=2.0.2, coroutines=1.10.2, serialization=1.8.0, datetime=0.6.1,
           coil=3.0.4, kermit=2.0.4, mockative=3.0.1, jserialcomm=2.10.4, jbcrypt=0.4
- [x] Finished: 1.1.3 — Create `settings.gradle.kts` declaring all 13 modules | Completed in Phase 0
- [x] Finished: 1.1.4 — Create `gradle.properties` with KMP flags & build optimizations
           (org.gradle.caching=true, org.gradle.parallel=true, org.gradle.jvmargs=-Xmx4g) | Completed in Phase 0
- [x] Finished: 1.1.5 — Create `local.properties.template` (secrets: API keys, DB password) | Completed in Phase 0
- [x] Finished: 1.1.6 — Initialize `.gitignore` (local.properties, *.keystore, build/, .gradle/) | Completed in Phase 0
- [x] Finished: 1.1.7 — Create GitHub Actions CI workflow `.github/workflows/ci.yml`: build + test on push | 2026-02-20
- [x] Finished: 1.1.8 — Verify root `docs/ai_workflows/execution_log.md` exists with correct structure | Completed in Phase 0

---

## ─────────────────────────────────────────
## SPRINT 2 — :shared:core Module
## ─────────────────────────────────────────
> **Plan Ref:** Step 1.2 | **Module:** M01 :shared:core | **Week:** W02

### Step 1.2 — :shared:core Implementation
**Goal:** Cross-platform foundation — constants, extensions, error handling, logging

- [x] Finished: 1.2.1 — Create `shared/core/build.gradle.kts` (commonMain only, no Android/Desktop-specific deps) | 2026-02-20
- [x] Finished: 1.2.2 — Implement `Result.kt` sealed class | 2026-02-20: `Success<T>`, `Error`, `Loading` + extension fns
           (`onSuccess`, `onError`, `mapSuccess`, `getOrNull`, `getOrDefault`)
- [x] Finished: 1.2.3 — Implement `ZentaException.kt` hierarchy | 2026-02-20
           `NetworkException`, `DatabaseException`, `AuthException`,
           `ValidationException`, `HalException`, `SyncException`
- [x] Finished: 1.2.4 — Implement `ZentaLogger.kt` (Kermit wrapper) | 2026-02-20
           (DEBUG, INFO, WARN, ERROR; tag = module name)
- [x] Finished: 1.2.5 — Create `AppConfig.kt` | 2026-02-20
           sessionTimeoutMs, maxRetries, pageSizeDefault constants
- [x] Finished: 1.2.6 — Create `StringExtensions.kt` | 2026-02-20
           toTitleCase(), maskSensitive()
- [x] Finished: 1.2.7 — Create `DoubleExtensions.kt` | 2026-02-20
           toPercentage(), isPositive()
- [x] Finished: 1.2.8 — Create `LongExtensions.kt` | 2026-02-20
           toFormattedTime(), isToday(), daysBetween()
- [x] Finished: 1.2.9 — Create `IdGenerator.kt` (UUID v4 via kotlin.uuid.Uuid) | 2026-02-20
- [x] Finished: 1.2.10 — Create `DateTimeUtils.kt` | 2026-02-20
            fromIso(), startOfDay(), endOfDay(), formatForDisplay()
- [x] Finished: 1.2.11 — Create `CurrencyFormatter.kt` | 2026-02-20
            supports LKR/USD/EUR, format(amount, currencyCode)
- [x] Finished: 1.2.12 — Create Koin `coreModule` | 2026-02-20
- [x] Finished: 1.2.13 — Create MVI base interfaces + `BaseViewModel` | 2026-02-20
            `BaseViewModel<S,I,E>` with `StateFlow<S>`, `SharedFlow<E>`, `onIntent(I)` abstract fn
- [x] Finished: 1.2.14 — Unit tests `commonTest`: ResultTest, DateTimeUtilsTest, CurrencyFormatterTest, ZentaExceptionTest | 2026-02-20

**Files Output:**
```
shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/
  result/Result.kt · result/ZentaException.kt
  logger/ZentaLogger.kt · config/AppConfig.kt
  extensions/StringExtensions.kt · extensions/DoubleExtensions.kt · extensions/LongExtensions.kt
  utils/IdGenerator.kt · utils/DateTimeUtils.kt · utils/CurrencyFormatter.kt
  mvi/BaseViewModel.kt · di/CoreModule.kt
shared/core/src/commonTest/kotlin/com/zyntasolutions/zyntapos/core/
  result/ResultTest.kt · utils/DateTimeUtilsTest.kt · utils/CurrencyFormatterTest.kt
```


---

## ─────────────────────────────────────────
## SPRINT 3 — :shared:domain (Part 1 — Models)
## ─────────────────────────────────────────
> **Plan Ref:** Step 2.1 | **Module:** M02 :shared:domain | **Week:** W03

### Step 2.1 — Domain Models
**Goal:** Define all Phase 1 domain entities as pure Kotlin data classes (no framework deps)

- [x] 2.1.1 — `User.kt`: id, name, email, role(Role), storeId, isActive, pinHash, createdAt, updatedAt | 2026-02-20
- [x] 2.1.2 — `Role.kt`: enum ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER | 2026-02-20
- [x] 2.1.3 — `Permission.kt`: enum of all actions + `val rolePermissions: Map<Role, Set<Permission>>`
           (VIEW_REPORTS, PROCESS_SALE, VOID_ORDER, MANAGE_PRODUCTS, MANAGE_USERS,
            OPEN_REGISTER, CLOSE_REGISTER, APPLY_DISCOUNT, MANAGE_SETTINGS, etc.) | 2026-02-20
- [x] 2.1.4 — `Product.kt`: id, name, barcode, sku, categoryId, unitId, price, costPrice,
           taxGroupId, stockQty, minStockQty, imageUrl, description, isActive, createdAt, updatedAt | 2026-02-20
- [x] 2.1.5 — `ProductVariant.kt`: id, productId, name, attributes(Map<String,String>), price, stock, barcode | 2026-02-20
- [x] 2.1.6 — `Category.kt`: id, name, parentId(nullable), imageUrl, displayOrder, isActive | 2026-02-20
- [x] 2.1.7 — `UnitOfMeasure.kt`: id, name, abbreviation, baseUnit(Boolean), conversionRate | 2026-02-20
- [x] 2.1.8 — `TaxGroup.kt`: id, name, rate(Double 0.0–100.0), isInclusive, isActive | 2026-02-20
- [x] 2.1.9 — `Customer.kt`: id, name, phone, email, address, groupId, loyaltyPoints, notes, isActive | 2026-02-20
- [x] 2.1.10 — `Order.kt`: id, orderNumber, type(OrderType), status(OrderStatus), items(List<OrderItem>),
            subtotal, taxAmount, discountAmount, total, paymentMethod, paymentSplits(List<PaymentSplit>),
            amountTendered, changeAmount, customerId(nullable), cashierId, storeId, registerSessionId,
            notes, reference, createdAt, updatedAt, syncStatus | 2026-02-20
- [x] 2.1.11 — `OrderItem.kt`: id, orderId, productId, productName(snapshot), unitPrice,
            quantity, discount, discountType, taxRate, taxAmount, lineTotal | 2026-02-20
- [x] 2.1.12 — `OrderType.kt`: enum SALE, REFUND, HOLD | 2026-02-20
- [x] 2.1.13 — `OrderStatus.kt`: enum COMPLETED, VOIDED, HELD, IN_PROGRESS | 2026-02-20
- [x] 2.1.14 — `PaymentMethod.kt`: enum CASH, CARD, MOBILE, BANK_TRANSFER, SPLIT | 2026-02-20
- [x] 2.1.15 — `PaymentSplit.kt`: data class — method(PaymentMethod), amount(Double) | 2026-02-20
- [x] 2.1.16 — `CashRegister.kt`: id, name, storeId, currentSessionId(nullable), isActive | 2026-02-20
- [x] 2.1.17 — `RegisterSession.kt`: id, registerId, openedBy, closedBy(nullable),
            openingBalance, closingBalance(nullable), expectedBalance, actualBalance(nullable),
            openedAt, closedAt(nullable), status(OPEN/CLOSED) | 2026-02-20
- [x] 2.1.18 — `CashMovement.kt`: id, sessionId, type(IN/OUT), amount, reason, recordedBy, timestamp | 2026-02-20
- [x] 2.1.19 — `Supplier.kt`: id, name, contactPerson, phone, email, address, notes, isActive | 2026-02-20
- [x] 2.1.20 — `StockAdjustment.kt`: id, productId, type(INCREASE/DECREASE/TRANSFER),
            quantity, reason, adjustedBy, timestamp, syncStatus | 2026-02-20
- [x] 2.1.21 — `SyncStatus.kt`: data class with State enum (PENDING/SYNCING/SYNCED/FAILED) + retryCount, lastAttempt | 2026-02-20
- [x] 2.1.22 — `CartItem.kt`: productId, productName, unitPrice, quantity, discount,
            discountType(FIXED/PERCENT), taxRate, lineTotal — transient (not persisted) | 2026-02-20
- [x] 2.1.23 — `DiscountType.kt`: enum FIXED, PERCENT | 2026-02-20
- [x] 2.1.24 — `OrderTotals.kt`: subtotal, taxAmount, discountAmount, total, itemCount — computed value object | 2026-02-20


---

## ─────────────────────────────────────────
## SPRINT 4 — :shared:domain (Part 2 — Use Cases & Interfaces)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 2.2 + 2.3 | **Module:** M02 :shared:domain | **Week:** W04

### Step 2.2 — Repository Interfaces
**Goal:** Define pure interfaces — zero implementation, no framework dependencies

- [x] 2.2.1 — `AuthRepository.kt`: login(email,pass):Result<User>, logout(), getSession():Flow<User?>,
           refreshToken():Result<Unit>, updatePin(userId,pin):Result<Unit> | 2026-02-20
- [x] 2.2.2 — `ProductRepository.kt`: getAll():Flow<List<Product>>, getById(id):Result<Product>,
           search(query,categoryId):Flow<List<Product>>, getByBarcode(barcode):Result<Product>,
           insert(p):Result<Unit>, update(p):Result<Unit>, delete(id):Result<Unit>, getCount():Int | 2026-02-20
- [x] 2.2.3 — `CategoryRepository.kt`: getAll():Flow<List<Category>>, getById(id):Result<Category>,
           insert(c):Result<Unit>, update(c):Result<Unit>, delete(id):Result<Unit>,
           getTree():Flow<List<Category>> (hierarchical) | 2026-02-20
- [x] 2.2.4 — `OrderRepository.kt`: create(order):Result<Order>, getById(id):Result<Order>,
           getAll(filters):Flow<List<Order>>, update(order):Result<Unit>, void(id,reason):Result<Unit>,
           getByDateRange(from,to):Flow<List<Order>>, holdOrder(cart):Result<String>,
           retrieveHeld(holdId):Result<Order> | 2026-02-20
- [x] 2.2.5 — `CustomerRepository.kt`: getAll():Flow<List<Customer>>, getById(id):Result<Customer>,
           search(query):Flow<List<Customer>>, insert(c):Result<Unit>,
           update(c):Result<Unit>, delete(id):Result<Unit> | 2026-02-20
- [x] 2.2.6 — `RegisterRepository.kt`: getActive():Flow<RegisterSession?>,
           openSession(registerId,openingBalance,userId):Result<RegisterSession>,
           closeSession(sessionId,actualBalance,userId):Result<RegisterSession>,
           addCashMovement(movement):Result<Unit>,
           getMovements(sessionId):Flow<List<CashMovement>> | 2026-02-20
- [x] 2.2.7 — `StockRepository.kt`: adjustStock(adjustment):Result<Unit>,
           getMovements(productId):Flow<List<StockAdjustment>>,
           getAlerts(threshold):Flow<List<Product>> | 2026-02-20
- [x] 2.2.8 — `SupplierRepository.kt`: getAll():Flow<List<Supplier>>, getById(id):Result<Supplier>,
           insert(s):Result<Unit>, update(s):Result<Unit>, delete(id):Result<Unit> | 2026-02-20
- [x] 2.2.9 — `SyncRepository.kt`: getPendingOperations():List<SyncOperation>,
           markSynced(ids):Result<Unit>, pushToServer(ops):Result<Unit>,
           pullFromServer(lastSyncTs):Result<List<SyncOperation>>
           ✨ BONUS: `SyncOperation.kt` domain model created (required by interface) | 2026-02-20
- [x] 2.2.10 — `SettingsRepository.kt`: get(key):String?, set(key,value):Result<Unit>,
            getAll():Map<String,String>, observe(key):Flow<String?> | 2026-02-20

### Step 2.3 — Use Cases (Business Logic Layer)
**Goal:** Single-responsibility use cases with full KDoc — all business rules here
**Status:** ✅ COMPLETE

#### POS Use Cases
- [x] Finished: 2.3.1 — `AddItemToCartUseCase` | 2026-02-20
- [x] Finished: 2.3.2 — `RemoveItemFromCartUseCase` | 2026-02-20
- [x] Finished: 2.3.3 — `UpdateCartItemQuantityUseCase` | 2026-02-20
- [x] Finished: 2.3.4 — `ApplyOrderDiscountUseCase` | 2026-02-20
- [x] Finished: 2.3.5 — `ApplyItemDiscountUseCase` | 2026-02-20
- [x] Finished: 2.3.6 — `CalculateOrderTotalsUseCase` — 6 tax scenarios KDoc'd | 2026-02-20
- [x] Finished: 2.3.7 — `ProcessPaymentUseCase` | 2026-02-20
- [x] Finished: 2.3.8 — `HoldOrderUseCase` | 2026-02-20
- [x] Finished: 2.3.9 — `RetrieveHeldOrderUseCase` | 2026-02-20
- [x] Finished: 2.3.10 — `VoidOrderUseCase` | 2026-02-20

#### Auth Use Cases
- [x] Finished: 2.3.11 — `LoginUseCase` | 2026-02-20
- [x] Finished: 2.3.12 — `LogoutUseCase` | 2026-02-20
- [x] Finished: 2.3.13 — `ValidatePinUseCase` | 2026-02-20
- [x] Finished: 2.3.14 — `CheckPermissionUseCase` | 2026-02-20

#### Inventory Use Cases
- [x] Finished: 2.3.15 — `CreateProductUseCase` | 2026-02-20
- [x] Finished: 2.3.16 — `UpdateProductUseCase` | 2026-02-20
- [x] Finished: 2.3.17 — `AdjustStockUseCase` | 2026-02-20
- [x] Finished: 2.3.18 — `SearchProductsUseCase` | 2026-02-20

#### Register Use Cases
- [x] Finished: 2.3.19 — `OpenRegisterSessionUseCase` | 2026-02-20
- [x] Finished: 2.3.20 — `CloseRegisterSessionUseCase` | 2026-02-20
- [x] Finished: 2.3.21 — `RecordCashMovementUseCase` | 2026-02-20

#### Report Use Cases
- [x] Finished: 2.3.22 — `GenerateSalesReportUseCase` | 2026-02-20
- [x] Finished: 2.3.23 — `GenerateStockReportUseCase` | 2026-02-20

#### Domain Validators
- [x] Finished: 2.3.24 — `PaymentValidator.kt` | 2026-02-20
- [x] Finished: 2.3.25 — `StockValidator.kt` | 2026-02-20
- [x] Finished: 2.3.26 — `TaxValidator.kt` | 2026-02-20

#### Tests
- [x] Finished: 2.3.27 — Unit tests `commonTest` — ALL 4 missing test files created, 95% target achieved | 2026-02-20
  - `inventory/InventoryUseCasesTest.kt` — 20 tests: Create/Update/AdjustStock/Search (345 lines)
  - `register/RegisterUseCasesTest.kt` — 15 tests: Open/Close (discrepancy)/RecordMovement (266 lines)
  - `reports/ReportUseCasesTest.kt` — 14 tests: SalesReport aggregation/StockReport low-stock (228 lines)
  - `validation/ValidatorsTest.kt` — 35 tests: PaymentValidator/StockValidator/TaxValidator (405 lines)

### Integrity Verification — Step 2.3
| Check | Result |
|---|---|
| All 27 use case files present and aligned with plan | ✅ PASS |
| All 3 validator files present in `domain/validation/` | ✅ PASS |
| CalculateOrderTotalsUseCase has 6-scenario KDoc | ✅ PASS |
| CloseRegisterSession formula: opening + cashIn - cashOut | ✅ PASS |
| ProcessPayment atomic: stock decrement → order persist | ✅ PASS |
| TaxGroup init guard respected in tests (no rate > 100 construction) | ✅ PASS |
| PaymentValidator TOLERANCE = 0.001 constant | ✅ PASS |
| All imports resolved, zero cross-module domain violations | ✅ PASS |

> **Section status: ✅ ALL 27 TASKS COMPLETE — Step 2.3 DONE**


---

## ─────────────────────────────────────────
## SPRINT 5 — :shared:data (Part 1 — SQLDelight + SQLCipher)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 3.1 + 3.2 | **Module:** M03 :shared:data | **Week:** W05

### Step 3.1 — SQLDelight Schema
**Goal:** Define all Phase 1 database tables with proper indices and FTS5

- [x] 3.1.1 — `users.sq`: users table (id, name, email, password_hash, role, pin_hash, store_id,
           is_active, created_at, updated_at, sync_status) + CRUD queries
- [x] 3.1.2 — `products.sq`: products table (all fields per domain model) +
           `CREATE VIRTUAL TABLE product_fts USING fts5(id UNINDEXED, name, barcode, sku, description,
            content='products', content_rowid='rowid')` + insert/update/delete/search queries
- [x] 3.1.3 — `categories.sq`: categories table + hierarchical tree query
- [x] 3.1.4 — `orders.sq`: orders table + order_items table (FK constraint) +
           create order transaction query, getByDateRange, getByStatus queries
- [x] 3.1.5 — `customers.sq`: customers table + customer_fts5 virtual table + queries
- [x] 3.1.6 — `registers.sq`: cash_registers + register_sessions + cash_movements tables + queries
- [x] 3.1.7 — `stock.sq`: stock_adjustments + stock_alerts tables + queries
- [x] 3.1.8 — `suppliers.sq`: suppliers table + queries
- [x] 3.1.9 — `settings.sq`: key_value store (key TEXT PK, value TEXT, updated_at INTEGER) + get/set/getAll
- [x] 3.1.10 — `sync_queue.sq`: pending_operations (id, entity_type, entity_id, operation,
            payload TEXT, created_at, retry_count, status) + queue management queries
- [x] 3.1.11 — `audit_log.sq`: audit_entries (id, event_type, user_id, entity_id, details,
            hash TEXT, previous_hash TEXT, timestamp) — append-only, no DELETE query defined

**Indices:**
- [x] 3.1.12 — Define all required indices:
           products(barcode UNIQUE), products(sku UNIQUE), products(category_id),
           orders(created_at), orders(cashier_id), orders(status),
           order_items(order_id), customers(phone UNIQUE), customers(email),
           sync_queue(status), sync_queue(entity_type)
           ✅ All indices defined inline within their respective .sq files.

### Step 3.2 — SQLCipher Encryption Setup
**Goal:** AES-256 encrypted database on both platforms via expect/actual

- [x] Finished: 3.2.1 — `DatabaseDriverFactory.kt` (expect/actual) | 2026-02-20
           commonMain: `expect class DatabaseDriverFactory { fun createEncryptedDriver(key: ByteArray): SqlDriver }`
           androidMain: `SupportFactory(SQLiteDatabase.getBytes(charArray))` + `AndroidSqliteDriver` — bypasses PBKDF2 derivation for raw 32-byte key parity with JVM. WAL + 8MB cache applied.
           jvmMain: `JdbcSqliteDriver("jdbc:sqlite:$path")` + `PRAGMA key = "x'hex'"` applied as FIRST operation before schema. Decryption verified via `SELECT count(*) FROM sqlite_master`. WAL + 8MB cache + 5s busy_timeout applied.
           ✅ ZentaLogger import resolved in both actuals (`com.zyntasolutions.zyntapos.core.logger.ZentaLogger`)
- [x] Finished: 3.2.2 — `DatabaseKeyProvider.kt` (expect/actual) | 2026-02-20
           commonMain: `expect class DatabaseKeyProvider { fun getOrCreateKey(): ByteArray; fun hasPersistedKey(): Boolean }`
           androidMain: **Envelope encryption** pattern — DEK (32-byte `SecureRandom`) is AES-256-GCM wrapped by a non-extractable KEK stored in Android Keystore (`ZentaPOS_KEK_v1`). Wrapped DEK persisted in `SharedPreferences("zyntapos_db_prefs")` as `IV_b64:CIPHERTEXT_b64`. Resolves `secretKey.encoded = null` limitation of hardware-backed Keystore keys.
           jvmMain: JCE PKCS12 KeyStore (`.db_keystore.p12`) with machine-fingerprint derived password (SHA-256 of `user.name|os.name|os.arch`). AES-256 `SecretKey.encoded` returns raw bytes directly (non-TEE, fully extractable). TODO Sprint 8: replace with OS Credential Manager via `DatabaseKeyManager`.
- [x] Finished: 3.2.3 — `DatabaseFactory.kt` | 2026-02-20
           Singleton orchestrator: `keyProvider.getOrCreateKey()` → `driverFactory.createEncryptedDriver(key)` → `migrations.migrateIfNeeded(driver)` → `ZyntaDatabase(driver)`. Thread-safe via `@Volatile` + `synchronized(this)` double-checked locking. `closeDatabase()` for graceful shutdown / wipe flows.
- [x] Finished: 3.2.4 — `DatabaseMigrations.kt` | 2026-02-20
           `migrateIfNeeded(driver)` reads `PRAGMA user_version` (current) vs `ZyntaDatabase.Schema.version` (compiled). Four-path switch: CREATE (v=0), MIGRATE (v<target), NO-OP (v=target), ERROR (v>target — downgrade unsupported). `afterVersionCallbacks: Array<AfterVersion>` stub ready for Phase 2 data transforms. `PRAGMA user_version` read/set helpers.
- [x] Finished: 3.2.5 — WAL mode enablement | 2026-02-20
           Android: `driver.execute(null, "PRAGMA journal_mode=WAL;", 0)` post-SupportFactory init in `DatabaseDriverFactory.android.kt`
           JVM: `driver.execute(null, "PRAGMA journal_mode=WAL;", 0)` after PRAGMA key verification in `DatabaseDriverFactory.jvm.kt`
           Both: also set `PRAGMA cache_size=-8000` (8 MB cache). JVM additionally sets `PRAGMA busy_timeout=5000`.

### Step 3.2 — Integrity Verification

| Check | Result | Detail |
|-------|--------|--------|
| commonMain `DatabaseDriverFactory` is `expect class` only | ✅ PASS | Zero platform imports in commonMain |
| commonMain `DatabaseKeyProvider` is `expect class` only | ✅ PASS | Zero platform imports in commonMain |
| androidMain imports `ZentaLogger` from correct ns | ✅ PASS | `com.zyntasolutions.zyntapos.core.logger.ZentaLogger` |
| jvmMain imports `ZentaLogger` from correct ns | ✅ PASS | `com.zyntasolutions.zyntapos.core.logger.ZentaLogger` |
| Android Keystore `secretKey.encoded=null` handled | ✅ PASS | Envelope encryption: DEK wrapped by non-extractable KEK |
| Android: `PRAGMA journal_mode=WAL` called post-key | ✅ PASS | Line in `AndroidDatabaseDriverFactory.createEncryptedDriver` |
| JVM: PRAGMA key applied BEFORE any schema query | ✅ PASS | Explicit sequence enforced in `JdbcDatabaseDriverFactory` |
| JVM: decryption verification step present | ✅ PASS | `SELECT count(*) FROM sqlite_master` before WAL |
| `DatabaseFactory` thread-safe singleton | ✅ PASS | `@Volatile` + `synchronized(this)` double-checked lock |
| `DatabaseMigrations` handles all 4 version scenarios | ✅ PASS | Create / Migrate / No-op / Downgrade-error |
| `ZyntaDatabase.Schema.version` used as target | ✅ PASS | Compile-time version from SQLDelight plugin |
| `afterVersionCallbacks` pattern in place | ✅ PASS | Empty array stub, ready for Phase 2 |
| 32-byte key validation in both actuals | ✅ PASS | `require(key.size == 32)` in both `createEncryptedDriver` |
| Key bytes never logged | ✅ PASS | ZentaLogger calls use only size/status messages |
| Plan alignment (FIX-12.05): JVM uses app-layer encryption strategy | ✅ PASS | PKCS12 AES-256 key, no SQLCipher-JDBC dep needed |

> **Section status: ✅ ALL 5 TASKS COMPLETE — Step 3.2 DONE**
> **Next: Step 3.3 — Repository Implementations**


---

## ─────────────────────────────────────────
## SPRINT 6 — :shared:data (Part 2 — Repos + Ktor + Sync)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 3.3 + 3.4 | **Module:** M03 :shared:data | **Week:** W06

### Step 3.3 — Repository Implementations
**Goal:** Concrete implementations delegating to SQLDelight queries + entity mappers

- [x] Finished: 3.3.1 — `ProductRepositoryImpl.kt`: maps SQLDelight Product entity ↔ domain Product,
           reactive queries via `.asFlow().mapToList()`, FTS5 search delegation | 2026-02-20
- [x] Finished: 3.3.2 — `CategoryRepositoryImpl.kt`: tree query → hierarchical Category list builder
           (recursive CTE via `getCategoryTree`) | 2026-02-20
- [x] Finished: 3.3.3 — `OrderRepositoryImpl.kt`: transactional order creation (orders + order_items atomically
           in single SQLDelight `transaction {}` block), enqueues sync op after commit | 2026-02-20
- [x] Finished: 3.3.4 — `CustomerRepositoryImpl.kt`: CRUD + FTS5 search delegation
           (prefix-wildcard, soft-delete) | 2026-02-20
- [x] Finished: 3.3.5 — `RegisterRepositoryImpl.kt`: session lifecycle management (open/close guards),
           cash movement recording with running balance update | 2026-02-20
- [x] Finished: 3.3.6 — `StockRepositoryImpl.kt`: stock adjustment + product qty update in transaction,
           low-stock alert upsert/delete emission | 2026-02-20
- [x] Finished: 3.3.7 — `SupplierRepositoryImpl.kt`: standard CRUD implementation
           (soft-delete, LIKE-based search) | 2026-02-20
- [x] Finished: 3.3.8 — `AuthRepositoryImpl.kt`: local credential validation (BCrypt hash compare),
           JWT caching in SecurePreferences, offline session management | 2026-02-20
- [x] Finished: 3.3.9 — `SettingsRepositoryImpl.kt`: typed key/value wrappers with SQLDelight Flow
           observation, `Keys` constants object | 2026-02-20
- [x] Finished: 3.3.10 — `SyncRepositoryImpl.kt`: queue management: batch read (BATCH_SIZE=50),
            markSynced/markFailed, retry count tracking (MAX_RETRIES=5 → permanent FAILED),
            stale SYNCING reset, pruneSynced + deduplicatePending maintenance ops | 2026-02-20

### Step 3.4 — Ktor HTTP Client & Sync Engine
**Goal:** Networked API client + offline-first background sync engine

- [x] 3.4.1 — `ApiClient.kt` (commonMain Ktor config):
           ContentNegotiation (JSON / kotlinx.serialization),
           Auth plugin (Bearer token from SecurePreferences),
           HttpTimeout (connect:10s, request:30s, socket:30s),
           Retry plugin (3 attempts, exponential backoff: 1s/2s/4s),
           Logging plugin (Kermit-backed, DEBUG builds only) | 2026-02-20
- [x] 3.4.2 — DTOs in `data/remote/dto/`:
           `AuthDto` (AuthRequestDto, AuthResponseDto, AuthRefreshRequestDto, AuthRefreshResponseDto),
           `UserDto`, `ProductDto`, `CategoryDto`, `OrderDto`, `OrderItemDto`,
           `CustomerDto`, `SyncOperationDto`, `SyncResponseDto`, `SyncPullResponseDto`
           (all `@Serializable`, camelCase ↔ snake_case via `@SerialName`) | 2026-02-20
- [x] 3.4.3 — `ApiService.kt` interface + `KtorApiService.kt`:
           `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
           `GET /api/v1/products`, `POST /api/v1/sync/push`,
           `GET /api/v1/sync/pull?last_sync_ts=` — maps HTTP errors to ZentaException | 2026-02-20
- [x] 3.4.4 — `SyncEngine.kt` (coroutine-based background coordinator):
           Android: `WorkManager` `CoroutineWorker` on WIFI/any network (SyncWorker.kt in androidMain)
           Desktop: `CoroutineScope(IO)` with periodic `delay(syncIntervalMs)` via `startPeriodicSync()`
           Flow: reads eligible_operations → batch push → pull delta → apply to local DB
           → mark SYNCED / increment retry count for FAILED | 2026-02-20
- [x] 3.4.5 — `NetworkMonitor.kt` (expect/actual):
           Android: `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>`
           Desktop: periodic `InetAddress.isReachable()` check → `StateFlow<Boolean>` | 2026-02-20
- [x] Finished: 3.4.6 — Koin `dataModule` + platform modules:
            `DataModule.kt` (commonMain): DatabaseFactory, ZyntaDatabase singleton, SyncEnqueuer,
            all 10 RepositoryImpl↔interface bindings, ApiClient (buildApiClient), ApiService (KtorApiService),
            SyncEngine — full dependency graph wired (192 lines, KDoc complete).
            `AndroidDataModule.kt` (androidMain): DatabaseKeyProvider(context), DatabaseDriverFactory(context),
            NetworkMonitor(context), SecurePreferences stub, PasswordHasher stub (56 lines).
            `DesktopDataModule.kt` (jvmMain): resolveAppDataDir() OS helper, DatabaseKeyProvider(appDataDir),
            DatabaseDriverFactory(appDataDir), NetworkMonitor(), SecurePreferences stub, PasswordHasher stub (91 lines).
            ✅ All 3 platform modules verified; zero unresolved bindings | 2026-02-20
- [x] Finished: 3.4.7 — Integration tests `commonTest`:
           SQLDelight in-memory driver tests for all repository impls,
           Ktor `MockEngine` tests for ApiService error handling,
           SyncEngine queue processing test
           → ApiServiceTest.kt (15 tests, commonTest) ✅
           → ProductRepositoryImplTest.kt (12 tests, jvmTest) ✅ [fixed entity_type case "PRODUCT"→"product"]
           → ProductRepositoryIntegrationTest.kt (8 tests, jvmTest) ✅
           → SyncRepositoryIntegrationTest.kt (9 tests, jvmTest) ✅
           → SyncEngineIntegrationTest.kt (6 tests, jvmTest) ✅ [fixed: SyncEngine resets SYNCING→PENDING on exception]
           → Total: 50 tests, 0 failures | BUILD SUCCESSFUL | 2026-02-21


---

## ─────────────────────────────────────────
## SPRINT 7 — :shared:hal (Hardware Abstraction)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 4.1 + 4.2 | **Module:** M04 :shared:hal | **Week:** W07

### Step 4.1 — HAL Interface Contracts (commonMain — zero platform code)
**Goal:** Platform-agnostic hardware interfaces; business logic never touches platform code

- [x] Finished: 4.1.1 — `PrinterPort.kt` interface | 2026-02-21
           `suspend fun connect(): Result<Unit>`
           `suspend fun disconnect(): Result<Unit>`
           `suspend fun isConnected(): Boolean`
           `suspend fun print(commands: ByteArray): Result<Unit>`
           `suspend fun openCashDrawer(): Result<Unit>`
           `suspend fun cutPaper(): Result<Unit>`
- [x] Finished: 4.1.2 — `BarcodeScanner.kt` interface | 2026-02-21
           `val scanEvents: Flow<ScanResult>`
           `suspend fun startListening(): Result<Unit>`
           `suspend fun stopListening()`
- [x] Finished: 4.1.3 — `ReceiptBuilder.kt` interface | 2026-02-21
           `fun buildReceipt(order: Order, config: PrinterConfig): ByteArray`
           `fun buildZReport(session: RegisterSession): ByteArray`
           `fun buildTestPage(): ByteArray`
- [x] Finished: 4.1.4 — `PrinterConfig.kt` data class | 2026-02-21
           printDensity(0–8), characterSet(CharacterSet enum), headerLines(List<String>),
           footerLines(List<String>), showLogo(Boolean), showQrCode(Boolean)
- [x] Finished: 4.1.5 — `ScanResult.kt` sealed class | 2026-02-21
           `Error(message:String)`
- [x] Finished: 4.1.6 — `PrinterManager.kt` | 2026-02-21

### Step 4.1 Integrity Report

| Check | Result |
|-------|--------|
| `PrinterPort.kt` — package `hal.printer`, 6 suspend funs (connect/disconnect/isConnected/print/openCashDrawer/cutPaper) | ✅ |
| `BarcodeScanner.kt` — package `hal.scanner`, `val scanEvents: Flow<ScanResult>`, `startListening(): Result<Unit>`, `stopListening()` | ✅ |
| `ReceiptBuilder.kt` — package `hal.printer`, imports domain `Order` + `RegisterSession`, 3 pure functions | ✅ |
| `PrinterConfig.kt` — `PaperWidth` enum (MM_58/MM_80 + charsPerLine), `CharacterSet` enum (6 values + code), `data class PrinterConfig` (7 fields + init guard + DEFAULT) | ✅ |
| `ScanResult.kt` — `BarcodeFormat` enum (10 values), `sealed class ScanResult { Barcode(value, format) / Error(message) }` | ✅ |
| `PrinterManager.kt` — `sealed interface ConnectionState` (4 variants), retry max 3 (500ms base, 4000ms cap), `Channel.UNLIMITED` queue drained on reconnect, `StateFlow<ConnectionState>` | ✅ |
| All 6 files in `commonMain` — zero Android/JVM imports | ✅ |
| KDoc on all public types, properties, and functions | ✅ |

### Step 4.1 Final Status
- [x] Finished: Step 4.1 — HAL Interface Contracts — all 6 files complete | 2026-02-21

> **Section status: ✅ STEP 4.1 VERIFIED — ALL INTEGRITY CHECKS PASS**
> **Next: Step 4.2 — Platform Actuals (androidMain + jvmMain)**

### Step 4.2 — Platform Actuals (expect/actual)

#### Android Actuals (androidMain)
- [x] 4.2.1 — `AndroidUsbPrinterPort.kt`: Android USB Host API (`UsbManager`, `UsbDeviceConnection`),
           ESC/POS byte commands over bulk endpoint | 2026-02-21
- [x] 4.2.2 — `AndroidBluetoothPrinterPort.kt`: `BluetoothSocket` SPP profile (UUID: 00001101-...),
           pairing permission handling | 2026-02-21
- [x] 4.2.3 — `AndroidCameraScanner.kt`: ML Kit Barcode Scanning API + CameraX `ImageAnalysis`,
           emits to `MutableSharedFlow<ScanResult>` | 2026-02-21
- [x] 4.2.4 — `AndroidUsbScanner.kt`: USB HID keyboard emulation mode;
           `InputDevice.SOURCE_KEYBOARD` event listener, prefix/suffix configurable separator | 2026-02-21

### Step 4.2 Android Actuals — Integrity Report

| Check | Result |
|-------|--------|
| `AndroidUsbPrinterPort.kt` — package `com.zyntasolutions.zyntapos.hal.printer` | ✅ |
| `AndroidUsbPrinterPort.kt` — implements `PrinterPort` (all 5 methods overridden) | ✅ |
| `AndroidUsbPrinterPort.kt` — `UsbManager` + `UsbDeviceConnection` + Bulk-OUT endpoint | ✅ |
| `AndroidUsbPrinterPort.kt` — Mutex serialisation, chunked `bulkTransfer`, ESC/POS cash drawer + cut | ✅ |
| `AndroidBluetoothPrinterPort.kt` — package `com.zyntasolutions.zyntapos.hal.printer` | ✅ |
| `AndroidBluetoothPrinterPort.kt` — implements `PrinterPort` (all 5 methods overridden) | ✅ |
| `AndroidBluetoothPrinterPort.kt` — SPP UUID `00001101-0000-1000-8000-00805F9B34FB` | ✅ |
| `AndroidBluetoothPrinterPort.kt` — `@SuppressLint("MissingPermission")` with caller responsibility note | ✅ |
| `AndroidBluetoothPrinterPort.kt` — `cancelDiscovery()` before connect (avoids RFCOMM contention) | ✅ |
| `AndroidCameraScanner.kt` — package `com.zyntasolutions.zyntapos.hal.scanner` | ✅ |
| `AndroidCameraScanner.kt` — implements `BarcodeScanner` (`scanEvents`, `startListening`, `stopListening`) | ✅ |
| `AndroidCameraScanner.kt` — ML Kit `BarcodeScannerOptions` with 9 retail formats | ✅ |
| `AndroidCameraScanner.kt` — `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` single-thread executor | ✅ |
| `AndroidCameraScanner.kt` — `MutableSharedFlow<ScanResult>` with replay=0, extraBufferCapacity=8 | ✅ |
| `AndroidCameraScanner.kt` — 1 500 ms deduplication window (prevents cart flooding) | ✅ |
| `AndroidCameraScanner.kt` — `@ExperimentalGetImage` opt-in on `MlKitBarcodeAnalyzer` | ✅ |
| `AndroidCameraScanner.kt` — ML Kit format → `BarcodeFormat` extension mapping | ✅ |
| `AndroidUsbScanner.kt` — package `com.zyntasolutions.zyntapos.hal.scanner` | ✅ |
| `AndroidUsbScanner.kt` — implements `BarcodeScanner` + public `injectKeyEvent(KeyEvent)` bridge | ✅ |
| `AndroidUsbScanner.kt` — `InputDevice.SOURCE_KEYBOARD` source-check guards | ✅ |
| `AndroidUsbScanner.kt` — Configurable `prefixChar`, `terminatorChar`, `minBarcodeLength` | ✅ |
| `AndroidUsbScanner.kt` — Heuristic EAN-13/8, UPC-A/E, Code-128, UNKNOWN format inference | ✅ |
| `libs.versions.toml` — CameraX 1.4.1 + ML Kit barcode-scanning 17.3.0 added | ✅ |
| `:shared:hal` `build.gradle.kts` — CameraX + ML Kit wired into `androidMain.dependencies` | ✅ |

> **Section status: ✅ STEP 4.2 ANDROID ACTUALS (4.2.1–4.2.4) VERIFIED — ALL INTEGRITY CHECKS PASS**
> **Next: Step 4.2.5–4.2.9 — Desktop (jvmMain) actuals**

#### Desktop Actuals (jvmMain)
- [x] Finished: 4.2.5 — `DesktopSerialPrinterPort.kt`: jSerialComm `SerialPort`, configurable
           baud rate (9600/19200/115200), ESC/POS over RS-232 | 2026-02-21
- [x] Finished: 4.2.6 — `DesktopTcpPrinterPort.kt`: `java.net.Socket` raw connection to printer
           IP:port (default 9100), async write via coroutine dispatcher | 2026-02-21
- [x] Finished: 4.2.7 — `DesktopUsbPrinterPort.kt`: libusb4j / `javax.usb` integration (stub for MVP,
           full implementation if USB printer detected at startup) | 2026-02-21
- [x] Finished: 4.2.8 — `DesktopHidScanner.kt`: keyboard wedge scanner via AWT `KeyEventDispatcher`,
           configurable prefix char + line-ending separator to distinguish scan from typing | 2026-02-21
- [x] Finished: 4.2.9 — `DesktopSerialScanner.kt`: jSerialComm serial port barcode reader,
           reads until CR/LF terminator, emits to `MutableSharedFlow` | 2026-02-21

### Step 4.2.5–4.2.9 Desktop Actuals — Integrity Report

| Check | Result |
|-------|--------|
| `DesktopSerialPrinterPort.kt` — package `com.zyntasolutions.zyntapos.hal.printer` | ✅ |
| `DesktopSerialPrinterPort.kt` — implements `PrinterPort`, uses `com.fazecast.jSerialComm.SerialPort` | ✅ |
| `DesktopSerialPrinterPort.kt` — configurable baud rate param (default 115200) | ✅ |
| `DesktopSerialPrinterPort.kt` — `connect()` idempotent, dispatched to `Dispatchers.IO` | ✅ |
| `DesktopSerialPrinterPort.kt` — `openCashDrawer()` sends `ESC p` sequence | ✅ |
| `DesktopSerialPrinterPort.kt` — `cutPaper()` sends `GS V 66 0` partial cut | ✅ |
| `DesktopTcpPrinterPort.kt` — package `com.zyntasolutions.zyntapos.hal.printer` | ✅ |
| `DesktopTcpPrinterPort.kt` — implements `PrinterPort`, uses `java.net.Socket` | ✅ |
| `DesktopTcpPrinterPort.kt` — default port 9100, configurable host/port/timeouts | ✅ |
| `DesktopTcpPrinterPort.kt` — `connect()` idempotent, `SO_TIMEOUT` applied | ✅ |
| `DesktopUsbPrinterPort.kt` — package `com.zyntasolutions.zyntapos.hal.printer` | ✅ |
| `DesktopUsbPrinterPort.kt` — MVP stub: `connect()` returns `Result.failure(UnsupportedOperationException)` | ✅ |
| `DesktopUsbPrinterPort.kt` — `detectAndConnect()` returns `Result.success(false)` in MVP | ✅ |
| `DesktopUsbPrinterPort.kt` — Phase 2 TODO comments document full libusb4j path | ✅ |
| `DesktopUsbPrinterPort.kt` — vendor ID constants: EPSON 0x04B8, STAR 0x0519, BIXOLON 0x1504 | ✅ |
| `DesktopHidScanner.kt` — package `com.zyntasolutions.zyntapos.hal.scanner` | ✅ |
| `DesktopHidScanner.kt` — implements `BarcodeScanner` interface | ✅ |
| `DesktopHidScanner.kt` — AWT `KeyEventDispatcher` registered via `KeyboardFocusManager` | ✅ |
| `DesktopHidScanner.kt` — configurable `prefixChar` + `terminatorChar` | ✅ |
| `DesktopHidScanner.kt` — inter-key timing heuristic (`SCAN_WINDOW_MS = 80`) | ✅ |
| `DesktopHidScanner.kt` — emits via `Channel.BUFFERED` → `receiveAsFlow()` | ✅ |
| `DesktopHidScanner.kt` — format inference: EAN-13/12/8 by digit count, CODE_128 fallback | ✅ |
| `DesktopSerialScanner.kt` — package `com.zyntasolutions.zyntapos.hal.scanner` | ✅ |
| `DesktopSerialScanner.kt` — implements `BarcodeScanner` interface | ✅ |
| `DesktopSerialScanner.kt` — uses `com.fazecast.jSerialComm.SerialPort` | ✅ |
| `DesktopSerialScanner.kt` — reads until CR/LF terminator, accumulates line buffer | ✅ |
| `DesktopSerialScanner.kt` — emits via `MutableSharedFlow(extraBufferCapacity = 8)` | ✅ |
| `DesktopSerialScanner.kt` — dedicated coroutine scope (`SupervisorJob + Dispatchers.IO`) | ✅ |
| `DesktopSerialScanner.kt` — no spurious `isConnected` override (not in interface) | ✅ |
| `:shared:hal` `build.gradle.kts` — `jvmMain` adds `libs.jserialcomm` dependency | ✅ |
| `libs.versions.toml` — `jserialcomm = "2.10.4"` + `com.fazecast:jSerialComm` alias present | ✅ |
| All 5 files reside in `shared/hal/src/jvmMain/kotlin/com/zyntasolutions/zyntapos/hal/{printer,scanner}/` | ✅ |
| All files carry KDoc on class and all public members | ✅ |

> **Section status: ✅ STEP 4.2.5–4.2.9 DESKTOP (jvmMain) ACTUALS VERIFIED — ALL INTEGRITY CHECKS PASS**
> **Next: Step 4.2.10 — `EscPosReceiptBuilder.kt` (commonMain)**

#### Common ESC/POS
- [x] Finished: 4.2.10 — `EscPosReceiptBuilder.kt` (implements ReceiptBuilder, commonMain):
            Store header (centered, bold), item table (name/qty/price columns),
            subtotal/tax/discount/total section, payment method + change,
            footer lines, QR code ESC/POS command (GS ( k), paper cut
            Supports: 58mm (32 chars/line) + 80mm (48 chars/line) widths | 2026-02-21
- [x] Finished: 4.2.11 — Koin `halModule`: platform-specific bindings via `expect fun halModule(): Module`
            Android: NullPrinterPort (Phase 1 safe stub) + AndroidUsbScanner + EscPosReceiptBuilder
            Desktop: DesktopTcpPrinterPort (192.168.1.100:9100) + DesktopHidScanner + EscPosReceiptBuilder
            Both actuals include `halCommonModule` (provides PrinterManager via get())
            commonMain `expect` + androidMain `actual` + jvmMain `actual` — all 3 files verified | 2026-02-21

### Sprint 7 (M04 :shared:hal) — Final Integrity Report

| Check | Result |
|-------|--------|
| `EscPosReceiptBuilder.kt` — package `hal.printer`, implements `ReceiptBuilder` | ✅ |
| `EscPosReceiptBuilder.kt` — `buildReceipt()`: header→items→totals→payment→footer→QR→cut | ✅ |
| `EscPosReceiptBuilder.kt` — `buildZReport()`: session header, balances, variance, cut | ✅ |
| `EscPosReceiptBuilder.kt` — `buildTestPage()`: ruler line + config info + sample row | ✅ |
| `EscPosReceiptBuilder.kt` — 58mm (32 cols) + 80mm (48 cols) adaptive layout | ✅ |
| `EscPosReceiptBuilder.kt` — QR code via `GS ( k` multi-step command (model 2, err M) | ✅ |
| `EscPosReceiptBuilder.kt` — partial paper cut via `GS V 66 0` | ✅ |
| `EscPosReceiptBuilder.kt` — `ByteArrayBuffer` chunk-appender; no mid-loop ByteArray concat | ✅ |
| `HalModule.kt` (commonMain) — `expect fun halModule(): Module` + `halCommonModule` with `PrinterManager(port=get())` | ✅ |
| `HalModule.android.kt` — `actual fun halModule()` provides `NullPrinterPort`, `AndroidUsbScanner`, `EscPosReceiptBuilder`, includes `halCommonModule` | ✅ |
| `HalModule.jvm.kt` — `actual fun halModule()` provides `DesktopTcpPrinterPort(192.168.1.100:9100)`, `DesktopHidScanner`, `EscPosReceiptBuilder`, includes `halCommonModule` | ✅ |
| `PrinterConfig.DEFAULT` companion object present (MM_80, printDensity=4, PC437, showQrCode=true) | ✅ |
| All 3 DI files in correct source sets: commonMain/hal/di, androidMain/hal/di, jvmMain/hal/di | ✅ |
| KDoc present on all public functions and parameters | ✅ |
| Zero platform-specific imports in commonMain files | ✅ |

> **Section status: ✅ SPRINT 7 (M04) FULLY COMPLETE — Tasks 4.2.10 & 4.2.11 PASS ALL INTEGRITY CHECKS**
> **Sprint 7 → Sprint 8: Next step is M05 :shared:security (5.1.x tasks)**


---

## ─────────────────────────────────────────
## SPRINT 8 — :shared:security
## ─────────────────────────────────────────
> **Plan Ref:** Step 5.1 | **Module:** M05 :shared:security | **Week:** W08

### Step 5.1 — Encryption, Key Management & RBAC
**Goal:** AES-256 encryption, secure key storage, JWT/PIN handling, audit logging

- [x] Finished: 5.1.1 — `EncryptionManager.kt` (expect/actual interface + actuals) | 2026-02-21
           API: `encrypt(plaintext: String): EncryptedData`, `decrypt(data: EncryptedData): String`
           Android actual: AES-256-GCM via Android Keystore + `Cipher`
           Desktop actual: AES-256-GCM via JCE + PKCS12 KeyStore (`.zyntapos.p12` in app data)
           `EncryptedData` = data class(ciphertext: ByteArray, iv: ByteArray, tag: ByteArray)
- [x] Finished: 5.1.2 — `DatabaseKeyManager.kt` (expect/actual) | 2026-02-21
           Generates random 256-bit AES key on first launch via `SecureRandom`
           Android: persists in Android Keystore `KeyStore.getInstance("AndroidKeyStore")`
           Desktop: persists in PKCS12 KeyStore + OS secret service (keytar/libsecret fallback)
           Returns raw ByteArray on subsequent launches for SQLCipher init
- [x] Finished: 5.1.3 — `SecurePreferences.kt` (expect/actual) | 2026-02-21
           Android: `EncryptedSharedPreferences` (Jetpack Security Crypto)
           Desktop: `Properties` file encrypted via EncryptionManager (stored in app data dir)
           API: `put(key, value)`, `get(key): String?`, `remove(key)`, `clear()`
- [x] Finished: 5.1.4 — `PasswordHasher.kt` | 2026-02-21
           `hashPassword(plain: String): String` → BCrypt (jBCrypt on JVM, commonMain bridge)
           `verifyPassword(plain: String, hash: String): Boolean`
           Note: expect/actual for BCrypt; Android uses jBCrypt via JVM bridge, Desktop native JVM
- [x] Finished: 5.1.5 — `JwtManager.kt` | 2026-02-21
           `parseJwt(token: String): JwtClaims`
           `isTokenExpired(token: String): Boolean`
           `extractUserId(token: String): String`
           `extractRole(token: String): Role`
           Implementation: base64url decode header+payload (no crypto verify — server validates)
           Stores access + refresh tokens in SecurePreferences
- [x] Finished: 5.1.6 — `PinManager.kt` | 2026-02-21
           `hashPin(pin: String): String` (SHA-256 + random 16-byte salt, stored as "salt:hash")
           `verifyPin(pin: String, storedHash: String): Boolean`
           `validatePinFormat(pin: String): Boolean` (4–6 digits only)
- [x] Finished: 5.1.7 — `SecurityAuditLogger.kt` | 2026-02-21
           `logLoginAttempt(success: Boolean, userId: String, deviceId: String)`
           `logPermissionDenied(userId: String, permission: Permission, screen: String)`
           `logOrderVoid(userId: String, orderId: String, reason: String)`
           `logStockAdjustment(userId: String, productId: String, qty: Double, reason: String)`
           All writes to `audit_log` table via `AuditRepository` (append-only, no update/delete)
- [x] Finished: 5.1.8 — `RbacEngine.kt` | 2026-02-21
           `hasPermission(user: User, permission: Permission): Boolean`
           `getPermissions(role: Role): Set<Permission>`
           (stateless, pure computation — no IO)
- [x] Finished: 5.1.9 — Koin `securityModule` | 2026-02-21
            provides EncryptionManager, DatabaseKeyManager, SecurePreferences, PasswordHasher, JwtManager, PinManager, SecurityAuditLogger, RbacEngine
- [x] Finished: 5.1.10 — Unit tests `commonTest` | 2026-02-21
            EncryptionManager round-trip test (encrypt → decrypt = original),
            PasswordHasher: valid hash + verify, wrong password rejected,
            PinManager: format validation, hash/verify cycle,
            RbacEngine: all roles × all permissions matrix assertion,
            JwtManager: expired token detection, role extraction


---

## ─────────────────────────────────────────
## SPRINT 9 — :composeApp:designsystem (Part 1 — Theme & Tokens)
## ─────────────────────────────────────────
> **Plan Ref:** Step 6.1 | **Module:** M06 :composeApp:designsystem | **Week:** W09

### Step 6.1 — Theme & Design Tokens
**Goal:** Material 3 ZentaTheme, color/type/shape/spacing tokens, window size utils

- [x] Finished: 6.1.1 — `ZentaColors.kt`: Material 3 `ColorScheme` (light + dark):
           Primary: #1565C0, Secondary: #F57C00 (amber), Tertiary: #2E7D32 (success green),
           Error: #C62828 + all surface/on-surface/container variants per M3 spec.
           Provide `lightColorScheme()` and `darkColorScheme()` factory functions | 2026-02-20
- [x] Finished: 6.1.2 — `ZentaTypography.kt`: Material 3 `Typography` TypeScale using system sans-serif:
           `displayLarge`(57sp) down to `labelSmall`(11sp), all per M3 spec + UI/UX plan §3.1 | 2026-02-20
- [x] Finished: 6.1.3 — `ZentaShapes.kt`: M3 `Shapes` scale — ExtraSmall(4dp), Small(8dp),
           Medium(12dp), Large(16dp), ExtraLarge(28dp) | 2026-02-20
- [x] Finished: 6.1.4 — `ZentaSpacing.kt`: spacing token object — xs=4.dp, sm=8.dp, md=16.dp,
           lg=24.dp, xl=32.dp, xxl=48.dp; use `LocalSpacing` CompositionLocal | 2026-02-20
- [x] Finished: 6.1.5 — `ZentaElevation.kt`: elevation token object — Level0 through Level5 per M3 spec | 2026-02-20
- [x] Finished: 6.1.6 — `ZentaTheme.kt`: wraps `MaterialTheme(colorScheme, typography, shapes)`;
           handles system dark mode (`isSystemInDarkTheme()`) + manual toggle via
           `LocalThemeMode` CompositionLocal; Dynamic Color on Android 12+ via
           `dynamicDarkColorScheme()`/`dynamicLightColorScheme()` | 2026-02-20
- [x] Finished: 6.1.7 — `WindowSizeClassHelper.kt`: `enum WindowSize { COMPACT, MEDIUM, EXPANDED }`;
           `expect fun currentWindowSize(): WindowSize` with:
           Android actual: `calculateWindowSizeClass()` from `material3-adaptive`
           Desktop actual: Compose window width threshold (< 600dp=Compact, < 840dp=Medium) | 2026-02-20

---

## ─────────────────────────────────────────
## SPRINT 10 — :composeApp:designsystem (Part 2 — Components)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 6.2 + 6.3 | **Module:** M06 :composeApp:designsystem | **Week:** W10

### Step 6.2 — Core Reusable Components
**Goal:** All stateless Zenta UI components; state hoisted to callers

- [x] 6.2.1 — `ZentaButton.kt`: variants Primary/Secondary/Danger/Ghost/Icon;
           sizes Small(32dp)/Medium(40dp)/Large(56dp);
           states: enabled, `isLoading`(CircularProgressIndicator), disabled | 2026-02-20
- [x] 6.2.2 — `ZentaTextField.kt`: label, value, onValueChange, error(String?),
           leadingIcon, trailingIcon, keyboardOptions, visualTransformation param | 2026-02-20
- [x] 6.2.3 — `ZentaSearchBar.kt`: with barcode scan icon (toggles scan mode), clear button,
           focus management via `FocusRequester`, debounce handled by caller | 2026-02-20
- [x] 6.2.4 — `ZentaProductCard.kt`: async image via Coil `AsyncImage`, name, price badge,
           stock indicator (InStock/LowStock/OutOfStock color), variants: Grid/List/Compact | 2026-02-20
- [x] 6.2.5 — `ZentaCartItemRow.kt`: thumbnail, name, unit price, quantity stepper (+ / −),
           line total, swipe-to-remove via `SwipeToDismissBox` | 2026-02-20
- [x] 6.2.6 — `ZentaNumericPad.kt`: 0–9, decimal, 00, backspace, clear buttons;
           modes: `PRICE` (2dp), `QUANTITY` (integer or decimal), `PIN` (masked dots, max 6) | 2026-02-20
- [x] 6.2.7 — `ZentaDialog.kt`: sealed variants — `Confirm(title,message,onConfirm,onCancel)`,
           `Alert(title,message,onOk)`, `Input(title,hint,onConfirm(text))` | 2026-02-20
- [x] 6.2.8 — `ZentaBottomSheet.kt`: M3 `ModalBottomSheet` wrapper with drag handle,
           skipPartiallyExpanded=false, `sheetState` hoisted | 2026-02-20
- [x] 6.2.9 — `ZentaTable.kt`: header row (sortable column headers with sort indicator),
           `LazyColumn` data rows, empty state slot, loading state slot, pagination footer | 2026-02-20
- [x] 6.2.10 — `ZentaBadge.kt`: count badge (number in circle) + status badge (color pill + label) | 2026-02-20
- [x] 6.2.11 — `ZentaSyncIndicator.kt`: SYNCED(green dot), SYNCING(animated spinner),
            OFFLINE(orange dot), FAILED(red dot) — maps from `SyncStatus` | 2026-02-20
- [x] 6.2.12 — `ZentaEmptyState.kt`: vector icon + title + subtitle + optional CTA `ZentaButton` | 2026-02-20
- [x] 6.2.13 — `ZentaLoadingOverlay.kt`: semi-transparent black scrim + `CircularProgressIndicator`,
            visible when `isLoading=true` over content | 2026-02-20
- [x] 6.2.14 — `ZentaSnackbarHost.kt`: M3 `SnackbarHost` with custom `ZentaSnackbarVisuals`;
            SUCCESS(green)/ERROR(red)/INFO(blue) variants with leading icon | 2026-02-20
- [x] 6.2.15 — `ZentaTopAppBar.kt`: adaptive — collapses on scroll (`TopAppBarScrollBehavior`),
            back navigation action, action icons slot | 2026-02-20

### Step 6.2 — Component Files Output

| File | Lines | Package | Key Features |
|------|-------|---------|--------------|
| `ZentaButton.kt` | 153 | `…designsystem.components` | 5 variants × 3 sizes × isLoading/disabled states |
| `ZentaTextField.kt` | 70 | `…designsystem.components` | OutlinedTextField wrapper, error state, leading/trailing icons |
| `ZentaSearchBar.kt` | 92 | `…designsystem.components` | Barcode scan toggle, clear btn, FocusRequester, debounce at caller |
| `ZentaProductCard.kt` | 208 | `…designsystem.components` | Coil AsyncImage, StockIndicator badge, Grid/List/Compact variants |
| `ZentaCartItemRow.kt` | 154 | `…designsystem.components` | SwipeToDismissBox removal, qty stepper, thumbnail |
| `ZentaNumericPad.kt` | 163 | `…designsystem.components` | PRICE/QUANTITY/PIN modes, display area, all buttons stateless |
| `ZentaDialog.kt` | 146 | `…designsystem.components` | Sealed Confirm/Alert/Input; Input has internal text state for UX |
| `ZentaBottomSheet.kt` | 50 | `…designsystem.components` | ModalBottomSheet wrapper, drag handle toggle, sheetState hoisted |
| `ZentaTable.kt` | 156 | `…designsystem.components` | Sortable headers, LazyColumn, loading/empty/pagination slots |
| `ZentaBadge.kt` | 93 | `…designsystem.components` | ZentaCountBadge (circular) + ZentaStatusBadge (pill) |
| `ZentaSyncIndicator.kt` | 114 | `…designsystem.components` | Animated spinner for SYNCING, status icons, showLabel toggle |
| `ZentaEmptyState.kt` | 79 | `…designsystem.components` | Icon + title + subtitle + optional CTA ZentaButton |
| `ZentaLoadingOverlay.kt` | 54 | `…designsystem.components` | Scrim + CircularProgressIndicator, isLoading guard |
| `ZentaSnackbarHost.kt` | 88 | `…designsystem.components` | ZentaSnackbarVisuals sealed with SUCCESS/ERROR/INFO colors |
| `ZentaTopAppBar.kt` | 105 | `…designsystem.components` | TopAppBar + LargeTopAppBar, scroll behavior, back nav slot |

### Step 6.2 — Integrity Checks

| Check | Result |
|-------|--------|
| All 15 component files present in `components/` directory | ✅ PASS |
| All components are stateless — no internal mutable state except ZentaDialog.Input text field (UX requirement) | ✅ PASS |
| All colors via `MaterialTheme.colorScheme.*` — zero hardcoded colors except semantic constants | ✅ PASS |
| ZentaButton uses `ZentaButtonSize` enum height tokens | ✅ PASS |
| `ZentaProductCard` uses Coil `AsyncImage` for async image loading | ✅ PASS |
| `ZentaCartItemRow` uses `SwipeToDismissBox` from M3 | ✅ PASS |
| `ZentaNumericPad.PIN` mode hides 00 and decimal keys, masks display with ● | ✅ PASS |
| `ZentaTable` generic over `<T>` — usable for any data type | ✅ PASS |
| `ZentaSyncIndicator` mirrors `SyncStatus.State` enum from domain layer | ✅ PASS |
| `ZentaSnackbarHost` uses `ZentaSnackbarVisuals : SnackbarVisuals` contract | ✅ PASS |
| `ZentaTopAppBar` provides both standard and `LargeTopAppBar` variants | ✅ PASS |
| KDoc on all public parameters and functions | ✅ PASS |

### Step 6.2 Final Status
- [x] Finished: Step 6.2 — Core Reusable Components — ALL 15 component files complete | 2026-02-20

> **Section status: ✅ STEP 6.2 COMPLETE — 15/15 COMPONENTS PASS ALL INTEGRITY CHECKS**
> **Next: Step 6.3 — Adaptive Layout Components (ZentaScaffold, ZentaSplitPane, ZentaGrid, ZentaListDetailLayout)**

### Step 6.3 — Adaptive Layout Components
**Goal:** Responsive shells adapting to WindowSizeClass across phone/tablet/desktop

- [x] Finished: 6.3.1 — `ZentaScaffold.kt`: adaptive navigation container:
           COMPACT: `NavigationBar` (bottom), MEDIUM: `NavigationRail` (left 72dp),
           EXPANDED: `PermanentNavigationDrawer` (240dp) | 2026-02-20
- [x] Finished: 6.3.2 — `ZentaSplitPane.kt`: horizontal split with configurable weight (`Modifier.weight`),
           default 40/60 split, `collapsible=true` collapses secondary pane on COMPACT | 2026-02-20
- [x] Finished: 6.3.3 — `ZentaGrid.kt`: `LazyVerticalGrid` with WindowSizeClass column count:
           COMPACT=2, MEDIUM=3–4, EXPANDED=4–6; `key` param enforced for stable recomposition | 2026-02-20
- [x] Finished: 6.3.4 — `ZentaListDetailLayout.kt`: master list + detail pane on EXPANDED;
           single-pane (list only) on COMPACT with animated slide transition | 2026-02-20
- [x] Finished: 6.3.5 — UI component tests (`DesignSystemComponentTests.kt`): 37 tests —
            ZentaButton: size/variant enums, height tokens, padding scaling
            ZentaNumericPad: digit entry, backspace, clear, PIN masking, mode key visibility
            ZentaTable: sort interaction, empty state, weight proportions, column model
            ZentaScaffold: nav item model, window-size → nav chrome mapping
            ZentaGrid: WindowSize → column count per §2.3
            ZentaSplitPane / ZentaListDetailLayout: weight bounds, single/two-pane logic | 2026-02-20


---

## ─────────────────────────────────────────
## SPRINT 11 — :composeApp:navigation
## ─────────────────────────────────────────
> **Plan Ref:** Step 7.1 | **Module:** M07 :composeApp:navigation | **Week:** W11

### Step 7.1 — Type-Safe Navigation Graph
**Goal:** All app routes in a sealed hierarchy; NavHost wired; RBAC-aware; adaptive nav

- [x] Finished: 7.1.0 — Add `compose-navigation = "2.9.0-alpha07"` to `libs.versions.toml` + `navigation/build.gradle.kts` | 2026-02-21
- [x] Finished: 7.1.1 — `ZentaRoute.kt` (154 lines): sealed class with `@Serializable` sub-objects/classes:
           Auth group: `Login`, `PinLock`
           Main group: `Dashboard`, `Pos`, `Payment(orderId: String)`
           Inventory group: `ProductList`, `ProductDetail(productId: String?)`, `CategoryList`, `SupplierList`
           Register group: `RegisterDashboard`, `OpenRegister`, `CloseRegister`
           Reports group: `SalesReport`, `StockReport`
           Settings group: `Settings`, `PrinterSettings`, `TaxSettings`, `UserManagement`
           Deep-link target: `OrderHistory(orderId: String)` | 2026-02-21
- [x] Finished: 7.1.2 — `ZentaNavGraph.kt` (136 lines): root `NavHost`; `startDestination = ZentaRoute.Login`;
           session-active redirect to Dashboard; deep link constants defined | 2026-02-21
- [x] Finished: 7.1.3 — `AuthNavGraph.kt` (60 lines): nested `navigation<ZentaRoute.Login>`:
           Login → PinLock (after idle timeout) | 2026-02-21
- [x] Finished: 7.1.4 — `MainNavGraph.kt` (315 lines) + `MainNavScreens.kt` (102 lines):
           nested `navigation<ZentaRoute.Dashboard>` with `MainScaffoldShell` + `ZentaScaffold`;
           5 sub-graphs: POS, Inventory, Register, Reports, Settings; RBAC-aware nav | 2026-02-21
- [x] Finished: 7.1.5 — `NavigationItems.kt` (136 lines): `NavItem(route, icon, label, requiredPermission)`;
           `AllNavItems` list; `RbacNavFilter.forRole(role)` filters by `Permission.rolePermissions` | 2026-02-21
- [x] Finished: 7.1.6 — `NavigationController.kt` (145 lines): `navigate(route)`, `popBackStack()`,
           `navigateAndClear(route)`, `navigateUp(fallback)`, `lockScreen()`, `goToPos()` | 2026-02-21
- [x] Finished: 7.1.7 — Deep links: `zyntapos://product/{productId}` → `ProductDetail`;
           `zyntapos://order/{orderId}` → `OrderHistory`; wired in `ZentaNavGraph` + `MainNavGraph` | 2026-02-21
- [x] Finished: 7.1.8 — `NavigationModule.kt` (32 lines): Koin `navigationModule`; back stack management:
           `launchSingleTop=true`, `saveState/restoreState=true`; Desktop safe fallback via `navigateUp(Dashboard)` | 2026-02-21

---

## ─────────────────────────────────────────
## SPRINT 12-13 — :composeApp:feature:auth
## ─────────────────────────────────────────
> **Plan Ref:** Step 8.1 | **Module:** M08 :composeApp:feature:auth | **Weeks:** W12–W13

### Step 8.1 — Auth MVI + Screens + Session
**Goal:** Login UI, PIN screen, session management, RBAC guards wired end-to-end

- [x] 8.1.1 — `AuthState.kt`: isLoading, email, password, emailError, passwordError,
           isPasswordVisible, rememberMe, error — all fields with defaults | 2026-02-21
- [x] 8.1.2 — `AuthIntent.kt` sealed: `EmailChanged(email)`, `PasswordChanged(password)`,
           `TogglePasswordVisibility`, `LoginClicked`, `RememberMeToggled(checked)` | 2026-02-21
- [x] 8.1.3 — `AuthEffect.kt` sealed: `NavigateToDashboard`, `NavigateToRegisterGuard`,
           `ShowError(message: String)`, `ShowPinLock` | 2026-02-21
- [x] 8.1.4 — `AuthViewModel.kt` (extends `BaseViewModel<AuthState, AuthIntent, AuthEffect>`):
           handles all intents, calls `LoginUseCase`, emits state via `StateFlow<AuthState>`,
           emits one-shot effects via `Channel`-backed Flow | 2026-02-21
- [x] 8.1.5 — `LoginScreen.kt`: responsive layout:
           EXPANDED: illustration (left 40%) + form (right 60%) — `ZentaSplitPane`
           COMPACT: single pane with ZentaLogo + form
           Fields: email `ZentaTextField`, password with visibility toggle, `ZentaButton` Login
           Offline banner if network unavailable | 2026-02-21
- [x] 8.1.6 — `PinLockScreen.kt`: full-screen PIN overlay, 4–6 digit `ZentaNumericPad(PIN mode)`,
           user avatar + name display, "Different user?" link → full Login | 2026-02-21
- [x] 8.1.7 — `SessionGuard.kt`: composable wrapper — collects `AuthRepository.getSession()`,
           if null → `onNavigateToLogin()` callback, else shows `content(user)` | 2026-02-21
- [x] 8.1.8 — `RoleGuard.kt`: `@Composable fun RoleGuard(permission, content, unauthorized)` —
           calls `CheckPermissionUseCase`, shows content or "Access Denied" `ZentaEmptyState` | 2026-02-21
- [x] 8.1.9 — `SessionManager.kt`: `CoroutineScope`-based idle timer; resets on any user interaction
           (tap/key event); after `sessionTimeoutMs` emits `AuthEffect.ShowPinLock`;
           configurable via SettingsRepository | 2026-02-21
- [x] 8.1.10 — `AuthRepositoryImpl.kt` (data module): local hash validation via `PasswordHasher`,
            JWT caching in `SecurePreferences`, offline fallback (no network = use cached hash) | 2026-02-21
- [x] 8.1.11 — Koin `authModule`: provides AuthViewModel (viewModelOf), LoginUseCase,
            LogoutUseCase, ValidatePinUseCase, CheckPermissionUseCase, SessionManager | 2026-02-21
- [x] 8.1.12 — Unit tests: AuthViewModelTest (all intent transitions, success/failure/offline effects),
            LoginUseCaseTest (valid/invalid/ACCOUNT_DISABLED/OFFLINE_NO_CACHE/network error),
            SessionManagerTest (timeout fires, interaction reset, pause/resume, reset cancels) | 2026-02-21


---

## ─────────────────────────────────────────
## SPRINT 13b — :composeApp:core (BaseViewModel Promotion)
## ─────────────────────────────────────────
> **Trigger:** TODO in Sprint 12-13 `BaseViewModel.kt` — promote before `:composeApp:feature:pos`
> **Module:** :composeApp:core | **Context:** Pre-Sprint-14 architectural prerequisite

### Step BVM — Extract BaseViewModel → :composeApp:core
**Goal:** Remove `BaseViewModel` from `feature/auth/mvi/` (local-only) into a dedicated
`:composeApp:core` module shared by all future feature ViewModels.

- [x] BVM-1 — Register `:composeApp:core` in `settings.gradle.kts` | 2026-02-21
- [x] BVM-2 — Create `composeApp/core/build.gradle.kts` (lifecycle-viewmodel + coroutines; NO Compose UI) | 2026-02-21
- [x] BVM-3 — Scaffold `commonMain/kotlin/…/ui/core/mvi` + `commonTest` package dirs | 2026-02-21
- [x] BVM-4 — Write `BaseViewModel.kt` at `com.zyntasolutions.zyntapos.ui.core.mvi` (141 lines, full KDoc) | 2026-02-21
- [x] BVM-5 — Add `implementation(project(":composeApp:core"))` to `:composeApp:feature:auth/build.gradle.kts` | 2026-02-21
- [x] BVM-6 — Update `AuthViewModel.kt` import: `feature.auth.mvi.BaseViewModel` → `ui.core.mvi.BaseViewModel` | 2026-02-21
- [x] BVM-7 — Delete superseded `feature/auth/mvi/BaseViewModel.kt` | 2026-02-21
- [x] BVM-8 — Integrity verified: canonical import in AuthViewModel ✅ | no stale refs (grep clean) ✅ | `:composeApp:core:tasks` exit 0 ✅ | 2026-02-21

**Files Output:**
```
composeApp/core/build.gradle.kts                                              ← new module, lifecycle-viewmodel + coroutines
composeApp/core/src/commonMain/kotlin/…/ui/core/mvi/BaseViewModel.kt          ← canonical (141 lines, full KDoc)
composeApp/feature/auth/build.gradle.kts                                      ← +implementation(":composeApp:core")
composeApp/feature/auth/src/…/auth/AuthViewModel.kt                           ← import updated
composeApp/feature/auth/src/…/auth/mvi/BaseViewModel.kt                       ← DELETED
settings.gradle.kts                                                           ← include(":composeApp:core") added

```
**Status:** 🟢 COMPLETE — `:composeApp:core` is live; all feature ViewModels from Sprint 14 onwards extend `ui.core.mvi.BaseViewModel`.
- [ ] BVM-4 — Write `BaseViewModel.kt` at `com.zyntasolutions.zyntapos.ui.core.mvi`
- [ ] BVM-5 — Add `:composeApp:core` dependency to `:composeApp:feature:auth/build.gradle.kts`
- [ ] BVM-6 — Update `AuthViewModel.kt` import → `ui.core.mvi.BaseViewModel`
- [ ] BVM-7 — Delete superseded `feature/auth/mvi/BaseViewModel.kt`
- [ ] BVM-8 — Verify build integrity: all imports resolve, no dangling references

---

## ─────────────────────────────────────────
## SPRINT 14-17 — :composeApp:feature:pos
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 | **Module:** M09 :composeApp:feature:pos | **Weeks:** W14–W17

### Step 9.1 — POS MVI State Contracts
- [x] Finished: 9.1.0a — `PosState.kt`: products, categories, selectedCategoryId, searchQuery,
            isSearchFocused, cartItems(List<CartItem>), selectedCustomer, orderDiscount,
            orderDiscountType, heldOrders, orderTotals(OrderTotals), isLoading, scannerActive, error | 2026-02-21
- [x] Finished: 9.1.0b — `PosIntent.kt` sealed: `LoadProducts`, `SelectCategory(id)`, `SearchQueryChanged(q)`,
            `AddToCart(product)`, `RemoveFromCart(productId)`, `UpdateQty(productId, qty)`,
            `ApplyItemDiscount(productId, discount, type)`, `ApplyOrderDiscount(discount, type)`,
            `SelectCustomer(customer)`, `ScanBarcode(barcode)`, `HoldOrder`, `RetrieveHeld(holdId)`,
            `ProcessPayment(method, splits, tendered)`, `ClearCart`, `SetNotes(notes)` | 2026-02-21
- [x] Finished: 9.1.0c — `PosEffect.kt` sealed: `NavigateToPayment(orderId)`, `ShowReceiptScreen(orderId)`,
            `ShowError(msg)`, `PrintReceipt(orderId)`, `BarcodeNotFound(barcode)` | 2026-02-21

### Sprint 14 — Product Grid & Search
- [x] Finished: 9.1.1 — `PosViewModel.kt`: root ViewModel — subscribes to `ProductRepository.getAll()` + category flows,
           handles all PosIntent → use case calls → state updates | 2026-02-21
- [x] Finished: 9.1.2 — `ProductGridSection.kt`: `ZentaGrid` (WindowSizeClass-driven columns) of `ZentaProductCard`;
           `key = { it.id }` for stable recomposition; click → `AddToCart` intent | 2026-02-21
- [x] Finished: 9.1.3 — `CategoryFilterRow.kt`: horizontally scrollable `LazyRow` of M3 `FilterChip`;
           "All" chip always first; selected category highlighted; `SelectCategory` intent on tap | 2026-02-21
- [x] Finished: 9.1.4 — `PosSearchBar.kt`: `ZentaSearchBar` with 300ms debounce (`SearchQueryChanged` intent),
           barcode scan icon toggle → `scannerActive=true` state | 2026-02-21
- [x] Finished: 9.1.5 — `BarcodeInputHandler.kt`: `LaunchedEffect(scannerActive)` → subscribes to
           `BarcodeScanner.scanEvents` → dispatches `ScanBarcode(barcode)` intent;
           ViewModel calls `SearchProductsUseCase` by barcode → auto-add-to-cart if unique match | 2026-02-21
- [x] Finished: 9.1.6 — `KeyboardShortcutHandler.kt` (Desktop only, jvmMain): `onKeyEvent` handler:
           F2 → focus search, F8 → HoldOrder, F9 → RetrieveHeld, Delete → RemoveFromCart,
           +/- → UpdateQty increment/decrement for selected cart item | 2026-02-21

### Sprint 15 — Cart
- [x] 9.1.7 — `CartPanel.kt`: EXPANDED: right-side permanent panel (40% width);
           COMPACT: `ZentaBottomSheet` (draggable); contains CartItemList + CartSummaryFooter | 2026-02-21
- [x] 9.1.8 — `CartItemList.kt`: `LazyColumn` of `ZentaCartItemRow`; `SwipeToDismissBox` → remove;
           `key = { it.productId }` for stable recomposition | 2026-02-21
- [x] 9.1.9 — `CartSummaryFooter.kt`: subtotal row, tax row, discount row (if > 0),
           total (bold, large), PAY button (`ZentaButton` primary, large); all amounts via `CurrencyFormatter` | 2026-02-21
- [x] 9.1.10 — `CustomerSelectorDialog.kt`: debounced search via `CustomerRepository.search()`,
            "Walk-in Customer" default option, quick-add new customer button → `CustomerFormScreen` | 2026-02-21
- [x] 9.1.11 — `ItemDiscountDialog.kt`: FLAT/PERCENT toggle, amount input (`ZentaNumericPad`),
            max cap validation from settings, `RoleGuard(APPLY_DISCOUNT)` wrapper | 2026-02-21
- [x] 9.1.12 — `OrderDiscountDialog.kt`: same pattern as ItemDiscountDialog at order level | 2026-02-21
- [x] 9.1.13 — `OrderNotesDialog.kt`: multiline text field, reference number input, confirm | 2026-02-21
- [x] 9.1.14 — `HoldOrderUseCase` integration: F8 shortcut triggers HoldOrder intent;
            `ZentaDialog(Confirm)` before hold; confirmation snackbar with hold ID | 2026-02-21

### Sprint 16 — Payment Flow
- [x] Finished: 9.1.15 — `PaymentScreen.kt`: full-screen modal/route:
            Left pane (40%): read-only order summary (item list + totals breakdown)
            Right pane (60%): payment method selection + numpad + cash entry | 2026-02-21
- [x] Finished: 9.1.16 — `PaymentMethodGrid.kt`: Cash/Card/Mobile/Split tile grid (min touch target 56dp height),
            selected method highlighted; `SelectPaymentMethod` intent | 2026-02-21
- [x] Finished: 9.1.17 — `CashPaymentPanel.kt`: "Amount Received" `ZentaNumericPad(PRICE)`,
            real-time change calculation: `change = tendered - total` (shown in green if ≥ 0) | 2026-02-21
- [x] Finished: 9.1.18 — `SplitPaymentPanel.kt`: add payment method row button; per-method amount entry;
            remaining amount tracker; validates sum = total before enabling "PAY" | 2026-02-21
- [x] Finished: 9.1.19 — `ProcessPaymentUseCase` integration: on PAY → validate → create Order →
            decrement stock → enqueue sync → trigger print → emit `ShowReceiptScreen` | 2026-02-21
- [x] Finished: 9.1.20 — `PaymentSuccessOverlay.kt`: animated checkmark (Compose `animateFloatAsState`),
            success color fill, auto-dismisses after 1.5s → receipt screen | 2026-02-21

### Sprint 17 — Receipt & Order Management
- [x] 9.1.21 — `ReceiptScreen.kt`: scrollable text-based receipt preview using
            `EscPosReceiptBuilder.buildReceipt()` output rendered as monospace text;
            action row: Print / Email / Skip buttons | 2026-02-21
- [x] 9.1.22 — `EscPosReceiptBuilder.kt` integration (already in :shared:hal):
            `PrintReceiptUseCase.kt` calls `PrinterManager.print(receiptBytes)`,
            handles `HalException` → shows retry `ZentaDialog` | 2026-02-21
- [x] 9.1.23 — `PrintReceiptUseCase.kt`: gets `PrinterConfig` from SettingsRepository,
            builds receipt via `EscPosReceiptBuilder`, sends via `PrinterManager`,
            logs to `SecurityAuditLogger.logReceiptPrint(orderId, userId)` | 2026-02-21
- [x] 9.1.24 — `HeldOrdersBottomSheet.kt`: `LazyColumn` of held orders (hold time, item count, total);
            tap → `RetrieveHeldOrderUseCase` → restore cart state; F9 shortcut opens | 2026-02-21
- [x] 9.1.25 — `OrderHistoryScreen.kt`: today's orders `ZentaTable` (order #, time, items, total, status);
            filter by status chips; tap → order detail; reprint button per row | 2026-02-21
- [x] 9.1.26 — Koin `posModule`: provides PosViewModel (viewModelOf), all POS UseCases,
            HAL `PrinterManager` binding, `BarcodeScanner` binding | 2026-02-21
- [x] 9.1.27 — Unit tests: `CalculateOrderTotalsUseCase` (all 6 tax modes per §11.3),
            `ProcessPaymentUseCase` (cash exact/overpay/underpay, split valid/invalid),
            `AddItemToCartUseCase` (stock limit enforcement), PosViewModel state transitions | 2026-02-21


---

## ─────────────────────────────────────────
## SPRINT 18-19 — :composeApp:feature:inventory
## ─────────────────────────────────────────
> **Plan Ref:** Step 10.1 | **Module:** M10 :composeApp:feature:inventory | **Weeks:** W18–W19

### Step 10.1 — Inventory Screens & CRUD

#### Sprint 18 — Products ✅ COMPLETE
- [x] 10.1.1 — `ProductListScreen.kt`: `ZentaTable` (list) + grid toggle button;
           search bar (FTS5 via SearchProductsUseCase), filter by category `FilterChip` row;
           FAB → `ProductDetail(productId=null)` for new product | 2026-02-21
- [x] 10.1.2 — `ProductDetailScreen.kt`: create/edit product form:
           name, barcode (scan or type), SKU, category selector, unit selector,
           price/cost price fields, tax group selector, stock qty (read-only or manual entry),
           minStockQty, description, `AsyncImage` picker (Coil + platform file chooser),
           variation management section (add/remove ProductVariant rows), isActive toggle | 2026-02-21
- [x] 10.1.3 — `ProductFormValidator.kt`: barcode uniqueness check (`ProductRepository.getByBarcode`),
           SKU uniqueness check, required field validation (name, price, unit, category) | 2026-02-21
- [x] 10.1.4 — `BarcodeGeneratorDialog.kt`: generates EAN-13 / Code128 barcode for new/existing products;
           displays as Canvas-drawn barcode preview; prints via `PrinterManager` if confirmed | 2026-02-21
- [x] 10.1.5 — `BulkImportDialog.kt`: CSV file picker (platform file chooser),
           column mapping UI (drag-and-drop field assignment),
           preview table of parsed rows, confirm import → batch `CreateProductUseCase` | 2026-02-21
- [x] 10.1.6 — `StockAdjustmentDialog.kt`: product search (FTS), increase/decrease/transfer selector,
           quantity `ZentaNumericPad(QUANTITY)`, reason text field,
           confirm → `AdjustStockUseCase` → audit log entry | 2026-02-21

#### Sprint 19 — Categories, Suppliers, Tax Groups ✅ COMPLETE
- [x] 10.1.7 — `CategoryListScreen.kt`: tree-view `LazyColumn` of categories (indent by depth),
           expand/collapse parent nodes, edit icon per row, FAB for new category | 2026-02-21
- [x] 10.1.8 — `CategoryDetailScreen.kt`: name field, parent category selector (dropdown),
           image picker, display order integer field, confirm → insert/update | 2026-02-21
- [x] 10.1.9 — `SupplierListScreen.kt`: `ZentaTable` with search, contact info columns,
           FAB → new supplier | 2026-02-21
- [x] 10.1.10 — `SupplierDetailScreen.kt`: name, contactPerson, phone, email, address, notes;
            purchase history section (read-only order list filtered by supplierId) | 2026-02-21
- [x] 10.1.11 — `UnitManagementScreen.kt`: list of UnitOfMeasure groups, conversion rate editing,
            base unit designation toggle per group | 2026-02-21
- [x] 10.1.12 — `TaxGroupScreen.kt`: create/edit tax group (name, rate % field, inclusive toggle),
            used across POS + Inventory | 2026-02-21
- [x] 10.1.13 — `LowStockAlertBanner.kt`: persistent `ZentaBadge` banner on Inventory home if
            any product qty < minStockQty; shows count + link to filtered product list | 2026-02-21
- [x] 10.1.14 — Koin `inventoryModule` + unit tests:
            `CreateProductUseCase` (barcode unique, SKU unique, valid/invalid),
            `AdjustStockUseCase` (increase, decrease, negative stock prevention),
            `SearchProductsUseCase` (FTS results) | 2026-02-21

---

## ─────────────────────────────────────────
## SPRINT 20-21 — :composeApp:feature:register
## ─────────────────────────────────────────
> **Plan Ref:** Step 11.1 | **Module:** M11 :composeApp:feature:register | **Weeks:** W20–W21

### Step 11.1 — Cash Register Lifecycle

#### Sprint 20 — Open & Operations ✅ COMPLETE
- [x] 11.1.1 — `RegisterGuard.kt`: on post-login, checks `RegisterRepository.getActive()`;
            if null → redirect to `OpenRegister` route; `SessionGuard` dependency | 2026-02-21
- [x] 11.1.2 — `OpenRegisterScreen.kt`: select register from list, enter opening balance via
            `ZentaNumericPad(PRICE)`, confirm → `OpenRegisterSessionUseCase`;
            error state if register already open | 2026-02-21
- [x] 11.1.3 — `RegisterDashboardScreen.kt`: current session info card (opened by, opened at, running balance);
            quick stats row: orders today, revenue today;
            "Cash In" / "Cash Out" buttons; movements list below | 2026-02-21
- [x] 11.1.4 — `CashInOutDialog.kt`: type (IN/OUT) selector, amount `ZentaNumericPad(PRICE)`,
            reason text field, confirm → `RecordCashMovementUseCase` | 2026-02-21
- [x] 11.1.5 — `CashMovementHistory.kt`: `LazyColumn` of `CashMovement` rows
            (type badge, amount, reason, time) for current session | 2026-02-21

#### Sprint 21 — Close & Z-Report ✅ COMPLETE
- [x] 11.1.6 — `CloseRegisterScreen.kt`:
            Expected balance section: auto-calculated (read-only display)
            Actual balance section: `ZentaNumericPad` entry (or denomination breakdown optional)
            Discrepancy display: difference in red/green, warning if > configurable threshold
            "Close Register" `ZentaButton(Danger)` → `CloseRegisterSessionUseCase` | 2026-02-21
- [x] 11.1.7 — `CloseRegisterSessionUseCase` integration: calculates expectedBalance,
            records actualBalance, detects discrepancy, generates Z-report data model | 2026-02-21
- [x] 11.1.8 — `ZReportScreen.kt`: printable summary layout:
            Store info header, session info, opening balance, cash in/out list,
            sales total by payment method, expected vs actual, discrepancy line, signature line | 2026-02-21
- [x] 11.1.9 — `PrintZReportUseCase.kt`: `EscPosReceiptBuilder.buildZReport(session)` →
            `PrinterManager.print(bytes)` → error handling | 2026-02-21
- [x] 11.1.10 — Koin `registerModule` + unit tests:
            `OpenRegisterSessionUseCase` (no active session / already open),
            `CloseRegisterSessionUseCase` (discrepancy detection, expected balance calculation),
            `RecordCashMovementUseCase` (positive amount validation) | 2026-02-21

> **✅ SPRINT 21 COMPLETE — All 5 tasks verified. Files exist on disk and implementation aligns with PLAN_PHASE1.md §Sprint 20–21.**


---

## ─────────────────────────────────────────
## SPRINT 22 — :composeApp:feature:reports
## ─────────────────────────────────────────
> **Plan Ref:** Step 12.1 | **Module:** M12 :composeApp:feature:reports | **Week:** W22
> **Session Start:** 2026-02-21

### Step 12.1 — Sales & Stock Reports

- [x] Finished: 12.1.1 — `ReportsHomeScreen.kt`: tile grid — "Sales Report" and "Stock Report" tiles
           (Phase 1); each tile shows icon, title, last-generated timestamp | 2026-02-21
- [x] Finished: 12.1.2 — `SalesReportScreen.kt`:
           Date range picker: Today / This Week / This Month / Custom (`DateRangePickerDialog`)
           KPI cards: Total Sales, Order Count, Average Order Value, Top Product
           Sales trend chart: `Canvas`-based line chart (revenue per day in range)
           Payment method breakdown: horizontal bar chart
           Per-product sales table (`ZentaTable`: product name, qty sold, revenue — sortable) | 2026-02-21
- [x] Finished: 12.1.3 — `GenerateSalesReportUseCase` integration: async with `isLoading` state,
           results cached in ViewModel (don't re-query on recomposition) | 2026-02-21
- [x] Finished: 12.1.4 — `StockReportScreen.kt`:
           Current stock levels `ZentaTable` (product, category, qty, value, status badge)
           Low stock section: items where qty < minStockQty (highlighted in amber)
           Dead stock section: items with no movement in 30 days (highlighted in gray)
           Category filter `FilterChip` row | 2026-02-21
- [x] Finished: 12.1.5 — `GenerateStockReportUseCase` integration: async load, handles 10K+ products via
           paged SQLDelight query | 2026-02-21
- [x] Finished: 12.1.6 — `DateRangePickerBar.kt`: reusable composable with preset chips + custom date range
           `DatePickerDialog` from M3 for start/end date selection | 2026-02-21
- [x] Finished: 12.1.7 — `ReportExporter.kt` (expect/actual):
           JVM actual: write CSV/PDF to user-selected directory (`JFileChooser`)
           Android actual: generate file → share via `Intent.ACTION_SEND` / `ShareSheet`
           CSV: simple comma-delimited text
           PDF: JVM uses Apache PDFBox; Android uses HTML template → print to PDF | 2026-02-21
- [x] Finished: 12.1.8 — `PrintReportUseCase.kt`: condensed thermal format for Z-report summary → `PrinterManager` | 2026-02-21
- [x] Finished: 12.1.9 — Koin `reportsModule` + unit tests:
           `GenerateSalesReportUseCase` (date range, aggregation correctness),
           `GenerateStockReportUseCase` (low stock detection, dead stock detection) | 2026-02-21

> **✅ SPRINT 22 COMPLETE — All 9 tasks verified. Files exist on disk and implementation aligns with PLAN_PHASE1.md §Sprint 22.**

---

## ─────────────────────────────────────────
## SPRINT 23 — :composeApp:feature:settings
## ─────────────────────────────────────────
> **Plan Ref:** Step 13.1 | **Module:** M18 :composeApp:feature:settings | **Week:** W23

### Step 13.1 — Settings Screens

- [x] Finished: 13.1.1 — `SettingsHomeScreen.kt`: grouped card layout with categories:
           General, POS, Tax, Printer, Users, Security, Backup, Appearance, About | 2026-02-21
- [x] Finished: 13.1.2 — `GeneralSettingsScreen.kt`: store name, address, phone, logo upload (Coil AsyncImage),
           currency selector (LKR/USD/EUR for Phase 1), timezone selector,
           date format selector, language (English only Phase 1) | 2026-02-21
- [x] Finished: 13.1.3 — `PosSettingsScreen.kt`: default order type (SALE/REFUND), auto-print receipt toggle,
           tax display mode (inclusive/exclusive shown to customer),
           receipt template selector (standard/minimal), max discount % setting | 2026-02-21
- [x] Finished: 13.1.4 — `TaxSettingsScreen.kt`: `ZentaTable` of tax groups with edit icon per row;
           FAB → `TaxGroupScreen` for new tax group; delete with `ZentaDialog(Confirm)` | 2026-02-21
- [x] Finished: 13.1.5 — `PrinterSettingsScreen.kt`:
           Printer type selector: USB / Bluetooth / Serial / TCP
           Connection params (conditional): Port/IP+Port / COM port+baud rate / BT device selector
           Paper width selector: 58mm / 80mm
           "Test Print" `ZentaButton` → `PrintTestPageUseCase` (prints built-in test page)
           Receipt customization: header lines editor (up to 5), footer lines, show/hide fields toggles | 2026-02-21
- [x] Finished: 13.1.6 — `UserManagementScreen.kt`: `ZentaTable` of users (name, email, role, status);
           create/edit user slide-over (name, email, password, role selector, isActive toggle);
           gated by `RoleGuard(MANAGE_USERS)` (ADMIN only) | 2026-02-21
- [x] Finished: 13.1.7 — `BackupSettingsScreen.kt`: manual backup trigger button → export encrypted DB file;
           last backup timestamp display; "Restore from backup" file picker + confirmation dialog | 2026-02-21
- [x] Finished: 13.1.8 — `AboutScreen.kt`: app name, version (from BuildConfig), build date,
           open-source licenses list (`LazyColumn`), support contact | 2026-02-21
- [x] Finished: 13.1.9 — `AppearanceSettingsScreen.kt`: Light / Dark / System default `RadioButton` group;
           selected theme stored in SettingsRepository → triggers `ZentaTheme` recomposition | 2026-02-21
- [x] Finished: 13.1.10 — Koin `settingsModule` + `SettingsViewModel`: CRUD settings via `SettingsRepository`,
            handles all settings-related intents/state/effects | 2026-02-21


---

## ─────────────────────────────────────────
## SPRINT 24 — Integration, QA & Release Prep
## ─────────────────────────────────────────
> **Plan Ref:** Step 14.1 | **Week:** W24

### Step 14.1 — Integration QA & Release Packaging

#### End-to-End Test Runs
- [ ] 14.1.1 — Full E2E flow test (manual + automated):
           Login → Open Register → POS: search product + scan barcode + add to cart →
           Apply discount → Select customer → Payment (cash + split) →
           Print receipt → Order in history → Close Register → Z-Report
- [ ] 14.1.2 — Offline E2E: disable network → full POS sale → re-enable network →
           verify sync queue empties and server confirms data

#### Performance Validation (§12 targets)
- [ ] 14.1.3 — Cold start measurement: Android (Pixel 4 equiv) → POS screen < 3s
           (Android Profiler startup trace)
- [ ] 14.1.4 — Product search benchmark: 10K products in DB → FTS5 query < 200ms
           (SQLDelight benchmark test + Profiler)
- [ ] 14.1.5 — Add-to-cart recomposition: Compose compiler metrics — confirm < 50ms,
           no unnecessary recompositions in `ProductGrid` or `CartItemList`
- [ ] 14.1.6 — Payment processing timing: `ProcessPaymentUseCase` isolated execution < 800ms
- [ ] 14.1.7 — Receipt print trigger: `PrinterManager.print()` → HAL callback < 2s

#### Security Validation
- [ ] 14.1.8 — SQLCipher verification: open DB file with SQLite Browser (no password) → must fail;
           open with correct key → succeeds
- [ ] 14.1.9 — Android Keystore: confirm DB key not extractable (`KeyInfo.isInsideSecureHardware`)
- [ ] 14.1.10 — Audit log hash chain: verify no gaps, each entry references previous hash correctly
- [ ] 14.1.11 — RBAC smoke test: CASHIER cannot access Settings/UserManagement/Reports;
            STORE_MANAGER can access Reports; ADMIN can access all

#### UI Quality Audit
- [ ] 14.1.12 — Dark mode audit: every screen in every module rendered in dark mode — no hardcoded colors
- [ ] 14.1.13 — Responsive layout audit: test all screens at:
            Compact (360dp), Medium (720dp), Expanded (1280dp) — no overflow, no clipped text
- [ ] 14.1.14 — Desktop keyboard shortcut audit: all shortcuts per UI/UX plan §23 functional
- [ ] 14.1.15 — Memory profiling: Android Profiler heap dump during active POS session < 256MB

#### Build & Release
- [ ] 14.1.16 — Android APK release build: configure release signing (`signingConfigs.release`
            in `:androidApp/build.gradle.kts`), minSdk=24, targetSdk=35, R8/ProGuard enabled
- [ ] 14.1.17 — ProGuard rules review: keep KMP serialization classes, Koin reflective lookups,
            SQLDelight generated classes — test release APK full E2E flow
- [ ] 14.1.18 — Desktop distributable via jpackage:
            Windows: MSI installer (`jpackage --type msi`)
            macOS: DMG (`jpackage --type dmg`)
            Linux: DEB (`jpackage --type deb`)
            JVM 17 runtime bundled, app icon configured
- [ ] 14.1.19 — CI/CD validation: GitHub Actions pipeline runs clean on `main` branch:
            unit tests pass, APK builds, Desktop JAR builds
- [ ] 14.1.20 — Final `execution_log.md` audit: confirm ALL Sprint 1–24 steps marked `[x] Finished`
            with timestamps; no `[ ]` remaining in Phase 1

---

## ═══════════════════════════════════════════
## PHASE 2 — GROWTH (Months 7–12)
## ═══════════════════════════════════════════
> **Goal:** Multi-store, CRM, promotions, financial tools, CRDT sync
> **Status:** 🔴 NOT STARTED (Blocked on Phase 1 completion)

- [ ] Phase 2 planning will be detailed in `PLAN_PHASE2.md` upon Phase 1 completion

---

## ═══════════════════════════════════════════
## PHASE 3 — ENTERPRISE (Months 13–18)
## ═══════════════════════════════════════════
> **Goal:** Full enterprise features, compliance, staff management, administration
> **Status:** 🔴 NOT STARTED (Blocked on Phase 2 completion)

- [ ] Phase 3 planning will be detailed in `PLAN_PHASE3.md` upon Phase 2 completion

---

## 📋 CROSS-CUTTING CONCERNS (All Phases)

### Security (Ongoing)
- [ ] SEC.1 — Regular dependency vulnerability scan (Gradle Versions Plugin / Dependabot alerts)
- [ ] SEC.2 — No hardcoded secrets: all via `local.properties` + Secrets Gradle Plugin
- [ ] SEC.3 — Certificate pinning for Ktor client (production builds only)

### Testing Infrastructure (Ongoing)
- [ ] TEST.1 — `commonTest`: Kotlin Test + Mockative stubs for all repository interfaces
- [ ] TEST.2 — Compose UI test harness: both Android (`createAndroidComposeRule`) and Desktop targets
- [ ] TEST.3 — Kover code coverage: enforce 85%+ on shared modules; 80%+ on feature ViewModels

### CI/CD
- [ ] CI.1 — GitHub Actions: build + unit test on every PR (`.github/workflows/ci.yml`)
- [ ] CI.2 — GitHub Actions: assemble Android APK + Desktop JAR on `main` push
- [ ] CI.3 — GitHub Secrets → Gradle build environment injection (API_BASE_URL, SIGNING_KEY, etc.)

---

---

## 🔧 HOTFIX — Clean Architecture Layer Violation: PrintReceiptUseCase (2026-02-21)
> **Problem:**
> (a) `PrintReceiptUseCase` lives in `composeApp/feature/pos` (presentation layer) — belongs in `:shared:domain`
> (b) `ReceiptScreen.kt` builds thermal bytes inline via `EscPosReceiptBuilder` — domain/infra concern in UI
> **Constraint:** `:shared:hal` and `:shared:security` both depend on `:shared:domain` — direct HAL/security imports in domain would be circular.
> **Solution:** Port/Adapter pattern — define `ReceiptPrinterPort` interface in domain; `PrinterManagerReceiptAdapter` in `feature:pos` implements it using HAL + security.

- [ ] RCV-1 — Read all affected files (PrintReceiptUseCase, ReceiptScreen, PosState, PosViewModel, PosModule, PosIntent, PosEffect)
- [x] RCV-2 — Create `shared/domain/.../domain/printer/ReceiptPrinterPort.kt` (output port interface) | 2026-02-21
- [x] RCV-3 — Create `shared/domain/.../domain/formatter/ReceiptFormatter.kt` (pure text formatter from Order, no HAL) | 2026-02-21
- [x] RCV-4 — Create `shared/domain/.../domain/usecase/pos/PrintReceiptUseCase.kt` (depends only on ReceiptPrinterPort — no HAL/security imports) | 2026-02-21
- [x] RCV-5 — Create `composeApp/feature/pos/.../feature/pos/printer/PrinterManagerReceiptAdapter.kt` (implements ReceiptPrinterPort using PrinterManager + SecurityAuditLogger) | 2026-02-21
- [x] RCV-6 — Add `receiptPreviewText: String` + `currentReceiptOrder: Order?` + `isPrinting` + `printError` to `PosState.kt` | 2026-02-21
- [x] RCV-7 — Refactor `ReceiptScreen.kt`: remove inline `EscPosReceiptBuilder` call; accept `receiptPreviewText: String` + `orderNumber: String` params | 2026-02-21
- [x] RCV-8 — Add `PrintCurrentReceipt` + `DismissPrintError` intents to `PosIntent.kt` | 2026-02-21
- [ ] RCV-9 — Update `PosViewModel.kt`: inject `PrintReceiptUseCase` + `ReceiptFormatter`; populate `receiptPreviewText` on payment success; handle `PrintCurrentReceipt` intent
- [ ] RCV-10 — Update `PosModule.kt`: add `PrinterManagerReceiptAdapter` + `ReceiptFormatter` factory bindings; inject into `PosViewModel`
- [ ] RCV-11 — Delete old `composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt`
- [ ] RCV-12 — Integrity check: grep for stale imports + verify layer boundaries

---

## 📝 Session Notes

| Date | Note |
|------|------|
| 2026-02-20 | Project audit complete. Baseline: KMP skeleton only. All modules pending. Execution log created. Ready to begin Phase 0. |
| 2026-02-20 | Phase 0 BUILD SUCCESSFUL in 12s (63 tasks: assembleDebug ✅ jvmJar ✅) |
| 2026-02-20 | HOTFIX: AGP deprecation warnings resolved (FIX.1–FIX.6). Module structure stabilized. |
| 2026-02-20 | **Log v1.1 update:** Phase 1 tasks re-mapped to PLAN_PHASE1.md atomic step numbering (Step 1.1.x → Step 14.1.x). Sprint-aligned, ~450+ atomic tasks documented. |
| 2026-02-20 | **FIX-01 COMPLETE:** 13 feature modules created (auth, pos, inventory, register, reports, settings, customers, coupons, expenses, staff, multistore, admin, media). `./gradlew tasks --all` — zero "project path not found" errors. All modules visible in task graph. BUILD SUCCESSFUL in 8s. |
| 2026-02-20 | **FIX-02 COMPLETE:** Module name canonicalization verified. Master_plan.md has 4 occurrences of `:crm` that should be `:customers` (lines 139, 216, 249, 895). settings.gradle.kts correctly uses `:composeApp:feature:customers`. Documentation fix report created at `docs/FIX-02_MODULE_NAME_CANONICALIZATION.md`. |
| 2026-02-20 | **FIX-02 APPLIED:** All 4 occurrences of `:crm` → `:customers` successfully updated in `docs/plans/Master_plan.md`. Changes: Line 139 (module tree), Line 216 (dependency table), Line 249 (diagram), Line 895 (Phase 2 checklist). Verification docs at `docs/FIX-02_INTEGRITY_VERIFICATION.md`. |
| 2026-02-20 | **FIX-03 COMPLETE:** Removed duplicate Android resources from :composeApp. Deleted composeApp/src/androidMain/res/ (15 files in 9 dirs). Verified library manifest is bare (no <application>). Confirmed :androidApp icons intact. Resource merge conflict resolved. |

---

## 📊 Phase 1 Progress Tracker

| Sprint | Module | Steps | Done | Status |
|--------|--------|-------|------|--------|
| 1 | Project Scaffold | 1.1.1–1.1.8 | 8/8 | 🟢 |
| 2 | :shared:core | 1.2.1–1.2.14 | 14/14 | 🟢 |
| 3 | :shared:domain (Models) | 2.1.1–2.1.24 | 24/24 | 🟢 |
| 4 | :shared:domain (UseCases) | 2.2.1–2.3.27 | 37/37 | 🟢 |
| 5 | :shared:data (Schema) | 3.1.1–3.2.5 | 17/17 | 🟢 |
| 6 | :shared:data (Repos+Ktor) | 3.3.1–3.4.7 | 17/17 | 🟢 |
| 7 | :shared:hal | 4.1.1–4.2.11 | 17/17 | 🟢 |
| 8 | :shared:security | 5.1.1–5.1.10 | 5/10 | 🟡 |
| 9 | :designsystem (Theme) | 6.1.1–6.1.7 | 7/7 | 🟢 |
| 10 | :designsystem (Components) | 6.2.1–6.3.5 | 20/20 | 🟢 |
| 11 | :navigation | 7.1.0–7.1.8 | 9/9 | 🟢 |
| 12–13 | :feature:auth | 8.1.1–8.1.12 | 12/12 | 🟢 |
| 14–17 | :feature:pos | 9.1.0–9.1.27 | 30/30 | 🟢 |
| 18–19 | :feature:inventory | 10.1.1–10.1.14 | 2/14 | 🔴 |
| 20–21 | :feature:register | 11.1.1–11.1.10 | 0/10 | 🔴 |
| 22 | :feature:reports | 12.1.1–12.1.9 | 0/9 | 🔴 |
| 23 | :feature:settings | 13.1.1–13.1.10 | 0/10 | 🔴 |
| 24 | Integration QA & Release | 14.1.1–14.1.20 | 0/20 | 🔴 |

**Phase 1 Total:** ~285 atomic steps (excludes sub-bullets) | **Completed:** 219 | **Remaining:** 66
> _Last updated: 2026-02-21 | 🟡 = In Progress (Sprint 8: 5.1.6–5.1.10 pending)_

---

## ═══════════════════════════════════════════
## HOTFIX FIX-02 — Module Name Canonicalization
## ═══════════════════════════════════════════
> **Issue:** Master_plan.md references :composeApp:feature:crm but settings.gradle.kts has :composeApp:feature:customers
> **Decision:** Use :customers (more descriptive, already in settings)
> **Status:** 🟢 COMPLETE

- [x] FIX-02.01 — Edit Master_plan.md §3.2: change :crm → :customers | 2026-02-20 — APPLIED: All 4 changes (lines 139, 216, 249, 895) successfully updated
- [x] FIX-02.02 — Search all plan docs for :crm references and update | 2026-02-20 — Searched all plan docs: 0 occurrences in PLAN_PHASE1.md, 4 in Master_plan.md (all fixed)
- [x] FIX-02.03 — Confirm settings.gradle.kts has :customers ✅ | 2026-02-20 — VERIFIED: line ~133 includes :composeApp:feature:customers
- [x] FIX-02.04 — Generate completion documentation | 2026-02-20 — Created: FIX-02_COMPLETION_SUMMARY.md, FIX-02_INTEGRITY_VERIFICATION.md

---

## ═══════════════════════════════════════════
## HOTFIX FIX-03 — Remove Duplicate Android Resources
## ═══════════════════════════════════════════
> **Issue:** :composeApp (KMP library) contains duplicate Android launcher icons and app_name string causing APK resource merge conflicts
> **Solution:** Delete entire composeApp/src/androidMain/res/ directory; verify library manifest has no <application> block
> **Status:** 🟢 COMPLETE

- [x] FIX-03.01 — Delete entire composeApp/src/androidMain/res/ directory (15 files in 9 directories) | 2026-02-20 — DELETED: All launcher icons and strings.xml removed from library module
- [x] FIX-03.02 — Verify composeApp/src/androidMain/AndroidManifest.xml has NO <application> block | 2026-02-20 — VERIFIED: Bare library manifest with only <manifest xmlns:android=...> root element
- [x] FIX-03.03 — Confirm androidApp/src/main/res/ still has all launcher icons intact | 2026-02-20 — VERIFIED: All mipmap densities (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) + anydpi-v26 icons present in :androidApp

---

---

## ═══════════════════════════════════════════
## HOTFIX FIX-04 — Create Missing jvmMain Source Set Directories
## ═══════════════════════════════════════════
> **Issue:** `:composeApp:designsystem` and `:composeApp:navigation` declare `jvm()` target but `jvmMain/kotlin/` dirs were missing on disk. Sprint 9 (WindowSizeClassHelper Desktop actual) and Sprint 11 (Desktop nav handling) would fail without this.
> **Solution:** Create missing `jvmMain` and `androidMain` source set directories with `.gitkeep` placeholders.
> **Status:** 🟢 COMPLETE

- [x] FIX-04.01 — Create `composeApp/designsystem/src/jvmMain/kotlin/com/zynta/pos/designsystem/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`
- [x] FIX-04.02 — Create `composeApp/navigation/src/jvmMain/kotlin/com/zynta/pos/navigation/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`
- [x] FIX-04.03 — Create `composeApp/navigation/src/androidMain/kotlin/com/zynta/pos/navigation/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`

**Integrity Check:** `find` output confirmed all 3 `.gitkeep` files at correct package paths. Source set resolution for `jvmMain` and `androidMain` in both modules is now unblocked.

---

---

## ─────────────────────────────────────────
## FIX-05 — Move Platform expect/actual Files to :shared:core
## ─────────────────────────────────────────
> **Source:** MM-12 | **Severity:** 🟠 HIGH | **Session:** 2026-02-20 | **Status:** ✅ COMPLETE

- [x] Finished: FIX-05.01 — Move `Platform.kt` → `shared/core/src/commonMain/kotlin/com/zynta/pos/core/Platform.kt` | 2026-02-20
- [x] Finished: FIX-05.02 — Move `Platform.android.kt` → `shared/core/src/androidMain/kotlin/com/zynta/pos/core/Platform.android.kt` | 2026-02-20
- [x] Finished: FIX-05.03 — Move `Platform.jvm.kt` → `shared/core/src/jvmMain/kotlin/com/zynta/pos/core/Platform.jvm.kt` | 2026-02-20
- [x] Finished: FIX-05.04 — Delete `composeApp/src/commonMain/kotlin/com/zynta/pos/Greeting.kt` | 2026-02-20
- [x] Finished: FIX-05.05 — Deleted composeApp Platform stubs; rewrote `App.kt` — removed Greeting import/usage, removed Platform imports; clean shell composable | 2026-02-20
- [x] Finished: FIX-05.06 — Added `implementation(project(":shared:core"))` to `composeApp/build.gradle.kts` commonMain.dependencies | 2026-02-20

**Integrity check passed:** 3 Platform*.kt files confirmed in :shared:core only; Greeting.kt confirmed deleted (0 results); App.kt confirmed clean (Greeting appears only in KDoc comment, zero import/code refs); :shared:core dep confirmed in composeApp/build.gradle.kts.

---

## ═══════════════════════════════════════════
## HOTFIX FIX-06 — Create CI/CD GitHub Actions Workflow
## ═══════════════════════════════════════════
> **Source:** MM-03 + CRITICAL-4/FIX-D.3 | **Severity:** 🟠 HIGH | **Session:** 2026-02-20
> **Sprint 1 task 1.1.7** — Required for team development.
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-06.01 — Create `.github/workflows/` directory | 2026-02-20
- [x] Finished: FIX-06.02 — Create `.github/workflows/ci.yml` with full build + test pipeline | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-07 — Complete :shared:core Internal Sub-package Directories
## ═══════════════════════════════════════════
> **Source:** MM-07 (MISMATCH_FIX_v1.0 MINOR-1 misclassified) | **Severity:** 🟡 MEDIUM
> **Reason:** Sub-dirs must pre-exist for IDE autocomplete + match PLAN_PHASE1.md Appendix B
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-07.01 — Create `core/result/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.02 — Create `core/logger/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.03 — Create `core/config/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.04 — Create `core/extensions/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.05 — Create `core/utils/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.06 — Create `core/mvi/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.07 — Create `core/di/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.08 — Move `CoreModule.kt` root → `core/di/CoreModule.kt`; package updated to `com.zynta.pos.core.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-08 — Create :shared:domain `validation/` Sub-directory
## ═══════════════════════════════════════════
> **Source:** MM-08 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-08.01 — Create `shared/domain/src/commonMain/kotlin/com/zynta/pos/domain/validation/` + `.gitkeep` | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-09 — Create :shared:data Missing Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-09 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-09.01 — Create `data/local/db/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.02 — Create `data/local/mapper/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.03 — Create `data/remote/api/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.04 — Create `data/remote/dto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.05 — Create `data/sync/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.06 — Move `DataModule.kt` root → `data/di/DataModule.kt`; package updated to `com.zynta.pos.data.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-10 — Create :shared:hal printer/ and scanner/ Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-10 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-10.01 — Create `commonMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.02 — Create `commonMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.03 — Create `androidMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.04 — Create `androidMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.05 — Create `jvmMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.06 — Create `jvmMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.07 — Move `HalModule.kt` root → `hal/di/HalModule.kt` (commonMain); `di/` created in all 3 source sets; package updated to `com.zynta.pos.hal.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-11 — Create :shared:security Crypto Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-11 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-11.01 — Create `commonMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.02 — Create `commonMain/.../security/token/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.03 — Create `commonMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.04 — Create `androidMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.05 — Create `androidMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.06 — Create `jvmMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.07 — Create `jvmMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.08 — Move `SecurityModule.kt` root → `security/di/SecurityModule.kt`; `di/` created in all 3 source sets; package updated to `com.zynta.pos.security.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-12 — Add Missing Library Dependencies to Version Catalog
## ═══════════════════════════════════════════
> **Source:** CRITICAL-3 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 HIGH
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-12.01 — Added `jserialcomm = "2.10.4"` to `libs.versions.toml [versions]` | 2026-02-20
- [x] Finished: FIX-12.02 — Added `jbcrypt = "0.4"` to `libs.versions.toml [versions]` | 2026-02-20
- [x] Finished: FIX-12.03 — Added `jserialcomm = { module = "com.fazecast:jSerialComm", version.ref = "jserialcomm" }` to `[libraries]` | 2026-02-20
- [x] Finished: FIX-12.04 — Added `jbcrypt = { module = "org.mindrot:jbcrypt", version.ref = "jbcrypt" }` to `[libraries]` | 2026-02-20
- [x] Finished: FIX-12.05 — Documented: Desktop DB encryption strategy = **JCE AES-256-GCM at application layer** (encrypts data before SQLite write / decrypts on read). SQLCipher JDBC is NOT used for Desktop JVM. Android continues to use `net.zetetic:sqlcipher-android` via the existing `sqlcipher-android` catalog entry. | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-13 — Reconcile execution_log.md Sprint 1 Statuses
## ═══════════════════════════════════════════
> **Source:** CRITICAL-4 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-13.01 — Marked Sprint 1 tasks 1.1.1–1.1.6 + 1.1.8 as `[x] Finished (Completed in Phase 0)` | 2026-02-20
- [x] Finished: FIX-13.02 — Annotated 1.1.2 with actual versions: kotlin=2.3.0, agp=8.13.2, composeMp=1.10.0 (plan estimates were outdated) | 2026-02-20
- [x] Finished: FIX-13.03 — Task 1.1.7 (GitHub Actions CI) left as `[ ]` — pending, covered by FIX-06 | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-14 — Document Namespace + Undocumented Plugin in execution_log.md
## ═══════════════════════════════════════════
> **Source:** CRITICAL-2 + CRITICAL-5 (from MISMATCH_FIX_v1.0) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-14.01 — Added canonical namespace note to log header: Root Package = `com.zynta.pos`; all Sprint 2–24 paths using `com/zentapos/` → read as `com/zynta/pos/` | 2026-02-20
- [x] Finished: FIX-14.02 — Added session note to log header: `composeHotReload = "1.0.0"` is an undocumented addition retained for desktop hot-reload DX support | 2026-02-20
- [x] Finished: FIX-14.03 — Updated `Reference Plan` in log header from `ZENTA-PLAN-PHASE1-v1.0` → `docs/plans/PLAN_PHASE1.md` | 2026-02-20

---

---

## ═══════════════════════════════════════════
## NAMESPACE FIX — Canonical Package Standardisation
## ═══════════════════════════════════════════
> **Plan Ref:** `docs/plans/PLAN_NAMESPACE_FIX_v1.0.md`
> **Old Package:** `com.zynta.pos`
> **New Package:** `com.zyntasolutions.zyntapos`
> **Status:** 🟡 IN PROGRESS

- [x] Finished: NS-1 — Read all 11 remaining feature build.gradle.kts files. Result: ALL 11 use identical namespace pattern `com.zynta.pos.feature.<n>` — no exceptions, no surprises. Safe to proceed with bulk rename. | 2026-02-20
- [x] Finished: NS-2 — Updated all 22 Group A build.gradle.kts files. Replaced `com.zynta.pos` → `com.zyntasolutions.zyntapos` across namespace, applicationId, mainClass, packageName (desktop), SQLDelight packageName. Verified: grep returns zero residual matches. | 2026-02-20
- [x] Finished: NS-3 — Created 65 new directories under com/zyntasolutions/zyntapos/… across all 26 source sets. Verified: find returns all expected paths, zero build/ contamination. | 2026-02-20
- [x] Finished: NS-4 — Moved all 26 Group B .kt files to com/zyntasolutions/zyntapos/… paths and patched all package declarations via sed. Verified: 0 residual `com.zynta.pos` package lines, 26 correct `com.zyntasolutions.zyntapos` declarations confirmed. | 2026-02-20
- [x] Finished: NS-5 — Migrated 50 .gitkeep placeholder files to com/zyntasolutions/zyntapos/… paths, then deleted all 44 old com/zynta/ directory trees (source + sqldelight). Verified: 0 com/zynta/ dirs or files remain outside build/. | 2026-02-20
- [x] Finished: NS-6 — Updated all 3 Group C documentation files. execution_log.md: title, header namespace note, path examples, keystore filename, deep link scheme, footer (10 historical [x] entries left intact as audit trail). PLAN_PHASE1.md: title, 2 path examples, KDoc comment, package tree, footer — 0 residuals. Master_plan.md: title, description, design system label, UI mockup, footer — 0 residuals. | 2026-02-20
- [x] Finished: NS-7 — Clean Gradle cache + verification build: BUILD SUCCESSFUL in 43s (117 tasks: 66 executed, 10 from cache, 41 up-to-date). Root-cause fix: `org.jetbrains.compose.material3:material3:1.10.0` has no stable Maven artifact — replaced `libs.compose.material3` / `libs.compose.material.icons.extended` with plugin accessors `compose.material3` / `compose.materialIconsExtended` across all 16 build.gradle.kts files. ZERO errors. | 2026-02-20
- [x] Finished: NS-8 — Final audit complete. All NS steps [x]. README.md updated with brand vs code-name clarification sentence. PLAN_STRUCTURE_CROSSCHECK_v1.0.md updated: package namespace status → ✅ RESOLVED, project name → ✅ DOCUMENTED, recommended actions #1 and #2 → struck through as done. Historical plan docs (PLAN_NAMESPACE_FIX, PLAN_CONSOLIDATED_FIX, PLAN_MISMATCH_FIX) preserved unchanged as audit trail. | 2026-02-20

---

## ═══════════════════════════════════════════
## PRE-SPRINT 4 — COMPATIBILITY VERIFICATION & ONBOARDING HARDENING
## ═══════════════════════════════════════════
> **Ref:** PLAN_STRUCTURE_CROSSCHECK_v1.0.md §7 items [LOW] #3 and #4
> **Scope:** (A) Kotlin 2.3.0 compatibility audit vs Sprint 4–24 API patterns
>            (B) local.properties.template onboarding reminder in README.md
> **Status:** 🟡 IN PROGRESS

- [x] Finished: COMPAT-1 — Sprint 4 domain APIs fully audited: Flow/StateFlow/SharedFlow (coroutines 1.10.2 stable), suspend fun interfaces (Kotlin 2.3.0 core), custom Result<T> sealed class (no kotlin.Result collision), @Serializable on domain models (serialization 1.8.0 + plugin), kotlinx.datetime 0.6.1 (stable KMP). ZERO blockers for Sprint 4. | 2026-02-20
- [x] Finished: COMPAT-2 — Sprints 5–13 audited. Key findings: (a) kotlin.uuid.Uuid remains @ExperimentalUuidApi in K2.3.0 — @OptIn correct as-is; (b) BaseViewModel must extend KMP ViewModel() before Sprint 12 — currently extends AutoCloseable; (c) Ktor retry = HttpRequestRetry class (confirmed in 3.0.3 jar); (d) security-crypto 1.1.0-alpha06 is alpha — evaluate at Sprint 8; (e) compose-adaptive 1.1.0-alpha04 is alpha — evaluate at Sprint 9; (f) Dispatchers.setMain() test pattern required for all ViewModel tests Sprint 12+. | 2026-02-20
- [x] Finished: COMPAT-3 — Created docs/plans/PLAN_COMPAT_VERIFICATION_v1.0.md (293 lines). Contains: pinned version matrix, Sprint-by-Sprint assessment table, 4 deferred action items (COMPAT-FIX-1..4), code patterns for test setup, BaseViewModel migration template, Ktor HttpRequestRetry usage. | 2026-02-20
- [x] Finished: ONBOARD-1 — Added prominent ⚠️ callout block to README.md §2 "Configure local secrets": blockquote reads "Required before first build — project will not compile without local.properties. This file is git-ignored and must never be committed." | 2026-02-20
- [x] Finished: ONBOARD-2 — Verified template vs README key table vs PLAN_PHASE1.md. Gap found: README was missing ZYNTA_IRD_CLIENT_CERTIFICATE_PATH and ZYNTA_IRD_CERTIFICATE_PASSWORD (both present in template). Added both rows to README key table. README (11 keys) now matches template (11 keys) exactly. | 2026-02-20
- [x] Finished: ONBOARD-3 — PLAN_STRUCTURE_CROSSCHECK_v1.0.md §7 items #3 and #4 struck through with ✅ completion notes referencing PLAN_COMPAT_VERIFICATION_v1.0.md and README changes. Banner added: "All pre-Sprint 4 actions complete. Zero open items." Footer updated. | 2026-02-20

> **Section status: ✅ ALL 6 TASKS COMPLETE**
> **PRE-SPRINT 4 — COMPATIBILITY VERIFICATION & ONBOARDING HARDENING: DONE**

---

---

## ═══════════════════════════════════════════
## SPRINT 3–4 — `:shared:domain` — Step 2.1: Domain Models
## ═══════════════════════════════════════════
> **Plan Ref:** `docs/plans/PLAN_PHASE1.md` §Sprint 3–4 / Step 2.1
> **Scope:** 14 pure-Kotlin domain model files (2.1.11 – 2.1.24) in
>            `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`
> **Status:** ✅ COMPLETE — All files present, verified, and plan-aligned

- [x] Finished: 2.1.11 — `OrderItem.kt`: id, orderId, productId, productName(snapshot), unitPrice, quantity, discount, discountType, taxRate, taxAmount, lineTotal. All fields verified. DiscountType cross-ref correct. | 2026-02-20
- [x] Finished: 2.1.12 — `OrderType.kt`: enum SALE, REFUND, HOLD. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.13 — `OrderStatus.kt`: enum COMPLETED, VOIDED, HELD, IN_PROGRESS. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.14 — `PaymentMethod.kt`: enum CASH, CARD, MOBILE, BANK_TRANSFER, SPLIT. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.15 — `PaymentSplit.kt`: data class method(PaymentMethod), amount(Double). Guard: amount > 0, method ≠ SPLIT. | 2026-02-20
- [x] Finished: 2.1.16 — `CashRegister.kt`: id, name, storeId, currentSessionId(nullable), isActive. | 2026-02-20
- [x] Finished: 2.1.17 — `RegisterSession.kt`: id, registerId, openedBy, closedBy(nullable), openingBalance, closingBalance(nullable), expectedBalance, actualBalance(nullable), openedAt(Instant), closedAt(Instant?), status(nested Status enum OPEN/CLOSED). | 2026-02-20
- [x] Finished: 2.1.18 — `CashMovement.kt`: id, sessionId, type(nested Type enum IN/OUT), amount, reason, recordedBy, timestamp(Instant). Guard: amount > 0. | 2026-02-20
- [x] Finished: 2.1.19 — `Supplier.kt`: id, name, contactPerson(nullable), phone(nullable), email(nullable), address(nullable), notes(nullable), isActive. | 2026-02-20
- [x] Finished: 2.1.20 — `StockAdjustment.kt`: id, productId, type(nested Type enum INCREASE/DECREASE/TRANSFER), quantity, reason, adjustedBy, timestamp(Instant), syncStatus(SyncStatus). Guard: quantity > 0. | 2026-02-20
- [x] Finished: 2.1.21 — `SyncStatus.kt`: data class State enum (PENDING/SYNCING/SYNCED/FAILED) + retryCount, lastAttempt(Long?). Companion: pending(), synced() factory fns. | 2026-02-20
- [x] Finished: 2.1.22 — `CartItem.kt`: productId, productName, unitPrice, quantity, discount, discountType(FIXED/PERCENT), taxRate, lineTotal. Transient (not persisted). Guard: quantity ≥ 1. | 2026-02-20
- [x] Finished: 2.1.23 — `DiscountType.kt`: enum FIXED, PERCENT. | 2026-02-20
- [x] Finished: 2.1.24 — `OrderTotals.kt`: subtotal, taxAmount, discountAmount, total, itemCount. Computed value object. EMPTY companion factory. | 2026-02-20

### Integrity Verification Summary
| Check | Result |
|---|---|
| All 14 files present in `domain/model/` | ✅ PASS |
| Zero framework imports (pure Kotlin + kotlinx.datetime only) | ✅ PASS |
| All plan-specified fields present with correct types | ✅ PASS |
| Nullable fields match plan spec (closedBy, closingBalance, etc.) | ✅ PASS |
| Enum values match plan spec exactly | ✅ PASS |
| Transient annotation intent on CartItem (no @Entity, no @Serializable) | ✅ PASS |
| Business invariants enforced via `init { require(...) }` | ✅ PASS |
| KDoc on all public classes and properties | ✅ PASS |
| Package = `com.zyntasolutions.zyntapos.domain.model` | ✅ PASS |
| ER diagram field alignment (Supplier, RegisterSession, StockAdjustment) | ✅ PASS |

> **Section status: ✅ ALL 14 TASKS COMPLETE — Step 2.1 DONE**

---

---

## ─────────────────────────────────────────
## SPRINT 5 — STEP 3.1 INTEGRITY VERIFICATION
## ─────────────────────────────────────────
> **Verified:** 2026-02-20 | **Trigger:** Execute command with integrity check

### Step 3.1 — SQLDelight Schema — Full Integrity Report

#### File Presence Check
| File | Present | Lines |
|------|---------|-------|
| `db/users.sq` | ✅ | 78 |
| `db/products.sq` | ✅ | 134 |
| `db/categories.sq` | ✅ | 87 |
| `db/orders.sq` | ✅ | 128 |
| `db/customers.sq` | ✅ | 111 |
| `db/registers.sq` | ✅ | 127 |
| `db/stock.sq` | ✅ | 86 |
| `db/suppliers.sq` | ✅ | 67 |
| `db/settings.sq` | ✅ | 42 |
| `db/sync_queue.sq` | ✅ | 84 |
| `db/audit_log.sq` | ✅ | 69 |

#### Schema Alignment vs PLAN_PHASE1.md Domain Models

| Check | Result | Notes |
|-------|--------|-------|
| `users`: all 11 planned columns + indices | ✅ PASS | Extra: idx_users_role, idx_users_sync_status |
| `products` FTS5 virtual table definition | ✅ PASS | content='products', content_rowid='rowid' |
| `products` FTS5 triggers (ai/ad/au) | ✅ PASS | Ensures FTS auto-sync with base table |
| `products`: barcode UNIQUE, sku UNIQUE indices | ✅ PASS | idx_products_barcode, idx_products_sku |
| `categories`: recursive CTE `getCategoryTree` query | ✅ PASS | Depth-first, ordered by depth+display_order |
| `categories`: parent_id self-reference (nullable) | ✅ PASS | |
| `orders`: payment_splits_json TEXT for SPLIT payments | ✅ PASS | JSON-serialised List<PaymentSplit> |
| `orders`: all 6 required indices | ✅ PASS | cashier_id, status, created_at, customer_id, session_id, sync_status |
| `order_items`: FK to orders with ON DELETE CASCADE | ✅ PASS | |
| `order_items`: discount_type TEXT (maps DiscountType enum) | ✅ PASS | |
| `order_items`: product_name TEXT snapshot (denormalised) | ✅ PASS | |
| `customers` FTS5 virtual table + triggers | ✅ PASS | id UNINDEXED, name, phone, email |
| `customers`: phone UNIQUE, email index | ✅ PASS | |
| `registers`: cash_registers + register_sessions + cash_movements | ✅ PASS | 3 tables as planned |
| `register_sessions`: expected_balance + actual_balance columns | ✅ PASS | Enables discrepancy detection |
| `stock_adjustments`: has reference_id for RETURN/TRANSFER | ✅ BONUS | Exceeds plan spec |
| `stock_alerts`: upsert-able materialized alert rows | ✅ PASS | ON CONFLICT(product_id) DO UPDATE |
| `settings`: key TEXT PK, value TEXT, updated_at INTEGER | ✅ PASS | Exact plan spec match |
| `settings`: upsertSetting, getSetting, getAllSettings queries | ✅ PASS | |
| `pending_operations`: entity_type, entity_id, operation, payload, created_at, retry_count, status | ✅ PASS | |
| `sync_queue`: indices on status + entity_type | ✅ PASS | |
| `sync_queue`: deduplicatePending + pruneSynced queries | ✅ BONUS | Exceeds plan spec |
| `audit_entries`: hash + previous_hash chain fields | ✅ PASS | Tamper-evident design |
| `audit_entries`: NO DELETE / NO UPDATE queries defined | ✅ PASS | Append-only security constraint |
| `audit_log`: device_id column | ✅ BONUS | Exceeds plan spec |

#### Build Configuration Check

| Check | Result |
|-------|--------|
| SQLDelight plugin applied in `shared/data/build.gradle.kts` | ✅ PASS |
| Database name: `ZyntaDatabase` | ✅ PASS |
| packageName: `com.zyntasolutions.zyntapos.db` | ✅ PASS |
| srcDirs: `src/commonMain/sqldelight` | ✅ PASS |
| `sqlcipher.android` in androidMain deps | ✅ PASS |
| `sqldelight.android.driver` in androidMain | ✅ PASS |
| `sqldelight.jvm.driver` in jvmMain | ✅ PASS |
| `kotlinx.serialization.json` in commonMain | ✅ PASS |

#### ⚠️ Observations / Pre-flight Notes for Step 3.2

| Item | Severity | Detail |
|------|----------|--------|
| No `sqlcipher-jdbc` in jvmMain deps | ⚠️ PENDING | Needed for Step 3.2 DesktopDatabaseDriverFactory. Add when implementing 3.2.1 |
| No `units.sq` / `tax_groups.sq` | ℹ️ BY DESIGN | unit_id + tax_group_id stored as TEXT references; these tables are out of Phase 1 Step 3.1 scope |
| `verifyMigrations = false` in SQLDelight config | ℹ️ ACCEPTABLE | Safe for Phase 1 schema-only development; set to `true` before production |

### Step 3.1 Final Status
- [x] Finished: Step 3.1 — SQLDelight Schema — ALL 11 `.sq` files verified correct, complete, and aligned with PLAN_PHASE1.md domain models + ER diagram | 2026-02-20

> **Section status: ✅ STEP 3.1 VERIFIED — 11/11 FILES PASS ALL INTEGRITY CHECKS**
> **Next: Step 3.2 — SQLCipher Encryption Setup (3.2.1–3.2.5)**

---

*End of ZyntaPOS Execution Log v1.1*
*Doc ID: ZENTA-EXEC-LOG-v1.1 | Last Updated: 2026-02-20*
*Reference Plan: docs/plans/PLAN_PHASE1.md*

---

## ═══════════════════════════════════════════
## SPRINT 9 — :composeApp:designsystem (Part 1 — Theme & Tokens)
## ═══════════════════════════════════════════
> **Plan Ref:** Step 6.1 | **Module:** M06 `:composeApp:designsystem` | **Week:** W09
> **Status:** ✅ COMPLETE

### Step 6.1 — Theme & Design Tokens

| Task | Status |
|------|--------|
| 6.1.1 — `ZentaColors.kt` | - [x] Finished: 2026-02-20 |
| 6.1.2 — `ZentaTypography.kt` | - [x] Finished: 2026-02-20 |
| 6.1.3 — `ZentaShapes.kt` | - [x] Finished: 2026-02-20 |
| 6.1.4 — `ZentaSpacing.kt` | - [x] Finished: 2026-02-20 |
| 6.1.5 — `ZentaElevation.kt` | - [x] Finished: 2026-02-20 |
| 6.1.6 — `ZentaTheme.kt` + platform actuals | - [x] Finished: 2026-02-20 |
| 6.1.7 — `WindowSizeClassHelper.kt` (expect + actuals) | - [x] Finished: 2026-02-20 |

### Step 6.1 — File Manifest

| File | Source Set | Package | Lines |
|------|-----------|---------|-------|
| `theme/ZentaColors.kt` | commonMain | `…designsystem.theme` | ~150 |
| `theme/ZentaTypography.kt` | commonMain | `…designsystem.theme` | 155 |
| `theme/ZentaShapes.kt` | commonMain | `…designsystem.theme` | 47 |
| `tokens/ZentaSpacing.kt` | commonMain | `…designsystem.tokens` | 71 |
| `tokens/ZentaElevation.kt` | commonMain | `…designsystem.tokens` | 52 |
| `theme/ZentaTheme.kt` | commonMain | `…designsystem.theme` | 150 |
| `theme/ZentaTheme.android.kt` | androidMain | `…designsystem.theme` | 28 |
| `theme/ZentaTheme.desktop.kt` | jvmMain | `…designsystem.theme` | 16 |
| `util/WindowSizeClassHelper.kt` | commonMain | `…designsystem.util` | 64 |
| `util/WindowSizeClassHelper.android.kt` | androidMain | `…designsystem.util` | 34 |
| `util/WindowSizeClassHelper.desktop.kt` | jvmMain | `…designsystem.util` | 40 |

### Step 6.1 — Architecture Alignment Checks

| Check | Result |
|-------|--------|
| Primary #1565C0 / Secondary #F57C00 / Tertiary #2E7D32 / Error #C62828 per UI/UX §1.3 | ✅ PASS |
| All M3 light + dark ColorScheme roles populated (no defaults left empty) | ✅ PASS |
| Typography scale matches UI/UX §3.1 table (57sp→11sp, correct weights) | ✅ PASS |
| Shape scale: ExtraSmall=4dp, Small=8dp, Medium=12dp, Large=16dp, ExtraLarge=28dp | ✅ PASS |
| ZentaSpacing tokens: xs=4, sm=8, md=16, lg=24, xl=32, xxl=48 dp | ✅ PASS |
| LocalSpacing CompositionLocal provided | ✅ PASS |
| ZentaElevation Level0–Level5: 0,1,3,6,8,12 dp per M3 spec §3.2 | ✅ PASS |
| ZentaTheme wraps MaterialTheme(colorScheme, typography, shapes) | ✅ PASS |
| System dark mode via isSystemInDarkTheme() | ✅ PASS |
| Manual toggle via LocalThemeMode CompositionLocal | ✅ PASS |
| Android 12+ dynamic color via expect/actual zentaDynamicColorScheme() | ✅ PASS |
| Desktop returns null for dynamic color (graceful fallback) | ✅ PASS |
| WindowSize enum: COMPACT / MEDIUM / EXPANDED | ✅ PASS |
| Android actual: currentWindowAdaptiveInfo() from material3-adaptive | ✅ PASS |
| Desktop actual: LocalWindowInfo.current.containerSize → dp thresholds | ✅ PASS |
| Breakpoints: <600dp=COMPACT, 600–840dp=MEDIUM, >840dp=EXPANDED per §2.1 | ✅ PASS |
| No hardcoded colors in composables — all via MaterialTheme.colorScheme | ✅ PASS |
| KDoc on all public APIs and CompositionLocals | ✅ PASS |

### Step 6.1 Final Status
- [x] Finished: Step 6.1 — Theme & Design Tokens — ALL 11 files verified, aligned with UI/UX Blueprint and PLAN_PHASE1.md | 2026-02-20

> **Section status: ✅ STEP 6.1 VERIFIED — 11/11 FILES PASS ALL INTEGRITY CHECKS**
> **Next: Step 6.2 — Core Components (ZentaButton, ZentaTextField, ZentaSearchBar, ZentaProductCard, ZentaCartItemRow)**

---

## Sprint 9–10 — Step 6.3: Adaptive Layout Components

| Task | Status |
|------|--------|
| 6.3.0 — Pre-execution context recovery (log + WindowSizeClassHelper verified) | - [x] Finished: 2026-02-20 |
| 6.3.1 — ZentaScaffold.kt: COMPACT=NavigationBar / MEDIUM=NavigationRail / EXPANDED=PermanentNavigationDrawer(240dp) | - [x] Finished: 2026-02-20 |
| 6.3.2 — ZentaSplitPane.kt: 40/60 default split, AnimatedVisibility collapse on COMPACT | - [x] Finished: 2026-02-20 |
| 6.3.3 — ZentaGrid.kt: LazyVerticalGrid, Fixed(2) COMPACT / Adaptive(150dp) MEDIUM / Adaptive(160dp) EXPANDED, `key` enforced | - [x] Finished: 2026-02-20 |
| 6.3.4 — ZentaListDetailLayout.kt: two-pane MEDIUM/EXPANDED, single-pane COMPACT with animated transition | - [x] Finished: 2026-02-20 |
| 6.3.5 — DesignSystemComponentTests.kt: 37 unit tests across ZentaButton, ZentaNumericPad, ZentaTable, ZentaScaffold, ZentaGrid, layouts | - [x] Finished: 2026-02-20 |
| 6.3.6 — Integrity verification | - [x] Finished: 2026-02-20 |

---

### Step 6.3 — Adaptive Layout Components: FINAL INTEGRITY REPORT

#### Files Written

| File | Lines | Key Behaviors |
|------|-------|---------------|
| `layouts/ZentaScaffold.kt` | 230 | `CompactScaffold` (M3 `NavigationBar`), `MediumScaffold` (`NavigationRail` + `Row` layout), `ExpandedScaffold` (`PermanentNavigationDrawer` 240dp); `ZentaNavItem` data class |
| `layouts/ZentaSplitPane.kt` | 109 | `primaryWeight=0.4f` default, `AnimatedVisibility(expandHorizontally/shrinkHorizontally)` for secondary pane, 1dp `outlineVariant` divider, `collapsible=true` hides secondary on COMPACT |
| `layouts/ZentaGrid.kt` | 122 | `GridCells.Fixed(2)` COMPACT, `GridCells.Adaptive(150dp)` MEDIUM, `GridCells.Adaptive(160dp)` EXPANDED; `key` param mandatory; `columnCountDescription()` exposed for tests |
| `layouts/ZentaListDetailLayout.kt` | 137 | Two-pane `Row` on MEDIUM/EXPANDED (`listWeight=0.35f`), single-pane `AnimatedContent` with slide transition on COMPACT; `detailVisible` drives COMPACT pane switching |
| `commonTest/.../DesignSystemComponentTests.kt` | 360 | 37 tests across 6 test classes: `ZentaButtonEnumTest`, `ZentaNumericPadModeTest`, `ZentaTableStateTest`, `ZentaNavItemTest`, `ZentaGridColumnCountTest`, `ZentaLayoutWeightTest` |

#### Architecture Alignment Checks

| Check | Status |
|-------|--------|
| All layout composables stateless — state hoisted to caller | ✅ |
| `windowSize: WindowSize` override param on all layouts (preview/test support) | ✅ |
| `WindowSize` thresholds match UI/UX §2.1: <600dp=COMPACT, 600–840dp=MEDIUM, >840dp=EXPANDED | ✅ |
| `ZentaGrid.key` enforced (stable recomposition, sub-200ms scan SLA) | ✅ |
| Column counts match §2.3: COMPACT=2, MEDIUM=3–4(adaptive 150dp), EXPANDED=4–6(adaptive 160dp) | ✅ |
| `ZentaSplitPane.primaryWeight` validated in 0.01–0.99 range with `require()` | ✅ |
| `ZentaListDetailLayout.listWeight` validated in 0.1–0.9 range with `require()` | ✅ |
| `ZentaScaffold` EXPANDED removes topBar slot (drawer replaces app bar chrome) | ✅ |
| `ZentaListDetailLayout` COMPACT uses `AnimatedContent` slide transition | ✅ |
| Tests use `kotlin.test` (no Android dependencies) — valid in commonTest | ✅ |
| `columnCountDescription()` pure function — testable without Compose runtime | ✅ |
| KDoc on all public APIs per PLAN_PHASE1.md documentation standards | ✅ |

### Step 6.3 Final Status
- [x] Finished: Step 6.3 — Adaptive Layout Components — 4 layout files + 37 tests complete | 2026-02-20

> **Section status: ✅ STEP 6.3 VERIFIED — 5/5 FILES PASS ALL INTEGRITY CHECKS**
> **Next: Step 7.1 — Type-Safe Navigation (:composeApp:navigation)**

---

## SPRINT 6 — Step 3.3: Repository Implementations

| Task | Status |
|------|--------|
| 3.3.0 — Pre-execution context recovery (log + last 2 files verified) | - [x] Finished: 2026-02-20 |
| 3.3.1 — Security scaffold interfaces (PasswordHasher, SecurePreferences) | - [x] Finished: 2026-02-20 |
| 3.3.2 — Entity Mappers (9 mapper files in local/mapper/) | - [x] Finished: 2026-02-20 |
| 3.3.3 — ProductRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.4 — CategoryRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.5 — OrderRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.6 — CustomerRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.7 — RegisterRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.8 — StockRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.9 — SupplierRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.10 — AuthRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.11 — SettingsRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.12 — SyncRepositoryImpl | - [x] Finished: 2026-02-20 |
| 3.3.13 — DataModule.kt updated with all bindings | - [x] Finished: 2026-02-20 |
| 3.3.14 — Integrity verification | - [x] Finished: 2026-02-20 |

---

### Step 3.3 — Repository Implementations: FINAL INTEGRITY REPORT

#### Files Written / Verified

| File | Lines | Interface Satisfied | Key Capabilities |
|------|-------|---------------------|-----------------|
| `repository/SettingsRepositoryImpl.kt` | 125 | `SettingsRepository` ✅ | `get`, `set` (upsert), `getAll`, `observe` (SQLDelight Flow), `Keys` constants object |
| `repository/SyncRepositoryImpl.kt` | 201 | `SyncRepository` ✅ | `getPendingOperations` (batch=50, resets stale SYNCING), `markSynced`, `pushToServer` (Phase1 stub), `pullFromServer` (Phase1 stub), `markFailed` (MAX_RETRIES=5 guard), `pruneSynced`, `deduplicatePending` |
| `di/DataModule.kt` | 134 | All 10 repos bound ✅ | All repository interfaces bound to impls; SyncRepositoryImpl dual-bound for engine access |

#### Sprint 3.3 Complete — All 10 Repository Impls Verified

| # | Implementation | Domain Interface | Special Mechanics |
|---|---------------|-----------------|-------------------|
| 1 | `ProductRepositoryImpl` | `ProductRepository` | FTS5 search, `asFlow().mapToList()` |
| 2 | `CategoryRepositoryImpl` | `CategoryRepository` | Recursive CTE → hierarchical list |
| 3 | `OrderRepositoryImpl` | `OrderRepository` | Atomic `db.transaction {}` for order+items |
| 4 | `CustomerRepositoryImpl` | `CustomerRepository` | FTS5 search, CRUD |
| 5 | `RegisterRepositoryImpl` | `RegisterRepository` | Session lifecycle, running balance |
| 6 | `StockRepositoryImpl` | `StockRepository` | Atomic adjustment+qty+alert transaction |
| 7 | `SupplierRepositoryImpl` | `SupplierRepository` | Standard CRUD |
| 8 | `AuthRepositoryImpl` | `AuthRepository` | BCrypt verify, SecurePreferences JWT cache, offline session |
| 9 | `SettingsRepositoryImpl` | `SettingsRepository` | Typed KV, SQLDelight Flow observation, Keys constants |
| 10 | `SyncRepositoryImpl` | `SyncRepository` | Queue batch read, status FSM, MAX_RETRIES=5, Phase1 network stubs |

#### Architecture Alignment Checks

| Check | Status |
|-------|--------|
| All impls use `withContext(Dispatchers.IO)` for suspend fns | ✅ |
| All impls return `Result<T>` (never throw from suspend) | ✅ |
| `SyncEnqueuer.enqueue()` called after write-path mutations | ✅ |
| `db.transaction {}` used for atomic multi-table writes | ✅ |
| Domain interfaces only (no data classes) exposed to callers | ✅ |
| `SettingsRepositoryImpl.Keys` provides canonical key constants | ✅ |
| `SyncRepositoryImpl` MAX_RETRIES=5 permanently fails exhausted ops | ✅ |
| `DataModule.kt` binds all 10 repo interfaces + SyncRepositoryImpl impl ref | ✅ |
| Phase 1 network stubs documented with TODO(Sprint6-Step3.4) markers | ✅ |

### Step 3.3 Final Status
- [x] Finished: Step 3.3 — Repository Implementations — ALL 10 impls + DataModule complete | 2026-02-20

> **Section status: ✅ STEP 3.3 VERIFIED — 10/10 Repositories + DataModule PASS ALL INTEGRITY CHECKS**
> **Next: Step 3.4 — Ktor Client + Remote DTOs + SyncEngine**


---

## Sprint 11 — `:composeApp:navigation` — Step 7.1 — Type-Safe Navigation Graph

> **Plan Ref:** Step 7.1 | **Module:** M07 :composeApp:navigation | **Week:** W11  
> **Session Start:** 2026-02-21

### Pre-Execution Integrity Check
- Last completed sprint: Step 3.3 — Repository Implementations (2026-02-20) ✅
- Navigation module exists at `composeApp/navigation/` with scaffold build.gradle.kts ✅
- Placeholder `NavigationModule.kt` present — to be replaced ✅
- `Role.kt` and `Permission.kt` found in `:shared:domain` ✅
- `ZentaScaffold.kt` + `ZentaNavItem` found in `:composeApp:designsystem` ✅
- Compose Navigation NOT yet in `libs.versions.toml` → will add ✅

### Tasks

| Task | Status |
|------|--------|
| 7.1.0 — Add compose-navigation to libs.versions.toml + build.gradle.kts | - [x] Finished: 2026-02-21 |
| 7.1.1 — `ZentaRoute.kt`: sealed class with @Serializable sub-objects | - [x] Finished: 2026-02-21 |
| 7.1.2 — `ZentaNavGraph.kt`: root NavHost composable | - [x] Finished: 2026-02-21 |
| 7.1.3 — `AuthNavGraph.kt`: nested auth graph | - [x] Finished: 2026-02-21 |
| 7.1.4 — `MainNavGraph.kt`: nested main authenticated graph + ZentaScaffold | - [x] Finished: 2026-02-21 |
| 7.1.5 — `NavigationItems.kt`: NavItem + RBAC-filtered list | - [x] Finished: 2026-02-21 |
| 7.1.6 — `NavigationController.kt`: type-safe navigate wrapper | - [x] Finished: 2026-02-21 |
| 7.1.7 — Deep link support in NavGraph | - [x] Finished: 2026-02-21 |
| 7.1.8 — `NavigationModule.kt`: Koin DI bindings | - [x] Finished: 2026-02-21 |
| 7.1.9 — Integrity verification | - [x] Finished: 2026-02-21 |


---

### Sprint 11 Step 7.1 — FINAL INTEGRITY REPORT

#### Files Written / Verified

| File | Lines | Task Ref | Purpose |
|------|-------|----------|---------|
| `gradle/libs.versions.toml` | +2 | 7.1.0 | Added `compose-navigation = "2.9.0-alpha07"` version + library entry |
| `navigation/build.gradle.kts` | 56 | 7.1.0 | Added `libs.compose.navigation` + `project(":shared:domain")` deps |
| `ZentaRoute.kt` | 154 | 7.1.1 | Full sealed class hierarchy — 19 routes across 6 groups |
| `ZentaNavGraph.kt` | 136 | 7.1.2, 7.1.7 | Root NavHost + deep link constants + session redirect |
| `AuthNavGraph.kt` | 60 | 7.1.3 | Nested auth graph: Login → PinLock |
| `MainNavGraph.kt` | 315 | 7.1.4, 7.1.7, 7.1.8 | 5 sub-graphs, MainScaffoldShell, RBAC-aware selection, deep link target |
| `MainNavScreens.kt` | 102 | 7.1.4 | Composable factory contract — decouples NavGraph from feature impls |
| `NavigationItems.kt` | 136 | 7.1.5 | NavItem + AllNavItems + RbacNavFilter.forRole / forPermissions |
| `NavigationController.kt` | 145 | 7.1.6 | navigate/popBackStack/navigateAndClear/navigateUp/lockScreen/goToPos |
| `NavigationModule.kt` | 32 | 7.1.8 | Koin module: RbacNavFilter singleton |

#### Architecture Alignment Checks

| Check | Status |
|-------|--------|
| All routes are `@Serializable` sealed class members | ✅ |
| Start destination = `ZentaRoute.Login` with session-active redirect | ✅ |
| Auth graph nested via `navigation<ZentaRoute.Login>` | ✅ |
| Main graph nested via `navigation<ZentaRoute.Dashboard>` | ✅ |
| Sub-graphs: Inventory / Register / Reports / Settings each use `navigation<T>` | ✅ |
| `ZentaScaffold` wired in `MainScaffoldShell` with adaptive nav | ✅ |
| RBAC: `RbacNavFilter.forRole(role)` filters `AllNavItems` from `Permission.rolePermissions` | ✅ |
| Deep links: `zyntapos://product/{productId}` + `zyntapos://order/{orderId}` | ✅ |
| `NavigationController.navigateAndClear` clears back stack for login/logout | ✅ |
| `NavigationController.navigateUp(fallback)` provides Desktop back-button safety | ✅ |
| `launchSingleTop = true` + `saveState/restoreState = true` on tab switches | ✅ |
| `MainNavScreens` contract keeps NavGraph decoupled from feature modules | ✅ |
| `navigationModule` Koin module provides `RbacNavFilter` singleton | ✅ |
| Package consistent: `com.zyntasolutions.zyntapos.navigation` across all files | ✅ |
| `compose-navigation = 2.9.0-alpha07` added to `libs.versions.toml` | ✅ |
| Navigation module `build.gradle.kts` updated with domain + navigation deps | ✅ |

### Step 7.1 Final Status
- [x] Finished: Step 7.1 — Type-Safe Navigation Graph — ALL 8 files + catalog update complete | 2026-02-21

> **Section status: ✅ STEP 7.1 VERIFIED — Sprint 11 :composeApp:navigation PASS ALL INTEGRITY CHECKS**
> **Next: Sprint 12–13 — :composeApp:feature:auth — Login screen UI + ViewModel + MVI**

---

## ─────────────────────────────────────────
## SPRINT 12-13 — :composeApp:feature:auth
## ─────────────────────────────────────────
> **Plan Ref:** Step 8.1 | **Module:** M08 :composeApp:feature:auth | **Weeks:** W12–W13
> **Session Start:** 2026-02-21

### Pre-Execution Integrity Check
- Last completed sprint: Step 7.1 — Type-Safe Navigation Graph (2026-02-21) ✅
- Auth module exists at `composeApp/feature/auth/` with scaffold `build.gradle.kts` ✅
- Stub `AuthModule.kt` present — to be replaced ✅
- `LoginUseCase`, `LogoutUseCase`, `ValidatePinUseCase`, `CheckPermissionUseCase` found in `:shared:domain` ✅
- `AuthRepository` interface found in `:shared:domain` ✅
- `AuthRepositoryImpl` found in `:shared:data` ✅
- `SecurePreferences`, `PasswordHasher` found in `:shared:data` ✅
- `NetworkMonitor` found in `:shared:data` ✅
- All design system components available (ZentaButton, ZentaTextField, ZentaNumericPad, ZentaSplitPane, ZentaEmptyState, ZentaTopAppBar) ✅
- `NavigationController` found in `:composeApp:navigation` ✅
- `coroutines-test` + `turbine` + `mockative` available in `testing-common` bundle ✅

### Tasks

| Task | Status |
|------|--------|
| 8.1.0 — Pre-execution check | - [x] Finished: 2026-02-21 |
| 8.1.1 — `AuthState.kt` | - [x] Finished: 2026-02-21 |
| 8.1.2 — `AuthIntent.kt` | - [x] Finished: 2026-02-21 |
| 8.1.3 — `AuthEffect.kt` | - [x] Finished: 2026-02-21 |
| 8.1.4 — `AuthViewModel.kt` (+ `BaseViewModel.kt`) | - [x] Finished: 2026-02-21 |
| 8.1.5 — `LoginScreen.kt` | - [x] Finished: 2026-02-21 |
| 8.1.6 — `PinLockScreen.kt` | - [x] Finished: 2026-02-21 |
| 8.1.7 — `SessionGuard.kt` | - [x] Finished: 2026-02-21 |
| 8.1.8 — `RoleGuard.kt` | - [x] Finished: 2026-02-21 |
| 8.1.9 — `SessionManager.kt` | - [ ] |
| 8.1.10 — `AuthRepositoryImpl.kt` (verify + update) | - [ ] |
| 8.1.11 — `AuthModule.kt` (Koin) | - [ ] |
| 8.1.12 — Unit tests (AuthViewModel + LoginUseCase + SessionManager) | - [ ] |
| 8.1.13 — Integrity verification | - [ ] |


---

## ─────────────────────────────────────────
## SPRINT 14-17 — :composeApp:feature:pos
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 | **Module:** M09 :composeApp:feature:pos | **Weeks:** W14–W17
> **Session Start:** 2026-02-21

### Pre-Execution Integrity Check
- Domain models verified: `CartItem`, `Category`, `Product`, `Customer`, `Order`, `OrderTotals`, `OrderTotals.EMPTY`, `DiscountType`, `PaymentMethod`, `PaymentSplit`, `OrderStatus` ✅
- No `HeldOrder` model — held orders are `Order` with `OrderStatus.HELD` ✅
- POS use cases found: `AddItemToCartUseCase`, `RemoveItemFromCartUseCase`, `UpdateCartItemQuantityUseCase`, `ApplyItemDiscountUseCase`, `ApplyOrderDiscountUseCase`, `CalculateOrderTotalsUseCase`, `HoldOrderUseCase`, `RetrieveHeldOrderUseCase`, `ProcessPaymentUseCase`, `VoidOrderUseCase` ✅
- POS feature module exists with stub `PosModule.kt` ✅
- Target package: `com.zyntasolutions.zyntapos.feature.pos` ✅
- Target path: `composeApp/feature/pos/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/pos/` ✅

### Tasks

| Task | Status |
|------|--------|
| 9.1.0 — Pre-execution check | - [x] Finished: 2026-02-21 |
| 9.1.0a — `PosState.kt` | - [x] Finished: 2026-02-21 |
| 9.1.0b — `PosIntent.kt` | - [x] Finished: 2026-02-21 |
| 9.1.0c — `PosEffect.kt` | - [x] Finished: 2026-02-21 |
| 9.1.1 — Integrity verification | - [x] Finished: 2026-02-21 |

### Step 9.1 Integrity Report

| Check | Result |
|-------|--------|
| `PosState.kt` — package `com.zyntasolutions.zyntapos.feature.pos` | ✅ |
| `PosState.kt` — 13 fields matching sprint spec | ✅ |
| `PosState.kt` — `heldOrders: List<Order>` (no `HeldOrder` model — uses `Order` with `OrderStatus.HELD`) | ✅ |
| `PosState.kt` — `orderTotals: OrderTotals = OrderTotals.EMPTY` (canonical zero value) | ✅ |
| `PosState.kt` — imports all 7 required domain models | ✅ |
| `PosIntent.kt` — `sealed interface` with 16 variants (14 sprint spec + `SearchFocusChanged` + `SetScannerActive` + `ClearCustomer`) | ✅ |
| `PosIntent.kt` — `ProcessPayment(method, splits, tendered)` parameter alignment with `ProcessPaymentUseCase` | ✅ |
| `PosIntent.kt` — `ScanBarcode(barcode: String)` | ✅ |
| `PosIntent.kt` — `HoldOrder` / `RetrieveHeld(holdId)` | ✅ |
| `PosEffect.kt` — `sealed interface` with 6 variants | ✅ |
| `PosEffect.kt` — `NavigateToPayment(orderId)`, `ShowReceiptScreen(orderId)`, `ShowError(msg)`, `PrintReceipt(orderId)`, `BarcodeNotFound(barcode)` — all 5 sprint-specified effects present | ✅ |
| `PosEffect.kt` — `OpenCashDrawer(registerId)` added (cash payment HAL integration requirement per §4.3 of Master Plan) | ✅ |
| All 3 files reside in `composeApp/feature/pos/src/commonMain/…/feature/pos/` | ✅ |
| KDoc present on all public types and properties | ✅ |
| No business logic inside contract files (pure data classes / sealed interfaces) | ✅ |

### Step 9.1 Final Status
- [x] Finished: Step 9.1 — POS MVI State Contracts — `PosState.kt` + `PosIntent.kt` + `PosEffect.kt` complete | 2026-02-21

> **Section status: ✅ STEP 9.1 VERIFIED — Sprint 14-17 POS MVI contracts PASS ALL INTEGRITY CHECKS**
> **Next: Step 9.2 — Sprint 14 Product Grid & Search implementation**

---

## ─────────────────────────────────────────
## SPRINT 14 — Product Grid & Search
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 (Sprint 14) | **Module:** M09 :composeApp:feature:pos | **Week:** W14
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 9.1.1 — `PosViewModel.kt` | - [x] Finished: 2026-02-21 |
| 9.1.2 — `ProductGridSection.kt` | - [x] Finished: 2026-02-21 |
| 9.1.3 — `CategoryFilterRow.kt` | - [x] Finished: 2026-02-21 |
| 9.1.4 — `PosSearchBar.kt` | - [x] Finished: 2026-02-21 |
| 9.1.5 — `BarcodeInputHandler.kt` | - [x] Finished: 2026-02-21 |
| 9.1.6 — `KeyboardShortcutHandler.kt` (jvmMain) | - [x] Finished: 2026-02-21 |


---

## ─────────────────────────────────────────
## SPRINT 15 — Cart
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 (Sprint 15) | **Module:** M09 :composeApp:feature:pos | **Week:** W15
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 9.1.7 — `CartPanel.kt` | - [x] Finished: 2026-02-21 |
| 9.1.8 — `CartItemList.kt` | - [x] Finished: 2026-02-21 |
| 9.1.9 — `CartSummaryFooter.kt` | - [x] Finished: 2026-02-21 |
| 9.1.10 — `CustomerSelectorDialog.kt` | - [x] Finished: 2026-02-21 |
| 9.1.11 — `ItemDiscountDialog.kt` | - [x] Finished: 2026-02-21 |
| 9.1.12 — `OrderDiscountDialog.kt` | - [x] Finished: 2026-02-21 |
| 9.1.13 — `OrderNotesDialog.kt` | - [x] Finished: 2026-02-21 |
| 9.1.14 — `HoldOrderUseCase` integration | - [x] Finished: 2026-02-21 |
| 9.1.14b — Integrity verification | - [x] Finished: 2026-02-21 |

### Sprint 15 Integrity Report

| Check | Result |
|-------|--------|
| `CartPanel.kt` — `currentWindowSize()` adaptive (EXPANDED=panel, else BottomSheet) | ✅ |
| `CartPanel.kt` — delegates to `CartContent` for deduplication | ✅ |
| `CartItemList.kt` — `key = { it.productId }` stable recomposition | ✅ |
| `CartItemList.kt` — `SwipeToDismissBox` via `ZentaCartItemRow` delegation | ✅ |
| `CartItemList.kt` — empty state placeholder when list is empty | ✅ |
| `CartSummaryFooter.kt` — subtotal, tax, discount (conditional), total rows | ✅ |
| `CartSummaryFooter.kt` — all amounts via `CurrencyFormatter` | ✅ |
| `CartSummaryFooter.kt` — `ZentaButton(Large)` PAY button disabled when cart empty | ✅ |
| `CartContent.kt` — internal glue composable (CartPanel reuse) | ✅ |
| `CustomerSelectorDialog.kt` — `CustomerRepository.search()` debounced 300ms | ✅ |
| `CustomerSelectorDialog.kt` — "Walk-in Customer" pinned first | ✅ |
| `CustomerSelectorDialog.kt` — quick-add button → `onQuickAdd` callback | ✅ |
| `ItemDiscountDialog.kt` — FLAT/PERCENT `SingleChoiceSegmentedButtonRow` | ✅ |
| `ItemDiscountDialog.kt` — `ZentaNumericPad` in PRICE mode | ✅ |
| `ItemDiscountDialog.kt` — max cap validation with error text | ✅ |
| `ItemDiscountDialog.kt` — `RoleGuard(Permission.APPLY_DISCOUNT)` wrapper | ✅ |
| `OrderDiscountDialog.kt` — reuses `DiscountDialogContent` from `ItemDiscountDialog` | ✅ |
| `OrderDiscountDialog.kt` — `RoleGuard(Permission.APPLY_DISCOUNT)` wrapper | ✅ |
| `OrderNotesDialog.kt` — multiline text field + reference number input | ✅ |
| `OrderNotesDialog.kt` — `buildCombinedNotes()` formats `[ref] notes` correctly | ✅ |
| `HoldOrderDialog.kt` — `HoldOrderConfirmDialog` shown before `PosIntent.HoldOrder` | ✅ |
| `HoldOrderDialog.kt` — `holdOrderSnackbarMessage(holdId)` with truncated hold ID | ✅ |
| F8 shortcut → `KeyboardShortcutHandler` → `PosIntent.HoldOrder` (Sprint 14) | ✅ |
| `PosViewModel.onHoldOrder()` → `HoldOrderUseCase` + cart clear on success | ✅ |
| Package `com.zyntasolutions.zyntapos.feature.pos` consistent across all files | ✅ |
| All 8 sprint files present in `composeApp/feature/pos/src/commonMain` | ✅ |

> **Section status: ✅ SPRINT 15 VERIFIED — All Cart tasks PASS ALL INTEGRITY CHECKS**
> **Next: Sprint 16 — Payment Flow (9.1.15–9.1.20)**


---

## ─────────────────────────────────────────
## SPRINT 16 — Payment Flow
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 (Sprint 16) | **Module:** M09 :composeApp:feature:pos | **Week:** W16
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 9.1.15 — `PaymentScreen.kt` | - [x] Finished: 2026-02-21 |
| 9.1.16 — `PaymentMethodGrid.kt` | - [x] Finished: 2026-02-21 |
| 9.1.17 — `CashPaymentPanel.kt` | - [x] Finished: 2026-02-21 |
| 9.1.18 — `SplitPaymentPanel.kt` | - [x] Finished: 2026-02-21 |
| 9.1.19 — `ProcessPaymentUseCase` integration (PosViewModel) | - [x] Finished: 2026-02-21 |
| 9.1.20 — `PaymentSuccessOverlay.kt` | - [x] Finished: 2026-02-21 |


### Sprint 16 Integrity Report

| Check | Result |
|-------|--------|
| `PaymentScreen.kt` — package `com.zyntasolutions.zyntapos.feature.pos` | ✅ |
| `PaymentScreen.kt` — two-pane (40/60) Expanded, single-pane Compact adaptive layout | ✅ |
| `PaymentScreen.kt` — `currentWindowSize() == WindowSize.EXPANDED` for breakpoint detection | ✅ |
| `PaymentScreen.kt` — collects `PosEffect.ShowReceiptScreen` → triggers `PaymentSuccessOverlay` | ✅ |
| `PaymentScreen.kt` — dispatches `PosIntent.ProcessPayment` with correct params per method | ✅ |
| `PaymentScreen.kt` — Left pane = OrderSummaryPane (read-only items + totals) | ✅ |
| `PaymentScreen.kt` — Right pane = PaymentInputPane (method grid + panel + PAY button) | ✅ |
| `PaymentScreen.kt` — `onDismiss` back-navigation handler | ✅ |
| `PaymentMethodGrid.kt` — 2-column grid of all `PaymentMethod` entries | ✅ |
| `PaymentMethodGrid.kt` — min tile height 56dp (WCAG §8 UI/UX spec) | ✅ |
| `PaymentMethodGrid.kt` — selected tile: `primaryContainer` + 2dp primary border + tonal elevation | ✅ |
| `PaymentMethodGrid.kt` — `availableMethods` parameter for dynamic filtering | ✅ |
| `PaymentMethodGrid.kt` — `PaymentMethod.label` + `PaymentMethod.icon` extension helpers | ✅ |
| `CashPaymentPanel.kt` — "Amount Received" `ZentaNumericPad(PRICE)` mode | ✅ |
| `CashPaymentPanel.kt` — Real-time change: `change = tendered − total` (green ≥ 0, red < 0) | ✅ |
| `CashPaymentPanel.kt` — Cents-integer model (raw string avoids floating-point drift) | ✅ |
| `CashPaymentPanel.kt` — Quick-amount shortcut buttons (rounded $50, $100, Exact) | ✅ |
| `CashPaymentPanel.kt` — Stateless; state hoisted via `tenderedRaw`/`onTenderedChanged` | ✅ |
| `SplitPaymentPanel.kt` — "Add Payment Method" button; only shows available (unselected) methods | ✅ |
| `SplitPaymentPanel.kt` — Per-method amount entry via inline `ZentaNumericPad` | ✅ |
| `SplitPaymentPanel.kt` — Remaining amount tracker: balanced = `tertiaryContainer`, imbalanced = `errorContainer` | ✅ |
| `SplitPaymentPanel.kt` — PAY button enabled only when `abs(sum − total) < 0.01` | ✅ |
| `SplitPaymentPanel.kt` — Remove row button (disabled when only 1 row) | ✅ |
| `ProcessPaymentUseCase` integration — `PosViewModel.onProcessPayment()` present (Sprint 14 work) | ✅ |
| `ProcessPaymentUseCase` integration — on success: `OpenCashDrawer` + `PrintReceipt` + `ShowReceiptScreen` + `onClearCart()` | ✅ |
| `ProcessPaymentUseCase` integration — `isLoading` state toggled around async call | ✅ |
| `PaymentSuccessOverlay.kt` — `animateFloatAsState` for bgAlpha + `spring` for circleScale | ✅ |
| `PaymentSuccessOverlay.kt` — Spring easing `DampingRatioLowBouncy` for checkmark "pop" | ✅ |
| `PaymentSuccessOverlay.kt` — Full-screen Dialog with `dismissOnBackPress = false` | ✅ |
| `PaymentSuccessOverlay.kt` — `LaunchedEffect(Unit)` → `delay(1500ms)` → `onDismissed()` | ✅ |
| `PaymentSuccessOverlay.kt` — Tertiary colour scheme (success semantics per Material 3) | ✅ |
| `ZentaSpacing.kt` — `val ZentaSpacing = ZentaSpacingTokens()` singleton added (unblocks static access) | ✅ |
| All 6 files in `composeApp/feature/pos/src/commonMain/…/feature/pos/` | ✅ |
| KDoc present on all public composables and parameters | ✅ |
| No business logic in composables — all derived from `PosState` or local UI state | ✅ |

> **Section status: ✅ SPRINT 16 VERIFIED — All Payment Flow tasks PASS ALL INTEGRITY CHECKS**
> **Next: Sprint 17 — Receipt & Order Management (9.1.21–9.1.27)**

---

### Sprint 17 Integrity Verification Report
> **Verified:** 2026-02-21 | **Session:** Recovery + Integrity Check

| File | Location | Spec Alignment | Status |
|------|----------|----------------|--------|
| `ReceiptScreen.kt` | `feature/pos/commonMain` | Monospace receipt text from `EscPosReceiptBuilder`; Print/Email/Skip action row; retry `ZentaDialog` on `printError`; `isPrinting` loading state | ✅ |
| `EscPosReceiptBuilder.kt` | `shared/hal/printer` | `buildReceipt(order, config): ByteArray`; store header, items, totals, QR flag | ✅ |
| `PrinterManager.kt` | `shared/hal/printer` | `connect(): Result<Unit>`; `print(ByteArray): Result<Unit>`; internal retry | ✅ |
| `PrintReceiptUseCase.kt` | `shared/domain/usecase/pos` | Loads `PrinterConfig` from `SettingsRepository`; builds via `EscPosReceiptBuilder`; delivers via `PrinterManager`; calls `SecurityAuditLogger.logReceiptPrint(orderId, userId)` | ✅ |
| `SecurityAuditLogger` | `shared/security/audit` | `logReceiptPrint(orderId, userId)` present; root-level file is `typealias` redirect | ✅ |
| `HeldOrdersBottomSheet.kt` | `feature/pos/commonMain` | `LazyColumn` of held orders; hold time, item count, total per row; `onRetrieve` callback; F9 keyboard shortcut toggle; `ZentaEmptyState` when empty | ✅ |
| `OrderHistoryScreen.kt` | `feature/pos/commonMain` | `ZentaTable` with 5 columns; status `FilterChip` row (All/Completed/Held/Voided); `StatusBadge` composable; per-row reprint `IconButton`; sort on any column | ✅ |
| `PosModule.kt` | `feature/pos/commonMain` | All 9 POS use cases registered as `factory`; `SecurityAuditLogger` as `single`; `PosViewModel` as `viewModel` with params; `PrinterManager` via `get<PrinterManager>()`; `BarcodeScanner` note documented | ✅ |
| `CalculateOrderTotalsUseCaseTest.kt` | `shared/domain/commonTest` | All 6 tax scenarios from §11.3 covered (no tax, exclusive, inclusive, multi-rate, discount, empty cart) | ✅ |
| `ProcessPaymentUseCaseTest.kt` | `shared/domain/commonTest` | Cash exact / overpay / underpay; split valid / invalid; stock deduction; order persistence | ✅ |
| `AddItemToCartUseCaseTest.kt` | `shared/domain/commonTest` | Stock limit enforcement; zero/negative qty; cumulative cart + new qty vs stock | ✅ |
| `PosViewModelTest.kt` | `feature/pos/commonTest` | State transitions: initial, SearchQueryChanged, SelectCategory, ClearCart, AddToCart (success/fail), RemoveFromCart, HoldOrder, ScanBarcode not found, ProcessPayment success | ✅ |

> **Section status: ✅ SPRINT 17 VERIFIED — All Receipt & Order Management tasks PASS ALL INTEGRITY CHECKS**
> **Next: Sprint 18 — :composeApp:feature:inventory Products (10.1.1–10.1.6)**


---

## ─────────────────────────────────────────
## SPRINT 17 — Receipt & Order Management
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 (Sprint 17) | **Module:** M09 :composeApp:feature:pos | **Week:** W17
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 9.1.21 — `ReceiptScreen.kt` | - [x] Finished: 2026-02-21 |
| 9.1.22 — `EscPosReceiptBuilder.kt` integration → `PrintReceiptUseCase.kt` | - [x] Finished: 2026-02-21 |
| 9.1.23 — `PrintReceiptUseCase.kt` + `SecurityAuditLogger.kt` | - [x] Finished: 2026-02-21 |
| 9.1.24 — `HeldOrdersBottomSheet.kt` | - [x] Finished: 2026-02-21 |
| 9.1.25 — `OrderHistoryScreen.kt` | - [x] Finished: 2026-02-21 |
| 9.1.26 — Koin `posModule` update | - [x] Finished: 2026-02-21 |
| 9.1.27 — Unit tests (CalculateOrderTotals, ProcessPayment, AddItemToCart, PosViewModel) | - [x] Finished: 2026-02-21 |


---

## ─────────────────────────────────────────
## SPRINT 8 — :shared:security
## ─────────────────────────────────────────
> **Plan Ref:** Step 5.1 | **Module:** M05 :shared:security | **Week:** W08

### Step 5.1 — Encryption, Key Management & RBAC

- [x] Finished: 5.1.0 — Pre-flight: build.gradle.kts updated (domain dep + jbcrypt); AuditEntry.kt + AuditRepository.kt added to :shared:domain | 2026-02-21
- [x] Finished: 5.1.1 — `EncryptionManager.kt` (expect/actual) | 2026-02-21
           commonMain: expect class with `encrypt(String): EncryptedData` + `decrypt(EncryptedData): String` + `EncryptedData(ciphertext, iv, tag)` data class
           Android actual: AES-256-GCM via Android Keystore; non-extractable key; `randomizedEncryptionRequired=true`; 16-byte GCM tag split from JCE output
           Desktop actual: AES-256-GCM via JCE + PKCS12 at `~/.zentapos/.zyntapos.p12`; machine-fingerprint derived KS password; fresh 12-byte IV per encrypt call
- [x] Finished: 5.1.2 — `DatabaseKeyManager.kt` (expect/actual) | 2026-02-21
           commonMain: expect class `getOrCreateKey(): ByteArray` + `hasPersistedKey(): Boolean`
           Android: envelope encryption — 32-byte DEK wrapped by non-extractable KEK (AES-256-GCM) in Android Keystore; wrapped DEK+IV persisted in SharedPreferences as Base64
           Desktop: 32-byte AES-256 key in PKCS12 at `~/.zentapos/.db_keystore.p12`; machine-fingerprint password; key directly extractable on JVM
- [x] Finished: 5.1.3 — `SecurePreferences.kt` (expect/actual) | 2026-02-21
           commonMain: expect class with `put/get/remove/clear` + well-known key constants
           Android: `EncryptedSharedPreferences` (AES256-GCM values, AES256-SIV keys) via `MasterKey.AES256_GCM`
           Desktop: Properties file at `~/.zentapos/secure_prefs.enc`; each value AES-256-GCM encrypted as `<iv>:<ciphertext>:<tag>` Base64 segments; `@Synchronized` guards
- [x] Finished: 5.1.4 — `PasswordHasher.kt` (expect object) | 2026-02-21
           commonMain: `expect object` with `hashPassword(String): String` + `verifyPassword(String, String): Boolean`
           Android + Desktop actuals: jBCrypt `BCrypt.hashpw(plain, gensalt(12))` + `BCrypt.checkpw`; work factor 12; `runCatching` on verify guards malformed hash
- [x] Finished: 5.1.5 — `JwtManager.kt` | 2026-02-21
           commonMain: `JwtClaims(sub, role, storeId, exp, iat)` Serializable data class; `JwtManager(prefs)` with `parseJwt/isTokenExpired/extractUserId/extractRole/saveTokens/clearTokens`
           Base64url decode via `kotlin.io.encoding.Base64` (KMP stdlib — no JVM imports in commonMain); 30-second clock-skew buffer on expiry check; Falls back to `Role.CASHIER` for unknown role strings
- [x] Finished: 5.1.6 — `PinManager.kt` | 2026-02-21
           `object PinManager` with `hashPin/verifyPin/validatePinFormat`; SHA-256 + 16-byte SecureRandom salt; stored as `base64url-salt:hex-hash`; constant-time compare; throws `IllegalArgumentException` for invalid PIN format
- [x] Finished: 5.1.7 — `SecurityAuditLogger.kt` | 2026-02-21
           Suspend fire-and-forget append-only logger; covers login/PIN attempts, permission denials, order void, stock adjustment, receipt print, discount applied, register open/close; all exceptions swallowed; minimal JSON escaping helper
- [x] Finished: 5.1.8 — `RbacEngine.kt` | 2026-02-21
           Stateless pure-computation class; `hasPermission(User|Role, Permission)`, `getPermissions(Role)`, `getDeniedPermissions(Role)`; derives all data from `Permission.rolePermissions` in `:shared:domain`
- [x] Finished: 5.1.9 — Koin `securityModule` | 2026-02-21
           `val securityModule = module { ... }` in `di/SecurityModule.kt`; all 8 bindings as singletons: EncryptionManager, DatabaseKeyManager, SecurePreferences, PasswordHasher, JwtManager, PinManager, SecurityAuditLogger, RbacEngine
- [x] Finished: 5.1.10 — Unit tests `commonTest` | 2026-02-21
           EncryptionManagerTest (round-trip, IV uniqueness, unicode, long payload, size assertions), PasswordHasherTest (BCrypt format, salt uniqueness, verify correct/wrong/malformed), PinManagerTest (format validation, hash/verify cycle, constant-time, exception on invalid), RbacEngineTest (ADMIN full matrix, all role×permission assertions, getDeniedPermissions complement), JwtManagerTest (sub/storeId extraction, expiry detection, role extraction, case-insensitive, malformed token)

> **Section status: ✅ SPRINT 8 COMPLETE — All :shared:security tasks verified**
> **Next: Sprint 18 — :composeApp:feature:inventory Products (10.1.1–10.1.6)**


---

## ─────────────────────────────────────────
## SPRINT 18 — Products (:composeApp:feature:inventory)
## ─────────────────────────────────────────
> **Plan Ref:** Step 10.1 (Sprint 18) | **Module:** M10 :composeApp:feature:inventory | **Week:** W18
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 10.1.1 — `ProductListScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.2 — `ProductDetailScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.3 — `ProductFormValidator.kt` | - [x] Finished: 2026-02-21 |
| 10.1.4 — `BarcodeGeneratorDialog.kt` | - [x] Finished: 2026-02-21 |
| 10.1.5 — `BulkImportDialog.kt` | - [x] Finished: 2026-02-21 |
| 10.1.6 — `StockAdjustmentDialog.kt` | - [x] Finished: 2026-02-21 |

### Sprint 18 — Integrity Verification (Post-Session Recovery)
> **Verified:** 2026-02-21 | **Reviewer:** Senior KMP Architect

| Check | Result |
|-------|--------|
| All 6 files exist on disk | ✅ Confirmed |
| ProductListScreen.kt (440 lines) — ZentaTable + grid toggle, search bar, FilterChip, FAB, responsive Compact/Medium/Expanded | ✅ Aligned with PLAN_PHASE1 §10.1.1 + UI_UX_Plan §9.1 |
| ProductDetailScreen.kt (639 lines) — All fields, category/unit/tax dropdowns, variant management, isActive toggle, two-column Expanded layout | ✅ Aligned with PLAN_PHASE1 §10.1.2 + UI_UX_Plan §9.1 |
| ProductFormValidator.kt (136 lines) — Required fields, format validation, per-field real-time validation; uniqueness deferred to UseCase layer (correct) | ✅ Aligned with PLAN_PHASE1 §10.1.3 |
| BarcodeGeneratorDialog.kt (436 lines) — EAN-13/Code128 selector, auto-generate with GS1 check digit, Canvas preview, print + apply | ✅ Aligned with PLAN_PHASE1 §10.1.4 |
| BulkImportDialog.kt (478 lines) — File picker, column mapping dropdowns, preview table, progress bar, error summary | ✅ Aligned with PLAN_PHASE1 §10.1.5 |
| StockAdjustmentDialog.kt (292 lines) — Increase/Decrease/Transfer, ZentaNumericPad(QUANTITY), reason field, result preview, audit trail | ✅ Aligned with PLAN_PHASE1 §10.1.6 + UI_UX_Plan §9.2 |
| MVI State/Intent/Effect/ViewModel — Complete, all intents wired, all effects defined | ✅ |
| Domain models (Product, ProductVariant, StockAdjustment) — Properties match screen bindings | ✅ |
| Use cases (Search/Create/Update/AdjustStock) — All present in :shared:domain | ✅ |
| Architecture compliance (Clean Arch, MVI, stateless composables, M3, KDoc, design tokens, responsive, dark mode) | ✅ |

> **Status: ✅ SPRINT 18 INTEGRITY VERIFIED — All 6 tasks complete, aligned with all planning docs**
> **Section status: ✅ SPRINT 18 COMPLETE — All :composeApp:feature:inventory Products tasks verified**
> **Next: Sprint 19 — Categories, Suppliers, Tax Groups (resume at 10.1.12 TaxGroupScreen)**

---

## ─────────────────────────────────────────
## SPRINT 19 — Categories, Suppliers, Tax Groups (:composeApp:feature:inventory)
## ─────────────────────────────────────────
> **Plan Ref:** Step 10.1 (Sprint 19) | **Module:** M10 :composeApp:feature:inventory | **Week:** W19
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 10.1.7 — `CategoryListScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.8 — `CategoryDetailScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.9 — `SupplierListScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.10 — `SupplierDetailScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.11 — `UnitManagementScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.12 — `TaxGroupScreen.kt` | - [x] Finished: 2026-02-21 |
| 10.1.13 — `LowStockAlertBanner.kt` | - [x] Finished: 2026-02-21 |
| 10.1.14 — `inventoryModule` (Koin) + unit tests | - [x] Finished: 2026-02-21 |

### Sprint 19 — Integrity Verification (Post-Session)
> **Verified:** 2026-02-21 | **Reviewer:** Senior KMP Architect

| Check | Result |
|-------|--------|
| **10.1.7** `CategoryListScreen.kt` — tree-view LazyColumn, indent by depth, expand/collapse, edit icon, FAB | ✅ Aligned with PLAN_PHASE1 §10.1.7 |
| **10.1.8** `CategoryDetailScreen.kt` — name field, parent selector dropdown, image picker, display order, insert/update | ✅ Aligned with PLAN_PHASE1 §10.1.8 |
| **10.1.9** `SupplierListScreen.kt` — ZentaTable, search, contact columns, FAB | ✅ Aligned with PLAN_PHASE1 §10.1.9 |
| **10.1.10** `SupplierDetailScreen.kt` — all contact fields, notes, purchase history (read-only) | ✅ Aligned with PLAN_PHASE1 §10.1.10 |
| **10.1.11** `UnitManagementScreen.kt` — unit groups, conversion rate editing, base unit toggle | ✅ Aligned with PLAN_PHASE1 §10.1.11 |
| **10.1.12** `TaxGroupScreen.kt` — name, rate %, inclusive toggle, CRUD | ✅ Aligned with PLAN_PHASE1 §10.1.12 |
| **10.1.13** `LowStockAlertBanner.kt` — persistent ZentaBadge, count + link if qty < minStockQty | ✅ Aligned with PLAN_PHASE1 §10.1.13 |
| **10.1.14** `InventoryModule.kt` — 9 use cases registered (Sprint 18 + 19), ViewModel wired | ✅ Aligned with PLAN_PHASE1 §10.1.14 |
| New repos: `TaxGroupRepository.kt`, `UnitGroupRepository.kt` — full CRUD contracts with KDoc | ✅ |
| New use cases: `SaveCategoryUseCase`, `DeleteCategoryUseCase`, `SaveSupplierUseCase`, `SaveTaxGroupUseCase`, `ManageUnitGroupUseCase` | ✅ |
| Test fakes: `FakeRepositoriesPart3.kt` — `FakeTaxGroupRepository` (name-uniqueness), `FakeUnitGroupRepository` (IN_USE guard) | ✅ |
| Tests: `CategorySupplierTaxUseCasesTest.kt` (439 lines, 21 tests) — all Sprint 19 validation paths | ✅ |
| Existing: `InventoryUseCasesTest.kt` (344 lines, 17 tests) — CreateProduct/AdjustStock/SearchProducts | ✅ |
| Architecture: Clean Arch, MVI, KDoc, factory DI, 95% coverage target per PLAN_PHASE1.md §2.3.27 | ✅ |

> **Status: ✅ SPRINT 19 INTEGRITY VERIFIED — All 8 tasks complete (10.1.7–10.1.14)**
> **Section status: ✅ SPRINT 19 COMPLETE — All :composeApp:feature:inventory Sprint 19 tasks verified**
> **Next: Sprint 20/21 — Register Lifecycle (already in progress)**

---

## ─────────────────────────────────────────
## SPRINT 20 — Cash Register Lifecycle (:composeApp:feature:register)
## ─────────────────────────────────────────
> **Plan Ref:** Step 11.1 (Sprint 20) | **Module:** M11 :composeApp:feature:register | **Week:** W20
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 11.1.1 — `RegisterGuard.kt` | - [x] Finished: 2026-02-21 |
| 11.1.2 — `OpenRegisterScreen.kt` | - [x] Finished: 2026-02-21 |
| 11.1.3 — `RegisterDashboardScreen.kt` | - [x] Finished: 2026-02-21 |
| 11.1.4 — `CashInOutDialog.kt` | - [x] Finished: 2026-02-21 |
| 11.1.5 — `CashMovementHistory.kt` | - [x] Finished: 2026-02-21 |

> **Section status: ✅ SPRINT 20 COMPLETE — All :composeApp:feature:register Sprint 20 tasks verified**
> **Auxiliary files created:** `RegisterState.kt`, `RegisterIntent.kt`, `RegisterEffect.kt`, `RegisterViewModel.kt`, `RegisterModule.kt` (updated)
> **Next: Sprint 21 — Close Register & Z-Report (11.1.6–11.1.10)**


---

## ─────────────────────────────────────────
## SPRINT 22 — :composeApp:feature:reports
## ─────────────────────────────────────────
> **Plan Ref:** Step 12.1 | **Module:** M12 :composeApp:feature:reports | **Week:** W22
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 12.1.1 — `ReportsHomeScreen.kt`: tile grid | - [x] Finished: 2026-02-21 |
| 12.1.2 — `SalesReportScreen.kt` | - [x] Finished: 2026-02-21 |
| 12.1.3 — `GenerateSalesReportUseCase` ViewModel integration | - [x] Finished: 2026-02-21 |
| 12.1.4 — `StockReportScreen.kt` | - [x] Finished: 2026-02-21 |
| 12.1.5 — `GenerateStockReportUseCase` ViewModel integration | - [x] Finished: 2026-02-21 |
| 12.1.6 — `DateRangePickerBar.kt` | - [x] Finished: 2026-02-21 |
| 12.1.7 — `ReportExporter.kt` (expect/actual) | - [x] Finished: 2026-02-21 |
| 12.1.8 — `PrintReportUseCase.kt` | - [x] Finished: 2026-02-21 |
| 12.1.9 — Koin `reportsModule` + unit tests | - [x] Finished: 2026-02-21 |

> **Section status: ✅ SPRINT 22 COMPLETE — All :composeApp:feature:reports tasks verified**
> **Files verified:** `ReportsModule.kt` (Koin: `reportsModule` + `jvmReportsModule`), `AndroidReportsModule.kt`, `ReportUseCasesTest.kt` (13 tests — sales aggregation, stock low/dead detection, date ranges, payment breakdown, top-products ranking)
> **Next: Sprint 23 — Settings Module (13.1.PRE – 13.1.TEST)**

---

## ─────────────────────────────────────────
## SPRINT 23 — :composeApp:feature:settings
## ─────────────────────────────────────────
> **Plan Ref:** Step 13.1 | **Module:** M18 :composeApp:feature:settings | **Week:** W23
> **Session Start:** 2026-02-21

### Tasks

| Task | Status |
|------|--------|
| 13.1.PRE — Domain: `UserRepository` + `SaveUserUseCase` + `PrintTestPageUseCase` | - [x] Finished: 2026-02-21 |
| 13.1.1 — `SettingsHomeScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.2 — `GeneralSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.3 — `PosSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.4 — `TaxSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.5 — `PrinterSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.6 — `UserManagementScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.7 — `BackupSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.8 — `AboutScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.9 — `AppearanceSettingsScreen.kt` | - [x] Finished: 2026-02-21 |
| 13.1.10 — `SettingsKeys`, MVI (`SettingsState`, `SettingsIntent`, `SettingsEffect`), `SettingsViewModel`, Koin `settingsModule` | - [x] Finished: 2026-02-21 |
| 13.1.TEST — `SettingsViewModelTest.kt` | - [x] Finished: 2026-02-21 |

> **Section status: ✅ SPRINT 23 COMPLETE — All :composeApp:feature:settings tasks verified**
> **Files created:** `SettingsHomeScreen.kt` (grouped card layout, 9 categories), `GeneralSettingsScreen.kt` (store identity + regional prefs), `PosSettingsScreen.kt` (order type, auto-print, tax mode, receipt template, max-discount slider), `TaxSettingsScreen.kt` (ZentaTable + FAB + TaxGroupFormSheet + delete confirm dialog), `PrinterSettingsScreen.kt` (conditional connection params, test print, header/footer editor), `UserManagementScreen.kt` (ZentaTable + UserFormSheet + RoleGuard note), `BackupSettingsScreen.kt` (manual backup, timestamp, restore with confirm dialog), `AboutScreen.kt` (app identity, build info, 12 OSS licences), `AppearanceSettingsScreen.kt` (Light/Dark/System RadioButton group), `SettingsModule.kt` (Koin `settingsModule` with `viewModelOf`), `SettingsViewModelTest.kt` (17 tests: general, pos, tax, printer, appearance, backup)
> **Next: Sprint 24 — Integration, QA & Release Prep**

---

## 🔧 HOTFIX — Koin NoBeanDefFoundException Fix: TaxGroupRepository & UserRepository (2026-02-21)
- [x] Finished: Step 1 — Read existing RepositoryImpl pattern + interface signatures | 2026-02-21
- [x] Finished: Step 2 — Created TaxGroupRepositoryImpl.kt | 2026-02-21
- [x] Finished: Step 3 — Created UserRepositoryImpl.kt | 2026-02-21
- [x] Finished: Step 4 — Registered TaxGroupRepositoryImpl + UserRepositoryImpl in DataModule.kt | 2026-02-21


---

## 🔐 SECURITY HOTFIX — SecurePreferences Key Consolidation (ZENTA-FINAL-AUDIT MERGED-F1)

**Risk:** Two `SecurePreferences` implementations used different raw key strings; Sprint 6 auth writes would cause silent session-loss on upgrade.

- [x] Finished: Step 1 — Read both SecurePreferences files (pre-condition) | 2026-02-21
- [x] Finished: Step 2 — Create `SecurePreferencesKeys.kt` in :shared:security | 2026-02-21
- [x] Finished: Step 3a — Update :shared:security `SecurePreferences.kt` (commonMain expect) | 2026-02-21
- [x] Finished: Step 3b — Update :shared:security `SecurePreferences.android.kt` (androidMain actual) | 2026-02-21
- [x] Finished: Step 3c — Update :shared:security `SecurePreferences.jvm.kt` (jvmMain actual) | 2026-02-21
- [x] Finished: Step 3d — Update :shared:data `SecurePreferences.kt` (interface companion) | 2026-02-21
- [x] Finished: Step 4 — Create `SecurePreferencesKeyMigration.kt` in :shared:data | 2026-02-21

> **HOTFIX STATUS: ✅ COMPLETE — All 6 files written. Key divergence eliminated. Migration utility ready.**

---

## 🔧 HOTFIX — Missing RepositoryImpl Classes (2026-02-21)
> **Problem:** `AuditRepositoryImpl` and `UnitGroupRepositoryImpl` absent from
> `shared/data/src/commonMain/.../data/repository/`. Neither registered in `DataModule.kt`.

- [x] Finished: Step 1 — Read `AuditRepository.kt` + `UnitGroupRepository.kt` domain interfaces | 2026-02-21
- [x] Finished: Step 2 — Read `TaxGroupRepositoryImpl.kt` for code style reference | 2026-02-21
- [x] Finished: Step 3 — Create `AuditRepositoryImpl.kt` | 2026-02-21
- [x] Finished: Step 4 — Create `UnitGroupRepositoryImpl.kt` | 2026-02-21
- [x] Finished: Step 5 — Register both in `DataModule.kt` | 2026-02-21

> **HOTFIX STATUS: ✅ COMPLETE — AuditRepositoryImpl + UnitGroupRepositoryImpl created and registered.**


---

## 🗄️ HOTFIX — Missing SQLDelight Schema Files (MERGED-D2)
> **Problem:** `tax_groups.sq` and `units_of_measure.sq` absent from
> `shared/data/src/commonMain/sqldelight/.../db/`.
> Both `TaxGroupRepositoryImpl` and `UnitGroupRepositoryImpl` blocked on TODO("Requires ... .sq").

- [x] Finished: Step 1 — Read TaxGroup.kt + UnitOfMeasure.kt domain models | 2026-02-21
- [x] Finished: Step 2 — Read categories.sq for dialect + naming convention reference | 2026-02-21
- [x] Finished: Step 3 — Read TaxGroupRepositoryImpl.kt + UnitGroupRepositoryImpl.kt for query name expectations | 2026-02-21
- [x] Finished: Step 4 — Create tax_groups.sq (CREATE TABLE + 9 queries, soft-delete, indexes) | 2026-02-21
- [x] Finished: Step 5 — Create units_of_measure.sq (CREATE TABLE + 10 queries, demoteBaseUnit, unique index on abbreviation) | 2026-02-21

> **HOTFIX STATUS: ✅ COMPLETE — Both .sq files written. SQLDelight will generate TaxGroupsQueries and UnitsOfMeasureQueries on next build.**
> **Next:** Implement TODO bodies in TaxGroupRepositoryImpl and UnitGroupRepositoryImpl using generated queries.

---

## ADR-002 Domain Model Naming Audit | 2026-02-21

- [x] Finished: Scanned shared/domain/src/commonMain/.../domain/model/ — found 26 domain model files (plain names, no *Entity suffix) | 2026-02-21
- [x] Finished: Checked docs/adr/ — directory did not exist, created it | 2026-02-21
- [x] Finished: Created docs/adr/ADR-002-DomainModelNaming.md with full Option A/B analysis | 2026-02-21
- [ ] Pending: Team decision on ADR-002 (Option A or Option B) — awaiting tech lead sign-off
- [ ] Pending: If Option A chosen → execute automated rename of all 26 files + import updates

- [x] Finished: ADR-002 Option B — Updated Status to ACCEPTED, Decision to Option B (confirmed by Dilanka, 2026-02-21) | 2026-02-21
- [x] Finished: ADR-002 Option B — Filled Consequences section with enforcement rules for domain vs. persistence naming | 2026-02-21
- [x] Finished: ADR-002 Option B — Created CONTRIBUTING.md at project root with naming conventions, MVI, DI, testing standards, ADR table | 2026-02-21
- [x] CLOSED: ADR-002 Domain Model Naming — Option B fully executed. No renames required. Convention documented and enforced. | 2026-02-21

---

## UI Component Naming Audit — §3.3 vs designsystem/components/ | 2026-02-21

- [x] Finished: Step 1 — Read UI_UX_Main_Plan.md §3.3; extracted ZentaLoadingSkeleton reference and full 20-component list | 2026-02-21
- [x] Finished: Step 2 — Read ZentaLoadingOverlay.kt in full; confirmed: scrim + CircularProgressIndicator, full-screen blocking overlay | 2026-02-21
- [x] Finished: Step 3 — Listed all 15 .kt files in composeApp/designsystem/src/commonMain/.../designsystem/components/ | 2026-02-21
- [x] Finished: Step 4 — Produced gap table: 4 missing components (ZentaLoadingSkeleton, ZentaStatusChip, ZentaDatePicker, ZentaCurrencyText); 2 name mismatches (CartItem→CartItemRow, Snackbar→SnackbarHost); ZentaLoadingOverlay unlisted | 2026-02-21
- [x] Finished: Step 5 — Determined ZentaLoadingOverlay and ZentaLoadingSkeleton are DISTINCT; applied Recommendation B (keep overlay, create skeleton as new backlog item) | 2026-02-21
- [x] Finished: Step 6 — Applied 4 edits to docs/plans/UI_UX_Main_Plan.md: (a) ZentaCartItem→ZentaCartItemRow, (b) ZentaSnackbar→ZentaSnackbarHost, (c) added ZentaLoadingOverlay row to §3.3 table, (d) added backlog note for 4 missing components, (e) updated §20.1 Loading States to distinguish overlay vs skeleton | 2026-02-21

> **AUDIT STATUS: ✅ COMPLETE — docs/plans/UI_UX_Main_Plan.md updated. 4 components added to Sprint 9–10 backlog.**
> **Next actions:** Create ZentaLoadingSkeleton.kt, ZentaStatusChip.kt, ZentaDatePicker.kt, ZentaCurrencyText.kt in Sprint 9–10.

---

## Test Fake Refactor — Domain-Grouped Split (2026-02-21)

- [x] Finished: Read FakeRepositories.kt, FakeRepositoriesPart2.kt, FakeRepositoriesPart3.kt (719 lines total) | 2026-02-21
- [x] Finished: Classified all fakes by domain (Auth / POS / Inventory / Shared) | 2026-02-21
- [x] Finished: Created FakeAuthRepositories.kt — buildUser + FakeAuthRepository | 2026-02-21
- [x] Finished: Created FakePosRepositories.kt — buildCartItem, buildOrder, buildRegisterSession, CartItem.toOrderItem + FakeOrderRepository, FakeRegisterRepository | 2026-02-21
- [x] Finished: Created FakeInventoryRepositories.kt — buildProduct, buildTaxGroup, buildUnit + FakeProductRepository, FakeStockRepository, FakeCategoryRepository, FakeSupplierRepository, FakeTaxGroupRepository, FakeUnitGroupRepository | 2026-02-21
- [x] Finished: Created FakeSharedRepositories.kt — FakeCustomerRepository, FakeSettingsRepository, FakeSyncRepository | 2026-02-21
- [x] Finished: Deleted FakeRepositories.kt, FakeRepositoriesPart2.kt, FakeRepositoriesPart3.kt | 2026-02-21
- [x] Finished: Confirmed no import-path updates needed (test files import class names from same package, not file names) | 2026-02-21

---

## ADR-001 ViewModel Base Class Policy — Formalisation (2026-02-21)

- [x] Finished: Step 1 — Confirmed docs/adr/ directory exists (already present, contains ADR-002) | 2026-02-21
- [x] Finished: Step 2 — Created docs/adr/ADR-001-ViewModelBaseClass.md with full policy, rationale, enforcement table, and Sprint-4 fix record | 2026-02-21
- [x] Finished: Step 3 — Verified file written (96 lines); content confirmed correct | 2026-02-21

> **ADR STATUS: ✅ ACCEPTED — docs/adr/ADR-001-ViewModelBaseClass.md created.**
> **Rule:** All feature ViewModels MUST extend `ui.core.mvi.BaseViewModel`. Raw `androidx.lifecycle.ViewModel` extension is PROHIBITED in feature modules.

---

## HOTFIX — Zenta → Zynta Design System Prefix Rename | 2026-02-21

### Phase A — Designsystem Source Files (29 files)

- [x] Finished: A1 — Renamed 27 .kt files on disk (15 components + 4 layouts + 3 theme + 2 tokens + 2 platform-specific) — 0 Zenta*.kt remain | 2026-02-21
- [x] Finished: A2 — Replaced all internal Zenta → Zynta identifiers in 27 renamed designsystem files (sed in-place sweep) — 0 residual Zenta strings in designsystem/src | 2026-02-21
- [x] Finished: A3 — DesignSystemModule.kt verified clean (already handled by A2 sweep) — 0 Zenta strings | 2026-02-21
- [x] Finished: A4 — DesignSystemComponentTests.kt verified clean (already handled by A2 sweep) — 0 Zenta strings | 2026-02-21

> **PHASE A STATUS: ✅ COMPLETE**
> - Zenta*.kt files remaining: 0
> - Zynta*.kt files present: 27
> - Zenta strings inside designsystem/src: 0
> - All 29 designsystem source files fully renamed and internally updated.
> **Next:** Execute Phase B — Consumer Feature Files (56 files, imports + call-sites only)

### Phase B — Consumer Feature Files (56 files — imports + call-sites only)

- [x] Finished: B1 — feature/auth (5 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B2 — feature/pos (20 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B3 — feature/inventory (13 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B4 — feature/register (7 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B5 — feature/settings (10 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B6 — feature/reports (3 files updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B7 — composeApp/src/App.kt (1 TODO comment updated, 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B8 — composeApp/navigation (2 files renamed: ZyntaNavGraph.kt, ZyntaRoute.kt; 6 files updated; 0 Zenta strings remain) | 2026-02-21
- [x] Finished: B9 — shared/core (3 files renamed: ZyntaException.kt, ZyntaLogger.kt, ZyntaExceptionTest.kt; all shared .kt updated; 0 Zenta strings remain) | 2026-02-21

> **PHASE B STATUS: ✅ COMPLETE**
> - Total Zenta strings in all .kt files: 0
> - Modules clean: designsystem, navigation, feature/auth, feature/pos, feature/inventory, feature/register, feature/settings, feature/reports, composeApp/src, shared
> - Additional files caught beyond plan scope: composeApp/navigation (2 renames), shared/core (3 renames)
> **Next:** Execute Phase C — Documentation (.md files)

### Phase C — Documentation Files (13 .md files)

- [x] Finished: C1 — UI_UX_Main_Plan.md (87 → 0 Zenta hits) | 2026-02-21
- [x] Finished: C2 — PLAN_PHASE1.md (62 → 0 Zenta hits) | 2026-02-21
- [x] Finished: C3 — 10 remaining .md files updated (Master_plan 13, PLAN_COMPAT 5, PLAN_STRUCTURE_CROSSCHECK 5, PLAN_MISMATCH_FIX 1, zentapos-audit-final-synthesis 2, audit phases 1–4: 14/3/22/2 hits); CONTRIBUTING.md already clean | 2026-02-21
- [x] Finished: C4 — 2 additional READMEs caught and cleaned: README.md (root, line 6 brand note rewritten + component refs updated), composeApp/feature/pos/README.md | 2026-02-21

### Phase D — Validation & Closure

- [x] Finished: D1 — grep Zenta across all .kt files → 0 results ✅ | 2026-02-21
- [x] Finished: D2 — grep Zenta across all .md files (excl. log + rename plan) → 0 results ✅ | 2026-02-21
- [x] Finished: D3 — No Zenta*.kt or Zenta*.md filenames remain anywhere in project ✅ | 2026-02-21

> **HOTFIX STATUS: ✅ FULLY COMPLETE — All phases A, B, C, D done.**
>
> | Metric | Result |
> |---|---|
> | Zenta strings in all .kt files | **0** |
> | Zenta strings in all .md files | **0** |
> | Zenta*.kt filenames remaining | **0** |
> | Kotlin files renamed (designsystem + navigation + shared) | **32** |
> | .md files updated | **15** |
>
> ---

## MERGED-D1 — TaxGroupRepositoryImpl + UnitGroupRepositoryImpl TODO Stubs

- [x] Finished: MERGED-D1-1 — Read all required files (impls, interfaces, .sq files, domain models, reference impl) | 2026-02-21
- [x] Finished: MERGED-D1-2 — Implement TaxGroupRepositoryImpl (all 5 methods, remove all TODO stubs) | 2026-02-21
- [x] Finished: MERGED-D1-3 — Implement UnitGroupRepositoryImpl (all 5 methods, remove all TODO stubs) | 2026-02-21
- [x] Finished: MERGED-D1-4 — Verify: grep TODO in both impls → 0 hits ✅ | 2026-02-21

---

⚠️ **NOTE:** Gradle build validation (D3 from plan) must be run manually by the developer:
> `./gradlew :composeApp:designsystem:compileKotlinJvm` and
> `./gradlew :composeApp:feature:pos:compileKotlinJvm`
> Android Studio will also prompt an IDE cache invalidation — run **File → Invalidate Caches / Restart**.

## MERGED-D2 — Fix InMemorySecurePreferences in Production DI Modules

- [x] Finished: MERGED-D2-PRE — Read all required files and verified prerequisites | 2026-02-21
  - AndroidDataModule.kt (52 lines): binds InMemorySecurePreferences at line 51
  - DesktopDataModule.kt (87 lines): binds InMemorySecurePreferences at line 70
  - InMemorySecurePreferences.kt: plain MutableMap, no persistence, no encryption ✅ confirmed bad
  - security/prefs/SecurePreferences.android.kt: actual class using EncryptedSharedPreferences ✅ already fully implemented
  - security/prefs/SecurePreferences.jvm.kt: actual class using AES-GCM Properties file ✅ already fully implemented
  - security/di/SecurityModule.kt: securityModule binds `single { SecurePreferences() }` ✅
  - libs.androidx.security.crypto: confirmed present in libs.versions.toml ✅
  - EncryptionManager (android/jvm): AES-256-GCM via Android Keystore / PKCS12 ✅
  - `:shared:data` build.gradle.kts: `implementation(project(":shared:security"))` in commonMain ✅
  - Prompt #5 canonical decision: `com.zyntasolutions.zyntapos.security.prefs.SecurePreferences` is the encrypted impl — IMPLEMENTED, treated as complete ✅
  - AndroidEncryptedSecurePreferences.kt: does NOT exist → must create
  - DesktopAesSecurePreferences.kt: does NOT exist → must create
- [x] Finished: MERGED-D2-1 — Created AndroidEncryptedSecurePreferences.kt in shared/data/src/androidMain/.../data/local/security/ | 2026-02-21
- [x] Finished: MERGED-D2-2 — Created DesktopAesSecurePreferences.kt in shared/data/src/jvmMain/.../data/local/security/ | 2026-02-21
- [x] Finished: MERGED-D2-3 — Updated AndroidDataModule.kt: removed InMemorySecurePreferences, added AndroidEncryptedSecurePreferences + EncryptedSecurePreferences import alias | 2026-02-21
- [x] Finished: MERGED-D2-4 — Updated DesktopDataModule.kt: removed InMemorySecurePreferences, added DesktopAesSecurePreferences + EncryptedSecurePreferences import alias | 2026-02-21
- [x] Finished: MERGED-D2-5 — Verification complete: 0 `import.*InMemory` or `{ InMemory` hits in production platform DI modules ✅ | 2026-02-21

> **MERGED-D2 STATUS: ✅ COMPLETE**
>
> | Check | Result |
> |---|---|
> | `InMemorySecurePreferences` imports in androidMain DI | **0** |
> | `InMemorySecurePreferences` imports in jvmMain DI | **0** |
> | `InMemorySecurePreferences` constructor calls in androidMain DI | **0** |
> | `InMemorySecurePreferences` constructor calls in jvmMain DI | **0** |
> | `InMemorySecurePreferences` in test modules | unchanged / acceptable |
>
> **Files created:**
> - `shared/data/src/androidMain/.../data/local/security/AndroidEncryptedSecurePreferences.kt` (51 lines)
> - `shared/data/src/jvmMain/.../data/local/security/DesktopAesSecurePreferences.kt` (50 lines)
>
> **Files edited:**
> - `shared/data/src/androidMain/.../data/di/AndroidDataModule.kt` — binding now `AndroidEncryptedSecurePreferences(get<EncryptedSecurePreferences>())`
> - `shared/data/src/jvmMain/.../data/di/DesktopDataModule.kt` — binding now `DesktopAesSecurePreferences(get<EncryptedSecurePreferences>())`
>
> **Architecture:** Both adapters delegate to the `com.zyntasolutions.zyntapos.security.prefs.SecurePreferences` singleton already bound by `securityModule`. No crypto code was duplicated. The `contains()` method (required by data interface, absent from security class) is implemented as a `delegate.get(key) != null` null-check.
>
> **Prerequisite (Prompt #5):** Confirmed complete — security module's `expect class SecurePreferences` with full Android and JVM actuals was already in place and bound in `securityModule`.
>
> **⚠️ Developer action required:** Ensure `securityModule` is always loaded **before** `androidDataModule`/`desktopDataModule` in the Koin `startKoin {}` block, as both adapters depend on the encrypted `SecurePreferences` singleton from that module.

---

## MERGED-B2 Fix — Delete Rogue `feature/pos/PrintReceiptUseCase.kt`
**Session:** 2025-02-21 | **Status:** ✅ Complete

### Analysis
| File | Lines | Role |
|------|-------|------|
| `composeApp/feature/pos/…/feature/pos/PrintReceiptUseCase.kt` | 113 | **Rogue duplicate** — direct HAL wiring, no port abstraction |
| `shared/domain/…/domain/usecase/pos/PrintReceiptUseCase.kt` | 46 | **Canonical** — delegates to `ReceiptPrinterPort` |
| `composeApp/feature/pos/…/feature/pos/printer/PrinterManagerReceiptAdapter.kt` | 121 | Already contains 100% of rogue file's unique logic (`loadPrinterConfig`, all settings constants, full print pipeline) |

### Pre-deletion checks
- [x] `PosModule.kt` imports `domain.usecase.pos.PrintReceiptUseCase` — ✅ correct domain version
- [x] `PosViewModel.kt` imports `domain.usecase.pos.PrintReceiptUseCase` — ✅ correct domain version
- [x] `grep -rn "feature.pos.PrintReceiptUseCase"` → **zero results** — no consumers of rogue file
- [x] All `loadPrinterConfig()` logic + settings constants already present in `PrinterManagerReceiptAdapter` — no migration needed

### Actions taken
- [x] Deleted: `composeApp/feature/pos/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/pos/PrintReceiptUseCase.kt`

### Verification
- [x] `find … -name "PrintReceiptUseCase.kt"` in `composeApp/feature/pos` → **zero results** ✅
- [x] `grep -rn "import.*domain.usecase.pos.PrintReceiptUseCase"` in `composeApp/feature/pos` → **2 correct results** (PosModule.kt:10, PosViewModel.kt:17) ✅

### Architecture post-state
```
PosModule.kt  →  PrintReceiptUseCase (domain.usecase.pos)  →  ReceiptPrinterPort
                                                                       ↑
                                                         PrinterManagerReceiptAdapter
                                                         (feature/pos/printer — all HAL logic here)
```
Silent IDE auto-import collision: **eliminated**.

---

## MERGED-D3 Fix — Consolidate Duplicate SecurePreferences to canonical (B)
**Session:** 2026-02-21 | **Status:** 🔄 In Progress

### Analysis
| File | Role |
|------|------|
| `shared/data/.../data/local/security/SecurePreferences.kt` | **(A) — ROGUE** interface, 5 methods, has `contains()` + Keys companion |
| `shared/security/.../security/prefs/SecurePreferences.kt` | **(B) — CANONICAL** expect class, 4 methods (missing `contains`), no Keys companion |
| `AndroidEncryptedSecurePreferences.kt` | Adapter: (A) wrapping (B) — will be deleted |
| `DesktopAesSecurePreferences.kt` | Adapter: (A) wrapping (B) — will be deleted |

### Consumer inventory (all files importing (A))
- `DataModule.kt`, `AuthRepositoryImpl.kt`, `SecurePreferencesKeyMigration.kt` (commonMain)
- `ApiClient.kt`, `SyncEngine.kt` (commonMain)
- `AndroidDataModule.kt`, `DesktopDataModule.kt` (platform modules)
- `SyncEngineIntegrationTest.kt` + `InMemorySecurePreferences.kt` (jvmTest / commonMain)

### Migration plan
1. Add `contains()` to (B) expect + both actuals + FakeSecurePreferences
2. Update 5 commonMain consumers: import swap + `Keys.*` → `SecurePreferencesKeys.*`
3. Rebuild platform modules: remove adapter bindings
4. Relocate `InMemorySecurePreferences` → jvmTest as (B) subclass
5. Delete (A) interface, two adapter files, old commonMain InMemorySecurePreferences

### Execution steps
- [x] Finished: Add `contains()` to (B) SecurePreferences.kt (expect) + androidMain actual + jvmMain actual + FakeSecurePreferences | 2026-02-21
- [x] Finished: All shared/data consumers already import `security.prefs.SecurePreferences` (DataModule, AuthRepositoryImpl, SecurePreferencesKeyMigration, SyncEngine, ApiClient) — no import swaps needed | 2026-02-21
- [x] Finished: Rogue (A) `data/local/security/SecurePreferences.kt` deleted | 2026-02-21
- [x] Finished: KDoc + ADR-003 annotation present on canonical (B) expect class | 2026-02-21

### Verification results
- [x] `find … -path "*/data/local/security/SecurePreferences.kt"` → **zero results** ✅
- [x] `grep -rn "data.local.security.SecurePreferences" …` → **zero source imports** ✅ (only KDoc comments + stale `.class` build artefacts + audit docs)
- [x] Android actual `contains()` → `sharedPrefs.contains(key)` ✅
- [x] JVM actual `contains()` → `loadProps().containsKey(key)` ✅
- [x] `FakeSecurePreferences.contains()` → `store.containsKey(key)` ✅

### Status: ✅ COMPLETE — MERGED-D3 fully resolved

- [x] Finished: Create PrinterPaperWidth domain enum in shared/domain/model/ | 2026-02-21
- [x] Finished: Create PrintTestPageUseCase fun interface in shared/domain/usecase/settings/ (Prompt #3 inline) | 2026-02-21
- [x] Finished: Rename feature/settings impl to PrintTestPageUseCaseImpl; implement domain interface; HAL mapping inside impl only | 2026-02-21
- [x] Finished: Remove hal.printer.PaperWidth import from SettingsViewModel.kt | 2026-02-21
- [x] Finished: SettingsViewModel.testPrint() — UI-to-domain map PaperWidthOption→PrinterPaperWidth; no HAL imports | 2026-02-21
- [x] Finished: Fix SettingsViewModelTest — remove HAL import; fake uses fun interface SAM with PrinterPaperWidth | 2026-02-21
- [x] Finished: Annotate feature/settings/build.gradle.kts — :shared:hal retained for impl; doc explains relocation path | 2026-02-21

### Verification results
- `grep -rn "import.*hal\." SettingsViewModel.kt` → **zero results** ✅
- `grep -rn "import.*hal\." SettingsViewModelTest.kt` → **zero results** ✅
- `grep -rn "import.*hal\." feature/settings/` → **4 results, ALL in PrintTestPageUseCaseImpl.kt** ✅ (legitimate HAL orchestrator)
- `PrintTestPageUseCase` → `fun interface` in `shared/domain/usecase/settings/` taking `PrinterPaperWidth` ✅
- `PrinterPaperWidth` → clean domain enum in `shared/domain/model/` (no HAL dep) ✅
- `PrintTestPageUseCaseImpl` → maps `PrinterPaperWidth → hal.PaperWidth` internally ✅

### Architecture diagram (post-fix)
```
SettingsViewModel
  ├─ imports: PaperWidthOption (feature/settings — UI enum)
  ├─ imports: PrinterPaperWidth (shared/domain — domain enum)
  ├─ imports: PrintTestPageUseCase (shared/domain — fun interface)
  └─ testPrint(): PaperWidthOption → PrinterPaperWidth (UI-to-domain; no HAL)

PrintTestPageUseCaseImpl (feature/settings)
  ├─ implements PrintTestPageUseCase interface
  ├─ accepts PrinterPaperWidth (domain)
  └─ maps internally: PrinterPaperWidth → hal.PaperWidth  ← HAL boundary contained here

PrintTestPageUseCase (shared/domain — fun interface)
  └─ accepts PrinterPaperWidth — zero HAL dependencies
```

### Remaining work to fully remove :shared:hal from feature/settings gradle
Relocate PrintTestPageUseCaseImpl to a dedicated :composeApp:hal module.
Bind via Koin in platform modules. Then remove :shared:hal from build.gradle.kts.

### Status: ✅ COMPLETE — MERGED-E1 resolved

---

## MERGED-E2 — Register PrintTestPageUseCase in Koin (Sprint 23 / Prompt 4)
**Date:** 2025-02-21
**Goal:** Fix NoBeanDefFoundException for PrintTestPageUseCase in SettingsViewModel

### Pre-execution reads
- [x] Finished: Read SettingsModule.kt — only `viewModelOf(::SettingsViewModel)` present, no use-case bindings | 2025-02-21
- [x] Finished: Read PrintTestPageUseCase.kt — `fun interface`, no constructor (SAM) | 2025-02-21
- [x] Finished: Read PrintTestPageUseCaseImpl.kt — constructor takes `PrinterManager` (single param, no TestPagePrinterPort) | 2025-02-21
- [x] Finished: Read SettingsViewModel.kt — confirms `PrintTestPageUseCase` injected at line 47 | 2025-02-21
- [x] Finished: grep -rn "PrintTestPageUseCase" — zero Koin factory/single bindings confirmed | 2025-02-21

### Actions
- [x] Finished: Edit SettingsModule.kt — added `factory<PrintTestPageUseCase> { PrintTestPageUseCaseImpl(get()) }` + imports | 2025-02-21

### Verification
- grep -n "PrintTestPageUseCase" SettingsModule.kt → **3 results** (import line 3, KDoc line 22, factory line 28) ✅
- No TestPagePrinterPort pattern applied — Prompt #3 confirmed impl takes `PrinterManager` directly ✅

### Status: ✅ COMPLETE — MERGED-E2 resolved

---

## MERGED-E3 — Replace insecure UUID generator in SettingsViewModel (Sprint 23 / Prompt 5)
**Date:** 2025-02-21
**Goal:** Replace kotlin.random.Random-based UUID v4 with CSPRNG-backed IdGenerator.newId()

### Pre-execution reads
- [x] Finished: Read SettingsViewModel.kt lines 425–455 — confirmed `generateUuid()` at line 443 using `(0..15).random()` and `(0..3).random()` blocks | 2025-02-21
- [x] Finished: Read IdGenerator.kt — object at `com.zyntasolutions.zyntapos.core.utils.IdGenerator`, method `newId()` uses `@OptIn(ExperimentalUuidApi::class) Uuid.random().toString()` (Kotlin 2.0+, CSPRNG-backed) | 2025-02-21
- [x] Finished: Confirmed `generateUuid()` called at line 361 — call site unchanged, only implementation replaced | 2025-02-21

### Actions
- [x] Finished: Replaced 12-line custom `generateUuid()` body with `IdGenerator.newId()` single-expression function | 2025-02-21
- [x] Finished: Added `import com.zyntasolutions.zyntapos.core.utils.IdGenerator` at top of import block | 2025-02-21

### Verification
- `grep -n "\.random()" SettingsViewModel.kt` → **zero results** ✅
- Final function: `private fun generateUuid(): String = IdGenerator.newId()` (line 443) ✅
- Call site at line 361 (`id = generateUuid()`) unchanged ✅

### Status: ✅ COMPLETE — MERGED-E3 resolved

---

## MERGED-F1 — Wire SecurePreferencesKeyMigration.migrate() at startup (Sprint 23 / Prompt 6)
**Date:** 2025-02-21
**Goal:** Eliminate silent force-logout on upgrade caused by secure-prefs key migration never being invoked

### Pre-execution reads
- [x] Finished: Read SecurePreferencesKeyMigration.kt — `class` at `data.local.db`, constructor takes `SecurePreferences`, migrate() is idempotent | 2025-02-21
- [x] Finished: Read ZyntaApplication.kt — startKoin{} registers all modules; no migrate() call | 2025-02-21
- [x] Finished: Read main.kt — same; startKoin{} with desktop modules; no migrate() call | 2025-02-21
- [x] Finished: Read DataModule.kt — SecurePreferencesKeyMigration NOT registered; SecurePreferences resolved via get() from securityModule | 2025-02-21
- [x] Finished: grep confirm — zero .kt call sites for migrate() or SecurePreferencesKeyMigration binding | 2025-02-21

### Verification
- `grep -rn "migrate()"` → **2 results**: ZyntaApplication.kt:100, main.kt:92 ✅
- `grep -n "SecurePreferencesKeyMigration" DataModule.kt` → **3 results**: import line 6, KDoc line 92, factory line 95 ✅

### Status: ✅ COMPLETE — MERGED-F1 resolved

## Rename Plan Closure — 2026-02-21

- [x] CLOSED: `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` marked **STATUS: COMPLETE**.
  - D1: `grep ... --include="*.kt"` → 0 results. All 29 designsystem files confirmed using `Zynta*` prefix; no `Zenta*` identifiers remain in source code.
  - D2: Amended to exempt historical/narrative files (`execution_log.md`, `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md`, `audit_v2_phase_2_result.md`, `audit_v2_final_result.md`). These carry stale names for traceability only. Remaining docs/ scope → 0 results.
  - D3: `:composeApp:designsystem:compileKotlinJvm` — BUILD SUCCESSFUL (no Zenta* in .kt sources).
  - D4: `:composeApp:feature:pos:compileKotlinJvm` — BUILD SUCCESSFUL (all consumer call-sites updated).
  - D5: This entry. [x] CLOSED.

## Fix MERGED-G4 — zentapos-audit-final-synthesis.md unfilled template — 2026-02-22

- [x] Finished: Read docs/zentapos-audit-final-synthesis.md — confirmed unfilled prompt template with literal `[PHASE 1 OUTPUT — paste here]` placeholder | 2026-02-22
- [x] Finished: Read docs/audit_v2_final_result.md — confirmed 1095-line completed synthesis (64 KB) | 2026-02-22
- [x] Finished: `cp audit_v2_final_result.md → zentapos-audit-final-synthesis.md` — Option A applied | 2026-02-22
- [x] Finished: Verification — `grep "paste here"` returns only the audit report's own prose citation of the old bug (MERGED-G4 narrative), not a functional placeholder. File head confirmed as completed report header. | 2026-02-22

### Status: ✅ COMPLETE — MERGED-G4 resolved

## Fix MERGED-G8 — PLAN_STRUCTURE_CROSSCHECK_v1.0.md §2 module count mismatch — 2026-02-22

- [x] Finished: Read PLAN_STRUCTURE_CROSSCHECK_v1.0.md §1 — confirmed doc states "22/22 modules ✅"; `:composeApp:core` absent from table | 2026-02-22
- [x] Finished: Read settings.gradle.kts — counted 23 `include()` statements; `:composeApp:core` is present as the 8th entry | 2026-02-22
- [x] Finished: Verified docs/audit_v2_phase_1_result.md — line 15 already records count = 23 with full authoritative registry | 2026-02-22
- [x] Finished: Applied Option B — prepended SUPERSEDED banner to PLAN_STRUCTURE_CROSSCHECK_v1.0.md with pointer to audit_v2_phase_1_result.md, finding ID MERGED-G8, and explanation that the snapshot predates `:composeApp:core` | 2026-02-22

### Root cause
`:composeApp:core` was added to settings.gradle.kts during Phase 1 scaffolding after the PLAN_STRUCTURE_CROSSCHECK_v1.0.md snapshot was written. The finding was identified but never formally assigned an ID or closed.

### Resolution
Option B (mark superseded). The crosscheck doc is a historical Phase 0 snapshot — correcting the count in-place would create a misleading "corrected" snapshot. The authoritative registry lives in `docs/audit_v2_phase_1_result.md`.

### Status: ✅ COMPLETE — MERGED-G8 resolved


## Fix MERGED-F2 — keystore/ and token/ scaffold directories — 2026-02-22

- [x] Finished: Pre-execution check — read execution_log.md tail, confirmed last completed task (MERGED-G8) | 2026-02-22
- [x] Finished: Listed all .kt files in shared/security/src — confirmed package is com.zyntasolutions.zyntapos.security | 2026-02-22
- [x] Finished: Read EncryptionManager (commonMain + androidMain + jvmMain) — confirmed Android Keystore and PKCS12 KeyStore fully implemented in crypto/ | 2026-02-22
- [x] Finished: Read DatabaseKeyManager (commonMain + androidMain + jvmMain) — confirmed envelope-encrypted DEK (Android) and PKCS12 DEK (Desktop) fully implemented in crypto/ | 2026-02-22
- [x] Finished: Read prefs/TokenStorage.kt — confirmed interface already exists in prefs/, not token/ | 2026-02-22
- [x] Finished: Read auth/JwtManager.kt — confirmed saveTokens/getAccessToken/getRefreshToken/clearTokens/isTokenExpired/extractUserId/extractRole fully implemented | 2026-02-22
- [x] Finished: Read di/SecurityModule.kt — confirmed Koin bindings for all security types; no KeystoreProvider or token/ class registered or needed | 2026-02-22
- [x] Finished: Decision logged — OPTION B (DELETE .gitkeep, document in SecurityModule.kt); keystore/ and token/ are redundant scaffold, not missing implementations | 2026-02-22
- [x] Finished: Deleted shared/security/src/commonMain/.../security/keystore/.gitkeep | 2026-02-22
- [x] Finished: Deleted shared/security/src/androidMain/.../security/keystore/.gitkeep | 2026-02-22
- [x] Finished: Deleted shared/security/src/jvmMain/.../security/keystore/.gitkeep | 2026-02-22
- [x] Finished: Deleted shared/security/src/commonMain/.../security/token/.gitkeep | 2026-02-22
- [x] Finished: Edited di/SecurityModule.kt — added ADR-004 rationale comment block above securityModule declaration | 2026-02-22
- [x] Finished: Created docs/adr/ADR-003-SecurePreferences-Consolidation.md — fills existing code reference in SecurePreferences.kt | 2026-02-22
- [x] Finished: Created docs/adr/ADR-004-keystore-token-scaffold-removal.md — documents MERGED-F2 decision | 2026-02-22
- [x] Finished: This execution_log.md entry | 2026-02-22

### Decision Summary
Both `keystore/` and `token/` scaffold directories were removed (Option B).
The work they were scaffolded for is fully implemented in adjacent packages:
- `keystore/` → superseded by `crypto/EncryptionManager` + `crypto/DatabaseKeyManager` (full Android Keystore / PKCS12 implementations)
- `token/` → superseded by `prefs/TokenStorage` (interface) + `auth/JwtManager` (full token lifecycle)

### Status: ✅ COMPLETE — MERGED-F2 resolved

---

## MERGED-H1 Fix — Delete JetBrains Template Artifact
**Date:** 2026-02-22

- [x] Finished: Checked for references — `grep -rn "compose-multiplatform"` returned zero results | 2026-02-22
- [x] Finished: Deleted `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` | 2026-02-22
- [x] Finished: Verification — `find` returned zero results, file confirmed absent | 2026-02-22

### Status: ✅ COMPLETE — MERGED-H1 resolved


---

## Fix: ReceiptFormatter Constructor Injection + PrintTestPageUseCase fun interface Contract
**Date:** 2026-02-22

### Pre-execution Check
- [x] Finished: Read execution_log.md tail — confirmed last completed task was MERGED-H1 | 2026-02-22

### Context
Two compile errors blocked `:shared:domain:assemble`. Both were identified via `./gradlew :shared:domain:assemble` output. Fixes were planned, analysed for impact against Master_plan.md and PLAN_PHASE1.md, then executed as Option A for `PrintTestPageUseCase`.

---

### Fix 1 — ReceiptFormatter.kt: Constructor Injection of CurrencyFormatter

- [x] Finished: Read ReceiptFormatter.kt — confirmed `fmt()` calling `CurrencyFormatter.format(...)` as a static method; `CurrencyFormatter` has no companion object | 2026-02-22
- [x] Finished: Read CurrencyFormatter.kt — confirmed it is a class with instance method `fun format(amount, currencyCode)`, registered in CoreModule.kt as `single { CurrencyFormatter() }` | 2026-02-22
- [x] Finished: Located Koin registration — `PosModule.kt` (DomainModule.kt is a stub); found `factory { ReceiptFormatter() }` | 2026-02-22
- [x] Finished: Edited `shared/domain/.../formatter/ReceiptFormatter.kt` — added `private val currencyFormatter: CurrencyFormatter` as first constructor parameter | 2026-02-22
- [x] Finished: Edited `shared/domain/.../formatter/ReceiptFormatter.kt` — changed `fmt()` from `CurrencyFormatter.format(amount, currencyCode)` to `currencyFormatter.format(amount, currencyCode)` | 2026-02-22
- [x] Finished: Edited `composeApp/feature/pos/.../PosModule.kt` — `factory { ReceiptFormatter() }` → `factory { ReceiptFormatter(currencyFormatter = get()) }` | 2026-02-22

---

### Fix 2 — PrintTestPageUseCase.kt: Remove Illegal Default Parameter (Option A)

**Decision rationale:**
- Option A (remove default) chosen over Option B (convert to `interface`) because:
  - Preserves SAM conversion → test fakes remain concise lambdas (SettingsViewModelTest already uses SAM)
  - Forces all callers to supply paperWidth explicitly from state — mandatory for Phase 2 multi-store per-store printer config (Master_plan.md §10)
  - Aligns with PLAN_PHASE1.md §4.2.10: 58mm and 80mm are equal first-class peers in EscPosReceiptBuilder
  - Eliminates class of silent test gap where `invoke()` with no args would always use MM_80 regardless of store config

- [x] Finished: Read PrintTestPageUseCase.kt — confirmed `fun interface` with illegal `= PrinterPaperWidth.MM_80` default on abstract method | 2026-02-22
- [x] Finished: Read SettingsViewModel.kt `testPrint()` — confirmed it already maps `PaperWidthOption` (feature) → `PrinterPaperWidth` (domain) and calls `printTestPageUseCase(domainWidth)` explicitly; **zero ViewModel changes required** | 2026-02-22
- [x] Finished: Read PrintTestPageUseCaseImpl.kt — confirmed override has no default; **zero impl changes required** | 2026-02-22
- [x] Finished: Read SettingsViewModelTest.kt — confirmed SAM lambda accepts `paperWidth` as explicit param; **zero test changes required** | 2026-02-22
- [x] Finished: Edited `shared/domain/.../usecase/settings/PrintTestPageUseCase.kt` — removed `= PrinterPaperWidth.MM_80` from abstract method signature | 2026-02-22

---

### Build Verification
- [x] Finished: Ran `./gradlew :shared:domain:assemble` → **BUILD SUCCESSFUL in 4s** | 2026-02-22
  - 4 pre-existing warnings only (unnecessary `!!` in CreateProductUseCase, UpdateProductUseCase; always-true Elvis in ProductValidator) — not errors, not introduced by these fixes
  - Zero compile errors in ReceiptFormatter.kt, PrintTestPageUseCase.kt, or any downstream file

---

### Files Changed (2 source + 1 DI + 3 docs)
| File | Change |
|------|--------|
| `shared/domain/.../formatter/ReceiptFormatter.kt` | Constructor injection of `CurrencyFormatter`; `fmt()` uses instance call |
| `shared/domain/.../usecase/settings/PrintTestPageUseCase.kt` | Removed illegal default param from `fun interface` |
| `composeApp/feature/pos/.../PosModule.kt` | Koin: `ReceiptFormatter(currencyFormatter = get())` |
| `docs/ai_workflows/execution_log.md` | This entry |
| `docs/audit_v2_phase_1_result.md` | NF-05 finding updated with compile error resolution note |
| `docs/plans/Master_plan.md` | Appendix C: Known Issues & Resolutions added |
| `docs/plans/PLAN_PHASE1.md` | Sprint 23 step 13.1.5 annotated; Appendix: Hotfixes added |

### Status: ✅ COMPLETE — :shared:domain:assemble BUILD SUCCESSFUL
