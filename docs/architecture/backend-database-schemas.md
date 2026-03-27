# Backend Database Schemas

**Last updated:** 2026-03-27 (V27–V39: master products, IST workflow, warehouse stock, replenishment, pricing rules, multi-currency, regional tax, user-store access, employee assignments, promotions config, global customers, fulfillment status)

This document catalogs all PostgreSQL tables across the two backend databases.
For the KMM client-side SQLDelight schema, see `shared/data/src/commonMain/sqldelight/`.

---

## Database: `zyntapos_api` (39 Migrations)

### V1 — Initial Schema

| Table | Key Columns | Notes |
|-------|------------|-------|
| **stores** | id, name, license_key, timezone, currency, is_active | Master store registry |
| **users** | id, store_id, username, password_hash, role, is_active | POS terminal users; SHA-256 + salt |
| **sync_queue** | id, store_id, device_id, entity_type, entity_id, operation, payload, vector_clock | Legacy outbox (superseded by sync_operations in V4) |
| **products** | id, store_id, name, sku, barcode, price, cost_price, stock_qty, is_active, sync_version | Product catalog with FTS support |

### V2 — Admin Panel Auth

| Table | Key Columns | Notes |
|-------|------------|-------|
| **admin_users** | id, email (UNIQUE), name, role, password_hash, mfa_secret, mfa_enabled, failed_attempts, locked_until | Admin panel users; BCrypt + TOTP MFA (google_sub removed in V17) |
| **admin_sessions** | id, user_id, token_hash (UNIQUE), user_agent, ip_address, expires_at, revoked_at | Refresh token store; single-use rotation |
| **admin_mfa_backup_codes** | id, user_id, code_hash, used_at | One-time TOTP backup codes |

### V3 — Config, Alerts & Audit

| Table | Key Columns | Notes |
|-------|------------|-------|
| **feature_flags** | key (PK), name, enabled, category, editions_available | Feature toggles, edition-gated |
| **tax_rates** | id, name, rate, is_default, country | Pre-seeded Sri Lanka defaults |
| **system_config** | key (PK), value, type, category, sensitive | System KV settings |
| **alert_rules** | id, name, category, severity, conditions (JSONB) | Rule definitions |
| **alerts** | id, rule_id, title, severity, status, store_id, fired_at | Alert instances |
| **admin_audit_log** | id, event_type, category, admin_id, entity_type, entity_id, hash_chain | Immutable admin audit trail with tamper detection |
| **store_sync_flags** | store_id (PK), force_sync_requested | Admin force-sync signal |

### V4 — Sync Engine

| Table | Key Columns | Notes |
|-------|------------|-------|
| **sync_operations** | id, store_id, device_id, entity_type, entity_id, operation, payload (JSONB), server_seq (BIGSERIAL), status | Main sync log; cursor-based pull via server_seq |
| **sync_cursors** | store_id, device_id (composite PK), last_seq | Per-device pull position |
| **sync_conflict_log** | id, entity_type, entity_id, local_op_id, server_op_id, resolution | LWW conflict resolution audit trail |
| **sync_dead_letters** | id, store_id, entity_type, payload (JSONB), error_reason | Failed operations for manual review |
| **entity_snapshots** | store_id, entity_type, entity_id (composite PK), payload (JSONB), last_seq, is_deleted | Latest entity state; maintained by trigger |

**Trigger:** `trg_sync_op_snapshot` — auto-updates entity_snapshots on sync_operations INSERT.

### V5 — Helpdesk Tickets

| Table | Key Columns | Notes |
|-------|------------|-------|
| **support_tickets** | id, ticket_number (UNIQUE), store_id, title, category, priority, status, sla_due_at | Support case tracking with SLA |
| **ticket_comments** | id, ticket_id, author_id, body, is_internal | Discussion thread |
| **ticket_attachments** | id, ticket_id, file_name, file_url, attachment_type | Ticket artifacts |

### V6 — Schema Fix

Converted `admin_audit_log.previous_values` and `new_values` from JSONB to TEXT (Exposed compatibility).

### V7 — Email & Password Reset

| Table | Key Columns | Notes |
|-------|------------|-------|
| **email_preferences** | user_id (PK), marketing_emails, unsubscribe_token (UNIQUE) | GDPR unsubscribe support |
| **password_reset_tokens** | id, admin_user_id, token_hash (UNIQUE), expires_at, used_at | One-time reset; 1-hour expiry |

### V8 — Remote Diagnostic Sessions

| Table | Key Columns | Notes |
|-------|------------|-------|
| **diagnostic_sessions** | id, store_id, technician_id, token_hash, data_scope, status, expires_at | Remote technician access; requires store consent |

