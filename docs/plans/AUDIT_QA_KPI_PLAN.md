# ZyntaPOS Comprehensive Audit, QA & KPI Plan

## Context

ZyntaPOS is a KMP+Compose Multiplatform enterprise POS system currently in Phase 3, Sprint 18 of 24. The project has a strong architectural foundation (Clean Architecture, MVI, SQLDelight, SQLCipher, Koin, RBAC) with Phase 1 complete and Phase 3 actively delivering. This plan equips the QA, Compliance, and Product teams with a structured audit and measurement framework.

**Codebase snapshot:**
- 26 modules (18 implemented, 8 scaffolded for Phase 2+)
- 80+ use cases, 36 domain repositories, 36 SQLDelight tables
- 49 test files total (strong in domain/security; thin in UI layer)
- 4 accepted ADRs, 30+ docs, full CI/CD pipelines

---

## PART A — QA & Testing Audit

### A1. Test Coverage Gap Analysis

**Current State:** Domain layer well-tested; UI/presentation layer nearly untested.

| Module | Test Count | Target Coverage | Gap |
|--------|-----------|-----------------|-----|
| `:shared:core` | 4 | 95% | Medium — missing extension fn tests |
| `:shared:domain` | 21 | 95% | Medium — 80+ use cases, only 21 tests |
| `:shared:data` | 5 | 80% | High — 36 repo impls, only 5 tests |
| `:shared:security` | 5 | 95% | High — JWT expiry, key rotation untested |
| `:composeApp:core` | 0 | 80% | **Critical — BaseViewModel itself untested** |
| `:composeApp:designsystem` | 0 | N/A | Low — visual regression acceptable |
| `:composeApp:navigation` | 0 | 80% | High — RBAC route gating untested |
| `:feature:auth` | 3 | 80% | High — PIN lock, session guard logic untested |
| `:feature:pos` | 1 | 80% | **Critical — revenue-critical payment flow** |
| `:feature:inventory` | 0 | 80% | High |
| `:feature:register` | 0 | 80% | High — cash session/Z-report is financially sensitive |
| `:feature:reports` | 0 | 80% | Medium |
| `:feature:settings` | 1 | 80% | Medium |
| `:feature:dashboard` | 1 | 80% | Medium |
| `:tools:debug` | 6 | 60% | Low |

**Action for QA Team:**

**Priority 1 — Revenue-Critical Tests**
- [ ] `shared:domain` — Expand to cover all 80+ use cases: `CalculateOrderTotalsUseCaseTest`, `ProcessPaymentUseCaseTest`, `ApplyItemDiscountUseCaseTest`, `ApplyOrderDiscountUseCaseTest`, `ValidateCouponUseCaseTest`, `HoldOrderUseCaseTest`, `VoidOrderUseCaseTest`
- [ ] `feature:pos` — `PosViewModelTest` with Turbine: cart operations, payment flows, hold order, discount application, barcode scan, receipt generation
- [ ] `feature:register` — `RegisterViewModelTest`: session open/close, cash-in/out, Z-report generation
- [ ] `shared:security` — JWT expiry edge case, key rotation, RBAC permission matrix exhaustive coverage, PBKDF2 collision resistance

**Priority 2 — Architectural Contract Tests**
- [ ] `composeApp:core` — `BaseViewModelTest`: state updates atomicity, effect delivery exactly-once guarantee via Channel, intent dispatch ordering
- [ ] `composeApp:navigation` — `RbacNavFilterTest`: each role (ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER) sees correct route set; unauthorized routes blocked
- [ ] `shared:security` — `PinManagerTest`: hash uniqueness (different salts), constant-time compare, invalid PIN rejection

**Priority 3 — Data Layer Integration Tests**
- [ ] `shared:data` (jvmTest) — SQLDelight in-memory database tests for all 36 repos: CRUD, pagination, FTS search, stock adjustment, sync queue enqueue/dequeue
- [ ] `shared:data` — `SyncEngineTest`: offline operation queuing, conflict resolution, retry on failure, CRDT merge

