# ZyntaPOS — Mismatch Fix Plan
> **Doc ID:** ZENTA-MISMATCH-FIX-v1.0  
> **Created:** 2026-02-20  
> **Author:** Senior KMP Architect (AI Agent)  
> **Reference:** `docs/ai_workflows/execution_log.md` (ZENTA-EXEC-LOG-v1.1)  
> **Status:** 🔴 REQUIRES IMMEDIATE ACTION BEFORE Phase 1 Execution

---

## 📊 Executive Summary

Deep audit of `execution_log.md` against the physical project at  
`/Users/dilanka/Developer/StudioProjects/ZyntaPOS/` reveals **5 critical mismatches** and  
**3 minor discrepancies**. The most severe is a **build-breaking issue**: 13 feature modules  
declared in `settings.gradle.kts` have zero physical directories or `build.gradle.kts` files —  
Gradle sync will fail. Additionally, the execution log uses package `com.zentapos` while all  
generated source code uses `com.zynta.pos`, creating a systemic path mismatch for all Phase 1  
file creation tasks. These must be resolved before Sprint 1 execution begins.

---

## 🔍 Mismatch Analysis

### CRITICAL-1 — Feature Modules Declared but Non-Existent on Disk
**Severity:** 🔴 BUILD BREAKING  
**Log Claims:** P0.1.4 `[x]` — "Update `settings.gradle.kts`: register all new modules"  
**Reality:** `settings.gradle.kts` registers **13 feature modules** that have **no physical  
directories, no `build.gradle.kts`, and no source sets** on disk.

