# ZyntaPOS — Consolidated Mismatch Fix Plan (Master)
> **Doc ID:** ZENTA-CONSOLIDATED-FIX-v1.0
> **Created:** 2026-02-20
> **Supersedes:** PLAN_MISMATCH_FIX_v1.0.md · PLAN_STRUCTURE_CROSSCHECK_v1.0.md
> **Status:** 🔴 AUTHORITATIVE — Execute this document only

---

## 📊 Gap Analysis: Two Audit Documents vs Reality

### Mapping Table — All Issues Across Both Documents

| ID | Issue | In MISMATCH_FIX_v1.0? | In CROSSCHECK_v1.0? | Net Status |
|----|-------|----------------------|---------------------|------------|
| **CRITICAL-1 / MM-01** | 13 feature modules registered but no dirs | ✅ FIX-A (full) | ✅ MISMATCH-01 | ✅ Covered |
| **CRITICAL-2** | Package namespace com.zentapos vs com.zynta.pos in log | ✅ FIX-C (full) | ⚠️ Mentioned in notes only | ⚠️ Partial |
| **CRITICAL-3** | jSerialComm + jBCrypt missing from libs.versions.toml | ✅ FIX-B (full) | ❌ Not listed | ⚠️ Partial |
| **CRITICAL-4** | Sprint 1 tasks duplicate Phase 0 (wrongly marked not started) | ✅ FIX-D (full) | ❌ Not listed | ⚠️ Partial |
| **CRITICAL-5** | composeHotReload plugin undocumented in execution_log | ✅ FIX-E (full) | ❌ Not listed | ⚠️ Partial |
| **MM-02** | Module name conflict: `:crm` (Master plan) vs `:customers` (settings.gradle) | ❌ NOT PRESENT | ✅ MISMATCH-02 | ❌ **GAP** |
| **MM-03** | `.github/workflows/ci.yml` does not exist | ⚠️ Acknowledged only (FIX-D.3 marks it `[ ]` but no creation task) | ✅ MISMATCH-03 | ❌ **GAP — No FIX task** |
| **MM-04** | Duplicate launcher icons/res in `:composeApp/src/androidMain/res/` | ❌ NOT PRESENT | ✅ MISMATCH-04 | ❌ **GAP** |
| **MM-05** | `:composeApp:designsystem` missing `jvmMain/kotlin/` directory | ❌ NOT PRESENT | ✅ MISMATCH-05 | ❌ **GAP** |
| **MM-06** | `:composeApp:navigation` missing `jvmMain/` + `androidMain/` directories | ❌ NOT PRESENT | ✅ MISMATCH-06 | ❌ **GAP** |
| **MM-07** | `:shared:core` missing 7 sub-package dirs (result/, logger/, etc.) | ⚠️ MINOR-1 says "expected, no fix" — **WRONG** | ✅ MISMATCH-07 | ❌ **GAP — Misclassified** |
| **MM-08** | `:shared:domain` missing `validation/` sub-dir | ❌ NOT PRESENT | ✅ MISMATCH-08 | ❌ **GAP** |
| **MM-09** | `:shared:data` missing sync/, local/db/, local/mapper/, remote/api/, remote/dto/ | ❌ NOT PRESENT | ✅ MISMATCH-09 | ❌ **GAP** |
| **MM-10** | `:shared:hal` missing printer/, scanner/ sub-dirs | ❌ NOT PRESENT | ✅ MISMATCH-10 | ❌ **GAP** |
| **MM-11** | `:shared:security` missing crypto/, token/, keystore/ sub-dirs | ❌ NOT PRESENT | ✅ MISMATCH-11 | ❌ **GAP** |
| **MM-12** | Stale wizard files: Greeting.kt (delete), Platform.* (move to :shared:core) | ❌ NOT PRESENT | ✅ MISMATCH-12 | ❌ **GAP** |
| **MM-13** | `:shared:domain` has no androidMain/jvmMain (intentional) | ✅ Implicitly correct | ✅ MISMATCH-13 (no fix needed) | ✅ Confirmed correct — no action |
| **MM-14** | docs/plans/ path reference inconsistency in execution_log | ⚠️ Partially in FIX-C.2 | ✅ MISMATCH-14 | ⚠️ Partial |

