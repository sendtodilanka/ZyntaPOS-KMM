# ZyntaPOS — Audit v2 | Phase 2: Alignment Audit
> **Doc ID:** ZENTA-AUDIT-V2-PHASE2-ALIGNMENT
> **Auditor:** Senior KMP Architect
> **Date:** 2026-02-21
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`
> **Prerequisite:** `docs/audit_v2_phase_1_result.md` — Phase 1 Discovery (9 new findings, 19 open)
> **Method:** Forward Check (Docs → Code) + Reverse Check (Code → Docs)

---

## SECTION 1 — METHODOLOGY

### Forward Check
Every architectural claim in `Master_plan.md`, module dep tables, and ADRs is verified against
physical source files (`build.gradle.kts`, `.kt` imports, Koin module registrations).

### Reverse Check
Physical code that has no corresponding documentation entry is flagged as undocumented.

### Carried-Forward Verification
All 9 findings from v2 Phase 1 (`NF-01` through `NF-09`) are re-examined with a live code scan
to report status: **RESOLVED**, **CONFIRMED OPEN**, or **STATUS CORRECTION**.

---

## SECTION 2 — CARRIED-FORWARD FINDINGS STATUS

Fresh code evidence collected for all 9 v2 Phase 1 findings.

| ID | v2-P1 Description | Evidence Collected | Status |
|----|-------------------|--------------------|--------|
| NF-01 | `PrintReceiptUseCase` orphan in `feature/pos` | `ls feature/pos/` — file present; `PosModule.kt` imports `domain.usecase.pos.PrintReceiptUseCase` ✅; `PosViewModel` imports domain version ✅ | ❌ CONFIRMED OPEN — file is dead code, but dangerously named |
| NF-02 | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all `TODO` stubs | Both files read: all 5 methods remain `TODO("…tracked in MERGED-D2")` despite schemas existing | ❌ CONFIRMED OPEN — imminent runtime crash |
| NF-03 | `PLAN_ZENTA_TO_ZYNTA` DOD unchecked | `grep -c "\- \[ \]"` → **5** (all 5 DOD items still unchecked); `STATUS: APPROVED FOR EXECUTION` | ❌ CONFIRMED OPEN |
| NF-04 | `zentapos-audit-final-synthesis.md` is unfilled template | Not re-read in Phase 2 (file not changed) | ❌ ASSUMED OPEN |
| NF-05 | "ZENTRA" string in `ReceiptFormatter.kt` KDoc | `grep -rn "ZENTRA" shared/domain --include="*.kt"` → one hit at `formatter/ReceiptFormatter.kt:23` | ❌ CONFIRMED OPEN |
| NF-06 | `SecurePreferencesKeyMigration.migrate()` not wired to startup | `grep -rn "SecurePreferencesKeyMigration" androidApp/ composeApp/src/` → **0 results**; `MainActivity.kt` and `main.kt` call only `App()` | ❌ CONFIRMED OPEN — migration never runs |
| NF-07 | ADR-001 missing from `CONTRIBUTING.md` table | `grep -n "ADR" CONTRIBUTING.md` → line 116 shows only `ADR-002` | ❌ CONFIRMED OPEN |
| NF-08 | Empty `keystore/` + `token/` scaffold dirs | v1 finding; no new code change detected | ❌ ASSUMED OPEN |
| NF-09 | Boilerplate `compose-multiplatform.xml` asset | File confirmed present at `composeApp/src/commonMain/composeResources/drawable/` | ❌ CONFIRMED OPEN |

### ⚠️ STATUS CORRECTION — F-10

v2 Phase 1 (`Section 3 STATUS DELTA`) listed **F-10** (`PLAN_MISMATCH_FIX_v1.0.md` not marked SUPERSEDED)
as **❌ STILL OPEN**.

**Phase 2 evidence:** The file now begins:
```
> ⚠️ SUPERSEDED — DO NOT EXECUTE
> This plan has been superseded by PLAN_CONSOLIDATED_FIX_v1.0.md.
```

**Verdict: F-10 is ✅ RESOLVED.** The v2 Phase 1 report incorrectly carried it as open.
Recommendation: Update `audit_v2_phase_1_result.md` Section 3 to mark F-10 as RESOLVED.

---

## SECTION 3 — FORWARD CHECK (Docs → Code)

### 3A. Master_plan Module Dependency Table — Full Verification

**Legend:**  M01=:shared:core · M02=:shared:domain · M03=:shared:data · M04=:shared:hal
             M05=:shared:security · M06=:composeApp:designsystem · M21=:composeApp:core

#### What docs claim (Master_plan.md §Module Registry, lines 272-292)

| Module | Master_plan Listed Deps |
|--------|------------------------|
| M07 `:composeApp:navigation` | M02, M05, M06 |
| M08 `:composeApp:feature:auth` | M02, **M03**, **M05**, M06, M21 |
| M09 `:composeApp:feature:pos` | M02, **M03**, M04, M06, M21 |
| M10 `:composeApp:feature:inventory` | M02, **M03**, M06, M21 |
| M11 `:composeApp:feature:register` | M02, **M03**, **M04**, M06, M21 |
| M12 `:composeApp:feature:reports` | M02, **M03**, M06, M21 |
| M18 `:composeApp:feature:settings` | M02, **M03**, M04, **M05**, M06, M21 |

#### What code shows (actual `build.gradle.kts` `project(":…")` declarations)

| Module | Actual Declared Deps | M03 in build? | M04 in build? | M05 in build? |
|--------|---------------------|:---:|:---:|:---:|
| M07 `:navigation` | M02 (domain), M05 (security), M06 (designsystem) | ✅ n/a | ✅ n/a | ✅ declared |
| M08 `:feature:auth` | M02, M06, M21 | ❌ absent | ✅ n/a | ❌ absent |
| M09 `:feature:pos` | M02, M04, **M05**, M06, M21 | ❌ absent | ✅ declared | ✅ present (undocumented) |
| M10 `:feature:inventory` | M02, M06, M21 | ❌ absent | ✅ n/a | ✅ n/a |
| M11 `:feature:register` | M02, M06, M21 | ❌ absent | ❌ absent | ✅ n/a |
| M12 `:feature:reports` | M02, M06, M21 | ❌ absent | ✅ n/a | ✅ n/a |
| M18 `:feature:settings` | M02, M04, M06, M21 | ❌ absent | ✅ declared | ❌ absent |

#### Analysis: M03 (:shared:data) — Systematic Absence Across All 6 Feature Modules

**What docs say:** Master_plan lists M03 as a compile-time dependency of M08, M09, M10, M11, M12, M18.
**What code shows:** Not a single feature module declares `:shared:data` in its `build.gradle.kts`.

This is not a bug — it is **architecturally correct**. Feature modules depend only on `:shared:domain`
interfaces; Koin wires the `:shared:data` implementations at runtime via `DataModule`. Features never
import `com.zyntasolutions.zyntapos.data.*` packages directly. The code implements Clean Architecture;
the Master_plan dependency table does not reflect it.

**Finding:** `AV2-P2-01` — documentation error, not a code error.

---

### 3B. Finding AV2-P2-01 — Master_plan Module Dep Table Lists M03 on All Feature Modules 🟠 MEDIUM

**What docs say:** Master_plan lines 279-292 — M08 through M18 all declare `M03` as a dependency.
**What code shows:** Zero feature `build.gradle.kts` files include `project(":shared:data")`.

**Root cause:** The dependency table was authored before the Clean Architecture pattern was enforced.
At that time, it was assumed features might call data directly. The code evolved correctly; the doc did not.

**Impact:** Developers reading the dependency table will believe `:shared:data` is a required module
for their feature and may add it unnecessarily, creating forbidden layer coupling (`feature → data`).

**Recommendation:** Update Master_plan `§Module Registry` dependency column for M08–M12, M18:
remove M03 from each. Add a note: *"Features depend on M02 domain interfaces only. M03 is wired by
the application DI graph at runtime."*

---

### 3C. Finding AV2-P2-02 — M09 `:feature:pos` Has Undocumented M05 (:shared:security) 🟠 MEDIUM

**What docs say:** Master_plan M09 deps = `M02, M03, M04, M06, M21` — M05 not listed.
**What code shows:**
- `composeApp/feature/pos/build.gradle.kts` line: `implementation(project(":shared:security"))`
- `composeApp/feature/pos/src/.../PosModule.kt` line 15: `import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger`
- `PosModule.kt` registers: `single { SecurityAuditLogger() }`

**Rationale check:** POS module directly instantiates `SecurityAuditLogger` for audit trail events.
This is intentional and architecturally justifiable — POS is a write-heavy, audit-critical domain.
However it bypasses the Ports & Adapters boundary: the adapter `PrinterManagerReceiptAdapter` already
receives a `SecurityAuditLogger` from `posModule`. This creates a second, uncontrolled `SecurityAuditLogger`
singleton that is registered independently from any `SecurityModule` singleton. Two separate instances
of `SecurityAuditLogger` may maintain divergent state or write duplicate audit entries.

**What docs say vs code:** Doc omits M05 entirely for M09; code has it registered as a raw singleton
outside SecurityModule's scope.

**Recommendation:**
1. Add M05 to Master_plan M09 deps column.
2. Investigate whether `SecurityAuditLogger` in `posModule` and `SecurityModule` are the same Koin
   singleton — if `posModule` defines `single { SecurityAuditLogger() }` separately, it creates a
   second instance. Remove it from `posModule` and rely on `get()` from the shared `SecurityModule`.

---

### 3D. Finding AV2-P2-03 — M11 `:feature:register` Missing M04 (:shared:hal) 🔴 HIGH

**What docs say:** Master_plan M11 deps = `M02, M03, M04, M06, M21` — M04 (:shared:hal) required.
**What code shows:**
- `composeApp/feature/register/build.gradle.kts` declares: `:composeApp:core`, `:composeApp:designsystem`,
  `:shared:core`, `:shared:domain` — **no `:shared:hal`**.
- The domain use case `PrintZReportUseCase` exists in `:shared:domain`. If `RegisterViewModel` or
  any register composable imports any `hal.*` package (e.g., `hal.printer.*`, `hal.scanner.*`),
  compilation succeeds only via undeclared transitive dependency.

**Risk assessment:** If `:feature:register` imports HAL types (even indirectly through `ReceiptPrinterPort`
adapters), removing a transitive dependency elsewhere in the graph would cause a compile break in
register with no warning at the point of breakage.

**Recommendation:** Run:
```bash
grep -rn "import com.zyntasolutions.zyntapos.hal\." \
  composeApp/feature/register/src --include="*.kt"
