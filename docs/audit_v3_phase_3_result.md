# ZyntaPOS — Phase 3 Audit Report: Doc Consistency + Duplication
> **Doc ID:** ZENTA-PHASE3-CONSISTENCY-v2.0  
> **Auditor:** Senior KMP Architect  
> **Date:** 2026-02-22  
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`  
> **Prerequisites:** Phase 1 Discovery (ZENTA-PHASE1-DISCOVERY-v1.0) · Phase 2 Alignment (ZENTA-PHASE2-ALIGNMENT-v1.0)  
> **Supersedes:** ZENTA-PHASE3-CONSISTENCY-v1.0 (draft, 2026-02-21)

---

## LEGEND

| Symbol | Meaning |
|--------|---------|
| ⚠️ | Doc-to-doc conflict or doc-to-code mismatch |
| 📄 | File-level duplication (identical or near-identical) |
| 🔁 | Logic-level duplication (same business logic in multiple places) |
| 🏗️ | Architectural boundary violation (code in wrong layer) |
| ✅ | Verified clean — no issue |
| 🔒 | CLOSED — finding resolved since prior phase |

---

## PHASE 2 FINDING CLOSURE STATUS

Before reporting Phase 3 findings, all Phase 2 open items were verified against the
live codebase. The following were confirmed **CLOSED**:

| Phase 2 ID | Finding | Status |
|------------|---------|--------|
| **P2-01** | `PosViewModel`, `InventoryViewModel`, `RegisterViewModel` had undeclared `:composeApp:core` transitive dep | 🔒 **CLOSED** — all three `build.gradle.kts` files now declare `implementation(project(":composeApp:core"))` |
| **P2-02** | `ReportsViewModel` + `SettingsViewModel` extended raw `ViewModel` instead of canonical `BaseViewModel` | 🔒 **CLOSED** — both now extend `BaseViewModel<…>` from `ui.core.mvi` using `handleIntent`/`dispatch`/`updateState`/`sendEffect` |
| **P2-03** | `:shared:hal` undeclared in `settings/build.gradle.kts` | 🔒 **CLOSED** — `implementation(project(":shared:hal"))` present with explanatory comment about `PrintTestPageUseCaseImpl` |
| **P2-04** | `SettingsViewModel` injected `TaxGroupRepository` + `UserRepository` with no Koin bindings (runtime crash risk) | 🔒 **CLOSED** — all 14 repository impls created and bound in `DataModule.kt` |
| **P2-05** | 4 missing repository impls: `AuditRepositoryImpl`, `UserRepositoryImpl`, `TaxGroupRepositoryImpl`, `UnitGroupRepositoryImpl` | 🔒 **CLOSED** — all four files present in `shared/data/src/commonMain/.../data/repository/` |
| **P2-06** | No SQLDelight schemas for `TaxGroup`/`UnitOfMeasure` | 🔒 **CLOSED** — SQLDelight generated `Tax_groups.kt`, `Tax_groupsQueries.kt`, `Units_of_measure.kt`, `Units_of_measureQueries.kt` confirmed in build output |
| **P2-07** | `:composeApp:navigation` dependency docs understated | 🟡 Still open — `Master_plan.md` not updated |
| **P2-08** | `:composeApp:core` absent from Master_plan §4.1 registry | 🟡 Still open — not added to docs |
| **P2-09** | `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` boilerplate left in repo | 🔒 **CLOSED** — directory `drawable/` is empty; file was deleted |
| **P2-10** | `shared/data/local/security/` stubs undocumented and in wrong module | 🔒 **CLOSED** — stubs deleted from `:shared:data`; replaced by domain port interfaces and proper adapter in `:shared:security` (detailed in DC-02/DC-03 below) |
| **F-01** | Zombie `shared/core/.../core/mvi/BaseViewModel.kt` with orphan `UiState`/`UiIntent`/`UiEffect` | 🔒 **CLOSED** — file no longer exists in `shared/core/src/commonMain/` |
| **F-02** | `PrintReceiptUseCase` placed in feature layer | 🔒 **CLOSED** — `PrintReceiptUseCase.kt` now in `shared/domain/src/commonMain/.../domain/usecase/pos/` |
| **F-03** | Root-level `shared/hal/.../hal/BarcodeScanner.kt` duplicating `scanner/BarcodeScanner.kt` | 🔒 **CLOSED** — only `hal/scanner/BarcodeScanner.kt` exists; root-level file deleted |
| **F-04** | Root-level `shared/security/.../security/SecurityAuditLogger.kt` duplicating `audit/SecurityAuditLogger.kt` | 🔒 **CLOSED** — only `security/audit/SecurityAuditLogger.kt` exists |
| **F-09** | `settings.gradle.kts` header comment "22 modules" stale | NEEDS VERIFICATION — not confirmed in this scan |

**Findings closed this phase: 13 of 21 outstanding (including all 🔴 HIGH severity items from Phase 2)**

---

## 3A — DOC INTERNAL CONSISTENCY

### DC-01 — Project Name: "ZentaPOS" vs "ZyntaPOS" 🟠 MEDIUM (still open)

⚠️ **`UI_UX_Main_Plan.md`** title: `"ZentaPOS — Enterprise UI/UX Master Blueprint"`, Doc ID: `ZENTA-UI-UX-PLAN-v1.0`  
⚠️ **`ER_diagram.md`** title: `"ZentaPOS — Enterprise ER Diagram Plan"`, Doc ID: `ZENTA-ER-DIAGRAM-v1.0`  
✅ **`Master_plan.md`** title: `"ZyntaPOS — Enterprise Master Blueprint"` — correct

| Document | Product Name | Status |
|----------|-------------|--------|
| `UI_UX_Main_Plan.md` | **ZentaPOS** | ⚠️ Wrong |
| `ER_diagram.md` | **ZentaPOS** | ⚠️ Wrong |
| `Master_plan.md` | **ZyntaPOS** | ✅ Correct |
| All source code, packages, folders | **ZyntaPOS** / `zyntasolutions` | ✅ Correct |

> **Recommendation:** Canonical spelling is `ZyntaPOS` (all code, `Master_plan.md`, package names). Search-replace `ZentaPOS` → `ZyntaPOS` in `UI_UX_Main_Plan.md` and `ER_diagram.md`. Optionally harmonise Document IDs from `ZENTA-` prefix to `ZYNTA-` across all three docs for consistency.

---

### DC-02 — `BaseViewModel` API: Master Plan Code Sample Matches Deleted Zombie 🟠 MEDIUM (still open)

⚠️ **`Master_plan.md §3.3`** shows an MVI code sample using `fun onIntent(intent: I)`, `setState { }`, `AutoCloseable`, and `SharedFlow` effects.  
✅ **`composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt`** (the live canonical) uses `fun dispatch(intent: I)`, `abstract suspend fun handleIntent(intent: I)`, `updateState { }`, and `Channel<E>` for effects.

**What the doc says vs what code shows:**

| Aspect | `Master_plan.md §3.3` sample | Live `BaseViewModel` in `composeApp/core` |
|--------|-------------------------------|-------------------------------------------|
| Base class | Implicitly `AutoCloseable` | `androidx.lifecycle.ViewModel` |
| Intent entry point | `fun onIntent(intent: I)` | `fun dispatch(intent: I)` |
| Handler signature | `abstract fun onIntent(intent: I)` | `abstract suspend fun handleIntent(intent: I)` |
| Effect delivery | `MutableSharedFlow` | `Channel<E>` (buffered, capacity=64) |
| State mutation | `setState { }` | `updateState { }` |

The zombie `shared/core` BaseViewModel (which matched the doc) was **deleted**. The doc now points developers to non-existent API patterns.

> **Recommendation:** Update `Master_plan.md §3.3` MVI code sample to reflect the live `composeApp/core` BaseViewModel: replace `onIntent` → `dispatch`, `setState` → `updateState`, add `suspend` modifier to handler, replace `SharedFlow` with `Channel` pattern. All six active ViewModels (`AuthViewModel`, `PosViewModel`, `InventoryViewModel`, `RegisterViewModel`, `ReportsViewModel`, `SettingsViewModel`) now use the correct API — the doc is the only laggard.

---

### DC-03 — Tech Stack Versions: Master Plan Stale vs Actual Catalog 🟡 LOW (still open)

⚠️ **`Master_plan.md §15.1`** documents library versions with `+` suffix notation (e.g. `Kotlin: 2.1+`).  
✅ **`gradle/libs.versions.toml`** has exact pinned versions.

| Library | Master Plan Says | Actual (`libs.versions.toml`) | Delta |
|---------|-----------------|-------------------------------|-------|
| Kotlin | `2.1+` | `2.3.0` | +0.2 minor ⚠️ |
| Compose Multiplatform | `1.7+` | `1.10.0` | +0.3 minor ⚠️ |
| Coroutines | `1.9+` | `1.10.2` | +0.1 minor ⚠️ |
| Serialization | `1.7+` | `1.8.0` | +0.1 minor ⚠️ |
| Koin | `4.0+` | `4.0.4` | patch ✅ |
| Ktor | `3.0+` | `3.0.3` | patch ✅ |
| SQLDelight | `2.0+` | `2.0.2` | patch ✅ |
| AGP | `8.5+` | `8.13.2` | +0.8 minor ⚠️ |

> **Recommendation:** Update `Master_plan.md §15.1` with exact versions from `libs.versions.toml`. Remove `+` notation — exact pins are required in architecture docs for reproducibility auditing. Run `./gradlew dependencyUpdates` and update both `libs.versions.toml` and `Master_plan.md` together as part of each sprint's technical debt retro.

---

### DC-04 — `:composeApp:feature:media` in Registry but Missing from Source Tree 🟡 LOW (still open)

⚠️ **`Master_plan.md §3.2`** source set tree shows 12 `:composeApp:feature:*` modules but omits `:composeApp:feature:media`.  
✅ **`Master_plan.md §4.1`** lists it as M20 (`MediaModule`).  
✅ **`settings.gradle.kts`** includes it.  
✅ **Code** — `composeApp/feature/media/src/commonMain/.../feature/media/MediaModule.kt` exists.

Two sections within `Master_plan.md` contradict each other about this module's existence.

> **Recommendation:** Add `:composeApp:feature:media` to the §3.2 source tree diagram. Also add `:composeApp:core` (the canonical BaseViewModel home) which is likewise missing from §3.2. Both were present in code before this phase began.

---

### DC-05 — Design System Component Naming Divergence 🟡 LOW (still open)

⚠️ **`UI_UX_Main_Plan.md §3.3`** references `ZentaLoadingSkeleton`.  
✅ **Code:** `composeApp/designsystem/src/commonMain/.../components/ZyntaLoadingOverlay.kt` — name differs in both brand (`Zenta` → `Zynta`) and concept (`Skeleton` vs `Overlay`).

Confirmed existing design system components vs. doc spec:

| UI_UX_Main_Plan.md §3.3 Component | Actual File in `designsystem/components/` | Status |
|------------------------------------|-------------------------------------------|--------|
| `ZentaLoadingSkeleton` | `ZyntaLoadingOverlay.kt` | ⚠️ Name mismatch (brand + concept) |
| `ZentaSearchBar` | `ZyntaSearchBar.kt` | ⚠️ Brand prefix only |
| `ZentaButton` | `ZyntaButton.kt` | ⚠️ Brand prefix only |
| `ZentaTextField` | `ZyntaTextField.kt` | ⚠️ Brand prefix only |
| `ZentaProductCard` | `ZyntaProductCard.kt` | ⚠️ Brand prefix only |
| `ZentaBadge` | `ZyntaBadge.kt` | ⚠️ Brand prefix only |
| `ZentaDialog` | `ZyntaDialog.kt` | ⚠️ Brand prefix only |
| `ZentaTable` | `ZyntaTable.kt` | ⚠️ Brand prefix only |
| `ZentaNumericPad` | `ZyntaNumericPad.kt` | ⚠️ Brand prefix only |
| `ZentaTopAppBar` | `ZyntaTopAppBar.kt` | ⚠️ Brand prefix only |
| `ZentaSyncIndicator` | `ZyntaSyncIndicator.kt` | ⚠️ Brand prefix only |
| `ZentaSnackbarHost` | `ZyntaSnackbarHost.kt` | ⚠️ Brand prefix only |
| `ZentaBottomSheet` | `ZyntaBottomSheet.kt` | ⚠️ Brand prefix only |
| `ZentaCartItemRow` | `ZyntaCartItemRow.kt` | ⚠️ Brand prefix only |
| `ZentaEmptyState` | `ZyntaEmptyState.kt` | ⚠️ Brand prefix only |

All 15 component names in the doc use the wrong brand prefix. The `LoadingSkeleton` vs `LoadingOverlay` is also a semantic difference beyond mere naming.

> **Recommendation:** This is a bulk consequence of DC-01. Perform the DC-01 brand rename in `UI_UX_Main_Plan.md` globally. Additionally rename `ZentaLoadingSkeleton` → `ZyntaLoadingOverlay` explicitly to capture the semantic change. If skeleton/shimmer loading (animated placeholder content) is a distinct planned component from `ZyntaLoadingOverlay` (which is an opaque blocking overlay), document both separately.

---

## 3B — CODE DUPLICATION SCAN

### DUP-01 — `PosSearchBar.kt` vs `ZyntaSearchBar.kt` ✅ RESOLVED

📄 `composeApp/feature/pos/src/commonMain/.../pos/PosSearchBar.kt` ↔ `composeApp/designsystem/src/commonMain/.../components/ZyntaSearchBar.kt`

**Code confirms:** `PosSearchBar.kt` is a clean stateless thin wrapper (57 lines). It delegates ALL rendering to `ZyntaSearchBar`, forwarding: `query`, `onQueryChange`, `onClear`, `onScanToggle`, `isScanActive`, `focusRequester`, and `modifier`. It adds only POS-specific defaults (padding via `ZyntaSpacing.md/sm`) and a `FocusRequester` default. No UI logic is duplicated.

> ✅ **CLOSED** — `PosSearchBar` is a correctly structured composition wrapper. No remediation required.

---

### DUP-02 — `ProductValidator` in Domain vs Deleted Feature-Layer `ProductFormValidator` ✅ RESOLVED

📄 Previously: `composeApp/feature/inventory/.../ProductFormValidator.kt` (136 lines, presentation layer)  
✅ Now: `shared/domain/src/commonMain/.../domain/validation/ProductValidator.kt` + `ProductValidationParams.kt`

**Code confirms:** `ProductFormValidator.kt` has been **deleted** from the inventory feature module. Validation logic lives exclusively in the domain layer alongside `PaymentValidator`, `StockValidator`, `TaxValidator`.

> ✅ **CLOSED** — Domain validation is the single source of truth.

---

### DUP-03 — `PasswordHasher` / `SecurePreferences` Parallel Implementations ✅ RESOLVED (MERGED-F3)

📄 Previously (Phase 2):  
- `shared/data/src/commonMain/.../data/local/security/PasswordHasher.kt` (stub `interface`)  
- `shared/security/src/commonMain/.../security/auth/PasswordHasher.kt` (`expect object`, incompatible API)  
- `shared/data/.../local/security/SecurePreferences.kt` (stub with divergent key constants)  
- `shared/data/.../local/security/InMemorySecurePreferences.kt` + `PlaceholderPasswordHasher.kt`

**Code confirms MERGED-F3 fix:** All five stub files are **gone** from `shared/data/src/commonMain/`. The codebase now uses a clean hexagonal port/adapter pattern:

```
:shared:domain
  └── domain/port/PasswordHashPort.kt        — interface hash()/verify()
  └── domain/port/SecureStoragePort.kt       — interface put/get/remove/clear/contains
  └── domain/port/SecureStorageKeys.kt       — canonical key constants (single source of truth)

