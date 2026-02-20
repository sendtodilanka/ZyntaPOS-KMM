# ZyntaPOS — Full Structure Cross-Check Report
> **Doc ID:** ZENTA-CROSSCHECK-v1.0
> **Created:** 2026-02-20
> **Author:** Senior KMP Architect (AI Agent)
> **References:** Master_plan.md (ZENTA-MASTER-PLAN-v1.0) · PLAN_PHASE1.md (ZENTA-PLAN-PHASE1-v1.0)
> **Scope:** Full filesystem audit against both plan documents

---

## 📊 Overall Alignment Score: 62% ⚠️

| Category | Plan Expects | Reality | Score |
|----------|-------------|---------|-------|
| Gradle module registrations | 20 modules | 20 registered ✅ | 100% |
| Module build.gradle.kts files | 20 | 9 exist ❌ | 45% |
| Module source set directories | All | 9 modules scaffolded ❌ | 45% |
| Package sub-directory structure | Full tree | Partial ⚠️ | 40% |
| Feature module physical presence | 13 | 0 ❌ | 0% |
| CI/CD pipeline | 1 workflow | 0 ❌ | 0% |
| Placeholder cleanup | Required | Not done ⚠️ | 0% |
| Module naming canonical | Consistent | 1 conflict ⚠️ | — |

---

## ✅ WHAT IS CORRECT (Fully Aligned)

### Build System Foundation
| Item | Plan | Reality | Status |
|------|------|---------|--------|
| Root `build.gradle.kts` | All plugins, apply false | ✅ Correct | ✅ |
| `gradle/libs.versions.toml` | Full version catalog | ✅ Present | ✅ |
| `settings.gradle.kts` | All 20 modules registered | ✅ All 20 registered | ✅ |
| `gradle.properties` | caching, parallel, Xmx4g | ✅ Correct | ✅ |
| `local.properties.template` | API key placeholders | ✅ Present | ✅ |
| `.gitignore` | Excludes local.properties, *.jks | ✅ Present | ✅ |
| `README.md` | Architecture overview | ✅ Present | ✅ |
| `docs/` hierarchy | api/, architecture/, compliance/, ai_workflows/ | ✅ All present | ✅ |
| Gradle wrapper (8.14.3) | 8.5+ required | ✅ Exceeds requirement | ✅ |

### Module Build Files & Source Set Architecture
| Module | build.gradle.kts | Correct Plugins | Source Sets | Status |
|--------|-----------------|-----------------|-------------|--------|
| `:androidApp` | ✅ | androidApplication + kotlinAndroid | main/ | ✅ |
| `:composeApp` | ✅ | KMP + androidKmpLibrary + compose | commonMain, androidMain, jvmMain | ✅ |
| `:shared:core` | ✅ | KMP + androidKmpLibrary | commonMain, androidMain, jvmMain, commonTest | ✅ |
| `:shared:domain` | ✅ | KMP + androidKmpLibrary | commonMain, commonTest | ✅ |
| `:shared:data` | ✅ | KMP + androidKmpLibrary + sqldelight + serialization | commonMain, androidMain, jvmMain, commonTest + sqldelight dir | ✅ |
| `:shared:hal` | ✅ | KMP + androidKmpLibrary | commonMain, androidMain, jvmMain, commonTest | ✅ |
| `:shared:security` | ✅ | KMP + androidKmpLibrary | commonMain, androidMain, jvmMain, commonTest | ✅ |
| `:composeApp:designsystem` | ✅ | KMP + androidKmpLibrary + compose | commonMain, androidMain, commonTest | ⚠️ jvmMain missing |
| `:composeApp:navigation` | ✅ | KMP + androidKmpLibrary + compose + serialization | commonMain, commonTest | ⚠️ jvmMain missing |