Missing modules (zero filesystem presence):
| Module Path | settings.gradle.kts | Disk |
|-------------|---------------------|------|
| `:composeApp:feature:auth` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:pos` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:inventory` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:register` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:reports` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:settings` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:customers` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:coupons` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:expenses` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:staff` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:multistore` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:admin` | ✅ Registered | ❌ Missing |
| `:composeApp:feature:media` | ✅ Registered | ❌ Missing |

**Root Cause:** The Gradle registration was completed but the scaffold creation step for feature  
modules was omitted. Phase 0 only scaffolded the 5 `shared:*` modules + `designsystem` + `navigation`.

---

### CRITICAL-2 — Package Namespace Mismatch (Log vs Code)
**Severity:** 🔴 SYSTEMIC — All Phase 1 file paths in log will be WRONG  
**Log Claims:** All Phase 1 file paths use package `com.zentapos.core`, `com.zentapos.domain`, etc.  
**Reality:** Every generated source file uses package `com.zynta.pos.*`

| Context | Package |
|---------|---------|
| execution_log.md Sprint 2–24 file paths | `com/zentapos/...` |
| All actual source files on disk | `com/zynta/pos/...` |
| Project root folder name | `ZyntaPOS` |
| Gradle artifact IDs | `zynta.pos` |

**Impact:** Without fixing the log, every "Files Output" reference in Sprints 2–24 will point to  
wrong paths, and automated integrity checks will fail. This must be documented as a canonical  
resolution in the execution log.

---

### CRITICAL-3 — Missing Critical Library Dependencies
**Severity:** 🔴 WILL BREAK compilations in Sprint 7 (:shared:hal) and Sprint 8 (:shared:security)  
**Log Claims:** P0.1.1 `[x]` — "add all Phase 1 deps" including jSerialComm  
**Reality:** `gradle/libs.versions.toml` is missing:

| Dependency | Required By | Status |
|------------|-------------|--------|
| `jSerialComm` (2.10.4) | `:shared:hal` Desktop serial printers | ❌ Missing |
| `jBCrypt` (0.4+) or equivalent | `:shared:security` PasswordHasher | ❌ Missing |
| SQLCipher JDBC (Desktop driver) | `:shared:data` DatabaseDriverFactory (JVM) | ❌ Missing |

Only `sqlcipher-android = "4.5.4"` is present for Android. The Desktop JVM encrypted driver  
(`com.github.sqlcipher:sqlcipher-jdbc` or `net.zetetic:sqlcipher-jdbc`) is not catalogued.

---

### CRITICAL-4 — Sprint 1 Tasks Duplicate Phase 0 Work (Log Consistency)
**Severity:** 🟠 LOGICAL INCONSISTENCY — Will cause re-execution of already-done work  
**Issue:** Sprint 1 tasks 1.1.1–1.1.4 mirror Phase 0 tasks P0.1.1–P0.1.4 exactly, but Sprint 1  
tasks are all marked `[ ] Not Started`. If executed blindly, the agent will overwrite files  
that Phase 0 already produced. Additionally, Sprint 1.1.2 specifies `kotlin=2.1.0` but the  
actual installed version is `2.3.0`.

| Sprint 1 Task | Phase 0 Equivalent | Action Needed |
|---------------|--------------------|---------------|
| 1.1.1 — root build.gradle.kts | P0.1.2 `[x]` Done | Mark 1.1.1 `[x]` |
| 1.1.2 — libs.versions.toml | P0.1.1 `[x]` Done | Mark 1.1.2 `[x]` (with correction for actual versions) |
| 1.1.3 — settings.gradle.kts | P0.1.4 `[x]` Done | Mark 1.1.3 `[x]` |
| 1.1.4 — gradle.properties | P0.1.3 `[x]` Done | Mark 1.1.4 `[x]` |
| 1.1.5 — local.properties.template | P0.3.1 `[x]` Done | Mark 1.1.5 `[x]` |
| 1.1.6 — .gitignore | P0.3.2 `[x]` Done | Mark 1.1.6 `[x]` |
| 1.1.8 — verify execution_log.md | P0.2.8 `[x]` Done | Mark 1.1.8 `[x]` |
| 1.1.7 — CI workflow `.github/workflows/ci.yml` | Not in Phase 0 | Remains `[ ]` |

---

### CRITICAL-5 — Undocumented Dependency Added (composeHotReload)
**Severity:** 🟠 TRACKING GAP — undocumented change breaks log integrity  
**Reality:** `libs.versions.toml` contains `composeHotReload = "1.0.0"` and  
`root build.gradle.kts` applies `alias(libs.plugins.composeHotReload) apply false`.  
This is NOT mentioned anywhere in `execution_log.md`.  
**Risk:** Future AI sessions may flag it as an unknown dependency or remove it during cleanup.

---

### MINOR-1 — shared/core Module: Only Stub File Exists
**Severity:** 🟡 EXPECTED — Phase 0 scope was scaffold only  
**Log Status:** P0.2.1 `[x]` "Create `:shared:core` Gradle module with source sets" — ✅ Correct  
**Reality:** `CoreModule.kt` stub exists. All Sprint 2 content tasks are correctly `[ ] Not Started`.  
**Assessment:** No mismatch. Phase 0 intent was directory + build file scaffold only. ✅

---

### MINOR-2 — :shared:hal Missing Build Directory
**Severity:** 🟡 HARMLESS  
**Reality:** `shared/hal/` has no `/build/` directory because it was never compiled.  
`shared/hal/build.gradle.kts` exists. All source sets (androidMain/commonMain/jvmMain) are  
scaffolded. This is normal — the module will build when Sprint 7 adds actual `expect` declarations.

---

### MINOR-3 — README.md and docs/ Hierarchy Partial
**Severity:** 🟡 COSMETIC  
**Log Claims:** P0.3.3 `[x]` — "Create `README.md`"  
**Reality:** `README.md` exists at root. `docs/api/README.md`, `docs/architecture/README.md`,  
`docs/compliance/README.md` exist but are placeholder `.gitkeep` files. `docs/plans/` exists  
with `PLAN_PHASE1.md`. The log also references `PLAN_XXX.md` but lists it under `docs/plans/`  
while earlier entries imply root level. This is a minor documentation inconsistency.

---

## 🛠️ Fix Plan — Atomic Deliverables

All fixes must be applied **in order** before Sprint 1 execution resumes.

---

### FIX-A — Create All Missing Feature Module Scaffolds
**Addresses:** CRITICAL-1  
**Priority:** P0 — Must be first (build gate)  
**Modules to create** (13 total, each needs: `build.gradle.kts` + source set directories + stub `.kt` file):

```
composeApp/feature/auth/
composeApp/feature/pos/
composeApp/feature/inventory/
composeApp/feature/register/
composeApp/feature/reports/
composeApp/feature/settings/
composeApp/feature/customers/
composeApp/feature/coupons/
composeApp/feature/expenses/
composeApp/feature/staff/
composeApp/feature/multistore/
composeApp/feature/admin/
composeApp/feature/media/
```

**Per-module scaffold:**
```
feature/[name]/
  build.gradle.kts          ← KMP library, compose, koin deps
  src/
    commonMain/kotlin/com/zynta/pos/feature/[name]/
      [Name]Module.kt       ← empty Koin module stub
    androidMain/kotlin/com/zynta/pos/feature/[name]/
      .gitkeep
    jvmMain/kotlin/com/zynta/pos/feature/[name]/
      .gitkeep
    commonTest/kotlin/com/zynta/pos/feature/[name]/
      .gitkeep
