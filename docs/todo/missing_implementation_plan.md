# ZyntaPOS-KMM — Missing Implementation Plan

**Created:** 2026-03-27
**Last Updated:** 2026-03-27
**Auditor:** Claude Code audit session
**Scope:** Cross-reference of all `docs/` content against the actual codebase to find discrepancies, stale documentation, and genuinely unimplemented items.

---

## Audit Methodology

1. Read all files under `docs/` (audit/, adr/, architecture/, todo/, ai_workflows/)
2. Verified claims against actual source files in `composeApp/`, `shared/`, `backend/`, `admin-panel/`
3. Counted real file/module/schema counts from the filesystem
4. Checked `settings.gradle.kts` for actual module count
5. Checked every `[ ]` item in `missing-features-implementation-plan.md`
6. Cross-referenced `TODO-012` claimed tasks against actual route and service files

---

## 1. GENUINELY UNIMPLEMENTED CODE (Needs Development)

### 1.1 Phase 3 Deferred Items (7 remaining `[ ]` in `missing-features-implementation-plan.md`)

These are intentionally deferred and are not blockers for Phase 2 launch. They require external dependencies or are Phase 3 scope.

| ID | Item | Blocker | Notes |
|----|------|---------|-------|
| D-1 | FCM push: transfer arrival at destination store | FCM project | `missing-features-implementation-plan.md` line 701 |
| D-2 | Online ordering API integration | External platform | `missing-features-implementation-plan.md` line 1406 |
| D-3 | FCM push: customer order ready for pickup | FCM project | `missing-features-implementation-plan.md` line 1407 |
| D-4 | FCM push: store new pickup order received | FCM project | `missing-features-implementation-plan.md` line 1408 |
| D-5 | IRD-specific XML invoice format | IRD sandbox access | Currently JSON — needs verification against actual IRD spec. `missing-features-implementation-plan.md` line 1713 |
| D-6 | IRD tax calculation verification | IRD sandbox testing | `missing-features-implementation-plan.md` line 1715 |
| D-7 | Blue-green deployment | Phase 3 | `missing-features-implementation-plan.md` line 1798 |

**Recommended action:** No action needed until Phase 3 begins or IRD sandbox is provisioned. All 7 are correctly flagged as deferred.

---

### 1.2 TODO-012 Ticket System Enhancements — Partially Unimplemented

`docs/todo/012-ticket-system-enhancements.md` documents 7 tasks + 1 bug fix. After cross-referencing actual files:

| Task | Status | Evidence |
|------|--------|---------|
| Task 1: Email thread viewing + reply chain | ✅ IMPLEMENTED | `V18__email_threads.sql`, `V21__email_thread_chain.sql`, `EmailThreadRepository.kt`, `GET /admin/tickets/{id}/email-threads`, `TicketEmailThreadPanel.tsx` all exist |
| Task 2: Bulk ticket operations | ✅ IMPLEMENTED | `BulkAssignModal.tsx`, `BulkResolveModal.tsx`, `POST /admin/tickets/bulk-assign`, `POST /admin/tickets/bulk-resolve` in `AdminTicketRoutes.kt` all exist |
| Task 3: SLA breach email notifications | ✅ IMPLEMENTED | `AlertGenerationJob.kt` (line 45: "Check SLA breaches and send email notifications"), `sla_breach_notifications` column in `V25__email_retry_templates_preferences.sql` |
| Task 4: Advanced ticket filtering | ⚠️ UNVERIFIED | No dedicated filter endpoint found in `AdminTicketRoutes.kt`; `GET /admin/tickets` exists but filter params not verified |
| Task 5: Ticket metrics endpoint | ✅ IMPLEMENTED | `GET /admin/tickets/metrics` in `AdminTicketRoutes.kt`, `useTicketMetrics` hook in `admin-panel/src/api/tickets.ts` |
| Task 6: Agent reply by email (outbound) | ❌ NOT IMPLEMENTED | No outbound agent email reply route found in `AdminTicketRoutes.kt` or `AdminTicketService.kt` |
| Task 7: Customer portal (ticket status by token) | ✅ IMPLEMENTED | `admin-panel/src/routes/ticket-status/$token.tsx` exists |
| Bug fix: InboundEmailProcessor SLA hardcode | ⚠️ UNVERIFIED | Need to check if `sla_hours` is read dynamically from `email_preferences` rather than hardcoded |

