# ZyntaPOS — Phase 3 Audit Report: Doc Consistency + Duplication
> **Doc ID:** ZENTA-PHASE3-CONSISTENCY-v1.0  
> **Auditor:** Senior KMP Architect  
> **Date:** 2026-02-21  
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`  
> **Prerequisites:** Phase 1 Discovery · Phase 2 Alignment  

---

## LEGEND

| Symbol | Meaning |
|--------|---------|
| ⚠️ | Doc-to-doc conflict or doc-to-code mismatch |
| 📄 | File-level duplication (identical or near-identical) |
| 🔁 | Logic-level duplication (same business logic in multiple places) |
| 🏗️ | Architectural violation (code in wrong layer) |
| ✅ | Verified clean — no issue |

---

## 3A — DOC INTERNAL CONSISTENCY

### DC-01 — Project Name: "ZentaPOS" vs "ZyntaPOS" 🔴 HIGH
⚠️ **`UI_UX_Main_Plan.md`** title says `"ZentaPOS — Enterprise UI/UX Master Blueprint"` · Document ID: `ZENTA-UI-UX-PLAN-v1.0`  
⚠️ **`ER_diagram.md`** title says `"ZentaPOS — Enterprise ER Diagram Plan"` · Document ID: `ZENTA-ER-DIAGRAM-v1.0`  
⚠️ **`Master_plan.md`** title says `"ZyntaPOS — Enterprise Master Blueprint"` · Document ID: `ZENTA-MASTER-PLAN-v1.0`  
⚠️ **All code** uses package `com.zyntasolutions.zyntapos`, project folder `ZyntaPOS/`, app name `ZyntaPOS`.

**What the docs say vs what exists:**

| Document | Product Name Used | App Identifier |
|----------|------------------|----------------|
| `UI_UX_Main_Plan.md` | **ZentaPOS** | ZENTA- |
| `ER_diagram.md` | **ZentaPOS** | ZENTA- |
| `Master_plan.md` | **ZyntaPOS** | ZENTA- |
| Codebase, folders, packages | **ZyntaPOS** / `zyntasolutions` | — |

This is the most pervasive doc conflict in the project. Two of three planning documents use the wrong product name, causing consistent ambiguity in developer onboarding, brand references, and any grep/search over doc content.

> **Recommendation:** Decide on the canonical spelling (codebase uses `ZyntaPOS`). Do a search-and-replace in `UI_UX_Main_Plan.md` and `ER_diagram.md`: replace all occurrences of `ZentaPOS` with `ZyntaPOS`. Update Document IDs from `ZENTA-` to `ZYNTA-` or choose a unified prefix. The doc comments inside source files (e.g., `ZentaPOS — PasswordHasher`) also use the wrong name and should be updated.

---

### DC-02 — `PasswordHasher` API Contract Mismatch 🔴 HIGH
⚠️ **`shared/data/src/commonMain/.../data/local/security/PasswordHasher.kt`** declares:
```kotlin
interface PasswordHasher {
    fun verify(plainText: String, hash: String): Boolean   // ← "verify"
    fun hash(plainText: String): String                    // ← "hash"
}
```
⚠️ **`shared/security/src/commonMain/.../security/auth/PasswordHasher.kt`** declares:
```kotlin
expect object PasswordHasher {
    fun verifyPassword(plain: String, hash: String): Boolean  // ← "verifyPassword"
    fun hashPassword(plain: String): String                   // ← "hashPassword"
}
```

**Three-way incompatibility:**
1. **Type system**: data stub is an `interface` (instance-based, injected via Koin); security module is an `expect object` (singleton, no injection)
2. **Method names**: `verify/hash` vs `verifyPassword/hashPassword` — call sites are not interchangeable
3. **Koin binding**: `DataModule.kt` binds `PlaceholderPasswordHasher` to the data-layer `PasswordHasher` interface. `SecurityModule.kt` provides the security module's `expect object`. Any future migration will silently compile while calling wrong implementations.

> **Recommendation:** When Sprint 8 migrates data stubs to security actuals, every call site using the data-layer `PasswordHasher.verify()` must be updated to `PasswordHasher.verifyPassword()`. Eliminate the data-layer stub now — move `PlaceholderPasswordHasher` to `:shared:security` as a test/debug actual, and have `DataModule.kt` import from `:shared:security`. Document this contract in `Master_plan.md §6`.

---

### DC-03 — `SecurePreferences` Key Constant Mismatch 🔴 HIGH
⚠️ **`shared/data/src/commonMain/.../data/local/security/SecurePreferences.kt`** defines keys:
```kotlin
companion object Keys {
    const val ACCESS_TOKEN    = "auth.access_token"
    const val REFRESH_TOKEN   = "auth.refresh_token"
    const val TOKEN_EXPIRY    = "auth.token_expiry"
    const val CURRENT_USER_ID = "auth.user_id"
    const val LAST_SYNC_TS    = "sync.last_timestamp"
}
```
⚠️ **`shared/security/src/commonMain/.../security/prefs/SecurePreferences.kt`** defines:
```kotlin
companion object {
    const val KEY_ACCESS_TOKEN: String   // different prefix convention
    const val KEY_REFRESH_TOKEN: String
    const val KEY_DEVICE_ID: String      // ← NEW key, absent from data stub
    const val KEY_LAST_USER_ID: String   // ← "LAST_USER_ID" vs "CURRENT_USER_ID"
    // TOKEN_EXPIRY and LAST_SYNC_TS are ABSENT
}
```

**Impact:** When Sprint 8 switches from the data stub to the security module's `SecurePreferences`, any tokens stored under `"auth.access_token"` will not be readable via `KEY_ACCESS_TOKEN` because the actual storage key strings will differ between the two implementations. This causes **silent session invalidation** on all devices after the migration — every logged-in user will be force-logged-out.

> **Recommendation:** Before Sprint 8, publish a single authoritative key constant class in `:shared:security` that BOTH modules reference. Implement a one-time migration in `DatabaseMigrations.kt` (or a `SecurePreferencesKeyMigration` utility) that copies all values to the new key names on first launch. Flag as P0 for Sprint 7 (pre-migration sprint).

---

### DC-04 — `BaseViewModel` Contract Documented Twice with Incompatible APIs 🔴 HIGH
⚠️ **`Master_plan.md §3.3`** shows the canonical MVI contract with `sealed interface PosIntent` and `abstract fun onIntent(intent: I)` — matching the `shared/core` zombie `BaseViewModel`.  
⚠️ **`composeApp/core/.../ui/core/mvi/BaseViewModel.kt`** (the actual, active implementation) uses `abstract suspend fun handleIntent(intent: I)` and `fun dispatch(intent: I)` — a completely different API surface.

**Doc says vs code shows:**

| Aspect | Master_plan §3.3 code sample | Canonical `composeApp/core` BaseViewModel |
|--------|------------------------------|-------------------------------------------|
| Base class | (implies) `AutoCloseable` | `androidx.lifecycle.ViewModel` |
| Intent entry point | `fun onIntent(intent: I)` | `fun dispatch(intent: I)` |
| Handler | `abstract fun onIntent` | `abstract suspend fun handleIntent` |
| Effect delivery | `MutableSharedFlow` | `Channel<E>` |
| State update | `setState { }` | `updateState { }` |

The Master_plan §3.3 code snippet aligns with the **zombie** `shared/core` BaseViewModel — not with the **active canonical** one. Every developer who follows the Master_plan will write ViewModels against the wrong API.

> **Recommendation:** Update Master_plan §3.3 MVI code sample to reflect `composeApp/core` BaseViewModel API: change `onIntent` → `dispatch`, `setState` → `updateState`, add `suspend` to handler, show `Channel`-based effects. Delete `shared/core/.../core/mvi/BaseViewModel.kt` (already tracked as Phase 2 P1).

---

### DC-05 — Tech Stack Versions: Master Plan Stale vs Actual Catalog 🟠 MEDIUM
⚠️ **`Master_plan.md §15.1`** vs **`gradle/libs.versions.toml`** (actual):

| Library | Master Plan Says | Actual in Catalog | Delta |
|---------|-----------------|-------------------|-------|
| Kotlin | `2.1+` | `2.3.0` | +0.2 minor |
| Compose Multiplatform | `1.7+` | `1.10.0` | +0.3 minor |
| Coroutines | `1.9+` | `1.10.2` | +0.1 minor, +2 patch |
| Serialization | `1.7+` | `1.8.0` | +0.1 minor |
| Koin | `4.0+` | `4.0.4` | patch only ✅ |
| Ktor | `3.0+` | `3.0.3` | patch only ✅ |
| SQLDelight | `2.0+` | `2.0.2` | patch only ✅ |
| AGP (Gradle) | `8.5+` | `8.13.2` | +0.8 minor |
| DateTime | `0.6+` | `0.6.1` | patch only ✅ |

The major divergences are Kotlin (`2.3.0` is a minor release ahead of `2.1`), Compose Multiplatform (`1.10.0`), and AGP (`8.13.2`). These are legitimate upgrades, but the Master Plan is stale.

> **Recommendation:** Update `Master_plan.md §15.1` table with exact pinned versions from `libs.versions.toml`. Remove `+` suffix notation — exact versions are preferred in architecture docs for reproducibility. Consider automating this via `./gradlew dependencyUpdates` output into the doc as part of sprint retro.

---

### DC-06 — `:composeApp:feature:media` Missing from Master Plan §3.2 Source Tree 🟡 LOW
⚠️ **`Master_plan.md §3.2`** source set tree shows 12 `:composeApp:feature:*` modules but omits `:composeApp:feature:media`.  
⚠️ **`Master_plan.md §4.1`** module registry DOES list it as M20 (`MediaModule`).  
⚠️ **`settings.gradle.kts`** includes it as module #23.

The §3.2 architectural tree and the §4.1 registry are inconsistent with each other within the same document.

> **Recommendation:** Add `:composeApp:feature:media → Media upload, product image management` to the §3.2 source tree directly after `:composeApp:feature:admin`. Also add `:composeApp:core → Shared BaseViewModel, MVI contracts` which is entirely absent from §3.2 (tracked P2-08).

---

### DC-07 — Design System Component Names: Doc vs Code 🟡 LOW
⚠️ **`UI_UX_Main_Plan.md §3.3`** specifies component `ZentaLoadingSkeleton` (shimmer animation).  
⚠️ **`composeApp/designsystem/src/.../components/`** contains `ZentaLoadingOverlay.kt` — no `ZentaLoadingSkeleton.kt` exists.

Additional components specified in UI_UX_Main_Plan §3.3 that have **no corresponding file**:
- `ZentaIconButton` — MISSING
- `ZentaStatusChip` — MISSING  
- `ZentaDatePicker` — MISSING
- `ZentaCurrencyText` — MISSING

> **Recommendation:** For `ZentaLoadingOverlay` vs `ZentaLoadingSkeleton`: if these are genuinely different components (overlay = blocking spinner, skeleton = shimmer placeholder), create both. If the same, update `UI_UX_Main_Plan.md §3.3` to match the code name. For the four missing components: add them to the design system implementation backlog (Sprint 5–6) or mark them as `Planned` in the doc with a sprint target.

---

## 3B — CODE DUPLICATION SCAN

### DUP-01 — `BarcodeScanner.kt` Dual Copy in Same Module 🟠 MEDIUM
📄 `shared/hal/src/commonMain/kotlin/com/zyntasolutions/zyntapos/hal/BarcodeScanner.kt` ↔ `shared/hal/src/commonMain/kotlin/com/zyntasolutions/zyntapos/hal/scanner/BarcodeScanner.kt` — **identical concept, same module, different packages**

The root-level file was the original placement; `scanner/BarcodeScanner.kt` is the canonical location after package reorganization. The root copy is an orphan (0 import references in production code) but pollutes the `hal` package namespace.

> **Recommendation:** Delete `shared/hal/src/commonMain/.../hal/BarcodeScanner.kt` immediately. This was tracked as Phase 1 F-03 and Phase 2 Action Item (🟠 P2 priority) but remains unexecuted.

---

### DUP-02 — `SecurityAuditLogger.kt` Dual Copy in Same Module 🟡 LOW
📄 `shared/security/src/commonMain/.../security/SecurityAuditLogger.kt` ↔ `shared/security/src/commonMain/.../security/audit/SecurityAuditLogger.kt` — **same class, same module, root vs audit/ sub-package**

The root file is NOT a real duplication — it is a `typealias` with `@Deprecated(level = DeprecationLevel.ERROR)` pointing to the `audit/` canonical class. This is an intentional redirect for IDE cache compatibility. However, it creates confusion because grepping for `SecurityAuditLogger` returns two files.

> **Recommendation:** This is safer than a raw duplicate, but the `@file:Suppress("UNUSED")` and IDE-cache justification are fragile. Once the Phase 2 F-04 orphan file for `shared/security/src/commonMain/.../security/SecurityAuditLogger.kt` (without the typealias) is removed per Phase 1/2 action items, verify this bridge is also cleaned up. If no external module imports from the root package, delete the typealias bridge entirely.

---

### DUP-03 — `BaseViewModel.kt` Dual Copy Across Modules (Incompatible APIs) 🔴 HIGH
📄 `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/mvi/BaseViewModel.kt` ↔ `composeApp/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/ui/core/mvi/BaseViewModel.kt` — **same concept, different modules, fundamentally different implementations**

| Property | `shared/core` (zombie) | `composeApp/core` (canonical) |
|----------|------------------------|-------------------------------|
| Base type | `AutoCloseable` | `androidx.lifecycle.ViewModel` |
| Scope management | Manual `CoroutineScope` cancel | AndroidX `viewModelScope` |
| Effect delivery | `MutableSharedFlow(replay=0)` | `Channel<E>(BUFFERED)` |
| State update API | `setState { }` | `updateState { }` |
| Intent API | `abstract fun onIntent(I)` | `abstract suspend fun handleIntent(I)` |
| Active consumers | **0** | **4 feature ViewModels** |

The `shared/core` version also bundles `UiState`, `UiIntent`, `UiEffect` marker interfaces that no feature module extends (every feature defines its own state/intent/effect without extending these).

> **Recommendation:** Delete `shared/core/src/commonMain/.../core/mvi/BaseViewModel.kt` and its bundled marker interfaces. Confirm no test code references `com.zyntasolutions.zyntapos.core.mvi` — run `grep -r "core.mvi" --include="*.kt"` project-wide. Move `UiState`, `UiIntent`, `UiEffect` to `composeApp/core` if feature contracts should implement them (currently optional). Tracked as Phase 2 P1, unexecuted.

---

### DUP-04 — `PasswordHasher`: Parallel Implementations Across Modules (Incompatible) 🔴 HIGH
📄 `shared/data/src/commonMain/.../data/local/security/PasswordHasher.kt` ↔ `shared/security/src/commonMain/.../security/auth/PasswordHasher.kt` — **same domain concept, different modules, incompatible type and API**

```
:shared:data     → interface PasswordHasher { fun verify(); fun hash() }
:shared:security → expect object PasswordHasher { fun verifyPassword(); fun hashPassword() }
```

These are not connected by KMP expect/actual — they are two independent implementations of the same concept. The data module's `DataModule.kt` Koin binding resolves `PasswordHasher` to `PlaceholderPasswordHasher` (the data-layer stub). The security module provides BCrypt actuals for `androidMain` and `jvmMain`. There is no code path that makes the security actuals available to the data layer.

> **Recommendation:** Establish a single `PasswordHasher` contract in `:shared:security`. Remove `PasswordHasher.kt` and `PlaceholderPasswordHasher.kt` from `shared/data/src/commonMain/.../data/local/security/`. Add `:shared:security` as a dependency of `:shared:data` in `shared/data/build.gradle.kts` and update `DataModule.kt` bindings to consume the security module's object. This resolves DC-02 simultaneously.

---

### DUP-05 — `SecurePreferences`: Parallel Interfaces Across Modules (Incompatible Key Constants) 🔴 HIGH
📄 `shared/data/src/commonMain/.../data/local/security/SecurePreferences.kt` ↔ `shared/security/src/commonMain/.../security/prefs/SecurePreferences.kt` — **same domain concept, different modules, mismatched key names and method surface**

```
:shared:data (interface, 5 methods, 5 key constants):
  put(), get(), remove(), clear(), contains()
  ACCESS_TOKEN, REFRESH_TOKEN, TOKEN_EXPIRY, CURRENT_USER_ID, LAST_SYNC_TS

