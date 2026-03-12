# Backend Database Schemas

**Last updated:** 2026-03-12 (S4-7 remediation)

This document catalogs all PostgreSQL tables across the two backend databases.
For the KMM client-side SQLDelight schema, see `shared/data/src/commonMain/sqldelight/`.

---

## Database: `zyntapos_api` (14 Migrations)

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
| **admin_users** | id, email (UNIQUE), name, role, password_hash, google_sub, mfa_secret, mfa_enabled, failed_attempts, locked_until | Admin panel users; BCrypt + Google SSO |
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
| API tables | 24 |
| License tables | 3 |
| Total indexes | 55+ |
| Triggers | 1 (entity snapshot) |
| Sequences | 1 (ticket numbering) |

### Key Design Patterns

1. **Sync**: Outbox pattern + cursor-based pull (V4); LWW conflict resolution
2. **Auth**: 2-tier — POS (SHA-256 + salt) and Admin (BCrypt + Google SSO); separate session stores
3. **Audit**: Immutable logs with hash-chain tamper detection (V3, V14)
4. **Isolation**: Separate databases per service (ADR-007); no cross-DB foreign keys
5. **Normalization**: EntityApplier writes to V12 tables from sync operations for efficient admin queries
