# ZyntaPOS KMM — Comprehensive Audit, QA & KPI Plan

> **Doc ID:** AUDIT-QA-KPI-v2.0
> **Prepared by:** Senior KMP Architect & Lead Engineer
> **Date:** 2026-02-25
> **Audit method:** Automated full-codebase scan (all 26 modules, git log, execution_log.md cross-validation)
> **Branch audited:** `claude/kmp-zynta-pos-setup-QVcJ9`

---

## Context

This plan was prepared after a full automated codebase audit of the ZyntaPOS-KMM repository (26 modules, ~3 phases implemented). The goal is to equip the QA/SDET, Product Analyst, and IT Auditor/Compliance teams with a structured, priority-ordered programme of work covering testing, documentation, architecture governance, performance, security, and compliance.

**CRITICAL FINDING — Documentation vs Reality Gap:**
Multiple Claude sessions implemented Phase 1, 2, and 3 between 2026-02-20 and 2026-02-25 without keeping `docs/ai_workflows/execution_log.md` in sync. The result is a large discrepancy:

| Source | Phase Status |
|--------|-------------|
| `execution_log.md` header | "Phase 1 PENDING EXECUTION" |
| `execution_log.md` Phase 2/3 | "NOT STARTED (Blocked on Phase 1 completion)" |
| **Actual git commits** | Phase 1 ✅, Phase 2 ✅, Phase 3 ~80% ✅ |

This gap is the primary driver for the audit programme below.

**Audit Findings Summary (current baseline):**
- Architecture compliance: EXCELLENT — all 4 ADRs enforced, MVI correct on all ViewModels
- KDoc coverage: ~95% on domain models, use cases, repositories
- Test cases: 808 across 46 files — but 11/15 feature ViewModels have ZERO tests
- Active test compilation issues: 6 "Doing" tasks in execution log (FakeRepositories, OrderTotals, PosViewModelTest)
- Data layer: Only 3 of 34 repository implementations have integration tests
- Missing architecture docs: sync-strategy.md, security-model.md, module-dependency-graph.md
- CLAUDE.md references 6 files that do NOT exist in the codebase
- 4 code-level stubs (TODO) in critical paths: AuditRepositoryImpl, SyncEngine, SyncRepositoryImpl, AuthViewModel
- Sprint 24 Phase 1 QA (20 validation tasks): ALL unchecked — never done
- CRDT implementation: infrastructure scaffolded but core merge logic missing

---

## SECTION A: Documentation vs Implementation Truth Map

### A1 — Execution Log Drift (Most Critical)

The `execution_log.md` (3,479 lines, last meaningful update 2026-02-22) does NOT track:
- Phase 2: All 4 feature modules (customers, coupons, multistore, expenses) — 135 files, 13,165 lines added in commit `5672a9a`
- Phase 2: 8 new repository implementations, 16 new SQLDelight schemas, 20+ new domain models
- Phase 2: POS extensions (wallet/loyalty/coupon integration into PosViewModel)
- Phase 3: Staff, Admin, Media, Accounting, E-Invoice feature modules — 8 commits on 2026-02-25
- Phase 3: Domain/data layer for all Phase 3 features

**Action:** The execution_log.md needs a Phase 2 and Phase 3 summary section appended, OR a new canonical tracking document created.

### A2 — CLAUDE.md Stale File References

CLAUDE.md references these files that do NOT exist:

| CLAUDE.md Claims | Reality |
|-----------------|---------|
| `SessionManager` in `:shared:security` | File is at `composeApp/feature/auth/session/SessionManager.kt` (wrong module) |
| `ConflictResolver` | Does not exist anywhere — CRDT merge logic is unimplemented |
| `CashDrawerController` | Does not exist — HAL cash drawer support is missing |
| `PinHasher` | Does not exist — it's named `PinManager.kt` |
| `TokenManager` | Does not exist — split into `JwtManager.kt` + `TokenStorage.kt` |
| `RbacNavFilter` | Does not exist anywhere in navigation module |

**Action:** CLAUDE.md Module Map and Security Details sections must be corrected to match actual file names and locations.

### A3 — Code Stubs Marked as Complete

These implementations have `TODO` bodies but their parent tasks are marked `[x]` done:

| File | Line | Stub Description | Risk |
|------|------|-----------------|------|
| `shared/data/.../repository/AuditRepositoryImpl.kt` | 42, 53, 62 | `TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")` | Security audit trail is NOT persisted to DB |
| `shared/data/.../sync/SyncEngine.kt` | 272 | `TODO Sprint 7: route by entityType → RepositoryImpl.upsertFromSync(op.payload)` | Delta sync is non-functional |
| `shared/data/.../repository/SyncRepositoryImpl.kt` | 156 | `TODO(Sprint6-Step3.4): wire Ktor ApiService here` | Cloud push/pull is non-functional |
| `composeApp/feature/auth/.../AuthViewModel.kt` | 107 | `TODO (Sprint 20): check open register session; emit NavigateToRegisterGuard if none` | Register session guard bypassed |

### A4 — Active Test Compilation Issues (Unfinished "Doing" Tasks)

These items are marked as "Doing" (not done) at the bottom of execution_log.md:

| Task | File | Issue |
|------|------|-------|
| Fake fix | `FakeAuthRepositories.kt` | `logout()` return type: `Unit` not `Result<Unit>` |
| Fake fix | `FakeInventoryRepositories.kt` | 3 issues: syncStatus param, nullable barcode/sku, getAlerts signature |
| Fake fix | `FakePosRepositories.kt` | `getAll(Map<String,String>?)` → non-nullable with default |
| Fake fix | `FakeSharedRepositories.kt` | Add `suspend` to `get()` and `getAll()` |
| Domain fix | `OrderTotals.kt` | `itemCount: Double` → `Int`; fix EMPTY companion |
| Test fix | `PosViewModelTest.kt` | Missing `printReceiptUseCase` & `receiptFormatter` constructor params |

**These may cause `./gradlew test` to fail. Must be resolved before any testing work begins.**

### A5 — Sprint 24 QA Never Executed

All 20 Phase 1 final validation tasks are unchecked in execution_log.md:

- E2E flow tests (manual + automated) — never done
- Offline E2E sync test — never done
- Performance benchmarks (cold start, FTS5 search, payment timing) — never done
- Security validation (SQLCipher, Keystore, RBAC smoke test) — never done
- UI quality audit (dark mode, responsive, keyboard shortcuts) — never done
- Release build preparation (APK signing config, ProGuard rules, jpackage) — never done
- CI/CD final validation — never done

### A6 — CRDT Gap (Phase 2 Claimed Complete, Actually ~30%)

Phase 2 audit reports claim CRDT is complete, but code reveals:
- Infrastructure scaffolded: `conflict_log.sq`, `version_vectors.sq`, `sync_state.sq` ✅
- Domain model exists: `SyncConflict.kt`, `ConflictLogRepository` ✅
- **Missing:** `ConflictResolver.kt` — the actual PN-Counter/LWW merge logic
- **Missing:** Delta operation dispatcher in `SyncEngine.kt` (line 272 TODO)
- **Missing:** `SyncRepositoryImpl` Ktor wiring (line 156 TODO)
- Result: Multi-store sync cannot resolve conflicts; cloud push/pull non-functional

---

## SECTION B: Orphan Implementations — Defined but Not Wired to UI

These are backend implementations (domain models, use cases, repository interfaces, SQLDelight schemas) that exist in the codebase but have **zero references in any feature module UI, ViewModel, or Koin module**. The full vertical stack was built but never connected to the application surface.

### B1 — Completely Dark Features (Zero UI Consumers)

| Backend Asset | Layer | UI Consumer | Missing Feature |
|---|---|---|---|
| `ConflictLogRepository` + `conflict_log.sq` | Domain + Data | **None** | Sync conflict viewer in Admin console |
| `SyncRepository` / `SyncOperation` | Domain + Data | **None** | Sync status / queue inspector screen |
| `VersionVector` + `version_vectors.sq` | Data | **None** | Multi-store sync health indicator |
| `InstallmentPlan` + `installment_plans.sq` | Domain + Data | **None** | Instalment plan CRUD / POS payment option |

### B2 — Partial Wiring (Use Case Exists but NOT Injected into Koin or ViewModel)

These use cases compile fine but are unreachable at runtime because they are not bound in any Koin module and not called by any ViewModel:

| Use Case | Missing In | Missing From ViewModel |
|---|---|---|
| `VacuumDatabaseUseCase` | `AdminModule.kt` | `AdminViewModel.handleIntent()` |
| `PurgeExpiredDataUseCase` | `AdminModule.kt` | `AdminViewModel.handleIntent()` |
| `GetPayrollHistoryUseCase` | `StaffModule.kt` | `StaffViewModel.handleIntent()` |
| `GetAttendanceSummaryUseCase` | `StaffModule.kt` | `StaffViewModel.handleIntent()` |
| `GetLeaveHistoryUseCase` | `StaffModule.kt` | `StaffViewModel.handleIntent()` |

### B3 — Full Vertical Slice but Missing Navigation Route

These feature modules may have working screens but are not reachable from the main navigation:
- Verify `InstallmentPlan` payment flow is absent from `PosScreen.kt` payment method list
- Verify `ConflictLogScreen` is absent from `AdminScreen.kt`
- Verify `SyncQueueScreen` is absent from admin/debug navigation

### B4 — How to Audit for More Orphans

Use this search pattern during QA:
```bash
# For each domain model, check if any ViewModel imports it
grep -r "ModelName" composeApp/feature --include="*.kt"
# For each use case, check Koin module binding
grep -r "UseCaseName" composeApp/feature --include="*Module.kt"
```

---

## Audit Programme: 12 Areas

### Area 1 — Test Coverage (Enhanced from Original Suggestion)

**Current state:** 808 tests, 46 files. Critical gaps:
- 11 feature modules with zero ViewModel tests (accounting, admin, coupons, customers, expenses, inventory, media, multistore, register, reports, staff)
- Only 3/34 repository implementations have integration tests
- BaseViewModel infrastructure: 0 tests
- HAL module: 0 tests
- Compose UI: 0 tests (Phase 2)

**Recommended actions:**
1. Add ViewModel tests (using Turbine + fake repos) for all 11 missing feature modules — target 80% coverage per CLAUDE.md
2. Add jvmTest integration tests (in-memory SQLite via TestDatabase.kt) for all 34 repository implementations
3. Add commonTest unit tests for BaseViewModel (state mutation, effect channel delivery, viewModelScope lifecycle)
4. Add HAL mock tests using NullPrinterPort and fake scanner event streams
5. Add Compose UI tests in Phase 2 (ZyntaButton, ZyntaCard, NavHost, responsive breakpoints)
6. Add E2E integration tests for the 3 critical flows: Login → POS → Payment → Receipt; Register Session Open/Close; Stock Adjustment → Sync
7. Create a shared `BaseViewModelTest` base class to standardise Turbine setup/teardown

**Coverage targets (per CLAUDE.md):**

| Layer | Target | Current | Gap |
|-------|--------|---------|-----|
| Use cases | 95% | ~95% | ✅ Met |
| Repository implementations | 80% | ~9% | **Critical** |
| ViewModels | 80% | ~40% | **High** |
| Compose UI | Phase 2 | 0% | Planned |

---

### Area 2 — Documentation Completeness (Enhanced from Original Suggestion)

**Missing files (referenced in README but absent):**
- `docs/architecture/sync-strategy.md` — offline-first queue, CRDT conflict resolution
- `docs/architecture/security-model.md` — key flow, Keystore/JCE handoff, AES-256-GCM lifecycle
- `docs/architecture/module-dependency-graph.md` — visual DAG of 26 modules + import rules

**Stale/placeholder files:**
- `docs/api/README.md` — empty placeholder; needs endpoint list, auth flow, models
- `docs/compliance/README.md` — empty placeholder; needs PCI-DSS, GDPR, IRD e-invoice notes
- 50+ versioned audit/plan docs in `docs/plans/` and `docs/audit/` — need consolidation

**Recommended additions:**
- SessionManager design note (clarify its definition/location, extend ADR-003)
- Document the intentional DB schema expansion (36 tables vs. 13 documented)
- Update master roadmap to reflect 16 features (accounting and onboarding added)
- Add kotlinx-datetime version comment in `gradle/libs.versions.toml` explaining the 0.7.1 vs 0.6.1 discrepancy

---

### Area 3 — Clean Architecture & MVI Governance (Confirmed Green)