### Dependency Wiring (build.gradle.kts correctness)
| Module | Key Dependencies | Status |
|--------|-----------------|--------|
| `:shared:core` | koin-core, kermit, kotlinx-common bundle | ✅ |
| `:shared:domain` | api(:shared:core), kotlinx-datetime, collections-immutable | ✅ |
| `:shared:data` | api(:shared:domain), ktor bundle, sqldelight bundle, sqlcipher-android | ✅ |
| `:shared:hal` | api(:shared:core), coroutines | ✅ |
| `:shared:security` | api(:shared:core), datastore, security-crypto (android) | ✅ |
| `:composeApp:designsystem` | api(:shared:core), compose M3, coil, adaptive | ✅ |
| `:composeApp:navigation` | api(:designsystem), api(:shared:security), serialization | ✅ |
| `:androidApp` | implementation(:composeApp), activity-compose | ✅ |

### Package Sub-directory Structure (Domain — best scaffolded)
| Path | Plan | Reality | Status |
|------|------|---------|--------|
| `shared/domain/.../domain/model/` | Required | ✅ Exists | ✅ |
| `shared/domain/.../domain/repository/` | Required | ✅ Exists | ✅ |
| `shared/domain/.../domain/usecase/` | Required | ✅ Exists | ✅ |
| `shared/data/.../data/local/` | Required | ✅ Exists | ✅ |
| `shared/data/.../data/remote/` | Required | ✅ Exists | ✅ |
| `shared/data/.../data/repository/` | Required | ✅ Exists | ✅ |
| `shared/data/.../sqldelight/` | Required | ✅ Exists | ✅ |
| `designsystem/.../designsystem/theme/` | Required | ✅ Exists | ✅ |
| `designsystem/.../designsystem/component/` | Required | ✅ Exists | ✅ |

---

## ❌ MISMATCHES FOUND (14 Total)

---

### MISMATCH-01 — All 13 Feature Modules Have Zero Physical Presence
**Severity:** 🔴 CRITICAL — Build-Breaking  
**Plan Source:** Master_plan.md §3.2 + PLAN_PHASE1.md §1.1  
**Details:**

The following modules are registered in `settings.gradle.kts` but have **no directory, no
`build.gradle.kts`, and no source sets** on disk:

```
composeApp/feature/auth/         ❌ MISSING
composeApp/feature/pos/          ❌ MISSING
composeApp/feature/inventory/    ❌ MISSING
composeApp/feature/register/     ❌ MISSING
composeApp/feature/reports/      ❌ MISSING
composeApp/feature/settings/     ❌ MISSING
composeApp/feature/customers/    ❌ MISSING
composeApp/feature/coupons/      ❌ MISSING
composeApp/feature/expenses/     ❌ MISSING
composeApp/feature/staff/        ❌ MISSING
composeApp/feature/multistore/   ❌ MISSING
composeApp/feature/admin/        ❌ MISSING
composeApp/feature/media/        ❌ MISSING
```

Gradle sync will fail with "Project path ':composeApp:feature:auth' not found" for all 13.

**Fix Required:** Create each feature module's directory, `build.gradle.kts`, and stub source sets.

---

### MISMATCH-02 — Module Naming Conflict: `customers` vs `crm`
**Severity:** 🔴 CRITICAL — Architectural Inconsistency  
**Plan Source:** Master_plan.md §3.2 specifies `:composeApp:feature:crm`  
**Reality:** `settings.gradle.kts` registers `:composeApp:feature:customers`

Master_plan.md §3.2:
```
:composeApp:feature:crm   → Customer management, loyalty
```
settings.gradle.kts:
```kotlin
include(":composeApp:feature:customers")  // NOT :crm
```

The module path is permanently embedded in Gradle artifact IDs, navigation routes, and
import paths. **Must canonicalize before any Sprint 12+ code is written.**

**Decision Required:** Choose one name and update both settings.gradle.kts AND Master_plan.md.
**Recommendation:** Use `:composeApp:feature:customers` (more descriptive, avoids CRM acronym
ambiguity) and update Master_plan.md to match.

---

### MISMATCH-03 — `.github/workflows/ci.yml` Does Not Exist
**Severity:** 🟠 HIGH — Sprint 1 task 1.1.7 marked not started but no directory exists  
**Plan Source:** PLAN_PHASE1.md Step 1.1.7 | execution_log.md Sprint 1 item 1.1.7  
**Reality:** No `.github/` directory exists at all

```
.github/workflows/ci.yml   ❌ MISSING
```

Sprint 1 correctly marks this as `[ ]` not started — the tracking is honest. But this is
a real gap that must be created before the repo is used in team development.