**Priority 4 — E2E / Instrumented Tests**
- [ ] POS checkout flow (end-to-end): product scan → add to cart → apply discount → cash payment → receipt print trigger
- [ ] Auth flow: login → PIN lock → session timeout → re-authentication
- [ ] Register session: open → multiple transactions → close → Z-report

### A2. Test Infrastructure Verification

**Verify these are functional in all modules:**
- [ ] `Turbine` (`1.2.0`) — Flow/StateFlow assertions work in `commonTest`
- [ ] `Mockative 3.0.1` — KSP runs correctly with Kotlin 2.3.0 + KSP 2.3.4; generated mocks don't have stale codegen
- [ ] `kotlinx-coroutines-test` — `runTest` + `UnconfinedTestDispatcher` available in all commonTest source sets
- [ ] `koin-test` — Koin module validation tests (`checkModules {}`) exist for at least core DI graph

**Missing test infrastructure to add:**
- [ ] `TestDispatcherRule` — Equivalent to Tranzlate project; ensures `Dispatchers.Main` reset between tests (critical for ViewModel tests)
- [ ] `FakeRepository` implementations — In-memory fakes for each of the 4 Phase 1 domain repositories (Product, Order, Register, User) for ViewModel unit tests without DB

### A3. Edge Case Inventory

QA team must document and test these known edge cases:

**POS / Payment:**
- Split payment amounts don't sum to order total
- Payment with 0-quantity items in cart
- Hold order then modify cart before retrieving
- Apply coupon that exceeds order total
- Void an order that has already been synced to cloud
- Concurrent barcode scans (rapid successive scans)

**Register / Cash:**
- Close session with pending held orders
- Z-report when no transactions in session
- Negative cash-in amount entered
- Register already open when trying to open again

**Security / Auth:**
- PIN lock on app background after configurable timeout
- JWT token expiry mid-transaction
- RBAC: CASHIER attempting manager-only routes
- Session invalidation on concurrent login from another device
- Invalid/expired IRD certificate during e-invoice submission

**Database / Sync:**
- Conflict resolution when same product updated offline on 2 devices
- Sync queue fills during extended offline period (>1000 operations)
- SQLCipher decrypt failure (wrong passphrase scenario)
- Database migration from v(N) to v(N+1) with existing data

**Network:**
- IRD API timeout / 5xx during e-invoice submission
- Partial sync upload (connection drops mid-batch)
- Ktor retry backoff — 3 retries with exponential delay

---

## PART B — Documentation Audit

### B1. ADR Review

**Current ADRs (4 total, all ACCEPTED):**

| ADR | Compliant? | Action |
|-----|-----------|--------|
| ADR-001: BaseViewModel mandatory | ✅ All 15 feature VMs extend BaseViewModel | No action |
| ADR-002: No `*Entity` in domain | ✅ 51+ domain models verified clean | No action |
| ADR-003: SecurePreferences canonical in :shared:security | ✅ Old data-layer interface deleted | Verify in PR #26 (KMP testing) |
| ADR-004: Keystore token scaffold removed | ✅ Scaffold directories deleted | No action |

**New ADRs to create:**
- [ ] **ADR-005: E-Invoice Architecture** — Document mTLS, certificate management, IRD submission lifecycle, retry policy
- [ ] **ADR-006: CRDT Conflict Resolution** — Formalize `version_vectors.sq` strategy, when to auto-merge vs flag for review
- [ ] **ADR-007: Koin Module Load Order** — Formalize the 7-tier order as an ADR (currently only in CLAUDE.md); prevents future boot failures

### B2. Code-Level Documentation Audit

**Check each module for:**
- [ ] Public APIs have KDoc (`/** */`) — required by CONTRIBUTING.md
- [ ] Non-obvious business logic has inline comments
- [ ] Complex SQL queries in `.sq` files have comments explaining intent
- [ ] `expect/actual` pairs in HAL and security have doc on both sides