**Current state:** FULLY COMPLIANT. All checks passed:
- ADR-001: All ViewModels extend BaseViewModel ✅
- ADR-002: 0 `*Entity` suffixes in domain layer ✅
- ADR-003/004: No SecurePreferences in `:shared:data`, no GlobalContext.get() misuse ✅
- Architecture boundaries: No domain → data/hal/security imports ✅
- Reactive patterns: Canonical `debounce(300)/flatMapLatest` usage everywhere ✅

**Recommended governance actions:**
1. Automate ADR checks with custom Detekt rules (e.g., forbid direct ViewModel subclassing)
2. Harden Detekt for Phase 2: `warningsAsErrors: true`, reduce complexity thresholds, enable `UnusedPrivateProperty`
3. Add a CI step that enforces module boundary rules (e.g., Dependency Guard plugin)
4. Move the 13 legitimate TODO/FIXME comments into sprint backlog tracker rather than inline code

---

### Area 4 — Planning vs Implementation Gap (Enhanced from Original Suggestion)

**Gaps found:**
- Database: 36 `.sq` files implemented vs. 13 documented in CLAUDE.md — Phase 2/3 tables built ahead of schedule
- Features: 16 feature modules vs. 15 planned (accounting, onboarding not in master roadmap)
- `SessionManager` referenced in CLAUDE.md Security Details as being in `:shared:security` — actual location is `feature/auth/session/`
- `CashDrawerController` listed in CLAUDE.md HAL section — does not exist in codebase

**Recommended actions:**
1. Update CLAUDE.md Module Map and README to reflect actual state (36 tables, 16 features)
2. Append Phase 2 and Phase 3 summary sections to execution_log.md
3. Run a formal vertical-slice mapping: every domain model → DB table → repository interface → use case → ViewModel intent → screen

---

### Area 5 — Dead Code & Unused Symbols (Confirmed Low Risk)

**Current state:** Minimal. Only 13 TODO/FIXME markers found, all legitimate sprint placeholders.

**Recommended actions:**
1. Enable Detekt `UnusedPrivateProperty` and `UnusedParameter` in Phase 2 config
2. Audit Phase 1 stub handlers in `InventoryViewModel` (Sprint 19 placeholders — lines 126-149)
3. Verify `NullPrinterPort` is only used in test/debug builds (not production DI bindings)
4. Add `@Suppress` annotations with written justification rather than silent suppressions

---

### Area 6 — Code Duplication & Reinvention (Confirmed Low Risk)

**Current state:** No problematic duplication. Pattern consistency is intentional.

**Minor opportunities:**
1. Extract form-field update boilerplate (`copy()` pattern repeated in Inventory/Expenses/Customer VMs)
2. Consolidate `Result` pattern `fold()` handling in `:shared:core`
3. Standardise fake repository base class with shared error-injection flags (`shouldFailCreate`, `shouldFailOpen`)

---

### Area 7 — Security & Penetration Testing (New)

Beyond code review — actual security validation:

1. **Crypto correctness:** Verify AES-256-GCM tag validation, IV uniqueness per encryption call, no key material in logs
2. **PIN brute-force resistance:** Validate lockout policy in `SessionManager` after N failed attempts
3. **JWT clock-skew handling:** Test tokens at exactly the 30s buffer boundary (`JwtManager`)
4. **RBAC bypass testing:** Attempt to access routes above user's role (CASHIER → ADMIN routes, REPORTER → POS mutations)
5. **SQLCipher passphrase exposure:** Verify passphrase is never written to SharedPreferences in plaintext or included in Sentry payloads
6. **Token refresh race condition:** Simultaneous 401 responses — verify `TokenManager` prevents double refresh
7. **Keystore migration path:** Verify `SecurePreferencesKeyMigration.migrate()` handles cold-start and upgrade paths
8. **Secrets in BuildConfig:** Verify `ZYNTA_*` keys from `local.properties` are not extractable from release APK
9. **IRD certificate lifecycle:** Verify `.p12` path is not hardcoded; test certificate expiry handling

---

### Area 8 — Performance & KPI Metrics (New)

For the Product Analyst & Data Analyst team:

**Application KPIs to instrument:**