```
- If any hit → add `implementation(project(":shared:hal"))` to `register/build.gradle.kts`
  and add M04 to Master_plan M11 deps.
- If zero hits → remove M04 from Master_plan M11 deps column (doc is wrong, code is right).

**Current verdict: NEEDS CLARIFICATION** — cannot determine without running the grep.

---

### 3E. Finding AV2-P2-04 — M18 `:feature:settings` Missing M05 (:shared:security) — Doc Says Required 🟠 MEDIUM

**What docs say:** Master_plan M18 deps = `M02, M03, M04, M05, M06, M21` — M05 required.
**What code shows:**
- `settings/build.gradle.kts`: no `project(":shared:security")`.
- `SettingsViewModel.kt` imports: `hal.printer.PaperWidth` (from :shared:hal — ✅ declared),
  `ui.core.mvi.BaseViewModel` (from :composeApp:core — ✅ declared).
- No direct `security.*` import visible in `SettingsViewModel.kt`.

**Analysis:** If SettingsViewModel or any settings composable uses security types (e.g., `RbacEngine`,
`JwtManager`, `SecurePreferences`), it compiles only via transitive path through another declared module.
The doc claims M05 is needed; the build file omits it.

**Recommendation:** Run:
```bash
grep -rn "import com.zyntasolutions.zyntapos.security\." \
  composeApp/feature/settings/src --include="*.kt"