### V9 — API Contract Alignment

Added columns to existing tables: `users.email`, `users.name`; `products.unit_id`, `products.tax_group_id`, `products.min_stock_qty`, `products.image_url`, `products.description`, `products.created_at`.

### V10 — JWT Token Revocation

| Table | Key Columns | Notes |
|-------|------------|-------|
| **revoked_tokens** | jti (PK), revoked_at, reason | POS access token blocklist |

### V11 — POS Auth Hardening

| Table | Key Columns | Notes |
|-------|------------|-------|
| **pos_sessions** | id, user_id, store_id, token_hash, device_id, expires_at, revoked_at | POS refresh token store |

Added `users.failed_attempts` and `users.locked_until` for brute-force protection.

### V12 — Normalized Entity Tables

| Table | Key Columns | Notes |
|-------|------------|-------|
| **categories** | id, store_id, name, parent_id, sort_order, is_active, sync_version | Populated by EntityApplier |
| **customers** | id, store_id, name, email, phone, loyalty_points, is_active, sync_version | GDPR export source |
| **suppliers** | id, store_id, name, contact_name, phone, email, is_active, sync_version | Supplier directory |
| **orders** | id, store_id, order_number, customer_id, status, grand_total, sync_version | Sales orders |
| **order_items** | id, order_id, product_id, product_name, quantity, unit_price, subtotal, sync_version | Order line items |

### V13 — Sync Operations Performance Indexes

Added `idx_sync_operations_store_status` and `idx_sync_operations_created`.

### V14 — Audit Entries (S4-11)

| Table | Key Columns | Notes |
|-------|------------|-------|
| **audit_entries** | id, store_id, device_id, event_type, user_id, entity_type, entity_id, details (JSONB), hash, previous_hash, timestamp, sync_version | Server-side storage of synced POS audit entries; hash-chain for tamper detection |

### V15 — Site Visit Token (TODO-006)

Added columns to `diagnostic_sessions`:
- `visit_type TEXT NOT NULL DEFAULT 'REMOTE'` — `REMOTE` | `ON_SITE`
- `site_visit_token_hash TEXT` — SHA-256 hash of the one-time on-site access token (raw token returned once, never stored)

Added index: `idx_diag_sessions_token_hash` on `site_visit_token_hash WHERE NOT NULL`.

### V16 — High-Query Indexes (S3-11)

Performance indexes added to `sync_operations` (the highest-traffic table):

| Index | Columns | Predicate | Purpose |
|-------|---------|-----------|---------|
| `idx_sync_ops_store_entity` | `(store_id, entity_type, entity_id)` | — | EntityApplier lookup; covers the full join predicate used by `SyncProcessor` |
| `idx_sync_ops_pending` | `(store_id, created_at DESC)` | `WHERE status = 'PENDING'` | Partial index; skips APPLIED/FAILED rows entirely on `SyncProcessor`'s batch-fetch query |

### V17 — Remove Google SSO

Removed `google_sub` column from `admin_users`. Admin authentication is email/password + TOTP MFA only; Google OAuth has been removed entirely.

### V18 — Email Threads

| Table | Key Columns | Notes |
|-------|------------|-------|
| **email_threads** | id, ticket_id (FK), message_id (UNIQUE), in_reply_to, email_references, from_address, to_address, subject, body_text, body_html, chatwoot_conversation_id, received_at | Inbound email storage from CF Email Worker; RFC 2822 threading headers for thread linking |

### V19 — Site Visit Extended Columns

Added to `diagnostic_sessions`:
- `hardware_scope TEXT` — comma-separated hardware component identifiers the technician may access (e.g. `PRINTER,SCANNER,CASH_DRAWER`); NULL for REMOTE sessions.
- `site_visit_presented_at BIGINT` — epoch-ms when the technician presented the physical site visit token on-site; NULL until validated.
- `idx_diag_sessions_visit_type` composite index on `(visit_type, status)`.

### V20 — Email Delivery Log

| Table | Key Columns | Notes |
|-------|------------|-------|
| **email_delivery_log** | id, to_address, from_address, subject, template_slug, status (QUEUED/SENDING/SENT/DELIVERED/BOUNCED/FAILED), error_message, sent_at | Outbound transactional email audit trail; admin panel reads via `GET /admin/email/delivery-logs` |

### V21 — Email Thread Chain

Added `parent_thread_id UUID` self-referencing FK to `email_threads` for nested reply-to-reply conversation rendering in the admin panel.

### V22 — Ticket Customer Token