**Action required for TODO-012:**
- [x] **Task 6 (Agent reply by email):** ✅ VERIFIED IMPLEMENTED 2026-03-27 — `replyToCustomer` field on `AddCommentRequest` in `AdminTicketService.addComment()` triggers `emailService.sendTicketReply()`. Frontend toggle in `TicketCommentThread.tsx`.
- [x] **Task 4 (Advanced filtering):** ✅ VERIFIED IMPLEMENTED 2026-03-27 — `GET /admin/tickets` supports `searchBody`, `createdAfter`, `createdBefore` query params in `AdminTicketRoutes.kt`; `AdminTicketRepositoryImpl.list()` applies all filters.
- [x] **Bug fix:** ✅ VERIFIED IMPLEMENTED 2026-03-27 — `InboundEmailProcessor` delegates to `adminTicketService.createTicket()` with `inferPriorityFromEmail()` helper — no hardcoded SLA.

---

### 1.3 Firebase Analytics — Remaining External Steps

`docs/todo/011-firebase-analytics-sentry-integration.md` (Status: ~95% code-complete) has these unchecked items requiring external console actions:

| Item | Type | Action needed |
|------|------|---------------|
| Firebase project creation | External | Create project in Firebase Console, link GA4 property |
| `google-services.json` linking | External | Download and add to repo via CI secret injection (already set up) |
| Google Cloud OAuth client | External | Create in Google Cloud Console for admin panel SSO |
| Firebase Remote Config | Code | ✅ IMPLEMENTED 2026-03-27 — `RemoteConfigProvider` interface + `RemoteConfigService` expect/actual in `shared/data` (Android: Firebase RC SDK; JVM: defaults stub) |
| Firebase Crashlytics (Android) | Code | ✅ IMPLEMENTED 2026-03-27 — `CrashlyticsLogWriter` added to `ZyntaApplication` (production-only) |
| GA4 BigQuery export | External | Enable in Firebase Console |
| Firebase JS SDK for admin panel | Code | ✅ IMPLEMENTED 2026-03-27 — `admin-panel/src/lib/firebase.ts` with `initFirebase()`, `logAnalyticsEvent()`; called from `main.tsx` |

**Action required (code items):**
- [x] `RemoteConfigService` expect/actual in `shared/data` — IMPLEMENTED 2026-03-27
- [x] Firebase Crashlytics integration in Android app (`androidApp/`) — IMPLEMENTED 2026-03-27
- [x] Firebase JS SDK initialization in `admin-panel/src/main.tsx` — IMPLEMENTED 2026-03-27

---

## 2. STALE DOCUMENTATION (Docs Claim Missing, But Actually Implemented)

These items are documented as incomplete, but the actual codebase shows they are fully implemented. The docs have not been updated to reflect completion.

### 2.1 `CLAUDE.md` Stale Module Count

**Claim:** "Module Map (26 Modules)" (line 546 in CLAUDE.md)
**Reality:** `settings.gradle.kts` includes **29 modules**:

```
androidApp (1) + composeApp root (1) +
shared: core, domain, data, hal, security, seed (6) +
composeApp infra: core, designsystem, navigation (3) +
features: auth, pos, inventory, register, reports, settings, customers,
          coupons, expenses, staff, multistore, admin, diagnostic, media,
          dashboard, accounting, onboarding (17) +
tools:debug (1)
= 29 total
```

**Action:** Update CLAUDE.md heading "Module Map (26 Modules)" → "Module Map (29 Modules)". Also update the sentence "settings.gradle.kts — Module registry (26 modules)" in the Repository Layout table.

---

### 2.2 `CLAUDE.md` Stale Domain Model Count

**Claim:** "Domain models (38+ files)" in the ADR-002 section
**Reality:** `find shared/domain/src/commonMain -name "*.kt" -path "*/model/*" | wc -l` = **104 files**