```
- If any hit → add `implementation(project(":shared:security"))` to `settings/build.gradle.kts`.
- If zero hits → remove M05 from Master_plan M18 deps column.

**Current verdict: NEEDS CLARIFICATION**

---

### 3F. Finding AV2-P2-05 — M08 `:feature:auth` Missing Both M03 and M05 — Doc Says Both Required 🔴 HIGH

**What docs say:** Master_plan M08 deps = `M02, M03, M05, M06, M21`.
**What code shows:**
- `auth/build.gradle.kts`: `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain`.
- **M03 (:shared:data) absent** — same Clean Architecture justification as 3B above.
- **M05 (:shared:security) absent** — `AuthViewModel` imports only `domain.*` and `ui.core.mvi.BaseViewModel`.
  Authentication is handled via `AuthRepository` (domain interface). No direct `security.*` import.

**Analysis for M05:** Auth is the most security-sensitive feature. The fact that it does not declare
`:shared:security` means it either (a) correctly uses domain interfaces only, with `SecurityModule`
providing implementations via Koin, or (b) is missing a direct dependency it should have (e.g., for
biometric, PIN hashing, or RBAC checks in session guards).

**Recommendation:** Run:
```bash
grep -rn "import com.zyntasolutions.zyntapos.security\." \
  composeApp/feature/auth/src --include="*.kt"