### Summary

| Result | Count | Items |
|--------|-------|-------|
| ✅ Fully covered in MISMATCH_FIX | 1 | CRITICAL-1/MM-01 |
| ⚠️ Only in MISMATCH_FIX (not in crosscheck) | 4 | CRITICAL-2, 3, 4, 5 |
| ❌ Only in CROSSCHECK (gaps in MISMATCH_FIX) | 9 | MM-02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12 |
| ✅ No fix needed (confirmed by both) | 1 | MM-13 |

**PLAN_MISMATCH_FIX_v1.0.md missed 9 issues entirely and misclassified 1 (MM-07 as "no fix").**
**This document supersedes both and contains ALL 18 distinct issues.**

---

## 🔴 CRITICAL FIXES — Build Blockers (Do Before Anything Else)

---

### FIX-01 — Create 13 Feature Module Physical Scaffolds
**Sources:** CRITICAL-1 + MM-01 | **Severity:** 🔴 BUILD-BREAKING

Each module needs: `build.gradle.kts` + 4 source set dirs + stub Koin module `.kt` file.

**Standard feature `build.gradle.kts` template:**
```kotlin
// Each feature module: KMP library + Compose + Koin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
kotlin {
    android {
        namespace  = "com.zynta.pos.feature.[name]"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
    }
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":composeApp:navigation"))
            implementation(libs.bundles.koin.common)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }
        androidMain.dependencies { implementation(libs.koin.android) }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies { implementation(libs.bundles.testing.common) }
    }
}
```

**Atomic tasks:**
- [ ] FIX-01.01 — Create `composeApp/feature/auth/` + build.gradle.kts + AuthModule.kt stub
- [ ] FIX-01.02 — Create `composeApp/feature/pos/` + build.gradle.kts + PosModule.kt stub
- [ ] FIX-01.03 — Create `composeApp/feature/inventory/` + build.gradle.kts + InventoryModule.kt stub
- [ ] FIX-01.04 — Create `composeApp/feature/register/` + build.gradle.kts + RegisterModule.kt stub
- [ ] FIX-01.05 — Create `composeApp/feature/reports/` + build.gradle.kts + ReportsModule.kt stub
- [ ] FIX-01.06 — Create `composeApp/feature/settings/` + build.gradle.kts + SettingsModule.kt stub
- [ ] FIX-01.07 — Create `composeApp/feature/customers/` + build.gradle.kts + CustomersModule.kt stub
- [ ] FIX-01.08 — Create `composeApp/feature/coupons/` + build.gradle.kts + CouponsModule.kt stub
- [ ] FIX-01.09 — Create `composeApp/feature/expenses/` + build.gradle.kts + ExpensesModule.kt stub
- [ ] FIX-01.10 — Create `composeApp/feature/staff/` + build.gradle.kts + StaffModule.kt stub
- [ ] FIX-01.11 — Create `composeApp/feature/multistore/` + build.gradle.kts + MultistoreModule.kt stub
- [ ] FIX-01.12 — Create `composeApp/feature/admin/` + build.gradle.kts + AdminModule.kt stub
- [ ] FIX-01.13 — Create `composeApp/feature/media/` + build.gradle.kts + MediaModule.kt stub
- [ ] FIX-01.14 — Run `./gradlew tasks` — verify zero "project path not found" errors

---

### FIX-02 — Canonicalize Module Name: `:crm` vs `:customers`
**Sources:** MM-02 (NEW — not in MISMATCH_FIX_v1.0) | **Severity:** 🔴 CRITICAL