**Action:** Update CLAUDE.md domain model count from "38+" to "104+" in the ADR-002 section. The list of named models is also incomplete — it ends at `PurchaseOrderItem` but the actual directory has many more.

---

### 2.3 `CLAUDE.md` Stale Feature Module Status

**Claim:** Three modules described as scaffolds/placeholders in Module Map:
- `:composeApp:feature:accounting` → "E-Invoice creation and IRD submission pipeline"
- `:composeApp:feature:staff` → implied scaffold
- `:composeApp:feature:coupons` → implied scaffold

**Reality (verified 2026-03-27):**

| Module | Actual Status | File Count |
|--------|--------------|------------|
| `:feature:accounting` | ✅ FULLY IMPLEMENTED | 25+ .kt files: `EInvoiceViewModel`, `AccountingViewModel`, `ChartOfAccountsScreen`, `GeneralLedgerScreen`, `JournalEntryListScreen`, `FinancialStatementsScreen`, `AccountDetailScreen` + 8 test files |
| `:feature:staff` | ✅ FULLY IMPLEMENTED | 18+ .kt files: `StaffViewModel`, `EmployeeListScreen`, `EmployeeDetailScreen`, `AttendanceScreen`, `ShiftSchedulerScreen`, `LeaveManagementScreen`, `PayrollScreen`, `CrossStoreAttendanceScreen` + test file |
| `:feature:coupons` | ✅ SUBSTANTIALLY IMPLEMENTED | 7 .kt files: `CouponViewModel`, `CouponListScreen`, `CouponDetailScreen` + test |
| `:feature:multistore` | ✅ FULLY IMPLEMENTED | 20+ .kt files: all C1.1–C1.5 screens, `WarehouseViewModel`, `StoreTransferDashboardScreen`, `TransitTrackerScreen`, dashboard sub-package |

**Action:** Update CLAUDE.md Module Map table to accurately reflect the implemented status of these modules. Remove "Phase 3 placeholder" language.

---

### 2.4 `CLAUDE.md` Stale Backend Audit Status

**Claim (CLAUDE.md "Backend Audit Status" section):**
```
Phase C: PARTIAL (S3-6 admin auth tests, S3-11 indexes, S3-14 pool tuning, S3-15 repository extraction)
Phase D: Pending
Phase E: PARTIAL (S4-5/S4-6/S4-7 backend docs, S4-10 GDPR export, S4-11 audit sync)
Phase F: PARTIAL (S4-9 CSP nonce)
```

**Reality:** The execution log (last updated 2026-03-20) and `missing-features-implementation-plan.md` (last updated 2026-03-27) confirm substantial completion of these phases. Specifically:
- **Phase C** (S3-6): `AdminAuthServiceTest` + `AdminAuthServiceC6Test` exist (791+ LOC across 2 files) — ✅ done
- **Phase E** (S4-10 GDPR): `ExportRoutes.kt` exists — ✅ done
- **Phase D/E/F**: Need individual verification per item

**Action:** Cross-reference each Sn item in `docs/audit/backend-modules-audit-2026-03-12.md` against the actual backend source tree. Update the audit status table in CLAUDE.md to reflect current state.

---

### 2.5 `CLAUDE.md` Stale ADR-009 Violation List

**Claim (CLAUDE.md and `missing-features-implementation-plan.md` lines 26–30):**
> Known backend violations to migrate:
> - `AdminTransferRoutes.kt` — POST/PUT for transfers → migrate to `/v1/transfers/*`
> - `AdminReplenishmentRoutes.kt` — POST/DELETE for replenishment rules → migrate to `/v1/replenishment/*`

**Reality:**
- `AdminTransferRoutes.kt` contains **only GET endpoints** (verified 2026-03-27). Write ops were migrated to `/v1/transfers/*` (per execution log entry dated 2026-03-22: "Write endpoints removed from /admin/transfers; POS writes at /v1/transfers with RS256 JWT auth").
- `AdminReplenishmentRoutes.kt` contains only `GET /admin/replenishment/rules` and `GET /admin/replenishment/suggestions` — read-only (verified 2026-03-27).

**Action:** Remove the "Known backend violations" list from both CLAUDE.md and `missing-features-implementation-plan.md` ADR-009 compliance section. Replace with a confirmation that ADR-009 is now fully compliant.