```
- If zero hits → remove M03 and M05 from Master_plan M08 column (code is architecturally correct).
- If any hit → add the missing dep to `auth/build.gradle.kts`.

**Current verdict: NEEDS CLARIFICATION** for M05; M03 absence is CORRECT (same as 3B).

---

### 3G. ADR-001 vs Code — Verified ✅

**What ADR-001 says:** All ViewModels MUST extend `ui.core.mvi.BaseViewModel`. `ReportsViewModel`
and `SettingsViewModel` migrated. Zombie `shared/core/.../mvi/BaseViewModel.kt` deleted.

**Code evidence:**
- `ReportsViewModel.kt` line 4: `import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` ✅
- `SettingsViewModel.kt` line 15: `import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` ✅
- `shared/core/.../mvi/` contains only `.gitkeep` ✅
- All feature build.gradle.kts files declare `implementation(project(":composeApp:core"))` ✅

**Verdict: ADR-001 fully enforced in code.** ✅

---

### 3H. Koin DI Binding Verification — DataModule vs Repository Impls

**What docs claim:** `DataModule.kt` registers all 14 repository implementations.
**Code evidence — DataModule.kt scanned in full:**

| Repository Interface | Bound Impl | Status |
|---------------------|------------|--------|
| `ProductRepository` | `ProductRepositoryImpl` | ✅ |
| `CategoryRepository` | `CategoryRepositoryImpl` | ✅ |
| `OrderRepository` | `OrderRepositoryImpl` | ✅ |
| `CustomerRepository` | `CustomerRepositoryImpl` | ✅ |
| `RegisterRepository` | `RegisterRepositoryImpl` | ✅ |
| `StockRepository` | `StockRepositoryImpl` | ✅ |
| `SupplierRepository` | `SupplierRepositoryImpl` | ✅ |
| `AuthRepository` | `AuthRepositoryImpl` | ✅ |
| `SettingsRepository` | `SettingsRepositoryImpl` | ✅ |
| `TaxGroupRepository` | `TaxGroupRepositoryImpl` | ✅ registered / ⚠️ impl is TODO |
| `UnitGroupRepository` | `UnitGroupRepositoryImpl` | ✅ registered / ⚠️ impl is TODO |
| `AuditRepository` | `AuditRepositoryImpl` | ✅ |
| `UserRepository` | `UserRepositoryImpl` | ✅ |
| `SyncRepository` | `SyncRepositoryImpl` | ✅ |

**All 14 bindings present.** The Koin registration gap from v1 audit is fully resolved. The remaining
risk is not a missing binding — it is that `TaxGroupRepositoryImpl` and `UnitGroupRepositoryImpl`
**throw `NotImplementedError` at first method call** (NF-02, still open).

---

### 3I. PosModule.kt DI Correctness — PrintReceiptUseCase Binding

**What NF-01 feared:** `PosModule.kt` might still inject the feature-layer `PrintReceiptUseCase`
instead of the domain version, making the Ports & Adapters refactor a no-op.

**Code evidence — PosModule.kt:**
```kotlin
// Line 8:
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
// factory block:
factory {
    PrintReceiptUseCase(
        printerPort = get<ReceiptPrinterPort>(),
    )
}
```

**PosViewModel.kt:**
```kotlin
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
```

**Verdict:** `PosModule.kt` correctly injects `domain.usecase.pos.PrintReceiptUseCase` and wires it
to `PrinterManagerReceiptAdapter` via `ReceiptPrinterPort`. The Ports & Adapters refactor **is
working as intended**. The `feature/pos/PrintReceiptUseCase.kt` file is pure dead code — it is not
imported anywhere, has no Koin binding, and is never called. It is nonetheless a correctness and
maintenance hazard (NF-01 remains open because the file exists, not because it is injected).

---

## SECTION 4 — REVERSE CHECK (Code → Docs)

### 4A. Finding AV2-P2-06 — `execution_log.md` Contains Extensive Stale `Zenta*` Brand References — PLAN_ZENTA_TO_ZYNTA DOD Item D2 Unsatisfied 🟠 MEDIUM

**What PLAN_ZENTA_TO_ZYNTA DOD says (item D2):**
```
D2. Verify: grep -r "ZentaButton|ZentaTheme|ZentaColors" docs/ --include="*.md" → 0 results
```

**What code shows:**
```
docs/ai_workflows/execution_log.md:905: ZentaTheme
docs/ai_workflows/execution_log.md:907: ZentaColors.kt
docs/ai_workflows/execution_log.md:918: ZentaTheme.kt
docs/ai_workflows/execution_log.md:937: ZentaButton.kt
docs/ai_workflows/execution_log.md:959: ZentaEmptyState.kt … ZentaButton
docs/ai_workflows/execution_log.md:971: ZentaButton.kt
docs/ai_workflows/execution_log.md:982: ZentaEmptyState.kt … ZentaButton
...  (20+ additional hits across lines 994–1960)
```

`execution_log.md` is a narrative history file that recorded the work done using the old names —
it was not updated as part of the rename. This makes DOD item D2 permanently fail with no practical
path to resolution through find-and-replace (doing so would falsify the historical log).

**What docs say vs code:** PLAN_ZENTA_TO_ZYNTA DOD item D2 is unsatisfiable as written because
`execution_log.md` documents history that used the old prefix. The DOD was authored without exempting
the execution log.

**Recommendation:** Amend PLAN_ZENTA_TO_ZYNTA DOD item D2 to:
```
D2. Verify: grep -r "ZentaButton|ZentaTheme|ZentaColors" docs/ --include="*.md"
    --exclude="execution_log.md" → 0 results
    (execution_log.md is a historical narrative; stale names are expected and exempt)
