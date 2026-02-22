# ZyntaPOS — Final Synthesis | Step 2: Deduplicated & Merged Findings

> **Auditor:** Senior KMP Architect (AI-assisted)
> **Date:** 2026-02-22
> **Inputs:** Phase 1 (v3), Phase 2 (v3), Phase 3 (v2.1), Phase 4 (v1.0), Step 1 Mismatch Report
> **Scope:** Merge all findings from all 4 phases into a single deduplicated list, grouped by component/module

---

## LEGEND

| Symbol | Meaning |
|--------|---------|
| 🔴 | CRITICAL — blocks testing/deployment |
| 🟠 | MEDIUM — incorrect behaviour or misleading docs |
| 🟡 | LOW — cosmetic, hygiene, minor doc drift |
| ✅ | CLOSED — resolved during audit lifecycle |
| 🔀 | Cross-phase mismatch resolved in Step 1 |

---

## PART A — CLOSED FINDINGS (Resolved During Audit Lifecycle)

These were found in earlier phases and confirmed **closed** in subsequent phases. Listed for traceability only.

| Merged ID | Original IDs | Finding | Closed By |
|-----------|-------------|---------|-----------|
| CLOSED-01 | P2-01 | ViewModels had undeclared `:composeApp:core` transitive dep | Phase 3: all `build.gradle.kts` now declare `implementation(project(":composeApp:core"))` |
| CLOSED-02 | P2-02 | `ReportsViewModel` + `SettingsViewModel` extended raw `ViewModel()` | Phase 3: both now extend `BaseViewModel<…>` |
| CLOSED-03 | P2-03 | `:shared:hal` undeclared in `settings/build.gradle.kts` | Phase 3: `implementation(project(":shared:hal"))` present |
| CLOSED-04 | P2-04 | `SettingsViewModel` injected repos with no Koin bindings (crash risk) | Phase 3: all 14 repo impls bound in `DataModule.kt` |
| CLOSED-05 | P2-05 | 4 missing repository implementations | Phase 3: `AuditRepositoryImpl`, `UserRepositoryImpl`, `TaxGroupRepositoryImpl`, `UnitGroupRepositoryImpl` created |
| CLOSED-06 | P2-06 | No SQLDelight schemas for TaxGroup/UnitOfMeasure | Phase 3: `tax_groups.sq`, `units_of_measure.sq` confirmed |
| CLOSED-07 | P2-09 | `compose-multiplatform.xml` boilerplate left in repo | Phase 3: `drawable/` dir emptied |
| CLOSED-08 | P2-10 | `shared/data/local/security/` stubs in wrong module | Phase 3: stubs deleted; replaced by domain port/adapter pattern |
| CLOSED-09 | F-01 | Zombie `BaseViewModel` in `shared/core/.../mvi/` | Phase 3: file deleted |
| CLOSED-10 | F-02 | `PrintReceiptUseCase` placed in feature layer | Phase 3: moved to `shared/domain/.../usecase/pos/` |
| CLOSED-11 | F-03 | Root-level `BarcodeScanner.kt` duplicating `scanner/BarcodeScanner.kt` | Phase 3: root-level file deleted |
| CLOSED-12 | F-04 | Root-level `SecurityAuditLogger.kt` duplicating `audit/` | Phase 3: root-level file deleted |
| CLOSED-13 | F-09 | `settings.gradle.kts` header comment "22 modules" stale | Phase 3-v2.1: now reads "23 modules" |
| CLOSED-14 | DUP-01 | `PosSearchBar.kt` vs `ZyntaSearchBar.kt` | Phase 3: confirmed clean thin wrapper, not a duplication |
| CLOSED-15 | DUP-02 | `ProductFormValidator.kt` duplicating domain `ProductValidator.kt` | Phase 3: feature-layer validator deleted |
| CLOSED-16 | DUP-03 | `PasswordHasher`/`SecurePreferences` parallel implementations | Phase 3 (MERGED-F3): stubs deleted; hexagonal port/adapter implemented |
| CLOSED-17 | DUP-04 | `FakeRepositories` test file fragmentation | Phase 3: reorganised as `FakeAuth/Inventory/Pos/SharedRepositories.kt` |
| CLOSED-18 | DUP-05 | Hardcoded `androidx-work-runtime` version | Phase 3: `version.ref` added to `libs.versions.toml` |
| CLOSED-19 | P2-08 🔀 | `:composeApp:core` absent from `Master_plan.md §4.1` | **Step 1 Mismatch #6**: Phase 4 NC-03 verified M21 IS in §4.1. Stale carry-forward from v2 audit. |

