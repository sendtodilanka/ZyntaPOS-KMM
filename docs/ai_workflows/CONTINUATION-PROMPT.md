# ZyntaPOS-KMM — Continuation Prompt for New Claude Code Sessions

**Last Updated:** 2026-03-13
**Purpose:** Copy-paste this prompt into a new Claude Code session to continue implementation work. Each session should pick up where the last left off.

---

## THE PROMPT

Copy everything below the line into a new Claude Code session:

---

```
You are continuing implementation work on ZyntaPOS-KMM, a Kotlin Multiplatform POS system. Read CLAUDE.md first for full architecture context.

## Current Status (as of 2026-03-13)

Phase 1: COMPLETE (5/5 TODOs done)
Phase 2: ~87% complete
Sprint 1 (Admin Panel Session Stability): COMPLETE
Sprint 2 (Backend Cross-Module Alignment): 87% COMPLETE (13/15 tasks done)
Sprint 3 (Test Coverage & Performance): NOT STARTED
Sprint 4 (Documentation & Compliance): NOT STARTED

## YOUR TASK: Complete ALL remaining work items below in priority order

Work through these items sequentially. For each item: implement, run `./gradlew assemble` to verify compilation, commit with conventional commits format, and push. Follow the CLAUDE.md pre-commit sync ritual (fetch + merge origin/main) before EVERY commit. Monitor the 7-step CI/CD pipeline after each push before starting the next item.

Use `curl` with `$PAT` for GitHub API — NOT `gh` CLI (git remote is a local proxy).

---

### BLOCK 1: Deferred Sprint 2 Tasks (2 items, ~7 hrs)

**S2-3: Timestamp Standardization**
- Files: `backend/api/src/main/kotlin/.../service/AdminAuthService.kt`, `UserService.kt`, WebSocket message classes
- Change: Replace all `System.currentTimeMillis()` with `Instant.now()` (kotlinx-datetime) for UTC consistency
- Why: Mixed timestamp formats cause TTL drift across services
- Verify: `cd backend && ./gradlew :api:test :sync:test`

**S2-12: License Validation in API Auth Flow**
- Files: `backend/api/src/main/kotlin/.../service/UserService.kt` or `AuthRoutes.kt`, new `LicenseValidationClient.kt`
- Change: API auth flow should call License service to verify license is active before granting access
- Why: Revoked licenses currently still grant API access
- Verify: `cd backend && ./gradlew :api:test`

---

### BLOCK 2: Sprint 3 — Test Coverage & Performance (~64 hrs)

**Do these in order:**

**S3-1: Add Testcontainers (PostgreSQL + Redis)**
- Add `testcontainers` dependency to all 3 backend `build.gradle.kts` files
- Create shared test base class with PostgreSQL + Redis containers
- This is the prerequisite for all integration tests below

**S3-2: Add MockK to test dependencies**
- Add MockK to all 3 backend `build.gradle.kts` test dependencies

**S3-15: Extract repository layer from API service classes**
- Create repository interfaces + impls for Admin, User, Product data access
- Move SQL/Exposed queries out of service classes into repositories
- This enables unit testing of services with mocked repositories
- Files: `backend/api/src/main/kotlin/.../repository/` (some already exist — extend)

**S3-3: License service tests**
- File: `backend/license/src/test/kotlin/.../LicenseServiceTest.kt` (rewrite — current tests are trivial)
- Cover: activate, heartbeat, status check, grace period expiry, device limit
- Target: >60% coverage

**S3-4: API POS auth tests**
- Files: new `UserServiceTest.kt`, `AuthRoutesTest.kt`
- Cover: login, refresh, lockout after 5 failed attempts, token revocation
- Target: >40% coverage

**S3-5: API sync integration tests**
- File: expand `SyncPushPullIntegrationTest.kt`
- Cover: full push + pull cycle with actual DB, conflict detection, EntityApplier for all entity types
- Target: end-to-end sync flow verified

**S3-6: Admin auth tests**
- File: expand `AdminAuthServiceExtendedTest.kt`
- Cover: MFA setup, MFA verify, password reset flow, session management
- Target: MFA flow fully covered

**S3-7: Sync WebSocket integration tests**
- File: expand `WebSocketHubTest.kt`
- Cover: connection lifecycle, store-scoped isolation, Redis pub/sub relay

**S3-8: License admin CRUD tests**
- New test files for admin license management operations

**S3-9: Common module validation tests**
- File: new `ValidationScopeTest.kt`
- Target: 0% → >80%

**S3-10: API route-level auth enforcement tests**
- Verify all protected routes return 401/403 without valid auth

**S3-11: Add composite index on sync_operations**
- New Flyway migration: `CREATE INDEX idx_sync_ops_store_entity ON sync_operations(store_id, entity_type, entity_id)`

**S3-12: Batch sync push transactions**
- File: `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt`
- Wrap multiple operations in a single transaction instead of one-per-op

**S3-13: Redis connection pooling for API**
- File: `backend/api/src/main/kotlin/.../di/AppModule.kt`

**S3-14: Make License HikariCP pool configurable**
- File: `backend/license/src/main/kotlin/.../DatabaseFactory.kt`
- Make pool size configurable via env var, default 10

---

### BLOCK 3: Sprint 4 — Documentation, Observability & Compliance (~40 hrs)

**S4-1: Prometheus/Micrometer metrics**
- Add to all 3 backend services
- Add `/metrics` endpoint for Prometheus scraping

**S4-2: Request correlation ID middleware**
- Add `X-Request-Id` header propagation across all 3 services
- Include in Redis messages for cross-service tracing

**S4-3: Structured logging (MDC)**
- Replace string interpolation logging with structured MDC-based logging

**S4-4: Generate OpenAPI spec**
- Use Ktor OpenAPI plugin or manually write specs for all routes
- Output to `docs/api/`

**S4-5: Update CLAUDE.md backend section**
- Ensure backend architecture section reflects current state

**S4-7: Document backend database schemas**
- New file documenting all Flyway migration tables

**S4-8: HSTS + Permissions-Policy headers**
- File: `admin-panel/nginx.conf`

**S4-9: CSP nonce-based scripts**
- Files: `admin-panel/nginx.conf`, `admin-panel/vite.config.ts`
- Replace `unsafe-inline` with nonce-based script loading

**S4-10: GDPR customer data export (CRITICAL for launch)**
- Implement `ExportCustomerDataUseCase` in `shared/domain/`
- Implement repository method in `shared/data/`
- Must export all customer PII as JSON/CSV
- Backend route already exists at `backend/api/.../routes/ExportRoutes.kt`

**S4-11: Audit trail sync to server (PCI-DSS R10 — CRITICAL)**
- File: `shared/data/src/commonMain/.../repository/AuditRepositoryImpl.kt`
- Currently: audit entries from SecurityAuditLogger are generated but never persisted
- Fix: implement `recordEvent()` and `recordAuditEntry()` to write to SQLDelight audit_log table
- Add sync pipeline to push audit entries to backend

---

### BLOCK 4: Feature Completion (~20 hrs)

**TODO-006: Remote Diagnostic WebSocket Relay**
- File: new `backend/sync/src/main/kotlin/.../relay/DiagnosticRelay.kt`
- Relay diagnostic commands between admin technician panel and POS app via WebSocketHub
- Domain model, security validator, backend service, and routes all exist — only the WS relay is missing

**TODO-011: Firebase Analytics KMP Wiring**
- Create `AnalyticsService` expect/actual (commonMain expect + androidMain Firebase SDK actual + jvmMain GA4 stub)
- Wire Koin binding in `dataModule` for both platforms
- Add analytics events in PosViewModel, AuthViewModel, DashboardViewModel

**TODO-008a: Admin Panel Email Delivery Log UI**
- File: new `admin-panel/src/routes/settings/email.tsx`
- TanStack Query hooks: new `admin-panel/src/api/email.ts`
- Display email delivery logs from backend API

**TODO-007e: API Documentation Site**
- Scaffold project in `zyntapos-docs/` with Scalar
- Split OpenAPI specs per service (api-v1.yaml, license-v1.yaml, admin-v1.yaml, sync-v1.yaml)
- Write guide pages: getting-started.mdx, authentication.mdx, sync-protocol.mdx
- Add Docker service + Caddyfile route for docs.zyntapos.com

---

### BLOCK 5: KMM Client Stubs to Complete

**BackupRepositoryImpl** — `shared/data/src/commonMain/.../repository/BackupRepositoryImpl.kt`
- Currently in-memory only — needs platform expect/actual `BackupFileManager` for real file I/O
- Android: copy encrypted SQLite DB file; Desktop: file copy to backup dir

**ReportRepositoryImpl stubs** — `shared/data/src/commonMain/.../repository/ReportRepositoryImpl.kt`
- `getPurchaseOrders()` returns emptyList() — needs purchase_orders SQLDelight table
- `getWarehouseInventory()` returns placeholder data — needs rack_products join table
- `getSupplierPurchases()` returns zero totals — needs purchase_orders aggregation
- `getMultiStoreComparison()` uses store ID as name — needs stores registry table
- `getLeaveBalances()` uses hardcoded allotments — needs leave_allotments table

**EInvoiceRepositoryImpl.submitToIrd()** — `shared/data/src/commonMain/.../repository/EInvoiceRepositoryImpl.kt`
- Currently marks as SUBMITTED without calling IRD API
- Needs actual HTTP call to Sri Lanka IRD API endpoint

---

## VERIFICATION after completing each block

```bash
# KMM modules
./gradlew assemble
./gradlew allTests
./gradlew detekt