**Known gaps to fill:**
- [ ] `BaseViewModel.kt` — Document thread-safety guarantees and effect delivery contract
- [ ] `EscPosEncoder.kt` — ESC/POS command constants need doc comments (currently suppressed by Detekt)
- [ ] `ConflictResolver.kt` — CRDT merge algorithm needs design rationale comment
- [ ] `RbacEngine.kt` — Permission matrix table needs inline doc
- [ ] `IrdApiClient.kt` (Sprint 18) — mTLS setup, certificate lifecycle, error codes from IRD spec

### B3. Planning Documentation Alignment

**Verify each Sprint plan against actual implementation:**

| Sprint | Plan Doc | Git Evidence | Status |
|--------|---------|-------------|--------|
| Sprint 1–4 | `Phase3_Sprint1-4.md` | Domain models (51+) committed | ✅ |
| Sprint 5–7 | `Phase3_Sprint5-7.md` | Repo impls + HAL | ✅ |
| Sprint 8–12 | `Phase3_Sprint8-12.md` | Staff feature | ✅ |
| Sprint 13–15 | `Phase3_Sprint13-15.md` | Admin feature | ✅ |
| Sprint 16–17 | `Phase3_Sprint16-17.md` | Media feature | ✅ (commit `cca233b`) |
| Sprint 18 | `Phase3_Sprint18.md` | AccountingLedger MVI + e_invoices | 🔄 In Progress |
| Sprint 19–24 | Future sprint plans | N/A | ⬜ Not started |

**Action:**
- [ ] Read `Phase3_Sprint18.md` — Verify `IrdApiClient`, `EInvoiceRepositoryImpl`, `e_invoices.sq`, `AccountingLedger` MVI are all complete per plan specification
- [ ] Update `sprint_progress.md` after each completed sprint
- [ ] Update `execution_log.md` with Sprint 18 completion notes

### B4. README & CONTRIBUTING Freshness Check

- [ ] `README.md` — Does "Development Phases" section reflect Phase 3 in progress?
- [ ] `CONTRIBUTING.md` — Is the Mockative 3 + KSP 2.3.4 constraint documented?
- [ ] `CLAUDE.md` — Does module map reflect the `accounting` feature addition from Sprint 18?
- [ ] `gradle_commands.md` — Verify all test task examples are current for Sprint 18 modules

---

## PART C — Architecture & Code Quality Audit

### C1. Clean Architecture Compliance