**Total closed: 19 findings** (including all 🔴 HIGH severity items from the v2 audit era)

---

## PART B — OPEN FINDINGS (Deduplicated & Merged)

Findings are grouped by component/module. Where the same issue was found in multiple phases, the entries are merged with combined context. Per synthesis rules, the **higher severity** is used when phases disagree.

---

### GROUP 1: `:shared:security` — SecurityModule

**3 findings | 1 🔴 CRITICAL, 1 🟡 LOW, 1 🟡 LOW**

#### MERGED-G1.1 — `named("deviceId")` Has Zero Providers — Guaranteed Startup Crash
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-04 + Phase 4 AV-04 carry-forward |
| **Severity** | 🔴 **P0 CRITICAL** |
| **Type** | Runtime Risk |
| **File** | `shared/security/src/commonMain/.../security/di/SecurityModule.kt` |
| **Evidence** | `SecurityAuditLogger(deviceId = get(named("deviceId")))` — Koin requires a `String` with qualifier `"deviceId"` at singleton-creation time. Phase 3 continuation scan checked ALL 23 module DI files, both entry points (`ZyntaApplication.kt`, `main.kt`), and `App.kt` — **zero providers found**. `securityModule` loads at Tier 2, before any platform module. |
| **Impact** | `NoBeanDefFoundException` on first app launch on all platforms — blocks ALL testing and deployment. |
| **Recommendation** | Add `single(named("deviceId")) { ... }` to `AndroidDataModule.kt` (use `Settings.Secure.ANDROID_ID` + UUID fallback) and `DesktopDataModule.kt` (UUID persisted to config file). Document in `Master_plan.md §4.2` as a required precondition for `securityModule`. |

#### MERGED-G1.2 — Bare `single { PasswordHasher }` Koin Binding (Possible Zero Consumers)
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-03 |
| **Severity** | 🟡 LOW |
| **Type** | Dead Code |
| **File** | `shared/security/src/commonMain/.../security/di/SecurityModule.kt` |
| **Evidence** | `single { PasswordHasher }` exposes the raw `expect object` alongside the correct `single<PasswordHashPort> { PasswordHasherAdapter() }`. `PinManager` calls `PasswordHasher` directly (not via Koin injection). This binding allows developers to bypass the domain port. |
| **Recommendation** | Grep for `get<PasswordHasher>()` and `inject<PasswordHasher>()`. If zero Koin consumers, remove `single { PasswordHasher }`. Retain only the port-based binding. |

#### MERGED-G1.3 — `named("deviceId")` Prerequisite Not Documented
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-04 (documentation aspect) |
| **Severity** | 🟡 LOW (after G1.1 is fixed) |
| **Type** | Doc Gap |
| **File** | `Master_plan.md §4.2` |
| **Recommendation** | After implementing G1.1, add to `Master_plan.md §4.2`: *"Platform modules must provide `String named("deviceId")` before `securityModule` loads."* |

---

### GROUP 2: `:composeApp:feature:pos` — PrinterManagerReceiptAdapter

**1 finding | 1 🟠 MEDIUM**