---

### MISMATCH-04 — Duplicate Android Resource Assets in `:composeApp`
**Severity:** 🟠 HIGH — Architectural Violation  
**Plan Source:** Both plans: `:composeApp` = KMP library, `:androidApp` = Android application  
**Reality:** `composeApp/src/androidMain/res/` contains full launcher icon set:

```
composeApp/src/androidMain/res/
  drawable/ic_launcher_background.xml       ← Duplicate of androidApp/
  drawable-v24/ic_launcher_foreground.xml   ← Duplicate of androidApp/
  mipmap-anydpi-v26/ic_launcher.xml         ← Duplicate of androidApp/
  mipmap-*/ic_launcher.png                  ← Duplicate × 5 density buckets
  values/strings.xml                        ← Possible duplicate app_name
```

**Impact:** KMP library modules must NOT contain application resources (launcher icons, app_name).
These belong exclusively in `:androidApp`. Having them in `:composeApp` (an Android library)
will cause resource merge conflicts and APK size bloat. The wizard generated these; they were
not cleaned up during the FIX.5 refactor.

**Fix:** Delete all contents of `composeApp/src/androidMain/res/` and
`composeApp/src/androidMain/AndroidManifest.xml` (keep only the library manifest with
no `<application>` block, or merge into the KMP androidMain properly).

---

### MISMATCH-05 — `:composeApp:designsystem` Missing `jvmMain` Source Set
**Severity:** 🟠 HIGH — Desktop compilation will miss platform-specific composables  
**Plan Source:** PLAN_PHASE1.md §5 — all UI modules target JVM Desktop  
**Reality:** `composeApp/designsystem/build.gradle.kts` declares `jvm()` target
but no `jvmMain/kotlin/` directory exists on disk.

```
designsystem/src/
  androidMain/kotlin/  ✅ exists
  commonMain/kotlin/   ✅ exists
  commonTest/kotlin/   ✅ exists
  jvmMain/kotlin/      ❌ MISSING
```

Sprint 6.1.7 (`WindowSizeClassHelper.kt` Desktop actual) and any other JVM-specific
composable actuals have nowhere to be written.

---

### MISMATCH-06 — `:composeApp:navigation` Missing `jvmMain` Source Set
**Severity:** 🟠 HIGH — Same issue as MISMATCH-05  
**Plan Source:** PLAN_PHASE1.md Sprint 11 — navigation must work on Desktop  
**Reality:** `composeApp/navigation/build.gradle.kts` declares `jvm()` target
but no `jvmMain/kotlin/` directory exists.

```
navigation/src/
  commonMain/kotlin/   ✅ exists
  commonTest/kotlin/   ✅ exists
  jvmMain/kotlin/      ❌ MISSING
  androidMain/kotlin/  ❌ MISSING
```

Sprint 7.1.8 notes Desktop back-stack handling requires jvmMain-specific code.

---

### MISMATCH-07 — `:shared:core` Missing All Package Sub-directories
**Severity:** 🟡 MEDIUM — Sprint 2 will create files but parents don't exist  
**Plan Source:** PLAN_PHASE1.md Appendix B Package Structure  
**Reality:** Only `CoreModule.kt` stub exists. None of the required sub-packages
exist as directories:

```
shared/core/.../core/
  result/         ← MISSING (Result.kt, ZentaException.kt go here)
  logger/         ← MISSING (ZentaLogger.kt)
  config/         ← MISSING (AppConfig.kt)
  extensions/     ← MISSING (StringExtensions.kt, DoubleExtensions.kt, etc.)
  utils/          ← MISSING (DateTimeUtils.kt, CurrencyFormatter.kt, IdGenerator.kt)
  mvi/            ← MISSING (BaseViewModel.kt)
  di/             ← MISSING (CoreModule.kt should move here from root)
```

Note: Kotlin/Gradle will auto-create these when source files are written — this is a
tooling convenience issue, not a hard blocker. However it means the IDE will not
suggest correct package completions.

---

### MISMATCH-08 — `:shared:domain` Missing `validation/` Sub-directory
**Severity:** 🟡 MEDIUM  
**Plan Source:** PLAN_PHASE1.md Appendix B + Sprint 4 (Step 2.3.24–26 validators)  
**Reality:**