| KPI | Target | How to Measure |
|-----|--------|----------------|
| POS transaction time (add-to-cart → payment-processed) | < 2s | Trace via AndroidProfiler / custom timer |
| Product search latency (after 300ms debounce) | < 500ms | SQLDelight benchmark test |
| App cold-start to login screen | Android < 3s / Desktop < 2s | Android Profiler startup trace |
| Receipt print time | < 5s | HAL callback timer in `PrinterManager` |
| FTS5 search with 10K products | < 200ms | jvmTest benchmark |
| Sync engine push latency | < 1s avg | SyncEngine timing hook |

**Technical KPIs:**

| KPI | Target | Frequency |
|-----|--------|-----------|
| CI pipeline duration | < 15 min | Per PR |
| Release APK size | < 20MB | Per sprint |
| Android method count | < 64K | Per sprint |
| Test suite execution time | < 5 min | Per PR |
| Detekt violation count | 0 critical | Per PR |

---

### Area 9 — Offline-First Resilience Testing (New)

Critical for an enterprise POS system:

1. **Network partition:** `SyncEngine` behaviour when offline for 24h+ (queue depth, conflict accumulation)
2. **Conflict resolution:** Concurrent edits to same product on two store terminals — verify `ConflictResolver` logic
3. **DB corruption recovery:** Wrong SQLCipher passphrase on cold start — graceful error vs. crash
4. **Partial sync failure:** Push succeeds, pull fails — verify `SyncEnqueuer` `INSERT OR IGNORE` idempotency
5. **Queue overflow:** 10,000+ operations in `sync_queue` — does the engine degrade gracefully?
6. **Round-trip data integrity:** `CalculateOrderTotalsUseCase` output must survive serialisation → DB write → DB read → deserialisation without floating-point drift
7. **Version vector conflicts:** CRDT vector logic in `SyncEngine` for multi-store concurrent operations

---

### Area 10 — Platform Compatibility & HAL Testing (New)

1. **Android API range:** minSdk 24 (Android 7.0) through targetSdk 36 — Keystore API and SecurityManager differences
2. **Desktop OS matrix:** macOS (Intel + Apple Silicon), Windows 10/11, Ubuntu 22.04 LTS
3. **Printer models:** Test ESC/POS against at least 3 thermal printers: Epson TM-T88, Star TSP100, Xprinter XP-58
4. **Barcode scanner types:** USB HID keyboard-wedge, serial RS-232, camera (ML Kit on Android)
5. **Cash drawer:** 12V vs. 24V pulse width compatibility (once `CashDrawerController` is implemented)
6. **Responsive layout breakpoints:** Compact (360dp), Medium (720dp), Expanded (1280dp) — no overflow or clipped text
7. **jSerialComm:** Verify `DesktopSerialPrinterPort` handles port-not-found and disconnection gracefully

---

### Area 11 — Regulatory Compliance Audit (New)

For the IT Auditor & Compliance Officer:

1. **PCI-DSS:** Verify no card PANs stored in SQLite — `orders`, `audit_log`, `sync_queue` tables must not contain raw card numbers
2. **GDPR:** Validate `CustomerRepository.gdprExport()` and right-to-erasure; verify all PII fields deleted on request; check audit trail retention policy
3. **IRD e-invoice (Sri Lanka):** Validate `EInvoice` domain model and `e_invoices` DB table against IRD API specification; test certificate renewal path
4. **Audit trail integrity:** Verify `AuditEntry` is persisted for all write operations (currently `AuditRepositoryImpl` has 3 TODO stubs — security audit trail not functional)
5. **Data retention policy:** Confirm `audit_log`, `sync_queue`, and `orders` have appropriate retention/archival policies
6. **Session timeout:** Verify auto-lock triggers after configured inactivity period
7. **Backup/restore:** Validate encrypted backup format; test restore to new device without passphrase exposure in transit

---

### Area 12 — Build Reproducibility & Dependency Security (New)

1. **Dependency vulnerability scan:** Add OWASP Dependency Check Gradle plugin to CI; scan all 250+ dependencies in `libs.versions.toml` for known CVEs
2. **Build determinism:** Same source → same binary across clean builds (critical for release signing audit)
3. **License audit:** SQLCipher is BSL-licensed; jSerialComm is LGPL — verify enterprise usage is permitted
4. **Dependency freshness:** Track outdated libraries; consider Renovate bot for automated PRs
5. **Supply chain:** `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` — verify no rogue repositories via transitive dependencies