# Backend
cd backend && ./gradlew test

# Admin panel
cd admin-panel && npm run build && npm test

# Full CI
./gradlew clean test lint --parallel --continue --stacktrace
```

## RULES
1. Follow CLAUDE.md exactly — especially pre-commit sync ritual
2. Use conventional commits: feat/fix/refactor/test/docs/build(scope): message
3. One logical change per commit — don't bundle unrelated work
4. Push after each commit and monitor the full 7-step pipeline
5. Use `curl` with `$PAT` for GitHub API, NOT `gh` CLI
6. Never push to main directly — always use the feature branch
7. If any CI step fails, stop and fix before continuing
8. Read existing code before modifying — understand before changing
9. No Java — 100% Kotlin
10. All ViewModels MUST extend BaseViewModel<S,I,E> (ADR-001)
11. No *Entity suffix in shared/domain/model/ (ADR-002)
```

---

## NOTES FOR THE OPERATOR

- **Session length:** Each block is roughly one session's worth of work. If a session times out, start a new one with this same prompt — it will pick up from where it left off by checking git log.
- **Priority:** Blocks 1-3 are highest priority (security + test coverage). Block 4 is feature completion. Block 5 contains Phase 3 items that can be deferred.
- **Tracking:** After each session, update `docs/todo/GAP-ANALYSIS-AND-FILL-PLAN.md` with current completion percentages.
- **Branch naming:** Each session should create its own branch: `claude/<descriptive-name>-<sessionId>`
