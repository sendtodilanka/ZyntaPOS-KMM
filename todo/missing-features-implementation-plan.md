# ZyntaPOS-KMM — Missing & Partially Implemented Features Implementation Plan

**Created:** 2026-03-18
**Last Updated:** 2026-03-18
**Status:** Draft — Awaiting Approval

---

## Overview

This document lists ALL missing and partially implemented features across the ZyntaPOS-KMM codebase,
organized by priority and phase. Each item includes current status, what's missing, affected modules,
key files, and implementation steps.

---

## PRIORITY LEGEND

| Priority | Meaning |
|----------|---------|
| P0-CRITICAL | Blocks MVP launch — must fix immediately |
| P1-HIGH | Feature-blocking — required for production readiness |
| P2-MEDIUM | Completeness — needed for full feature parity |
| P3-LOW | Enhancement — nice to have, can defer |
| PHASE-2 | Planned for Phase 2 (Growth) |
| PHASE-3 | Planned for Phase 3 (Enterprise) |

---

## SECTION A: CRITICAL / HIGH PRIORITY (P0–P1)

---

### A1. Sync Engine Server-Side (TODO-007g) — ~60% Complete

**Priority:** P0-CRITICAL
**Impact:** Offline-first sync pipeline non-functional; client data sits in `sync_queue` unprocessed
**Modules:** `:shared:data`, `backend/api`

**What EXISTS:**
- `sync_operations`, `sync_cursors`, `entity_snapshots`, `sync_conflict_log`, `sync_dead_letters` tables (V4)
- `SyncProcessor.kt` — push processing with batch validation
- `DeltaEngine.kt` — cursor-based pull with delta computation
- `EntityApplier.kt` — JSONB → normalized tables (ONLY handles PRODUCT type)
- `ServerConflictResolver.kt` — LWW (Last-Write-Wins) resolution
- `SyncRoutes.kt` — REST `/sync/push` and `/sync/pull` endpoints
- WebSocket endpoints in `backend/sync` service

**What's MISSING:**
- [ ] `EntityApplier` — extend to handle ALL entity types (ORDER, CUSTOMER, CATEGORY, SUPPLIER, STOCK_ADJUSTMENT, CASH_REGISTER, REGISTER_SESSION, etc.)
- [ ] `ConflictResolver` client-side in `:shared:data` (CRDT merge logic — Phase 2 backlog)
- [ ] `version_vectors` table management (table exists, never written to)
- [ ] Multi-store data isolation enforcement on sync endpoints (validate `store_id` matches JWT claims)
- [ ] WebSocket push notifications to clients after server processes sync ops
- [ ] JWT validation on WebSocket upgrade in `backend/sync`
- [ ] Sync payload field-level validation (missing fields, invalid types)
- [ ] POS token revocation check during JWT validation
- [ ] Heartbeat replay protection

**Key Files:**
- `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt`
- `backend/api/src/main/kotlin/.../sync/DeltaEngine.kt`
- `backend/api/src/main/kotlin/.../sync/EntityApplier.kt`
- `backend/api/src/main/kotlin/.../sync/ServerConflictResolver.kt`
- `backend/api/src/main/kotlin/.../routes/SyncRoutes.kt`
- `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt`
- `shared/data/src/commonMain/kotlin/.../sync/SyncEngine.kt`

**Implementation Steps:**
1. Extend `EntityApplier` with handlers for all 10+ entity types
2. Add `store_id` validation middleware to sync routes
3. Implement WebSocket JWT validation in sync service
4. Wire WebSocket push after `SyncProcessor` commits operations
5. Add field-level payload validation in `SyncProcessor`
6. Implement `revoked_tokens` check in JWT validation pipeline
7. Add heartbeat replay detection (timestamp + nonce)
8. Write integration tests with Testcontainers (PostgreSQL + Redis)

---

### A2. Email Management System (TODO-008a) — ~95% Complete

**Priority:** P1-HIGH
**Impact:** Email logs cannot be viewed by admins; templates not editable

**What EXISTS:**
- Stalwart mail server deployed (SMTP/IMAP)
- Cloudflare Email Worker → HTTP relay → Stalwart pipeline working
- `EmailService.kt` — Resend API transactional email sending
- `email_threads` table (V18) — inbound email storage
- `email_delivery_log` table (V20) — outbound email audit trail
- `InboundEmailProcessor.kt` — CF Worker → ticket creation
- `ChatwootService.kt` — Chatwoot conversation sync
- Admin panel API hooks: `useEmailLogs()`, `useEmailPreferences()`
- Backend routes: `AdminEmailRoutes.kt`