:shared:security
  └── auth/PasswordHasherAdapter.kt          — implements PasswordHashPort, delegates to expect object
  └── prefs/SecurePreferencesKeys.kt         — delegates every constant to SecureStorageKeys
  └── prefs/SecurePreferences.kt (expect)    — implements SecureStoragePort on all platforms

:shared:data
  └── local/db/SecurePreferencesKeyMigration.kt — one-time migration from legacy bare keys
  └── DataModule.kt                          — consumes SecureStoragePort + PasswordHashPort (from securityModule)
```

**Key migration:** `SecurePreferencesKeyMigration` handles the one-time rewrite of bare keys (`"access_token"`) to canonical dotted keys (`"auth.access_token"`). It depends only on `SecureStoragePort` (domain interface) — zero `:shared:security` imports.

> ✅ **CLOSED** — DC-02 and DC-03 from Phase 3-v1 are fully resolved. The data layer has zero compile-time dependency on `:shared:security`.

---

### DUP-04 — `FakeRepositories` Test File Fragmentation ✅ RESOLVED

📄 Previously: `FakeRepositories.kt`, `FakeRepositoriesPart2.kt`, `FakeRepositoriesPart3.kt`  
✅ Now: `FakeAuthRepositories.kt`, `FakeInventoryRepositories.kt`, `FakePosRepositories.kt`, `FakeSharedRepositories.kt`

> ✅ **CLOSED** — Test fakes organised by domain concern; fully discoverable.

---

### DUP-05 — Hardcoded Gradle Versions ✅ CLEAN

Previously `androidx-work-runtime` used a literal version string. **Code confirms** fix:
```toml
[versions]
androidx-work = "2.10.1"