`Master_plan.md §3.2` defines `:composeApp:feature:crm`
`settings.gradle.kts` registers `:composeApp:feature:customers`

**Decision:** Use `:composeApp:feature:customers` (already in settings + more descriptive).
Update `Master_plan.md §3.2` module table to match.

- [ ] FIX-02.01 — Edit `Master_plan.md §3.2` KMP Source Set Structure: change `:composeApp:feature:crm` → `:composeApp:feature:customers`
- [ ] FIX-02.02 — Search all plan docs for any other `:crm` references and update
- [ ] FIX-02.03 — Confirm `settings.gradle.kts` already has `:composeApp:feature:customers` ✅ (no change needed)

---

## 🟠 HIGH FIXES — Architectural Violations

---

### FIX-03 — Remove Duplicate Android Resources from `:composeApp`
**Sources:** MM-04 (NEW — not in MISMATCH_FIX_v1.0) | **Severity:** 🟠 HIGH

`:composeApp` is a **KMP library**. It must NOT contain launcher icons, `app_name` string,
or any application-level Android resources. These belong exclusively in `:androidApp`.
Leaving them causes APK resource merge conflicts and violates the library/app separation.

- [ ] FIX-03.01 — Delete entire `composeApp/src/androidMain/res/` directory (all 15 files)
- [ ] FIX-03.02 — Verify `composeApp/src/androidMain/AndroidManifest.xml` contains NO `<application>` block — only `<manifest xmlns:android=...>` with no child elements (bare library manifest)
- [ ] FIX-03.03 — Confirm `androidApp/src/main/res/` still has all launcher icons intact ✅

---

### FIX-04 — Create Missing `jvmMain` Source Set Directories
**Sources:** MM-05 + MM-06 (NEW) | **Severity:** 🟠 HIGH

Both `:composeApp:designsystem` and `:composeApp:navigation` declare `jvm()` target in their
`build.gradle.kts` files but the `jvmMain/kotlin/` directory doesn't exist on disk.
Sprint 9 (WindowSizeClassHelper Desktop actual) and Sprint 11 (Desktop nav handling)
will fail without this.

- [ ] FIX-04.01 — Create `composeApp/designsystem/src/jvmMain/kotlin/com/zynta/pos/designsystem/` directory + `.gitkeep`
- [ ] FIX-04.02 — Create `composeApp/navigation/src/jvmMain/kotlin/com/zynta/pos/navigation/` directory + `.gitkeep`
- [ ] FIX-04.03 — Create `composeApp/navigation/src/androidMain/kotlin/com/zynta/pos/navigation/` directory + `.gitkeep`

---

### FIX-05 — Move Platform Expect/Actual Files to `:shared:core`
**Sources:** MM-12 (NEW) | **Severity:** 🟠 HIGH

Both plans place platform abstraction in `:shared:core`, not `:composeApp`. The KMP wizard
generated `Platform.kt`, `Platform.android.kt`, `Platform.jvm.kt` in `:composeApp` as
scaffolding stubs. They must be relocated before Sprint 2 begins.

- [ ] FIX-05.01 — Move `composeApp/src/commonMain/kotlin/.../Platform.kt` → `shared/core/src/commonMain/kotlin/com/zynta/pos/core/Platform.kt`
- [ ] FIX-05.02 — Move `composeApp/src/androidMain/kotlin/.../Platform.android.kt` → `shared/core/src/androidMain/kotlin/com/zynta/pos/core/Platform.android.kt`
- [ ] FIX-05.03 — Move `composeApp/src/jvmMain/kotlin/.../Platform.jvm.kt` → `shared/core/src/jvmMain/kotlin/com/zynta/pos/core/Platform.jvm.kt`
- [ ] FIX-05.04 — Delete `composeApp/src/commonMain/kotlin/.../Greeting.kt` (wizard artifact, no plan equivalent)
- [ ] FIX-05.05 — Update `composeApp/src/commonMain/kotlin/.../App.kt` — remove `Greeting` import if present
- [ ] FIX-05.06 — Add `:composeApp` → `implementation(project(":shared:core"))` dependency in `composeApp/build.gradle.kts` to resolve Platform import