**What's MISSING:**
- [ ] Admin panel email delivery log UI page (`admin-panel/src/routes/settings/email.tsx` — file missing)
- [ ] Email template editor in admin panel (CRUD for template slugs)
- [ ] Email preference management UI for customers
- [ ] Bounce/complaint webhook handler from Resend
- [ ] Email retry logic for QUEUED → SENDING failures

**Key Files:**
- `backend/api/src/main/kotlin/.../service/EmailService.kt`
- `backend/api/src/main/kotlin/.../routes/AdminEmailRoutes.kt`
- `backend/api/src/main/kotlin/.../service/InboundEmailProcessor.kt`
- `admin-panel/src/api/email.ts`
- `admin-panel/src/routes/settings/email.tsx` (MISSING — needs creation)

**Implementation Steps:**
1. Create `admin-panel/src/routes/settings/email.tsx` with delivery log table
2. Add email template CRUD endpoints in backend API
3. Build template editor component in admin panel
4. Implement Resend bounce/complaint webhook endpoint
5. Add retry queue worker for failed email deliveries

---

### A3. Remote Diagnostic Access (TODO-006) — 0% Complete

**Priority:** P1-HIGH
**Impact:** Enterprise support cannot remotely diagnose customer POS issues

**What EXISTS:**
- `diagnostic_sessions` table (V8, extended V15, V19) with columns for:
  - `visit_type` (REMOTE/ON_SITE)
  - `site_visit_token_hash`
  - `hardware_scope` (comma-separated)
  - `data_scope` (READ_ONLY_DIAGNOSTICS / FULL_READ_ONLY)
  - consent tracking, expiry, revocation
- Feature flag `remote_diagnostic` (disabled, PROFESSIONAL/ENTERPRISE editions)
- `DiagnosticRelay.kt` in sync service (WebSocket relay scaffold)
- `DiagnosticWebSocketRoutes.kt` in sync service

**What's MISSING (entire module):**
- [ ] `:composeApp:feature:diagnostic` module — not in `settings.gradle.kts`
- [ ] `DiagnosticSession` domain model in `:shared:domain/model/`
- [ ] `DiagnosticRepository` interface in `:shared:domain/repository/`
- [ ] `DiagnosticRepositoryImpl` in `:shared:data`
- [ ] `DiagnosticTokenValidator` in `:shared:security`
- [ ] `DiagnosticSessionService.kt` in backend API
- [ ] `AdminDiagnosticRoutes.kt` in backend API
- [ ] Customer consent flow UI (KMM app)
- [ ] Technician session viewer UI (admin panel)
- [ ] JIT token generation (15-min TTL, TOTP-based)
- [ ] 3-layer data isolation (read-only, diagnostic ops, transaction-restricted)
- [ ] Session audit trail integration
- [ ] Remote session revocation mechanism
- [ ] Hardware scope enforcement (printer, scanner, cash drawer)
- [ ] On-site token validation flow (V19 schema exists, no endpoint)

**Key Files:**
- `backend/api/src/main/resources/db/migration/V8__diagnostic_sessions.sql`
- `backend/api/src/main/resources/db/migration/V15__diagnostic_visit_type.sql`
- `backend/api/src/main/resources/db/migration/V19__diagnostic_hardware_scope.sql`
- `backend/sync/src/main/kotlin/.../routes/DiagnosticWebSocketRoutes.kt`
- `backend/sync/src/main/kotlin/.../hub/DiagnosticRelay.kt`

**Implementation Steps:**
1. Add `:composeApp:feature:diagnostic` module to `settings.gradle.kts`
2. Create `DiagnosticSession` domain model and repository interface
3. Implement `DiagnosticTokenValidator` (TOTP, 15-min TTL)
4. Build `DiagnosticSessionService` + `AdminDiagnosticRoutes` in backend
5. Implement customer consent flow UI in KMM app
6. Build technician session viewer in admin panel
7. Wire WebSocket relay for live diagnostic data streaming
8. Add hardware scope enforcement and data isolation
9. Integration tests for consent → token → session → revocation flow

---

### A4. API Documentation Site (TODO-007e) — 0% Complete

**Priority:** P1-HIGH
**Impact:** External developers cannot integrate; partner API adoption blocked

**What EXISTS:**
- `docs/api/` directory with README and `.gitkeep` only