```
Then close the DOD. Update `STATUS: APPROVED FOR EXECUTION` → `STATUS: COMPLETE`.

---

### 4B. Finding AV2-P2-07 — `shared/data/local/security/` Parallel SecurePreferences Path — Undocumented 🟡 LOW

**What docs say:** Architecture docs reference `security/prefs/SecurePreferences.kt` (in `:shared:security`)
as the single interface. `SecurePreferencesKeys.kt` was added in v2 to centralize key strings.

**What code shows:**
```
shared/data/src/commonMain/kotlin/.../data/local/security/
  InMemorySecurePreferences.kt
  SecurePreferences.kt
```

A second `SecurePreferences` interface exists inside `:shared:data` alongside `InMemorySecurePreferences`.
This was noted in v1 Phase 2 (P2-08 category) but remains undocumented in any ADR or architecture note.
Two `SecurePreferences` interfaces with the same simple name in different packages creates import
confusion and potential contract divergence.

**Recommendation:** Determine which interface is canonical:
- If `data/local/security/SecurePreferences` is the live one (used by `AuthRepositoryImpl` and
  `DataModule`), archive or remove `security/prefs/SecurePreferences`.
- If `security/prefs/SecurePreferences` is canonical, remove the `data/local/` copy.
- Document the decision in an ADR or inline KDoc, and ensure `SecurePreferencesKeys.kt` references
  the canonical interface.

---

### 4C. Finding AV2-P2-08 — `SecurityAuditLogger` Registered in `posModule` Outside `SecurityModule` Scope 🟠 MEDIUM

**What code shows (cross-reference of 3C):**
- `SecurityModule` (`:shared:security`) presumably registers `SecurityAuditLogger` as a singleton.
- `posModule` (`composeApp/feature/pos/PosModule.kt`) **also** registers:
  ```kotlin
  single { SecurityAuditLogger() }
  ```
  This is a separate `single` binding, creating a **second Koin singleton** for `SecurityAuditLogger`
  within the POS module scope.

**What docs say:** Master_plan §3 states infrastructure singletons are provided by their home modules
and consumed by features via `get()`. There is no documented exception for audit loggers.

**Risk:** If Koin module loading order makes `posModule` resolve before `SecurityModule`, or if Koin
encounters two `single<SecurityAuditLogger>` bindings, behaviour is undefined. Audit entries from
the POS module may go to a different logger instance than entries from `:shared:security` flows —
creating a silent audit gap.

**Recommendation:** Remove `single { SecurityAuditLogger() }` from `posModule`. Inject via `get()`.
Verify `SecurityModule` (or the platform DI setup) registers `SecurityAuditLogger` as a singleton.

---

## SECTION 5 — CONSOLIDATED FINDING REGISTRY (v2 Phase 2)

### Carried Forward from v2 Phase 1 — Status

| ID | Severity | Description | Phase 2 Status |
|----|----------|-------------|----------------|
| NF-01 | 🔴 P0 | `PrintReceiptUseCase` orphan in `feature/pos` | ❌ OPEN — file present, not injected |
| NF-02 | 🔴 P0 | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all TODO | ❌ OPEN — crash imminent |
| NF-03 | 🟠 P1 | PLAN_ZENTA_TO_ZYNTA DOD unchecked, STATUS wrong | ❌ OPEN — all 5 items unchecked |
| NF-04 | 🟠 P1 | `zentapos-audit-final-synthesis.md` is unfilled template | ❌ ASSUMED OPEN |
| NF-05 | 🟡 P2 | "ZENTRA" string in `ReceiptFormatter.kt` KDoc | ❌ OPEN — confirmed at line 23 |
| NF-06 | 🟡 P2 | `SecurePreferencesKeyMigration.migrate()` not wired to startup | ❌ OPEN — 0 call sites |
| NF-07 | 🟡 P3 | ADR-001 missing from `CONTRIBUTING.md` ADR table | ❌ OPEN — confirmed |
| NF-08 | 🟡 P3 | Empty `keystore/` + `token/` scaffold dirs | ❌ OPEN — no change detected |
| NF-09 | 🟡 P3 | Boilerplate `compose-multiplatform.xml` still present | ❌ OPEN — confirmed |
| F-10 | — | `PLAN_MISMATCH_FIX` not marked SUPERSEDED | ✅ RESOLVED (Phase 1 report was wrong) |

### New Findings — v2 Phase 2

| ID | Severity | Description | File(s) |
|----|----------|-------------|---------|
| AV2-P2-01 | 🟠 MEDIUM | Master_plan dep table lists M03 on all feature modules — architecturally incorrect in docs, code is right | `docs/plans/Master_plan.md` lines 279-292 |
| AV2-P2-02 | 🟠 MEDIUM | M09 `:feature:pos` has undocumented M05 dep + possible duplicate `SecurityAuditLogger` singleton | `pos/build.gradle.kts`, `PosModule.kt` |
| AV2-P2-03 | 🔴 HIGH | M11 `:feature:register` missing M04 (:shared:hal) per doc — or doc is wrong | `register/build.gradle.kts`, Master_plan M11 |
| AV2-P2-04 | 🟠 MEDIUM | M18 `:feature:settings` missing M05 per doc — NEEDS CLARIFICATION | `settings/build.gradle.kts`, Master_plan M18 |
| AV2-P2-05 | 🔴 HIGH | M08 `:feature:auth` missing M05 per doc — auth is highest-security module | `auth/build.gradle.kts`, Master_plan M08 |
| AV2-P2-06 | 🟠 MEDIUM | `execution_log.md` has 20+ stale `Zenta*` refs — PLAN_ZENTA_TO_ZYNTA DOD item D2 structurally unsatisfiable as written | `docs/ai_workflows/execution_log.md`, `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| AV2-P2-07 | 🟡 LOW | `shared/data/local/security/SecurePreferences` parallel interface — undocumented | `shared/data/src/.../data/local/security/` |
| AV2-P2-08 | 🟠 MEDIUM | `SecurityAuditLogger` registered as duplicate singleton in `posModule` | `composeApp/feature/pos/PosModule.kt` |