#### MERGED-G2.1 — Feature Layer Imports Security Infrastructure Class
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-01 + Phase 4 AV-04 carry-forward + Phase 2 M09 dep analysis |
| **Severity** | 🟠 MEDIUM |
| **Type** | Architectural Violation |
| **File** | `composeApp/feature/pos/src/commonMain/.../pos/printer/PrinterManagerReceiptAdapter.kt` |
| **Evidence** | `import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger` — This is the **only remaining cross-boundary infrastructure import** in the feature layer. The sole usage is `auditLogger.logReceiptPrint(orderId, userId)`, which is a domain-level audit event achievable via `AuditRepository.log(AuditEntry(...))`. |
| **Cross-phase note** | Phase 2 validated the `:feature:pos` → `:shared:security` (M05) dependency as matching the Master Plan. Phase 3 identified it as architecturally wrong. Per Step 1 Mismatch #8: Phase 3 takes precedence — the Master Plan itself is incorrect to list this dependency. |
| **Recommendation** | Replace `SecurityAuditLogger` injection with `AuditRepository`. Remove `implementation(project(":shared:security"))` from `pos/build.gradle.kts`. Update Master Plan §4.1 M09 deps to remove M05. |

---

### GROUP 3: `:composeApp:feature:register` + `:composeApp:feature:pos` + `:composeApp:feature:inventory` — Currency Formatting Duplication

**1 finding | 1 🟠 MEDIUM**

#### MERGED-G3.1 — 4 Private Currency Formatters Bypass `CurrencyFormatter`
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-05 |
| **Severity** | 🟠 MEDIUM |
| **Type** | Logic Duplication |
| **Files** | `feature/register/.../CloseRegisterScreen.kt:501` — `formatCurrency()` |
| | `feature/register/.../ZReportScreen.kt:434` — `formatZCurrency()` (byte-for-byte identical to above) |
| | `feature/pos/.../ProductGridSection.kt:58` — `formatPrice()` |
| | `feature/inventory/.../ProductListScreen.kt:394` — `formatPrice()` (adds `"LKR "` prefix) |
| **Canonical** | `shared/core/src/commonMain/.../core/utils/CurrencyFormatter.kt` — locale-aware, `HALF_UP` rounding, configurable symbol. Already used in `CashPaymentPanel.kt`, `CartItemList.kt`, `OrderDiscountDialog.kt` within the same `:feature:pos` module. |
| **Risk** | `CloseRegisterScreen.formatCurrency` uses `(abs - int) * 100` floating-point math which can produce `"2.499999"` for amounts like `2.50`. Reconciliation totals on Z-reports could show incorrect values. |
| **Recommendation** | Delete all 4 private functions. Add `CurrencyFormatter` to each ViewModel's state and pre-format all monetary fields. Priority: `CloseRegisterScreen` + `ZReportScreen` (financial reconciliation accuracy). |

---

### GROUP 4: `:composeApp:feature:inventory` — UI Component Duplication

**1 finding | 1 🟡 LOW**

#### MERGED-G4.1 — 4 Private `*EmptyState` Composables Bypass `ZyntaEmptyState`
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-06 |
| **Severity** | 🟡 LOW |
| **Type** | UI Duplication |
| **Files** | `CategoryListScreen.kt:306` — `CategoryEmptyState(onAdd)` |
| | `SupplierListScreen.kt:276` — `SupplierEmptyState()` |
| | `UnitManagementScreen.kt:352` — `UnitEmptyState(onAdd, modifier)` |
| | `TaxGroupScreen.kt:357` — `TaxGroupEmptyState(modifier, onAdd)` |
| **Canonical** | `composeApp/designsystem/.../components/ZyntaEmptyState.kt` — already used by `:feature:pos` and `:feature:reports`. |
| **Recommendation** | Replace all 4 with `ZyntaEmptyState(icon, title, subtitle, ctaLabel, onCtaClick)` calls. Ensures consistent spacing tokens and `ZyntaButton` styling. |

---

### GROUP 5: Cross-Feature — Loading UX Inconsistency

**1 finding | 1 🟡 LOW**

#### MERGED-G5.1 — 17 Raw `CircularProgressIndicator` vs 2 `ZyntaLoadingOverlay` Usages
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-07 |
| **Severity** | 🟡 LOW |
| **Type** | UX Inconsistency |
| **Locations** | 17 screens across `auth`, `register`, `pos`, `inventory` use raw `CircularProgressIndicator()`. Only `reports/SalesReportScreen` and `reports/StockReportScreen` use `ZyntaLoadingOverlay`. |
| **Recommendation** | Audit the 17 usages: ~8 full-screen loading states should use `ZyntaLoadingOverlay`; ~9 inline/button-embedded spinners are appropriate as raw `CircularProgressIndicator`. Schedule for UX sprint. |