---

### FIX-06 — Create CI/CD GitHub Actions Workflow
**Sources:** MM-03 + CRITICAL-4/FIX-D.3 | **Severity:** 🟠 HIGH

Sprint 1 task 1.1.7 — genuinely not created in Phase 0. Required for team development.

- [ ] FIX-06.01 — Create `.github/workflows/` directory
- [ ] FIX-06.02 — Create `.github/workflows/ci.yml`:
  ```yaml
  name: CI
  on:
    push:
      branches: [main, develop]
    pull_request:
      branches: [main]
  jobs:
    build:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: zulu, java-version: 21 }
        - uses: gradle/actions/setup-gradle@v3
        - run: ./gradlew :shared:core:jvmJar :shared:domain:jvmJar
        - run: ./gradlew :androidApp:assembleDebug
        - run: ./gradlew :composeApp:jvmJar
        - run: ./gradlew allTests
  ```

---

## 🟡 MEDIUM FIXES — Package Scaffold Completion

---

### FIX-07 — Complete `:shared:core` Internal Sub-package Directories
**Sources:** MM-07 — MISCLASSIFIED as "no fix" in MISMATCH_FIX_v1.0 | **Severity:** 🟡 MEDIUM

MISMATCH_FIX_v1.0 MINOR-1 incorrectly said this was "expected, no fix needed."
In reality, the sub-directories must exist for IDE autocomplete and to match Appendix B
of PLAN_PHASE1.md. Gradle will auto-create them at compile time, but having them
pre-created prevents Sprint 2 from producing files in the root package.

- [ ] FIX-07.01 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/result/`
- [ ] FIX-07.02 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/logger/`
- [ ] FIX-07.03 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/config/`
- [ ] FIX-07.04 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/extensions/`
- [ ] FIX-07.05 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/utils/`
- [ ] FIX-07.06 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/mvi/`
- [ ] FIX-07.07 — Create `shared/core/src/commonMain/kotlin/com/zynta/pos/core/di/`
- [ ] FIX-07.08 — Move `CoreModule.kt` from `core/` root → `core/di/CoreModule.kt` (matches Appendix B)

---

### FIX-08 — Create `:shared:domain` `validation/` Sub-directory
**Sources:** MM-08 (NEW) | **Severity:** 🟡 MEDIUM

- [ ] FIX-08.01 — Create `shared/domain/src/commonMain/kotlin/com/zynta/pos/domain/validation/`

---

### FIX-09 — Create `:shared:data` Missing Sub-directories
**Sources:** MM-09 (NEW) | **Severity:** 🟡 MEDIUM

- [ ] FIX-09.01 — Create `shared/data/src/commonMain/kotlin/com/zynta/pos/data/local/db/`
- [ ] FIX-09.02 — Create `shared/data/src/commonMain/kotlin/com/zynta/pos/data/local/mapper/`
- [ ] FIX-09.03 — Create `shared/data/src/commonMain/kotlin/com/zynta/pos/data/remote/api/`
- [ ] FIX-09.04 — Create `shared/data/src/commonMain/kotlin/com/zynta/pos/data/remote/dto/`
- [ ] FIX-09.05 — Create `shared/data/src/commonMain/kotlin/com/zynta/pos/data/sync/`
- [ ] FIX-09.06 — Move `DataModule.kt` from `data/` root → `data/di/DataModule.kt`

---

### FIX-10 — Create `:shared:hal` `printer/` and `scanner/` Sub-directories
**Sources:** MM-10 (NEW) | **Severity:** 🟡 MEDIUM

