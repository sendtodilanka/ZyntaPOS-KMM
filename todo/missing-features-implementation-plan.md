# ZyntaPOS-KMM — Missing & Partially Implemented Features Implementation Plan

**Created:** 2026-03-18
**Last Updated:** 2026-03-18
**Status:** Draft — Awaiting Approval

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

## SECTION A: CRITICAL / HIGH PRIORITY (P0–P1)

---

### A1. Sync Engine Server-Side (TODO-007g) — ~60% Complete

**Priority:** P0-CRITICAL
**Impact:** Offline-first sync pipeline non-functional; client data sits in `sync_queue` unprocessed
**Modules:** `:shared:data`, `backend/api`, `backend/sync`

**What EXISTS:**
- `sync_operations`, `sync_cursors`, `entity_snapshots`, `sync_conflict_log`, `sync_dead_letters` tables (V4)
- `SyncProcessor.kt` — push processing with batch validation
- `DeltaEngine.kt` — cursor-based pull with delta computation
- `EntityApplier.kt` — JSONB → normalized tables (ONLY handles PRODUCT type)
- `ServerConflictResolver.kt` — LWW (Last-Write-Wins) resolution
- `SyncRoutes.kt` — REST `/sync/push` and `/sync/pull` endpoints
- WebSocket endpoints in `backend/sync` service
- KMM client: `sync_queue.sq` (outbox), `sync_state.sq` (cursor), `version_vectors.sq` (CRDT metadata)
- KMM client: `ConflictResolver.kt` — LWW with field-level merge for PRODUCT

**What's MISSING:**
- [ ] `EntityApplier` — extend to handle ALL entity types (ORDER, CUSTOMER, CATEGORY, SUPPLIER, STOCK_ADJUSTMENT, CASH_REGISTER, REGISTER_SESSION, AUDIT_ENTRY, etc.)
- [ ] Multi-store data isolation enforcement on sync endpoints (validate `store_id` matches JWT claims)
- [ ] WebSocket push notifications to clients after server processes sync ops
- [ ] JWT validation on WebSocket upgrade in `backend/sync`
- [ ] Sync payload field-level validation (missing fields, invalid types)
- [ ] POS token revocation check during JWT validation (`revoked_tokens` table exists, not checked)
- [ ] Heartbeat replay protection

**Key Files:**
- `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt`
- `backend/api/src/main/kotlin/.../sync/DeltaEngine.kt`
- `backend/api/src/main/kotlin/.../sync/EntityApplier.kt`
- `backend/api/src/main/kotlin/.../sync/ServerConflictResolver.kt`
- `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt`
- `shared/data/src/commonMain/kotlin/.../sync/ConflictResolver.kt`
- `shared/data/src/commonMain/sqldelight/.../sync_queue.sq`

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
- [ ] Admin panel email delivery log UI page (`admin-panel/src/routes/settings/email.tsx`)
- [ ] Email template editor in admin panel
- [ ] Email preference management UI for customers
- [ ] Bounce/complaint webhook handler from Resend
- [ ] Email retry logic for QUEUED → SENDING failures

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

### A5. Firebase Analytics & Sentry Integration (TODO-011) — ~40% Complete

**Priority:** P1-HIGH

**What EXISTS:** Sentry DSN secrets configured, `GOOGLE_SERVICES_JSON` + `GA4_MEASUREMENT_ID` secrets exist

**What's MISSING:**
- [ ] Firebase Android SDK in `androidApp/build.gradle.kts`
- [ ] `google-services.json` injection in CI
- [ ] `FirebaseAnalytics` initialization in `ZyntaApplication.kt`
- [ ] `AnalyticsTracker` interface in `:shared:core`
- [ ] Screen view events in all feature modules
- [ ] Sentry initialization in 3 backend services
- [ ] Sentry error boundary in admin panel

---

### A6. Security Monitoring (TODO-010) — ~85% Complete

**Priority:** P1-HIGH

**What's MISSING:**
- [ ] Snyk Monitor step in `ci-gate.yml`
- [ ] Falcosidekick → Slack webhook wiring
- [ ] Cloudflare tunnel config placeholder replacement
- [ ] OWASP dependency check in CI pipeline
- [ ] CF Zero Trust + WAF rules (dashboard action)

---

### A7. Admin JWT Security Gap

