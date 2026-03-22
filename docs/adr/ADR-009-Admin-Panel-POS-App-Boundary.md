# ADR-009: Admin Panel / POS App Feature Boundary

**Date:** 2026-03-21
**Status:** Accepted
**Deciders:** Senior KMP Architect (Dilanka)

---

## Context

The ZyntaPOS ecosystem has two distinct user-facing systems:

| System | Audience | Auth | Purpose |
|--------|----------|------|---------|
| **ZyntaPOS KMM App** (Android/Desktop) | Store personnel (ADMIN, MANAGER, CASHIER, etc.) | POS JWT (RS256) | Day-to-day store operations |
| **Admin Panel** (Web) | Zynta Solutions internal staff (ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK) | Admin JWT (HS256) | Platform monitoring, support, licensing |

During Phase 2 (multi-store, C1.x), several **store-operational features** were incorrectly implemented in the admin panel instead of the KMM app:

1. **Stock Transfers** (C1.3) — approve, dispatch, receive, cancel inter-store transfers
2. **Replenishment Rules** (C1.5) — create, update, delete reorder rules for warehouses
3. **Pricing Rules** (C2.1) — create, delete per-store/per-product pricing overrides
4. **Tax Rate CRUD** — create, update, delete tax rates (already duplicated in KMM `TaxSettingsScreen`)

These features were built with admin JWT auth (`/admin/*` endpoints) and placed in the admin panel UI. This is architecturally wrong because:

- **Store managers own these decisions**, not Zynta Solutions staff. A Zynta support operator should not approve a store's inter-warehouse transfer or set a store's reorder points.
- **Zynta staff have no business authority** over store-level pricing, tax configuration, or inventory movement.
- **Offline-first principle violated** — the admin panel is web-only, so stores with intermittent connectivity cannot manage transfers or replenishment rules offline.
- **Auth boundary crossed** — store operations should use POS JWT (RS256), not admin JWT (HS256).

---

## Decision

### Rule: The admin panel MUST NOT contain store-operational write features

The admin panel exists exclusively for **Zynta Solutions platform operations**:
- License management
- Store provisioning and monitoring
- Support ticket handling
- System health and diagnostics
- Audit log viewing
- Master product catalog curation
- Platform configuration (feature flags, system-wide settings)
- Email and notification management

**All store-level business operations** belong in the KMM app:
- Product CRUD, pricing, tax configuration
- Order processing, POS, payments
- Inventory management, stock adjustments
- Inter-store transfers (create, approve, dispatch, receive, cancel)
- Replenishment rules (create, update, delete)
- Customer management, loyalty
- Staff scheduling, payroll
- Reports and analytics
- Cash register sessions

### Boundary Test (mandatory for every new feature)

Before implementing any feature, ask:

> **"Who has the business authority to perform this action?"**

| Answer | Where it goes |
|--------|---------------|
| Store owner / manager / cashier | KMM App (POS JWT, `/v1/*` endpoints) |
| Zynta Solutions staff | Admin Panel (Admin JWT, `/admin/*` endpoints) |
| Both need visibility | KMM App for **write**, Admin Panel for **read-only monitoring** (if justified by support needs) |

### Read-only exception

The admin panel MAY display **read-only views** of store data **only** when there is a demonstrated support need (e.g., diagnosing a stuck transfer for a support ticket). However:
- Read-only views are **not the default** — they require justification.
- If the same information is accessible via the **remote diagnostic** system (TODO-006), prefer that over building a dedicated admin panel view.
- Read-only views MUST NOT include action buttons (approve, delete, edit, etc.).

### Migration: Remove misplaced features from admin panel

The following must be **completely removed** from the admin panel and implemented in the KMM app:

| Feature | Admin Panel (REMOVE) | KMM Module (IMPLEMENT) | POS API Endpoint (NEW) |
|---------|---------------------|----------------------|----------------------|
| Stock Transfers | `routes/transfers/`, `api/transfers.ts`, `types/transfer.ts` | `:feature:multistore` | `/v1/transfers/*` |
| Replenishment Rules | `routes/replenishment/`, `api/replenishment.ts`, `types/replenishment.ts` | `:feature:inventory` | `/v1/replenishment/*` |
| Pricing Rules | `api/pricing.ts` (backend only, no UI yet) | `:feature:inventory` or `:feature:settings` | `/v1/pricing/*` |
| Tax Rate CRUD | `components/config/TaxRateEditor.tsx`, tax hooks in `api/config.ts` | `:feature:settings` (already has `TaxSettingsScreen`) | Sync via `/v1/sync/push` |

Backend changes required:
- New POS-authenticated (`jwt-rs256`) endpoints under `/v1/` for transfers, replenishment, and pricing
- Existing `/admin/` endpoints for these features should be **removed** (not kept as read-only)
- If support needs store data for debugging, they use the remote diagnostic system (TODO-006)

### Sidebar cleanup

Remove these entries from admin panel `Sidebar.tsx` navigation:
- `Transfers` (`/transfers`)
- `Replenishment` (`/replenishment`)
- Tax Rates tab from Config page

---

## Consequences

### Positive

- **Clear ownership boundary** — store managers control their own business operations
- **Offline-first preserved** — KMM app works without internet; admin panel requires connectivity
- **Auth model simplified** — store operations always use POS JWT, platform operations always use admin JWT
- **Reduced admin panel attack surface** — fewer write endpoints exposed to internal staff
- **Single source of truth** — no more duplicated tax rate management in two systems

### Negative

- **Migration effort** — existing admin panel features must be rebuilt in KMM (Compose Multiplatform)
- **Backend route changes** — new `/v1/` POS-authenticated endpoints needed
- **Support visibility reduced** — support staff lose direct views; must use remote diagnostic system for store-level debugging

### Neutral

- Admin panel retains all platform-level features (licenses, stores, health, audit, master products)
- Remote diagnostic system (TODO-006) becomes the canonical way for support to inspect store data

---

## Compliance

This ADR is enforced via code review. If a PR adds a **write operation** for store-level data to the admin panel, reject it citing ADR-009.

**Code review checklist item:**
> "Does this admin panel change add store-operational write features? If yes, reject — see ADR-009."