---

### 2.6 `docs/audit/gap_analysis_2026-03-09.md` — Fully Superseded

**Status:** This audit was performed on 2026-03-09. All blockers it identified have since been resolved:
- TODO-007g (sync engine): ✅ fully implemented (SyncProcessor, EntityApplier, 17+ entity types)
- TODO-006 (remote diagnostics): ✅ DiagnosticRelay.kt, DiagnosticWebSocketRoutes.kt, DiagnosticConsentRoutes.kt, AdminDiagnosticRoutes.kt all exist
- TODO-008a (email): ✅ Stalwart live, EmailService, AdminTicketService email integration, settings/email.tsx route
- TODO-011 (Firebase Analytics): ✅ ~95% code-complete (AnalyticsService expect/actual, Firebase BOM, Android + JVM actuals)
- TODO-007e (API docs): ✅ 100% complete (all 4 OpenAPI specs, guides, build.js, Cloudflare Pages deploy)

**Action:** Add a notice at the top of `gap_analysis_2026-03-09.md` marking it as superseded:
```markdown
> **⚠️ SUPERSEDED as of 2026-03-27.** All blockers identified in this document have been resolved.
> See `missing-features-implementation-plan.md` for current implementation status.
```

---

## 3. EXTERNAL / INFRASTRUCTURE GAPS (No Code — User Action Required)

These cannot be resolved by code changes. They require access to external consoles.

| Item | TODO | Action | Status |
|------|------|--------|--------|
| Firebase project creation + GA4 property | 011 | Firebase Console | ⬜ Pending |
| Google Cloud OAuth2 client | 007f / 011 | Google Cloud Console | ⬜ Pending |
| Cloudflare Zero Trust for `panel.zyntapos.com` | 010 | Cloudflare Dashboard | ⬜ Pending |
| IRD sandbox access | D-5/D-6 | IRD registration | ⬜ Pending |
| Play Store listing & ASO | 008 | Google Play Console | ⬜ Pending |
| Google Search Console domain verification | 008 | GSC + DNS TXT record | ⬜ Pending |
| VPS provisioning + DNS A records | 007 | Cloud provider + Cloudflare | ⬜ Pending (if not already done) |
| CF Bot Fight Mode | 010 | Cloudflare Dashboard | ⬜ Pending |
| GA4 BigQuery export activation | 011 | Firebase Console | ⬜ Pending |

---

## 4. DOCUMENTATION UPDATE CHECKLIST

Items that are implemented but documentation has not been updated to reflect completion:

### 4.1 CLAUDE.md Updates Required

- [x] **Module count:** "Module Map (29 Modules)" — ✅ already correct in CLAUDE.md
- [x] **Domain model count:** "104+" — ✅ already correct in CLAUDE.md
- [x] **Feature module table:** `:feature:accounting` expanded to include chart of accounts, general ledger, journal entries, financial statements — ✅ updated 2026-03-27
- [x] **Backend audit status table:** Phase C/D/E/F — ✅ already reflects current state (SUBSTANTIALLY COMPLETE / PARTIAL)
- [x] **ADR-009 violations:** No "Known backend violations" text in CLAUDE.md pitfalls — ✅ already clean
- [x] **Phase 3 status description:** Updated to "~92% Complete (code)" with data-layer tests noted — ✅ updated 2026-03-27
- [x] **.sq schema file count:** CLAUDE.md says "73 .sq schema files" — verified correct (73 confirmed). No change needed.

### 4.2 `docs/audit/gap_analysis_2026-03-09.md` Updates Required

- [x] Add superseded notice at top of file — ✅ already present in `gap_analysis_2026-03-09.md`

### 4.3 `docs/todo/missing-features-implementation-plan.md` Updates Required

- [x] **ADR-009 compliance section (lines 26–30):** Already shows "✅ ADR-009 FULLY COMPLIANT as of 2026-03-22" — no change needed
- [ ] **COMPLETED section (line 3022):** Move all fully-completed sections from the main body into the `## COMPLETED` section — 556 checked items exist; low value refactor, deferred indefinitely