**What's MISSING:**
- [ ] OpenAPI 3.0 spec for API service (all REST endpoints)
- [ ] OpenAPI 3.0 spec for License service endpoints
- [ ] OpenAPI 3.0 spec for Sync service WebSocket protocol
- [ ] Swagger UI or Redoc hosting
- [ ] Deployment workflow to docs subdomain
- [ ] Code samples (Kotlin KMM client, TypeScript admin)
- [ ] Authentication flow documentation
- [ ] Sync protocol documentation (push/pull/cursor/conflict)

**Implementation Steps:**
1. Auto-generate OpenAPI spec from Ktor route definitions (ktor-openapi plugin)
2. Add Redoc static site generation to CI
3. Deploy to `docs.zyntapos.com` via Caddy
4. Write authentication and sync protocol guides
5. Add code samples for common integrations

---

### A5. Firebase Analytics & Sentry Integration (TODO-011) — ~40% Complete

**Priority:** P1-HIGH
**Impact:** Product telemetry unavailable; growth metrics invisible

**What EXISTS:**
- Sentry DSN secrets configured (3 services: API, License, Sync)
- `SENTRY_AUTH_TOKEN` configured
- `GOOGLE_SERVICES_JSON` secret exists
- `GA4_MEASUREMENT_ID` secret exists

**What's MISSING:**
- [ ] Firebase Android SDK dependency in `androidApp/build.gradle.kts`
- [ ] `google-services.json` injection step in CI workflows
- [ ] `FirebaseAnalytics` initialization in `ZyntaApplication.kt`
- [ ] Custom event tracking in feature modules (screen views, actions)
- [ ] GA4 user properties (role, edition, store_size)
- [ ] Sentry SDK initialization in backend services
- [ ] Sentry source maps for admin panel
- [ ] Error boundary → Sentry reporting in admin panel React

**Implementation Steps:**
1. Add Firebase SDK deps to `androidApp/build.gradle.kts`
2. Add `google-services.json` decode step in CI
3. Initialize Firebase in `ZyntaApplication.kt`
4. Create `AnalyticsTracker` interface in `:shared:core`
5. Add screen view events in each feature module
6. Initialize Sentry in all 3 backend services
7. Add Sentry error boundary to admin panel

---

### A6. Security Monitoring & Automated Response (TODO-010) — ~85% Complete

**Priority:** P1-HIGH
**Impact:** Runtime security blind spots; breach detection incomplete

**What EXISTS:**
- Falcosidekick container deployed
- Canary service deployed
- `NVD_API_KEY` configured for OWASP dependency check
- Cloudflare Origin Certificate configured

**What's MISSING:**
- [ ] Snyk Monitor step in `ci-gate.yml`
- [ ] Canary tokens embedded in source files (placeholder values)
- [ ] `config/cloudflare/tunnel-config.yml` — `<TUNNEL_ID>` placeholder not replaced
- [ ] Falcosidekick → Slack webhook wiring (placeholder value)
- [ ] CF Zero Trust access policies (Cloudflare dashboard action)
- [ ] Bot Fight Mode + WAF rules (Cloudflare dashboard action)
- [ ] OWASP dependency check step in CI pipeline

**Implementation Steps:**
1. Add Snyk CLI step to `ci-gate.yml`
2. Replace Falcosidekick Slack webhook placeholder
3. Replace Cloudflare tunnel config placeholder
4. Add OWASP dependency-check Gradle plugin
5. Configure CF dashboard policies (manual, document steps)

---

## SECTION B: MEDIUM PRIORITY (P2)

---

### B1. Admin Panel Enhancements (TODO-007a) — ~98% Complete

**Priority:** P2-MEDIUM

**What's MISSING:**
- [ ] Security dashboard page (intrusion alerts, audit summary)
- [ ] OTA update management page (app version distribution)
- [ ] E2E tests with Playwright (`playwright.config.ts` scaffold exists)
- [ ] Admin panel VPS deployment via GitHub Actions

**Implementation Steps:**
1. Build security dashboard component
2. Build OTA update page with version tracking
3. Write Playwright E2E tests for critical flows
4. Add admin panel build + deploy to `cd-deploy.yml`

---

### B2. Admin Panel Custom Auth (TODO-007f) — ~75% Complete

**Priority:** P2-MEDIUM

**What EXISTS:**
- BCrypt login, HS256 JWT, MFA (TOTP + backup codes)
- Session management (single-use refresh tokens)
- Account lockout (5 failures → 15 min)
- Password reset flow