---

### GROUP 6: `Master_plan.md §4.1` — Dependency Table Inaccuracies

**1 merged finding (7 sub-items) | 🟠 MEDIUM**

#### MERGED-G6.1 — Master Plan Dependency Table Has 8+ Documentation Errors
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 2 F1, F2, F3, F4, F5, F6, P2-07 |
| **Severity** | 🟠 MEDIUM |
| **Type** | Doc↔Code Mismatch |
| **File** | `docs/plans/Master_plan.md §4.1` |
| **Sub-findings** | |

| Sub-ID | Issue | Correct Value |
|--------|-------|---------------|
| F1 | M13-M17, M19-M20 (scaffolds) list M03 (`:shared:data`) as dependency. Architecture Note below table explicitly forbids this. | **Remove M03** — scaffolds have no `:shared:data` compile dep. Runtime wiring is via `DataModule`. |
| F2 | M04 (`:shared:hal`) listed as depending on M01 only. | **Add M02** — HAL needs domain printer port interfaces. Actual: M01, M02. |
| F2 | M05 (`:shared:security`) listed as depending on M01 only. | **Add M02** — Security needs domain port interfaces (`PasswordHashPort`, `SecureStoragePort`). Actual: M01, M02. |
| F3 | M21 (`:composeApp:core`) listed as depending on M02 (`:shared:domain`). | **Remove M02** — Code has zero project dependencies (only `lifecycle-viewmodel` + coroutines). |
| F4 | M11 (`:feature:register`) and M12 (`:feature:reports`) missing M04 (`:shared:hal`) dependency. | **Add M04** — both use HAL for printer adapters (`ZReportPrinterAdapter`, `ReportPrinterAdapter`). |
| F5 | M09 (`:feature:pos`) missing M08 (`:feature:auth`) dependency. | **Add M08** — pos depends on auth for `RoleGuard` composable. |
| F6 | All feature modules explicitly declare M01 (`:shared:core`) via `implementation()` but Master Plan omits M01 from every feature's dep list. | **Either** add M01 to all feature dep lists, **or** remove the explicit `implementation(project(":shared:core"))` from feature modules (relying on transitive via M02). |
| P2-07 | `:composeApp:navigation` (M07) deps understated. | **Update** M07 dep list to match actual `build.gradle.kts`. |

| **Recommendation** | Single editing pass on `Master_plan.md §4.1` to correct all 8 sub-items simultaneously. |

---

### GROUP 7: `Master_plan.md` — Other Documentation Gaps

**3 findings | 1 🟠 MEDIUM, 2 🟡 LOW**

#### MERGED-G7.1 — §3.3 MVI Code Sample Matches Deleted Zombie API
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 DC-02 |
| **Severity** | 🟠 MEDIUM |
| **Type** | Doc↔Code Mismatch |
| **File** | `docs/plans/Master_plan.md §3.3` |
| **Evidence** | Doc shows `onIntent(intent)`, `setState { }`, `SharedFlow` for effects. Live `BaseViewModel` uses `dispatch(intent)`, `handleIntent(intent)` (suspend), `updateState { }`, `Channel<E>` (capacity=64). The deleted zombie in `shared/core` was the doc's reference. |
| **Recommendation** | Replace code sample with live API: `dispatch` → `handleIntent` → `updateState` → `Channel` effect pattern. All 6 active ViewModels already use the correct API. |

#### MERGED-G7.2 — §15.1 Tech Stack Versions Stale
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 DC-03 |
| **Severity** | 🟡 LOW |
| **Type** | Doc↔Code Mismatch |
| **File** | `docs/plans/Master_plan.md §15.1` |
| **Evidence** | Doc says `Kotlin 2.1+`, `Compose 1.7+`, `AGP 8.5+`. Actual: Kotlin 2.3.0, Compose 1.10.0, AGP 8.13.2. |
| **Recommendation** | Update §15.1 with exact pinned versions from `libs.versions.toml`. Remove `+` suffix notation. |