**Run these checks:**
- [ ] Grep for `import com.zyntasolutions.zyntapos.data` inside `shared/domain/` — must be zero results
- [ ] Grep for `import com.zyntasolutions.zyntapos.data` inside `composeApp/feature/*/` — must be zero results
- [ ] Grep for `@Entity` inside `shared/domain/model/` — must be zero results (ADR-002)
- [ ] Grep for `ViewModel()` (direct extension) inside `composeApp/feature/*/` — must be zero results (ADR-001)
- [ ] Grep for `GlobalContext.get()` outside of DI bootstrap code — must be zero results
- [ ] Grep for `loadKoinModules(global=true)` — must be zero (PR #21 migration complete)
- [ ] Verify `kotlinx-datetime` pinned to `0.6.1` in root `build.gradle.kts` resolutionStrategy

**Known minor issues (pre-existing, track for remediation):**
- Flow/Rx debounce pattern repeated in 2+ ViewModels — extract `DEBOUNCE_DELAY_MS = 300L` constant
- `18.dp`, `28.dp`, `16.dp` magic numbers in Compose — Phase 4 design tokens planned

### C2. MVI Pattern Consistency Audit

For each of the 15 feature modules, verify:
- [ ] `*State` is a `data class` with all fields having default values
- [ ] `*Intent` is a `sealed class` (exhaustive `when` in `handleIntent`)
- [ ] `*Effect` is a `sealed class` (one-shot, delivered via Channel)
- [ ] ViewModel does NOT call `viewModelScope.launch {}` directly from UI callbacks — all state changes via `dispatch(intent)`
- [ ] No business logic in ViewModel — only delegates to use cases
- [ ] `updateState { }` used exclusively for state mutations (no direct `_state.value = ...`)

### C3. Unused Code Audit

**Detekt suppressions reviewed:** `UnusedPrivateProperty` and `UnusedParameter` are globally suppressed (intentional for design tokens and Compose callbacks). Audit manually:

- [ ] Search `shared/domain/usecase/` for use cases with zero callers in any feature ViewModel (possible: Phase 2 use cases for scaffolded modules)
- [ ] Search `composeApp/navigation/` for routes with no `composable {}` registration in NavGraph
- [ ] Search HAL interfaces for `expect` declarations without complete `actual` implementations on all targets (Android + JVM)
- [ ] Check `shared/seed/` — are all fixtures still in sync with the current 36-table schema? Tables added in Sprint 8+ may not have seed data
- [ ] Check `tools/debug/` — are all 6 debug tabs functional with current Sprint 18 schema?

### C4. Duplicate Code Audit

**Known patterns to standardize:**

| Pattern | Locations | Recommendation |
|---------|-----------|----------------|
| `combine(debounce(300L)).flatMapLatest` | PosViewModel, InventoryViewModel | Extract `SEARCH_DEBOUNCE_MS` constant to `:shared:core` Constants |
| `repository.getAll().onEach { updateState {...} }.launchIn(viewModelScope)` | All 8 Phase 1 ViewModels | Acceptable; consider `collectAndUpdateState()` extension if > 5 instances |
| `openRegisterSession()` + null guard pattern | RegisterViewModel (expected single instance) | OK |
| Koin feature module template (single `factory { }`) | 8 scaffold modules | Templates OK, will fill in implementation |

### C5. Detekt & Lint Execution

**Run and analyze output:**
```bash
./gradlew detekt lint --parallel --continue > audit_detekt_output.txt 2>&1
```

- [ ] Zero severity `error` findings (blocks CI)
- [ ] Document all `warning` findings and assign owners
- [ ] Verify Detekt baseline (`config/detekt/detekt-baseline.xml`) is current — regenerate if stale
- [ ] Lint: check for deprecated API usage, accessibility issues in Compose

---

## PART D — Security & Compliance Audit (IT Auditor / Compliance Officer)

### D1. Security Implementation Verification

| Control | Implementation | Test Status | Action |
|---------|---------------|-------------|--------|
| DB at rest encryption | SQLCipher AES-256 | Not tested | Add `DatabaseEncryptionTest` |
| Key storage (Android) | Android Keystore | Not tested | Add instrumented test |
| Key storage (JVM) | JCE KeyStore PKCS12 | Not tested | Add jvmTest |
| PIN hashing | PBKDF2 + salt | ✅ 1 test | Add brute-force resistance test |
| JWT validation | Decode + expiry check | ✅ 1 test | Add expired token, tampered token tests |
| RBAC enforcement | RbacEngine | ✅ 1 test | Add exhaustive role × permission matrix test |
| Audit log | `audit_log` table | Not tested | Verify every sensitive operation logs entry |
| IRD mTLS | `IrdCertificateManager` | Not tested | Add cert load / expired cert / wrong password tests |
| Secrets in build | `local.properties` git-ignored | ✅ | Verify `.gitignore` entry present |
| Secure preferences | `SecurePreferences` expect/actual | ✅ 1 test | Add concurrent read/write test |

### D2. GDPR & Data Privacy Compliance

- [ ] Verify customer data export (`GenerateCustomerReportUseCase`) includes all PII fields
- [ ] Verify customer data deletion cascade (if implemented in domain) — checks `CollectionTranslateMap` pattern
- [ ] Confirm audit log is immutable (append-only, no DELETE queries in `audit_log.sq`)
- [ ] Verify no PII in `sync_queue` plain-text payloads (must be encrypted or tokenized)
- [ ] Confirm `ZYNTA_*` secrets never appear in build outputs (check `BuildConfig.java` generated file)

### D3. IRD E-Invoice Compliance (Sri Lanka)

- [ ] Verify invoice schema matches IRD API spec v1 (check `EInvoiceDto.kt` field names and types)
- [ ] Verify SHA-256 digital signature implementation matches IRD signing requirements
- [ ] Verify TIN (Tax Identification Number) validation logic in `ProductValidator` or domain layer
- [ ] Verify IRD response error codes are all handled (ACCEPTED, REJECTED, PENDING, CONNECTION_ERROR)
- [ ] Verify cancelled invoices are immutable (status update only, no deletion)
- [ ] Confirm IRD certificate password is stored in `SecurePreferences`, NOT `local.properties` at runtime

### D4. CI/CD Security Posture

- [ ] Verify GitHub Actions secrets (`RELEASE_KEYSTORE_BASE64`, `DB_ENCRYPTION_PASSWORD`) are not exposed in CI logs
- [ ] Verify `local.properties` is in `.gitignore` and not committed (check `git log --all -- local.properties`)
- [ ] Verify release APK is signed before upload to GitHub Release
- [ ] Verify release workflow cannot run on forks (check `workflow_dispatch` or branch protection)

---

## PART E — KPI Dashboard (Product Analyst / Data Analyst)

### E1. Code Quality KPIs

| KPI | Current | Target | Measurement Method |
|-----|---------|--------|--------------------|
| Test file count | 49 | 150+ | `find . -name "*Test*.kt" | wc -l` |
| Domain use case coverage | ~21/80 (26%) | 95% | `./gradlew :shared:domain:test` + Kover report |
| Data layer repo coverage | ~5/36 (14%) | 80% | `./gradlew :shared:data:jvmTest` + Kover |
| ViewModel coverage | ~7/15 (47%) | 80% | `./gradlew :composeApp:feature:*:test` |
| Security module coverage | ~5/11 components | 95% | `./gradlew :shared:security:test` |
| Detekt violations (error) | Unknown | 0 | `./gradlew detekt` |
| Detekt violations (warning) | Unknown | < 20 | `./gradlew detekt` |
| Lint issues | Unknown | 0 errors | `./gradlew lint` |
| Build time (clean) | Unknown | < 5 min | CI `ci.yml` artifact timing |
| Build time (incremental) | Unknown | < 90 sec | Local benchmark |

### E2. Architecture Compliance KPIs

| KPI | Pass Condition | Check Command |
|-----|---------------|---------------|
| ADR-001: No direct ViewModel extension | 0 occurrences | `grep -r "extends ViewModel()" composeApp/feature/` |
| ADR-002: No *Entity in domain | 0 occurrences | `grep -r "Entity" shared/domain/model/` |
| ADR-003: SecurePreferences in correct module | 0 in :shared:data | `grep -r "SecurePreferences" shared/data/` |
| No cross-layer imports | 0 in feature modules | `grep -r "import.*zyntapos.data" composeApp/feature/` |
| Koin 7-tier load order intact | Manual verification | Read `ZyntaApplication.kt` |
| No hardcoded secrets | 0 in source | `grep -r "ZYNTA_DB_PASSPHRASE\|password" --include="*.kt"` |

### E3. Feature Completeness KPIs

| Phase | Modules | Implemented | Scaffolded | % Complete |
|-------|---------|-------------|-----------|-----------|
| Phase 1 (MVP) | 8 feature | 8 | 0 | 100% |
| Phase 2 (Growth) | 4 feature | 0 | 4 | 0% |
| Phase 3 (Enterprise) | 3+accounting | 3 + partial | 0 | ~80% |
| Infrastructure | 10 | 10 | 0 | 100% |

### E4. Documentation KPIs

| KPI | Current | Target |
|-----|---------|--------|
| ADRs with ACCEPTED status | 4 | 7 (add ADR-005, 006, 007) |
| Public APIs with KDoc | Unknown | 100% of public APIs |
| Sprint plans written ahead of sprint | 17/24 | 24/24 |
| Audit reports current | v3 (Feb 2026) | Updated each sprint |
| CLAUDE.md last updated | Feb 2026 | Within 1 sprint of changes |

---

## PART F — Execution Checklist (Ordered by Priority)

### Sprint 18 Immediate Actions (Before Sprint 19)

- [ ] **QA:** Write `AccountingLedgerViewModelTest` and `EInvoiceRepositoryImplTest` for Sprint 18 deliverables
- [ ] **QA:** Verify `e_invoices.sq` schema matches IRD spec — 9 columns, 4 indexes, 8 queries
- [ ] **Compliance:** Review `IrdApiClient.kt` for mTLS correctness and error code coverage
- [ ] **Arch:** Ensure Sprint 18 accounting feature follows ADR-001/002; check module Koin registration in correct tier
- [ ] **Docs:** Update `sprint_progress.md` with Sprint 18 status
- [ ] **Docs:** Add ADR-005 for E-Invoice Architecture

### Ongoing Each Sprint

- [ ] Run `./gradlew detekt lint --parallel --continue` — zero errors
- [ ] Run `./gradlew test --parallel` — all tests pass
- [ ] Update `execution_log.md` with completed tasks
- [ ] Peer review: verify new ViewModel extends `BaseViewModel`, new domain model has no `*Entity` suffix

### Phase 2 Pre-Kickoff Gate

Before implementing customers/coupons/multistore/expenses UI:
- [ ] All Phase 1 ViewModel tests written (80% coverage target)
- [ ] All Phase 1 use case tests written (95% coverage target)
- [ ] `FakeProductRepository`, `FakeOrderRepository`, `FakeRegisterRepository` created in test sourceSet
- [ ] `checkModules {}` Koin validation test passes for current DI graph

---

## Critical Files for Reference During Audit

| File | Purpose |
|------|---------|
| `shared/domain/src/commonMain/.../usecase/` | 80+ use cases — primary test target |
| `shared/data/src/commonMain/sqldelight/` | 36 `.sq` schema files |
| `composeApp/core/src/commonMain/.../mvi/BaseViewModel.kt` | MVI contract — test atomicity |
| `composeApp/navigation/src/commonMain/.../ZyntaRoute.kt` | RBAC route gating |
| `shared/security/src/commonMain/` | Security primitives |
| `config/detekt/detekt.yml` | Static analysis rules |
| `.github/workflows/ci.yml` | CI pipeline |
| `docs/adr/` | 4 ADRs |
| `docs/plans/phase/p3/Phase3_Sprint18.md` | Current sprint spec |
| `docs/sprint_progress.md` | Implementation status |
| `gradle/libs.versions.toml` | All dependency versions |

---

## Verification — How to Validate This Plan Is Working

```bash
# 1. Run full test suite
./gradlew test allTests --parallel --continue

# 2. Generate test coverage (requires Kover plugin)
./gradlew koverXmlReport

# 3. Run static analysis
./gradlew detekt lint --parallel --continue

# 4. Validate Koin DI graph
./gradlew :composeApp:testDebugUnitTest --tests "*.KoinGraphTest"

# 5. Build all targets
./gradlew assemble :composeApp:run

# 6. Full CI simulation
./gradlew clean test lint assembleDebug :composeApp:packageUberJarForCurrentOS \
          --parallel --continue --stacktrace
```

**Success criteria for audit completion:**
- Test count ≥ 150 files across all modules
- Zero Detekt `error` severity findings
- Zero clean-architecture violations (grep checks pass)
- All 7 ADRs documented and ACCEPTED
- Sprint 18 deliverables verified against `Phase3_Sprint18.md`
- CI pipeline green on `main` branch