**What's MISSING:**
- [ ] Session management UI in admin panel (view active sessions, revoke)
- [ ] Security audit log page (detailed event viewer with filtering)
- [ ] IP allowlisting for admin access
- [ ] Login notification emails
- [ ] Forced password rotation policy

**Implementation Steps:**
1. Build session list component with revoke action
2. Build audit log viewer with category/date/user filters
3. Add IP allowlist middleware to admin auth routes
4. Trigger email on new admin login
5. Add password age check to login flow

---

### B3. Monitoring Setup — Uptime Kuma (TODO-007c) — ~70% Complete

**Priority:** P2-MEDIUM

**What EXISTS:**
- Uptime Kuma container deployed and accessible

**What's MISSING:**
- [ ] Monitor configurations for all 7 subdomains (api, admin, sync, license, docs, status, mail)
- [ ] Slack/email alert channels
- [ ] Status page branding and public URL
- [ ] Docker container health monitors
- [ ] PostgreSQL + Redis connectivity monitors

**Implementation Steps:**
1. Script Uptime Kuma monitor setup via API
2. Configure Slack webhook notification channel
3. Set up branded status page at `status.zyntapos.com`
4. Add Docker container health checks
5. Add database/Redis ping monitors

---

### B4. Backend Test Coverage — ~25% Actual vs 80% Target

**Priority:** P2-MEDIUM
**Impact:** 52 files need test coverage improvements

**What EXISTS:**
- Some unit tests for API service
- Mockative 3 + Kotlin Test framework configured
- Turbine for Flow testing

**What's MISSING:**
- [ ] API service integration tests with Testcontainers (PostgreSQL + Redis)
- [ ] License service tests (<1% coverage)
- [ ] Sync service tests (~15% coverage)
- [ ] Common module tests (0% coverage)
- [ ] `AdminAuthService` tests (bcrypt, MFA, lockout)
- [ ] `SyncProcessor` tests (push validation, conflict detection)
- [ ] `DeltaEngine` tests (cursor pagination, filtering)
- [ ] `EntityApplier` tests (all entity types)
- [ ] `LicenseService` tests (activation, heartbeat, device limits)

**Implementation Steps:**
1. Set up Testcontainers config for PostgreSQL + Redis
2. Write `SyncProcessor` integration tests (push flow)
3. Write `DeltaEngine` integration tests (pull flow)
4. Write `EntityApplier` unit tests for each entity type
5. Write `AdminAuthService` tests (login, MFA, lockout, reset)
6. Write `LicenseService` tests (activate, heartbeat, device max)
7. Write `ServerConflictResolver` tests (LWW, tiebreak)
8. Add test coverage reporting to CI pipeline

---

## SECTION C: PHASE 2 FEATURES (Growth)

---

### C1. Multi-Store Dashboard & Management

**Priority:** PHASE-2
**Feature Flag:** `multi_store` (disabled, ENTERPRISE edition)

**What EXISTS:**
- `stores` table with all 18 store-aware FK tables
- `AdminStoresRoutes.kt` — store list/health/config endpoints
- `AdminStoresService.kt` — health scoring
- Admin panel store list/detail/health pages
- 5 API hooks for store management

**What's MISSING:**
- [ ] Cross-store product transfer workflow
- [ ] Inter-store stock movement endpoints + tables
- [ ] Consolidated multi-store reporting (aggregated KPIs)
- [ ] Store-level pricing rules (different prices per store)
- [ ] Stock rebalancing across stores
- [ ] Multi-store dashboard in KMM app (`:composeApp:feature:multistore` is scaffold)

**Implementation Steps:**
1. Design inter-store transfer schema (new table: `store_transfers`)
2. Build transfer request/approve/ship/receive workflow
3. Implement consolidated report aggregation endpoints
4. Build multi-store dashboard UI in KMM app
5. Enable `multi_store` feature flag for ENTERPRISE editions

---

### C2. CRDT ConflictResolver (Client-Side)

**Priority:** PHASE-2
**Feature Flag:** `crdt_sync` (disabled, ENTERPRISE edition)

**What EXISTS:**
- Server-side LWW conflict resolution (`ServerConflictResolver.kt`)
- `sync_conflict_log` table for audit trail
- `version_vectors` column on `sync_queue` (unused)

**What's MISSING:**
- [ ] `ConflictResolver` interface in `:shared:data`
- [ ] CRDT merge implementations (G-Counter, LWW-Register, OR-Set)
- [ ] Vector clock management (increment, compare, merge)
- [ ] Field-level merge strategies (not just entity-level LWW)
- [ ] Conflict UI in POS app (show conflicts, allow manual resolution)
- [ ] Client-side conflict log display