Added `customer_access_token UUID DEFAULT gen_random_uuid()` to `support_tickets`. Allows customers to check ticket status via a unique URL token without admin-panel authentication.

### V23 — Extended Normalized Entity Tables

Ten additional server-side tables for entity types flowing through the sync pipeline:

| Table | Key Columns | Notes |
|-------|------------|-------|
| **stock_adjustments** | id, store_id, product_id, type (INCREASE/DECREASE/TRANSFER), quantity, reason, adjusted_by | Stock adjustment events |
| **cash_registers** | id, store_id, name, current_session_id, is_active | Register hardware registry |
| **register_sessions** | id, store_id, register_id, opened_by, closed_by, opening_balance, closing_balance, status (OPEN/CLOSED) | Cash session open/close |
| **cash_movements** | id, store_id, session_id, type (IN/OUT), amount, reason, recorded_by | Cash in/out movements |
| **tax_groups** | id, store_id, name, rate, is_inclusive | Store-level tax group definitions |
| **units_of_measure** | id, store_id, name, abbreviation, is_base_unit, conversion_rate | UoM catalog |
| **payment_splits** | id, store_id, order_id, method, amount, reference | Split-payment records per order |
| **coupons** | id, store_id, code, name, discount_type, discount_value, usage_limit, usage_count, scope | Coupon catalog |
| **expenses** | id, store_id, category, amount, description, recorded_by | Expense log entries |
| **settings** | id, store_id, key, value | Per-store key-value settings (UNIQUE on store_id, key) |

### V24 — Employee, Expense Category, Coupon Usage, Promotion, Customer Group Tables

Five additional normalized entity tables:

| Table | Key Columns | Notes |
|-------|------------|-------|
| **employees** | id, store_id, name, email, phone, role, department, hire_date, hourly_rate | Staff profiles synced from POS |
| **expense_categories** | id, store_id, name, description, parent_id, sort_order | Hierarchical expense category tree |
| **coupon_usages** | id, store_id, coupon_id, order_id, customer_id, discount_amount, redeemed_by | Coupon redemption log |
| **promotions** | id, store_id, name, type, value, minimum_purchase, scope, valid_from, valid_to, priority, is_stackable | Promotion rules |
| **customer_groups** | id, store_id, name, description, discount_rate | Customer group segmentation |

### V25 — Email Retry, Templates, Preferences Enhancements

Added to `email_delivery_log`: `retry_count INTEGER`, `next_retry_at TIMESTAMPTZ`, `html_body TEXT`. Partial index `idx_email_delivery_log_retry` filters FAILED rows where retry is pending.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **email_templates** | id, slug (UNIQUE), name, subject, html_body, updated_at | Admin-editable email templates; seeded with 6 default slugs (password_reset, welcome_admin, ticket_created, ticket_updated, sla_breach, ticket_reply) |

Added to `email_preferences`: `sla_breach_notifications BOOLEAN`, `daily_digest BOOLEAN`.

### V26 — Admin Password Rotation

Added `password_changed_at BIGINT` to `admin_users`. Epoch-ms timestamp of last password change; used by the `ADMIN_PASSWORD_MAX_AGE_DAYS` policy (0 = disabled). Backfilled from `created_at` for existing users.

### V27 — Master Products (C1.1)

Two-tier global product catalog.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **master_products** | id, sku, barcode, name, description, category_id, base_price, cost_price, tax_group_id, unit_of_measure, is_active, sync_version | Global catalog managed by platform ADMIN |
| **store_products** | id, master_product_id, store_id, price_override, is_available, stock_qty, sync_version | Per-store overrides; inherits from master_products |

### V28 — Inter-Store Stock Transfer (IST) Workflow (C1.3)

Multi-step approval workflow for stock movement between stores.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **stock_transfers** | id, from_store_id, to_store_id, status, requested_by, approved_by, shipped_at, received_at | Statuses: PENDING → APPROVED → IN_TRANSIT → RECEIVED; exception: CANCELLED, REJECTED |
| **stock_transfer_items** | id, transfer_id, product_id, requested_qty, approved_qty, shipped_qty, received_qty | Per-item line in a transfer |
| **transit_tracking** | id, transfer_id, event_type, event_at, actor_id, notes | Audit trail for each status transition |

### V29 — Warehouse Stock (C1.2)

Per-warehouse product stock levels (mirrors `warehouse_stock.sq` on KMM client).

| Table | Key Columns | Notes |
|-------|------------|-------|
| **warehouses** | id, name, store_id, address, is_active | Physical warehouse locations |
| **warehouse_stock** | id, warehouse_id, product_id, qty_on_hand, qty_reserved, qty_available, updated_at | Real-time stock per warehouse per product |

