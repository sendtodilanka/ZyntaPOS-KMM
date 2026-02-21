# ZyntaPOS — Phase 2 Alignment Audit Report
> **Doc ID:** ZENTA-PHASE2-ALIGNMENT-v1.0  
> **Auditor:** Senior KMP Architect  
> **Date:** 2026-02-21  
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`  
> **Prerequisite:** Phase 1 Discovery Report (ZENTA-PHASE1-DISCOVERY-v1.0)

---

## LEGEND

| Symbol | Meaning |
|--------|---------|
| ✅ | FOUND & MATCHES — doc claim confirmed in code |
| ⚠️ | PARTIAL MATCH — doc says X, code shows Y |
| ❌ | MISSING IN CODE — doc claims it; code doesn't have it |
| 🗑️ | STALE/ORPHAN — code has it; docs say it shouldn't |
| 🆕 | NEW FINDING — not caught in Phase 1 |

---

## 2A — FORWARD CHECK (Docs → Code)

### MODULE REGISTRY

| Check | Result |
|-------|--------|
| All 23 `include()` calls in `settings.gradle.kts` match Phase 1 registry | ✅ **FOUND & MATCHES** — 23 physical module directories confirmed |
| Package root `com.zyntasolutions.zyntapos` consistent across all source sets | ✅ **FOUND & MATCHES** — all source trees use canonical namespace |
| `:androidApp` → `androidApp/src/main/kotlin/.../MainActivity.kt` | ✅ **FOUND & MATCHES** |
| `:composeApp` → `composeApp/src/commonMain/.../App.kt` | ✅ **FOUND & MATCHES** |

---

### DOMAIN LAYER — `:shared:domain`

| Check | Result |
|-------|--------|
| `DomainModule.kt` at `domain/DomainModule.kt` | ✅ **FOUND & MATCHES** |
| 25 domain models in `domain/model/` | ✅ **FOUND & MATCHES** — all 25 files present |
| 14 repository interfaces in `domain/repository/` | ✅ **FOUND & MATCHES** — all 14 present: `AuditRepository`, `AuthRepository`, `CategoryRepository`, `CustomerRepository`, `OrderRepository`, `ProductRepository`, `RegisterRepository`, `SettingsRepository`, `StockRepository`, `SupplierRepository`, `SyncRepository`, `TaxGroupRepository`, `UnitGroupRepository`, `UserRepository` |
| Use cases — `auth/` (4 classes) | ✅ **FOUND & MATCHES** — `CheckPermissionUseCase`, `LoginUseCase`, `LogoutUseCase`, `ValidatePinUseCase` |
| Use cases — `inventory/` (9 classes) | ✅ **FOUND & MATCHES** |
| Use cases — `pos/` (10 classes) | ✅ **FOUND & MATCHES** |
| Use cases — `register/` (4 classes) | ✅ **FOUND & MATCHES** |
| Use cases — `reports/` (3 classes) | ✅ **FOUND & MATCHES** |
| Use cases — `settings/` (2 classes) | ✅ **FOUND & MATCHES** |
| `validation/` — `PaymentValidator`, `StockValidator`, `TaxValidator` | ✅ **FOUND & MATCHES** |

---

### DATA LAYER — `:shared:data`

| Check | Result |
|-------|--------|
| `DataModule.kt` present | ✅ **FOUND & MATCHES** |
| `local/db/` — `DatabaseDriverFactory`, `DatabaseFactory`, `DatabaseKeyProvider`, `DatabaseMigrations` | ✅ **FOUND & MATCHES** |
| `local/mapper/` — 9 mappers | ✅ **FOUND & MATCHES** |
| `remote/api/` — `ApiClient`, `ApiService`, `KtorApiService` | ✅ **FOUND & MATCHES** |
| `remote/dto/` — `AuthDto`, `OrderDto`, `ProductDto`, `SyncDto` | ✅ **FOUND & MATCHES** |
| `sync/` — `NetworkMonitor`, `SyncEngine` | ✅ **FOUND & MATCHES** |
| `SyncEnqueuer.kt` | ✅ **FOUND & MATCHES** |
| SQLDelight schemas — 11 files in `sqldelight/.../db/` | ✅ **FOUND & MATCHES** |
| **Master_plan §3 — all 14 repository interfaces have concrete impls in `:shared:data`** | ❌ **MISSING IN CODE — 4 of 14 impls absent** |

**Repository Implementation Gap — EXPANDED from Phase 1 F-05:**

| Domain Interface | Impl in `:shared:data` | Registered in `DataModule.kt` |
|-----------------|------------------------|-------------------------------|
| `AuditRepository` | ❌ `AuditRepositoryImpl.kt` MISSING | ❌ No binding |
| `AuthRepository` | ✅ `AuthRepositoryImpl.kt` | ✅ |
| `CategoryRepository` | ✅ | ✅ |
| `CustomerRepository` | ✅ | ✅ |
| `OrderRepository` | ✅ | ✅ |
| `ProductRepository` | ✅ | ✅ |
| `RegisterRepository` | ✅ | ✅ |
| `SettingsRepository` | ✅ | ✅ |
| `StockRepository` | ✅ | ✅ |
| `SupplierRepository` | ✅ | ✅ |
| `SyncRepository` | ✅ | ✅ |
| `TaxGroupRepository` | ❌ `TaxGroupRepositoryImpl.kt` MISSING | ❌ No binding |
| `UnitGroupRepository` | ❌ `UnitGroupRepositoryImpl.kt` MISSING | ❌ No binding |
| `UserRepository` | ❌ `UserRepositoryImpl.kt` MISSING | ❌ No binding |

> **Phase 1 F-05 said 2 missing; Phase 2 confirms 4 missing.** `AuditRepositoryImpl` and `UserRepositoryImpl` were also missed.  
> **Recommendation:** Create all four impls. Register all in `DataModule.kt`. For `UserRepositoryImpl`, note that `SettingsViewModel` already injects `UserRepository` — runtime `NoBeanDefFoundException` is imminent.

| Check | Result |
|-------|--------|
| SQLDelight schema for `TaxGroupRepository` domain entity | ❌ **MISSING** — No `tax_groups.sq` in `sqldelight/.../db/`. `TaxGroup.kt` domain model exists; no DB table defined |
| SQLDelight schema for `UnitGroupRepository` domain entity | ❌ **MISSING** — No `units_of_measure.sq`. `UnitOfMeasure.kt` model exists; no DB table |

> **Recommendation:** Create `tax_groups.sq` and `units_of_measure.sq` in `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/`. Decide whether they should be in `categories.sq` or `products.sq` as documented in F-05 (NEEDS CLARIFICATION).

---

### INFRASTRUCTURE — `:shared:hal`

| Check | Result |
|-------|--------|
| `hal/printer/` — 6 classes in commonMain | ✅ **FOUND & MATCHES** |
| `hal/scanner/BarcodeScanner.kt` + `ScanResult.kt` | ✅ **FOUND & MATCHES** |
| `hal/di/HalModule.kt` | ✅ **FOUND & MATCHES** |
| androidMain printer + scanner actuals | ✅ **FOUND & MATCHES** |
| jvmMain printer + scanner actuals | ✅ **FOUND & MATCHES** |
| **F-03 resolved: root-level `hal/BarcodeScanner.kt` deleted** | ❌ **STILL PRESENT** — `shared/hal/src/commonMain/.../hal/BarcodeScanner.kt` still exists alongside `hal/scanner/BarcodeScanner.kt` |

---

### INFRASTRUCTURE — `:shared:security`

| Check | Result |
|-------|--------|
| `security/auth/` — 5 classes | ✅ **FOUND & MATCHES** |
| `security/crypto/` — `DatabaseKeyManager`, `EncryptionManager` | ✅ **FOUND & MATCHES** |
| `security/prefs/` — `SecurePreferences`, `TokenStorage` | ✅ **FOUND & MATCHES** |
| `security/rbac/RbacEngine.kt` | ✅ **FOUND & MATCHES** |
| `security/di/SecurityModule.kt` | ✅ **FOUND & MATCHES** |
| **F-04 resolved: root-level `SecurityAuditLogger.kt` deleted** | ❌ **STILL PRESENT** — `shared/security/src/commonMain/.../security/SecurityAuditLogger.kt` still exists alongside `security/audit/SecurityAuditLogger.kt` |
| `keystore/` and `token/` empty (F-07, F-08) | ❌ **STILL UNRESOLVED** — both dirs contain only `.gitkeep` |

---

### PRESENTATION LAYER — `:composeApp:designsystem`

| Check | Result |
|-------|--------|
| 15 components in `designsystem/components/` | ✅ **FOUND & MATCHES** |
| 4 layout files in `designsystem/layouts/` | ✅ **FOUND & MATCHES** |
| Theme (`ZentaColors`, `ZentaShapes`, `ZentaTheme`, `ZentaTypography`) | ✅ **FOUND & MATCHES** |
| Tokens (`ZentaElevation`, `ZentaSpacing`) | ✅ **FOUND & MATCHES** |
| Platform actuals (androidMain + jvmMain theme + WindowSizeClassHelper) | ✅ **FOUND & MATCHES** |

---

### PRESENTATION LAYER — `:composeApp:navigation`

| Check | Result |
|-------|--------|
| Navigation classes: `AuthNavGraph`, `MainNavGraph`, `MainNavScreens`, `NavigationController`, `NavigationItems`, `NavigationModule`, `ZentaNavGraph`, `ZentaRoute` | ✅ **FOUND & MATCHES** |
| **Master_plan §4.1 M07: deps = [M06 only]** | ⚠️ **PARTIAL MATCH** — `navigation/build.gradle.kts` adds `:shared:domain` (M02) and `:shared:security` (M05). Doc says only M06. Undocumented but architecturally justifiable for RBAC route filtering |

> **Recommendation:** Update Master_plan §4.1 M07 dependency column to `[M02, M05, M06]`. Add a comment explaining RBAC route gating as the driver.

---

### FEATURE MODULES — `:composeApp:core`

| Check | Result |
|-------|--------|
| `composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt` | ✅ **FOUND** |
| **Master_plan §4.1 Module Registry lists `:composeApp:core` with an M-number** | ❌ **MISSING IN DOCS** — `:composeApp:core` has no entry in the M01–M20 table. It is the de-facto canonical BaseViewModel module (all feature ViewModels import from it) but is undocumented in the registry |

> **Recommendation:** Add `M0X | :composeApp:core | Presentation Infrastructure | M01 | 1` to Master_plan §4.1 and show it in the dependency graph between `:shared:*` and feature modules.

---

### FEATURE MODULES — F-01 BASEVIEWMODEL DIVERGENCE (EXPANDED)

**Phase 1 flagged two `BaseViewModel` files. Phase 2 reveals a deeper 3-pattern MVI divergence:**

| Module | ViewModel | BaseViewModel used | Build dep declared |
|--------|-----------|-------------------|-------------------|
| `:feature:auth` | `AuthViewModel` | `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` (`:composeApp:core`) | ✅ `:composeApp:core` declared in `build.gradle.kts` |
| `:feature:pos` | `PosViewModel` | `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` (`:composeApp:core`) | ❌ `:composeApp:core` NOT in `build.gradle.kts` |
| `:feature:inventory` | `InventoryViewModel` | `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` (`:composeApp:core`) | ❌ `:composeApp:core` NOT in `build.gradle.kts` |
| `:feature:register` | `RegisterViewModel` | `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel` (`:composeApp:core`) | ❌ `:composeApp:core` NOT in `build.gradle.kts` |
| `:feature:reports` | `ReportsViewModel` | `androidx.lifecycle.ViewModel` directly — **bypasses BaseViewModel entirely** | — |
| `:feature:settings` | `SettingsViewModel` | `androidx.lifecycle.ViewModel` directly — **bypasses BaseViewModel entirely** | — |
| `:shared:core` | `BaseViewModel.kt` | `AutoCloseable`-based, NOT used by any feature ViewModel | 🗑️ ZOMBIE |

> **Doc says:** Master_plan §4.1 + PLAN_PHASE1.md §8 → single `BaseViewModel<Intent,State,SideEffect>` in `:composeApp:core`, consumed by every feature.  
> **Code shows:** Three divergent patterns. `reports` and `settings` do not use BaseViewModel at all, implementing their own MVI-like plumbing with `MutableStateFlow` + `MutableSharedFlow` directly on `ViewModel`. The `shared/core` BaseViewModel is never imported by any feature module — it is a zombie.

> **Recommendation:**  
> 1. Delete `shared/core/src/commonMain/.../core/mvi/BaseViewModel.kt` (confirms F-01).  
> 2. Add `:composeApp:core` to `pos`, `inventory`, and `register` `build.gradle.kts` commonMain deps.  
> 3. Migrate `ReportsViewModel` and `SettingsViewModel` to extend `ui.core.mvi.BaseViewModel`. This enforces the single MVI contract documented in Master_plan §3.3.

---

### FEATURE MODULE — `:composeApp:feature:settings` — UNDECLARED HAL DEPENDENCY 🆕

| Check | Result |
|-------|--------|
| **Master_plan M18 deps = [M02, M03, M04, M05, M06]** | ⚠️ **PARTIAL MATCH** |
| `SettingsViewModel` imports `com.zyntasolutions.zyntapos.hal.printer.PaperWidth` | ❌ `:shared:hal` (M04) **NOT declared** in `settings/build.gradle.kts` |
| `settings/build.gradle.kts` includes `:shared:data` (M03) | ❌ **MISSING** |
| `settings/build.gradle.kts` includes `:shared:security` (M05) | ❌ **MISSING** |

> **Risk:** `SettingsViewModel` compiles only if `:shared:hal` is available as an undeclared transitive dependency through another module. This is fragile — any future dependency graph change can break the build silently. Additionally, `SettingsViewModel` injects `TaxGroupRepository` and `UserRepository` which have no Koin bindings → guaranteed runtime crash.

> **Recommendation:** Add to `settings/build.gradle.kts` commonMain deps:  
> ```kotlin  
> implementation(project(":shared:hal"))       // M04 — PaperWidth, PrinterManager  
> implementation(project(":shared:data"))      // M03 — repository impls via Koin  
> implementation(project(":shared:security"))  // M05 — RBAC checks  
> implementation(project(":composeApp:core"))  // BaseViewModel  
> ```

---

### FEATURE MODULES — DOCUMENTED DEPENDENCY vs ACTUAL BUILD GRAPH

| Module | Doc deps (Master_plan §4.1) | Missing from `build.gradle.kts` |
|--------|----------------------------|----------------------------------|
| M08 `:feature:auth` | M02, M03, M05, M06 | M03 (`:shared:data`), M05 (`:shared:security`) |
| M09 `:feature:pos` | M02, M03, M04, M06 | M03 (`:shared:data`), `:composeApp:core` |
| M10 `:feature:inventory` | M02, M03, M06 | M03 (`:shared:data`), `:composeApp:core` |
| M11 `:feature:register` | M02, M03, M04, M06 | M03 (`:shared:data`), M04 (`:shared:hal`), `:composeApp:core` |
| M12 `:feature:reports` | M02, M03, M06 | M03 (`:shared:data`), `:composeApp:core` |
| M18 `:feature:settings` | M02, M03, M04, M05, M06 | M03, M04, M05, `:composeApp:core` |

> **Note:** Missing M03 (`:shared:data`) across all features may be intentional deferral to their implementation sprints (Sprint 4-24 not started). NEEDS CLARIFICATION: determine if `:composeApp:core` and `:shared:data` should be added now (scaffold) or deferred per-sprint. **However, `settings` and `pos` need their missing deps immediately because their ViewModels already inject repositories.**

---

### PHASE 1 FINDINGS — STATUS TRACKING

| Finding | Severity | Status |
|---------|----------|--------|
| F-01: Dual `BaseViewModel` | 🔴 HIGH | ❌ UNRESOLVED — expanded to 3-pattern divergence |
| F-02: `PrintReceiptUseCase` in feature layer | 🟠 MEDIUM | ❌ UNRESOLVED — still at `feature/pos/PrintReceiptUseCase.kt` |
| F-03: Duplicate `BarcodeScanner.kt` | 🟠 MEDIUM | ❌ UNRESOLVED — both files still present |
| F-04: Duplicate `SecurityAuditLogger.kt` | 🟠 MEDIUM | ❌ UNRESOLVED — both files still present |
| F-05: Missing 2 repository impls | 🟠 MEDIUM | ❌ EXPANDED — Phase 2 confirms 4 missing impls total |
| F-06: Dependency version drift | 🟡 LOW | ✅ Acknowledged — no change required pre-Sprint 4 |
| F-07/F-08: Empty `keystore/` `token/` dirs | 🟡 LOW | ❌ UNRESOLVED — still `.gitkeep` only |
| F-09: Module count 22 vs 23 | 🟡 LOW | ❌ UNRESOLVED — `settings.gradle.kts` header still says 22 |
| F-10: `PLAN_MISMATCH_FIX` not marked superseded | 🟡 LOW | NEEDS VERIFICATION |
| F-11: `ZyntaPOS_Junior_Developer_Guide.docx` unaudited | NC | ❌ UNRESOLVED |

---

## 2B — REVERSE CHECK (Code → Docs)

### ALL MODULES

| Module | Documentation Status |
|--------|---------------------|
| `:androidApp` | ✅ **DOCUMENTED** — Phase 1 MODULE REGISTRY #1 |
| `:composeApp` | ✅ **DOCUMENTED** — Phase 1 MODULE REGISTRY #2 |
| `:shared:core` | ✅ **DOCUMENTED** — M01 |
| `:shared:domain` | ✅ **DOCUMENTED** — M02 |
| `:shared:data` | ✅ **DOCUMENTED** — M03 |
| `:shared:hal` | ✅ **DOCUMENTED** — M04 |
| `:shared:security` | ✅ **DOCUMENTED** — M05 |
| `:composeApp:designsystem` | ✅ **DOCUMENTED** — M06 |
| `:composeApp:navigation` | ✅ **DOCUMENTED** — M07 (added by Phase 1 F-09 finding) |
| `:composeApp:core` | ❌ **UNDOCUMENTED** — Exists in `settings.gradle.kts` as module #8, provides canonical `BaseViewModel`, imported by 4 feature ViewModels. Not listed in Master_plan §4.1 M01–M20 registry. Not in §4.2 dependency graph. |
| `:composeApp:feature:auth` | ✅ **DOCUMENTED** — M08 |
| `:composeApp:feature:pos` | ✅ **DOCUMENTED** — M09 |
| `:composeApp:feature:inventory` | ✅ **DOCUMENTED** — M10 |
| `:composeApp:feature:register` | ✅ **DOCUMENTED** — M11 |
| `:composeApp:feature:reports` | ✅ **DOCUMENTED** — M12 |
| `:composeApp:feature:settings` | ✅ **DOCUMENTED** — M18 |
| `:composeApp:feature:customers` | ✅ **DOCUMENTED** — M13 (Phase 2 stub) |
| `:composeApp:feature:coupons` | ✅ **DOCUMENTED** — M14 (Phase 2 stub) |
| `:composeApp:feature:expenses` | ✅ **DOCUMENTED** — M16 (Phase 2 stub) |
| `:composeApp:feature:multistore` | ✅ **DOCUMENTED** — M15 (Phase 2 stub) |
| `:composeApp:feature:staff` | ✅ **DOCUMENTED** — M17 (Phase 3 stub) |
| `:composeApp:feature:admin` | ✅ **DOCUMENTED** — M19 (Phase 3 stub) |
| `:composeApp:feature:media` | ✅ **DOCUMENTED** — M20 (Phase 3 stub) |

---

### UNDOCUMENTED CODE ELEMENTS

#### `shared/data/src/commonMain/.../data/local/security/` 🆕
❌ **UNDOCUMENTED** — `InMemorySecurePreferences.kt`, `PasswordHasher.kt`, `PlaceholderPasswordHasher.kt`, `SecurePreferences.kt`

Master_plan §3.1 Data Layer architecture shows no `security/` sub-package within `:shared:data`. These are sprint-stub placeholders for the platform `PasswordHasher`/`SecurePreferences` expect/actual interfaces that will live in `:shared:security`. Having them in `:shared:data` creates a second undocumented security implementation path and is the reason `DataModule.kt` can provide `PasswordHasher` without depending on `:shared:security`.

> **Recommendation:** Document in Master_plan §3.1 that `:shared:data` contains stub security adapters for Sprint 1–7, scheduled to be replaced by `:shared:security` actuals in Sprint 8. Alternatively, move the stubs to `:shared:security` immediately if the module already has the necessary platform source sets (it does).

---

#### `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` 🆕
❌ **UNDOCUMENTED** — Default Compose Multiplatform project template asset. Not referenced in `DesignSystemModule.kt`, not used in any screen, not mentioned in `UI_UX_Main_Plan.md`.

> **Recommendation:** Delete this file. If a ZyntaPOS-branded splash vector is needed, add it to `:composeApp:designsystem` under `commonMain/composeResources/drawable/` and document in `UI_UX_Main_Plan.md`.

---

#### `shared/core/src/commonMain/.../core/mvi/BaseViewModel.kt` 🗑️ STALE/ORPHAN
🗑️ **STALE/ORPHAN** — This `AutoCloseable`-based BaseViewModel (package `com.zyntasolutions.zyntapos.core.mvi`) is not imported by any feature ViewModel. All feature ViewModels import from `ui.core.mvi.BaseViewModel` in `:composeApp:core` or extend AndroidX `ViewModel` directly. The `shared/core` copy has zero consumers and conflicts with the architectural canon.

> **Recommendation:** Delete `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/mvi/BaseViewModel.kt`. Also delete the MVI marker interfaces (`UiState`, `UiIntent`, `UiEffect`) from that file unless they are used elsewhere; verify with a project-wide `grep`.

---

#### Three-Pattern ViewModel Inconsistency 🆕
❌ **UNDOCUMENTED DIVERGENCE** — Code implements three different ViewModel patterns with no documentation of the chosen standard:

| Pattern | Package | Users | Compliant with Master_plan |
|---------|---------|-------|---------------------------|
| `ui.core.mvi.BaseViewModel` (AndroidX `ViewModel` + `Channel` effects) | `com.zyntasolutions.zyntapos.ui.core.mvi` | auth, pos, inventory, register | ✅ This is the documented standard |
| Raw `ViewModel` with `MutableSharedFlow` (no base class) | `androidx.lifecycle.ViewModel` | reports, settings | ❌ Undocumented deviation |
| `AutoCloseable` BaseViewModel (custom scope, `SharedFlow` effects) | `com.zyntasolutions.zyntapos.core.mvi` | None (zombie) | ❌ Orphan |

> **Recommendation:** Mandate `ui.core.mvi.BaseViewModel` in all feature ViewModels. Migrate `ReportsViewModel` and `SettingsViewModel` as Sprint 4–5 technical debt items. Add a linting rule or `ARCHITECTURE.md` ADR noting that direct `ViewModel` extension is prohibited.

---

## PHASE 2 NEW FINDINGS SUMMARY

| ID | Finding | Severity | Impact |
|----|---------|----------|--------|
| **P2-01** | `PosViewModel`, `InventoryViewModel`, `RegisterViewModel` import `:composeApp:core` `BaseViewModel` but do NOT declare `:composeApp:core` in their `build.gradle.kts`. Compilation depends on undeclared transitive access. | 🔴 HIGH | Build fragility; breaks on dependency graph changes |
| **P2-02** | `ReportsViewModel` and `SettingsViewModel` bypass `BaseViewModel` entirely, extending `ViewModel` directly. Three divergent MVI patterns in codebase. | 🔴 HIGH | MVI contract violation; inconsistent effect delivery (Channel vs SharedFlow vs nothing) |
| **P2-03** | `SettingsViewModel` imports `hal.printer.PaperWidth` but `settings/build.gradle.kts` does NOT declare `:shared:hal`. | 🔴 HIGH | Build failure risk; undeclared transitive dependency |
| **P2-04** | `SettingsViewModel` injects `TaxGroupRepository` + `UserRepository` with no Koin bindings registered (no impls exist). | 🔴 HIGH | Runtime `NoBeanDefFoundException` when Settings screen loads |
| **P2-05** | Phase 1 F-05 said 2 repo impls missing; Phase 2 finds 4 missing: `AuditRepositoryImpl`, `UserRepositoryImpl`, `TaxGroupRepositoryImpl`, `UnitGroupRepositoryImpl`. | 🟠 MEDIUM | Koin DI will throw at startup for any ViewModel depending on these |
| **P2-06** | No SQLDelight schema for `TaxGroup` or `UnitOfMeasure` domain entities. `tax_groups.sq` and `units_of_measure.sq` absent from `sqldelight/.../db/`. | 🟠 MEDIUM | Repository impls cannot be written without DB schema |
| **P2-07** | `:composeApp:navigation` actually depends on M02 + M05 + M06 but Master_plan M07 documents only M06. | 🟠 MEDIUM | Stale doc; actual deps wider than stated |
| **P2-08** | `:composeApp:core` is absent from Master_plan §4.1 module registry despite being the canonical BaseViewModel home and a direct dependency of 4 modules. | 🟠 MEDIUM | Future architects have no authoritative record of this module's role |
| **P2-09** | `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` — boilerplate template asset never cleaned up. | 🟡 LOW | Clutter; misleading default branding in shipped app |
| **P2-10** | `shared/data/local/security/` security stubs undocumented in Master_plan data layer architecture. | 🟡 LOW | Architectural confusion; two security paths |

---

## CONSOLIDATED ACTION TABLE (Phase 2 Priority Order)

| Priority | ID | Action | File(s) | Sprint |
|----------|----|--------|---------|--------|
| 🔴 P0 | P2-04 | Fix runtime crash: create `TaxGroupRepositoryImpl` + `UserRepositoryImpl`, register in `DataModule.kt` | `shared/data/src/.../repository/`, `DataModule.kt` | Immediate |
| 🔴 P0 | P2-03 | Add `:shared:hal` to `settings/build.gradle.kts` | `composeApp/feature/settings/build.gradle.kts` | Immediate |
| 🔴 P1 | P2-01 | Add `:composeApp:core` to `pos`, `inventory`, `register` `build.gradle.kts` | 3 `build.gradle.kts` files | Sprint 4 |
| 🔴 P1 | F-01 + P2-02 | Delete `shared/core/.../mvi/BaseViewModel.kt`; migrate `ReportsViewModel` + `SettingsViewModel` to `ui.core.mvi.BaseViewModel` | `shared/core/`, `feature/reports/`, `feature/settings/` | Sprint 4 |
| 🟠 P2 | P2-05 | Create `AuditRepositoryImpl` + `UserRepositoryImpl` (extends F-05) | `shared/data/src/.../repository/` | Sprint 5–6 |
| 🟠 P2 | P2-06 | Create `tax_groups.sq` + `units_of_measure.sq` | `shared/data/src/commonMain/sqldelight/.../db/` | Sprint 6 |
| 🟠 P2 | F-02 | Move `PrintReceiptUseCase` to `shared/domain/src/.../usecase/pos/` | `feature/pos/`, `shared/domain/` | Sprint 5 |
| 🟠 P2 | F-03 | Delete root-level `shared/hal/.../hal/BarcodeScanner.kt` | `shared/hal/src/commonMain/` | Sprint 4 |
| 🟠 P2 | F-04 | Delete root-level `shared/security/.../security/SecurityAuditLogger.kt` | `shared/security/src/commonMain/` | Sprint 4 |
| 🟠 P2 | P2-07 + P2-08 | Update Master_plan §4.1: add `:composeApp:core` M-number; update M07 deps to [M02, M05, M06] | `docs/plans/Master_plan.md` | Sprint 4 |
| 🟡 P3 | P2-09 | Delete `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` | `composeApp/src/` | Sprint 4 |
| 🟡 P3 | P2-10 | Document `shared/data/local/security/` stubs in Master_plan §7 or add a doc comment | `docs/plans/Master_plan.md` | Sprint 5 |
| 🟡 P3 | F-07 + F-08 | Resolve `keystore/` and `token/` placeholder directories (decide: scaffold or delete) | `shared/security/src/` | Sprint 8 |
| 🟡 P3 | F-09 | Update `settings.gradle.kts` header comment: "22 modules" → "23 modules" | `settings.gradle.kts` | Sprint 4 |
| NC | F-11 | Audit `ZyntaPOS_Junior_Developer_Guide.docx` for stale package/module names | `docs/plans/` | Sprint 4 |

---

## AUDIT VERDICT

**Phase 1 open findings: 11. Phase 2 new findings: 10. Zero findings closed.**  

The codebase is architecturally sound at the macro level — module boundaries, package naming, domain model completeness, and design system structure are all correct. The critical risks are concentrated in two areas:

**1. Imminent runtime failures** — `SettingsViewModel` injects unbound Koin dependencies (`TaxGroupRepository`, `UserRepository`) that will crash the app on the first settings screen navigation. This must be fixed before any integration testing.

**2. MVI contract fragmentation** — Three different ViewModel base patterns exist simultaneously. This will cause developer confusion, inconsistent effect delivery behavior, and increasing divergence as Sprint 4–24 features build on the wrong base. A team-wide ADR must be issued before Sprint 4 feature work begins.

All Phase 1 fixes (F-01 through F-04, F-09, F-10) remain unexecuted and are prerequisite to Sprint 4 start.