Create in commonMain (interfaces), androidMain (actuals), and jvmMain (actuals).

- [ ] FIX-10.01 — Create `shared/hal/src/commonMain/kotlin/com/zynta/pos/hal/printer/`
- [ ] FIX-10.02 — Create `shared/hal/src/commonMain/kotlin/com/zynta/pos/hal/scanner/`
- [ ] FIX-10.03 — Create `shared/hal/src/androidMain/kotlin/com/zynta/pos/hal/printer/`
- [ ] FIX-10.04 — Create `shared/hal/src/androidMain/kotlin/com/zynta/pos/hal/scanner/`
- [ ] FIX-10.05 — Create `shared/hal/src/jvmMain/kotlin/com/zynta/pos/hal/printer/`
- [ ] FIX-10.06 — Create `shared/hal/src/jvmMain/kotlin/com/zynta/pos/hal/scanner/`
- [ ] FIX-10.07 — Move `HalModule.kt` from `hal/` root → `hal/di/HalModule.kt`

---

### FIX-11 — Create `:shared:security` Crypto Sub-directories
**Sources:** MM-11 (NEW) | **Severity:** 🟡 MEDIUM

- [ ] FIX-11.01 — Create `shared/security/src/commonMain/kotlin/com/zynta/pos/security/crypto/`
- [ ] FIX-11.02 — Create `shared/security/src/commonMain/kotlin/com/zynta/pos/security/token/`
- [ ] FIX-11.03 — Create `shared/security/src/commonMain/kotlin/com/zynta/pos/security/keystore/`
- [ ] FIX-11.04 — Create `shared/security/src/androidMain/kotlin/com/zynta/pos/security/crypto/`
- [ ] FIX-11.05 — Create `shared/security/src/androidMain/kotlin/com/zynta/pos/security/keystore/`
- [ ] FIX-11.06 — Create `shared/security/src/jvmMain/kotlin/com/zynta/pos/security/crypto/`
- [ ] FIX-11.07 — Create `shared/security/src/jvmMain/kotlin/com/zynta/pos/security/keystore/`
- [ ] FIX-11.08 — Move `SecurityModule.kt` from `security/` root → `security/di/SecurityModule.kt`

---

## 🟠 MEDIUM FIXES — Dependency & Log Completeness (from MISMATCH_FIX_v1.0)

---

### FIX-12 — Add Missing Library Dependencies to Version Catalog
**Sources:** CRITICAL-3 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 HIGH

- [ ] FIX-12.01 — Add to `libs.versions.toml [versions]`: `jserialcomm = "2.10.4"`
- [ ] FIX-12.02 — Add to `libs.versions.toml [versions]`: `jbcrypt = "0.4"`
- [ ] FIX-12.03 — Add to `libs.versions.toml [libraries]`: `jserialcomm = { module = "com.fazecast:jSerialComm", version.ref = "jserialcomm" }`
- [ ] FIX-12.04 — Add to `libs.versions.toml [libraries]`: `jbcrypt = { module = "org.mindrot:jbcrypt", version.ref = "jbcrypt" }`
- [ ] FIX-12.05 — Document in execution_log.md: Desktop DB encryption = JCE AES-256-GCM (app layer), not SQLCipher JDBC

---

### FIX-13 — Reconcile execution_log.md Sprint 1 Statuses
**Sources:** CRITICAL-4 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 MEDIUM

- [ ] FIX-13.01 — Mark execution_log.md Sprint 1 tasks 1.1.1–1.1.6 + 1.1.8 as `[x] Finished (Completed in Phase 0)`
- [ ] FIX-13.02 — Note on 1.1.2: actual versions are kotlin=2.3.0, agp=8.13.2, composeMp=1.10.0
- [ ] FIX-13.03 — Sprint 1 task 1.1.7 remains `[ ]` — covered by FIX-06 above

---