**Implementation Steps:**
1. Define `ConflictResolver` interface in `:shared:data`
2. Implement LWW-Register for simple fields
3. Implement G-Counter for stock quantities
4. Implement OR-Set for order items
5. Build vector clock utility in `:shared:core`
6. Add conflict review UI in POS app settings

---

### C3. CashDrawerController (HAL)

**Priority:** PHASE-2

**What EXISTS:**
- HAL interfaces defined (`PrinterPort`, `BarcodeScanner`)
- Platform-specific printer/scanner implementations

**What's MISSING:**
- [ ] `CashDrawerPort` interface in `:shared:hal`
- [ ] Android USB implementation
- [ ] JVM serial port implementation (jSerialComm)
- [ ] ESC/POS kick command integration
- [ ] Auto-open on payment completion

**Implementation Steps:**
1. Define `CashDrawerPort` interface (open, status, close)
2. Implement Android USB HID driver
3. Implement JVM serial driver via jSerialComm
4. Wire to `RegisterViewModel` payment completion flow
5. Add cash drawer test in debug console

---

### C4. Coupon & Promotion Rule Engine

**Priority:** PHASE-2
**Module:** `:composeApp:feature:coupons` (scaffold only)

**What EXISTS:**
- Feature module scaffold registered in `settings.gradle.kts`
- `coupons` table in SQLDelight schema
- Basic Coupon domain model

**What's MISSING:**
- [ ] Promotion rule engine (BOGO, %, threshold, combo)
- [ ] Coupon validation logic (expiry, usage limits, min order)
- [ ] Coupon application in cart/checkout flow
- [ ] Coupon CRUD UI screens
- [ ] Coupon reporting (redemption rates, revenue impact)

---

### C5. Customer Loyalty & CRM

**Priority:** PHASE-2
**Module:** `:composeApp:feature:customers` (scaffold only)

**What EXISTS:**
- `customers` table with `loyalty_points` column (INT)
- Customer domain model
- Customer list/detail screens (basic)

**What's MISSING:**
- [ ] Points accumulation rules (per-purchase, per-amount)
- [ ] Points redemption flow at checkout
- [ ] Loyalty tier system (Bronze/Silver/Gold)
- [ ] GDPR customer data export (backend route exists, KMM UI missing)
- [ ] Customer purchase history view
- [ ] Customer analytics (lifetime value, frequency)

---

### C6. Advanced Reporting

**Priority:** PHASE-2

**What EXISTS:**
- `ReportRepositoryImpl.kt` — partial implementation
- `FinancialStatementRepositoryImpl.kt` — placeholder
- Report module UI scaffold

**What's MISSING:**
- [ ] Full P&L statement calculation
- [ ] Balance sheet generation
- [ ] Cash flow analysis
- [ ] Sales by category/product/time period reports
- [ ] CSV/PDF export from KMM app
- [ ] Supplier purchases aggregation
- [ ] Store-level report filtering

---

## SECTION D: PHASE 3 FEATURES (Enterprise)

---

### D1. E-Invoice & IRD Submission (TODO-005)

**Priority:** PHASE-3
**Module:** `:composeApp:feature:accounting`

**What EXISTS:**
- `EInvoiceRepositoryImpl.kt` — scaffold
- `e_invoices` table in SQLDelight
- IRD secret placeholders in `local.properties.template`

**What's MISSING:**
- [ ] IRD (Sri Lanka Inland Revenue Dept) API client
- [ ] Digital signature with `.p12` certificate
- [ ] E-invoice XML/JSON generation per IRD spec
- [ ] Submission pipeline with retry and error handling
- [ ] Invoice status tracking (SUBMITTED → ACCEPTED → REJECTED)
- [ ] Tax calculation alignment with IRD rules

---

### D2. Staff, Shifts & Payroll

**Priority:** PHASE-3
**Module:** `:composeApp:feature:staff` (scaffold only)

**What's MISSING:**
- [ ] Employee profiles CRUD
- [ ] Shift scheduling (calendar view)
- [ ] Attendance tracking (clock in/out)
- [ ] Leave management (allotments, requests, approval)
- [ ] Payroll calculation engine
- [ ] Payroll reports
- [ ] Staff role assignment and permissions

---

### D3. Expense Tracking & Accounting