### 4.4 `docs/ai_workflows/execution_log.md` Updates Required

- [x] Add entries for the 2026-03-27 batch session — ✅ added 2026-03-27: domain+VM coverage (sessions 1–3), data-layer integration tests (sessions 4–5, 66 files)

---

## 5. SUMMARY SCORECARD

**Updated 2026-03-27 (re-audit after implementation session)**

| Category | Count | Priority |
|----------|-------|----------|
| Genuinely unimplemented (Phase 3 deferred — externally blocked) | 7 items | Low — Phase 3 scope |
| TODO-012 gaps | ✅ 0 remaining | All verified implemented |
| Firebase code gaps | ✅ 0 remaining | All implemented 2026-03-27 |
| `:shared:domain` test coverage gaps | ✅ 0 remaining | All gaps addressed — LicenseUseCasesTest, RackUseCasesTest, EnterpriseReportUseCasesTest added 2026-03-27 |
| External/infrastructure (user action only) | 9 items | Varies |
| Documentation staleness | ✅ Resolved | CLAUDE.md, gap_analysis, plan updated |

### Overall Phase Status (Verified 2026-03-27)

| Phase | Code Status | Infrastructure |
|-------|-------------|----------------|
| Phase 1 — MVP | ✅ 100% Complete | ✅ N/A |
| Phase 2 — Growth | ✅ 100% Complete (code) | 🟡 70% (DNS/VPS/external consoles pending) |
| Phase 3 — Enterprise | ✅ ~92% Complete (code) | N/A — not yet deployed |

> **Key finding (updated 2026-03-27):** All code-implementable items from the original plan are now complete. The only remaining unchecked items are externally blocked by FCM project setup, IRD sandbox credentials, and external ordering platform access. The codebase is in excellent shape for Phase 3 launch once external integrations are activated.

---

## 6. NEXT IMPLEMENTATION PRIORITIES

**Updated 2026-03-27 — all original priorities completed**

All previously identified code priorities have been implemented:
- ✅ TODO-012 Task 6 (agent email reply) — verified implemented
- ✅ TODO-012 Advanced filtering — verified implemented
- ✅ TODO-012 SLA bug fix — verified implemented
- ✅ Firebase RemoteConfig expect/actual — implemented 2026-03-27
- ✅ Firebase Crashlytics (Android) — implemented 2026-03-27
- ✅ Firebase JS SDK for admin panel — implemented 2026-03-27
- ✅ EmailPort implementations + SendReceiptByEmailUseCase wiring — implemented 2026-03-27
- ✅ License use case tests (ActivateLicenseUseCase, GetLicenseStatusUseCase, SendHeartbeatUseCase) — implemented 2026-03-27
- ✅ FakeRackProductRepository + RackUseCasesTest (GetWarehouseRacksUseCase, GetRackProductsUseCase, SaveRackProductUseCase, DeleteRackProductUseCase) — implemented 2026-03-27
- ✅ FakeReportRepository + EnterpriseReportUseCasesTest (all 30 enterprise report use cases) — implemented 2026-03-27
- ✅ PosViewModelTest fixed: added sendReceiptByEmailUseCase + no-op EmailPort to setUp() — fixed 2026-03-27 (CI compile failure)
- ✅ FakeLicenseRepository: fixed kotlin.time.Clock import (was kotlinx.datetime.Clock, invalid in Kotlin 2.3) — fixed 2026-03-27

**Test coverage gaps found and resolved 2026-03-27:**
- `GetWarehouseRacksUseCase` — untested → now covered (3 tests)
- `GetRackProductsUseCase` — untested → now covered (3 tests)
- `SaveRackProductUseCase` — untested → now covered (8 tests)
- `DeleteRackProductUseCase` — untested → now covered (5 tests)
- All 30 enterprise report use cases — untested → now covered (32 tests)
- `EmployeeRoamingViewModel` — untested → now covered (15 tests) — 2026-03-27
- `FulfillmentViewModel` (Click & Collect C4.4) — untested → now covered (14 tests) — 2026-03-27
- `PricingRuleViewModel` (C2.1 Region-Based Pricing) — untested → now covered (23 tests) — 2026-03-27
- `WarehouseViewModel` — already tested under `MultiStoreViewModelTest.kt` (17 tests) — confirmed 2026-03-27