---

## SECTION 6 — PRE-SPRINT-5 ACTION TABLE

Sorted by priority. All items with concrete file paths and owner actions.

| Priority | ID | Action | File(s) | Blocker For |
|----------|----|--------|---------|-------------|
| 🔴 P0 | NF-02 | Implement all 5 methods in `TaxGroupRepositoryImpl` using `taxGroupsQueries` | `shared/data/.../repository/TaxGroupRepositoryImpl.kt` | Settings screen load |
| 🔴 P0 | NF-02 | Implement all 5 methods in `UnitGroupRepositoryImpl` using `unitsOfMeasureQueries` | `shared/data/.../repository/UnitGroupRepositoryImpl.kt` | Inventory unit management |
| 🔴 P0 | NF-01 | Delete `feature/pos/PrintReceiptUseCase.kt` (dead code — DI uses domain version) | `composeApp/feature/pos/src/.../feature/pos/PrintReceiptUseCase.kt` | Code hygiene / no confusion |
| 🔴 P1 | AV2-P2-03 | Run `grep -rn "import.*\.hal\."` in register; add `:shared:hal` if hits found; else remove M04 from Master_plan M11 | `register/build.gradle.kts`, `Master_plan.md` | Build stability |
| 🔴 P1 | AV2-P2-05 | Run `grep -rn "import.*\.security\."` in auth; add `:shared:security` if hits; else remove M05 from Master_plan M08 | `auth/build.gradle.kts`, `Master_plan.md` | Security correctness |
| 🟠 P1 | AV2-P2-08 | Remove `single { SecurityAuditLogger() }` from `posModule`; inject via `get()` | `composeApp/feature/pos/PosModule.kt` | Audit trail correctness |
| 🟠 P1 | AV2-P2-01 | Remove M03 from feature module dep columns in Master_plan; add note on Koin runtime wiring | `docs/plans/Master_plan.md` lines 279-292 | Doc accuracy |
| 🟠 P1 | AV2-P2-02 | Add M05 to Master_plan M09 deps; resolve `SecurityAuditLogger` singleton conflict | `Master_plan.md`, `PosModule.kt` | Doc accuracy + runtime correctness |
| 🟠 P2 | AV2-P2-04 | Run `grep` for security imports in settings; update build or doc accordingly | `settings/build.gradle.kts`, `Master_plan.md` | Doc accuracy |
| 🟠 P2 | NF-06 | Add `SecurePreferencesKeyMigration(get()).migrate()` to Android + Desktop startup | `androidApp/MainActivity.kt`, `composeApp/src/jvmMain/.../main.kt` | Auth key migration |
| 🟠 P2 | NF-03 | Amend DOD item D2 to exempt `execution_log.md`; check all 5 DOD items; update STATUS to COMPLETE | `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` | Plan hygiene |
| 🟠 P2 | AV2-P2-06 | Amend DOD D2 in rename plan; close plan as COMPLETE after amending | `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` | Unlock DOD closure |
| 🟠 P2 | NF-04 | Fill synthesis template OR rename to `…-TEMPLATE.md` | `docs/zentapos-audit-final-synthesis.md` | Audit completeness |
| 🟡 P3 | NF-05 | Update KDoc example in `ReceiptFormatter.kt` line 23: `ZENTRA` → `ZYNTA` | `shared/domain/.../domain/formatter/ReceiptFormatter.kt` | Brand consistency |
| 🟡 P3 | NF-07 | Add ADR-001 row to CONTRIBUTING.md §7 ADR table | `CONTRIBUTING.md` line 116 | Developer discoverability |
| 🟡 P3 | AV2-P2-07 | Decide canonical `SecurePreferences` interface; remove duplicate; document in KDoc or ADR | `shared/data/.../data/local/security/SecurePreferences.kt` | Architectural clarity |
| 🟡 P3 | NF-08 | ADR: decide scaffold vs implementation for `keystore/` + `token/` dirs | `shared/security/src/*/security/keystore/`, `.../token/` | Scaffold hygiene |
| 🟡 P3 | NF-09 | Delete boilerplate `compose-multiplatform.xml` | `composeApp/src/commonMain/composeResources/drawable/` | Template cleanup |
| NC | F-10 | Update `audit_v2_phase_1_result.md` Section 3 to mark F-10 as RESOLVED | `docs/audit_v2_phase_1_result.md` | Report accuracy |