**Priority:** PHASE-3
**Module:** `:composeApp:feature:expenses` (scaffold only)

**What's MISSING:**
- [ ] Expense log CRUD
- [ ] Expense categories
- [ ] Receipt image attachment
- [ ] P&L integration
- [ ] Cash flow view
- [ ] Budget tracking

---

### D4. Multi-Warehouse Tracking

**Priority:** PHASE-3

**What EXISTS:**
- `WarehouseRepositoryImpl.kt` — partial implementation

**What's MISSING:**
- [ ] Per-warehouse stock levels
- [ ] Inter-warehouse transfer workflow
- [ ] Warehouse location management
- [ ] Stock reorder point automation
- [ ] Warehouse-level reporting

---

## SECTION E: BACKEND ARCHITECTURAL GAPS

---

### E1. EntityApplier — Only Handles PRODUCT

**Priority:** P0-CRITICAL (blocks sync for all other entity types)

**Current State:** `EntityApplier.kt` only maps PRODUCT sync payloads to the `products` table.
All other entity types (ORDER, CUSTOMER, CATEGORY, SUPPLIER, etc.) are silently ignored.

**Fix:**
- [ ] Add handler for CATEGORY → `categories` table
- [ ] Add handler for CUSTOMER → `customers` table
- [ ] Add handler for SUPPLIER → `suppliers` table
- [ ] Add handler for ORDER → `orders` + `order_items` tables
- [ ] Add handler for STOCK_ADJUSTMENT → `products.stock_qty` update
- [ ] Add handler for CASH_REGISTER → (create table if missing)
- [ ] Add handler for REGISTER_SESSION → (create table if missing)
- [ ] Add handler for AUDIT_ENTRY → `audit_entries` table

---

### E2. Mixed Timestamp Formats

**Priority:** P2-MEDIUM

**Issue:** Backend mixes epoch-ms, `Instant.now()`, and `OffsetDateTime` across services.

**Fix:**
- [ ] Standardize on `Instant` (kotlinx-datetime) across all services
- [ ] Add timestamp format validation in sync payload processing
- [ ] Document timestamp contract in API spec

---

### E3. Admin JWT Security Gap

**Priority:** P1-HIGH

**Issue:** Admin panel uses HS256 (symmetric shared secret) while POS uses RS256 (asymmetric).
A compromised admin server leaks the signing secret.

**Fix:**
- [ ] Migrate admin auth to RS256 (same key pair as POS, or separate)
- [ ] Update `AdminAuthService.kt` token generation
- [ ] Update License service admin JWT validation
- [ ] Rotate existing admin sessions after migration

---

## SECTION F: CI/CD & INFRASTRUCTURE GAPS

---

### F1. CI Pipeline Enhancements

- [ ] Add OWASP dependency-check Gradle plugin to `ci-gate.yml`
- [ ] Add Snyk security scan step
- [ ] Add test coverage threshold enforcement (fail if < 60%)
- [ ] Add Playwright E2E tests for admin panel
- [ ] Add `google-services.json` decode step for Android builds

### F2. Deployment Enhancements

- [ ] Admin panel build + static deploy to Caddy
- [ ] API documentation site deployment
- [ ] Database migration dry-run step before deploy
- [ ] Blue-green deployment support (currently hard-reset)
- [ ] Automated database backup before deploy

---

## IMPLEMENTATION TIMELINE (Suggested)

| Sprint | Focus | Items |
|--------|-------|-------|
| Sprint 1 (Week 1-2) | Sync Engine Completion | A1, E1 |
| Sprint 2 (Week 3) | Email + Security | A2, A6, E3 |
| Sprint 3 (Week 4-5) | Remote Diagnostics | A3 |
| Sprint 4 (Week 6) | Analytics + Docs | A4, A5 |
| Sprint 5 (Week 7-8) | Test Coverage | B4 |
| Sprint 6 (Week 9-10) | Admin Polish | B1, B2, B3 |
| Phase 2 Sprints | Growth Features | C1-C6 |
| Phase 3 Sprints | Enterprise Features | D1-D4 |

---

## HOW TO USE THIS DOCUMENT

1. Pick items from the highest priority section first (A → B → C → D)
2. Each item has checkboxes `[ ]` — mark as `[x]` when complete
3. Update the `Last Updated` date at the top after changes
4. Reference specific items in commit messages (e.g., `feat(sync): implement ORDER handler [A1/E1]`)
5. Move completed sections to an `## COMPLETED` section at the bottom

---

*End of document*