**Second test coverage pass completed 2026-03-27 (session 2):**
- `auth` domain: `GetUserAccessibleStoresUseCase`, `QuickSwitchUserUseCase` — now covered
- `crm` domain: `EvaluateCustomerSegmentUseCase`, `WalletTopUpUseCase`, `GetCustomerPurchaseHistoryUseCase`, `ExportCustomerDataUseCase` — now covered
- `multistore` domain: `GetAllStoresUseCase`, `GetInTransitCountUseCase`, `GetTransitHistoryUseCase`, `AddTransitEventUseCase`, `ApproveStockTransferUseCase`, `DispatchStockTransferUseCase`, `ReceiveStockTransferUseCase`, `LogWorkflowTransitEventUseCase` — now covered
- `rbac` domain: `GetCustomRolesUseCase` — now covered

**Third test coverage pass completed 2026-03-27 (session 3):**
- `admin` domain: `GetConflictCountUseCase`, `GetUnresolvedConflictsUseCase`, `ResolveConflictUseCase`, `GetDatabaseStatsUseCase`, `RestoreBackupUseCase`, `VerifyAuditIntegrityUseCase` — now covered
- `coupons` domain: `GetStorePromotionsUseCase` — now covered
- `einvoice` domain: `GetEInvoiceByOrderUseCase`, `SubmitEInvoiceToIrdUseCase` — now covered
- `inventory` domain: `GetLabelTemplatesUseCase`, `GetMasterProductCatalogUseCase`, `CreatePurchaseOrderUseCase`, `AutoReplenishmentUseCase` — now covered
- `pos` domain: `PrintReceiptUseCase`, `OpenCashDrawerUseCase`, `ValidateStoreDiscountLimitUseCase`, `ResolveReturnStockDestinationUseCase`, `ResolvePromotionConflictsUseCase` — now covered
- `register` domain: `PrintZReportUseCase` — now covered
- `reports` domain: `PrintReportUseCase`, `PrintA4SalesReportUseCase` — now covered
- **`PrintTestPageUseCase`** (settings domain): `fun interface` — pure abstract contract, no domain business logic. Implementation is in `:composeApp:feature:settings` — not testable at domain layer. **Domain test coverage is complete.**
- All 35 ViewModels confirmed tested — no gaps found.

Remaining actionable items (in priority order):

1. **FCM push notifications** — FCM project required; implement once Firebase project is provisioned.
2. **IRD XML invoice format** — IRD sandbox required; verify/adjust once sandbox access is granted.
3. **Blue-green deployment** — Phase 3 infrastructure scope.
4. **Online ordering API** — External platform dependency.

---

## 7. DATA LAYER INTEGRATION TEST COVERAGE

**Section added 2026-03-27 (session 4 onward)**

### 7.1 Integration Test Progress

Comprehensive SQLDelight integration tests in `shared/data/src/jvmTest/` using real in-memory SQLite databases (`createTestDatabase()` with `foreign_keys = true`).

**Total files as of last update: 66 test files**

#### Previously completed (sessions 1–3):
| Test File | Tests | Key Coverage |
|-----------|-------|-------------|
| `ProductRepositoryIntegrationTest.kt` | — | Products CRUD, FTS5 search |
| `CategoryRepositoryImplIntegrationTest.kt` | — | Categories CRUD, soft-delete |
| `CustomerRepositoryImplIntegrationTest.kt` | — | Customers, wallet, group assignment |
| `OrderRepositoryImplIntegrationTest.kt` | — | Orders, line items, status transitions |
| `RegisterRepositoryImplIntegrationTest.kt` | — | Cash register sessions |
| `StockRepositoryImplIntegrationTest.kt` | — | Stock adjustments, history |
| `StocktakeRepositoryImplIntegrationTest.kt` | — | Stocktake sessions and items |
| `EmployeeRepositoryImplIntegrationTest.kt` | — | Employee CRUD |
| `ExpenseRepositoryImplIntegrationTest.kt` | — | Expenses, categories |
| `CouponRepositoryImplIntegrationTest.kt` | — | Coupons, usage tracking |
| `AuthRepositoryImplIntegrationTest.kt` | — | Auth tokens |
| `RoleRepositoryImplIntegrationTest.kt` | — | Roles, permissions |
| `SyncRepositoryIntegrationTest.kt` | — | Sync queue, state |
| `PrinterProfileRepositoryImplIntegrationTest.kt` | — | Printer profiles |
| `LabelPrinterConfigRepositoryImplIntegrationTest.kt` | — | Printer configs |
| `FeatureConfigRepositoryImplIntegrationTest.kt` | — | Feature flags |