**Priority:** P1-HIGH
**Issue:** Admin panel uses HS256 (symmetric) while POS uses RS256 (asymmetric)

**Fix:**
- [ ] Migrate admin auth to RS256
- [ ] Update `AdminAuthService.kt` token generation
- [ ] Update License service admin JWT validation
- [ ] Rotate existing admin sessions

---

## SECTION B: MEDIUM PRIORITY (P2)

---

### B1. Admin Panel Enhancements (TODO-007a) — ~98%

- [ ] Security dashboard page
- [ ] OTA update management page
- [ ] Playwright E2E tests
- [ ] VPS deployment via GitHub Actions

### B2. Admin Panel Custom Auth (TODO-007f) — ~75%

- [ ] Session management UI (view/revoke active sessions)
- [ ] Security audit log page
- [ ] IP allowlisting middleware
- [ ] Login notification emails
- [ ] Forced password rotation policy

### B3. Monitoring — Uptime Kuma (TODO-007c) — ~70%

- [ ] Monitors for all 7 subdomains
- [ ] Slack/email alert channels
- [ ] Status page branding
- [ ] Docker + DB health monitors

### B4. Backend Test Coverage — ~25% vs 80% target

- [ ] Testcontainers setup (PostgreSQL + Redis)
- [ ] `SyncProcessor`, `DeltaEngine`, `EntityApplier` tests
- [ ] `AdminAuthService`, `LicenseService` tests
- [ ] Coverage reporting in CI pipeline

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

**What's MISSING (8 tasks):**
- [ ] **TASK 1:** Email thread viewing + reply-to-reply chain tracking (V21 migration: `parent_thread_id`)
- [ ] **TASK 2:** Bulk ticket operations (assign, resolve, CSV export)
- [ ] **TASK 3:** SLA breach email notifications (extend `checkSlaBreaches()`)
- [ ] **TASK 4:** Advanced ticket filtering (date range, full-text search on body)
- [ ] **TASK 5:** Ticket metrics/analytics endpoint (totalOpen, avgResolutionTime, etc.)
- [ ] **TASK 6:** Agent reply by email (outbound from ticket comment)
- [ ] **TASK 7:** Customer portal — public ticket status check via token URL (V22 migration)
- [ ] **BUG FIX:** InboundEmailProcessor hardcoded SLA (always MEDIUM/48h) — should use `inferPriorityFromEmail()`

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
**Status:** PARTIALLY EXISTS

**Codebase State:**
- Products have `stock_qty` per product per store (via `store_id` FK)
- `stock_transfers.sq` EXISTS in SQLDelight — inter-warehouse transfers partially implemented
- `warehouses.sq` EXISTS — warehouse table with store_id FK
- `WarehouseRepositoryImpl.kt` — partial implementation
- `min_stock_qty` column exists on `products.sq` — reorder point data present
- Backend `products` table has `stock_qty NUMERIC(12,4)` per store

**What's MISSING:**
- [ ] Per-warehouse stock levels within a store (currently product-level only)
- [ ] Stock level aggregation API across stores (total stock for a product globally)
- [ ] Low-stock alerts per store (KMM dashboard shows alerts but single-store only)
- [ ] Admin panel: Cross-store stock level comparison view
- [ ] Backend: `GET /admin/inventory/global?productId=X` endpoint

**Key Files:**
- `shared/data/src/commonMain/sqldelight/.../products.sq` (stock_qty column)
- `shared/data/src/commonMain/sqldelight/.../warehouses.sq`
- `shared/data/src/commonMain/.../repository/WarehouseRepositoryImpl.kt`

---

### C1.3 Inter-Store Stock Transfer / IST (ශාඛා අතර තොග හුවමාරුව)

**Priority:** PHASE-2
**Status:** PARTIALLY EXISTS (warehouse-level, not store-level)

**Codebase State:**
- `stock_transfers.sq` EXISTS — transfer table with source/destination warehouse
- `StockTransfer` domain model EXISTS — status enum (REQUESTED, APPROVED, IN_TRANSIT, RECEIVED, CANCELLED)
- `purchase_orders.sq` EXISTS — PO system for supplier orders
- Backend: NO inter-store transfer endpoint or table