:shared:security (expect class, 4 methods, 4 key constants):
  put(), get(), remove(), clear()  [no contains()]
  KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_DEVICE_ID, KEY_LAST_USER_ID
```

Key constant values differ by both naming convention (`"auth.access_token"` vs unspecified string from the `expect` declaration), and by semantic coverage (`TOKEN_EXPIRY` and `LAST_SYNC_TS` exist in data stub, absent from security module). As noted in DC-03, Sprint 8 migration will cause silent data loss.

> **Recommendation:** Consolidate immediately. Add `contains()` to the security module's `SecurePreferences` (already present in data stub; Android `EncryptedSharedPreferences` supports it natively). Publish a `SecurePreferencesKeys` object in `:shared:security:commonMain` as the single source of truth. Remove all data-layer stub key constants and import from security module.

---

### DUP-06 — `UiState / UiIntent / UiEffect` Marker Interfaces: Dead Code in Zombie Module 🟠 MEDIUM
🔁 `shared/core/src/commonMain/.../core/mvi/BaseViewModel.kt` defines `interface UiState`, `interface UiIntent`, `interface UiEffect` — zero features extend these interfaces (all feature state/intent/effect classes are plain `data class` / `sealed interface` with no supertype from this package).

These duplicate the conceptual role of what *should* be in `composeApp/core` as a shared MVI contract, but they are effectively unreachable dead code.

> **Recommendation:** When deleting `shared/core/.../core/mvi/BaseViewModel.kt` (DUP-03 action), simultaneously decide: (a) if architectural policy requires features to extend marker interfaces, add `interface UiState`, `UiIntent`, `UiEffect` to `composeApp/core` and update all 6 active feature modules' state/intent/effect files; or (b) drop the marker interfaces entirely (current de-facto position). Document the decision in `ARCHITECTURE.md`.

---

### DUP-07 — `ProductFormValidator` in Feature Layer Duplicates Domain Validation Responsibility 🏗️ MEDIUM
🏗️ `composeApp/feature/inventory/src/commonMain/.../inventory/ProductFormValidator.kt` — 136 lines of validation logic (price, cost, stock qty, category, unit, barcode) in the **presentation layer**.

`shared/domain/src/commonMain/.../domain/validation/` already contains `PaymentValidator.kt`, `StockValidator.kt`, and `TaxValidator.kt`. The pattern is established: validators belong in `:shared:domain`.

`ProductFormValidator` validates `productId`, `price`, `costPrice`, `stockQty`, `minStockQty` — overlapping semantically with `StockValidator` (which validates stock quantities). Two sources of truth for "what is a valid product stock quantity" now exist.

> **Recommendation:** Move `ProductFormValidator` to `shared/domain/src/commonMain/.../domain/validation/ProductValidator.kt`. Merge numeric/stock validation rules with `StockValidator.kt` where overlap exists. Update `InventoryViewModel` to inject `ProductValidator` via use case or direct injection from domain. The feature module should only call into the domain validator, never define validation rules itself.

---

### DUP-08 — `FakeRepositories` Split Into 3 Files in Test Source Set 🟡 LOW
📄 `shared/domain/src/commonTest/.../usecase/fakes/FakeRepositories.kt` ↔ `FakeRepositoriesPart2.kt` ↔ `FakeRepositoriesPart3.kt` — **single logical unit fragmented across 3 files**

These are test fake implementations of domain repository interfaces. The split appears to have been introduced to work around file-length tooling warnings, but creates fragmented discoverability for test authors: finding the fake for `OrderRepository` requires knowing which Part file it lives in.

> **Recommendation:** Consolidate into a single `FakeRepositories.kt` file using Kotlin's multi-class-per-file model, or split by domain rather than by arbitrary size (e.g., `FakeAuthRepositories.kt`, `FakePosRepositories.kt`, `FakeInventoryRepositories.kt`). This is a low-priority test hygiene item — target Sprint 5 test infrastructure cleanup.

---

### DUP-09 — `PosSearchBar.kt` vs `ZentaSearchBar.kt` (NEEDS CLARIFICATION) 🟡 LOW
📄 `composeApp/feature/pos/src/commonMain/.../pos/PosSearchBar.kt` ↔ `composeApp/designsystem/src/commonMain/.../designsystem/components/ZentaSearchBar.kt`

A POS-specific search bar exists in the feature module alongside the canonical design system search bar. Without reading both implementations it is unclear whether `PosSearchBar` is: (a) a thin wrapper that adds POS-specific behavior (barcode scan trigger, debounce tuning) on top of `ZentaSearchBar`, or (b) a full reimplementation that bypasses the design system component.

> **NEEDS CLARIFICATION:** Verify that `PosSearchBar.kt` delegates rendering to `ZentaSearchBar` internally. If it duplicates UI logic, move the POS-specific behavior to a stateless `ZentaSearchBar` parameter (`onBarcodeIconClick: (() -> Unit)?`) and delete `PosSearchBar.kt`. If it is a thin wrapper, document it as an intentional composition in the feature module's README.

---

### DUP-10 — Hardcoded Gradle Versions ✅ CLEAN
All 23 module `build.gradle.kts` files examined use `alias(libs.plugins.*)` for plugin declarations and `libs.versions.*` / `libs.bundles.*` for dependency references. No hardcoded version strings found in production build scripts.

The one exception is `androidx-work-runtime` in `libs.versions.toml` itself:
```toml
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version = "2.10.1" }
```
This is a **literal version** (no `version.ref`) in the catalog. All other entries use `version.ref`.

> **Recommendation:** Add `androidx-work = "2.10.1"` to the `[versions]` section and change the library entry to `version.ref = "androidx-work"` for consistency and single-point-of-update.

---

## PHASE 3 CONSOLIDATED FINDINGS TABLE

| ID | Type | Severity | Finding | Files Involved |
|----|------|----------|---------|----------------|
| DC-01 | Doc Conflict | 🔴 HIGH | "ZentaPOS" vs "ZyntaPOS" across docs | `UI_UX_Main_Plan.md`, `ER_diagram.md` vs `Master_plan.md` + all code |
| DC-02 | Doc↔Code | 🔴 HIGH | `PasswordHasher` API mismatch: `interface verify/hash` vs `expect object verifyPassword/hashPassword` | `shared/data/.../local/security/PasswordHasher.kt` vs `shared/security/.../auth/PasswordHasher.kt` |
| DC-03 | Doc↔Code | 🔴 HIGH | `SecurePreferences` key constants differ: Sprint 8 migration will silently invalidate all user sessions | `shared/data/.../local/security/SecurePreferences.kt` vs `shared/security/.../prefs/SecurePreferences.kt` |
| DC-04 | Doc Conflict | 🔴 HIGH | `Master_plan §3.3` MVI code sample matches zombie `BaseViewModel` API, not canonical `composeApp/core` API | `Master_plan.md §3.3` vs `composeApp/core/.../ui/core/mvi/BaseViewModel.kt` |
| DC-05 | Doc↔Code | 🟠 MEDIUM | Tech stack versions in Master Plan stale: Kotlin, Compose, Coroutines, AGP | `Master_plan.md §15.1` vs `gradle/libs.versions.toml` |
| DC-06 | Doc Conflict | 🟡 LOW | `:composeApp:feature:media` in §4.1 M20 but absent from §3.2 source tree | `Master_plan.md §3.2` vs `Master_plan.md §4.1` |
| DC-07 | Doc↔Code | 🟡 LOW | `ZentaLoadingSkeleton` (docs) vs `ZentaLoadingOverlay.kt` (code); 4 other components missing | `UI_UX_Main_Plan.md §3.3` vs `composeApp/designsystem/src/.../components/` |
| DUP-01 | Duplication | 🟠 MEDIUM | `BarcodeScanner.kt` orphan at root of `hal` package alongside canonical `scanner/BarcodeScanner.kt` | `shared/hal/.../hal/BarcodeScanner.kt` ↔ `shared/hal/.../hal/scanner/BarcodeScanner.kt` |
| DUP-02 | Duplication | 🟡 LOW | `SecurityAuditLogger.kt` typealias bridge at root alongside canonical `audit/SecurityAuditLogger.kt` | `shared/security/.../security/SecurityAuditLogger.kt` ↔ `shared/security/.../security/audit/SecurityAuditLogger.kt` |
| DUP-03 | Duplication | 🔴 HIGH | `BaseViewModel.kt` zombie in `shared/core` vs canonical in `composeApp/core` — incompatible APIs, 0 consumers of zombie | `shared/core/.../core/mvi/BaseViewModel.kt` ↔ `composeApp/core/.../ui/core/mvi/BaseViewModel.kt` |
| DUP-04 | Duplication | 🔴 HIGH | `PasswordHasher` parallel implementations in two modules with incompatible APIs and no shared contract | `shared/data/.../local/security/PasswordHasher.kt` ↔ `shared/security/.../auth/PasswordHasher.kt` |
| DUP-05 | Duplication | 🔴 HIGH | `SecurePreferences` parallel interfaces in two modules with mismatched key constants — Sprint 8 data-loss risk | `shared/data/.../local/security/SecurePreferences.kt` ↔ `shared/security/.../prefs/SecurePreferences.kt` |
| DUP-06 | Dead Code | 🟠 MEDIUM | `UiState/UiIntent/UiEffect` marker interfaces defined in zombie module, never extended by any feature | `shared/core/.../core/mvi/BaseViewModel.kt` |
| DUP-07 | Arch Violation | 🟠 MEDIUM | `ProductFormValidator` (136 lines, validation logic) lives in presentation feature layer, not domain | `composeApp/feature/inventory/.../ProductFormValidator.kt` vs `shared/domain/.../domain/validation/` |
| DUP-08 | Fragmentation | 🟡 LOW | `FakeRepositories` split into 3 arbitrarily named test files with no domain-based grouping | `shared/domain/src/commonTest/.../fakes/FakeRepositories{,Part2,Part3}.kt` |
| DUP-09 | NEEDS CLARIFICATION | 🟡 LOW | `PosSearchBar.kt` may duplicate `ZentaSearchBar.kt` — intent unclear without reading impl | `composeApp/feature/pos/.../pos/PosSearchBar.kt` vs `composeApp/designsystem/.../components/ZentaSearchBar.kt` |
| DUP-10 | Clean | ✅ CLEAN | Gradle version catalog used consistently; single `version.ref` violation in `androidx-work-runtime` | `gradle/libs.versions.toml` |

---

## SPRINT REMEDIATION PLAN (Phase 3 Actions)

| Priority | ID | Action | Owner Target | Sprint |
|----------|----|--------|--------------|--------|
| 🔴 P0 | DC-03 + DUP-05 | Design `SecurePreferencesKeys` migration utility before Sprint 8 writes to `SecurePreferences` | Arch lead | **Sprint 5 — before any production data written** |
| 🔴 P0 | DUP-04 + DC-02 | Collapse `PasswordHasher` stubs — remove from `:shared:data`, import from `:shared:security` | Arch lead | **Sprint 5** |
| 🔴 P1 | DC-04 + DUP-03 | Delete `shared/core/.../core/mvi/BaseViewModel.kt`; update `Master_plan §3.3` code sample | All feature devs | Sprint 4 (carried from Phase 2 P1) |
| 🟠 P2 | DC-01 | Search-replace "ZentaPOS" → "ZyntaPOS" in `UI_UX_Main_Plan.md` and `ER_diagram.md`; update all source file doc comments | Tech writer | Sprint 4 |
| 🟠 P2 | DUP-07 | Move `ProductFormValidator` to `shared/domain/.../validation/ProductValidator.kt` | Inventory dev | Sprint 4 |
| 🟠 P2 | DUP-01 | Delete root `shared/hal/.../hal/BarcodeScanner.kt` (Phase 1 F-03, still open) | Any dev | Sprint 4 |
| 🟠 P2 | DUP-06 | Remove orphan `UiState/UiIntent/UiEffect` from zombie BaseViewModel | Arch lead | Sprint 4 (part of DUP-03 deletion) |
| 🟠 P2 | DC-05 | Update `Master_plan §15.1` versions to match `libs.versions.toml` exact values | Tech writer | Sprint 4 |
| 🟡 P3 | DC-06 | Add `:composeApp:feature:media` and `:composeApp:core` to `Master_plan §3.2` source tree | Tech writer | Sprint 4 |
| 🟡 P3 | DC-07 | Resolve `ZentaLoadingSkeleton` vs `ZentaLoadingOverlay` naming; backlog 4 missing components | Design lead | Sprint 5–6 |
| 🟡 P3 | DUP-02 | Clean up `SecurityAuditLogger` typealias bridge after F-04 root delete | Any dev | Sprint 4 |
| 🟡 P3 | DUP-08 | Reorganize `FakeRepositories` test files by domain | Test dev | Sprint 5 |
| 🟡 P3 | DUP-10 | Add `androidx-work` version ref to `[versions]` block in `libs.versions.toml` | Any dev | Sprint 4 |
| NC | DUP-09 | Clarify `PosSearchBar` intent — confirm wrapper vs reimplementation | Feature dev | Sprint 4 review |

---

## CUMULATIVE AUDIT STATUS

| Phase | New Findings | Closed from Prior Phase | Net Open |
|-------|-------------|------------------------|----------|
| Phase 1 | 11 | 0 | 11 |
| Phase 2 | 10 | 0 | 21 |
| **Phase 3** | **17** | **0** | **38** |

**Zero findings have been closed across all three phases.**

The most acute Phase 3 risk is DC-03 + DUP-05: the `SecurePreferences` key constant mismatch between the data-layer stub and the security module. If Sprint 6 repository implementations begin writing auth tokens to `SecurePreferences` using the data-stub key names, and Sprint 8 subsequently switches to the security module's `SecurePreferences`, every token written in Sprint 6–7 will become unreadable in Sprint 8+, silently invalidating all user sessions on upgrade. This requires an architectural decision and a key migration utility before any production data is persisted to `SecurePreferences`.

---

*End of Phase 3 Audit — ZyntaPOS ZENTA-PHASE3-CONSISTENCY-v1.0*