```

**Atomic sub-tasks:**
- [ ] FIX-A.1 — Create `composeApp/feature/auth/` scaffold
- [ ] FIX-A.2 — Create `composeApp/feature/pos/` scaffold
- [ ] FIX-A.3 — Create `composeApp/feature/inventory/` scaffold
- [ ] FIX-A.4 — Create `composeApp/feature/register/` scaffold
- [ ] FIX-A.5 — Create `composeApp/feature/reports/` scaffold
- [ ] FIX-A.6 — Create `composeApp/feature/settings/` scaffold
- [ ] FIX-A.7 — Create `composeApp/feature/customers/` scaffold
- [ ] FIX-A.8 — Create `composeApp/feature/coupons/` scaffold
- [ ] FIX-A.9 — Create `composeApp/feature/expenses/` scaffold
- [ ] FIX-A.10 — Create `composeApp/feature/staff/` scaffold
- [ ] FIX-A.11 — Create `composeApp/feature/multistore/` scaffold
- [ ] FIX-A.12 — Create `composeApp/feature/admin/` scaffold
- [ ] FIX-A.13 — Create `composeApp/feature/media/` scaffold
- [ ] FIX-A.14 — Verify `./gradlew tasks` completes without "Module not found" errors

---

### FIX-B — Add Missing Library Dependencies to Version Catalog
**Addresses:** CRITICAL-3  
**Priority:** P1  
**File:** `gradle/libs.versions.toml`

**Atomic sub-tasks:**
- [ ] FIX-B.1 — Add `jserialcomm = "2.10.4"` to `[versions]` block
- [ ] FIX-B.2 — Add `jBCrypt = "0.4"` to `[versions]` block  
- [ ] FIX-B.3 — Add library entry: `jserialcomm = { module = "com.fazecast:jSerialComm", version.ref = "jserialcomm" }`
- [ ] FIX-B.4 — Add library entry: `jbcrypt = { module = "org.mindrot:jbcrypt", version.ref = "jBCrypt" }`
- [ ] FIX-B.5 — Investigate + add Desktop SQLCipher JDBC driver:
  - Primary: `net.zetetic:sqlcipher-android` (Android only — already present)
  - For JVM Desktop: SQLDelight 2.x recommends unencrypted `sqlite-driver` + application-level encryption
  - Action: Document in execution_log.md that Desktop encryption strategy uses JCE AES-256-GCM  
    at application layer (not SQLCipher JDBC), update Sprint 5 (3.2.1) spec accordingly
- [ ] FIX-B.6 — Verify `./gradlew :shared:hal:jvmJar` resolves without missing deps

---

### FIX-C — Document Package Namespace Canonical Resolution in Execution Log
**Addresses:** CRITICAL-2  
**Priority:** P1  
**File:** `docs/ai_workflows/execution_log.md`

**Atomic sub-tasks:**
- [ ] FIX-C.1 — Add "Canonical Package Namespace" section to execution log header:
  ```
  ## 📌 CANONICAL NAMESPACE RESOLUTION
  - Project Name: ZyntaPOS (not ZentaPOS — log uses legacy name from initial planning)
  - Root Package: com.zynta.pos (not com.zentapos)
  - File Path Mapping: All Sprint 2–24 "Files Output" sections using com/zentapos/
    must be read as com/zynta/pos/ on disk
  - Module Artifact IDs: zynta.pos.* (not zentapos.*)
  ```
- [ ] FIX-C.2 — Add session note: `2026-02-20 | MISMATCH-FIX: Canonical namespace is com.zynta.pos. All log file paths use legacy com.zentapos prefix — treat as com/zynta/pos/ on disk.`

---

### FIX-D — Reconcile Sprint 1 Task Status Against Phase 0 Completions
**Addresses:** CRITICAL-4  
**Priority:** P1  
**File:** `docs/ai_workflows/execution_log.md`

**Atomic sub-tasks:**
- [ ] FIX-D.1 — Mark Sprint 1 tasks 1.1.1–1.1.6 and 1.1.8 as `[x] Finished` with note
  `(Completed in Phase 0 — see P0.x references)`
- [ ] FIX-D.2 — Add note to 1.1.2: `Actual versions differ from spec — kotlin=2.3.0, agp=8.13.2,  
  composeMp=1.10.0 (all newer than spec; catalog verified correct)`
- [ ] FIX-D.3 — Keep 1.1.7 (CI workflow) as `[ ]` — genuinely not created
- [ ] FIX-D.4 — Update Sprint 1 progress tracker: 7/8 Done → 🟡

---

### FIX-E — Document composeHotReload Plugin in Execution Log
**Addresses:** CRITICAL-5  
**Priority:** P2  
**File:** `docs/ai_workflows/execution_log.md`

**Atomic sub-tasks:**
- [ ] FIX-E.1 — Add to Phase 0 P0.1.1: `Note: composeHotReload 1.0.0 added for JVM desktop  
  live-reload DX (not in original spec but present in catalog + root build.gradle.kts)`
- [ ] FIX-E.2 — Add session note documenting the undocumented addition

---

## ✅ Execution Order

```
FIX-A (Critical — Build Gate) → FIX-B → FIX-C → FIX-D → FIX-E
```

Verify build success after FIX-A.14 before proceeding to FIX-B.

---

## 📊 Updated Build Readiness After Fixes

| Check | Before Fix | After Fix |
|-------|-----------|-----------|
| Gradle sync | ❌ 13 missing modules | ✅ All modules resolved |
| Sprint 1 status | ⚠️ 8/8 marked TODO (wrong) | ✅ 7/8 marked done |
| Phase 1 file paths | ⚠️ Wrong package prefix | ✅ Namespace documented |
| HAL desktop deps | ❌ jSerialComm missing | ✅ In catalog |
| Security deps | ❌ jBCrypt missing | ✅ In catalog |
| log integrity | ⚠️ Undocumented plugin | ✅ Documented |

---

*End of ZENTA-MISMATCH-FIX-v1.0*  
*Created: 2026-02-20*