### V30 — Warehouse Stock Unique Constraint Fix (C1.2)

Fixes the `UNIQUE(warehouse_id, product_id)` constraint from V29 to use a partial index that correctly handles multi-store deployments.

### V31 — Replenishment Rules (C1.5)

Warehouse-to-store auto-PO generation thresholds.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **replenishment_rules** | id, warehouse_id, store_id, product_id, min_stock_level, reorder_qty, auto_approve, is_active | Triggers PO generation when `warehouse_stock.qty_available` drops below `min_stock_level` |

### V32 — Pricing Rules (C2.1)

Region-based and time-bounded product price overrides.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **pricing_rules** | id, store_id, product_id, price, currency, valid_from, valid_until, priority, is_active | Synced to POS devices for offline-first price resolution |

### V33 — Exchange Rates (C2.2)

Multi-currency conversion table.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **exchange_rates** | id, from_currency, to_currency, rate, effective_at, source | Maintained by platform; referenced during multi-currency checkout |

### V34 — Regional Tax Overrides (C2.3)

Per-store tax rate overrides for multi-region compliance.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **regional_tax_overrides** | id, store_id, tax_group_id, rate_override, effective_at, reason | Overrides global `tax_groups.rate` for stores in different jurisdictions |

Also adds `tax_registration_number VARCHAR` to `stores` table.

### V35 — User-Store Access (C3.2)

Junction table for multi-store user permissions.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **user_store_access** | id, user_id, store_id, role_override, granted_by, granted_at | Optional per-store role override; NULL role_override means user inherits their global role |

### V36 — Employee Store Assignments (C3.4)

Tracks roaming employees assigned to multiple stores beyond their primary store.

| Table | Key Columns | Notes |
|-------|------------|-------|
| **employee_store_assignments** | id, employee_id, store_id, assigned_at, assigned_by, is_active | N:M between employees and stores (primary store remains in `employees.store_id`) |

### V37 — Promotions Config & Store IDs (C2.4)

Extends the V24 `promotions` table for richer promotion types.

Columns added to `promotions`: `config JSONB` (sealed `PromotionConfig`: BUY_X_GET_Y, BUNDLE, FLASH_SALE, SCHEDULED), `store_ids UUID[]` (NULL = all stores).

### V38 — Customers Store ID Nullable

Makes `customers.store_id` nullable to support global (cross-store) customers. NULL `store_id` means the customer belongs to the tenant account and is accessible from any store.

### V39 — Orders Fulfillment Status

Adds BOPIS (click-and-collect) fulfillment lifecycle to `orders`.

Column added to `orders`: `fulfillment_status VARCHAR` — lifecycle: `RECEIVED → PREPARING → READY_FOR_PICKUP → PICKED_UP`; exception paths: any state → `EXPIRED`; `RECEIVED/PREPARING` → `CANCELLED`.

---

## Database: `zyntapos_license` (4 Migrations)

### V1 — License Schema

| Table | Key Columns | Notes |
|-------|------------|-------|
| **licenses** | key (PK), customer_id, edition, max_devices, status, expires_at | License registry; NULL expires_at = perpetual |
| **device_registrations** | id, license_key (FK), device_id (UNIQUE per license), device_name, app_version, last_seen_at | Device tracking per license |

### V2 — Force Sync & Customer

Added `licenses.force_sync_requested` and `licenses.customer_name`.

### V3 — Admin Audit Log

| Table | Key Columns | Notes |
|-------|------------|-------|
| **admin_audit_log** | id, admin_id, action, license_key, details | License management audit trail |

### V4 — Community Edition

Updated edition CHECK constraint: `STARTER` renamed to `COMMUNITY`.

---

## Summary

| Metric | Count |
|--------|-------|
| API tables | 51 |
| License tables | 3 |
| Total indexes | 80+ |
| Triggers | 1 (entity snapshot) |
| Sequences | 1 (ticket numbering) |

### Key Design Patterns

1. **Sync**: Outbox pattern + cursor-based pull (V4); LWW conflict resolution
2. **Auth**: 2-tier — POS (SHA-256 + salt) and Admin (BCrypt + TOTP MFA; Google SSO removed in V17); separate session stores
3. **Audit**: Immutable logs with hash-chain tamper detection (V3, V14)
4. **Isolation**: Separate databases per service (ADR-007); no cross-DB foreign keys
5. **Normalization**: EntityApplier writes to V12 tables from sync operations for efficient admin queries