[libraries]
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "androidx-work" }
```

> ✅ **CLOSED** — All 23 module `build.gradle.kts` files use `alias(libs.*)`. `libs.versions.toml` is fully normalised.

---

## NEW FINDINGS (Phase 3 Code Scan)

### NEW-01 — `PrinterManagerReceiptAdapter` Directly Imports Infrastructure `SecurityAuditLogger` 🟠 MEDIUM

🏗️ **File:** `composeApp/feature/pos/src/commonMain/.../pos/printer/PrinterManagerReceiptAdapter.kt`

```kotlin
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
```

**Problem:** `PrinterManagerReceiptAdapter` implements the domain port `ReceiptPrinterPort` and lives in `:composeApp:feature:pos`. Its KDoc attempts to justify this import: *"Lives in `:composeApp:feature:pos` — the only module that can safely import from both `:shared:hal` and `:shared:security` without creating circular dependencies."*

This reasoning is incorrect:

1. The only usage is `auditLogger.logReceiptPrint(orderId, userId)` — a domain-level audit event, not a security infrastructure concern.
2. `AuditRepository` (domain interface, already bound in `DataModule.kt`) achieves the same result via `AuditRepository.log(AuditEntry(...))` with no cross-boundary import.
3. `SecurityAuditLogger` internally wraps `AuditRepository` — this creates a redundant diamond: `pos → security → domain → AuditRepository` AND `pos → domain → AuditRepository`.
4. The `:shared:security` entry in `pos/build.gradle.kts` exists solely for this one import.

**What the doc says vs what code shows:**

| Layer | Correct dependency | Actual dependency |
|-------|-------------------|-------------------|
| `:composeApp:feature:pos` | Domain ports only | Infrastructure `SecurityAuditLogger` from `:shared:security` |

> **Recommendation:** Replace `SecurityAuditLogger` injection with `AuditRepository`. Change step 5 in `print()`:
> ```kotlin
> runCatching {
>     auditRepository.log(AuditEntry(
>         action = "RECEIPT_PRINT", entityId = order.id, actorId = cashierId, ...
>     ))
> }
> ```
> Remove `implementation(project(":shared:security"))` from `pos/build.gradle.kts`. This eliminates the last cross-boundary infrastructure import in the feature layer and is fully consistent with MERGED-F3's design.

---

### NEW-02 — `DataModule.kt` KDoc Misattributes `PasswordHashPort` Binding Location 🟡 LOW

⚠️ `shared/data/src/commonMain/.../data/di/DataModule.kt` — module KDoc states:
```
Note: `PasswordHashPort` is bound HERE as `single<PasswordHashPort> { PasswordHasherAdapter() }`.
```

**What the code shows:** This binding is **not** in `DataModule.kt`. It is in `SecurityModule.kt`:
```kotlin
// shared/security/src/.../security/di/SecurityModule.kt
single<PasswordHashPort> { PasswordHasherAdapter() }
```

`DataModule.kt` consumes `PasswordHashPort` via `get()` but does not provide it. A developer following the DataModule KDoc to locate the `PasswordHashPort` binding will search the wrong file.

> **Recommendation:** Update the `DataModule.kt` KDoc to: *"`PasswordHashPort` is PROVIDED by `SecurityModule` (`PasswordHasherAdapter`) and consumed here via `get()`. See `shared/security/.../di/SecurityModule.kt` for the binding."*

---

### NEW-03 — `SecurityModule` Provides Bare `PasswordHasher` Binding Alongside Port Binding 🟡 LOW

⚠️ `shared/security/src/commonMain/.../security/di/SecurityModule.kt`:
```kotlin
single { PasswordHasher }                     // ← bare expect object — potentially zero consumers
single<PasswordHashPort> { PasswordHasherAdapter() }  // ← correct port binding
```

**Problem:** `single { PasswordHasher }` makes the raw `PasswordHasher` `expect object` injectable by any module that depends on `:shared:security`. This allows developers to bypass `PasswordHashPort` and call `hashPassword()`/`verifyPassword()` directly — defeating the MERGED-F3 abstraction. `PinManager` (the only likely consumer) uses `PasswordHasher` as a direct object call, not via Koin injection, so this binding appears to have zero Koin consumers.

> **Recommendation:** Grep project-wide for `get<PasswordHasher>()` and `inject<PasswordHasher>()`. If zero results, remove `single { PasswordHasher }` from `SecurityModule`. Retain only `single<PasswordHashPort> { PasswordHasherAdapter() }` to enforce the domain contract at the DI boundary.

---

### NEW-04 — `SecurityModule` Has Undocumented Runtime Prerequisite: `String named("deviceId")` 🟠 MEDIUM

🏗️ `shared/security/src/commonMain/.../security/di/SecurityModule.kt`:
```kotlin
single {
    SecurityAuditLogger(
        auditRepository = get(),
        deviceId = get(named("deviceId")),   // ← requires named String binding from elsewhere
    )
}
```

**Problem:** `securityModule` requires a `String` qualifier `"deviceId"` to exist in the Koin graph at module load time. This binding:
- Is **not documented** in `SecurityModule.kt` beyond a brief KDoc mention
- Is **not documented** in `Master_plan.md` §4.2 dependency graph
- Is **not visible** in `App.kt`, `MainActivity.kt`, `main.kt`, or any DI module found in this scan

If `securityModule` loads before the platform module providing `single(named("deviceId")) { ... }`, Koin throws `NoBeanDefFoundException("No definition found for class:'String' qualifier:'deviceId'")` — a startup crash on all platforms.

> **Recommendation:** (1) Locate the provider — search for `named("deviceId")` across all platform source sets. (2) If not yet registered: implement in `AndroidDataModule.kt` (e.g. `Settings.Secure.ANDROID_ID` + UUID fallback) and `DesktopDataModule.kt` (UUID persisted to a config file on first launch). (3) Document in `Master_plan.md §4.2` as a required pre-condition for `securityModule`. (4) Add a `check(...)` or a descriptive `NoBeanDefFoundException` handler in `App.kt` initialization so the missing binding produces an actionable error message rather than a Koin stack trace.

---

## PHASE 3 CONSOLIDATED FINDINGS TABLE

```
DOC CONFLICTS:

⚠️  [UI_UX_Main_Plan.md + ER_diagram.md] say "ZentaPOS" · [Master_plan.md + all code] say "ZyntaPOS"
    Recommendation: Search-replace "ZentaPOS" → "ZyntaPOS" in UI/UX + ER docs.

⚠️  [Master_plan.md §3.3] shows BaseViewModel API with onIntent/setState/SharedFlow
    [composeApp/core/.../ui/core/mvi/BaseViewModel.kt] uses dispatch/handleIntent/updateState/Channel
    Recommendation: Update §3.3 code sample; deleted zombie was the doc's reference.

⚠️  [Master_plan.md §15.1] says Kotlin 2.1+, Compose 1.7+, AGP 8.5+ etc.
    [gradle/libs.versions.toml] has Kotlin 2.3.0, Compose 1.10.0, AGP 8.13.2
    Recommendation: Pin exact versions in Master_plan; remove "+" suffix notation.

⚠️  [Master_plan.md §3.2] omits :composeApp:feature:media + :composeApp:core
    [Master_plan.md §4.1] lists media as M20 · [settings.gradle.kts + code] both modules present
    Recommendation: Add both to §3.2 source tree diagram.

⚠️  [UI_UX_Main_Plan.md §3.3] references ZentaLoadingSkeleton (15 components with "Zenta" prefix)
    [composeApp/designsystem/components/*.kt] uses "Zynta" prefix; LoadingSkeleton → LoadingOverlay
    Recommendation: Bulk rename in doc (DC-01 fix covers 14 of 15); fix LoadingSkeleton→LoadingOverlay separately.

DUPLICATIONS (resolved):

📄 composeApp/feature/pos/.../PosSearchBar.kt ↔ composeApp/designsystem/.../ZyntaSearchBar.kt
   — RESOLVED: PosSearchBar is a clean thin wrapper, no duplication.

📄 composeApp/feature/inventory/.../ProductFormValidator.kt ↔ shared/domain/.../validation/ProductValidator.kt
   — RESOLVED: Feature-layer validator deleted; domain validator is the single source.

📄 shared/data/.../local/security/{PasswordHasher,SecurePreferences,InMemorySecurePreferences,PlaceholderPasswordHasher}.kt
   ↔ shared/security/.../auth/PasswordHasher.kt + prefs/SecurePreferences.kt
   — RESOLVED (MERGED-F3): Stubs deleted; domain port/adapter pattern implemented.

📄 shared/domain/src/commonTest/.../fakes/FakeRepositories{,Part2,Part3}.kt
   — RESOLVED: Reorganised as FakeAuthRepositories, FakePosRepositories, FakeInventoryRepositories, FakeSharedRepositories.

ARCHITECTURAL VIOLATIONS (new):

🏗️ composeApp/feature/pos/.../printer/PrinterManagerReceiptAdapter.kt
   imports com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
   — should inject AuditRepository (domain port) instead of security infrastructure class.

RUNTIME RISK (new):

🔁 SecurityModule provides SecurityAuditLogger(deviceId = get(named("deviceId")))
   — named String provider undocumented and unverified in any platform DI module.
   If missing: NoBeanDefFoundException on all platforms at startup.
```

| ID | Type | Severity | Status | Finding | Files |
|----|------|----------|--------|---------|-------|
| DC-01 | Doc Conflict | 🟠 MEDIUM | 🔴 Open | "ZentaPOS" in 2 of 3 planning docs | `UI_UX_Main_Plan.md`, `ER_diagram.md` |
| DC-02 | Doc↔Code | 🟠 MEDIUM | 🔴 Open | `Master_plan.md §3.3` MVI sample matches deleted zombie API | `Master_plan.md §3.3` |
| DC-03 | Doc↔Code | 🟡 LOW | 🔴 Open | Tech stack versions stale in `Master_plan.md §15.1` | `Master_plan.md` vs `libs.versions.toml` |
| DC-04 | Doc Conflict | 🟡 LOW | 🔴 Open | `feature:media` + `core` in §4.1 but absent from §3.2 tree | `Master_plan.md` |
| DC-05 | Doc↔Code | 🟡 LOW | 🔴 Open | 15 component names wrong brand prefix in `UI_UX_Main_Plan.md`; LoadingSkeleton vs LoadingOverlay | `UI_UX_Main_Plan.md §3.3` |
| DUP-01 | ✅ CLOSED | — | — | PosSearchBar confirmed clean wrapper | — |
| DUP-02 | ✅ CLOSED | — | — | ProductFormValidator deleted; domain validator authoritative | — |
| DUP-03 | ✅ CLOSED | — | — | Data-layer security stubs removed; port/adapter implemented | — |
| DUP-04 | ✅ CLOSED | — | — | FakeRepositories reorganised by domain | — |
| DUP-05 | ✅ CLOSED | — | — | `androidx-work` version.ref added | — |
| **NEW-01** | Arch Violation | 🟠 MEDIUM | 🔴 Open | `PrinterManagerReceiptAdapter` imports `SecurityAuditLogger` in feature layer | `pos/printer/PrinterManagerReceiptAdapter.kt` |
| **NEW-02** | Doc↔Code | 🟡 LOW | 🔴 Open | `DataModule.kt` KDoc misattributes `PasswordHashPort` binding to DataModule | `DataModule.kt` vs `SecurityModule.kt` |
| **NEW-03** | Dead Code | 🟡 LOW | 🔴 Open | Bare `single { PasswordHasher }` Koin binding in SecurityModule may be unused | `SecurityModule.kt` |
| **NEW-04** | Runtime Risk | 🟠 MEDIUM | 🔴 Open | `named("deviceId")` Koin prerequisite undocumented; missing provider = startup crash | `SecurityModule.kt` |

---

## SPRINT REMEDIATION PLAN (Phase 3)

| Priority | ID | Action | File(s) | Sprint |
|----------|----|--------|---------|--------|
| 🔴 P0 | NEW-04 | Locate or create `single(named("deviceId"))` in Android + Desktop platform modules; document in `Master_plan.md §4.2` | `AndroidDataModule.kt`, `DesktopDataModule.kt`, `Master_plan.md` | **Immediate** |
| 🟠 P1 | NEW-01 | Replace `SecurityAuditLogger` with `AuditRepository` in `PrinterManagerReceiptAdapter`; remove `:shared:security` from `pos/build.gradle.kts` | `PrinterManagerReceiptAdapter.kt`, `pos/build.gradle.kts` | Sprint 4 |
| 🟠 P2 | DC-01 | Search-replace "ZentaPOS" → "ZyntaPOS" in `UI_UX_Main_Plan.md` + `ER_diagram.md` | Both planning docs | Sprint 4 |
| 🟠 P2 | DC-02 | Update `Master_plan.md §3.3` MVI code sample to match live `BaseViewModel` API | `Master_plan.md §3.3` | Sprint 4 |
| 🟠 P2 | DC-05 | Update all 15 component names in `UI_UX_Main_Plan.md §3.3` to `Zynta` prefix; clarify LoadingSkeleton vs LoadingOverlay | `UI_UX_Main_Plan.md §3.3` | Sprint 4 |
| 🟡 P3 | NEW-02 | Fix `DataModule.kt` KDoc: `PasswordHashPort` binding is in `SecurityModule` | `DataModule.kt` | Sprint 4 |
| 🟡 P3 | NEW-03 | Grep for bare `PasswordHasher` Koin consumers; remove `single { PasswordHasher }` if unused | `SecurityModule.kt` | Sprint 4 |
| 🟡 P3 | DC-03 | Update `Master_plan.md §15.1` version table with exact pinned versions | `Master_plan.md §15.1` | Sprint 4 |
| 🟡 P3 | DC-04 | Add `:composeApp:feature:media` + `:composeApp:core` to `Master_plan.md §3.2` source tree | `Master_plan.md §3.2` | Sprint 4 |
| 🟡 P3 | P2-07+P2-08 | Update `Master_plan.md §4.1`: add `:composeApp:core` M-number; fix M07 deps list | `Master_plan.md §4.1` | Sprint 4 |

---

## CUMULATIVE AUDIT STATUS

| Phase | New Findings | Closed from Prior Phase | Net Open |
|-------|-------------|------------------------|----------|
| Phase 1 | 11 | 0 | 11 |
| Phase 2 | 10 | 0 | 21 |
| Phase 3-v1 (draft, 2026-02-21) | 17 | 0 | 38 |
| **Phase 3-v2 (this report, 2026-02-22)** | **4 new** | **13 closed (all Phase 2 🔴 HIGH)** | **16 open** |

### Open Findings by Severity

| Severity | Count | IDs |
|----------|-------|-----|
| 🔴 P0 Runtime Risk | 1 | NEW-04 |
| 🟠 MEDIUM | 3 | NEW-01, DC-01, DC-02 |
| 🟡 LOW | 7 | NEW-02, NEW-03, DC-03, DC-04, DC-05, P2-07, P2-08 |

---

## AUDIT VERDICT — Phase 3

**The codebase underwent a substantial architecture repair sprint (MERGED-F1 through MERGED-F3) between the Phase 2 scan and this Phase 3 scan. 13 of 21 outstanding findings were closed, including all 🔴 HIGH severity Phase 2 items.**

The MERGED-F3 hexagonal port/adapter restructuring for security concerns is architecturally sound and correctly implemented:
- `PasswordHashPort` / `SecureStoragePort` / `SecureStorageKeys` in `:shared:domain` establish clean contracts
- `PasswordHasherAdapter` and `SecurePreferences` in `:shared:security` implement them
- `SecurePreferencesKeyMigration` in `:shared:data` handles the key-schema upgrade idempotently
- All data-layer code now imports only domain interfaces — zero `:shared:security` imports in `:shared:data`

**Two items require attention before Sprint 4:**

1. **NEW-04 (P0)** — `securityModule` requires `String named("deviceId")` in the Koin graph. If this named binding has no provider at startup, the app crashes on every platform. Must be located, implemented if missing, and documented before integration testing begins.

2. **NEW-01 (P1)** — `PrinterManagerReceiptAdapter` in `:composeApp:feature:pos` directly imports `SecurityAuditLogger` from `:shared:security`. This is the only remaining cross-boundary infrastructure import in the feature layer. Replacing it with `AuditRepository` (domain port) completes the MERGED-F3 decoupling and removes the `:shared:security` dependency from `pos/build.gradle.kts`.

The remaining 9 open findings are entirely **documentation debt**. The codebase has structurally outpaced its planning documents. A focused 1–2 hour doc sprint in Sprint 4 — covering DC-01 through DC-05, P2-07, P2-08, NEW-02, NEW-03 — would bring all three planning documents into full alignment with the current code state and unblock onboarding of new developers without ambiguity.

---

*End of Phase 3 Audit (v2.0) — ZyntaPOS ZENTA-PHASE3-CONSISTENCY-v2.0*


---

---

# PHASE 3 — CONTINUATION SCAN (v2.1)
> **Addendum Date:** 2026-02-22 (same audit day — deeper pass)  
> **Scope:** Live codebase re-scan for NEW-04 provider confirmation + unexplored module duplication sweep + Gradle hygiene audit  
> **Supersedes:** Nothing — appended to v2.0

---

## CONTINUATION FINDINGS — NEW-04 ESCALATION

### NEW-04 CONFIRMED — `named("deviceId")` Has Zero Providers Across ALL Modules 🔴 P0 RUNTIME CRASH

The Phase 3-v2.0 report flagged this as a probable P0. The continuation scan provides full proof.

**Evidence gathered:**

| Location Checked | `named("deviceId")` Provider? |
|-----------------|-------------------------------|
| `shared/core/src/.../di/CoreModule.kt` | ❌ ABSENT |
| `shared/security/src/.../di/SecurityModule.kt` | ❌ CONSUMER only (`get(named("deviceId"))`) |
| `shared/data/src/androidMain/.../di/AndroidDataModule.kt` | ❌ ABSENT |
| `shared/data/src/jvmMain/.../di/DesktopDataModule.kt` | ❌ ABSENT |
| `shared/data/src/commonMain/.../di/DataModule.kt` | ❌ ABSENT |
| `shared/hal/src/.../di/HalModule.kt` | ❌ ABSENT |
| `shared/domain/src/.../DomainModule.kt` | ❌ ABSENT |
| `composeApp/src/commonMain/.../App.kt` | ❌ ABSENT (no Koin setup) |
| `androidApp/src/.../ZyntaApplication.kt` | ❌ ABSENT |
| `composeApp/src/jvmMain/.../main.kt` | ❌ ABSENT |
| All 17 feature + navigation + design system modules | ❌ ABSENT |

**Koin load order in both entry points (`ZyntaApplication` + `main.kt`):**
```
Tier 1: coreModule          ← no deviceId
Tier 2: securityModule      ← REQUIRES get(named("deviceId")) ← CRASHES HERE
Tier 3: halModule
Tier 4: androidDataModule / desktopDataModule, dataModule
Tier 5: navigationModule
Tier 6: feature modules...
```

`securityModule` is loaded as Tier 2. Koin resolves `SecurityAuditLogger`'s `deviceId` at
singleton-creation time (eager). No named `String` provider exists anywhere in the graph.
**Result:** `org.koin.core.error.NoBeanDefFoundException: No definition found for class:'String' qualifier:'deviceId'`
on first app launch on Android AND Desktop.

> **Recommendation (P0 — do before any test run):**
> 
> **Android** — Add to `AndroidDataModule.kt`:
> ```kotlin
> single(named("deviceId")) {
>     try {
>         android.provider.Settings.Secure.getString(
>             androidContext().contentResolver,
>             android.provider.Settings.Secure.ANDROID_ID
>         ).takeIf { !it.isNullOrBlank() && it != "9774d56d682e549c" }
>     } catch (_: Exception) { null }
>     ?: java.util.UUID.randomUUID().toString().also { uuid ->
>         // Persist to secure prefs so it survives reinstall (optional but recommended)
>     }
> }
> ```
> 
> **Desktop** — Add to `DesktopDataModule.kt`:
> ```kotlin
> single(named("deviceId")) {
>     val dir = get<String>()   // resolves appDataDir
>     val idFile = java.io.File(dir, ".device_id")
>     if (idFile.exists()) idFile.readText().trim()
>     else java.util.UUID.randomUUID().toString().also { idFile.writeText(it) }
> }
> ```
>
> Both providers must be added to the **platform modules** (not `dataModule`) so they resolve before
> `securityModule` accesses them at Tier 2. Koin's lazy resolution means the exact ordering of
> `androidDataModule + coreModule` vs `securityModule` matters less than the overall graph completeness,
> but placing it in platform modules is the correct architectural home.
>
> Additionally: document in `Master_plan.md §4.2` as: *"Platform modules must provide `String named("deviceId")` before `securityModule` loads."*

---

## NEW FINDINGS FROM CONTINUATION SCAN

### NEW-05 — 4 Private Currency Formatters Bypassing `CurrencyFormatter` in `:shared:core` 🟠 MEDIUM

🔁 **Duplicate business logic** — found in 4 locations while canonical impl exists in `:shared:core`

| File | Function | Implementation |
|------|----------|----------------|
| `feature/register/.../CloseRegisterScreen.kt:501` | `private fun formatCurrency(Double): String` | `abs → toLong → padStart(2,'0')` — no symbol |
| `feature/register/.../ZReportScreen.kt:434` | `private fun formatZCurrency(Double): String` | **Byte-for-byte identical** to `CloseRegisterScreen.formatCurrency` — different name only |
| `feature/pos/.../ProductGridSection.kt:58` | `private fun formatPrice(Double): String` | Same `toLong cents` math — no symbol |
| `feature/inventory/.../ProductListScreen.kt:394` | `private fun formatPrice(Double): String` | **Near-identical** to `ProductGridSection.formatPrice` — adds `"LKR "` prefix |

**Canonical exists but ignored:**  
`shared/core/src/commonMain/.../core/utils/CurrencyFormatter.kt` — locale-aware, HALF_UP rounding, configurable symbol and decimal places — already injected in `CashPaymentPanel.kt`, `CartItemList.kt`, `OrderDiscountDialog.kt` inside the same `:feature:pos` module.

**What the doc says vs what code shows:**

| Master_plan.md §5 | Code |
|-------------------|------|
| `CurrencyFormatter` in `:shared:core` is the single formatting utility | 4 private hand-rolled formatters in 3 feature modules, all producing `"2.50"` without locale/symbol support |

⚠️ Risk: `formatCurrency` in `CloseRegisterScreen` uses floating-point subtraction (`(abs - int) * 100`) which can produce `"2.499999"` for amounts like `2.50`. `CurrencyFormatter` uses `HALF_UP` rounding via `roundToLong()`.

> **Recommendation:** Delete all four private functions. Each screen already has or can inject `CurrencyFormatter` via its ViewModel. Replace call sites:
> ```kotlin
> // Before: private fun formatCurrency(amount: Double) = ...  
> // After: in ViewModel, expose pre-formatted strings via state; or pass formatter as lambda
> ```
> Shortest path: add `CurrencyFormatter` to the screen's ViewModel state and pre-format all monetary fields. `CloseRegisterScreen` + `ZReportScreen` are the highest priority — they display reconciliation totals where rounding accuracy matters.

---

### NEW-06 — 4 Local `*EmptyState` Composables Duplicating `ZyntaEmptyState` in `:feature:inventory` 🟡 LOW

📄 **Structural duplication** — identical `Box > Column > Icon > Text > Button` pattern in 4 private functions

| File | Function | Icon | Title | Has CTA |
|------|----------|------|-------|---------|
| `CategoryListScreen.kt:306` | `private fun CategoryEmptyState(onAdd: () -> Unit)` | `Icons.Default.Category` | "No categories yet" | ✅ |
| `SupplierListScreen.kt:276` | `private fun SupplierEmptyState()` | `Icons.Default.Business` | "No suppliers found" | ❌ |
| `UnitManagementScreen.kt:352` | `private fun UnitEmptyState(onAdd, modifier)` | `Icons.Default.Scale` | "No unit groups defined" | ✅ |
| `TaxGroupScreen.kt:357` | `private fun TaxGroupEmptyState(modifier, onAdd)` | `Icons.Default.Percent` | "No tax groups configured" | ✅ |

**Canonical component exists and covers all cases:**  
`ZyntaEmptyState(icon, title, subtitle, ctaLabel, onCtaClick)` in `:composeApp:designsystem` already supports all these patterns. `feature/pos` and `feature/reports` use `ZyntaEmptyState` directly — only `:feature:inventory` uses local copies.

> **Recommendation:** Replace all 4 with `ZyntaEmptyState(...)` calls. Example replacement for `CategoryEmptyState`:
> ```kotlin
> // Before (24 lines)
> private fun CategoryEmptyState(onAdd: () -> Unit) { Box(...) { Column(...) { Icon(...); Text(...); Button(...) } } }
>
> // After (1 line)
> ZyntaEmptyState(icon = Icons.Default.Category, title = "No categories yet",
>     subtitle = "Create your first category to organise products",
>     ctaLabel = "Add Category", onCtaClick = onAdd)
> ```
> This also ensures consistent icon sizing, spacing tokens, and button styling from `ZyntaButton` rather than raw `Button`.

---

### NEW-07 — `CircularProgressIndicator` Used Raw in 17 Locations; `ZyntaLoadingOverlay` Used in Only 2 🟡 LOW

🔁 **Inconsistent loading UX pattern** — design system component underutilized

| Usage pattern | Count | Locations |
|--------------|-------|-----------|
| Raw `CircularProgressIndicator()` or `CircularProgressIndicator(Modifier.size(...))` | 17 | `auth/PinLockScreen`, `register/OpenRegisterScreen`, `CloseRegisterScreen`, `RegisterDashboardScreen`, `CashInOutDialog`, `ZReportScreen` (×3), `pos/ReceiptScreen`, `inventory/ProductDetailScreen`, `BulkImportDialog`, `SupplierListScreen`, `SupplierDetailScreen`, `UnitManagementScreen`, `CategoryDetailScreen`, `ProductListScreen`, `TaxGroupScreen` |
| `ZyntaLoadingOverlay(isLoading = ...)` | 2 | `reports/SalesReportScreen`, `reports/StockReportScreen` |

`ZyntaLoadingOverlay` provides a standardised scrim + spinner with a configurable `scrimAlpha`. Using raw `CircularProgressIndicator` in some screens and `ZyntaLoadingOverlay` in others creates visual inconsistency.

> **Recommendation (low priority, UX sprint):** Audit each of the 17 raw usages to determine if they are:
> (a) **Full-screen loading states** → replace with `ZyntaLoadingOverlay`  
> (b) **Inline/button-embedded spinners** (e.g. `Modifier.size(18.dp)` inside a dialog button) → keep raw `CircularProgressIndicator` since the overlay pattern is inappropriate for these  
> Of the 17, approximately 8 (full-screen `if (isLoading) CircularProgressIndicator()` wrapped in `Box(contentAlignment = Center)`) are candidates for `ZyntaLoadingOverlay`. The remaining ~9 inline uses in `SupplierDetailScreen`, `CategoryDetailScreen`, `CashInOutDialog`, etc. are appropriate as-is.

---

### F-09 STATUS UPDATE — ✅ CLOSED

`settings.gradle.kts` line 67 now reads:
```
// MODULE REGISTRY — 23 modules
```
Phase 2 item F-09 (stale "22 modules" comment) is **CONFIRMED CLOSED**.

---

## UPDATED CONSOLIDATED FINDINGS TABLE (v2.1)

| ID | Type | Severity | Status | Finding |
|----|------|----------|--------|---------|
| DC-01 | Doc Conflict | 🟠 MEDIUM | 🔴 Open | "ZentaPOS" in `UI_UX_Main_Plan.md` + `ER_diagram.md` |
| DC-02 | Doc↔Code | 🟠 MEDIUM | 🔴 Open | `Master_plan.md §3.3` MVI sample uses deleted zombie API |
| DC-03 | Doc↔Code | 🟡 LOW | 🔴 Open | Tech stack versions stale in `Master_plan.md §15.1` |
| DC-04 | Doc Conflict | 🟡 LOW | 🔴 Open | `:feature:media` + `:composeApp:core` absent from `Master_plan.md §3.2` |
| DC-05 | Doc↔Code | 🟡 LOW | 🔴 Open | 15 component names wrong prefix in `UI_UX_Main_Plan.md §3.3` |
| NEW-01 | Arch Violation | 🟠 MEDIUM | 🔴 Open | `PrinterManagerReceiptAdapter` imports `SecurityAuditLogger` (feature→infra) |
| NEW-02 | Doc↔Code | 🟡 LOW | 🔴 Open | `DataModule.kt` KDoc misattributes `PasswordHashPort` binding location |
| NEW-03 | Dead Code | 🟡 LOW | 🔴 Open | Bare `single { PasswordHasher }` in `SecurityModule` — possible zero consumers |
| NEW-04 | Runtime Risk | 🔴 **P0** | 🔴 Open | `named("deviceId")` has **ZERO providers** in all modules — startup crash guaranteed |
| **NEW-05** | Logic Dup | 🟠 MEDIUM | 🔴 Open | 4 private currency formatters bypass `CurrencyFormatter` in `:shared:core` |
| **NEW-06** | UI Dup | 🟡 LOW | 🔴 Open | 4 private `*EmptyState` composables in `:feature:inventory` bypass `ZyntaEmptyState` |
| **NEW-07** | UX Inconsistency | 🟡 LOW | 🔴 Open | 17 raw `CircularProgressIndicator` usages vs 2 `ZyntaLoadingOverlay` usages |
| P2-07 | Doc Gap | 🟡 LOW | 🔴 Open | `:composeApp:navigation` deps understated in `Master_plan.md` |
| P2-08 | Doc Gap | 🟡 LOW | 🔴 Open | `:composeApp:core` absent from `Master_plan.md §4.1` |
| F-09 | Stale comment | — | ✅ **CLOSED** | `settings.gradle.kts` now correctly reads "23 modules" |
| DUP-01–05 | ✅ CLOSED | — | — | All prior duplication findings resolved |

---

## UPDATED SEVERITY SUMMARY (v2.1)

| Severity | Count | IDs |
|----------|-------|-----|
| 🔴 P0 Runtime Crash | 1 | NEW-04 ← zero providers confirmed |
| 🟠 MEDIUM | 4 | NEW-01, NEW-05, DC-01, DC-02 |
| 🟡 LOW | 9 | NEW-02, NEW-03, NEW-06, NEW-07, DC-03, DC-04, DC-05, P2-07, P2-08 |
| **Total open** | **14** | |

---

## UPDATED REMEDIATION PLAN (v2.1)

| Priority | ID | Action | Sprint |
|----------|----|--------|--------|
| 🔴 P0 | NEW-04 | Add `single(named("deviceId"))` to `AndroidDataModule` + `DesktopDataModule`; document in `Master_plan.md §4.2` | **Immediate** |
| 🟠 P1 | NEW-01 | Replace `SecurityAuditLogger` with `AuditRepository` in `PrinterManagerReceiptAdapter`; remove `:shared:security` from `pos/build.gradle.kts` | Sprint 4 |
| 🟠 P1 | NEW-05 | Delete `formatCurrency`/`formatZCurrency`/`formatPrice` private functions in 4 screens; route through `CurrencyFormatter` from ViewModel state | Sprint 4 |
| 🟠 P2 | DC-01 | Search-replace "ZentaPOS" → "ZyntaPOS" in `UI_UX_Main_Plan.md` + `ER_diagram.md` | Sprint 4 |
| 🟠 P2 | DC-02 | Update `Master_plan.md §3.3` MVI sample to `dispatch`/`handleIntent`/`updateState`/`Channel` | Sprint 4 |
| 🟠 P2 | DC-05 | Update all 15 component names to `Zynta` prefix in `UI_UX_Main_Plan.md §3.3` | Sprint 4 |
| 🟡 P3 | NEW-06 | Replace 4 private `*EmptyState` composables in `:feature:inventory` with `ZyntaEmptyState(...)` calls | Sprint 4 |
| 🟡 P3 | NEW-07 | Audit 17 raw `CircularProgressIndicator` usages; replace ~8 full-screen ones with `ZyntaLoadingOverlay` | Sprint 5 |
| 🟡 P3 | NEW-02 | Fix `DataModule.kt` KDoc: `PasswordHashPort` is bound in `SecurityModule` | Sprint 4 |
| 🟡 P3 | NEW-03 | Grep `get<PasswordHasher>()`; remove bare binding from `SecurityModule` if zero consumers | Sprint 4 |
| 🟡 P3 | DC-03 | Update `Master_plan.md §15.1` with exact pinned library versions | Sprint 4 |
| 🟡 P3 | DC-04 | Add `:feature:media` + `:composeApp:core` to `Master_plan.md §3.2` tree | Sprint 4 |
| 🟡 P3 | P2-07+P2-08 | Add `:composeApp:core` M-number to `Master_plan.md §4.1`; fix nav module dep list | Sprint 4 |

---

*End of Phase 3 Continuation Addendum (v2.1) — ZyntaPOS ZENTA-PHASE3-CONSISTENCY-v2.1*