### FIX-14 — Document Namespace + Undocumented Plugin in execution_log.md
**Sources:** CRITICAL-2 + CRITICAL-5 (from MISMATCH_FIX_v1.0) | **Severity:** 🟡 MEDIUM

- [ ] FIX-14.01 — Add canonical namespace note to execution_log.md header:
  `Root Package: com.zynta.pos (all Sprint 2–24 file paths using com/zentapos/ → read as com/zynta/pos/)`
- [ ] FIX-14.02 — Add session note: composeHotReload 1.0.0 present in catalog (undocumented addition)
- [ ] FIX-14.03 — Update execution_log.md reference plan path: `docs/plans/PLAN_PHASE1.md`

---

## ✅ Confirmed Correct — No Action Required

| Item | Why |
|------|-----|
| `:androidApp` / `:composeApp` split | Correct AGP 9+ pattern |
| `:shared:domain` has no androidMain/jvmMain | Intentional — pure commonMain only |
| `shared/hal/` has no `/build/` dir | Normal — never compiled yet |
| All 9 module `build.gradle.kts` dependency graphs | Verified correct |
| SQLDelight ZyntaDatabase config in `:shared:data` | Correct package, correct srcDirs |

---

## 📋 Execution Order

```
CRITICAL FIRST (build gate):
  FIX-01 (feature modules) → FIX-02 (name canonical)

ARCHITECTURAL CLEANUP:
  FIX-03 (remove duplicate res) → FIX-04 (jvmMain dirs) →
  FIX-05 (move Platform files) → FIX-06 (CI workflow)

SCAFFOLD COMPLETION:
  FIX-07 → FIX-08 → FIX-09 → FIX-10 → FIX-11 (all sub-dirs)

LOG + CATALOG UPDATES:
  FIX-12 (add deps) → FIX-13 (Sprint 1 status) → FIX-14 (namespace)

VERIFY:
  ./gradlew assembleDebug + jvmJar — BUILD SUCCESSFUL
```

---

## 📊 Issue Ownership Matrix

| Fix | Source Doc | In MISMATCH_FIX? | In CROSSCHECK? | Resolution |
|-----|-----------|-----------------|----------------|------------|
| FIX-01 | Both | ✅ FIX-A | ✅ MM-01 | This doc |
| FIX-02 | CROSSCHECK | ❌ Missing | ✅ MM-02 | This doc |
| FIX-03 | CROSSCHECK | ❌ Missing | ✅ MM-04 | This doc |
| FIX-04 | CROSSCHECK | ❌ Missing | ✅ MM-05/06 | This doc |
| FIX-05 | CROSSCHECK | ❌ Missing | ✅ MM-12 | This doc |
| FIX-06 | Both (partial) | ⚠️ FIX-D.3 only | ✅ MM-03 | This doc |
| FIX-07 | CROSSCHECK | ❌ Misclassified | ✅ MM-07 | This doc |
| FIX-08 | CROSSCHECK | ❌ Missing | ✅ MM-08 | This doc |
| FIX-09 | CROSSCHECK | ❌ Missing | ✅ MM-09 | This doc |
| FIX-10 | CROSSCHECK | ❌ Missing | ✅ MM-10 | This doc |
| FIX-11 | CROSSCHECK | ❌ Missing | ✅ MM-11 | This doc |
| FIX-12 | MISMATCH_FIX | ✅ FIX-B | ❌ Missing | This doc |
| FIX-13 | MISMATCH_FIX | ✅ FIX-D | ❌ Missing | This doc |
| FIX-14 | MISMATCH_FIX | ✅ FIX-C/E | ❌ Missing | This doc |

**Total Atomic Sub-tasks: 72**

---

*End of ZENTA-CONSOLIDATED-FIX-v1.0*
*Supersedes: PLAN_MISMATCH_FIX_v1.0.md + PLAN_STRUCTURE_CROSSCHECK_v1.0.md*
*Created: 2026-02-20*