**What's MISSING:**
- [ ] `InterStoreTransfer` domain model (source_store_id, dest_store_id, items, status, requested_by, approved_by)
- [ ] `inter_store_transfers` SQLDelight table + `transfer_items` junction table
- [ ] Backend migration: `inter_store_transfers` PostgreSQL table
- [ ] Transfer workflow: REQUEST → APPROVE → DISPATCH → IN_TRANSIT → RECEIVE
- [ ] `InterStoreTransferRepository` interface + impl
- [ ] `RequestTransferUseCase`, `ApproveTransferUseCase`, `DispatchTransferUseCase`, `ReceiveTransferUseCase`
- [ ] KMM UI: Transfer request screen (select products, quantities, destination store)
- [ ] KMM UI: Transfer approval screen (manager role)
- [ ] Backend: `POST /admin/transfers`, `PUT /admin/transfers/{id}/approve`, etc.
- [ ] Admin panel: Transfer management dashboard
- [ ] Notification: Push notification when transfer arrives at destination store

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
**Status:** DATA EXISTS, NO AUTO-LOGIC

**Codebase State:**
- `products.sq` has `min_stock_qty REAL` — reorder threshold exists
- `purchase_orders.sq` EXISTS — manual PO creation
- `warehouses.sq` EXISTS — warehouse registry per store
- `WarehouseRepositoryImpl.kt` — partial implementation (Phase 3 references)
- No automated replenishment logic

**What's MISSING:**
- [ ] `ReplenishmentRule` domain model (product_id, warehouse_id, store_id, reorder_point, reorder_qty, auto_approve)
- [ ] `replenishment_rules` SQLDelight table
- [ ] `AutoReplenishmentUseCase` — check stock vs reorder_point, auto-create transfer/PO
- [ ] Background job: Periodic stock level check (e.g., daily or on stock change)
- [ ] KMM UI: Replenishment rules configuration screen
- [ ] Admin panel: Replenishment dashboard (pending auto-orders, approval queue)
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

## IMPLEMENTATION TIMELINE (Suggested)

| Sprint | Focus | Items | Duration |
|--------|-------|-------|----------|
| Sprint 1 | Sync Engine Completion | A1, C6.1, C6.2 | 2 weeks |
| Sprint 2 | Email + Security | A2, A6, A7 | 1 week |
| Sprint 3 | Remote Diagnostics | A3 | 2 weeks |
| Sprint 4 | Analytics + Docs | A4, A5 | 1 week |
| Sprint 5 | Test Coverage + Tickets | B4, B6 | 2 weeks |
| Sprint 6 | Admin Polish | B1, B2, B3 | 2 weeks |
| Sprint 7 | Centralized Inventory | C1.1, C1.2, C1.3, C1.4, C1.5 | 3 weeks |
| Sprint 8 | Pricing & Tax | C2.1, C2.2, C2.3, C2.4 | 2 weeks |
| Sprint 9 | Access Control | C3.2, C3.3, C3.4 | 2 weeks |
| Sprint 10 | Sales & Customer | C4.1, C4.2, C4.3 | 2 weeks |
| Sprint 11 | Reporting & Analytics | C5.1, C5.2, C5.4 | 2 weeks |
| Sprint 12 | Timezone + Sync Polish | C6.3, C5.3 | 1 week |
| Phase 3 | Enterprise Features | D1, D2, D3, C4.4 | 6+ weeks |

---

## FEATURE COVERAGE MATRIX

| ඔබේ Feature | Plan Item | Status |
|-------------|-----------|--------|
| **1. Centralized Inventory** | | |
| Global Product Catalog | C1.1 | NOT IMPLEMENTED |
| Store-Specific Inventory | C1.2 | PARTIAL |
| Inter-Store Stock Transfer | C1.3 | PARTIAL (warehouse level) |
| Stock In-Transit Tracking | C1.4 | SCHEMA ONLY |
| Warehouse Replenishment | C1.5 | DATA ONLY |
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
| Multi-node Sync | C6.1 | PARTIAL (LWW only) |
| Offline-First | C6.2 | PARTIAL |
| Timezone Management | C6.3 | MOSTLY COMPLETE |

---

## HOW TO USE THIS DOCUMENT

1. Pick items from the highest priority section first (A → B → C → D)
2. Each item has checkboxes `[ ]` — mark as `[x]` when complete
3. Update the `Last Updated` date at the top after changes
4. Reference specific items in commit messages (e.g., `feat(inventory): implement IST workflow [C1.3]`)
5. Move completed sections to an `## COMPLETED` section at the bottom

---

*End of document*