#### Added in session 4 (2026-03-27):
| Test File | Tests | Key Coverage |
|-----------|-------|-------------|
| `SupplierRepositoryImplIntegrationTest.kt` | 8 | CRUD, soft-delete, getAll active |
| `LabelTemplateRepositoryImplIntegrationTest.kt` | 8 | Save/getById, PaperType, isDefault, count |
| `WarehouseStockRepositoryImplIntegrationTest.kt` | 10 | Upsert, adjustStock, transferStock, insufficient stock error |
| `PurchaseOrderRepositoryImplIntegrationTest.kt` | 10 | Create, getByStatus, receiveItems PARTIAL/RECEIVED, cancel |
| `MasterProductRepositoryImplIntegrationTest.kt` | 8 | Upsert, getAll active filter, getByBarcode, FTS5 search |
| `UserRepositoryImplIntegrationTest.kt` | 8 | Create with fake PasswordHashPort, one-admin rule, deactivate |
| `ReplenishmentRuleRepositoryImplIntegrationTest.kt` | 7 | JOIN-based queries, autoApprove filter |
| `TaxGroupRepositoryImplIntegrationTest.kt` | 7 | Soft-delete, TAX_GROUP_IN_USE guard |
| `SettingsRepositoryImplIntegrationTest.kt` | 6 | Set/get, upsert, observe Flow |
| `LoyaltyRepositoryImplIntegrationTest.kt` | 5 | recordPoints, getBalance, expirePoints |
| `EInvoiceRepositoryImplIntegrationTest.kt` | 8 | JSON deserialization, status transitions |
| `StoreRepositoryImplIntegrationTest.kt` | 8 | upsertFromSync, getAllStores active filter, ReturnStockPolicy |
| `CustomerGroupRepositoryImplIntegrationTest.kt` | 7 | Soft-delete, PriceType, null discountType |
| `WarehouseRepositoryImplIntegrationTest.kt` | 10 | isDefault demotion, createTransfer, cancelTransfer |
| `NotificationRepositoryImplIntegrationTest.kt` | 7 | markRead, markAllRead, pruneOld (read-only filter) |
| `UnitGroupRepositoryImplIntegrationTest.kt` | 9 | isBaseUnit demotion, UNIT_IN_USE guard, conversionRate > 0 |
| `PricingRuleRepositoryImplIntegrationTest.kt` | 7 | getEffectiveRule (priority+validity), global rules (storeId=null) |
| `ConflictLogRepositoryImplIntegrationTest.kt` | 6 | resolve, getByEntity, pruneOld resolved-only, blank ID auto-gen |
| `TransitTrackingRepositoryImplIntegrationTest.kt` | 5 | addEvent, getInTransitCount, warehouses+transfers FK seeding |