---

## Improvements to Original Suggestions

| Original Suggestion | Assessment | Enhancement |
|---------------------|-----------|-------------|
| Comprehensive testing (unit, mock, E2E, edge cases) | ✅ Correct — needs specificity | Prioritise 11 missing feature VM tests and 31 missing repository integration tests; the domain layer is already at 95% |
| Up-to-date documentation | ✅ Correct | Three architecture docs explicitly referenced but absent; two docs are empty placeholders; execution_log.md is severely out of sync with reality |
| Clean architecture, MVI, best practices | ✅ Correct — already EXCELLENT | Redirect effort to Detekt hardening and automated ADR enforcement rather than manual review |
| Implementation vs. planning docs | ✅ Correct | Specific, code-verified gaps: 36 DB tables vs. 13 documented; 16 features vs. 15; 6 CLAUDE.md file references are wrong |
| Unused class/function check | ✅ Correct — very minimal | More important: 5 use cases that compile but are unreachable at runtime due to missing Koin bindings (Section B2) |
| Code duplication | ✅ Correct — minimal | Minor form-field copy() pattern is the only extractable opportunity |

**Six new areas not in the original suggestions (all high priority):**
- Area 7: Security penetration testing
- Area 8: Performance & KPI instrumentation
- Area 9: Offline-first resilience testing
- Area 10: Platform & HAL compatibility
- Area 11: Regulatory compliance
- Area 12: Dependency security & build hygiene

---

## Team Assignment Matrix

| Area | QA / SDET | Product Analyst | IT Auditor |
|------|-----------|----------------|-----------|
| 1. Test Coverage | **Primary** | — | Secondary |
| 2. Documentation | Support | Support | **Primary** |
| 3. Architecture Governance | Support | — | **Primary** |
| 4. Planning vs Implementation | Support | **Primary** | Support |
| 5. Dead Code | **Primary** | — | — |
| 6. Code Duplication | **Primary** | — | — |
| 7. Security Testing | **Primary** | — | **Primary** |
| 8. Performance & KPI | Support | **Primary** | — |
| 9. Offline Resilience | **Primary** | Support | — |
| 10. Platform Compatibility | **Primary** | — | — |
| 11. Regulatory Compliance | Support | — | **Primary** |
| 12. Dependency Security | Support | — | **Primary** |

---

## Priority Order

### Immediate — Fix Blockers First

These must be done before any other testing work begins:

1. Fix 6 active test compilation issues (Section A4) — `./gradlew test` may currently fail
2. Resolve 4 critical `TODO` stubs in `AuditRepositoryImpl`, `SyncEngine`, `SyncRepositoryImpl`, `AuthViewModel` (Section A3)
3. Correct 6 stale CLAUDE.md file references (Section A2)
4. Wire 5 orphan use cases into Koin modules and ViewModels (Section B2)

### Short Term — Phase 1 Completion Gate

5. ViewModel tests for 11 missing feature modules (Area 1)
6. Repository integration tests: 3 → 34 (Area 1)
7. Execute Sprint 24 validation: 20 unchecked tasks (Section A5)
8. Create 3 missing architecture docs (Area 2)
9. Security pen-test on crypto, RBAC, token handling (Area 7)
10. Detekt Phase 2 hardening config (Area 3)

### Phase 2 Entry Gate (Must All Pass)

- All feature modules ≥ 80% ViewModel test coverage
- All 34 repositories have integration tests
- 0 critical Detekt violations in strict mode
- Security pen-test sign-off
- Sprint 24 validation complete

### Ongoing Through Phase 2

- Area 8: KPI instrumentation and dashboard
- Area 9: Offline resilience test suite
- Area 10: Platform compatibility matrix
- Area 11: Compliance audit trail

### Phase 2 Exit Gate (Must All Pass)

- Area 12: Dependency CVE scan clean
- Area 11: PCI-DSS and GDPR sign-off
- Area 2: All documentation complete and reviewed
- `execution_log.md` fully updated to reflect Phases 1, 2, and 3
