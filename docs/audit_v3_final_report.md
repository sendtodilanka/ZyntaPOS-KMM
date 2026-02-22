```
══════════════════════════════════════════════════════
        ZENTAPOS — FINAL AUDIT REPORT
══════════════════════════════════════════════════════
Audit Date: 2026-02-22
Project:    /Users/dilanka/Developer/StudioProjects/ZyntaPOS/
Phases:     4 (Structure, Alignment, Consistency, Integrity)
Auditor:    Senior KMP Architect (AI-assisted)
Synthesis:  3-Step Cross-Phase Comparison + Deduplication

══════════════════════════════════════════════════════
SECTION 1: CROSS-PHASE MISMATCHES
══════════════════════════════════════════════════════

10 mismatches detected between the 4 phase outputs.
1 HIGH, 3 MEDIUM, 6 LOW — all resolved with verdicts below.

──────────────────────────────────────────────────────

🔀 MISMATCH #1 — Module Count Contradiction
   Phase 1 says: 18 Gradle modules observed
   Phase 2 says: 23 modules in settings.gradle.kts
   Phase 4 says: 23/23 verified
   Verdict: Phase 1 was incorrect (undercounted by 5).
            Phase 2/4's count of 23 is definitive.
   Action:  Use 23 throughout final report. No code change.

🔀 MISMATCH #2 — Use Case Count Discrepancy
   Phase 1/2 say: 33 use cases (auth:4, inv:9, pos:11, reg:4, rep:3, set:2)
   Phase 4 says:  32 use cases (auth:5, inv:9, pos:11, reg:4, rep:3)
   Codebase:      33 files confirmed. PrintTestPageUseCase is in usecase/settings/,
                  not usecase/auth/. SaveUserUseCase exists but was omitted by Phase 4.
   Verdict: Phase 4 is incorrect. Correct total is 33.
   Action:  Final report uses 33 use cases with settings(2).

🔀 MISMATCH #3 — Domain Model Count
   Phase 1/2 say: 26 .kt files (Phase 2 lists 27 names but claims 26)
   Phase 4 says:  20 entities + 8 enums = 28 items
   Codebase:      27 .kt files. Some files contain both data class + nested enum.
   Verdict: All three counts are wrong in different ways.
            Canonical: 27 model files.
   Action:  Final report uses 27 domain model files.

🔀 MISMATCH #4 — Phase 4 Internal Arithmetic Error
   Phase 4 NC-01 header says: "194 classes audited across 9 categories"
   Phase 4 NC-01 table totals: 186 classes across 12 categories
   Verdict: Header is wrong. Table sum of 186 is correct.
   Action:  Final report uses 186 audited components.

🔀 MISMATCH #5 — Cross-Phase Tracking Break [HIGH]
   Phase 2 (v3) reports: Findings F1–F13, gaps G1–G4
   Phase 3 tracks:       P2-01 through P2-10, F-01 through F-04 (different IDs)
   Verdict: Phase 3 referenced findings from the v2 audit, not v3 Phase 2.
            13 v3 Phase 2 findings (F1–F13) were never carried forward.
            All 13 have been recovered and merged into this final report.
   Action:  F1–F6 merged into MERGED-G6.1 (dep table).
            F7/F8/F10 merged into MERGED-G9.1 (brand naming).
            F9 recovered as MERGED-G8.1 (desktopMain vs jvmMain).
            F11 recovered as MERGED-G11.1 (empty dirs).
            F12/F13 recovered as MERGED-G13.1/G13.2 (test doc gaps).

🔀 MISMATCH #6 — NC-03 "Perfect Match" Contradicts P2-08 "Open"
   Phase 4 NC-03 says: 23/23 modules documented (M21 = :composeApp:core present)
   Phase 4 also carries: P2-08 as open (":composeApp:core absent from §4.1")
   Verdict: P2-08 is a stale carry-forward from the v2 audit era.
            NC-03 correctly verified M21 IS in §4.1.
   Action:  P2-08 closed. DC-04 (§3.2 tree diagram) remains open separately.

🔀 MISMATCH #7 — Severity Inconsistency: Brand Naming
   Phase 2 rates: F7 (zentaDynamicColorScheme) as "Cosmetic"
   Phase 3 rates: DC-01 (ZentaPOS in docs) as 🟠 MEDIUM
   Verdict: Same root cause, different severities. F7 is a public function
            name (not just a comment) — warrants MEDIUM.
   Action:  Per synthesis rules (use higher severity): F7 upgraded to 🟠 MEDIUM.
            F8/F10 remain 🟡 LOW (comments/doc IDs).

🔀 MISMATCH #8 — pos→security Dep: Phase 2 Validates, Phase 3 Condemns
   Phase 2 says: M09→M05 dependency matches Master Plan ✅
   Phase 3 says: This is an architectural violation (feature→infra) ❌
   Verdict: Both partially correct from their perspective. Phase 3 takes
            precedence — the Master Plan is itself wrong to list this dep.
   Action:  Fix NEW-01 (replace SecurityAuditLogger with AuditRepository),
            then update Master Plan to remove M05 from M09's dep list.

🔀 MISMATCH #9 — Phase 2 Findings F9/F11 Silently Dropped
   Phase 2 F9:  desktopMain vs jvmMain in Master Plan §3.1 — Cosmetic
   Phase 2 F11: Empty keystore/token dirs after ADR-004 — Hygiene
   Phase 3/4:   Neither mentioned
   Verdict: Dropped due to tracking break (Mismatch #5).
   Action:  Both re-added to final report at 🟡 LOW.

🔀 MISMATCH #10 — Phase 1 Components Never Evaluated
   Components: ci.yml, CONTRIBUTING.md, README.md, SyncEngine.kt,
              DatabaseMigrations.kt, 13 DTOs, 9 PLAN docs, 1 .docx guide
   Verdict: Coverage gaps (not errors). Audit scope was architecture,
            naming, and doc alignment — not CI or implementation correctness.
   Action:  Flagged as "NOT AUDITED — FUTURE SCOPE" in Section 2.

══════════════════════════════════════════════════════
SECTION 2: COMPLETE FINDINGS (Deduplicated)
══════════════════════════════════════════════════════

19 findings were CLOSED during the audit lifecycle.
18 findings remain OPEN after deduplication.
4 expected gaps acknowledged (not defects).

──────────────────────────────────────────────────────
2A. Alignment Issues (from Phase 1 & 2):
──────────────────────────────────────────────────────

  ✅ [MERGED-G6.1] Master Plan §4.1 dependency table has 8+ errors
     Severity: 🟠 MEDIUM
     Status: ✅ RESOLVED (2026-02-22)
     Source: Phase 2 F1, F2, F3, F4, F5, F6, P2-07
     Resolution: All 8 sub-issues corrected in single editing pass on Master_plan.md §4.1.

  ❌ [MERGED-G8.1] Master Plan §3.1 diagram uses "desktopMain", code uses "jvmMain"
     Severity: 🟡 LOW
     Source: Phase 2 F9 (recovered — was dropped from Phase 3/4 tracking)
     File: docs/plans/Master_plan.md §3.1
     Recommendation: Update diagram label from "desktopMain" to "jvmMain".

  🗑️ [CLOSED-19 / P2-08] :composeApp:core absent from Master Plan §4.1
     Status: CLOSED (stale carry-forward from v2 audit — Phase 4 NC-03
     confirmed M21 IS present in §4.1)

──────────────────────────────────────────────────────
2B. Documentation Conflicts (from Phase 3):
──────────────────────────────────────────────────────

  ⚠️ [MERGED-G9.1] Brand naming: residual "Zenta"/"ZentaPOS" across docs and code
     Severity: 🟠 MEDIUM
     Source: Phase 1 obs 3.5 + Phase 2 F7/F8/F10 + Phase 3 DC-01/DC-05
     Canonical: Product = ZyntaPOS, prefix = Zynta, package = com.zyntasolutions.zyntapos
     6 sub-items:
       DC-01a: UI_UX_Main_Plan.md title + Doc ID use "ZentaPOS" / "ZENTA-"
               → search-replace to "ZyntaPOS" / "ZYNTA-"
       DC-01b: ER_diagram.md title + Doc ID use "ZentaPOS" / "ZENTA-"
               → same search-replace
       DC-05:  UI_UX_Main_Plan.md §3.3 has 15 components with "Zenta" prefix
               → bulk rename to "Zynta"; also fix LoadingSkeleton → LoadingOverlay
       F7:     ZyntaTheme.kt function zentaDynamicColorScheme()
               → rename to zyntaDynamicColorScheme() in common + platform actuals
       F8:     designsystem/build.gradle.kts + navigation/build.gradle.kts comments
               → update "Zenta*" → "Zynta*" in comments
       F10:    Master_plan.md Doc ID "ZENTA-MASTER-PLAN-v1.0"
               → update to "ZYNTA-MASTER-PLAN-v1.0"
     Recommendation: Batch execution across all affected files in one commit.

  ⚠️ [MERGED-G7.1] Master Plan §3.3 MVI code sample matches deleted zombie API
     Severity: 🟠 MEDIUM
     Source: Phase 3 DC-02
     File: docs/plans/Master_plan.md §3.3
     Doc says: onIntent(), setState { }, SharedFlow effects, AutoCloseable
     Code has: dispatch(), handleIntent() (suspend), updateState { }, Channel<E>
     Recommendation: Replace §3.3 code sample with the live BaseViewModel API.
                     All 6 active ViewModels already use the correct patterns.

  ⚠️ [MERGED-G7.2] Master Plan §15.1 tech stack versions stale
     Severity: 🟡 LOW
     Source: Phase 3 DC-03
     File: docs/plans/Master_plan.md §15.1
     Doc says: Kotlin 2.1+, Compose 1.7+, AGP 8.5+
     Actual:   Kotlin 2.3.0, Compose 1.10.0, AGP 8.13.2
     Recommendation: Pin exact versions from libs.versions.toml. Remove "+" notation.

  ⚠️ [MERGED-G7.3] Master Plan §3.2 source tree omits :feature:media and :composeApp:core
     Severity: 🟡 LOW
     Source: Phase 3 DC-04
     File: docs/plans/Master_plan.md §3.2
     Note: Both ARE in §4.1 (as M20, M21) and in settings.gradle.kts and in code.
           Only the §3.2 tree diagram is incomplete.
     Recommendation: Add both modules to the §3.2 tree diagram.

  ⚠️ [MERGED-G10.1] DataModule.kt KDoc misattributes PasswordHashPort binding
     Severity: 🟡 LOW
     Source: Phase 3 NEW-02
     File: shared/data/src/commonMain/.../data/di/DataModule.kt
     KDoc says: "PasswordHashPort is bound HERE"
     Reality:   Binding is in SecurityModule.kt; DataModule consumes via get()
     Recommendation: Update KDoc to reference SecurityModule as the provider.

──────────────────────────────────────────────────────
2C. Code Duplications (from Phase 3):
──────────────────────────────────────────────────────

  ✅ [MERGED-G3.1] 4 private currency formatters bypass CurrencyFormatter
     Severity: 🟠 MEDIUM
     Status: ✅ RESOLVED (2026-02-22)
     Source: Phase 3 NEW-05
     Resolution:
       → Deleted formatCurrency() from CloseRegisterScreen.kt — replaced with CurrencyFormatter.formatPlain()
       → Deleted formatZCurrency() from ZReportScreen.kt — replaced with CurrencyFormatter.formatPlain()
       → Deleted formatPrice() from ProductGridSection.kt — replaced with CurrencyFormatter.format()
       → Deleted formatPrice() from ProductListScreen.kt — replaced with CurrencyFormatter.format()
       → All screens inject CurrencyFormatter via koinInject() — locale-aware, HALF_UP rounding

  🔁 [MERGED-G4.1] 4 private *EmptyState composables bypass ZyntaEmptyState
     Severity: 🟡 LOW
     Source: Phase 3 NEW-06
     Locations:
       feature/inventory/.../CategoryListScreen.kt:306     — CategoryEmptyState
       feature/inventory/.../SupplierListScreen.kt:276     — SupplierEmptyState
       feature/inventory/.../UnitManagementScreen.kt:352   — UnitEmptyState
       feature/inventory/.../TaxGroupScreen.kt:357         — TaxGroupEmptyState
     Canonical: composeApp/designsystem/.../components/ZyntaEmptyState.kt
     Recommendation: Replace all 4 with ZyntaEmptyState(...) calls.

  🔁 [MERGED-G5.1] 17 raw CircularProgressIndicator vs 2 ZyntaLoadingOverlay
     Severity: 🟡 LOW
     Source: Phase 3 NEW-07
     Locations: 17 screens across auth, register, pos, inventory use raw spinner.
                Only reports/SalesReportScreen and StockReportScreen use ZyntaLoadingOverlay.
     Recommendation: ~8 full-screen loading states → ZyntaLoadingOverlay.
                     ~9 inline/button spinners → keep raw (appropriate for context).
                     Schedule for UX consistency sprint.

──────────────────────────────────────────────────────
2D. Architectural & Integrity Violations (from Phase 3 & 4):
──────────────────────────────────────────────────────

  ✅ [MERGED-G1.1] named("deviceId") has ZERO providers — guaranteed startup crash
     Severity: 🔴 P0 CRITICAL
     Status: ✅ RESOLVED (2026-02-22)
     Source: Phase 3 NEW-04 + Phase 4 AV-04 carry-forward
     File: shared/security/src/commonMain/.../security/di/SecurityModule.kt
     Resolution:
       → AndroidDataModule.kt: single(named("deviceId")) using Settings.Secure.ANDROID_ID + UUID fallback
       → DesktopDataModule.kt: single(named("deviceId")) using UUID persisted to .device_id file

  ✅ [MERGED-G2.1] PrinterManagerReceiptAdapter imports SecurityAuditLogger (feature→infra)
     Severity: 🟠 MEDIUM
     Status: ✅ RESOLVED (2026-02-22)
     Source: Phase 3 NEW-01 + Phase 4 AV-04 carry-forward
     Resolution:
       → Replaced SecurityAuditLogger with AuditRepository (domain interface) in constructor
       → Adapter now builds AuditEntry directly — same audit event, no infra import
       → Removed implementation(project(":shared:security")) from pos/build.gradle.kts
       → Updated PosModule.kt binding: auditRepository = get(), deviceId = get(named("deviceId"))

  📛 [MERGED-G1.2] Bare single { PasswordHasher } Koin binding — possible dead code
     Severity: 🟡 LOW
     Source: Phase 3 NEW-03
     File: shared/security/src/commonMain/.../security/di/SecurityModule.kt
     Evidence: single { PasswordHasher } alongside correct single<PasswordHashPort> { ... }.
              PinManager calls PasswordHasher directly (not via Koin). Binding may have
              zero Koin consumers. Allows bypassing the domain port contract.
     Recommendation: Grep for get<PasswordHasher>() / inject<PasswordHasher>().
                     If zero consumers, remove the bare binding.

  📛 [MERGED-G1.3] named("deviceId") prerequisite not documented
     Severity: 🟡 LOW (becomes relevant after G1.1 is fixed)
     Source: Phase 3 NEW-04 (documentation aspect)
     File: docs/plans/Master_plan.md §4.2
     Recommendation: Add to §4.2: "Platform modules must provide String named('deviceId')
                     before securityModule loads."

  🔧 [MERGED-G12.1] 8 unused library entries in libs.versions.toml
     Severity: 🟡 LOW
     Source: Phase 4 BC-03
     File: gradle/libs.versions.toml
     Entries: androidx-appcompat, androidx-espresso-core, androidx-testExt-junit,
              datastore-preferences-core, datastore-core-okio, kermit-crashlytics,
              coil-network-ktor, turbine
     Recommendation: Add "# RESERVED FOR: <feature>" comments to entries planned
                     for near-term sprints. Remove entries with no concrete plan.

  🔧 [MERGED-G11.1] Empty keystore/ and token/ directories persist after ADR-004
     Severity: 🟡 LOW
     Source: Phase 2 F11 (recovered — was dropped from Phase 3/4 tracking)
     Files: shared/security/src/{commonMain,androidMain,jvmMain}/.../security/keystore/
            shared/security/src/commonMain/.../security/token/
     Evidence: ADR-004 removed .gitkeep files but left 4 empty dirs.
     Recommendation: Delete all 4 empty directories.

──────────────────────────────────────────────────────
2E. Test & Documentation Gaps:
──────────────────────────────────────────────────────

  📝 [MERGED-G13.1] CategorySupplierTaxUseCasesTest.kt not documented in project tree
     Severity: 🟡 LOW
     Source: Phase 2 F12 (recovered)
     File: shared/domain/src/commonTest/.../CategorySupplierTaxUseCasesTest.kt
     Recommendation: Add to project tree listing.

  📝 [MERGED-G13.2] Phase 1 tree aggregated 6 POS test files as "PosUseCasesTests"
     Severity: 🟡 LOW
     Source: Phase 2 F13
     Files: AddItemToCartUseCaseTest, CalculateOrderTotalsUseCaseTest,
            CartManagementUseCasesTest, DiscountUseCasesTest,
            ProcessPaymentUseCaseTest, VoidOrderUseCaseTest
     Recommendation: List all 6 individual test files in project tree.

──────────────────────────────────────────────────────
2F. Expected Gaps (Not Defects):
──────────────────────────────────────────────────────

  [G1] 50 of 63 ER entities have no .sq files — Phase 2/3 modules not yet started
  [G2] 7 scaffold feature modules have no nav routes — intentional, add when implemented
  [G3] docs/api/, docs/architecture/, docs/compliance/ are empty — populate in future sprints
  [G4] PlaceholderPasswordHasher.kt in codebase — intentional test utility, properly marked

──────────────────────────────────────────────────────
2G. Not Audited (Future Scope):
──────────────────────────────────────────────────────

  [NA-1] .github/workflows/ci.yml — CI pipeline not audited
  [NA-2] CONTRIBUTING.md, README.md — developer docs not reviewed
  [NA-3] SyncEngine.kt + SyncWorker.kt — documented but no deep correctness audit
  [NA-4] DatabaseMigrations.kt — referenced but not independently audited
  [NA-5] 13 DTOs in shared/data/.../remote/dto/ — counted but fields not verified
  [NA-6] 9 PLAN_*.md + 1 .docx in docs/plans/ — never checked for stale content

══════════════════════════════════════════════════════
SECTION 3: STATISTICS
══════════════════════════════════════════════════════

COMPONENT INVENTORY (corrected per Step 1 mismatch resolution):
  Gradle Modules:             23  (21 M-numbered + 2 infrastructure)
  ViewModels:                  6
  Repository Interfaces:      14
  Repository Implementations: 14
  Use Cases:                  33  (corrected from Phase 4's 32)
  Screens:                    29
  Domain Model Files:         27  (corrected from Phase 1/2's 26 and Phase 4's 28)
  DTOs:                       13
  DI Modules:                 24
  Port Interfaces:             6
  Adapters/Exporters:          5
  Design System Components:   15
  ─────────────────────────────────
  TOTAL AUDITED COMPONENTS:  186

AUDIT RESULTS:
  Naming convention compliance:       186/186  (100%)
  Architectural boundary violations:    1      (PrinterManagerReceiptAdapter)
  Circular module dependencies:         0      (clean DAG verified)
  Domain platform import leaks:         0
  Presentation→Data layer violations:   0
  ADR compliance:                     4/4      (100%)

FINDINGS SUMMARY:
  Total raw findings across 4 phases:          52
  Closed during audit lifecycle:               19
  Deduplicated open findings:                  18
  Expected gaps (not defects):                  4
  Components not audited (future scope):        6 categories
  ─────────────────────────────────────────────
  Missing from code (documented but absent):    0
  Undocumented in code (present but no docs):   0  *
  Doc-to-doc conflicts:                         5  (brand naming, §3.2 vs §4.1, dep table
                                                    vs architecture note, MVI sample, versions)
  Code duplications:                            3  (25 instances: 4 currency, 4 empty state,
                                                    17 loading indicators)
  Architectural violations:                     2  (feature→infra import, deviceId crash)
  Build config issues:                          1  (8 unused catalog entries)
  Documentation-only issues:                    7  (dep table, brand naming, MVI sample,
                                                    versions, tree diagram, source set name,
                                                    KDoc error)
  Cross-phase mismatches resolved:             10

  * Master_plan.md §3.2 still omits :composeApp:feature:media and :composeApp:core from
    the source tree diagram (DC-04). The §4.1 module registry is complete.

══════════════════════════════════════════════════════
SECTION 4: PRIORITY ACTION PLAN
══════════════════════════════════════════════════════

🔴 CRITICAL — Fix immediately (blocks all testing):

   1. [G1.1] Add named("deviceId") Koin provider
      → Add single(named("deviceId")) to AndroidDataModule.kt
        (Settings.Secure.ANDROID_ID + UUID fallback)
      → Add single(named("deviceId")) to DesktopDataModule.kt
        (UUID persisted to config file on first launch)
      → Files: shared/data/src/androidMain/.../di/AndroidDataModule.kt
               shared/data/src/jvmMain/.../di/DesktopDataModule.kt

🟡 WARNING — Fix in Sprint 4:

   1. [G2.1] Replace SecurityAuditLogger with AuditRepository in PrinterManagerReceiptAdapter
      → Replace SecurityAuditLogger injection with AuditRepository.log(AuditEntry(...))
      → Remove implementation(project(":shared:security")) from pos/build.gradle.kts
      → Files: composeApp/feature/pos/.../printer/PrinterManagerReceiptAdapter.kt
               composeApp/feature/pos/build.gradle.kts

   2. [G3.1] Delete 4 private currency formatters; route through CurrencyFormatter
      → Delete formatCurrency() in CloseRegisterScreen.kt
      → Delete formatZCurrency() in ZReportScreen.kt
      → Delete formatPrice() in ProductGridSection.kt
      → Delete formatPrice() in ProductListScreen.kt
      → Add CurrencyFormatter to each ViewModel state; pre-format monetary fields
      → Files: composeApp/feature/register/.../CloseRegisterScreen.kt
               composeApp/feature/register/.../ZReportScreen.kt
               composeApp/feature/pos/.../ProductGridSection.kt
               composeApp/feature/inventory/.../ProductListScreen.kt

   3. [G6.1] Fix Master Plan §4.1 dependency table (8 corrections in one pass)
      → Remove M03 from M13-M17, M19-M20 deps
      → Add M02 to M04, M05 deps
      → Remove M02 from M21 deps
      → Add M04 to M11, M12 deps
      → Add M08 to M09 deps (and remove M05 after G2.1 is fixed)
      → Add M01 to all feature dep lists (or document as transitive)
      → Update M07 dep list
      → File: docs/plans/Master_plan.md §4.1

   4. [G7.1] Update Master Plan §3.3 MVI code sample to match live BaseViewModel API
      → Replace onIntent → dispatch, setState → updateState, SharedFlow → Channel
      → Add suspend modifier to handler signature
      → File: docs/plans/Master_plan.md §3.3

   5. [G9.1] Complete Zenta → Zynta brand rename (batch execution)
      → Search-replace "ZentaPOS" → "ZyntaPOS" in UI_UX_Main_Plan.md + ER_diagram.md
      → Search-replace "ZENTA-" → "ZYNTA-" in all 3 planning doc headers
      → Rename zentaDynamicColorScheme() → zyntaDynamicColorScheme() in ZyntaTheme.kt
        + ZyntaTheme.android.kt + ZyntaTheme.desktop.kt
      → Update build.gradle.kts comments (Zenta* → Zynta*)
      → Fix ZentaLoadingSkeleton → ZyntaLoadingOverlay in UI_UX_Main_Plan.md §3.3
      → Update all 15 component names in UI_UX_Main_Plan.md §3.3 to Zynta prefix
      → Files: docs/plans/UI_UX_Main_Plan.md
               docs/plans/ER_diagram.md
               docs/plans/Master_plan.md (header only)
               composeApp/designsystem/.../theme/ZyntaTheme.kt (+ platform actuals)
               composeApp/designsystem/build.gradle.kts (comments)
               composeApp/navigation/build.gradle.kts (comments)

🟢 SUGGESTION — Nice to have (Sprint 4–5):

   1. [G4.1] Replace 4 private *EmptyState composables with ZyntaEmptyState(...)
      → Files: CategoryListScreen.kt, SupplierListScreen.kt,
               UnitManagementScreen.kt, TaxGroupScreen.kt

   2. [G5.1] Audit 17 raw CircularProgressIndicator usages; replace ~8 full-screen
      ones with ZyntaLoadingOverlay
      → 17 screens across auth, register, pos, inventory modules

   3. [G7.2] Update Master_plan.md §15.1 with exact pinned versions from libs.versions.toml
      → File: docs/plans/Master_plan.md §15.1

   4. [G7.3] Add :feature:media + :composeApp:core to Master_plan.md §3.2 tree diagram
      → File: docs/plans/Master_plan.md §3.2

   5. [G8.1] Update Master_plan.md §3.1 diagram: "desktopMain" → "jvmMain"
      → File: docs/plans/Master_plan.md §3.1

   6. [G10.1] Fix DataModule.kt KDoc: PasswordHashPort is bound in SecurityModule
      → File: shared/data/src/commonMain/.../data/di/DataModule.kt

   7. [G1.2] Remove bare single { PasswordHasher } from SecurityModule if unused
      → Grep for get<PasswordHasher>() / inject<PasswordHasher>() first
      → File: shared/security/.../di/SecurityModule.kt

   8. [G1.3] Document named("deviceId") prerequisite in Master_plan.md §4.2
      → File: docs/plans/Master_plan.md §4.2

   9. [G11.1] Delete 4 empty keystore/ and token/ directories
      → Files: shared/security/src/{commonMain,androidMain,jvmMain}/.../keystore/
               shared/security/src/commonMain/.../token/

  10. [G12.1] Annotate or remove 8 unused catalog entries in libs.versions.toml
      → File: gradle/libs.versions.toml

  11. [G13.1] Add CategorySupplierTaxUseCasesTest.kt to project tree docs
  12. [G13.2] Expand "PosUseCasesTests" to list all 6 individual test files

══════════════════════════════════════════════════════
SECTION 5: HEALTH SCORE
══════════════════════════════════════════════════════

Structure Alignment:     7 /10
  ✅ 23/23 modules exist and match settings.gradle.kts
  ✅ All 4 ADRs fully compliant
  ✅ All 21 package paths correct
  ✅ Sprint progress claims match reality
  ⚠️ Master Plan §4.1 dependency table has 8 errors vs actual build.gradle.kts
  ⚠️ 50/63 ER entities not yet implemented (expected for phased rollout)

Doc Consistency:         5 /10
  ✅ Master Plan product name and module registry are correct
  ✅ All 4 ADR docs match live codebase state
  ✅ Sprint progress doc is accurate and current
  ❌ 2 of 3 planning docs use wrong product name ("ZentaPOS")
  ❌ 15 UI component names use wrong brand prefix in UI_UX doc
  ❌ MVI code sample in §3.3 references deleted zombie API
  ❌ Tech stack versions stale (Kotlin 2.1+ vs actual 2.3.0)
  ❌ Source tree diagram (§3.2) incomplete
  ❌ Architecture diagram uses wrong source set name

Code Quality:            7 /10
  ✅ 186/186 naming convention compliance (100%)
  ✅ Zero circular module dependencies
  ✅ Zero presentation→data layer violations
  ✅ Zero domain platform import leaks
  ✅ All 6 ViewModels correctly extend BaseViewModel
  ✅ 14/14 repository interface↔implementation pairs
  ✅ Hexagonal port/adapter pattern correctly implemented
  ❌ 1 P0 runtime crash: named("deviceId") zero providers
  ⚠️ 1 feature→infrastructure cross-boundary import
  ⚠️ 4 private currency formatters (floating-point rounding risk)
  ⚠️ 4 private EmptyState composables bypassing design system
  ⚠️ 1 potentially dead Koin binding

Build Configuration:     9 /10
  ✅ 100% version catalog compliance (55/55 used deps)
  ✅ Zero hardcoded version strings
  ✅ 23/23 modules registered in settings.gradle.kts
  ✅ 14/14 plugins via catalog aliases
  ✅ JVM targets consistent (JVM_11 Android, JVM_17 Desktop)
  ✅ All 5 bundles actively used
  ✅ Compose DSL accessors correctly applied
  ⚠️ 8 unused library entries in version catalog

──────────────────────────────────────────────────────

Overall Project Health:  7 /10

──────────────────────────────────────────────────────

EXECUTIVE SUMMARY:

The ZyntaPOS KMM codebase demonstrates exceptional architectural discipline.
Clean Architecture boundaries are rigorously enforced — zero presentation→data
violations, zero domain platform leaks, zero circular dependencies. All 186
audited components conform to documented naming conventions (100%). The build
configuration is production-grade with full version catalog compliance.

ONE BLOCKING ISSUE remains: the named("deviceId") Koin binding has zero
providers across all modules (MERGED-G1.1). This causes a NoBeanDefFoundException
on app startup on ALL platforms. This must be fixed before any integration testing.

DOCUMENTATION DEBT accounts for 10 of the 18 open findings. A focused 1–2 hour
doc sprint — covering the brand rename (G9.1), dependency table (G6.1), MVI sample
(G7.1), tech versions (G7.2), and tree diagram (G7.3/G8.1) — would bring all
planning documents into full alignment with the codebase.

The MERGED-F3 hexagonal port/adapter restructuring (completed between Phase 2 and
Phase 3) is architecturally sound. One remaining cross-boundary import (G2.1:
SecurityAuditLogger in PrinterManagerReceiptAdapter) is the final cleanup needed
to achieve full domain-driven isolation.

The codebase is a solid, well-architected KMP foundation ready for scaling to
production — pending the P0 deviceId fix and a documentation alignment sprint.

══════════════════════════════════════════════════════
         END OF FINAL AUDIT REPORT
══════════════════════════════════════════════════════
Phases completed:        4 of 4
Synthesis steps:         3 of 3 (Mismatches → Dedup → Final)
Cross-phase mismatches:  10 found, 10 resolved
Findings lifecycle:      52 raw → 19 closed → 18 open → 4 expected gaps
Next action:             Fix MERGED-G1.1 (deviceId provider) IMMEDIATELY
══════════════════════════════════════════════════════
```