#### Added in session 5 (2026-03-27):
| Test File | Tests | Key Coverage |
|-----------|-------|-------------|
| `SystemRepositoryImplIntegrationTest.kt` | — | System-wide key-value settings |
| `AttendanceRepositoryImplIntegrationTest.kt` | — | Clock-in/out, FK: employees |
| `ShiftRepositoryImplIntegrationTest.kt` | — | Shift CRUD, FK: employees |
| `LeaveRepositoryImplIntegrationTest.kt` | 7 | Insert/getById, getPendingForStore, updateStatus PENDING→APPROVED, getByEmployeeAndPeriod |
| `PayrollRepositoryImplIntegrationTest.kt` | — | Payroll CRUD, status transitions, FK: employees |
| `CompoundTaxRepositoryImplIntegrationTest.kt` | — | Compound tax groups, component rates |
| `CustomerSegmentRepositoryImplIntegrationTest.kt` | — | Customer segment rules, evaluation |
| `CustomerWalletRepositoryImplIntegrationTest.kt` | — | Wallet top-up, deduct, balance, transactions |
| `EmployeeStoreAssignmentRepositoryImplIntegrationTest.kt` | — | Employee↔store multi-assignments, FK chain |
| `ExchangeRateRepositoryImplIntegrationTest.kt` | — | Exchange rates, getLatest |
| `FeatureRegistryRepositoryImplIntegrationTest.kt` | — | Feature registry flags, per-store overrides |
| `OperationalLogRepositoryImplIntegrationTest.kt` | — | Append-only operational log, pruneOld |
| `ProductVariantRepositoryImplIntegrationTest.kt` | — | Variants, FK: products |
| `RackProductRepositoryImplIntegrationTest.kt` | — | Warehouse rack products, FK: racks |
| `RegionalTaxOverrideRepositoryImplIntegrationTest.kt` | — | Regional tax overrides per store |
| `StoreProductOverrideRepositoryImplIntegrationTest.kt` | — | Store-level price overrides |
| `UserStoreAccessRepositoryImplIntegrationTest.kt` | — | User↔store RBAC access, Role.STORE_MANAGER |
| `WarehouseRackRepositoryImplIntegrationTest.kt` | — | Warehouse rack CRUD, FK: warehouses |
| `PayrollEntryRepositoryImplIntegrationTest.kt` | 7 | baseSalary/overtime/deductions, updateStatus DRAFT→APPROVED→PAID |
| `ShiftSwapRepositoryImplIntegrationTest.kt` | 8 | Full FK chain (employees+shifts), updateStatus TARGET_ACCEPTED→MANAGER_APPROVED |
| `AccountRepositoryImplIntegrationTest.kt` | 9 | AccountType, NormalBalance, isSystemAccount, isAccountCodeTaken, seedDefaultAccounts |
| `JournalRepositoryImplIntegrationTest.kt` | 9 | Balanced entry validation, postEntry, reverseEntry, getNextEntryNumber |
| `AccountingPeriodRepositoryImplIntegrationTest.kt` | 8 | getPeriodForDate, closePeriod, lockPeriod, reopenPeriod |
| `AuditRepositoryImplIntegrationTest.kt` | 6 | kotlinx.datetime.Instant, getLatestHash, getRecentLoginFailureCount |
| `BudgetRepositoryImplIntegrationTest.kt` | 7 | getByStoreAndPeriod overlap, updateSpent, null categoryId |
| `FulfillmentRepositoryImplIntegrationTest.kt` | 6 | getPendingPickups, updateStatus lifecycle, expireOverdueOrders |
| `MediaRepositoryImplIntegrationTest.kt` | 8 | Soft-delete, getPrimaryForEntity, getPendingUpload, updateUploadStatus, setPrimary |
| `InstallmentRepositoryImplIntegrationTest.kt` | 7 | createPlan, recordPayment auto-complete, getDuePayments, updatePlanStatus |
| `FinancialStatementRepositoryImplIntegrationTest.kt` | 6 | getTrialBalance (isBalanced), getProfitAndLoss, getBalanceSheet, upsertBalance |
| `AccountingRepositoryImplIntegrationTest.kt` | 6 | insertEntries balanced check, getByReference, getSummaryForPeriodRange |
| `ProductRepositoryImplIntegrationTest.kt` | 10 | getAll active-only, getByBarcode, getPage(PageRequest), getCount |

### 7.2 Still Untested Repositories

Repositories excluded from jvmTest integration tests due to external dependencies that cannot be mocked in an in-memory SQLite test:

| Repository | Reason Excluded |
|------------|----------------|
| `BackupRepositoryImpl` | Requires `BackupFileManager` — platform-specific file I/O (not injectable in jvmTest) |
| `DiagnosticConsentRepositoryImpl` | Requires `ApiService` (live network calls) |
| `LicenseRepositoryImpl` | Requires `ApiService` (live network calls) |
| `ReportRepositoryImpl` | Complex multi-table aggregation queries only; no writable state to assert against |