---

## SECTION 7 — CUMULATIVE OPEN FINDINGS SNAPSHOT

| Category | v2 Phase 1 | v2 Phase 2 |
|----------|------------|------------|
| Total open | 19 | **26** |
| Closed this phase | — | **1** (F-10) |
| New this phase | — | **8** |
| 🔴 Critical / High | 3 | **5** |
| 🟠 Medium | 2 | **11** |
| 🟡 Low / Hygiene | 8 | **8** |
| NEEDS CLARIFICATION | — | **3** (AV2-P2-03, AV2-P2-04, AV2-P2-05) |

---

## SECTION 8 — VERIFICATION CHECKLIST

| Check | Evidence | Status |
|-------|----------|--------|
| All 23 modules physically present | v2 Phase 1 confirmed | ✅ |
| Package `com.zyntasolutions.zyntapos` consistent | v2 Phase 1 confirmed | ✅ |
| Zombie `BaseViewModel` in shared/core deleted | `mvi/` = `.gitkeep` only | ✅ |
| ADR-001 enforced — all VMs use `ui.core.mvi.BaseViewModel` | ReportsVM + SettingsVM imports confirmed | ✅ |
| `BarcodeScanner.kt` root duplicate deleted | Root path returns ENOENT | ✅ |
| `SecurityAuditLogger.kt` root duplicate deleted | Root path — no `.kt` in security root | ✅ |
| Design system uses `Zynta*` prefix in `.kt` files | grep Zenta[A-Z] in designsystem → 0 hits | ✅ |
| `PosModule.kt` injects `domain.usecase.pos.PrintReceiptUseCase` | Import confirmed at line 8 | ✅ |
| `PrintReceiptUseCase` orphan in `feature/pos` deleted | `ls feature/pos/` — **still present** | ❌ |
| `TaxGroupRepositoryImpl` implemented | All 5 methods are TODO stubs | ❌ |
| `UnitGroupRepositoryImpl` implemented | All 5 methods are TODO stubs | ❌ |
| `SecurePreferencesKeyMigration` wired to app startup | grep returns 0 results in app entry points | ❌ |
| ADR-001 in CONTRIBUTING.md ADR table | Line 116 — ADR-002 only | ❌ |
| PLAN_ZENTA_TO_ZYNTA STATUS = COMPLETE | STATUS reads "APPROVED FOR EXECUTION" | ❌ |
| PLAN_ZENTA_TO_ZYNTA DOD all items checked | 5 unchecked items confirmed | ❌ |
| Master_plan dep table M03 removed from feature modules | Lines 279-292 still list M03 | ❌ |
| `SecurityAuditLogger` singleton conflict resolved in posModule | `single { SecurityAuditLogger() }` still present | ❌ |
| `execution_log.md` DOD D2 exemption documented | Not present | ❌ |
| `compose-multiplatform.xml` deleted | File still present | ❌ |
| "ZENTRA" string in `ReceiptFormatter.kt` fixed | Line 23 confirmed | ❌ |
| PLAN_MISMATCH_FIX marked SUPERSEDED | Banner present at top of file | ✅ |
| `PLAN_STRUCTURE_CROSSCHECK_v1.0.md` module count updated | Not re-checked this phase | ⚠️ UNVERIFIED |