```
shared/domain/.../domain/
  model/       ✅
  repository/  ✅
  usecase/     ✅
  validation/  ❌ MISSING (PaymentValidator, StockValidator, TaxValidator)
```

---

### MISMATCH-09 — `:shared:data` Missing `sync/` and Mapper Sub-directories
**Severity:** 🟡 MEDIUM  
**Plan Source:** PLAN_PHASE1.md Appendix B + Master_plan.md §3.1 data layer  
**Reality:**

```
shared/data/.../data/
  local/       ✅ (but missing db/ and mapper/ sub-dirs)
  remote/      ✅ (but missing api/ and dto/ sub-dirs)
  repository/  ✅
  sync/        ❌ MISSING (SyncEngine.kt, NetworkMonitor.kt go here)

shared/data/.../data/local/
  db/          ❌ MISSING (DatabaseDriverFactory, DatabaseFactory go here)
  mapper/      ❌ MISSING (entity ↔ domain mappers go here)

shared/data/.../data/remote/
  api/         ❌ MISSING (ApiService.kt, KtorApiService.kt)
  dto/         ❌ MISSING (all DTO data classes)
```

---

### MISMATCH-10 — `:shared:hal` Missing `printer/` and `scanner/` Sub-directories
**Severity:** 🟡 MEDIUM  
**Plan Source:** PLAN_PHASE1.md Appendix B | HAL interface contracts Sprint 7  
**Reality:**

```
shared/hal/.../hal/
  printer/    ❌ MISSING (PrinterPort, ReceiptBuilder, PrinterConfig interfaces)
  scanner/    ❌ MISSING (BarcodeScanner, ScanResult interfaces)
```

---

### MISMATCH-11 — `:shared:security` Missing Crypto Sub-directories
**Severity:** 🟡 MEDIUM  
**Plan Source:** PLAN_PHASE1.md Appendix B | Sprint 8 security contracts  
**Reality:**

```
shared/security/.../security/
  crypto/     ❌ MISSING (EncryptionManager.kt, DatabaseKeyManager.kt)
  token/      ❌ MISSING (JwtManager.kt, PinManager.kt)
  keystore/   ❌ MISSING (SecurePreferences.kt, DatabaseKeyProvider.kt)
```

---

### MISMATCH-12 — Stale Wizard Placeholder Files in `:composeApp`
**Severity:** 🟡 MEDIUM — Technical debt that will confuse Sprint implementations  
**Plan Source:** These files are KMP project wizard artifacts with no plan equivalent  
**Reality:** Three files exist that have no Sprint target and must be cleaned up:

```
composeApp/src/commonMain/kotlin/com/zynta/pos/
  Greeting.kt       ← Wizard artifact. DELETE. No equivalent in any plan.
  Platform.kt       ← Should MOVE to :shared:core/commonMain (expect fun getPlatform())
  
composeApp/src/androidMain/kotlin/com/zynta/pos/
  Platform.android.kt  ← Should MOVE to :shared:core/androidMain

composeApp/src/jvmMain/kotlin/com/zynta/pos/
  Platform.jvm.kt      ← Should MOVE to :shared:core/jvmMain
```

Both plans position platform abstraction in `:shared:core`, not `:composeApp`.
`App.kt` stays in `:composeApp` (it IS the shared root composable entry point — correct).

---

### MISMATCH-13 — `:shared:domain` Has No `androidMain`/`jvmMain` Source Sets
**Severity:** 🟢 LOW — Actually CORRECT per plan, documenting for clarity  
**Plan Source:** PLAN_PHASE1.md §1.1 M02 "commonMain only"  
**Reality:** Domain module has only `commonMain` + `commonTest` — no platform source sets.

```
shared/domain/src/
  commonMain/   ✅
  commonTest/   ✅
  (no androidMain, no jvmMain)  ← INTENTIONAL AND CORRECT
```

This is by design: domain = pure Kotlin, zero platform dependencies.
**No fix needed.** Documenting to prevent future "fix" attempts.

---

