# ZyntaPOS-KMM — Missing & Partially Implemented Features Implementation Plan

**Created:** 2026-03-18
**Last Updated:** 2026-03-18
**Status:** Approved — Verified against codebase 2026-03-18

---

## Overview

මෙම ලේඛනයේ ZyntaPOS-KMM codebase එකේ missing සහ partially implemented features සියල්ල
ඇතුළත් වේ. Multi-store enterprise features (6 categories) සම්පූර්ණයෙන් cover කරයි.
එක් එක් item එකට current codebase state, missing items, affected modules, key files,
සහ implementation steps ඇතුළත්ය.

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

## 🔴 PHASE 2 TOP 3 BLOCKERS

> මේ 3 items resolve නොකර Phase 2 (Multi-Store Growth) start කරන්න බැහැ.
> Implementation sessions වලදී මේවා පළමු priority ලෙස සලකන්න.

### Blocker 1: Sync Engine Server-Side (A1) — ~95% Complete | P0-CRITICAL

**ලොකුම blocker එක.** `EntityApplier` එකේ entity types 17ක් handle කරනවා. WebSocket push,
JWT validation, token revocation, heartbeat replay protection, circular parent detection
all implemented (2026-03-18 session 2).

- `EntityApplier` — ✅ extended to 17 entity types (2026-03-18)
- Multi-store data isolation (`store_id` JWT validation) — ✅ already existed (S2-10)
- WebSocket push notifications after sync — ✅ SyncProcessor publishes entityTypes to Redis
- JWT validation on WebSocket upgrade — ✅ already existed (authenticate wraps WS routes)
- Token revocation — ✅ in-memory + Redis cache, admin endpoint
- Heartbeat replay protection — ✅ nonce + timestamp validation
- Category circular parent ref detection — ✅ ancestor chain walk (max 10 levels)

**Impact:** Offline-first data sync මුළුමනින්ම non-functional. Client data `sync_queue` table එකේ unprocessed ඉඳලා යයි.

### Blocker 2: Multi-Store Data Architecture (C6.1 + C1.1–C1.5) — 0% Backend

Phase 2 core feature එක multi-store. නමුත්:

- **Global Product Catalog** (`global_products` table) — build කරන්න ඕන
- **Store-Specific Inventory** backend support — `store_id` column products/stock tables වලට add කරන්න ඕන
- **Inter-Store Transfer (IST)** backend pipeline — 0% (UI exists: `NewStockTransferScreen`, `StockTransferListScreen` — but backend routes, approval workflow, tracking tables නැහැ)
- **Cross-Store Sync** (multi-node CRDT) — conflict resolution multi-store context එකේ design කරන්න ඕන

**Impact:** Multi-store features UI level එකේ scaffold එකයි ඇත්තේ — backend support නැතුව dead screens.

### Blocker 3: Backend Test Coverage (B4) — ~40% vs 80% Target

Phase 2 stable release එකකට backend test coverage 80%+ ඕන. දැන් ~40%:

- `SyncProcessor`, `EntityApplier`, `DeltaEngine` — basic tests EXIST but need edge case expansion
- `AdminAuthService` (BCrypt, MFA, lockout) — substantial tests exist (791 LOC across 2 files)
- Repository layer — no integration tests with Testcontainers (infra exists, tests don't)
- Multi-store operations — zero test coverage
- License service — no tests

**Impact:** Sync engine extend කරද්දී regression risk ඉහළයි. Phase 2 features add කරන කොට existing functionality break වෙන risk untested code නිසා ඉහළයි.

---

## SECTION A: CRITICAL / HIGH PRIORITY (P0–P1)

---

### A1. Sync Engine Server-Side (TODO-007g) — ~95% Complete

> **HANDOFF (2026-03-18, session 2):** All remaining A1 items implemented:
> - WebSocket push notifications enhanced with entityTypes in SyncNotification
> - JWT validation on WebSocket upgrade already existed (authenticate("jwt-rs256") wraps WS routes)
> - Token revocation: in-memory cache in API service, Redis-backed cache in sync service,
>   admin endpoint POST /admin/sync/tokens/revoke added
> - Heartbeat nonce + timestamp replay protection added to license service
> - Category circular parent reference detection added to EntityApplier
> - Tests: TokenRevocationCacheTest, SyncTokenRevocationCacheTest, HeartbeatReplayProtectionTest,
>   EntityApplierTest (category tests), SyncProcessorTest (entityTypes), RedisPubSubListenerTest (entityTypes)
> Branch: claude/sync-websocket-jwt-q1NZG.

**Priority:** P0-CRITICAL
**Impact:** Offline-first sync pipeline non-functional; client data sits in `sync_queue` unprocessed
**Modules:** `:shared:data`, `backend/api`, `backend/sync`

**What EXISTS:**
- `sync_operations`, `sync_cursors`, `entity_snapshots`, `sync_conflict_log`, `sync_dead_letters` tables (V4)
- `SyncProcessor.kt` — push processing with batch validation, Redis publish with entityTypes
- `DeltaEngine.kt` — cursor-based pull with delta computation
- `EntityApplier.kt` — JSONB → normalized tables (handles 17+ entity types), circular parent ref detection
- `ServerConflictResolver.kt` — LWW (Last-Write-Wins) resolution
- `SyncRoutes.kt` — REST `/sync/push` and `/sync/pull` endpoints with store_id JWT validation (S2-10)
- `SyncValidator.kt` — batch + field-level validation for all major entity types
- WebSocket endpoints in `backend/sync` with JWT auth + token revocation check
- KMM client: `sync_queue.sq` (outbox), `sync_state.sq` (cursor), `version_vectors.sq` (CRDT metadata)
- KMM client: `ConflictResolver.kt` — LWW with field-level merge for PRODUCT
- V23 migration — normalized entity tables for all entity types

**What's DONE:**
- [x] `EntityApplier` — extended to handle 17+ entity types
- [x] Multi-store data isolation enforcement on sync endpoints
- [x] Sync payload field-level validation for all major entity types
- [x] STOCK_ADJUSTMENT handler with stock_qty side-effect on products table
- [x] WebSocket push notifications — SyncProcessor publishes entityTypes to Redis, RedisPubSubListener broadcasts via WebSocketHub
- [x] JWT validation on WebSocket upgrade — `authenticate("jwt-rs256")` wraps WS routes in sync service
- [x] POS token revocation check — API: in-memory cache (5min TTL) + DB fallback; Sync: Redis set `revoked_jtis` + in-memory cache
- [x] Admin token revocation endpoint — `POST /admin/sync/tokens/revoke` with audit trail
- [x] Heartbeat replay protection — nonce-based (ConcurrentHashMap, 5min TTL) + timestamp validation (60s max age)
- [x] Category circular parent reference detection — walks ancestor chain up to 10 levels

**What's REMAINING (minor):**
- [ ] Integration tests with Testcontainers (PostgreSQL + Redis) for full end-to-end sync flow
- [ ] Client-side nonce generation for heartbeat requests (KMM app update)

**Key Files:**
- `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt`
- `backend/api/src/main/kotlin/.../sync/EntityApplier.kt`
- `backend/api/src/main/kotlin/.../plugins/Authentication.kt` (TokenRevocationCache)
- `backend/api/src/main/kotlin/.../routes/AdminSyncRoutes.kt` (token revocation endpoint)
- `backend/sync/src/main/kotlin/.../plugins/Authentication.kt` (SyncTokenRevocationCache)
- `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt`
- `backend/sync/src/main/kotlin/.../hub/RedisPubSubListener.kt`
- `backend/license/src/main/kotlin/.../service/LicenseService.kt` (HeartbeatNonceCache)

**Implementation Steps:**
1. ~~Extend `EntityApplier` with handlers for all 10+ entity types~~ DONE
2. ~~Add `store_id` validation middleware to sync routes~~ DONE
3. ~~Implement WebSocket JWT validation in sync service~~ DONE (already existed)
4. ~~Wire WebSocket push after `SyncProcessor` commits operations~~ DONE (enhanced with entityTypes)
5. ~~Add field-level payload validation in `SyncProcessor`~~ DONE
6. ~~Implement `revoked_tokens` check in JWT validation pipeline~~ DONE (API + Sync service)
7. ~~Add heartbeat replay detection (timestamp + nonce)~~ DONE
8. Write integration tests with Testcontainers (PostgreSQL + Redis) — future session

---

### A2. Email Management System (TODO-008a) — ✅ COMPLETE

**Priority:** P1-HIGH
**Status:** COMPLETE — All features implemented (2026-03-18)

**What EXISTS:**
- Stalwart mail server deployed (SMTP/IMAP)
- Cloudflare Email Worker → HTTP relay → Stalwart pipeline working
- `EmailService.kt` — Resend API transactional email sending with retry support
- `email_threads` table (V18) — inbound email storage
- `email_delivery_log` table (V20, V25) — outbound email audit trail with retry columns
- `email_templates` table (V25) — admin-editable email templates with {{variable}} placeholders
- `email_preferences` enhancements (V25) — sla_breach_notifications, daily_digest columns
- `InboundEmailProcessor.kt` — CF Worker → ticket creation
- `ChatwootService.kt` — Chatwoot conversation sync
- Admin panel: 3-tab email management page (Delivery Logs, Templates, Preferences)
- Backend routes: `AdminEmailRoutes.kt`, `AdminEmailTemplateRoutes.kt`, `AdminEmailPreferencesRoutes.kt`
- `EmailRetryJob.kt` — background retry with exponential backoff (2m, 8m, 32m; max 3 retries)

**All Items Complete:**
- [x] Admin panel email delivery log UI page with status/date filters and pagination
- [x] `useEmailDeliveryLogs()` TanStack Query hook with status/date filters
- [x] Email delivery log table enhancement (status filters, date range filter, pagination)
- [x] Email template editor in admin panel (`GET/PUT /admin/email/templates/{slug}`)
- [x] Email preference management UI (`GET/PUT /admin/email/preferences`)
- [x] Bounce/complaint webhook handler from Resend (`POST /webhooks/resend`)
- [x] Email retry logic for FAILED deliveries (EmailRetryJob — exponential backoff, max 3 retries)

**Key Files:**
- `backend/api/src/main/kotlin/.../service/EmailRetryJob.kt` — retry background job
- `backend/api/src/main/kotlin/.../routes/AdminEmailTemplateRoutes.kt` — template CRUD
- `backend/api/src/main/kotlin/.../routes/AdminEmailPreferencesRoutes.kt` — preferences API
- `admin-panel/src/routes/settings/email.tsx` — 3-tab email management page
- `admin-panel/src/api/email.ts` — all email API hooks
- `backend/api/src/main/resources/db/migration/V25__email_retry_templates_preferences.sql`

---

### A3. Remote Diagnostic Access (TODO-006) — 0% Complete

**Priority:** P1-HIGH
**Impact:** Enterprise support cannot remotely diagnose customer POS issues

**What EXISTS:**
- `diagnostic_sessions` table (V8, V15, V19) with visit_type, hardware_scope, data_scope, consent tracking
- Feature flag `remote_diagnostic` (disabled, PROFESSIONAL/ENTERPRISE editions)
- `DiagnosticRelay.kt` + `DiagnosticWebSocketRoutes.kt` in sync service (scaffold)

**What's MISSING (entire module):**
- [ ] `:composeApp:feature:diagnostic` module — not in `settings.gradle.kts`
- [ ] `DiagnosticSession` domain model in `:shared:domain/model/`
- [ ] `DiagnosticRepository` interface + impl
- [ ] `DiagnosticTokenValidator` in `:shared:security`
- [ ] `DiagnosticSessionService.kt` + `AdminDiagnosticRoutes.kt` in backend
- [ ] Customer consent flow UI (KMM app)
- [ ] Technician session viewer UI (admin panel)
- [ ] JIT token generation (15-min TTL, TOTP-based)
- [ ] 3-layer data isolation + hardware scope enforcement
- [ ] Session audit trail integration + remote revocation

**Implementation Steps:**
1. Add module to `settings.gradle.kts`
2. Create domain model + repository interface
3. Implement token validator (TOTP, 15-min TTL)
4. Build backend service + routes
5. Build consent flow UI (KMM) + technician viewer (admin panel)
6. Wire WebSocket relay for live diagnostic streaming
7. Integration tests for full flow

---

### A4. API Documentation Site (TODO-007e) — 0% Complete

**Priority:** P1-HIGH

**What's MISSING:**
- [ ] OpenAPI 3.0 spec for all 3 backend services
- [ ] Swagger UI or Redoc hosting
- [ ] Deployment workflow to docs subdomain
- [ ] Code samples + authentication docs
- [ ] Sync protocol documentation

---

### A5. Firebase Analytics & Sentry Integration (TODO-011) — ✅ 100% COMPLETE

**Priority:** P1-HIGH

**What EXISTS:** Full stack analytics and crash reporting wired end-to-end.

**Completed:**
- [x] Firebase Android SDK in `androidApp/build.gradle.kts` (firebase-bom, analytics-ktx, crashlytics-ktx)
- [x] `google-services.json` injection in CI (`_reusable-build-test.yml` step)
- [x] `FirebaseAnalytics` initialization in `ZyntaApplication.kt` (line 83)
- [x] `AnalyticsTracker` interface in `:shared:core` (4 methods + event/param constants)
- [x] `AnalyticsService` expect/actual in `:shared:data` (Android: Firebase SDK, JVM: GA4 Measurement Protocol)
- [x] Koin binding `AnalyticsTracker` in both `androidDataModule` and `desktopDataModule`
- [x] Sentry initialization in Android app (`ZyntaApplication.kt` line 89)
- [x] Sentry initialization in backend API service (`Application.kt`)
- [x] Sentry initialization in backend License service (`Application.kt`)
- [x] Sentry initialization in backend Sync service (`Application.kt`)
- [x] Screen view events in ALL 16 feature ViewModels (Auth, POS, Dashboard, Inventory, Register, Reports, Settings, Customers, Coupons, Expenses, Staff, Multistore, Admin, Media, Onboarding, Accounting)
- [x] Analytics events: login, sale_completed, cart_updated, payment_processed, stock_adjusted, product_searched
- [x] Sentry `@sentry/react` SDK in admin panel with `Sentry.init()` in `main.tsx`
- [x] ErrorBoundary reports errors to Sentry via `Sentry.captureException()`

---

### A6. Security Monitoring (TODO-010) — ~95% Complete

**Priority:** P1-HIGH

**What's MISSING:**
- [x] Snyk Monitor step — already in `sec-backend-scan.yml` (weekly + on-demand)
- [x] Falcosidekick → Slack webhook wiring — already configured in docker-compose + falcosidekick.yaml
- [ ] Cloudflare tunnel config placeholder replacement (dashboard action — out of scope for code)
- [x] OWASP dependency check in CI pipeline — added to `ci-gate.yml` as `security-scan-backend` job
- [ ] CF Zero Trust + WAF rules (dashboard action — out of scope for code)

---

### A7. Admin JWT Security Gap — ✅ COMPLETE

**Priority:** P1-HIGH
**Issue:** Admin panel uses HS256 (symmetric) while POS uses RS256 (asymmetric)

**Fix:**
- [x] Migrate admin auth to RS256
- [x] Update `AdminAuthService.kt` token generation
- [x] Update License service admin JWT validation (`AdminJwtValidator.kt` now uses RS256 public key)
- [x] Rotate existing admin sessions (HS256 tokens rejected post-migration — re-login required)

---

## SECTION B: MEDIUM PRIORITY (P2)

---

### B1. Admin Panel Enhancements (TODO-007a) — ✅ ~99% Complete

- [x] Security dashboard page — `routes/security/index.tsx` (threat overview, recent events, active sessions)
- [ ] OTA update management page (deferred — requires device management backend)
- [x] Playwright E2E tests — `e2e/smoke.spec.ts` + `e2e/auth.spec.ts` (login, navigation, auth flows)
- [ ] VPS deployment via GitHub Actions (Caddy static site config — admin-panel Docker image already built by CI)

### B2. Admin Panel Custom Auth (TODO-007f) — ✅ 100% COMPLETE

- [x] Session management UI (view/revoke active sessions) — `settings/profile.tsx`
- [x] Security audit log page — `audit/index.tsx` with filters + CSV export
- [x] IP allowlisting middleware — `IpAllowlistPlugin` via `ADMIN_IP_ALLOWLIST` env var
- [x] Login notification emails — `sendLoginAlert()` in EmailService, triggered on new IP
- [x] Forced password rotation policy — `ADMIN_PASSWORD_MAX_AGE_DAYS` env var + `password_changed_at` column (V26 migration)

### B3. Monitoring — Uptime Kuma (TODO-007c) — ✅ ~95% Complete

- [x] Monitors for all 7 subdomains — `config/uptime-kuma/setup-monitors.sh` seeds HTTP monitors
- [x] Slack/email alert channels — documented in setup script (requires UI configuration)
- [x] Status page branding — documented in setup script (requires UI configuration)
- [x] Docker container health monitors — 6 container monitors in setup script
- [x] DB connection monitors — via /health endpoint checks

### B4. Backend Test Coverage — ~55% vs 80% target

> **HANDOFF (2026-03-18):** Test infra already existed (Testcontainers + 11 test files).
> This session expanded sync test coverage with 117 new test cases across 7 files,
> plus 1 new test file (SyncMetricsTest.kt). Total API test count: 225 (up from ~108).
> Existing tests: EntityApplierTest, SyncProcessorTest, DeltaEngineTest,
> ServerConflictResolverTest, SyncValidatorTest, SyncMetricsTest (NEW),
> AdminAuthServiceTest (x2), UserServiceTest, SyncPushPullIntegrationTest,
> AuthRoutesTest, CsrfPluginTest.
> Run: `cd backend/api && ./gradlew test --parallel` to verify all 225 pass.
> Next session: Repository integration tests (Testcontainers) + License service tests + coverage reporting CI step.

- [x] Testcontainers setup (PostgreSQL + Redis) — ALREADY EXISTS in `backend/api/build.gradle.kts`
- [x] `SyncProcessor`, `DeltaEngine`, `EntityApplier` tests — EXIST (basic coverage, need expansion)
- [x] `AdminAuthService` tests — EXIST (`AdminAuthServiceTest.kt` 272L + `AdminAuthServiceExtendedTest.kt` 519L)
- [x] Expand sync test coverage (edge cases, error paths, conflict resolution, field merge, metrics)
- [x] Repository integration tests (`ProductRepository`, `PosUserRepository`, `AdminUserRepository`) — DONE (2026-03-18)
  - `AbstractIntegrationTest` base class + `TestFixtures` factory (shared test infrastructure)
  - `ProductRepositoryTest` — 14 tests: CRUD, store scoping, pagination, updatedSince filter, nullable fields
  - `PosUserRepositoryTest` — 18 tests: store lookup, user queries, mutations, lockout, POS sessions, uniqueness
  - `AdminUserRepositoryTest` — 22 tests: CRUD, roles, lockout, sessions, password reset tokens
  - All use Testcontainers PostgreSQL + Flyway migrations (requires Docker — CI only)
- [ ] `LicenseService` tests (backend/license) — basic tests exist, need expansion
- [x] Coverage reporting in CI pipeline (Kover + threshold) — DONE (2026-03-18)
  - Kover `koverVerify { rule { minBound(60) } }` added to api, license, sync build.gradle.kts
  - `koverXmlReport` task + artifact upload added to ci-gate.yml test-backend job
  - JUnit 5 (`junit-jupiter-api:5.11.4`) added to api build for `@Nested`/`@BeforeEach` support

**Test Files (updated 2026-03-18):**
| File | Tests | Coverage |
|------|-------|----------|
| `sync/EntityApplierTest.kt` | 31 | All 7 entity types, payload parsing, missing fields, append-only audit |
| `sync/SyncProcessorTest.kt` | 18 | Push orchestration, conflict detection, dedup, dead letter, metrics |
| `sync/DeltaEngineTest.kt` | 19 | Cursor pagination, limit clamping, sequential pulls, boundary cases |
| `sync/ServerConflictResolverTest.kt` | 23 | LWW, device tiebreak, field merge, non-PRODUCT entities, log persistence |
| `sync/SyncValidatorTest.kt` | 38 | Field validation (S2-7), all entity types, batch limits, timestamp tolerance |
| `sync/SyncMetricsTest.kt` | 14 | Counters, P95 percentile, rolling window, conflict rate, snapshot |
| `service/AdminAuthServiceTest.kt` | 12 | Admin login + JWT |
| `service/AdminAuthServiceExtendedTest.kt` | 18 | BCrypt, MFA, lockout |
| `service/UserServiceTest.kt` | 17 | POS PIN auth |
| `integration/SyncPushPullIntegrationTest.kt` | 15 | End-to-end validation, batch boundaries, field validation |
| `routes/AuthRoutesTest.kt` | 12 | Auth HTTP routes |
| `routes/CsrfPluginTest.kt` | 7 | CSRF protection |

### B5. Mixed Timestamp Formats

- [ ] Standardize on `Instant` (kotlinx-datetime) across all services
- [ ] Add timestamp format validation in sync
- [ ] Document timestamp contract

### B6. Ticket System Enhancements (TODO-012) — COMPLETED (merged from main 2026-03-18)

**Priority:** P2-MEDIUM (HIGH per docs/todo)
**Status:** COMPLETED — All 8 tasks implemented and merged into main
**Ref:** `docs/todo/012-ticket-system-enhancements.md`

**What EXISTS:**
- `AdminTicketRoutes.kt` — CRUD + assign/resolve/close + comments
- `AdminTicketService.kt` — SLA deadline logic, `checkSlaBreaches()`
- `InboundEmailProcessor.kt` — HMAC-signed inbound email, dedup, thread linking
- `EmailService.kt` — Resend API, ticket_created/updated templates
- `ChatwootService.kt` — auto-creates conversations from inbound email
- `AlertGenerationJob.kt` — 60s interval background job
- DB: V5 (tickets, comments, attachments), V18 (email_threads), V20 (email_delivery_log)
- Frontend: `TicketTable`, `TicketCreateModal`, `TicketAssignModal`, `TicketResolveModal`, `TicketCommentThread`

**What WAS MISSING (8 tasks — ALL COMPLETED):**
- [x] **TASK 1:** Email thread viewing + reply-to-reply chain tracking (V21 migration: `parent_thread_id`)
- [x] **TASK 2:** Bulk ticket operations (assign, resolve, CSV export)
- [x] **TASK 3:** SLA breach email notifications (extend `checkSlaBreaches()`)
- [x] **TASK 4:** Advanced ticket filtering (date range, full-text search on body)
- [x] **TASK 5:** Ticket metrics/analytics endpoint (totalOpen, avgResolutionTime, etc.)
- [x] **TASK 6:** Agent reply by email (outbound from ticket comment)
- [x] **TASK 7:** Customer portal — public ticket status check via token URL (V22 migration)
- [x] **BUG FIX:** InboundEmailProcessor hardcoded SLA (always MEDIUM/48h) — should use `inferPriorityFromEmail()`

**Implementation Order:** BUG FIX → TASK 1 → TASK 3 → TASK 4 → TASK 5 → TASK 2 → TASK 6 → TASK 7

**Key Files:**
- `backend/api/src/main/kotlin/.../routes/AdminTicketRoutes.kt`
- `backend/api/src/main/kotlin/.../service/AdminTicketService.kt`
- `backend/api/src/main/kotlin/.../service/InboundEmailProcessor.kt`
- `backend/api/src/main/kotlin/.../service/EmailService.kt`
- `admin-panel/src/components/tickets/` (multiple new + modified files)
- `backend/api/src/main/resources/db/migration/V21__email_thread_chain.sql` (NEW)
- `backend/api/src/main/resources/db/migration/V22__ticket_customer_token.sql` (NEW)

---

## SECTION C: MULTI-STORE ENTERPRISE FEATURES (6 Categories)

> මෙම section එක ඔබ ලබා දුන් multi-store features 6 categories සම්පූර්ණයෙන් ආවරණය කරයි.
> එක් එක් feature එක codebase එකේ actual state එකත් සමඟ map කර ඇත.

---

### ═══════════════════════════════════════════════════════
### CATEGORY 1: මධ්‍යගත තොග කළමනාකරණය (Centralized Inventory Management)
### ═══════════════════════════════════════════════════════

---

### C1.1 Global Product Catalog (පොදු භාණ්ඩ නාමාවලිය)

**Priority:** PHASE-2
**Status:** NOT IMPLEMENTED

**Codebase State:**
- `Product` model has `storeId: String` — products are per-store, no global catalog concept
- `products.sq` has `store_id TEXT NOT NULL` — every product belongs to one store
- Backend `products` table (V1) also has `store_id TEXT NOT NULL REFERENCES stores(id)`
- No `master_product` or `global_product` concept exists anywhere

**What's MISSING:**
- [ ] `MasterProduct` domain model — template for products shared across stores
- [ ] `master_products` SQLDelight table (id, sku, barcode, name, description, base_price, category_id, image_url)
- [ ] `store_products` junction table (master_product_id, store_id, local_price, local_stock_qty, is_active)
- [ ] `MasterProductRepository` interface in `:shared:domain`
- [ ] `MasterProductRepositoryImpl` in `:shared:data`
- [ ] Backend migration: `master_products` + `store_products` tables
- [ ] Backend `MasterProductRoutes.kt` — CRUD for global catalog
- [ ] Admin panel: Global product catalog management UI
- [ ] KMM app: Store-local product override UI (price, stock)
- [ ] Sync: Master product changes propagate to all stores

**Key Files to Modify:**
- `shared/domain/src/commonMain/.../model/Product.kt`
- `shared/data/src/commonMain/sqldelight/.../products.sq`
- `backend/api/src/main/resources/db/migration/` (new V21)
- `:composeApp:feature:inventory` screens

---

### C1.2 Store-Specific Inventory Levels (ශාඛා අනුව තොග)

**Priority:** PHASE-2
**Status:** PARTIALLY EXISTS — global stock_qty only, no per-warehouse tracking

**Codebase State:**
- `Product.stockQty` is **global** (single number) — NOT per-warehouse or per-store
- `warehouses.sq` EXISTS — warehouse registry per store (`store_id` FK)
- `warehouse_racks.sq` EXISTS — rack shelving with capacity tracking
- `rack_products.sq` EXISTS — rack-level product location mapping (bin_location)
- `WarehouseRepositoryImpl.kt` — FULLY implemented (253 lines) with atomic two-phase commits
- `WarehouseRackRepository` + impl — FULLY implemented (120 lines)
- `WarehouseRack` use cases: Get, Save, Delete — all implemented
- `min_stock_qty` column exists on `products.sq` — reorder threshold
- **Reports exist:** `warehouseInventory` query (racks → products), `stockReorderAlerts` query
- **Comment in code:** "per-warehouse tracking is Phase 3" (WarehouseRepositoryImpl line 173)

**What's MISSING:**
- [ ] Per-warehouse stock levels (product.stock_qty is global — need warehouse_stock junction table)
- [ ] `warehouse_stock` table (warehouse_id, product_id, quantity) — replace global stock_qty
- [ ] Stock level aggregation API across stores (total stock for a product globally)
- [ ] Low-stock alerts per warehouse (currently per product globally)
- [ ] Admin panel: Cross-store/warehouse stock level comparison view
- [ ] Backend: `GET /admin/inventory/global?productId=X` endpoint
- [ ] Rack-product CRUD UI (schema exists in `rack_products.sq`, no management screens)

**Key Files:**
- `shared/data/src/commonMain/sqldelight/.../products.sq` (global stock_qty)
- `shared/data/src/commonMain/sqldelight/.../warehouses.sq`
- `shared/data/src/commonMain/sqldelight/.../warehouse_racks.sq`
- `shared/data/src/commonMain/sqldelight/.../rack_products.sq`
- `shared/data/src/commonMain/.../repository/WarehouseRepositoryImpl.kt` (253 lines)
- `shared/data/src/commonMain/.../repository/WarehouseRackRepositoryImpl.kt` (120 lines)
- `composeApp/feature/multistore/.../WarehouseListScreen.kt`
- `composeApp/feature/multistore/.../WarehouseRackListScreen.kt`

---

### C1.3 Inter-Store Stock Transfer / IST (ශාඛා අතර තොග හුවමාරුව)

**Priority:** PHASE-2
**Status:** WAREHOUSE-LEVEL IMPLEMENTED, STORE-LEVEL MISSING

**Codebase State:**
- `stock_transfers.sq` — FULLY IMPLEMENTED table (source_warehouse_id, dest_warehouse_id, product_id, quantity, status)
- `StockTransfer` domain model — status: PENDING → COMMITTED / CANCELLED (two-phase commit)
- `WarehouseRepositoryImpl.commitTransfer()` — atomic validation + stock adjustment + audit trail (TRANSFER_OUT/TRANSFER_IN)
- `CommitStockTransferUseCase.kt` — validates transfer exists and is PENDING
- `purchase_orders.sq` — SCHEMA ONLY (tables exist, no domain model/repo/UI)
- `NewStockTransferScreen.kt` + `StockTransferListScreen.kt` — UI screens exist
- `WarehouseViewModel.kt` (407 lines) — manages warehouses, transfers, racks
- Report: `interStoreTransfers` SQL query in `reports.sq` — committed transfers by date range

**What's MISSING (store-to-store level, beyond warehouse-to-warehouse):**
- [ ] Extend transfer workflow: PENDING → APPROVED → DISPATCHED → IN_TRANSIT → RECEIVED (currently only PENDING → COMMITTED)
- [ ] Multi-step approval workflow (currently auto-commit, no manager approval)
- [ ] Store-level transfer view (group warehouse transfers by source/dest store)
- [ ] Backend migration: Add store-level transfer tracking
- [ ] Backend: `POST /admin/transfers`, `PUT /admin/transfers/{id}/approve`
- [ ] Admin panel: Transfer management dashboard with store grouping
- [ ] Push notification when transfer arrives at destination store
- [ ] Purchase Order domain model + repository + UI (schema exists in `purchase_orders.sq`)
- [ ] `PurchaseOrder` domain model in `:shared:domain`
- [ ] `PurchaseOrderRepository` interface + impl

---

### C1.4 Stock In-Transit Tracking (මාර්ගයේ තොග නිරීක්ෂණය)

**Priority:** PHASE-2
**Status:** SCHEMA EXISTS, NO LOGIC

**Codebase State:**
- `StockTransfer` model has `IN_TRANSIT` status enum value
- `stock_transfers.sq` has `status TEXT NOT NULL DEFAULT 'REQUESTED'`
- No tracking of physical transit progress

**What's MISSING:**
- [ ] `TransitTracking` domain model (transfer_id, current_location, estimated_arrival, tracking_notes)
- [ ] `transit_tracking` SQLDelight table (transfer_id FK, status_update, timestamp, note)
- [ ] Real-time transit status updates via sync engine
- [ ] KMM UI: Transit tracker screen with status timeline
- [ ] Dashboard widget: "In-Transit Items" count per store
- [ ] Stock accounting: Products in transit deducted from source, not yet added to destination
- [ ] Backend: Transit status update endpoint

---

### C1.5 Warehouse-to-Store Replenishment (ස්වයංක්‍රීය නැවත ඇණවුම්)

**Priority:** PHASE-2
**Status:** REPORT EXISTS, NO AUTO-LOGIC

**Codebase State:**
- `products.sq` has `min_stock_qty REAL` — reorder threshold exists
- `purchase_orders.sq` EXISTS — schema for PO with items, status, supplier FK (SCHEMA ONLY — no domain model/repo/UI)
- `purchase_order_items` table — quantity_ordered, quantity_received, unit_cost, line_total
- `reports.sq` → `stockReorderAlerts` query — products WHERE stock_qty <= min_stock_qty with suggested reorder qty
- `ReportRepositoryImpl.getStockReorderAlerts()` — IMPLEMENTED
- `GenerateStockReorderReportUseCase` — IMPLEMENTED
- `StockReorderData` model — productId, productName, currentStock, reorderPoint, suggestedReorderQty
- **No automated PO generation** — alerts are informational only

**What's MISSING:**
- [ ] `PurchaseOrder` domain model in `:shared:domain`
- [ ] `PurchaseOrderRepository` interface + impl
- [ ] PO CRUD UI screens (create PO from reorder alert)
- [ ] `ReplenishmentRule` domain model (product_id, warehouse_id, reorder_point, reorder_qty, auto_approve)
- [ ] `replenishment_rules` SQLDelight table
- [ ] `AutoReplenishmentUseCase` — check stock vs reorder_point, auto-create transfer/PO
- [ ] Background job: Periodic stock check (daily or on stock change)
- [ ] KMM UI: Replenishment rules config + reorder alert → auto-PO generation
- [ ] Admin panel: Replenishment dashboard (pending auto-orders)
- [ ] Backend: `POST /admin/replenishment/rules`, `GET /admin/replenishment/suggestions`

---

### ═══════════════════════════════════════════════════════
### CATEGORY 2: මිල ගණන් සහ බදු කළමනාකරණය (Multi-Store Pricing & Taxation)
### ═══════════════════════════════════════════════════════

---

### C2.1 Region-Based Pricing (ප්‍රදේශ අනුව මිල)

**Priority:** PHASE-2
**Status:** NOT IMPLEMENTED

**Codebase State:**
- `Product.kt` has single `price: Double` — no regional override
- `products.sq` has single `price REAL` column
- `CartItem.kt` has `unitPrice: Double` — snapshot at time of cart add
- Backend `ProductDto` has single `price: Double`
- No `PricingRule`, `regional_price`, or `price_override` concept anywhere

**What's MISSING:**
- [ ] `PricingRule` domain model (id, product_id, store_id, region, price, valid_from, valid_to, priority)
- [ ] `pricing_rules` SQLDelight table
- [ ] `PricingRuleRepository` interface + impl
- [ ] `GetStorePriceUseCase` — resolve product price by store/region with fallback to base price
- [ ] Integrate into `PosViewModel` — use store-aware price at cart add time
- [ ] Backend migration: `pricing_rules` table
- [ ] Backend: `POST /admin/pricing-rules`, `GET /admin/pricing-rules?storeId=X`
- [ ] Admin panel: Price management UI (override per store, bulk update)
- [ ] KMM settings: Store pricing configuration screen

**Key Files to Modify:**
- `shared/domain/src/commonMain/.../model/Product.kt` (add `storePrice: Double?`)
- `shared/domain/src/commonMain/.../usecase/pos/CalculateOrderTotalsUseCase.kt`
- `composeApp/feature/pos/src/commonMain/.../PosViewModel.kt`

---

### C2.2 Multi-Currency Support (බහු මුදල් ඒකක)

**Priority:** PHASE-2
**Status:** PARTIAL — formatter exists, no conversion

**Codebase State:**
- `CurrencyFormatter.kt` — supports 9 currencies (LKR, USD, EUR, GBP, INR, JPY, AUD, CAD, SGD)
- `stores.sq` has `currency TEXT NOT NULL DEFAULT 'LKR'` — per-store currency
- `AppConfig.kt` has `DEFAULT_CURRENCY_CODE = "LKR"`, `CURRENCY_DECIMAL_PLACES = 2`
- `orders.sq` stores totals as `REAL` — no currency column on orders
- `CartItem.kt` has no currency field
- No exchange rate table or conversion logic

**What's MISSING:**
- [ ] `ExchangeRate` domain model (source_currency, target_currency, rate, effective_date)
- [ ] `exchange_rates` SQLDelight table
- [ ] `ExchangeRateRepository` interface + impl
- [ ] `ConvertCurrencyUseCase` — convert amount between currencies
- [ ] Add `currency TEXT` column to `orders.sq` (store currency at time of sale)
- [ ] `Store` domain model in `:shared:domain` (currently NO Store model — only table/DTO)
- [ ] `StoreRepository` interface in `:shared:domain` — expose store settings to business logic
- [ ] Backend migration: `exchange_rates` table + `currency` column on orders
- [ ] Backend: `GET /admin/exchange-rates`, `PUT /admin/exchange-rates`
- [ ] Admin panel: Exchange rate management UI
- [ ] KMM: Currency display using store's configured currency, not hardcoded LKR

**Key Files:**
- `shared/core/src/commonMain/.../utils/CurrencyFormatter.kt`
- `shared/core/src/commonMain/.../config/AppConfig.kt`
- `shared/data/src/commonMain/sqldelight/.../stores.sq`
- `shared/data/src/commonMain/sqldelight/.../orders.sq`

---

### C2.3 Localized Tax Configurations (ප්‍රදේශ අනුව බදු)

**Priority:** PHASE-2
**Status:** PARTIAL — single-region tax exists, no multi-region

**Codebase State:**
- `TaxGroup.kt` — model with `rate`, `isInclusive`, `isActive` (fully implemented)
- `tax_groups.sq` — table with CRUD queries (fully implemented)
- `CalculateOrderTotalsUseCase.kt` — 6 tax calculation scenarios (exclusive, inclusive, with discounts)
- `CartItem.taxRate` — snapshot per item (correct pattern)
- `TaxSettingsScreen.kt` — tax group CRUD UI in settings
- Backend `tax_rates` table (V3) — system-wide, not per-store
- Product → TaxGroup assignment via `taxGroupId` field

**What's MISSING:**
- [ ] `RegionalTax` domain model (store_id, tax_group_id, effective_rate, jurisdiction_code, valid_from, valid_to)
- [ ] `regional_tax_overrides` SQLDelight table — per-store tax rate overrides
- [ ] Tax group → region/store mapping logic
- [ ] Auto-select tax rate based on store's jurisdiction at checkout
- [ ] Support for compound taxes (VAT + service charge + local surcharge stacked)
- [ ] Tax registration number per store (legal requirement in many jurisdictions)
- [ ] Backend migration: `regional_tax_overrides` table, `tax_registration_number` on stores
- [ ] Backend: `GET /admin/taxes/by-store/{storeId}`
- [ ] KMM settings: Per-store tax override configuration

**Key Files:**
- `shared/domain/src/commonMain/.../model/TaxGroup.kt`
- `shared/domain/src/commonMain/.../usecase/pos/CalculateOrderTotalsUseCase.kt`
- `shared/data/src/commonMain/sqldelight/.../tax_groups.sq`
- `composeApp/feature/settings/src/commonMain/.../TaxSettingsScreen.kt`

---

### C2.4 Store-Specific Discounts & Promotions (ශාඛා අනුව වට්ටම්)

**Priority:** PHASE-2
**Status:** PARTIAL — global promotions exist, no store scoping

**Codebase State:**
- `Coupon.kt` — model with scope (CART/PRODUCT/CATEGORY/CUSTOMER), discount types (FIXED/PERCENT/BOGO)
- `Promotion.kt` — model with types (BUY_X_GET_Y, BUNDLE, FLASH_SALE, SCHEDULED), priority-based
- `coupons.sq` — coupons + promotions tables with `scope_ids` JSON array
- `CalculateCouponDiscountUseCase.kt`, `ValidateCouponUseCase.kt` — validation + calculation
- `ApplyItemDiscountUseCase.kt`, `ApplyOrderDiscountUseCase.kt` — role-gated discounts
- **NO `store_id` FK** on coupons or promotions tables

**What's MISSING:**
- [ ] Add `store_id TEXT` (nullable) to `coupons.sq` — null = global, non-null = store-specific
- [ ] Add `store_ids TEXT` (JSON array) to `promotions` table — target specific stores
- [ ] `GetStorePromotionsUseCase` — filter active promotions by current store
- [ ] Store-specific discount limits (e.g., max 20% at store A, max 30% at store B)
- [ ] Promotion conflict resolution when multiple match (BOGO + coupon both applicable)
- [ ] `PromotionConfig` sealed class to replace untyped JSON `config` field
- [ ] Backend: `GET /admin/promotions?storeId=X`
- [ ] Admin panel: Store-scoped promotion management
- [ ] KMM: Auto-apply store promotions at checkout

**Key Files:**
- `shared/domain/src/commonMain/.../model/Coupon.kt`
- `shared/domain/src/commonMain/.../model/Promotion.kt`
- `shared/data/src/commonMain/sqldelight/.../coupons.sq`
- `shared/domain/src/commonMain/.../usecase/coupons/`

---

### ═══════════════════════════════════════════════════════
### CATEGORY 3: පරිශීලක ප්‍රවේශ සීමාවන් (User Access Control & Permissions)
### ═══════════════════════════════════════════════════════

---

### C3.1 Role-Based Access Control — RBAC

**Priority:** PHASE-2
**Status:** IMPLEMENTED (Phase 1 complete)

**Codebase State:**
- `Role.kt` — 5 roles: ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER
- `Permission.kt` — 40+ granular permissions for all POS operations
- `CustomRole.kt` — custom role creation with explicit permission sets
- `RbacEngine.kt` in `:shared:security` — stateless permission checker
- Navigation RBAC gating in `ZyntaNavGraph.kt`
- Admin panel: 5 roles (ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK) with 39 permissions

**COMPLETE — no action needed**

---

### C3.2 Store-Level Permissions (ශාඛා මට්ටමේ ප්‍රවේශ)

**Priority:** PHASE-2
**Status:** NOT IMPLEMENTED

**Codebase State:**
- `users.sq` has `store_id TEXT NOT NULL` — each user belongs to ONE store only
- `employees.sq` has `store_id TEXT NOT NULL` — same limitation
- Backend `users` table: `UNIQUE (store_id, username)` — username scoped per store
- No `user_allowed_stores` junction table
- No mechanism for a user to access data from a different store

**What's MISSING:**
- [ ] `user_store_access` junction table (user_id, store_id, role, granted_at, granted_by)
- [ ] `UserStoreAccess` domain model
- [ ] `UserStoreAccessRepository` interface + impl
- [ ] Modify `RbacEngine` to accept `storeId` parameter for permission checks
- [ ] Backend migration: `user_store_access` table
- [ ] Backend: Middleware to validate user has access to requested store data
- [ ] Admin panel: User → store assignment management UI
- [ ] KMM: Store selector for users with multi-store access

---

### C3.3 Global Admin Dashboard (ප්‍රධාන පාලක පුවරුව)

**Priority:** PHASE-2
**Status:** PARTIALLY EXISTS

**Codebase State:**
- Admin panel dashboard: `/admin/metrics/dashboard` — totalStores, activeLicenses, revenueToday, syncHealth
- `AdminStoresRoutes.kt` — store list, health, config endpoints
- `AdminMetricsService.kt` — `getDashboardKPIs()`, `getStoreComparison()`, `getSalesChart()`
- KMM `:composeApp:feature:multistore` — scaffold only

**What's MISSING:**
- [ ] KMM app: Multi-store dashboard screen (see all store KPIs from a single view)
- [ ] KMM app: Store switcher (select which store to operate as)
- [ ] Real-time WebSocket updates for dashboard KPIs (currently REST polling)
- [ ] Cross-store notifications (e.g., "Store B low on Product X")

---

### C3.4 Employee Roaming (සේවක බහු-ශාඛා ප්‍රවේශය)

**Priority:** PHASE-2
**Status:** NOT IMPLEMENTED

**Codebase State:**
- Employees tied 1:1 to store via `employees.store_id NOT NULL`
- `attendance_records.sq` — clock in/out per employee (no cross-store tracking)
- `shift_schedules.sq` — shifts scoped by `store_id` (no cross-store shifts)

**What's MISSING:**
- [ ] `employee_store_assignments` table (employee_id, store_id, start_date, end_date, is_temporary)
- [ ] `EmployeeStoreAssignment` domain model
- [ ] Modify `attendance_records.sq` — add `store_id TEXT` for where they clocked in
- [ ] Modify `shift_schedules.sq` — allow shifts across different stores for same employee
- [ ] `AssignEmployeeToStoreUseCase`, `GetEmployeeStoresUseCase`
- [ ] KMM UI: Employee store assignment management
- [ ] KMM UI: Store selector on clock-in screen (if employee has multi-store access)
- [ ] Backend migration: `employee_store_assignments` table
- [ ] Cross-store attendance reports

---

### ═══════════════════════════════════════════════════════
### CATEGORY 4: විකුණුම් සහ පාරිභෝගික කළමනාකරණය (Sales & Customer Management)
### ═══════════════════════════════════════════════════════

---

### C4.1 Cross-Store Returns (බහු-ශාඛා ප්‍රතිලාභ)

**Priority:** PHASE-2
**Status:** NOT IMPLEMENTED

**Codebase State:**
- `OrderType.REFUND` exists — refund orders can be created
- `orders.sq` has `store_id TEXT NOT NULL` — each order/refund tied to one store
- Permission `PROCESS_REFUND` exists in RBAC
- No concept of "original store" for a refund

**What's MISSING:**
- [ ] Add `original_store_id TEXT` to orders table (for refunds initiated at different store)
- [ ] Add `original_order_id TEXT` to orders table (link refund to original sale)
- [ ] `ProcessCrossStoreRefundUseCase` — validate original order exists, process return
- [ ] Cross-store inventory adjustment (return stock to original or current store?)
- [ ] Business rule: Configurable policy — stock goes to return store vs original store
- [ ] KMM POS: Lookup order by ID/receipt from any store for return processing
- [ ] Backend: Cross-store order lookup endpoint
- [ ] Sync: Refund propagation to original store for accounting

---

### C4.2 Universal Loyalty Program (සර්වත්‍ර පක්ෂපාතිත්ව වැඩසටහන)

**Priority:** PHASE-2
**Status:** PARTIAL — points exist, no cross-store/redemption logic

**Codebase State:**
- `customers.sq` has `loyalty_points INTEGER` (aggregate field)
- `reward_points.sq` EXISTS — points ledger table with per-customer tracking
- `RewardPoints.kt` domain model exists
- `LoyaltyTier.kt` — tier definitions with discount multiplier (Bronze/Silver/Gold/Platinum)
- `loyalty_tiers` SQLDelight table exists
- No store scoping on loyalty — points are inherently global to customer

**What's MISSING:**
- [ ] `EarnPointsUseCase` — calculate points earned per purchase (configurable rate per store?)
- [ ] `RedeemPointsUseCase` — apply points as discount at checkout
- [ ] Points redemption flow integration into POS checkout
- [ ] Cross-store points earning/spending (ensure universal acceptance)
- [ ] Loyalty tier progression logic (auto-upgrade/downgrade based on spend)
- [ ] Points expiry policy (e.g., expire after 12 months inactive)
- [ ] KMM POS: "Apply Loyalty Points" button at checkout
- [ ] KMM: Customer loyalty summary screen
- [ ] Backend: `GET /admin/loyalty/summary`, `POST /admin/loyalty/rules`

**Key Files:**
- `shared/data/src/commonMain/sqldelight/.../reward_points.sq`
- `shared/domain/src/commonMain/.../model/RewardPoints.kt`
- `shared/domain/src/commonMain/.../model/LoyaltyTier.kt`

---

### C4.3 Centralized Customer Profiles (මධ්‍යගත පාරිභෝගික දත්ත)

**Priority:** PHASE-2
**Status:** AMBIGUOUS — nullable store_id exists

**Codebase State:**
- `customers.sq` has `store_id TEXT` (nullable) — customers CAN be global
- `Customer.kt` model has `storeId: String` but customer table allows NULL
- Backend `customers` table (V12) has `store_id TEXT NOT NULL` — inconsistency with KMM
- GDPR export endpoint exists in backend (`ExportRoutes.kt`)
- No clear strategy for global vs per-store customer profiles

**What's MISSING:**
- [ ] Resolve store_id ambiguity: Make customers truly global (remove NOT NULL on backend)
- [ ] Customer merge utility — merge duplicate profiles across stores
- [ ] Global customer search — find customer from any store
- [ ] `CustomerMergeUseCase` — merge two customer records (combine points, order history)
- [ ] GDPR export UI in KMM app (backend route exists)
- [ ] Customer purchase history spanning all stores
- [ ] Backend: `GET /admin/customers/global?search=X` (cross-store search)
- [ ] Admin panel: Global customer directory with store filter

---

### C4.4 Click & Collect / BOPIS (අන්තර්ජාල ඇණවුම් + ශාඛා භාරගැනීම)

**Priority:** PHASE-3
**Status:** NOT IMPLEMENTED

**Codebase State:**
- `OrderType` enum has `SALE, REFUND, HOLD` — no `CLICK_AND_COLLECT`
- `OrderStatus` has `IN_PROGRESS, COMPLETED, VOIDED, HELD` — no fulfillment statuses
- No pickup location, fulfillment workflow, or online ordering system
- Zero references to "pickup", "bopis", "fulfillment" in codebase

**What's MISSING:**
- [ ] Add `CLICK_AND_COLLECT` to `OrderType` enum
- [ ] Add fulfillment statuses: `RECEIVED, PREPARING, READY_FOR_PICKUP, PICKED_UP, EXPIRED`
- [ ] `fulfillment_orders` SQLDelight table (order_id, pickup_store_id, pickup_date, status, customer_notified)
- [ ] Online ordering API (or integration with external ordering platform)
- [ ] Push notification to customer: "Your order is ready for pickup"
- [ ] Push notification to store: "New pickup order received"
- [ ] `FulfillmentRepository` interface + impl
- [ ] KMM POS: Fulfillment queue screen (list of pending pickups)
- [ ] KMM POS: Mark order as ready/picked-up
- [ ] Backend: Fulfillment endpoints
- [ ] Timeout: Auto-cancel if not picked up within X hours

---

### ═══════════════════════════════════════════════════════
### CATEGORY 5: වාර්තා සහ විශ්ලේෂණ (Reporting & Analytics)
### ═══════════════════════════════════════════════════════

---

### C5.1 Consolidated Financial Reports (ඒකාබද්ධ මූල්‍ය වාර්තා)

**Priority:** PHASE-2
**Status:** PARTIAL — single-store P&L exists, no multi-store consolidation

**Codebase State:**
- `FinancialStatement.kt` — full models: P&L, BalanceSheet, TrialBalance, CashFlow
- `FinancialStatementRepository.kt` — contracts for all 4 statement types (all accept `storeId`)
- `FinancialStatementsViewModel.kt` — 4-tab UI (P&L, Balance Sheet, Trial Balance, Cash Flow)
- `FinancialStatementRepositoryImpl.kt` — **PLACEHOLDER** (Phase 2 reference)
- Backend `AdminMetricsService.kt` — `getDashboardKPIs()` has `revenueToday` aggregate
- 49 report use cases exist in `:shared:domain`
- `GenerateMultiStoreComparisonReportUseCase.kt` — **STUB returning empty list**

**What's MISSING:**
- [ ] Implement `FinancialStatementRepositoryImpl.kt` — actual queries against `journal_entries`, `account_balances`
- [ ] `ConsolidatedFinancialReportUseCase` — aggregate P&L across all stores
- [ ] Backend: `GET /admin/reports/consolidated-pnl?from=X&to=Y`
- [ ] Backend: `GET /admin/reports/consolidated-balance-sheet?asOf=X`
- [ ] Multi-currency consolidation (convert all store revenues to base currency)
- [ ] Inter-store transaction elimination (remove internal transfers from consolidated reports)
- [ ] Admin panel: Consolidated financial report pages
- [ ] CSV/PDF export for consolidated reports

**Key Files:**
- `shared/domain/src/commonMain/.../model/FinancialStatement.kt`
- `shared/domain/src/commonMain/.../repository/FinancialStatementRepository.kt`
- `shared/data/src/commonMain/.../repository/FinancialStatementRepositoryImpl.kt`
- `composeApp/feature/accounting/src/commonMain/.../FinancialStatementsViewModel.kt`

---

### C5.2 Store Comparison Analytics (ශාඛා සංසන්දන විශ්ලේෂණ)

**Priority:** PHASE-2
**Status:** PARTIAL — backend endpoint exists, KMM stub

**Codebase State:**
- Backend: `GET /admin/metrics/stores?period={period}` → `List<StoreComparisonData>` (revenue, orders, growth)
- Backend: `GET /admin/metrics/sales?period=X&storeId=Y` → `List<SalesChartData>` (date, revenue, orders, AOV)
- KMM: `GenerateMultiStoreComparisonReportUseCase.kt` — stub returning empty list
- KMM: `StoreSalesData` model (storeId, storeName, totalRevenue, orderCount, averageOrderValue)
- Admin panel: Store health pages exist but no side-by-side comparison charts

**What's MISSING:**
- [ ] Implement `GenerateMultiStoreComparisonReportUseCase` — call backend API
- [ ] KMM UI: Store comparison chart screen (bar chart: revenue per store)
- [ ] KMM UI: Rankings screen (top-performing stores by revenue, orders, margin)
- [ ] Backend: `GET /admin/reports/store-ranking?metric=revenue&period=monthly`
- [ ] Backend: Profit/margin comparison (not just revenue/orders)
- [ ] Admin panel: Interactive comparison dashboard with filters
- [ ] Trend analysis: Growth % per store over time

---

### C5.3 Individual Store Audit Logs (ශාඛා අනුව විගණන ලොග්)

**Priority:** PHASE-2
**Status:** EXISTS — fully implemented

**Codebase State:**
- `audit_entries` table (V14) — per-store audit log with `store_id` FK
- `admin_audit_log` table (V3) — admin actions with optional `store_id`
- `audit_log` SQLDelight table — client-side audit with `store_id`
- Audit events: hash-chained for tamper detection
- Fields: event_type, user_id, entity_type, entity_id, previous_value, new_value

**COMPLETE — minor enhancements only:**
- [ ] KMM UI: Dedicated audit log viewer screen (currently debug console only)
- [ ] Admin panel: Store-filtered audit log page (exists but could add export)

---

### C5.4 Real-time Dashboard (සජීවී පාලක පුවරුව)

**Priority:** PHASE-2
**Status:** PARTIAL — REST polling, no WebSocket push

**Codebase State:**
- `DashboardViewModel.kt` — loads KPIs (revenue, orders, AOV, hourly sparkline, weekly chart)
- Backend: `GET /admin/metrics/dashboard`, `GET /admin/metrics/sales`
- Backend sync: `WebSocketHub.kt` — per-store WebSocket connections exist for sync
- Backend: `SyncMetrics.kt` — real-time counters (ops accepted/rejected, P95 latency)
- No WebSocket channel for dashboard KPI streaming

**What's MISSING:**
- [ ] WebSocket channel: `ws://sync/dashboard/{storeId}` — push KPI updates on new orders
- [ ] Backend: Publish dashboard events to Redis when order completes
- [ ] `RedisPubSubListener` — subscribe to `dashboard:update:{storeId}` topic
- [ ] KMM: `DashboardViewModel` connect to WebSocket for live updates
- [ ] Admin panel: WebSocket connection for live store metrics
- [ ] SLA alerting: Notify admin when revenue drops below expected or sync queue grows

---

### ═══════════════════════════════════════════════════════
### CATEGORY 6: සමගාමී දත්ත සහ නොබැඳි සහාය (Synchronisation & Offline Support)
### ═══════════════════════════════════════════════════════

---

### C6.1 Multi-Node Data Sync (ශාඛා අතර දත්ත සමගාමීකරණය)

**Priority:** PHASE-2
**Status:** PARTIAL — LWW sync exists, CRDT deferred

**Codebase State:**
- KMM client: `sync_queue.sq` (outbox pattern), `sync_state.sq` (cursor), `version_vectors.sq` (CRDT metadata)
- KMM client: `ConflictResolver.kt` — LWW with field-level merge for PRODUCT
- Backend: `SyncProcessor.kt` (push), `DeltaEngine.kt` (pull), `ServerConflictResolver.kt` (LWW)
- Backend: `sync_operations` table with `server_seq BIGSERIAL` monotonic ordering
- WebSocket: `WebSocketHub` per-store broadcast, `RedisPubSubListener` pub/sub
- Feature flag: `crdt_sync` (disabled, ENTERPRISE)

**What's MISSING:**
- [ ] CRDT merge implementations (G-Counter for stock, LWW-Register for fields, OR-Set for collections)
- [ ] Vector clock management utility in `:shared:core`
- [ ] Multi-store sync isolation (ensure store A data never leaks to store B)
- [ ] Sync priority: Critical data (orders, payments) synced before low-priority (reports, settings)
- [ ] Bandwidth optimization: Delta compression for large payloads
- [ ] Offline queue size management (purge stale sync ops after X days)
- [ ] Conflict UI in KMM app (show conflicts, allow manual resolution)

**Key Files:**
- `shared/data/src/commonMain/kotlin/.../sync/ConflictResolver.kt`
- `shared/data/src/commonMain/sqldelight/.../sync_queue.sq`
- `shared/data/src/commonMain/sqldelight/.../version_vectors.sq`
- `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt`
- `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt`

---

### C6.2 Offline-First Capability (නොබැඳි හැකියාව)

**Priority:** PHASE-2
**Status:** PARTIAL — sync engine exists, end-to-end not complete

**Codebase State:**
- All data written to local SQLite immediately (offline-first by design)
- `sync_queue.sq` — outbox pattern for pending operations
- `sync_state.sq` — cursor tracking for incremental pulls
- `SyncEngine.kt` in `:shared:data` — coordinates push/pull cycles
- Backend: Push/pull endpoints exist and work

**What's MISSING:**
- [ ] Complete `EntityApplier` for all entity types (see A1)
- [ ] Background sync worker (periodic sync when online) — Android WorkManager / JVM coroutine scheduler
- [ ] Network connectivity detection → auto-trigger sync
- [ ] Sync progress indicator in KMM UI (syncing X of Y operations)
- [ ] Conflict notification to user (toast when sync conflict detected)
- [ ] Offline indicator in status bar (show when device is offline)
- [ ] Data integrity check: Verify local DB consistency on app startup

---

### C6.3 Timezone Management (වේලා කලාප කළමනාකරණය)

**Priority:** PHASE-2
**Status:** MOSTLY IMPLEMENTED

**Codebase State:**
- `AppTimezone.kt` — singleton with `set(tzId)`, `current: TimeZone` (default: Asia/Colombo)
- `DateTimeUtils.kt` — `nowLocal(tz)`, `startOfDay(epochMs, tz)`, `endOfDay(epochMs, tz)`, `formatForDisplay(epochMs, tz)`
- `stores.sq` has `timezone TEXT NOT NULL DEFAULT 'Asia/Colombo'`
- Admin panel: `use-timezone.ts` hook + `timezone-store.ts` Zustand store
- Backend: `OffsetDateTime.now(UTC)` — server always in UTC

**What's MISSING:**
- [ ] Load store timezone on app startup → call `AppTimezone.set(store.timezone)`
- [ ] Multi-store timezone handling: When admin views reports from different timezones
- [ ] Report date range conversion: User selects "Today" → convert to store's timezone for query
- [ ] Receipt timestamp: Print in store's local timezone, not UTC
- [ ] Sync timestamp normalization: All sync operations use UTC, display converts to local
- [ ] DST (Daylight Saving Time) handling for stores in affected regions

---

## SECTION D: PHASE 3 FEATURES (Enterprise)

---

### D1. E-Invoice & IRD Submission (TODO-005)

**Priority:** PHASE-3
**Module:** `:composeApp:feature:accounting`

**EXISTS:** `EInvoiceRepositoryImpl.kt` (scaffold), `e_invoices` SQLDelight table, IRD secret placeholders

**MISSING:**
- [ ] IRD API client, digital signature (.p12), XML/JSON generation
- [ ] Submission pipeline with retry, status tracking (SUBMITTED → ACCEPTED → REJECTED)
- [ ] Tax calculation alignment with IRD rules

---

### D2. Staff, Shifts & Payroll

**Priority:** PHASE-3
**Module:** `:composeApp:feature:staff`

**EXISTS:** `attendance_records.sq`, `shift_schedules.sq`, `leave_records` (SQLDelight), `employees.sq`, `payroll_records` table

**MISSING:**
- [ ] Payroll calculation engine (salary, overtime, deductions)
- [ ] Leave management workflow (request → approve → track)
- [ ] KMM UI: Staff module screens (currently scaffold)
- [ ] Cross-store attendance/shifts (see C3.4)

---

### D3. Expense Tracking & Accounting

**Priority:** PHASE-3
**Module:** `:composeApp:feature:expenses`

**EXISTS:** `expenses` SQLDelight table, `journal_entries` + `chart_of_accounts` tables

**MISSING:**
- [ ] Expense log CRUD UI
- [ ] Receipt image attachment
- [ ] P&L integration (connect expenses to financial statements)
- [ ] Budget tracking per store/category

---

### D4. CashDrawerController (HAL)

**Priority:** PHASE-2

**MISSING:**
- [ ] `CashDrawerPort` interface in `:shared:hal`
- [ ] Android USB + JVM serial implementations
- [ ] ESC/POS kick command, auto-open on payment

---

## SECTION E: CI/CD & INFRASTRUCTURE GAPS

---

### E1. CI Pipeline Enhancements

- [ ] OWASP dependency-check Gradle plugin
- [ ] Snyk security scan
- [ ] Test coverage threshold (fail if < 60%)
- [ ] Playwright E2E tests for admin panel
- [ ] `google-services.json` decode step

### E2. Deployment Enhancements

- [ ] Admin panel static deploy to Caddy
- [ ] API docs site deployment
- [ ] DB migration dry-run before deploy
- [ ] Blue-green deployment
- [ ] Automated backup before deploy

---

## SECTION G: UI/UX GAP AUDIT (Comprehensive — All Feature Modules)

> 2026-03-18 දින codebase scan කරලා features 16ක්, design system, navigation,
> onboarding සහ admin panel සියල්ල audit කර ඇත. එක් එක් screen එකේ
> MVI compliance, responsive design, error/loading/empty states, accessibility,
> සහ multi-store readiness check කර ඇත.
>
> **Deep audit completed:** Multistore (G16, 6 screens, 2,158 LOC, score 9/10) සහ
> Inventory (G17, 10+ screens, 4,200+ LOC, score 8/10) modules screen-by-screen
> audit කර ඇත. Gap IDs: MS-1 to MS-6, INV-1 to INV-10 — total 16 actionable gaps.
> Navigation route gaps: G18 (6 missing routes identified).

---

### G1. Design System Missing Components (`:composeApp:designsystem`)

**Current:** 27 Zynta* components exist (Button, TextField, CurrencyText, ProductCard, NumericPad, etc.)

**MISSING components needed for Phase 2+:**

| Component | Purpose | Priority | Blocks |
|-----------|---------|----------|--------|
| `ZyntaStoreSelector` | Active store picker in drawer footer + toolbar | **CRITICAL** | All multi-store features |
| `ZyntaCurrencyPicker` | Currency selection dropdown (9 currencies supported) | **CRITICAL** | C2.2 Multi-currency |
| `ZyntaTimezonePicker` | Timezone selection with UTC offset display | **HIGH** | C6.3 Timezone |
| `ZyntaTransferStatusBadge` | Pending/Approved/Shipped/Received/Cancelled states | **HIGH** | C1.3 IST |
| `ZyntaLoyaltyBadge` | Customer loyalty tier indicator (Bronze/Silver/Gold) | **MEDIUM** | C4.2 Loyalty |
| `ZyntaDateRangeSelector` | Two-date picker for report filters | **MEDIUM** | C5.1 Reports |
| `ZyntaWarehouseDropdown` | Warehouse context switcher | **MEDIUM** | C1.2 Inventory |
| `ZyntaConflictResolutionUI` | CRDT merge conflict presentation | **LOW** | C6.1 Sync |

---

### G2. Onboarding Gaps (`:composeApp:feature:onboarding`)

**Current:** 2-step wizard (Business Name → Admin Account)

**MISSING steps needed for correct store setup:**
- [ ] **Step 3: Currency & Timezone** — Select store currency + timezone (currently defaults to LKR + Asia/Colombo)
- [ ] **Step 4: Basic Tax Setup** — Optional tax group configuration (can defer to Settings)
- [ ] **Step 5: Receipt Format** — Optional printer/receipt configuration
- [ ] Multi-store setup flow (Phase 2 — additional store creation)

---

### G3. POS Module UI/UX Gaps (`:composeApp:feature:pos` — 32 files, ~3200 LOC)

**Production-ready:** Full cart → payment → receipt flow with adaptive layouts

| Gap | Severity | Phase |
|-----|----------|-------|
| **No store switcher** — POS assumes single store | CRITICAL | Phase 2 |
| **No loyalty points redemption at checkout** — Balance shown but no "Spend Points" UI | HIGH | Phase 2 |
| **No cross-store return processing** — No UI to scan/identify items from other stores | HIGH | Phase 2 |
| **Gift card lookup returns "Phase 2" stub** | MEDIUM | Phase 2 |
| **No card terminal integration UI** — No EMV reader connection status | HIGH | Phase 2 |
| **No wallet payment choice dialog** — Amount combined in total, unclear if applied | MEDIUM | Phase 2 |
| **No employee badge/name on POS screen header** | LOW | Phase 2 |
| **No multi-currency display** at checkout | MEDIUM | Phase 2 |
| **No coupon barcode scanning preview** | LOW | Phase 2 |

---

### G4. Auth Module UI/UX Gaps (`:composeApp:feature:auth` — 13 files, ~600 LOC)

**Production-ready:** Email/password login + PIN lock with adaptive layouts

| Gap | Severity | Phase |
|-----|----------|-------|
| **No store selector at login** — Multi-store users can't pick location | CRITICAL | Phase 2 |
| **No employee quick-switch** — Full logout required to change user | HIGH | Phase 2 |
| **Password reset is stub** ("contact admin") | MEDIUM | Phase 2 |
| **No biometric fallback** on PIN lock (fingerprint/Face ID) | LOW | Phase 2 |
| **Remember Me checkbox collected but not persisted** | LOW | Phase 1.5 |
| **No PIN lockout timer countdown** — User doesn't know wait time | MEDIUM | Phase 2 |
| **No session timeout warning** — Auto-lock without countdown | LOW | Phase 2 |

---

### G5. Register Module UI/UX Gaps (`:composeApp:feature:register` — 13 files, ~1400 LOC)

**Production-ready:** Open/close register with cash discrepancy detection

| Gap | Severity | Phase |
|-----|----------|-------|
| **No multi-store cash consolidation** — Single register view | HIGH | Phase 2 |
| **No discrepancy approval workflow** — Warning shown, no manager sign-off | MEDIUM | Phase 2 |
| **No shift handoff flow** — No cashier takeover process | MEDIUM | Phase 2 |
| **No cash removal authorization** — Large cash-outs bypass oversight | MEDIUM | Phase 2 |
| **No float tracking** — Register float vs sales cash not separated | MEDIUM | Phase 2 |
| **No register location label** (e.g., "Front Counter", "Lane 3") | LOW | Phase 2 |

---

### G6. Reports Module UI/UX Gaps (`:composeApp:feature:reports` — 13 files)

**Production-ready:** Sales, Stock, Customer, Expense reports with CSV export

| Gap | Severity | Phase |
|-----|----------|-------|
| **No real-time WebSocket updates** — Reports load once, never auto-refresh | CRITICAL | Phase 2 |
| **No multi-store consolidation** — All reports single-store only | CRITICAL | Phase 2 |
| **No store comparison charts** — No side-by-side performance | HIGH | Phase 2 |
| **PDF export buttons present but may be stubbed** | HIGH | Phase 2 |
| **No drill-down** — Clicking chart points doesn't navigate to transactions | MEDIUM | Phase 2 |
| **No report scheduling/email** — Can't schedule daily/weekly reports | LOW | Phase 3 |
| **No pagination for large datasets** — May crash with 10K+ products | MEDIUM | Phase 2 |
| **Date formatting doesn't respect GeneralSettings preference** | MEDIUM | Phase 2 |

---

### G7. Dashboard Module UI/UX Gaps (`:composeApp:feature:dashboard` — 6 files, 869 LOC)

**Production-ready:** Single-store KPIs with adaptive 3-variant layout

| Gap | Severity | Phase |
|-----|----------|-------|
| **No real-time updates** — Loads once on screen open, never refreshes | CRITICAL | Phase 2 |
| **No multi-store KPI consolidation** | CRITICAL | Phase 2 |
| **Daily sales target hardcoded** ("LKR 50,000") not configurable | MEDIUM | Phase 2 |
| **Hourly sparkline data calculated but never rendered** | LOW | Phase 1.5 |
| **No comparison to previous period** (yesterday, last week) | MEDIUM | Phase 2 |
| **Notifications menu item exists but not implemented** | LOW | Phase 2 |

---

### G8. Settings Module UI/UX Gaps (`:composeApp:feature:settings` — 27 files)

**Production-ready:** Store identity, tax, printer, user mgmt, RBAC, backup, appearance

| Gap | Severity | Phase |
|-----|----------|-------|
| **No multi-region tax support** — Single tax group globally, no per-store override | HIGH | Phase 2 |
| **No multi-currency management** — Single global currency selector | HIGH | Phase 2 |
| **Timezone dropdown hardcoded** — Static list, no auto-detect or UTC offset shown | MEDIUM | Phase 2 |
| **No receipt template visual editor** | LOW | Phase 3 |
| **No printer connection test button visible in UI** | LOW | Phase 1.5 |
| **No settings sync to backend** — Local only | MEDIUM | Phase 2 |
| **Language selector disabled** — No i18n infrastructure | LOW | Phase 3 |

---

### G9. Accounting Module UI/UX Gaps (`:composeApp:feature:accounting` — 25 files)

**UI shell exists:** P&L, Balance Sheet, Trial Balance, Cash Flow tabs + Chart of Accounts + E-Invoices

| Gap | Severity | Phase |
|-----|----------|-------|
| **Financial statements are UI shells** — No real data population from GL | CRITICAL | Phase 2 |
| **No date picker dialog** — Manual text entry required (YYYY-MM-DD) | HIGH | Phase 2 |
| **No multi-store P&L consolidation** | HIGH | Phase 2 |
| **No export buttons** on any financial statement | HIGH | Phase 2 |
| **No account reconciliation workflow** | MEDIUM | Phase 3 |
| **E-invoice list exists but no IRD submission flow** | MEDIUM | Phase 3 |
| **Trial Balance "UNBALANCED" error has no remediation path** | LOW | Phase 2 |

---

### G10. Customers Module UI/UX Gaps (`:composeApp:feature:customers` — 9 files, 453 LOC VM)

**Production-ready:** Customer CRUD, groups, wallet, credit management

| Gap | Severity | Phase |
|-----|----------|-------|
| **No GDPR Export button** — Backend supports, UI missing | HIGH | Phase 2 |
| **No cross-store customer profile view** | MEDIUM | Phase 2 |
| **No loyalty tier display** — Raw points only, no Bronze/Silver/Gold badge | MEDIUM | Phase 2 |
| **No bulk customer import** (CSV) | LOW | Phase 3 |
| **No advanced customer segmentation/filtering** | LOW | Phase 3 |

---

### G11. Staff Module UI/UX Gaps (`:composeApp:feature:staff` — 12 files, 673 LOC VM)

**Well-implemented (95%):** Employee CRUD, attendance, leave, shifts, payroll

| Gap | Severity | Phase |
|-----|----------|-------|
| **No roaming/multi-store dashboard** — Single store TabRow only | HIGH | Phase 2 |
| **No leave balance tracking display** (annual remaining/used) | MEDIUM | Phase 2 |
| **No shift conflict detection** — Overlapping shifts allowed | MEDIUM | Phase 2 |
| **No attendance report export** (CSV/PDF) | MEDIUM | Phase 2 |
| **No bulk payroll generation** ("Generate All" button) | LOW | Phase 2 |
| **No shift swap/request workflow** for employees | LOW | Phase 3 |

---

### G12. Coupons Module UI/UX Gaps (`:composeApp:feature:coupons` — 7 files, 234 LOC VM)

**Basic (70%):** Coupon CRUD with FIXED/PERCENT discounts

| Gap | Severity | Phase |
|-----|----------|-------|
| **No BOGO UI** — Domain has `DiscountType.BOGO` but form only shows FIXED/PERCENT | HIGH | Phase 2 |
| **No category-based promotion targeting** | HIGH | Phase 2 |
| **No store-specific discount assignment** | HIGH | Phase 2 |
| **No coupon code auto-generation** | MEDIUM | Phase 2 |
| **No minimum purchase threshold UI** | MEDIUM | Phase 2 |
| **No coupon analytics/redemption stats** | LOW | Phase 2 |
| **No date picker for validity period** — Epoch ms manual entry | MEDIUM | Phase 2 |

---

### G13. Expenses Module UI/UX Gaps (`:composeApp:feature:expenses` — ~8 files)

**Moderate (65%):** Expense CRUD with status workflow + journal integration

| Gap | Severity | Phase |
|-----|----------|-------|
| **Receipt attachment incomplete** — URL text field only, no file picker/camera | HIGH | Phase 2 |
| **No receipt image preview** | MEDIUM | Phase 2 |
| **No budget tracking per category** | MEDIUM | Phase 2 |
| **No approval amount thresholds** — All expenses same workflow | MEDIUM | Phase 2 |
| **No vendor/supplier field** in expense form | LOW | Phase 2 |
| **No recurring expense support** | LOW | Phase 3 |

---

### G14. Admin Module UI/UX Gaps (`:composeApp:feature:admin` — ~9 files)

**Comprehensive (90%):** System health, backups, audit log

| Gap | Severity | Phase |
|-----|----------|-------|
| **Data integrity check button missing** — State has `integrityReport` but no trigger UI | MEDIUM | Phase 2 |
| **No backup scheduling** — Manual only | MEDIUM | Phase 2 |
| **No audit log CSV/JSON export** | MEDIUM | Phase 2 |
| **No license info display** | LOW | Phase 2 |
| **No crash log/Sentry viewer** | LOW | Phase 3 |

---

### G15. Media Module UI/UX Gaps (`:composeApp:feature:media`)

**Functional (80%):** Media grid with primary image marking

| Gap | Severity | Phase |
|-----|----------|-------|
| **No native file picker** — Manual path entry only | HIGH | Phase 2 |
| **No camera capture** ("Take Photo" option) | HIGH | Phase 2 |
| **No image crop/compress UI** | MEDIUM | Phase 2 |
| **No batch upload** | LOW | Phase 3 |
| **No full-screen image preview** | LOW | Phase 2 |

---

### G16. Multistore Module UI/UX Audit (`:composeApp:feature:multistore` — 11 files, 2,158 LOC)

> **Audited:** 2026-03-18 | **Overall Score:** 9/10 | **MVI Compliance:** 100%
> **ViewModel:** `WarehouseViewModel` (407 lines) — manages warehouses, transfers, racks
> **State:** `WarehouseState` (38 properties) | **Intents:** 20 | **Effects:** 6

**Screens Audited (6):**

| # | Screen | Lines | Status | Key Issues |
|---|--------|-------|--------|------------|
| 1 | `WarehouseListScreen` | 70 | ✅ Complete | FAB, badge count, card list — no error snackbar on list |
| 2 | `WarehouseDetailScreen` | 126 | ✅ Complete | Name/address/isDefault form — no image/logo field |
| 3 | `StockTransferListScreen` | 186 | ✅ Complete | Confirm dialogs for commit/cancel — **shows raw warehouse IDs instead of names** |
| 4 | `NewStockTransferScreen` | 143 | ⚠️ Functional | **No product selector/autocomplete — user must enter product ID manually** |
| 5 | `WarehouseRackListScreen` | 181 | ✅ Complete | Expand/collapse, delete dialog — rack routes not in ZyntaRoute.kt |
| 6 | `WarehouseRackDetailScreen` | 102 | ⚠️ Functional | Name/desc/capacity form — **no back button or navigation scaffold** |

**Compliance Checklist:**

| Aspect | Status |
|--------|--------|
| MVI Pattern | ✅ 100% — all 6 screens dispatch intents, observe state |
| Loading States | ✅ All screens |
| Empty States | ✅ All screens with "No X found" messages |
| Form Validation | ✅ Name required, capacity validation |
| Confirmation Dialogs | ✅ Delete/commit/cancel operations |
| Responsive Design | ✅ Card-based layouts scale to all breakpoints |
| Material 3 | ✅ TopAppBar, FAB, Cards, Chips |
| Accessibility | ✅ contentDescription on icons |

**Critical Gaps (for implementation session):**

| Gap ID | Issue | Severity | Fix Required |
|--------|-------|----------|-------------|
| MS-1 | **No Product Selection UI** — NewStockTransferScreen requires manual product ID entry; should have autocomplete or dropdown backed by `ProductRepository.search()` | HIGH | Add `ExposedDropdownMenuBox` or search-as-you-type field with product results |
| MS-2 | **No Warehouse Name Display** — StockTransferCard shows raw `sourceWarehouseId`/`destWarehouseId` UUIDs; users see meaningless IDs | HIGH | Resolve warehouse names from `WarehouseState.warehouses` list or add `warehouseName` to `StockTransfer` model |
| MS-3 | **Rack Screen Navigation Missing** — `WarehouseRackListScreen` and `WarehouseRackDetailScreen` have no routes in `ZyntaRoute.kt`; parent handles nav locally | MEDIUM | Add `WarehouseRackList(warehouseId)` and `WarehouseRackDetail(warehouseId, rackId?)` to `ZyntaRoute` sealed class |
| MS-4 | **RackDetailScreen No Back Button** — No TopAppBar with back icon; assumes parent composable provides scaffold | MEDIUM | Add `TopAppBar` with navigationIcon back arrow |
| MS-5 | **No Warehouse Metadata** — No image/logo field for visual identity; warehouse cards look plain | LOW | Add optional `imageUrl` field to `Warehouse` domain model + `AsyncImage` in card |
| MS-6 | **No Rack Capacity Enforcement** — UI validates capacity but doesn't prevent overstocking against capacity limits | LOW | Add stock-vs-capacity check in `WarehouseRepositoryImpl.commitTransfer()` |

**Key Files:**
- `composeApp/feature/multistore/src/commonMain/.../WarehouseListScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../WarehouseDetailScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../StockTransferListScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../NewStockTransferScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../WarehouseRackListScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../WarehouseRackDetailScreen.kt`
- `composeApp/feature/multistore/src/commonMain/.../WarehouseViewModel.kt`
- `composeApp/feature/multistore/src/commonMain/.../di/MultistoreModule.kt`

---

### G17. Inventory Module UI/UX Audit (`:composeApp:feature:inventory` — 32 files, 4,200+ LOC)

> **Audited:** 2026-03-18 | **Overall Score:** 8/10 | **MVI Compliance:** 100%
> **ViewModels:** `InventoryViewModel` (737 lines) + `StocktakeViewModel`
> **State:** `InventoryState` (107 properties) | **Intents:** 50+ | **Effects:** 6

**Screens Audited (10 of 18+):**

| # | Screen | Lines | Status | Key Issues |
|---|--------|-------|--------|------------|
| 1 | `ProductListScreen` | 471 | ✅ Excellent | FTS5 search, category/stock filters, list/grid toggle, responsive columns — no result count |
| 2 | `ProductDetailScreen` | 649 | ⚠️ Functional | 5-tab form (ID, Pricing, Stock, Variants, Images) — **barcode scanner TODO**, no image preview, variants not persisted |
| 3 | `CategoryListScreen` | 332 | ✅ Complete | Animated tree view with expand/collapse, loading skeleton — 2-level depth limit |
| 4 | `CategoryDetailScreen` | 275 | ✅ Complete | Parent dropdown (excludes self+children), display order, image URL — no route in ZyntaRoute.kt |
| 5 | `SupplierListScreen` | 275 | ✅ Complete | Responsive table/card view, sortable columns — no purchase history |
| 6 | `StockAdjustmentDialog` | 293 | ✅ Excellent | NumericPad, type selector (increase/decrease/transfer), real-time preview with color-coded risk |
| 7 | `StocktakeScreen` | 623 | ✅ Excellent | Session lifecycle, scanner toggle, variance calc, snackbar feedback — well-designed for tablet |
| 8 | `BulkImportDialog` | — | ⚠️ Not Reviewed | State has fileName, parsedRows, columnMapping, importProgress — dialog composable not audited |
| 9 | `BarcodeGeneratorDialog` | — | ⚠️ Not Reviewed | Referenced in state — dialog composable not audited |
| 10 | `BarcodeLabelPrintScreen` | — | ⚠️ Not Reviewed | Referenced in routes — screen composable not audited |

**Compliance Checklist:**

| Aspect | Status |
|--------|--------|
| MVI Pattern | ✅ 100% — all screens dispatch intents, observe state |
| Loading States | ✅ CircularProgressIndicator on async operations |
| Empty States | ✅ ZyntaEmptyState with CTA buttons |
| Form Validation | ✅ Per-field error tracking via Map<String, String> |
| Responsive Design | ✅ WindowSizeClass-based columns (2/3/5 grid) |
| Material 3 | ✅ TopAppBar, FAB, Cards, Tabs, Chips, ExposedDropdown |
| Accessibility | ✅ contentDescription, semantic labels |
| Audit Logging | ✅ `auditLogger.logProductCreated/Updated/Deleted` |
| Reactive Search | ✅ 300ms debounce + `flatMapLatest` (cancels previous) |

**Critical Gaps (for implementation session):**

| Gap ID | Issue | Severity | Fix Required |
|--------|-------|----------|-------------|
| INV-1 | **Barcode Scanner Not Integrated** — `ProductDetailScreen` has QR icon with TODO comment; `StocktakeScreen` has scanner toggle but handler may be stubbed | HIGH | Wire HAL `BarcodeScanner` interface; implement `actual` for Android (ML Kit) and JVM (HID keyboard) |
| INV-2 | **Variant Persistence Not Implemented** — `ProductVariants` added/edited in form state but never saved to domain; `CreateProductUseCase`/`UpdateProductUseCase` ignore variants | HIGH | Add variant list to `CreateProductUseCase.Params`, persist via `product_variants.sq` table |
| INV-3 | **Missing Screen Route Definitions** — `CategoryDetail`, `SupplierDetail`, `TaxGroupScreen`, `UnitManagementScreen` not in `ZyntaRoute.kt` | HIGH | Add `@Serializable` route classes + NavHost entries |
| INV-4 | **Product Image Preview Missing** — `ProductDetailScreen` accepts image URL but doesn't show preview; just a text field | MEDIUM | Add Coil `AsyncImage` with placeholder/error states |
| INV-5 | **Supplier Purchase History Empty** — `supplierPurchaseHistory` state property exists but never populated | MEDIUM | Load purchase orders from `purchase_orders.sq` in supplier detail |
| INV-6 | **Bulk Import Dialog Unaudited** — Column mapping UX unclear; may have usability issues | MEDIUM | Audit and refine `BulkImportDialog.kt` composable |
| INV-7 | **No Batch Product Selection** — ProductListScreen has no multi-select for bulk operations (delete, price adjust) | MEDIUM | Add checkbox column + batch action toolbar |
| INV-8 | **No Search Result Count** — ProductListScreen doesn't show "X products found" after search | LOW | Add result count chip/text below search bar |
| INV-9 | **No Unsaved Changes Warning** — ProductDetailScreen doesn't warn on back if form has unsaved edits | LOW | Track form dirty state; show confirmation dialog on back press |
| INV-10 | **Tax Group / Unit Management Screens Missing** — Referenced in InventoryIntent but screen files not found | MEDIUM | Implement `TaxGroupScreen.kt` and `UnitManagementScreen.kt` |

**Key Files:**
- `composeApp/feature/inventory/src/commonMain/.../ProductListScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../ProductDetailScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../CategoryListScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../CategoryDetailScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../SupplierListScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../StockAdjustmentDialog.kt`
- `composeApp/feature/inventory/src/commonMain/.../stocktake/StocktakeScreen.kt`
- `composeApp/feature/inventory/src/commonMain/.../stocktake/StocktakeViewModel.kt`
- `composeApp/feature/inventory/src/commonMain/.../InventoryViewModel.kt`
- `composeApp/feature/inventory/src/commonMain/.../di/InventoryModule.kt`

**Reactive Pipeline (canonical pattern — reference for other modules):**
```kotlin
// ProductListScreen search pipeline — InventoryViewModel
combine(_searchQuery.debounce(300L), _selectedCategoryId)
    .distinctUntilChanged()
    .flatMapLatest { (query, categoryId) ->
        productRepository.search(query, categoryId)
    }
    .onEach { products -> updateState { copy(products = products) } }
    .launchIn(viewModelScope)
```

---

### G18. Navigation Route Gaps — Multistore & Inventory

**Missing routes in `ZyntaRoute.kt`** (discovered during G16/G17 audit):

| Missing Route | Referenced By | Fix |
|---------------|---------------|-----|
| `WarehouseRackList(warehouseId: String)` | `WarehouseDetailScreen` → navigate to racks | Add `@Serializable data class` in ZyntaRoute |
| `WarehouseRackDetail(warehouseId: String, rackId: String?)` | `WarehouseRackListScreen` → navigate to rack detail | Add `@Serializable data class` in ZyntaRoute |
| `CategoryDetail(categoryId: String?)` | `CategoryListScreen` → navigate to edit category | Add `@Serializable data class` in ZyntaRoute |
| `SupplierDetail(supplierId: String?)` | `SupplierListScreen` → navigate to edit supplier | Add `@Serializable data class` in ZyntaRoute |
| `TaxGroupList` / `TaxGroupDetail` | `InventoryIntent.OpenTaxGroupDetail` | Add routes + implement screens |
| `UnitManagementList` / `UnitDetail` | `InventoryIntent.OpenUnitManagement` | Add routes + implement screens |

**Existing routes confirmed working:**
- `WarehouseList`, `WarehouseDetail(warehouseId?)`, `StockTransferList`, `NewStockTransfer(sourceWarehouseId?)`
- `ProductList`, `ProductDetail(productId?)`, `CategoryList`, `SupplierList`
- `BarcodeLabelPrint(initialProductId?)`, `Stocktake`, `StocktakeDetail(sessionId)`

**Key File:** `composeApp/navigation/src/commonMain/.../ZyntaRoute.kt`

---

### G19. Navigation & Deep-Linking Gaps (General)

**Routes:** 58 registered across 11 graph groups, RBAC gating 100% compliant

| Gap | Severity | Phase |
|-----|----------|-------|
| **Deep-linking not wired** — `zyntapos://` scheme declared but not in AndroidManifest | MEDIUM | Phase 2 |
| **EditionManagementScreen is placeholder** — Feature toggle panel needed | MEDIUM | Phase 2 |
| **No 3-pane layout** for tablet warehouse management | LOW | Phase 3 |

---

### G20. Cross-Module UI/UX Issues

| Issue | Affected Modules | Severity |
|-------|-----------------|----------|
| **No auto-refresh/WebSocket** — All screens load once | Dashboard, Reports, POS, Accounting | CRITICAL |
| **Date format not from GeneralSettings** — Hardcoded formats | Reports, Accounting, Staff | MEDIUM |
| **No timezone label on timestamps** | All screens with timestamps | MEDIUM |
| **Color-only status indicators** — No icon pairing for color-blind | Stock, E-Invoice, Transfer | MEDIUM |
| **Canvas chart colors may fail in dark mode** | Dashboard, Reports | LOW |
| **No loading skeletons** — Abrupt blank → rendered transition | Dashboard, Reports | LOW |

---

### G21. UI/UX Implementation Priority Matrix

**Phase 1.5 Quick Wins (< 1 day each):**
- [ ] Render hourly sparkline in Dashboard (data already calculated)
- [ ] Add printer test button to PrinterSettingsScreen
- [ ] Persist "Remember Me" checkbox in auth
- [ ] Show UTC offset in timezone dropdown (e.g., "Asia/Colombo (UTC+5:30)")
- [ ] Add employee name/badge to POS screen header

**Phase 2 Must-Have (before multi-store launch):**
- [ ] Create `ZyntaStoreSelector` component + wire to drawer footer
- [ ] Create `ZyntaCurrencyPicker` + `ZyntaTimezonePicker` components
- [ ] Add store selector to login screen
- [ ] Add onboarding steps for currency + timezone
- [ ] Implement loyalty points redemption at POS checkout
- [ ] Implement WebSocket auto-refresh for Dashboard + Reports
- [ ] Populate financial statements with real GL data
- [ ] Add BOGO + category rules to coupon detail form
- [ ] Add native file picker to Media module
- [ ] Add GDPR Export button to customer detail
- [ ] Add date picker dialogs (replace manual text entry)
- [ ] Add transfer status badge to stock transfer list
- [ ] Add store-specific discount assignment to coupons
- [ ] **[MS-1]** Add product selector/autocomplete to NewStockTransferScreen
- [ ] **[MS-2]** Display warehouse names instead of IDs in StockTransferCard
- [ ] **[MS-3]** Add WarehouseRackList/Detail routes to ZyntaRoute.kt
- [ ] **[INV-1]** Wire barcode scanner HAL integration (ProductDetail + Stocktake)
- [ ] **[INV-2]** Implement variant persistence in CreateProduct/UpdateProduct use cases
- [ ] **[INV-3]** Add missing CategoryDetail, SupplierDetail routes to ZyntaRoute.kt
- [ ] **[INV-4]** Add Coil image preview in ProductDetailScreen
- [ ] **[INV-10]** Implement TaxGroupScreen + UnitManagementScreen

**Phase 3 Nice-to-Have:**
- [ ] 3-pane responsive layout for warehouse tablet UI
- [ ] High-contrast accessibility theme
- [ ] i18n/locale infrastructure
- [ ] Receipt template visual editor
- [ ] Conflict resolution UI for CRDT merges
- [ ] Customer segmentation/advanced filtering
- [ ] Shift swap/request workflow

---

## IMPLEMENTATION TIMELINE (Suggested)

| Sprint | Focus | Items | Duration |
|--------|-------|-------|----------|
| Sprint 1 | Sync Engine Completion | A1, C6.1, C6.2 | 2 weeks |
| Sprint 2 | Email + Security | A2, A6, A7 | 1 week |
| Sprint 3 | Remote Diagnostics | A3 | 2 weeks |
| Sprint 4 | Analytics + Docs | A4, A5 | 1 week |
| Sprint 5 | Test Coverage + Tickets | B4, B6 | 2 weeks |
| Sprint 6 | Admin Polish | B1, B2, B3 | 2 weeks |
| Sprint 6.5 | UI/UX Quick Wins | G18 Phase 1.5 items | 1 week |
| Sprint 7 | Design System + Onboarding | G1, G2 (new components + onboarding steps) | 1 week |
| Sprint 8 | Centralized Inventory | C1.1, C1.2, C1.3, C1.4, C1.5 | 3 weeks |
| Sprint 9 | Pricing & Tax | C2.1, C2.2, C2.3, C2.4 | 2 weeks |
| Sprint 10 | Access Control + Auth UI | C3.2, C3.3, C3.4, G4 | 2 weeks |
| Sprint 11 | Sales & Customer + POS UI | C4.1, C4.2, C4.3, G3, G10 | 2 weeks |
| Sprint 12 | Reporting + Dashboard UI | C5.1, C5.2, C5.4, G6, G7 | 2 weeks |
| Sprint 13 | Accounting + Coupons UI | G9, G12, G13 | 2 weeks |
| Sprint 14 | Timezone + Sync + Media | C6.3, C5.3, G15 | 1 week |
| Phase 3 | Enterprise Features | D1, D2, D3, C4.4, G18 Phase 3 | 6+ weeks |

---

## 🔴 ITEM DEPENDENCY GRAPH (MUST FOLLOW ORDER)

> Items have dependencies — implementing them out of order produces broken code.
> **ALWAYS check this graph before picking the next item.**

### Critical Path (must follow this order)

```
A1 (Sync Engine 60%) ──────────────────────────────────────────┐
  │                                                             │
  ├──→ C6.1 (Multi-Node Sync) — BLOCKED until A1 is ≥90%      │
  ├──→ C6.2 (Offline-First) — BLOCKED until A1 entity types   │
  └──→ C1.1 (Global Product Catalog) — needs sync for replication
         │
         ├──→ C1.2 (Store-Specific Inventory) — needs global catalog base
         │      │
         │      ├──→ C1.3 store-level IST — needs per-store inventory
         │      └──→ C1.5 (Replenishment) — needs per-store stock levels
         │
         └──→ C2.1 (Region-Based Pricing) — needs global product → price override
                │
                └──→ C2.4 (Store-Specific Discounts) — needs pricing rules infra
```

### Dependency Table (all items)

| Item | Depends On | Can Start When |
|------|-----------|----------------|
| **A1** Sync Engine | _(none — start here)_ | Immediately |
| **A2** Email System | _(none)_ | Immediately |
| **A3** Remote Diagnostics | _(none)_ | Immediately |
| **A4** API Docs | _(none)_ | Immediately |
| **A5** Analytics/Sentry | _(none)_ | Immediately |
| **A6** Security Monitoring | _(none)_ | Immediately |
| **A7** Admin JWT Gap | _(none)_ | Immediately |
| **B1** Admin Panel Polish | _(none)_ | Immediately |
| **B2** Admin Custom Auth | A7 (JWT fix) | After A7 |
| **B3** Uptime Kuma | _(none)_ | Immediately |
| **B4** Backend Test Coverage | _(none — but helps validate A1)_ | Immediately (parallel with A1) |
| **B5** Timestamp Formats | _(none)_ | Immediately |
| **C1.1** Global Product Catalog | **A1** (sync engine for replication) | After A1 ≥80% |
| **C1.2** Store-Specific Inventory | **C1.1** (global catalog) | After C1.1 |
| **C1.3** Store-Level IST | **C1.2** (per-store inventory) | After C1.2 |
| **C1.4** Stock In-Transit | **C1.3** (IST workflow) | After C1.3 |
| **C1.5** Replenishment | **C1.2** (per-store stock levels) | After C1.2 |
| **C2.1** Region-Based Pricing | **C1.1** (global product base) | After C1.1 |
| **C2.2** Multi-Currency | _(none — formatter exists)_ | Immediately |
| **C2.3** Localized Tax | **C1.1** (store context) | After C1.1 |
| **C2.4** Store-Specific Discounts | **C2.1** (pricing rules infra) | After C2.1 |
| **C3.1** RBAC | _(COMPLETE — already done)_ | N/A |
| **C3.2** Store-Level Permissions | **C1.1** (store concept) | After C1.1 |
| **C3.3** Global Admin Dashboard | **C3.2** (store-level data) | After C3.2 |
| **C3.4** Employee Roaming | **C3.2** (store-level perms) | After C3.2 |
| **C4.1** Cross-Store Returns | **C1.2** (per-store inventory) | After C1.2 |
| **C4.2** Universal Loyalty | _(none — partial exists)_ | Immediately |
| **C4.3** Centralized Customers | **C1.1** (store context) | After C1.1 |
| **C4.4** Click & Collect | **C4.1** + **C1.3** | After C4.1 + C1.3 |
| **C5.1** Consolidated Reports | **C1.2** (multi-store data) | After C1.2 |
| **C5.2** Store Comparison | **C5.1** (consolidated data) | After C5.1 |
| **C5.3** Store Audit Logs | _(COMPLETE)_ | N/A |
| **C5.4** Real-time Dashboard | **A1** (WebSocket push) | After A1 |
| **C6.1** Multi-Node Sync | **A1** (sync engine ≥90%) | After A1 ≥90% |
| **C6.2** Offline-First | **A1** (entity types in EntityApplier) | After A1 entity types |
| **C6.3** Timezone Mgmt | _(MOSTLY COMPLETE)_ | Immediately |
| **G1-G21** UI/UX Gaps | _(mostly independent)_ | Immediately (unless noted) |
| **G16** MS-1 to MS-6 | _(none — UI fixes)_ | Immediately |
| **G17** INV-1 (Scanner) | _(HAL interface exists)_ | Immediately |
| **G17** INV-2 (Variants) | _(domain model exists)_ | Immediately |
| **G17** INV-3 (Routes) | _(navigation module exists)_ | Immediately |
| **D1** E-Invoice | _(Phase 3)_ | After Phase 2 complete |
| **D2** Staff/Payroll | _(Phase 3)_ | After Phase 2 complete |
| **D3** Expense/Accounting | _(Phase 3)_ | After Phase 2 complete |

### Parallel Work Streams (safe to run in separate sessions simultaneously)

```
Stream 1: A1 → C6.1 → C6.2           (Sync engine — critical path)
Stream 2: A2, A3, A4, A5, A6, A7     (Infra/security — independent)
Stream 3: B1, B2, B3, B4, B5         (Admin/quality — independent)
Stream 4: G1-G21, INV-*, MS-*        (UI/UX fixes — independent)
Stream 5: C2.2, C4.2, C6.3           (No dependencies — can start now)
```

> **RULE:** Before starting any C-section item, check its "Depends On" column above.
> If the dependency isn't complete yet, pick a different item from Section A, B, or G instead.

---

## 🔴 SESSION SCOPE GUIDANCE

> Not all items fit in a single Claude Code session. Use this guide to
> estimate scope and handle partial completions.

### Item Size Estimates

| Size | Approx Scope | Examples |
|------|-------------|----------|
| **S (Small)** | 1 session, < 5 files changed | G-series UI fixes (INV-8 search count, MS-4 back button), B5 timestamp format |
| **M (Medium)** | 1 session, 5-15 files changed | A2 email UI, A7 JWT fix, INV-3 missing routes, MS-1 product selector |
| **L (Large)** | 2-3 sessions, 15-30 files changed | A3 remote diagnostics, B4 test coverage, C2.2 multi-currency |
| **XL (Extra Large)** | 3-5+ sessions, 30+ files changed | A1 sync engine, C1.1 global product catalog, C1.3 store-level IST |

### Per-Item Size Tags

| Item | Size | Est. Sessions |
|------|------|---------------|
| A1 Sync Engine | **XL** | 4-5 sessions |
| A2 Email System | **S** | 1 session |
| A3 Remote Diagnostics | **L** | 2-3 sessions |
| A4 API Docs | **M** | 1 session |
| A5 Analytics | **M** | 1 session |
| A6 Security Monitoring | **S** | 1 session |
| A7 Admin JWT | **M** | 1 session |
| B1-B3 Admin/Monitoring | **S** each | 1 session each |
| B4 Test Coverage | **XL** | 3-4 sessions |
| C1.1 Global Catalog | **XL** | 3-4 sessions |
| C1.2 Store Inventory | **L** | 2 sessions |
| C1.3 IST Store-Level | **L** | 2 sessions |
| C2.1-C2.4 Pricing | **M-L** each | 1-2 sessions each |
| C3.2-C3.4 Permissions | **M** each | 1 session each |
| G-series UI fixes | **S-M** each | 1 session each |
| INV-1 to INV-10 | **S-M** each | 1 session each |
| MS-1 to MS-6 | **S** each | 1 session each |

### Handling Partial Progress (for L/XL items)

When a session cannot finish an item, it MUST:

1. **Mark partial progress in the item's checkbox list:**
   ```
   - [x] EntityApplier — handle ORDER type (done in session 2026-03-18)
   - [x] EntityApplier — handle CUSTOMER type (done in session 2026-03-18)
   - [ ] EntityApplier — handle CATEGORY type
   - [ ] EntityApplier — handle SUPPLIER type
   ```

2. **Update the item's status line:**
   ```
   ### A1. Sync Engine Server-Side — ~60% Complete  →  ~75% Complete
   ```

3. **Add a handoff note at the TOP of the item section:**
   ```
   > **HANDOFF (2026-03-18):** EntityApplier extended with ORDER and CUSTOMER types.
   > Next session should continue with CATEGORY, SUPPLIER, and remaining types.
   > Tests added in `EntityApplierTest.kt` — run before modifying.
   > PR #NNN merged. Branch clean.
   ```

4. **Commit and push all progress** — even partial work must be on `main` via PR.

### Session Planning Rule

```
At session start, after reading this file:
1. Check your assigned item's SIZE estimate above
2. If XL: plan to implement 2-3 sub-items only (not the whole thing)
3. If L: plan to implement the core + tests, defer edge cases
4. If S/M: plan to complete fully in this session
5. ALWAYS leave 15 min at session end for docs + push + pipeline monitoring
```

---

## 🔴 ERROR RECOVERY GUIDE

> Common failures encountered during implementation and how to fix them.
> Check this section BEFORE asking for help or pushing more commits.

### Build Failures

| Error | Cause | Fix |
|-------|-------|-----|
| `NoSuchMethodError` on datetime screens | `kotlinx-datetime` downgraded below 0.7.1 | Check `gradle/libs.versions.toml` — must be `0.7.1`. Root `build.gradle.kts` has `resolutionStrategy` pin. |
| `Unresolved reference: BaseViewModel` | Wrong import or module dependency missing | Import from `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel`. Ensure module depends on `:composeApp:core`. |
| `Duplicate class` / `already defined` | New file duplicates existing class | Search codebase for the class name — delete your duplicate, use existing one. |
| `Cannot access 'xxx': it is internal` | Accessing internal API from wrong module | Check module boundaries. `:shared:domain` is public API — use it. |
| `Koin: No definition found for class` | Missing Koin binding or wrong module load order | Add binding in `<feature>/di/<Feature>Module.kt`. Check Koin load order in CLAUDE.md. |
| `SQLDelight: No such table` | New `.sq` file but `generateSqlDelightInterface` not run | Run `./gradlew generateSqlDelightInterface` after any `.sq` changes. |
| `Compose: @Composable invocations can only happen...` | Calling composable from non-composable scope | Move call inside `@Composable` function or use `LaunchedEffect`/`rememberCoroutineScope`. |

### Test Failures

| Error | Cause | Fix |
|-------|-------|-----|
| `Mockative: mock<X>()` compile error | KSP version mismatch with Kotlin | KSP must be `2.3.4` for Kotlin `2.3.0`. Check `libs.versions.toml`. |
| Turbine `No value produced` | Flow never emitted or test didn't wait | Use `awaitItem()` with timeout. Check that ViewModel actually calls `updateState`. |
| `Expected <X> but was <Y>` on state | Wrong initial state or missing state update | Check `BaseViewModel` initialState. Verify `handleIntent` triggers the right state copy. |

### Pipeline Failures

| Step | Common Failure | Fix |
|------|---------------|-----|
| Step[1] Branch Validate | Build error (compile, resource) | Read error log: `curl -s -H "Authorization: token $PAT" "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/runs/<run-id>/jobs" \| python3 -c "import sys,json; [print(j['name'],j['conclusion']) for j in json.load(sys.stdin)['jobs']]"` |
| Step[2] Auto PR | PR already exists (ok) or PAT issue | Check existing PR. If no PR created, check PAT_TOKEN secret. |
| Step[3+4] CI Gate | Detekt violation | Run `./gradlew detekt` locally, fix violations. Common: unused import, magic number, long method. |
| Step[3+4] CI Gate | Android Lint error | Run `./gradlew lint` locally. Common: missing `contentDescription`, hardcoded string. |
| Step[3+4] CI Gate | Test failure | Run `./gradlew allTests` locally. Fix failing test before pushing. |
| PR `mergeable=false` | Conflict with main | `git fetch origin main && git merge origin/main --no-edit`, resolve conflicts, push. |

### Recovery Workflow

```bash
# 1. Pipeline failed — get the failure details
RUN_ID=<from monitoring output>
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/runs/$RUN_ID/jobs" \
  | python3 -c "
import sys,json
for j in json.load(sys.stdin)['jobs']:
  print(f'[{j[\"conclusion\"]:10}] {j[\"name\"]}')
  for s in j.get('steps',[]):
    if s.get('conclusion') == 'failure':
      print(f'  FAILED: {s[\"name\"]}')
"

# 2. Read the full failure log
curl -s -H "Authorization: token $PAT" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/runs/$RUN_ID/jobs" \
  | python3 -c "
import sys,json
for j in json.load(sys.stdin)['jobs']:
  if j['conclusion'] == 'failure':
    print(f'Failed job: {j[\"name\"]} — URL: {j[\"html_url\"]}')
"

# 3. Fix locally, test locally, THEN push
./gradlew clean test lint detekt --parallel --continue --stacktrace

# 4. Sync before committing fix
git fetch origin main && git merge origin/main --no-edit

# 5. Commit fix referencing the failure
git commit -m "fix(module): resolve CI failure — [describe fix]"

# 6. Push and re-monitor from Step[1]
git push -u origin $(git branch --show-current)
```

### Known Pitfalls (from CLAUDE.md — quick reference)

1. `kotlinx-datetime` MUST be `0.7.1` — never downgrade
2. `loadKoinModules(global=true)` is banned — use `koin.loadModules()`
3. `*Entity` suffix banned in `:shared:domain` — use plain names (ADR-002)
4. `:shared:domain` can ONLY depend on `:shared:core`
5. `gradle` bare command is wrong — always `./gradlew`
6. `git rebase` is banned for conflict resolution — use `git merge`
7. Never manually create PRs — Step[2] auto-creates them
8. Never skip pre-commit sync — main moves fast with multiple sessions

---

## FEATURE COVERAGE MATRIX

| ඔබේ Feature | Plan Item | Status |
|-------------|-----------|--------|
| **1. Centralized Inventory** | | |
| Global Product Catalog | C1.1 | NOT IMPLEMENTED |
| Store-Specific Inventory | C1.2 | PARTIAL (global stock_qty, warehouse+rack infra complete) |
| Inter-Store Stock Transfer | C1.3 | WAREHOUSE-LEVEL COMPLETE (two-phase commit + UI), store-level missing |
| Stock In-Transit Tracking | C1.4 | NOT IMPLEMENTED (no intermediate transit state) |
| Warehouse Replenishment | C1.5 | REPORTS EXIST (reorder alerts), no auto-PO logic |
| **2. Pricing & Taxation** | | |
| Region-Based Pricing | C2.1 | NOT IMPLEMENTED |
| Multi-Currency | C2.2 | PARTIAL (formatter only) |
| Localized Tax | C2.3 | PARTIAL (single-region) |
| Store-Specific Discounts | C2.4 | PARTIAL (no store scoping) |
| **3. Access Control** | | |
| RBAC | C3.1 | COMPLETE |
| Store-Level Permissions | C3.2 | NOT IMPLEMENTED |
| Global Admin Dashboard | C3.3 | PARTIAL |
| Employee Roaming | C3.4 | NOT IMPLEMENTED |
| **4. Sales & Customer** | | |
| Cross-Store Returns | C4.1 | NOT IMPLEMENTED |
| Universal Loyalty | C4.2 | PARTIAL (no redemption) |
| Centralized Customers | C4.3 | AMBIGUOUS |
| Click & Collect (BOPIS) | C4.4 | NOT IMPLEMENTED |
| **5. Reporting & Analytics** | | |
| Consolidated Financial | C5.1 | PARTIAL (models, no impl) |
| Store Comparison | C5.2 | PARTIAL (backend only) |
| Store Audit Logs | C5.3 | COMPLETE |
| Real-time Dashboard | C5.4 | PARTIAL (REST only) |
| **6. Sync & Offline** | | |
| Multi-node Sync | C6.1 | PARTIAL (LWW + 17 entity types in EntityApplier) |
| Offline-First | C6.2 | PARTIAL (EntityApplier covers all core POS types) |
| Timezone Management | C6.3 | MOSTLY COMPLETE |

---

## HOW TO USE THIS DOCUMENT

1. Pick items from the highest priority section first (A → B → C → D)
2. Each item has checkboxes `[ ]` — mark as `[x]` when complete
3. Update the `Last Updated` date at the top after changes
4. Reference specific items in commit messages (e.g., `feat(inventory): implement IST workflow [C1.3]`)
5. Move completed sections to an `## COMPLETED` section at the bottom

---

## 🔴 MANDATORY: PRE-IMPLEMENTATION READING ORDER

> **BEFORE writing ANY code**, the implementation session MUST read and internalize
> these documents in this exact order. Skipping this step will produce code that
> violates architectural constraints, naming conventions, or security patterns.

### Step 1: Architecture Foundation (READ FIRST — ~10 min)

```
docs/adr/ADR-001-*.md   → ViewModel base class policy (BaseViewModel<S,I,E> mandatory)
docs/adr/ADR-002-*.md   → Domain model naming (no *Entity suffix in :shared:domain)
docs/adr/ADR-003-*.md   → SecurePreferences consolidation (canonical in :shared:security only)
docs/adr/ADR-004-*.md   → Keystore token scaffold removal (use TokenStorage interface)
docs/adr/ADR-005-*.md   → Single admin account management
docs/adr/ADR-006-*.md   → Backend Docker build in CI
docs/adr/ADR-007-*.md   → Database-per-service (zyntapos_api, zyntapos_license)
docs/adr/ADR-008-*.md   → RS256 key distribution (TOFU + well-known)
```

### Step 2: Architecture Diagrams & Module Dependencies (~5 min)

```
docs/architecture/       → Module dependency graphs, layer diagrams
CLAUDE.md                → Full codebase context (module map, tech stack, Koin order, security)
CONTRIBUTING.md          → Code review rules, architectural conventions
```

### Step 3: Existing Audit Reports (~5 min)

```
docs/audit/              → Phase 1-4 audit reports, backend modules audit
```

### Step 4: This Plan File

Only after completing Steps 1-3, begin implementing items from this plan.

---

## 🔴 MANDATORY: IMPLEMENTATION COMPLIANCE RULES

> These rules apply to EVERY line of code written during implementation.
> Violations will be caught by CI (Detekt + Android Lint) or break the pipeline.

### Architecture Compliance

1. **Clean Architecture (strict layering):**
   - `Presentation → Domain → Data/Infrastructure` — NEVER reverse the dependency
   - `:shared:domain` ONLY depends on `:shared:core` — no imports from `:shared:data`, `:shared:hal`, `:shared:security`
   - Feature modules depend on `:composeApp:navigation`, `:composeApp:designsystem`, `:composeApp:core` — NEVER on each other
   - Repository interfaces live in `:shared:domain` — implementations in `:shared:data`

2. **MVI Pattern (mandatory for ALL screens):**
   - Every screen has: `State` (immutable data class), `Intent` (sealed class of user actions), `Effect` (one-shot side effects)
   - Every ViewModel MUST extend `BaseViewModel<State, Intent, Effect>` from `:composeApp:core` (ADR-001)
   - State mutations ONLY via `updateState { }` — never modify state directly
   - Side effects (navigation, toasts) ONLY via `sendEffect()` — never from composable
   - Single entry point: `override suspend fun handleIntent(intent: I)` — all user actions flow through here

3. **DRY Principle — Codebase-First Development (CRITICAL):**

   > **RULE: NEVER write new code without first searching the codebase for existing implementations.**
   > This is the single most important rule. The codebase has 26 modules, 31 domain models,
   > 36 SQLDelight schemas, 27+ design system components, and dozens of use cases/repositories.
   > Duplicating ANY of these is a bug.

   **MANDATORY search-before-coding process (for EVERY new function/class/component):**

   ```
   Step 1: SEARCH — Before writing ANY new code, search the codebase:
     • Grep for similar function names, class names, or keywords
     • Glob for files in related modules that might already solve the problem
     • Read existing ViewModel/UseCase/Repository in the same feature module
     • Check `:shared:core` utilities (CurrencyUtils, DateTimeUtils, ValidationUtils, etc.)
     • Check `:composeApp:designsystem` for existing UI components (27+ Zynta* components)

   Step 2: EVALUATE — If something similar exists:
     • Can you reuse it directly? → USE IT
     • Can you extend it with a parameter? → EXTEND IT
     • Is it 80% what you need? → MODIFY IT (don't create a parallel version)
     • Only if NOTHING exists → CREATE NEW (and document why in the commit message)

   Step 3: VERIFY — After implementation, search again:
     • Confirm you didn't create a duplicate of something that already exists
     • Confirm your new code follows the same patterns as existing code in that module
   ```

   **Specific search locations by layer:**

   | Before creating... | Search these locations FIRST |
   |--------------------|------------------------------|
   | A new UI component | `composeApp/designsystem/src/commonMain/` — 27+ `Zynta*` components |
   | A new ViewModel | Same feature's existing ViewModel — can you add an Intent instead? |
   | A new UseCase | `shared/domain/src/commonMain/.../usecase/` — 20+ existing use cases |
   | A new Repository method | `shared/domain/src/commonMain/.../repository/` — existing interfaces |
   | A new domain model | `shared/domain/src/commonMain/.../model/` — 31 existing models |
   | A new SQLDelight query | `shared/data/src/commonMain/sqldelight/` — 36 existing `.sq` files |
   | A utility function | `shared/core/src/commonMain/` — CurrencyUtils, DateTimeUtils, ValidationUtils |
   | A new Koin module | `<feature>/di/` — existing module may just need new bindings |
   | A new navigation route | `composeApp/navigation/.../ZyntaRoute.kt` — 58 existing routes |
   | An error handling pattern | Check how existing VMs handle errors (sendEffect → ShowError) |
   | A reactive pipeline | Check `InventoryViewModel` search pipeline as canonical reference |

   **Examples of WRONG vs RIGHT:**

   ```kotlin
   // ❌ WRONG — Created new currency formatter without checking :shared:core
   fun formatPrice(amount: Double): String = "$${String.format("%.2f", amount)}"

   // ✅ RIGHT — Reused existing CurrencyUtils from :shared:core
   import com.zyntasolutions.zyntapos.core.util.CurrencyUtils
   val formatted = CurrencyUtils.format(amount, currencyCode)

   // ❌ WRONG — Created new empty state composable
   @Composable fun EmptyProductList() { Column { Text("No products") } }

   // ✅ RIGHT — Reused existing ZyntaEmptyState from designsystem
   ZyntaEmptyState(
       icon = Icons.Default.Inventory,
       title = "No products found",
       actionLabel = "Add Product",
       onAction = { /* navigate */ }
   )

   // ❌ WRONG — Created separate validation in ViewModel
   private fun validateName(name: String): Boolean = name.isNotBlank()

   // ✅ RIGHT — Used existing ValidationUtils
   import com.zyntasolutions.zyntapos.core.util.ValidationUtils
   ValidationUtils.validateRequired(name, "Product name")
   ```

   **Codebase exploration commands (run before implementing any feature):**

   ```bash
   # Find all existing components in design system
   grep -r "^fun Zynta" composeApp/designsystem/src/commonMain/ --include="*.kt" -l

   # Find all existing use cases
   find shared/domain/src/commonMain -name "*UseCase.kt" | sort

   # Find all existing repository interfaces
   find shared/domain/src/commonMain -name "*Repository.kt" | sort

   # Find all existing domain models
   find shared/domain/src/commonMain -name "*.kt" -path "*/model/*" | sort

   # Find all SQLDelight schemas
   find shared/data/src/commonMain/sqldelight -name "*.sq" | sort

   # Search for existing similar functionality
   grep -r "yourKeyword" shared/ composeApp/ --include="*.kt" -l
   ```

### Naming Conventions (enforced by code review)

| What | Convention | Example |
|------|-----------|---------|
| Domain models | Plain names, NO `*Entity` suffix (ADR-002) | `Product`, `Order`, `Customer` |
| ViewModels | `<Feature>ViewModel` extending `BaseViewModel` | `InventoryViewModel` |
| Use cases | `<Verb><Noun>UseCase` | `GetProductsUseCase`, `CalculateOrderTotalsUseCase` |
| Repository interfaces | `<Noun>Repository` | `ProductRepository` |
| Repository implementations | `<Noun>RepositoryImpl` | `ProductRepositoryImpl` |
| Koin DI modules | `<feature>Module` in `<feature>/di/` package | `inventoryModule` in `inventory/di/InventoryModule.kt` |
| Design system components | `Zynta*` prefix | `ZyntaButton`, `ZyntaCard`, `ZyntaSearchBar` |
| Routes | Sealed class members in `ZyntaRoute` | `data class ProductDetail(val productId: String?)` |
| Commits | Conventional Commits with module scope | `feat(pos): add hold order use case` |

### Security Requirements

1. **Database:** All SQLite access through SQLDelight — never raw SQL strings with user input
2. **Encryption:** DB passphrase from Keystore/JCE only — never hardcoded or in plaintext
3. **PIN:** SHA-256 + 16-byte salt via `PinManager` — constant-time compare
4. **JWT:** Use `JwtManager` — never decode tokens manually
5. **RBAC:** Route gating via `RbacEngine` — never bypass permission checks
6. **Secrets:** Via `local.properties` → Gradle Secrets Plugin → `BuildConfig` — never commit secrets
7. **Input validation:** At system boundaries (user input, API responses) — use `ValidationUtils`
8. **No OWASP Top 10:** No command injection, XSS, SQL injection in any code path

### Performance Requirements

1. **Search:** Use `debounce(300L)` on search queries — never fire on every keystroke
2. **Flow operators:** Use `flatMapLatest` for search (cancels previous) — never `flatMapMerge`
3. **State updates:** Atomic via `updateState { }` — never multiple sequential state emissions
4. **Image loading:** Use Coil with `AsyncImage` — never load bitmaps on main thread
5. **Database:** Use SQLDelight generated queries — never `rawQuery` with string interpolation
6. **Coroutines:** IO operations on `IO_DISPATCHER` — never block main thread
7. **Lists:** Use `LazyColumn`/`LazyVerticalGrid` — never `Column` with `forEach` for large lists

### Kotlin/KMP Specific

1. **100% Kotlin — NO Java files**
2. **Version catalog:** All deps via `gradle/libs.versions.toml` — never hardcode versions
3. **kotlinx-datetime:** Pinned at **0.7.1** — never downgrade (breaks CMP 1.10.0)
4. **Koin DI:** Use `koin.loadModules()` — never `loadKoinModules(global=true)` or `GlobalContext.get()`
5. **Platform code:** Use `expect/actual` in `:shared:hal` — never import platform APIs directly in shared code
6. **Gradle wrapper:** Always `./gradlew` — never bare `gradle`

### Testing Requirements

| Layer | Coverage Target | Framework |
|-------|----------------|-----------|
| Use Cases | 95% | Kotlin Test + Mockative 3 |
| Repositories | 80% | Kotlin Test + Mockative 3 |
| ViewModels | 80% | Kotlin Test + Turbine |
| New features | Tests MUST accompany implementation | All of above |

- Write tests in `src/commonTest/` (shared) or `src/jvmTest/` (integration)
- Mock via `mock<Interface>` (Mockative 3) — no hand-written fakes unless testing boundary contracts
- Flow assertions via Turbine — `awaitItem()`, `awaitError()`, `ensureAllEventsConsumed()`
- Run `./gradlew :shared:core:test :shared:domain:test :shared:security:test --parallel` before committing

### CI Pipeline Compliance

After EVERY commit+push, the 7-step pipeline must pass:
1. Step[1] Branch Validate — build shared + Android APK + Desktop JAR
2. Step[2] Auto PR — creates PR targeting main
3. Step[3+4] CI Gate — full build + Lint + Detekt + allTests
4. Steps 5-7 — Deploy + Smoke + Verify (after merge to main)

**Do NOT proceed to next item until pipeline is green.**

---

## 🔴 CRITICAL: MULTI-SESSION AWARENESS

> **මෙම repository එකේ GitHub Copilot Workspace / Claude Code sessions කිහිපයක්
> එකවර ක්‍රියාත්මක වේ.** එක් එක් session එක වෙනම branch එකක වැඩ කරයි,
> නමුත් සියල්ල `main` branch එකට merge වේ. මෙය ඇති කරන ප්‍රශ්න:

### Why This Matters

1. **Main moves fast:** අනෙක් sessions PR merge කරද්දී `main` එක update වෙනවා.
   ඔබේ branch එක sync නැතිනම්, PR එකේ conflict ඇති වෙනවා.

2. **Same files, different sessions:** කිහිප session එකකින් එකම file edit කරනවනම්
   (e.g., `ZyntaRoute.kt`, `build.gradle.kts`, `.sq` files), merge conflicts inevitable.

3. **Pipeline blocking:** Dirty PR එකක් auto-merge pipeline එක block කරනවා.
   එක session එකක stuck PR එකින් අනෙක් sessions වල PRs ද delay වෙනවා.

### Mandatory Multi-Session Safety Rules

1. **ALWAYS sync before EVERY commit** — not just once at session start:
   ```bash
   git fetch origin main
   git log HEAD..origin/main --oneline   # check if main moved
   git merge origin/main --no-edit       # merge if needed, resolve conflicts
   # THEN commit your changes
   ```

2. **ALWAYS check PR status after push** — confirm no conflicts:
   ```bash
   REPO="sendtodilanka/ZyntaPOS-KMM"
   BRANCH=$(git branch --show-current)
   curl -s -H "Authorization: token $PAT" \
     "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
     | python3 -c "
   import sys,json
   prs=json.load(sys.stdin)
   if not prs: print('No open PR yet — wait for Step[2]')
   for pr in prs:
     print(f'PR #{pr[\"number\"]}: mergeable={pr.get(\"mergeable\")} state={pr.get(\"mergeable_state\")}')
   "
   ```

3. **If PR shows `mergeable=false` or `mergeable_state=dirty`** — FIX IMMEDIATELY:
   ```bash
   git fetch origin main
   git merge origin/main --no-edit
   # resolve any conflicts manually
   git add <resolved-files>
   git commit -m "merge: resolve conflicts with main"
   git push -u origin $(git branch --show-current)
   # re-monitor pipeline from Step[1]
   ```

4. **NEVER force-push** — other sessions may have triggered pipeline runs referencing your commits.

5. **NEVER manually create PRs** — Step[2] auto-creates them. Manual PRs break the dispatch chain.

6. **If pipeline fails** — read the failure log FIRST, don't just push more commits hoping it fixes itself.

---

## IMPLEMENTATION SESSION CHECKLIST

> Copy-paste this checklist at the start of every implementation session.
> Steps marked ♻️ are repeated for EVERY item in the session.

```
═══ SESSION START (once) ═══════════════════════════════════════
□ 1.  Read CLAUDE.md (full codebase context)
□ 2.  Read all ADRs in docs/adr/ (architecture decisions)
□ 3.  Read docs/architecture/ (diagrams, module deps)
□ 4.  Read this plan file (features + gaps + priorities)
□ 5.  Run `echo $PAT` to confirm GitHub token available
□ 6.  Sync: `git fetch origin main && git merge origin/main --no-edit`

═══ PER-ITEM LOOP (repeat for each feature/fix) ♻️ ════════════
□ 7.  Pick highest priority unchecked item from Section A → B → C → D → G
□ 8.  Search codebase FIRST (DRY rule — SEARCH → EVALUATE → VERIFY)
□ 9.  Implement following all compliance rules above
□ 10. Write tests (use case 95%, repo 80%, VM 80%)
□ 11. Run `./gradlew :shared:core:test :shared:domain:test --parallel` locally

── POST-IMPLEMENTATION: UPDATE ALL DOCS (MANDATORY) ──────────
□ 12. Update THIS plan file to reflect actual state:
      a. Mark checkbox `[x]` for the completed item
      b. Change "What's MISSING" entry to "What's DONE" or delete it
      c. Update status line (e.g., "~60% Complete" → "~80% Complete")
      d. Update FEATURE COVERAGE MATRIX at bottom of this file
         (e.g., "NOT IMPLEMENTED" → "COMPLETE" or "PARTIAL (xyz done)")
      e. If ALL sub-items of a section are done, move entire section
         to `## COMPLETED` section at bottom
□ 13. Update CLAUDE.md if implementation changed any of these:
      - Module count (new module added) → update "Module Map (26 Modules)"
      - Domain model count (new model) → update "Domain models (31 files)"
      - SQLDelight schema count → update "36 .sq schema files"
      - New Koin module → update "Koin Module Loading Order"
      - New navigation route → update "Navigation Routes" section
      - New design system component → update Zynta* component count
      - New ADR created → update "Architecture Decision Records" table
      - New GitHub Secret → update secrets table
      - Technology version changed → update "Technology Stack" table
□ 14. Update docs/adr/ if implementation:
      - Introduced a new architectural pattern → create new ADR
      - Changed an existing ADR decision → update status to SUPERSEDED
      - Contradicted an ADR → STOP and discuss before proceeding
□ 15. Update docs/architecture/ if implementation:
      - Added new module → update module dependency diagram
      - Changed dependency direction → update layer diagram
      - Added new service → update service topology diagram

── PRE-COMMIT SYNC + COMMIT + PUSH ───────────────────────────
□ 16. PRE-COMMIT SYNC (MANDATORY — main may have moved since last commit):
      `git fetch origin main && git merge origin/main --no-edit`
□ 17. Stage ALL changed files (code + docs + plan file):
      `git add <implementation-files> <updated-docs> todo/missing-features-implementation-plan.md`
□ 18. Commit with conventional commit format referencing plan item ID
      Include "docs updated" in commit body if docs were changed
□ 19. Push: `git push -u origin $(git branch --show-current)`

── POST-PUSH: MONITOR PIPELINE + PR ──────────────────────────
□ 20. Monitor pipeline Step[1] → Step[2] until green
□ 21. Check PR for conflicts:
      - mergeable=true → proceed
      - mergeable=false → sync main, resolve conflicts, push, re-monitor
□ 22. Monitor pipeline Step[3+4] CI Gate until green
□ 23. ♻️ Go to step 7 for next item

═══ SESSION END (once) ═════════════════════════════════════════
□ 24. Final sync: `git fetch origin main && git merge origin/main --no-edit`
□ 25. Final push: `git push -u origin $(git branch --show-current)`
□ 26. Verify PR is green and auto-merge is enabled
□ 27. Update `Last Updated` date at top of this file
```

---

## 🔴 WARNING: STALE DATA CAUSES RE-IMPLEMENTATION

> **If you implement a feature but do NOT update this file + CLAUDE.md + docs/**,
> the next Claude Code session will read outdated information and **re-implement
> the same feature from scratch**, wasting an entire session.
>
> **Real example of what goes wrong:**
> - Session A implements `CategoryDetail` screen and route (INV-3)
> - Session A forgets to update FEATURE COVERAGE MATRIX
> - Matrix still shows `CategoryDetail` as "NOT IMPLEMENTED"
> - Session B reads this file, sees "NOT IMPLEMENTED", builds it AGAIN
> - PR conflict → pipeline blocked → manual resolution needed
>
> **Prevention:** Steps 12-15 in the checklist above are NOT optional.
> Every implementation MUST be accompanied by doc updates IN THE SAME COMMIT.

---

## COMPLETED

> Move fully completed sections here. Include completion date and PR number.
> Format: `### [DONE 2026-MM-DD PR #NNN] <original section header>`

_(No completed items yet)_

---

*End of document*