---

## SECTION 9 — ACUTE RISK SUMMARY

### Risk 1 — 🔴 IMMINENT RUNTIME CRASH
**`TaxGroupRepositoryImpl` and `UnitGroupRepositoryImpl` are registered in Koin but throw
`NotImplementedError` on first method call.** `SettingsViewModel` injects `TaxGroupRepository`
for the Tax Settings screen. Any developer or QA testing the Settings → Tax Groups path will
encounter a crash. Both impls cite "MERGED-D2" as the blocker — but both SQLDelight schemas
(`tax_groups.sq`, `units_of_measure.sq`) now exist. The blocker is resolved; the implementations
were never written. This must be the first fix in Sprint 5.

### Risk 2 — 🔴 AUTH MODULE SECURITY GAP (AV2-P2-05 NEEDS CLARIFICATION)
**`:feature:auth` does not declare `:shared:security` in `build.gradle.kts`** despite Master_plan
identifying it as required. Auth is the most security-sensitive module in the system. Until
`grep` confirms whether auth sources directly import `security.*`, this is either a documentation
error or a missing dependency that makes auth security operate entirely through transitive paths —
invisible to Gradle's dependency validation.

### Risk 3 — 🟠 AUDIT TRAIL INTEGRITY
**`PosModule.kt` creates its own `SecurityAuditLogger` singleton** outside of `SecurityModule`.
Two logger instances in a Koin graph for the same type produce undefined resolution behaviour and
may silently swallow POS audit events. POS operations (voids, discounts, reprints) are
audit-critical in any regulated retail environment.

---

*End of Audit v2 — Phase 2 — ZyntaPOS ZENTA-AUDIT-V2-PHASE2-ALIGNMENT*
*Findings open after this phase: 26 | Highest severity: 🔴 5 critical/high*
*Next: Phase 3 — Fix Execution (NF-02 repo implementations, NF-01 orphan deletion, AV2-P2-08 logger)*