#### MERGED-G7.3 — §3.2 Source Tree Omits `:feature:media` and `:composeApp:core`
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 DC-04 |
| **Severity** | 🟡 LOW |
| **Type** | Doc Conflict (internal to Master Plan) |
| **File** | `docs/plans/Master_plan.md §3.2` |
| **Evidence** | §4.1 lists both as M20 and M21. `settings.gradle.kts` includes both. Code exists for both. But §3.2 source tree diagram omits them. |
| **Recommendation** | Add both modules to the §3.2 tree diagram. |

---

### GROUP 8: `Master_plan.md §3.1` — Source Set Naming

**1 finding | 1 🟡 LOW**

#### MERGED-G8.1 — Architecture Diagram Uses `desktopMain` but Code Uses `jvmMain`
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 2 F9 (recovered via Step 1 Mismatch #5 — was dropped from Phase 3/4 tracking) |
| **Severity** | 🟡 LOW |
| **Type** | Doc↔Code Mismatch |
| **File** | `docs/plans/Master_plan.md §3.1` |
| **Evidence** | Architecture diagram labels the desktop source set as `desktopMain`. All code throughout the codebase uses `jvmMain`. |
| **Recommendation** | Update §3.1 diagram label from `desktopMain` to `jvmMain`. |

---

### GROUP 9: Brand Naming — "Zenta" → "Zynta" Incomplete Rename

**1 merged finding (5 sub-items) | 🟠 MEDIUM**

#### MERGED-G9.1 — Residual "Zenta" / "ZentaPOS" References in Docs and Code
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 1 observation 3.5, Phase 2 F7+F8+F10, Phase 3 DC-01+DC-05 |
| **Severity** | 🟠 MEDIUM (per Step 1 Mismatch #7 — using higher severity) |
| **Type** | Brand Inconsistency (Doc + Code) |
| **Canonical** | Product name is `ZyntaPOS`, prefix is `Zynta`, package is `com.zyntasolutions.zyntapos`. All code uses `Zynta` correctly. |
| **Sub-findings** | |

| Sub-ID | Location | Issue | Fix |
|--------|----------|-------|-----|
| DC-01a | `docs/plans/UI_UX_Main_Plan.md` title + Doc ID | "ZentaPOS" / `ZENTA-UI-UX-PLAN-v1.0` | Search-replace "ZentaPOS" → "ZyntaPOS", "ZENTA-" → "ZYNTA-" |
| DC-01b | `docs/plans/ER_diagram.md` title + Doc ID | "ZentaPOS" / `ZENTA-ER-DIAGRAM-v1.0` | Same search-replace |
| DC-05 | `UI_UX_Main_Plan.md §3.3` | 15 component names use `Zenta` prefix (e.g., `ZentaButton`) | Bulk rename to `Zynta` prefix. Additionally fix `ZentaLoadingSkeleton` → `ZyntaLoadingOverlay` (semantic change, not just prefix). |
| F7 | `composeApp/designsystem/.../theme/ZyntaTheme.kt` + platform actuals | Function `zentaDynamicColorScheme()` | Rename to `zyntaDynamicColorScheme()` |
| F8 | `designsystem/build.gradle.kts`, `navigation/build.gradle.kts` | Comments reference "ZentaButton", "ZentaCard", "ZentaNavHost" | Update comments to "ZyntaButton", "ZyntaCard", "ZyntaNavHost" |
| F10 | `docs/plans/Master_plan.md` header | Document ID `ZENTA-MASTER-PLAN-v1.0` | Update to `ZYNTA-MASTER-PLAN-v1.0` |

| **Recommendation** | Batch execution: (1) Search-replace "ZentaPOS" → "ZyntaPOS" and "ZENTA-" → "ZYNTA-" in all 3 planning docs. (2) Rename `zentaDynamicColorScheme()` in code. (3) Update build.gradle.kts comments. (4) Fix LoadingSkeleton → LoadingOverlay in UI_UX doc. |

---

### GROUP 10: `shared/data/src/commonMain/.../di/DataModule.kt` — KDoc Error

**1 finding | 1 🟡 LOW**

#### MERGED-G10.1 — KDoc Misattributes `PasswordHashPort` Binding Location
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 3 NEW-02 |
| **Severity** | 🟡 LOW |
| **Type** | Doc↔Code Mismatch |
| **File** | `shared/data/src/commonMain/.../data/di/DataModule.kt` |
| **Evidence** | KDoc states "`PasswordHashPort` is bound HERE". The binding is actually in `SecurityModule.kt`. `DataModule.kt` only consumes it via `get()`. |
| **Recommendation** | Update KDoc: *"`PasswordHashPort` is PROVIDED by `SecurityModule` (`PasswordHasherAdapter`) and consumed here via `get()`. See `shared/security/.../di/SecurityModule.kt`."* |

---

### GROUP 11: `shared/security/` — Empty Scaffold Directories

**1 finding | 1 🟡 LOW**

#### MERGED-G11.1 — Empty `keystore/` and `token/` Directories Persist After ADR-004
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 2 F11 (recovered via Step 1 Mismatch #5 — was dropped from Phase 3/4 tracking) |
| **Severity** | 🟡 LOW |
| **Type** | Hygiene |
| **Files** | `shared/security/src/commonMain/.../security/keystore/` (empty) |
| | `shared/security/src/androidMain/.../security/keystore/` (empty) |
| | `shared/security/src/jvmMain/.../security/keystore/` (empty) |
| | `shared/security/src/commonMain/.../security/token/` (empty) |
| **Evidence** | ADR-004 removed `.gitkeep` files but left the empty directories. They serve no purpose and may confuse new developers into thinking they need to add code there. |
| **Recommendation** | Delete all 4 empty directories. |

---

### GROUP 12: `gradle/libs.versions.toml` — Unused Catalog Entries

**1 finding | 1 🟡 LOW**

#### MERGED-G12.1 — 8 Unused Library Entries in Version Catalog
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 4 BC-03 |
| **Severity** | 🟡 LOW |
| **Type** | Build Bloat |
| **File** | `gradle/libs.versions.toml` |
| **Entries** | `androidx-appcompat`, `androidx-espresso-core`, `androidx-testExt-junit`, `datastore-preferences-core`, `datastore-core-okio`, `kermit-crashlytics`, `coil-network-ktor`, `turbine` |
| **Recommendation** | Retain entries planned for near-term sprints with `# RESERVED FOR: <feature>` comments. Remove any without a concrete plan. |

---

### GROUP 13: Test Documentation Gaps

**2 findings | 2 🟡 LOW**

#### MERGED-G13.1 — `CategorySupplierTaxUseCasesTest.kt` Not Documented
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 2 F12 (recovered via Step 1 Mismatch #5) |
| **Severity** | 🟡 LOW |
| **Type** | Documentation Gap |
| **File** | `shared/domain/src/commonTest/.../CategorySupplierTaxUseCasesTest.kt` |
| **Evidence** | Test file exists in code but was not listed in Phase 1 tree or any doc. |
| **Recommendation** | Add to Phase 1 tree listing under `shared/domain/commonTest/`. |

#### MERGED-G13.2 — Phase 1 Tree Aggregated 6 POS Test Files as "PosUseCasesTests"
| Attribute | Value |
|-----------|-------|
| **Merged from** | Phase 2 F13 |
| **Severity** | 🟡 LOW |
| **Type** | Documentation Imprecision |
| **Files** | `AddItemToCartUseCaseTest.kt`, `CalculateOrderTotalsUseCaseTest.kt`, `CartManagementUseCasesTest.kt`, `DiscountUseCasesTest.kt`, `ProcessPaymentUseCaseTest.kt`, `VoidOrderUseCaseTest.kt` |
| **Recommendation** | Update Phase 1 tree to list all 6 individual test files instead of the aggregated label. |

---

## PART C — EXPECTED GAPS (Not Defects)

These represent intentional incomplete work aligned with the project's phased rollout.

| ID | Gap | Reason | Action |
|----|-----|--------|--------|
| G1 | 50 of 63 ER entities have no `.sq` files | Phase 2/3 feature modules not yet started | Track as Phase 2/3 readiness checklist |
| G2 | 7 scaffold feature modules have no nav routes | Intentional — routes will be added when modules are implemented | Add routes during respective sprint |
| G3 | `docs/api/`, `docs/architecture/`, `docs/compliance/` are empty scaffolds | Placeholder directories for future content | Populate during respective sprints |
| G4 | `PlaceholderPasswordHasher.kt` still in codebase | Intentional test utility — clearly marked with `⚠️ DO NOT USE IN PRODUCTION` KDoc | No action — properly documented |

---

## PART D — NOT AUDITED (Future Scope)

These components appeared in the Phase 1 tree but were not evaluated in any subsequent phase.

| Component | Why Not Audited | Risk |
|-----------|----------------|------|
| `.github/workflows/ci.yml` | CI pipeline outside audit scope | LOW — but should verify it tests all 23 modules |
| `CONTRIBUTING.md` | Dev process docs outside audit scope | LOW |
| `README.md` (root) | Project overview outside audit scope | LOW |
| `SyncEngine.kt` + `SyncWorker.kt` | Listed as "documented" but no deep correctness audit | MEDIUM — sync logic is architecturally critical |
| `DatabaseMigrations.kt` | Referenced but not deep-audited | LOW |
| `SecurePreferencesKeyMigration.kt` | Referenced in DUP-03 context but not independently audited | LOW |
| 13 DTOs in `shared/data/.../remote/dto/` | Phase 4 counted them but didn't verify field correctness | LOW |
| 9 `PLAN_*.md` + 1 `.docx` in `docs/plans/` | Never cross-checked for stale content | LOW — but `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` may have stale progress claims |

---

## DEDUPLICATION SUMMARY

| Category | Raw Findings (All Phases) | After Merge | Reduction |
|----------|--------------------------|-------------|-----------|
| Closed findings | 19 individual items | 19 (listed in Part A) | — |
| Runtime risk | 2 (P3 NEW-04 + P4 carry) | 1 (MERGED-G1.1) | -1 |
| Architectural violations | 2 (P3 NEW-01 + P4 carry) | 1 (MERGED-G2.1) | -1 |
| Dependency doc gaps | 8 (P2 F1-F6 + P2-07 + P2-08) | 1 mega-finding + 7 sub-items (MERGED-G6.1); P2-08 closed | -7 |
| Brand naming | 7 (P1 obs + P2 F7/F8/F10 + P3 DC-01/DC-05) | 1 mega-finding + 6 sub-items (MERGED-G9.1) | -6 |
| Code duplication | 3 (P3 NEW-05/06/07) | 3 (MERGED-G3.1, G4.1, G5.1) | 0 |
| Doc gaps (Master Plan) | 5 (DC-02/03/04 + P2 F9 + P2-07) | 4 (MERGED-G7.1/7.2/7.3 + G8.1) | -1 |
| Dead code / build bloat | 2 (P3 NEW-03 + P4 BC-03) | 2 (MERGED-G1.2, G12.1) | 0 |
| KDoc / comment errors | 1 (P3 NEW-02) | 1 (MERGED-G10.1) | 0 |
| Hygiene | 1 (P2 F11) | 1 (MERGED-G11.1) | 0 |
| Test doc gaps | 2 (P2 F12/F13) | 2 (MERGED-G13.1/13.2) | 0 |
| **TOTALS** | **52 raw items** | **19 closed + 18 open + 4 expected gaps** | **~30% dedup** |

### Open Findings by Severity (after deduplication)

| Severity | Count | Merged IDs |
|----------|-------|------------|
| 🔴 CRITICAL | 1 | G1.1 |
| 🟠 MEDIUM | 4 | G2.1, G3.1, G6.1, G7.1, G9.1 |
| 🟡 LOW | 13 | G1.2, G1.3, G4.1, G5.1, G7.2, G7.3, G8.1, G10.1, G11.1, G12.1, G13.1, G13.2 |
| **Total open** | **18** | |

---

*End of Step 2 — Deduplicated & Merged Findings*
*Ready for Step 3 (Final Consolidated Report)*