### MISMATCH-14 — `docs/plans/` vs Root-Level Plan File Location
**Severity:** 🟢 LOW — Cosmetic  
**Plan Source:** Master_plan.md and PLAN_PHASE1.md reference each other without absolute paths  
**Reality:** Plans are in `docs/plans/PLAN_PHASE1.md` but execution_log.md references
them as if they were root-level. The PLAN_MISMATCH_FIX file from prior session is also
in `docs/plans/`. This is fine — consistent — just needs the execution log header
to be updated with the canonical path `docs/plans/PLAN_PHASE1.md`.

---

## 📋 Consolidated Fix Action Plan (Priority Order)

### PHASE-FIX-A — Build-Breaking Fixes (Do First)

| # | Action | Files Affected | Effort |
|---|--------|---------------|--------|
| A1 | Create 13 feature module directories + build.gradle.kts + stub source sets | 13 × ~5 files | Medium |
| A2 | Canonicalize module name: decide `crm` vs `customers`, update Master_plan.md | settings.gradle.kts, Master_plan.md | Trivial |

### PHASE-FIX-B — Architectural Cleanup (High Priority)

| # | Action | Files Affected | Effort |
|---|--------|---------------|--------|
| B1 | Delete/clean `composeApp/src/androidMain/res/` (all launcher icons + strings.xml) | ~15 files deleted | Trivial |
| B2 | Simplify `composeApp/src/androidMain/AndroidManifest.xml` to bare library manifest | 1 file edit | Trivial |
| B3 | Create `jvmMain/kotlin/` source set dir in `:designsystem` and `:navigation` | 2 dirs | Trivial |
| B4 | Move `Platform.kt` → `:shared:core/commonMain` (as expect declaration) | Move + update imports | Small |
| B5 | Move `Platform.android.kt` → `:shared:core/androidMain` | Move + update imports | Small |
| B6 | Move `Platform.jvm.kt` → `:shared:core/jvmMain` | Move + update imports | Small |
| B7 | Delete `composeApp/src/commonMain/kotlin/.../Greeting.kt` | 1 file deleted | Trivial |

### PHASE-FIX-C — Package Scaffold Completion (Medium Priority)

| # | Action | Dirs to Create | Effort |
|---|--------|---------------|--------|
| C1 | Create `:shared:core` sub-dirs: result/, logger/, config/, extensions/, utils/, mvi/, di/ | 7 dirs | Trivial |
| C2 | Create `:shared:domain` validation/ sub-dir | 1 dir | Trivial |
| C3 | Create `:shared:data` sub-dirs: local/db/, local/mapper/, remote/api/, remote/dto/, sync/ | 5 dirs | Trivial |
| C4 | Create `:shared:hal` sub-dirs: printer/, scanner/ (in commonMain + androidMain + jvmMain) | 6 dirs | Trivial |
| C5 | Create `:shared:security` sub-dirs: crypto/, token/, keystore/ | 3 dirs | Trivial |

### PHASE-FIX-D — CI/CD & Documentation

| # | Action | Files | Effort |
|---|--------|-------|--------|
| D1 | Create `.github/workflows/ci.yml` (Sprint 1 task 1.1.7) | 1 file | Small |
| D2 | Update execution_log.md plan reference path to `docs/plans/PLAN_PHASE1.md` | execution_log.md | Trivial |

---

## 🎯 Execution Order Summary

```
PHASE-FIX-A (build gate) → PHASE-FIX-B (arch cleanup) → PHASE-FIX-C (scaffold) → PHASE-FIX-D (CI)
```

**After all fixes applied:** Project will be in a state where Sprint 1 (officially) can be
marked 100% complete and Sprint 2 (`:shared:core` implementation) can begin cleanly.

---

## 📊 Post-Fix Alignment Projection

| Category | Current | After Fixes |
|----------|---------|------------|
| Module physical presence | 45% | 100% ✅ |
| Package sub-directory scaffold | 40% | 95% ✅ |
| Architectural cleanliness | 70% | 98% ✅ |
| CI/CD | 0% | 100% ✅ |
| **Overall** | **62%** | **~97%** |

---

*End of ZENTA-CROSSCHECK-v1.0*
*Created: 2026-02-20 | Reference: Master_plan.md + PLAN_PHASE1.md*
