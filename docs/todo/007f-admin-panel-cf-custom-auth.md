# TODO-007f — Admin Panel: CF + Custom Auth (Enterprise-Grade)

**Phase:** 2 — Growth
**Priority:** P0 (HIGH)
**Status:** Ready to implement
**Effort:** ~7 working days (1 developer)
**Related:** TODO-007a (admin panel), TODO-009 (Ktor security hardening), TODO-010 (security monitoring)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-07

---

## 1. Overview

Replace the current Cloudflare Access-only (Option 1) auth with a **CF + Custom hybrid** — keeping
Cloudflare for network-level security (DDoS, WAF, TLS) while adding a fully custom, ZyntaPOS-branded
login system for identity and access management.

### Why CF + Custom Hybrid?

```
Cloudflare Layer (network security — already in place)
  • DDoS protection (Tbps scale)       ← impossible to replicate in code
  • WAF custom rules (API, license)    ← already configured
  • TLS Full (Strict) + HSTS           ← already configured
  • Orange-cloud proxy (all subdomains)← already configured
  • Rate limiting at edge              ← already configured
        ↓  (only clean traffic reaches the VPS)
Custom Auth Layer (identity + access — to be implemented)
  • ZyntaPOS branded login page        ← currently CF branded
  • Backend admin user table + JWT     ← to implement
  • Role-based access (ADMIN / OPERATOR / FINANCE / AUDITOR / HELPDESK)  ← to implement
  • MFA (TOTP — Google Authenticator)  ← to implement
  • Google SSO (restricted to @zyntapos.com)  ← to implement
  • Admin user management UI           ← to implement
  • Brute-force protection + lockout   ← to implement
  • Audit trail for auth events        ← to implement
```

### Current State (before this TODO)

- `use-auth.ts` reads `CF_Authorization` cookie set by Cloudflare Access
- Role is stored as a CF custom JWT claim (`custom:role`) — managed in CF dashboard
- No custom login page; users see the CF Access login UI
- No backend admin user table; user management done in CF dashboard
- No MFA (CF Access has MFA but behind CF branded UI)
- No Google SSO (CF Access has it but CF-managed, not ZyntaPOS-managed)

### Target State (after this TODO)

- Custom login page at `panel.zyntapos.com/login` — ZyntaPOS branded
- Backend admin user table in PostgreSQL (`admin_users`, `admin_sessions`, `admin_mfa`)
- Ktor admin auth routes (`/admin/auth/*`) behind the existing `panel.zyntapos.com` Caddy config
- ZyntaPOS-issued JWTs (HS256, 15-min access + 7-day refresh) in httpOnly cookies
- TOTP MFA (Google Authenticator / Authy compatible)
- Google OAuth 2.0 SSO restricted to `@zyntapos.com` domain
- Admin user CRUD UI in the panel's `/settings/users` route
- Full auth audit trail in the existing audit log system

---

## 2. Architecture

### Auth Flow Diagram

```
User visits panel.zyntapos.com
        │
        ▼
Cloudflare (DDoS block, WAF rules, TLS termination)
        │
        ▼ (clean traffic only)
Caddy reverse proxy → panel:8081 (React SPA)
        │
        ▼
React Router → /login (if no valid JWT cookie)
        │
        ├── Username + Password  ─────────────────────┐
        │                                             │
        ├── Google SSO (OAuth 2.0 PKCE) ─────────────┤
        │                                             ▼
        │                              POST /admin/auth/login
        │                              (Ktor backend, port 8080)
        │                                             │
        │                              ┌──────────────┴──────────────┐
        │                              ▼                             ▼
        │                         Verify creds              Check Google token
        │                         + bcrypt hash             + validate @domain
        │                              │
        │                              ▼
        │                         MFA required? ──→ POST /admin/auth/mfa/verify
        │                              │
        │                              ▼
        │                    Issue JWT (15-min access)
        │                  + Refresh token (7-day, DB-stored)
        │                  → Set httpOnly cookies
        │                              │
        ▼                              ▼
Redirect to /          React reads user from JWT payload
```

### Token Architecture

```
Access Token (JWT HS256)
  Header:  { alg: "HS256", typ: "JWT" }
  Payload: {
    sub:   "admin_user_uuid",
    email: "dilanka@zyntapos.com",
    name:  "Dilanka",
    role:  "ADMIN",           // ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK
    mfa:   true,                    // MFA was verified in this session
    iat:   1741305600,
    exp:   1741306500               // 15 minutes
  }
  Storage: httpOnly, Secure, SameSite=Strict cookie

Refresh Token (opaque UUID)
  Storage: httpOnly, Secure, SameSite=Strict cookie
           + hashed in admin_sessions table (DB truth)
  Validity: 7 days, single-use (rotated on each refresh)
```

---

## 3. Database Schema

### New Tables (PostgreSQL — `zyntapos_api` database)

```sql
-- Admin users for the panel
CREATE TABLE admin_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK')),
    password_hash   TEXT,                      -- NULL if Google SSO only
    google_sub      TEXT UNIQUE,               -- NULL if password only
    mfa_secret      TEXT,                      -- TOTP secret, encrypted at rest
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,               -- NULL = not locked
    last_login_at   TIMESTAMPTZ,
    last_login_ip   TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Refresh token store (single-use rotation)
CREATE TABLE admin_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,      -- SHA-256 of the opaque refresh token
    user_agent      TEXT,
    ip_address      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ                -- NULL = valid
);

-- MFA backup codes (one-time use)
CREATE TABLE admin_mfa_backup_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    code_hash       TEXT NOT NULL,             -- bcrypt of the backup code
    used_at         TIMESTAMPTZ                -- NULL = unused
);

-- Indexes
CREATE INDEX idx_admin_sessions_user_id ON admin_sessions(user_id);
CREATE INDEX idx_admin_sessions_token_hash ON admin_sessions(token_hash);
CREATE INDEX idx_admin_mfa_backup_codes_user_id ON admin_mfa_backup_codes(user_id);

-- ───────────────────────────────────────────────────────────────────────────
-- Support Ticket System (HELPDESK extension — V6 migration)
-- ───────────────────────────────────────────────────────────────────────────

-- Support tickets created by HELPDESK, resolved by OPERATOR
CREATE TABLE support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number   TEXT NOT NULL UNIQUE,          -- TKT-2026-001234 (auto-generated)
    store_id        TEXT,                          -- links to ZyntaPOS store (nullable — not all issues are store-specific)
    license_id      UUID,                          -- links to a license row (nullable)

    -- Reporter (the HELPDESK agent who created the ticket)
    created_by      UUID NOT NULL REFERENCES admin_users(id),
    customer_name   TEXT NOT NULL,
    customer_email  TEXT,
    customer_phone  TEXT,

    -- Assignment (must be OPERATOR or ADMIN)
    assigned_to     UUID REFERENCES admin_users(id),
    assigned_at     TIMESTAMPTZ,

    -- Content
    title           TEXT NOT NULL,
    description     TEXT NOT NULL,
    category        TEXT NOT NULL CHECK (category IN (
                      'HARDWARE', 'SOFTWARE', 'SYNC', 'BILLING', 'OTHER')),
    priority        TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN (
                      'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    -- Lifecycle
    status          TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN (
                      'OPEN', 'ASSIGNED', 'IN_PROGRESS',
                      'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED')),

    -- Resolution (only OPERATOR / ADMIN can set these)
    resolved_by     UUID REFERENCES admin_users(id),
    resolved_at     TIMESTAMPTZ,
    resolution_note TEXT,
    time_spent_min  INTEGER,

    -- SLA (calculated from priority at creation time)
    sla_due_at      TIMESTAMPTZ,
    sla_breached    BOOLEAN NOT NULL DEFAULT FALSE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- SLA rules (enforced at application layer):
--   CRITICAL → response 1h,  resolution 4h
--   HIGH     → response 4h,  resolution 24h
--   MEDIUM   → response 8h,  resolution 48h
--   LOW      → response 24h, resolution 72h

-- Comments / activity log per ticket
CREATE TABLE ticket_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES admin_users(id),
    body        TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,    -- TRUE = internal note (hidden from customer-facing views)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- File / diagnostic log attachments
CREATE TABLE ticket_attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    uploaded_by     UUID NOT NULL REFERENCES admin_users(id),
    file_name       TEXT NOT NULL,
    file_url        TEXT NOT NULL,                 -- S3/R2 URL or inline diagnostic log reference
    attachment_type TEXT NOT NULL DEFAULT 'FILE' CHECK (attachment_type IN ('FILE', 'DIAGNOSTIC_LOG')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for tickets
CREATE INDEX idx_support_tickets_status      ON support_tickets(status);
CREATE INDEX idx_support_tickets_assigned_to ON support_tickets(assigned_to);
CREATE INDEX idx_support_tickets_created_by  ON support_tickets(created_by);
CREATE INDEX idx_support_tickets_store_id    ON support_tickets(store_id);
CREATE INDEX idx_support_tickets_priority    ON support_tickets(priority);
CREATE INDEX idx_ticket_comments_ticket_id   ON ticket_comments(ticket_id);
CREATE INDEX idx_ticket_attachments_ticket   ON ticket_attachments(ticket_id);
```

> **Migration file:** `backend/api/src/main/resources/db/migration/V6__helpdesk_tickets.sql`
> Run after V5 (admin auth tables).

---

## 3.5 Role Definitions & Permission Matrix

### Roles (5)

| Role | Who Gets It | Real-World Title |
|------|------------|-----------------|
| `ADMIN` | Company owner / CTO | Dilanka + co-founder |
| `OPERATOR` | Technical support engineers | Field techs who install/fix POS terminals |
| `FINANCE` | Finance team | Accountant, billing manager |
| `AUDITOR` | Company auditors | Internal/external compliance auditor |
| `HELPDESK` | Customer support agents | Front-line staff who log customer complaints and coordinate with OPERATOR |

### Permission Matrix

> 👁 = Read-only (no export/write) | ✅ = Full access | ❌ = No access (404 on API, redirected on frontend)

#### Dashboard
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Dashboard — ops KPIs (uptime, sync, errors) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Dashboard — financial KPIs (MRR, churn) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Dashboard — support KPIs (open tickets, SLA status) | ✅ | ✅ | ❌ | ❌ | ✅ |

#### Licenses
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| License — view | ✅ | ✅ | ✅ | 👁 | 👁 |
| License — create / extend | ✅ | ❌ | ❌ | ❌ | ❌ |
| License — revoke / suspend | ✅ | ❌ | ❌ | ❌ | ❌ |
| License — export to CSV | ✅ | ❌ | ✅ | ❌ | ❌ |

#### Stores
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Stores — view health & sync status | ✅ | ✅ | ❌ | ❌ | 👁 |
| Stores — manage sync conflicts | ✅ | ✅ | ❌ | ❌ | ❌ |
| Store config — read | ✅ | ✅ | ❌ | ❌ | ❌ |

#### Remote Operations
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Remote Diagnostics — run | ✅ | ✅ | ❌ | ❌ | ❌ |
| Remote Diagnostics — read results | ✅ | ✅ | ❌ | ❌ | 👁 |
| Remote Config Push | ✅ | ❌ | ❌ | ❌ | ❌ |

#### Reports
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Financial Reports (revenue, churn, billing) | ✅ | ❌ | ✅ | ❌ | ❌ |
| Operational Reports (usage, uptime, sync) | ✅ | ✅ | ❌ | 👁 | ❌ |
| Support Reports (ticket metrics, SLA stats) | ✅ | ✅ | ❌ | ❌ | ✅ |
| Reports — read (any) | ✅ | ✅ | ✅ | 👁 | 👁 |
| Reports — export to CSV/PDF | ✅ | ❌ | ✅ | ❌ | ❌ |

#### Alerts
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Alerts — view | ✅ | ✅ | ❌ | ❌ | ❌ |
| Alerts — acknowledge | ✅ | ✅ | ❌ | ❌ | ❌ |
| Alerts — configure thresholds | ✅ | ❌ | ❌ | ❌ | ❌ |

#### Audit Logs
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Audit Logs — read | ✅ | ❌ | ❌ | ✅ | ❌ |
| Audit Logs — export | ✅ | ❌ | ❌ | ✅ | ❌ |

#### Support Tickets *(HELPDESK extension)*
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Tickets — read all | ✅ | ✅ | ❌ | ❌ | ✅ |
| Tickets — create | ✅ | ✅ | ❌ | ❌ | ✅ |
| Tickets — update (title, description, priority) | ✅ | ✅ | ❌ | ❌ | ✅ |
| Tickets — assign to OPERATOR | ✅ | ✅ | ❌ | ❌ | ✅ |
| Tickets — resolve (mark RESOLVED + add note) | ✅ | ✅ | ❌ | ❌ | ❌ |
| Tickets — close (after OPERATOR resolves) | ✅ | ✅ | ❌ | ❌ | ✅ |
| Tickets — comment (internal + customer-visible) | ✅ | ✅ | ❌ | ❌ | ✅ |

> **Why HELPDESK cannot resolve:** Accountability principle — only the OPERATOR who performed the fix can mark the ticket RESOLVED, adding a required resolution note and time-spent. HELPDESK confirms with the customer and then closes the ticket.

#### Admin / System
| Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|---------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Admin User Management — read | ✅ | ❌ | ❌ | ❌ | ❌ |
| Admin User Management — write | ✅ | ❌ | ❌ | ❌ | ❌ |
| Admin User Management — deactivate | ✅ | ❌ | ❌ | ❌ | ❌ |
| Admin User Sessions — revoke | ✅ | ❌ | ❌ | ❌ | ❌ |
| System Settings | ✅ | ❌ | ❌ | ❌ | ❌ |
| System Health | ✅ | ✅ | ❌ | ❌ | ❌ |
| System Backup | ✅ | ❌ | ❌ | ❌ | ❌ |

### Granular Permission Constants (TypeScript + Kotlin)

> **37 atomic permissions** — each maps to exactly one capability. Backend enforces via `AdminPermissions.check()`. Frontend uses `hasPermission()` to show/hide nav items and action buttons.

```typescript
// admin-panel/src/lib/permissions.ts
export type AdminRole = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'AUDITOR' | 'HELPDESK'

export const PERMISSIONS = {
  // ── Dashboard ──────────────────────────────────────────────────────────────
  'dashboard:ops':              ['ADMIN', 'OPERATOR'],
  'dashboard:financial':        ['ADMIN', 'FINANCE'],
  'dashboard:support':          ['ADMIN', 'OPERATOR', 'HELPDESK'],

  // ── Licenses ───────────────────────────────────────────────────────────────
  'license:read':               ['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK'],
  'license:write':              ['ADMIN'],
  'license:revoke':             ['ADMIN'],
  'license:export':             ['ADMIN', 'FINANCE'],

  // ── Stores ─────────────────────────────────────────────────────────────────
  'store:read':                 ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'store:sync:manage':          ['ADMIN', 'OPERATOR'],
  'store:config:read':          ['ADMIN', 'OPERATOR'],

  // ── Remote Operations ──────────────────────────────────────────────────────
  'diagnostics:access':         ['ADMIN', 'OPERATOR'],
  'diagnostics:read':           ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'config:push':                ['ADMIN'],

  // ── Reports ────────────────────────────────────────────────────────────────
  'reports:financial':          ['ADMIN', 'FINANCE'],
  'reports:operational':        ['ADMIN', 'OPERATOR'],
  'reports:support':            ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'reports:read':               ['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK'],
  'reports:export':             ['ADMIN', 'FINANCE'],

  // ── Alerts ─────────────────────────────────────────────────────────────────
  'alerts:read':                ['ADMIN', 'OPERATOR'],
  'alerts:acknowledge':         ['ADMIN', 'OPERATOR'],
  'alerts:configure':           ['ADMIN'],

  // ── Audit Logs ─────────────────────────────────────────────────────────────
  'audit:read':                 ['ADMIN', 'AUDITOR'],
  'audit:export':               ['ADMIN', 'AUDITOR'],

  // ── Support Tickets ────────────────────────────────────────────────────────
  'tickets:read':               ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'tickets:create':             ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'tickets:update':             ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'tickets:assign':             ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'tickets:resolve':            ['ADMIN', 'OPERATOR'],           // HELPDESK cannot resolve
  'tickets:close':              ['ADMIN', 'OPERATOR', 'HELPDESK'],
  'tickets:comment':            ['ADMIN', 'OPERATOR', 'HELPDESK'],

  // ── Admin User Management ──────────────────────────────────────────────────
  'users:read':                 ['ADMIN'],
  'users:write':                ['ADMIN'],
  'users:deactivate':           ['ADMIN'],
  'users:sessions:revoke':      ['ADMIN'],

  // ── System ─────────────────────────────────────────────────────────────────
  'system:settings':            ['ADMIN'],
  'system:health':              ['ADMIN', 'OPERATOR'],
  'system:backup':              ['ADMIN'],
} as const satisfies Record<string, AdminRole[]>

export function hasPermission(role: AdminRole, permission: keyof typeof PERMISSIONS): boolean {
  return (PERMISSIONS[permission] as string[]).includes(role)
}
```

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/auth/AdminPermissions.kt
enum class AdminRole { ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK }

object AdminPermissions {
    private val permissions = mapOf(
        // Dashboard
        "dashboard:ops"           to setOf(ADMIN, OPERATOR),
        "dashboard:financial"     to setOf(ADMIN, FINANCE),
        "dashboard:support"       to setOf(ADMIN, OPERATOR, HELPDESK),

        // Licenses
        "license:read"            to setOf(ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK),
        "license:write"           to setOf(ADMIN),
        "license:revoke"          to setOf(ADMIN),
        "license:export"          to setOf(ADMIN, FINANCE),

        // Stores
        "store:read"              to setOf(ADMIN, OPERATOR, HELPDESK),
        "store:sync:manage"       to setOf(ADMIN, OPERATOR),
        "store:config:read"       to setOf(ADMIN, OPERATOR),

        // Remote operations
        "diagnostics:access"      to setOf(ADMIN, OPERATOR),
        "diagnostics:read"        to setOf(ADMIN, OPERATOR, HELPDESK),
        "config:push"             to setOf(ADMIN),

        // Reports
        "reports:financial"       to setOf(ADMIN, FINANCE),
        "reports:operational"     to setOf(ADMIN, OPERATOR),
        "reports:support"         to setOf(ADMIN, OPERATOR, HELPDESK),
        "reports:read"            to setOf(ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK),
        "reports:export"          to setOf(ADMIN, FINANCE),

        // Alerts
        "alerts:read"             to setOf(ADMIN, OPERATOR),
        "alerts:acknowledge"      to setOf(ADMIN, OPERATOR),
        "alerts:configure"        to setOf(ADMIN),

        // Audit logs
        "audit:read"              to setOf(ADMIN, AUDITOR),
        "audit:export"            to setOf(ADMIN, AUDITOR),

        // Support tickets
        "tickets:read"            to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:create"          to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:update"          to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:assign"          to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:resolve"         to setOf(ADMIN, OPERATOR),
        "tickets:close"           to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:comment"         to setOf(ADMIN, OPERATOR, HELPDESK),

        // Admin user management
        "users:read"              to setOf(ADMIN),
        "users:write"             to setOf(ADMIN),
        "users:deactivate"        to setOf(ADMIN),
        "users:sessions:revoke"   to setOf(ADMIN),

        // System
        "system:settings"         to setOf(ADMIN),
        "system:health"           to setOf(ADMIN, OPERATOR),
        "system:backup"           to setOf(ADMIN),
    )

    fun check(role: AdminRole, permission: String): Boolean =
        permissions[permission]?.contains(role) ?: false
}
```

### MFA Policy

| Role | MFA Requirement |
|------|----------------|
| `ADMIN` | ✅ **Mandatory** — system enforced, cannot skip |
| `OPERATOR` | ✅ **Mandatory** — has remote diagnostic access |
| `FINANCE` | ⚠️ Strongly recommended — has financial data |
| `AUDITOR` | ⚠️ Recommended — read-only but sensitive logs |
| `HELPDESK` | ⚠️ Recommended — handles customer PII (email, phone, store IDs) |

### Google SSO Auto-Provisioning

New users logging in via Google SSO for the first time are auto-provisioned
as `AUDITOR` (most restrictive role). ADMIN manually upgrades their role.
This prevents accidental over-privileged access.

---

## 4. Backend — Ktor Auth Routes

**New file:** `backend/api/src/main/kotlin/com/zyntasolutions/api/routes/AdminAuthRoutes.kt`

### Endpoint Specification

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/admin/auth/login` | None | Email+password login |
| `POST` | `/admin/auth/google` | None | Google OAuth token exchange |
| `POST` | `/admin/auth/mfa/verify` | Pre-MFA JWT | Verify TOTP code |
| `POST` | `/admin/auth/mfa/setup` | Admin JWT | Generate TOTP secret + QR URI |
| `POST` | `/admin/auth/mfa/setup/confirm` | Admin JWT | Confirm TOTP setup, issue backup codes |
| `POST` | `/admin/auth/mfa/backup` | Pre-MFA JWT | Verify backup code |
| `POST` | `/admin/auth/refresh` | None (cookie) | Rotate refresh token, issue new access token |
| `POST` | `/admin/auth/logout` | Admin JWT | Revoke session, clear cookies |
| `GET`  | `/admin/auth/me` | Admin JWT | Return current user info |
| `GET`  | `/admin/users` | ADMIN | List all admin users |
| `POST` | `/admin/users` | ADMIN | Create new admin user |
| `PUT`  | `/admin/users/{id}` | ADMIN | Update role / status |
| `DELETE` | `/admin/users/{id}` | ADMIN | Deactivate user |
| `GET`  | `/admin/users/{id}/sessions` | ADMIN | List active sessions |
| `DELETE` | `/admin/users/{id}/sessions` | ADMIN | Revoke all sessions for user |
| `GET`  | `/admin/tickets` | ADMIN, OPERATOR, HELPDESK | List tickets (filterable by status, priority, assignee) |
| `POST` | `/admin/tickets` | ADMIN, OPERATOR, HELPDESK | Create new ticket |
| `GET`  | `/admin/tickets/{id}` | ADMIN, OPERATOR, HELPDESK | Get ticket detail |
| `PUT`  | `/admin/tickets/{id}` | ADMIN, OPERATOR, HELPDESK | Update ticket (title, description, priority) |
| `POST` | `/admin/tickets/{id}/assign` | ADMIN, OPERATOR, HELPDESK | Assign to OPERATOR |
| `POST` | `/admin/tickets/{id}/resolve` | ADMIN, OPERATOR | Mark RESOLVED (requires resolution_note + time_spent_min) |
| `POST` | `/admin/tickets/{id}/close` | ADMIN, OPERATOR, HELPDESK | Mark CLOSED (after RESOLVED) |
| `GET`  | `/admin/tickets/{id}/comments` | ADMIN, OPERATOR, HELPDESK | List comments |
| `POST` | `/admin/tickets/{id}/comments` | ADMIN, OPERATOR, HELPDESK | Add comment (internal or customer-visible) |
| `POST` | `/admin/tickets/{id}/attachments` | ADMIN, OPERATOR, HELPDESK | Upload file / link diagnostic log |

### Rate Limiting (Redis-backed)

```
POST /admin/auth/login        → 5 requests / 15 min / IP
POST /admin/auth/google       → 10 requests / 15 min / IP
POST /admin/auth/mfa/verify   → 5 requests / 5 min / IP
POST /admin/auth/refresh      → 20 requests / 15 min / IP
```

### Security Headers (Ktor plugin — all `/admin/*` routes)

```kotlin
response.headers.apply {
    append("X-Content-Type-Options", "nosniff")
    append("X-Frame-Options", "DENY")
    append("Referrer-Policy", "strict-origin-when-cross-origin")
    append("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    // CSP — strict (inline scripts blocked; only ZyntaPOS panel allowed)
    append("Content-Security-Policy",
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https://lh3.googleusercontent.com; " +
        "connect-src 'self' https://api.zyntapos.com https://license.zyntapos.com; " +
        "frame-ancestors 'none'"
    )
}
```

### Brute Force Protection Logic

```
Login attempt received:
  1. Check admin_users.locked_until → if future timestamp: return 423 Locked
  2. Verify bcrypt(password, password_hash)
  3. On failure:
       failed_attempts++
       if failed_attempts >= 5:
         locked_until = NOW() + 15 minutes
         send auth audit event: ADMIN_LOGIN_LOCKOUT
       return 401 Unauthorized (generic message — do not reveal if email exists)
  4. On success:
       failed_attempts = 0
       locked_until = NULL
       last_login_at = NOW()
       last_login_ip = request.ip
```

---

## 5. Frontend — React Changes

### New Files

```
admin-panel/src/
├── routes/
│   ├── login.tsx                        # Login page (NEW)
│   └── tickets/
│       ├── index.tsx                    # Ticket list + filters (NEW — HELPDESK extension)
│       └── $ticketId.tsx                # Ticket detail + comments + timeline (NEW)
├── components/
│   ├── auth/
│   │   ├── LoginForm.tsx                # Email + password form (NEW)
│   │   ├── GoogleSsoButton.tsx          # Google OAuth PKCE button (NEW)
│   │   ├── MfaVerifyForm.tsx            # TOTP input (NEW)
│   │   ├── MfaSetupWizard.tsx           # QR code + backup codes (NEW)
│   │   └── ProtectedRoute.tsx           # Auth guard wrapper (NEW)
│   └── tickets/
│       ├── TicketCreateModal.tsx        # Create ticket form (NEW)
│       ├── TicketStatusBadge.tsx        # Status + priority badges (NEW)
│       ├── TicketAssignModal.tsx        # Assign to OPERATOR picker (NEW)
│       ├── TicketResolveModal.tsx       # Resolution note + time entry (NEW)
│       └── TicketCommentThread.tsx      # Comment list + add comment (NEW)
└── stores/
    ├── auth-store.ts                    # Zustand auth state (REPLACE use-auth.ts)
    └── ticket-store.ts                  # Zustand ticket list + selected ticket (NEW)
```

### Modified Files

```
admin-panel/src/
├── hooks/use-auth.ts                # Rewrite: read from auth-store, not CF cookie
├── lib/constants.ts                 # Remove CF_COOKIE_NAME, add AUTH_* constants
├── lib/api-client.ts                # Add 401 interceptor → auto refresh
├── routes/__root.tsx                # Add ProtectedRoute wrapper
└── routes/settings/
    └── users.tsx                    # Wire admin user CRUD (already has route shell)
```

### Auth Store (Zustand)

```typescript
// admin-panel/src/stores/auth-store.ts
export type AdminRole = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'AUDITOR' | 'HELPDESK'

export interface AdminUser {
  id: string
  email: string
  name: string
  role: AdminRole
  avatarUrl?: string
  mfaEnabled: boolean
}

interface AuthState {
  user: AdminUser | null;
  isAuthenticated: boolean;
  isMfaPending: boolean;        // logged in but MFA not yet verified
  isLoading: boolean;
}

interface AuthActions {
  login(email: string, password: string): Promise<LoginResult>;
  loginWithGoogle(code: string): Promise<LoginResult>;
  verifyMfa(code: string): Promise<void>;
  verifyBackupCode(code: string): Promise<void>;
  refresh(): Promise<boolean>;   // returns false if refresh token expired
  logout(): Promise<void>;
  checkAuth(): Promise<void>;    // called on app mount
}

type LoginResult =
  | { status: 'success' }
  | { status: 'mfa_required' }
  | { status: 'error'; message: string };
```

### Login Page Layout

```
panel.zyntapos.com/login
┌─────────────────────────────────────────────────────────┐
│                                                         │
│         [ZyntaPOS Logo]                                 │
│         ZyntaPOS Admin Panel                            │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Sign in to your account                         │  │
│  │                                                   │  │
│  │  Email address                                    │  │
│  │  ┌─────────────────────────────────────────────┐ │  │
│  │  │ dilanka@zyntapos.com                  │ │  │
│  │  └─────────────────────────────────────────────┘ │  │
│  │                                                   │  │
│  │  Password                                         │  │
│  │  ┌─────────────────────────────────────────────┐ │  │
│  │  │ ••••••••••••                            👁  │ │  │
│  │  └─────────────────────────────────────────────┘ │  │
│  │                                                   │  │
│  │  [ Sign in ]                                      │  │
│  │                                                   │  │
│  │  ──────────────── or ────────────────            │  │
│  │                                                   │  │
│  │  [ G  Continue with Google ]                      │  │
│  │                                                   │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│  © 2026 Zynta Solutions Pvt Ltd                         │
└─────────────────────────────────────────────────────────┘
```

### MFA Verification Screen (after password login)

```
┌──────────────────────────────────────────────┐
│  Two-Factor Authentication                   │
│                                              │
│  Enter the 6-digit code from your           │
│  authenticator app                           │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │   [ 6 ] [ digit ] [ code ] [ here ] │   │
│  └──────────────────────────────────────┘   │
│                                              │
│  [ Verify ]                                  │
│                                              │
│  Use a backup code instead →                 │
└──────────────────────────────────────────────┘
```

---

## 6. MFA Implementation Detail

**Library:** `otplib` (npm) — 4M weekly downloads, 8 years old, zero dependencies

### Setup Flow (ADMIN + OPERATOR enforced, FINANCE + AUDITOR optional)

```
1. Admin navigates to /settings/profile → "Enable Two-Factor Auth"
2. Frontend: POST /admin/auth/mfa/setup
   → Backend generates TOTP secret (20 bytes, base32 encoded)
   → Stores encrypted secret temporarily (pending confirmation)
   → Returns: { secret, otpauthUrl, qrDataUrl }
3. Frontend shows QR code (data URI, no 3rd party) + manual entry key
4. User scans with Google Authenticator / Authy / 1Password
5. User enters the 6-digit code to confirm setup
6. Frontend: POST /admin/auth/mfa/setup/confirm { code }
   → Backend: otplib.authenticator.verify({ token: code, secret })
   → On success: saves encrypted secret to DB, generates 8 backup codes
   → Returns: { backupCodes: string[] }
7. Frontend shows backup codes (shown ONCE — user must copy/download)
8. mfa_enabled = TRUE — enforced on next login
```

### TOTP Secret Encryption at Rest

```kotlin
// The TOTP secret is AES-256-GCM encrypted before storing in DB
// Key: derived from the same DatabaseKeyManager used for POS DB key
// Never stored in plaintext; decrypted in-memory only during verify
val encryptedSecret = encryptionManager.encrypt(totpSecret.toByteArray())
// Stored as: base64(iv) + ":" + base64(ciphertext)
```

### Backup Codes

- 8 codes generated on MFA setup
- Each code: 10 characters, `[A-Z2-9]` (ambiguous chars removed)
- Stored as bcrypt hash in `admin_mfa_backup_codes` table
- Each code single-use — `used_at` stamped on redemption
- Displayed once at setup; cannot be retrieved again (only regenerated)

---

## 7. Google SSO Implementation

**Approach:** OAuth 2.0 Authorization Code Flow with PKCE (frontend-initiated)

**No 3rd party auth service needed.** Uses Google's free OAuth 2.0 API directly.

### Setup (one-time, in Google Cloud Console)

```
1. Google Cloud Console → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID
   Application type: Web application
   Name: ZyntaPOS Admin Panel
   Authorized JavaScript origins: https://panel.zyntapos.com
   Authorized redirect URIs: https://panel.zyntapos.com/auth/google/callback
3. Copy Client ID (public — safe in frontend env var)
   Client Secret (backend only — stored in VPS .env)
```

### Flow

```
Frontend (PKCE):
1. Generate code_verifier (random 128-char string)
2. Generate code_challenge = base64url(SHA-256(code_verifier))
3. Redirect to:
   https://accounts.google.com/o/oauth2/v2/auth
     ?client_id=GOOGLE_CLIENT_ID
     &redirect_uri=https://panel.zyntapos.com/auth/google/callback
     &response_type=code
     &scope=openid email profile
     &code_challenge=<challenge>
     &code_challenge_method=S256
     &hd=zyntapos.com      ← restricts to company domain in Google UI

Backend (token exchange):
4. POST /admin/auth/google { code, code_verifier }
5. Ktor calls: POST https://oauth2.googleapis.com/token
   Returns: { id_token, access_token }
6. Verify id_token signature (Google public keys from JWKS endpoint)
7. Extract claims: { email, name, picture, sub, hd }
8. Validate: hd == "zyntapos.com"  ← domain restriction enforced server-side too
9. Upsert admin_users (google_sub = sub, email, name, avatarUrl = picture)
10. Issue ZyntaPOS JWT + refresh token → httpOnly cookies
11. Redirect to /
```

### Domain Restriction (Defense in Depth)

```
Layer 1: Google OAuth hd= parameter      → Google blocks non-domain accounts in chooser UI
Layer 2: Backend hd claim validation     → reject tokens where hd != "zyntapos.com"
Layer 3: Admin user is_active check      → even valid Google accounts can be deactivated
```

---

## 8. Cloudflare WAF Update

After implementing custom auth, update Cloudflare WAF rules to remove CF Access dependency
and tighten rules for the custom login endpoint:

### Updated WAF Rules (Cloudflare Dashboard → Security → WAF → Custom Rules)

| Rule Name | Expression | Action |
|-----------|-----------|--------|
| Block non-POS clients on API | `http.request.uri.path contains "/api" and not http.user_agent contains "ZyntaPOS"` | Block |
| Rate limit license heartbeat | `http.request.uri.path contains "/license/heartbeat"` | Rate limit: 10 req/min/IP |
| Block panel login non-LK IPs | `http.request.uri.path eq "/admin/auth/login" and ip.geoip.country ne "LK"` | JS Challenge |
| Block panel non-GET without cookie | `http.request.uri.path starts_with "/" and not http.request.uri.path starts_with "/admin/auth" and not http.cookie contains "zyntapos_access"` | — (handled in React) |

> **Note:** CF Access Application for `panel.zyntapos.com` should be set to **Bypass** (not deleted)
> so CF still terminates TLS and provides DDoS protection, but does not intercept with its own login.

---

## 9. Implementation Order (7 Days)

### Day 1 — Backend: Admin Auth API

**Goal:** Working login/logout/refresh API endpoints with bcrypt passwords and JWT issuance.

```
[ ] Create Flyway migration V5__admin_auth.sql
    - admin_users table
    - admin_sessions table
    - admin_mfa_backup_codes table

[ ] Implement AdminUserService.kt
    - createUser(email, name, role, password?) → hash with bcrypt (work factor 12)
    - authenticate(email, password) → verify bcrypt, check locked, update last_login
    - findByGoogleSub(sub) + upsertGoogleUser(...)
    - lockAccount(userId) / unlockAccount(userId)

[ ] Implement AdminTokenService.kt
    - issueAccessToken(user) → JWT HS256 (15 min), signed with ADMIN_JWT_SECRET
    - issueRefreshToken(userId, ip, userAgent) → UUID, hashed in DB, 7-day expiry
    - rotateRefreshToken(tokenHash) → revoke old, issue new (single-use)
    - revokeAllSessions(userId)

[ ] Implement AdminAuthRoutes.kt
    - POST /admin/auth/login (rate limited: 5/15min/IP)
    - POST /admin/auth/refresh (reads httpOnly cookie)
    - POST /admin/auth/logout (revokes session, clears cookies)
    - GET  /admin/auth/me (requires valid access JWT)

[ ] Add adminAuthPlugin to Ktor application (JWT verifier for ADMIN_JWT_SECRET)
[ ] Add admin route guard middleware (separate from existing POS JWT guard)
[ ] Seed first ADMIN user via environment variable on startup
    (ADMIN_BOOTSTRAP_EMAIL + ADMIN_BOOTSTRAP_PASSWORD — hashed and inserted if table empty)

[ ] Validation checklist:
    - curl POST /admin/auth/login → 200 + cookies set
    - curl POST /admin/auth/refresh → new access token issued
    - curl POST /admin/auth/login (6th attempt) → 429 or 423
    - curl GET /admin/auth/me without cookie → 401
    - curl GET /admin/auth/me with valid cookie → 200 + user JSON
```

---

### Day 2 — Frontend: Login UI + Auth State

**Goal:** React login page live, CF cookie auth replaced by custom JWT, protected routes working.

```
[ ] Create auth-store.ts (Zustand)
    - login(), logout(), checkAuth(), refresh() actions
    - user state, isAuthenticated, isMfaPending

[ ] Rewrite use-auth.ts
    - Remove CF_COOKIE_NAME reading
    - Read from auth-store (user comes from /admin/auth/me API on mount)

[ ] Create routes/login.tsx
    - ZyntaPOS branded layout (logo, card, footer)
    - LoginForm.tsx: email + password + show/hide toggle
    - Zod schema validation (email format, password min 8 chars)
    - Show inline error messages (generic — "Invalid credentials", never "wrong password")
    - Loading state during submission

[ ] Create ProtectedRoute.tsx
    - Wraps all routes except /login
    - Calls auth-store.checkAuth() on mount
    - Redirects to /login if not authenticated
    - Redirects to /login/mfa if isMfaPending

[ ] Update __root.tsx to use ProtectedRoute

[ ] Update api-client.ts
    - Add 401 interceptor → call auth-store.refresh()
    - On refresh failure → redirect to /login
    - Credentials: 'include' on all requests (sends httpOnly cookies)

[ ] Remove CF_COOKIE_NAME from constants.ts
    Add: AUTH_COOKIE_NAME, GOOGLE_CLIENT_ID, AUTH_MFA_PENDING_COOKIE_NAME

[ ] Validation checklist:
    - Navigate to panel.zyntapos.com → redirects to /login
    - Enter wrong password → inline error shown
    - Enter correct password → redirected to dashboard
    - Close browser → reopen → stays logged in (refresh token works)
    - Click logout → redirected to /login, cookies cleared
    - DevTools: cookies are httpOnly (not readable by JS)
```

---

### Day 3 — Security Hardening (App-Level CF Equivalent)

**Goal:** Brute-force protection, security headers, CSRF, input validation — all production-grade.

```
[ ] Brute-force lockout (already in AdminUserService, test thoroughly)
    - 5 failed attempts → 15-min lockout
    - Lockout message: "Account temporarily locked. Try again in X minutes."
    - Auth event logged: ADMIN_LOGIN_LOCKOUT (to existing audit log)
    - Unlock automatically when locked_until expires

[ ] Security headers plugin (Ktor)
    - X-Content-Type-Options: nosniff
    - X-Frame-Options: DENY
    - Referrer-Policy: strict-origin-when-cross-origin
    - Permissions-Policy: camera=(), microphone=(), geolocation=()
    - Content-Security-Policy (see Section 4)
    - Strict-Transport-Security: max-age=31536000; includeSubDomains (Caddy also sets this)

[ ] CSRF protection
    - Double-submit cookie pattern: backend sets CSRF_TOKEN cookie (not httpOnly)
    - Frontend reads CSRF_TOKEN and sends as X-CSRF-Token header
    - Backend validates header == cookie value on all state-changing requests
    - Excluded: GET, /admin/auth/login (pre-auth), /admin/auth/google

[ ] Input sanitization (backend)
    - Email: lowercase + trim on ingestion, regex validate
    - Password: max 128 chars (prevent bcrypt DoS — bcrypt silently truncates at 72 bytes)
    - All text fields: max length enforced via Ktor ValidationScope (existing plugin)

[ ] Redis rate limiting (extend existing rate limiter from TODO-009)
    - Sliding window counter per IP per endpoint
    - Distributed (works across multiple pod replicas)
    - Returns Retry-After header on 429

[ ] Auth event logging (extend existing audit log)
    New AuditEventType values:
    - ADMIN_LOGIN_SUCCESS
    - ADMIN_LOGIN_FAILURE
    - ADMIN_LOGIN_LOCKOUT
    - ADMIN_LOGOUT
    - ADMIN_TOKEN_REFRESHED
    - ADMIN_MFA_SETUP
    - ADMIN_MFA_VERIFIED
    - ADMIN_MFA_BACKUP_CODE_USED
    - ADMIN_USER_CREATED
    - ADMIN_USER_DEACTIVATED
    - ADMIN_SESSION_REVOKED

[ ] Validation checklist:
    - curl -I panel.zyntapos.com → response has all security headers
    - POST /admin/auth/login (5 times wrong) → 423 on 6th
    - POST state-changing endpoint without CSRF header → 403
    - POST /admin/auth/login with 300-char password → 400 (not 500)
    - Check audit log → ADMIN_LOGIN_FAILURE events visible
```

---

### Day 4 — MFA: TOTP Implementation

**Goal:** TOTP setup flow and enforcement on login for ADMIN accounts.

```
[ ] Add otplib to backend dependencies (or implement TOTP from RFC 6238 — it's only SHA-1 HMAC)
    Alternative: use Java's built-in HMAC + custom TOTP (no library needed):
    totp = HOTP(key=secret, counter=floor(unixTime/30))

[ ] Implement AdminMfaService.kt
    - generateSecret() → 20-byte random, base32 encoded
    - buildOtpauthUrl(email, secret) → otpauth://totp/ZyntaPOS:email?secret=...&issuer=ZyntaPOS
    - generateQrDataUri(otpauthUrl) → SVG/PNG QR code (use zxing-kotlin or server-side JS call)
    - encryptAndSave(userId, secret) → AES-256-GCM via EncryptionManager
    - verifyTotp(userId, code) → decrypt secret, check code + previous window (30s tolerance)
    - generateBackupCodes(userId) → 8 codes, bcrypt each, store in DB, return plaintext once
    - verifyBackupCode(userId, code) → constant-time bcrypt compare, mark used

[ ] Add MFA routes to AdminAuthRoutes.kt
    - POST /admin/auth/mfa/setup (requires valid access JWT, returns secret + QR URI)
    - POST /admin/auth/mfa/setup/confirm { code } (verifies TOTP, activates MFA, returns backup codes)
    - POST /admin/auth/mfa/verify { code } (requires pre-MFA JWT cookie)
    - POST /admin/auth/mfa/backup { code } (requires pre-MFA JWT cookie)

[ ] Pre-MFA JWT (intermediate state)
    - After password verify but before MFA verify, issue a short-lived pre-MFA JWT (5 min)
    - Claims: { sub, mfa: false, pre_mfa: true }
    - /admin/auth/mfa/verify only accepts pre-MFA JWT (not full access JWT)
    - On TOTP success: revoke pre-MFA JWT, issue full access JWT + refresh token

[ ] Enforce MFA for ADMIN and OPERATOR
    - On login: if user.role in (ADMIN, OPERATOR) and mfa_enabled == false:
        redirect frontend to /settings/profile?prompt=mfa_setup
    - If user.role in (ADMIN, OPERATOR) and mfa_enabled == true:
        always require MFA verify step (no bypass)
    - FINANCE and AUDITOR: MFA optional, but strongly recommended (shown as prompt, not enforced)

[ ] Frontend MFA components
    - MfaVerifyForm.tsx: 6-digit input (auto-submit on 6th digit), loading state
    - MfaSetupWizard.tsx: Step 1 QR code + Step 2 confirm code + Step 3 backup codes download
    - routes/login/mfa.tsx: MFA verify page (mid-login)

[ ] Validation checklist:
    - POST /admin/auth/mfa/setup → returns QR URI with correct otpauth format
    - Scan QR with Google Authenticator → generates correct 6-digit codes
    - POST /admin/auth/mfa/setup/confirm with wrong code → 400
    - POST /admin/auth/mfa/setup/confirm with correct code → 200 + 8 backup codes
    - Login flow: password → MFA screen → TOTP → dashboard
    - Use backup code → 200, mark used_at
    - Reuse same backup code → 401
    - Wrong TOTP 5 times → 429
```

---

### Day 5 — Google SSO

**Goal:** Google OAuth 2.0 PKCE flow, domain-restricted to @zyntapos.com.

```
[ ] Google Cloud Console setup (manual, one-time)
    - Create OAuth 2.0 Client ID (Web application)
    - Authorized origins: https://panel.zyntapos.com
    - Authorized redirect URIs: https://panel.zyntapos.com/auth/google/callback
    - Note Client ID (for frontend env) and Client Secret (for backend .env)

[ ] Add to VPS .env:
    GOOGLE_CLIENT_ID=...
    GOOGLE_CLIENT_SECRET=...
    GOOGLE_ALLOWED_DOMAIN=zyntapos.com

[ ] Backend: Google token exchange
    - POST /admin/auth/google { code, code_verifier, redirect_uri }
    - Exchange code for id_token via Google token endpoint
    - Fetch Google public keys from https://www.googleapis.com/oauth2/v3/certs (cache 1h)
    - Verify id_token signature (RS256, key from JWKS)
    - Validate claims: iss, aud == GOOGLE_CLIENT_ID, exp, hd == GOOGLE_ALLOWED_DOMAIN
    - Upsert admin_users (first SSO login creates AUDITOR role by default — most restrictive)
    - Issue ZyntaPOS JWT + refresh → cookies
    - If user.mfa_enabled → pre-MFA JWT instead

[ ] Frontend: GoogleSsoButton.tsx
    - Implements PKCE: generateCodeVerifier() + generateCodeChallenge()
    - Stores code_verifier in sessionStorage (never persists to localStorage)
    - Redirects to Google authorization URL with hd= + code_challenge
    - On callback: POST /admin/auth/google with code + code_verifier

[ ] Frontend: routes/auth/google/callback.tsx
    - Extracts ?code= from URL
    - Retrieves code_verifier from sessionStorage
    - Calls google login action from auth-store
    - On success → redirect to /
    - On failure (wrong domain, deactivated) → /login?error=sso_failed

[ ] Validation checklist:
    - Click "Continue with Google" → redirected to Google account chooser
    - Google chooser shows "Sign in to ZyntaPOS Admin Panel"
    - Login with non-company Google account → "Access denied" error
    - Login with @zyntapos.com account → auto-provisioned as AUDITOR, logged in
    - Second login with same Google account → existing user, no duplicate created
    - If ADMIN logs in via Google and MFA enabled → MFA verify step still required
    - Revoke access in admin panel → Google SSO login still blocked (is_active check)
```

---

### Day 6 — Admin User Management UI

**Goal:** Full user CRUD in the panel's /settings/users route.

```
[ ] Backend: /admin/users routes (GET, POST, PUT, DELETE, sessions)
    - List users: paginated, searchable by email/name, filterable by role
    - Create user: email + name + role + optional initial password
    - Update user: role change, activate/deactivate
    - Delete (deactivate, not hard delete): sets is_active = FALSE
    - List sessions: active refresh tokens for a user
    - Revoke sessions: marks all sessions revoked (forces re-login)

[ ] Frontend: /settings/users route (already has shell — wire to real API)
    - DataTable with columns: Name, Email, Role badge, MFA status, Last login, Status, Actions
    - Actions: Edit role | Deactivate | Revoke sessions
    - "Invite User" modal: email + name + role + initial password (or send invite email)
    - Role selector: ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK (with permission descriptions)
    - MFA status: "Enabled ✓" | "Not configured ⚠" (with enforce link for ADMIN + OPERATOR)

[ ] Frontend: /settings/profile route
    - View own profile (email, name, role)
    - Change password form (current + new + confirm)
    - MFA section: setup wizard if not enabled, disable option if enabled
    - Active sessions list: device + IP + last used; "Revoke" button per session

[ ] Validation checklist:
    - ADMIN can see user list and all actions
    - OPERATOR, FINANCE and AUDITOR cannot access /settings/users (403 on API, redirect on frontend)
    - Create user → appears in list, can log in with initial password
    - Deactivate user → login returns 401, cannot SSO in either
    - Revoke sessions → next request with old refresh token returns 401
    - ADMIN can change own password → old password no longer works
```

---

### Day 7 — Testing, CF Migration & CI Update

**Goal:** Tests passing, CF Access bypassed cleanly, CI pipeline includes auth.

```
[ ] Backend unit tests (Kotlin Test + Mockative)
    - AdminUserService: authenticate success, wrong password, lockout, unlock
    - AdminTokenService: issue access token, rotate refresh, revoke all
    - AdminMfaService: verify TOTP, verify backup code, reuse prevention
    - Rate limiter: 5th attempt allowed, 6th blocked

[ ] Backend integration tests (in-memory DB)
    - Full login flow: POST /login → verify cookies → GET /me → POST /logout
    - MFA flow: password → pre-MFA JWT → TOTP → full JWT
    - Google SSO: mock Google token endpoint → upsert user → issue JWT
    - Token rotation: use refresh → old refresh revoked → new refresh works
    - Concurrent refresh: only first succeeds, second gets 401

[ ] Frontend tests (Vitest + Testing Library)
    - LoginForm: renders, shows validation errors, calls login action
    - MfaVerifyForm: 6-digit input, auto-submit, error state
    - ProtectedRoute: redirects to /login when not authenticated
    - auth-store: login action sets user, logout clears state

[ ] Cloudflare migration
    - Set CF Access Application for panel.zyntapos.com to "Bypass" mode
      (keeps CF DDoS + TLS; removes CF login interception)
    - Remove CF_COOKIE_NAME constant and all references from frontend
    - Verify panel.zyntapos.com no longer shows CF Access login page
    - Verify custom /login page loads directly

[ ] CI pipeline update (.github/workflows/ci-gate.yml)
    - Add admin auth backend tests to the test matrix
    - Add ADMIN_JWT_SECRET to GitHub Secrets
    - Add GOOGLE_CLIENT_ID to GitHub Secrets (public, used in Vite build)
    - Add GOOGLE_CLIENT_SECRET to GitHub Secrets (backend only)

[ ] Environment variables documentation
    New variables to add to local.properties.template and VPS .env:
    - ADMIN_JWT_SECRET (min 256-bit random: openssl rand -hex 32)
    - ADMIN_BOOTSTRAP_EMAIL (first ADMIN email)
    - ADMIN_BOOTSTRAP_PASSWORD (first ADMIN password — change immediately after setup)
    - GOOGLE_CLIENT_ID
    - GOOGLE_CLIENT_SECRET
    - GOOGLE_ALLOWED_DOMAIN (= "zyntapos.com")

[ ] Security review checklist
    - [ ] No secrets in frontend bundle (GOOGLE_CLIENT_ID is safe; CLIENT_SECRET is not)
    - [ ] TOTP secrets encrypted in DB (not plaintext)
    - [ ] Refresh tokens stored as hashes only (never plaintext in DB)
    - [ ] All auth errors return generic messages (no email enumeration)
    - [ ] httpOnly, Secure, SameSite=Strict on all auth cookies
    - [ ] bcrypt work factor = 12 (verified in AdminUserService)
    - [ ] Password max 128 chars enforced (bcrypt DoS prevention)
    - [ ] Google hd= enforced both in OAuth URL and backend token validation
    - [ ] CSRF double-submit cookie on all state-changing endpoints
```

---

## 10. Validation Checklist (Full)

### Security

- [ ] Custom login page loads at `panel.zyntapos.com/login` (ZyntaPOS branded, not CF)
- [ ] CF Access login page no longer appears (set to Bypass mode)
- [ ] All auth cookies: httpOnly=true, Secure=true, SameSite=Strict (DevTools → Application → Cookies)
- [ ] `curl -I https://panel.zyntapos.com` → response includes all 6 security headers
- [ ] 5 wrong password attempts → 15-minute lockout (423 response)
- [ ] CSRF: state-changing request without X-CSRF-Token header → 403
- [ ] Password > 128 chars → 400 (not 500, not silent truncation)
- [ ] Rate limit: 6th login attempt within 15 min → 429 with Retry-After header
- [ ] All auth events visible in audit log viewer at `/audit`
- [ ] TOTP secret: not visible in DB in plaintext (verify with `SELECT mfa_secret FROM admin_users`)
- [ ] Refresh tokens: not stored in plaintext in DB (verify `SELECT token_hash FROM admin_sessions`)
- [ ] Cloudflare DDoS + WAF still active (orange-cloud proxy status in CF dashboard)

### Auth Flows

- [ ] Password login → dashboard (AUDITOR role — most restrictive, fewest nav items visible)
- [ ] Password login → MFA verify screen → dashboard (ADMIN / OPERATOR with MFA enabled)
- [ ] Password login → force MFA setup (ADMIN / OPERATOR without MFA → /settings/profile?prompt=mfa_setup)
- [ ] Google SSO → dashboard (auto-provisioned as AUDITOR)
- [ ] Google SSO with non-company email → "Access denied" error on /login
- [ ] Expired access token → auto-refreshed silently (user stays logged in)
- [ ] Expired refresh token → redirected to /login
- [ ] Logout → both cookies cleared → /login → old refresh token rejected
- [ ] Close browser → reopen → still logged in (refresh token in persistent cookie)

### User Management

- [ ] ADMIN can create user with any role → user can log in
- [ ] ADMIN can change another user's role → takes effect on next login
- [ ] ADMIN can deactivate user → login returns 401
- [ ] ADMIN can revoke sessions → user kicked out immediately (next request fails)
- [ ] OPERATOR cannot access `/admin/users` API (403)
- [ ] FINANCE cannot access `/admin/users` API (403)
- [ ] AUDITOR cannot access `/admin/users` API (403)
- [ ] OPERATOR can access remote diagnostics → FINANCE cannot (403)
- [ ] FINANCE can access financial reports → OPERATOR cannot (403)
- [ ] AUDITOR can read audit logs → OPERATOR cannot (403)
- [ ] HELPDESK cannot access `/admin/users` API (403)
- [ ] HELPDESK cannot call `POST /admin/tickets/{id}/resolve` → 403
- [ ] HELPDESK cannot run remote diagnostics → 403
- [ ] HELPDESK cannot access financial reports → 403

### HELPDESK & Support Tickets

- [ ] HELPDESK login → support dashboard shown (ticket KPIs: open count, SLA breach count, assigned to me)
- [ ] HELPDESK can create ticket → appears in list with `TKT-YYYY-NNNNNN` ticket number
- [ ] HELPDESK assigns ticket to OPERATOR → OPERATOR sees it in their queue; status becomes ASSIGNED
- [ ] OPERATOR sets status to IN_PROGRESS → HELPDESK sees the change in ticket timeline
- [ ] OPERATOR sets PENDING_CUSTOMER → HELPDESK notified; can follow up with customer
- [ ] OPERATOR marks RESOLVED (with resolution_note + time_spent_min) → HELPDESK can now mark CLOSED
- [ ] HELPDESK marks CLOSED → ticket status = CLOSED, no further status transitions allowed
- [ ] HELPDESK cannot mark CLOSED before OPERATOR resolves → 422 (invalid status transition)
- [ ] HELPDESK can view store health (read-only) → no sync/config action buttons visible
- [ ] HELPDESK can view license details (read-only) → no create/revoke buttons visible
- [ ] HELPDESK can view diagnostic log results attached to tickets (read-only)
- [ ] HELPDESK can add internal comment (is_internal=true) → OPERATOR sees it; customer-facing view hides it
- [ ] HELPDESK can add customer-visible comment (is_internal=false)
- [ ] SLA breach flag: `sla_breached = TRUE` when `sla_due_at` passes without RESOLVED status
- [ ] Diagnostic log attachment: OPERATOR attaches log to ticket → HELPDESK can view it on ticket detail

### MFA

- [ ] Google Authenticator: scan QR → generates 6-digit codes
- [ ] TOTP code verifies successfully (within 30s window)
- [ ] Old TOTP code (> 60s) rejected
- [ ] 8 backup codes shown once at setup
- [ ] Each backup code: single use only
- [ ] Reused backup code: 401
- [ ] Backup code via `/admin/auth/mfa/backup` (not `/mfa/verify`) works mid-login

### Google SSO

- [ ] Google chooser shows company domain hint (`hd=zyntapos.com`)
- [ ] Login with `@gmail.com` → blocked (both Google-side and backend-side)
- [ ] Login with `@zyntapos.com` → success, auto-provisioned
- [ ] Second login with same Google account → no duplicate user row
- [ ] Deactivated Google user cannot log in via SSO

---

## 11. Dependencies

| Dependency | Type | Where | Purpose |
|------------|------|-------|---------|
| `bcrypt` (JVM) | Backend | `build.gradle.kts` | Password hashing (work factor 12) |
| `jose4j` or `java-jwt` | Backend | `build.gradle.kts` | JWT issuance + verification |
| `zxing-core` | Backend | `build.gradle.kts` | QR code generation for TOTP setup |
| `otplib` (npm) | — | Not needed | TOTP implemented via JVM HMAC (RFC 6238) |
| `@noble/hashes` | Frontend | `package.json` | SHA-256 for PKCE code_challenge |
| Google OAuth 2.0 | External API | — | Free, no library — raw fetch calls |

> **No paid services required.** No Auth0, Clerk, Firebase Auth, or any auth SaaS.
> All auth logic is self-hosted in the existing Ktor backend.

---

## 12. Files Changed / Created

### Backend (new)

```
backend/api/src/main/kotlin/com/zyntasolutions/api/
├── routes/AdminAuthRoutes.kt         (NEW)
├── routes/AdminUserRoutes.kt         (NEW)
├── routes/AdminTicketRoutes.kt       (NEW — HELPDESK extension)
├── service/AdminUserService.kt       (NEW)
├── service/AdminTokenService.kt      (NEW)
├── service/AdminMfaService.kt        (NEW)
├── service/GoogleAuthService.kt      (NEW)
├── service/AdminTicketService.kt     (NEW — HELPDESK extension)
├── model/AdminUser.kt                (NEW)
└── model/SupportTicket.kt            (NEW — HELPDESK extension)

backend/api/src/main/resources/db/migration/
├── V5__admin_auth.sql                (NEW)
└── V6__helpdesk_tickets.sql          (NEW — HELPDESK extension)
```

### Frontend (new)

```
admin-panel/src/
├── routes/login.tsx                  (NEW)
├── routes/auth/google/callback.tsx   (NEW)
├── routes/tickets/
│   ├── index.tsx                     (NEW — HELPDESK extension)
│   └── $ticketId.tsx                 (NEW — HELPDESK extension)
├── components/auth/
│   ├── LoginForm.tsx                 (NEW)
│   ├── GoogleSsoButton.tsx           (NEW)
│   ├── MfaVerifyForm.tsx             (NEW)
│   ├── MfaSetupWizard.tsx            (NEW)
│   └── ProtectedRoute.tsx            (NEW)
├── components/tickets/
│   ├── TicketCreateModal.tsx         (NEW — HELPDESK extension)
│   ├── TicketStatusBadge.tsx         (NEW — HELPDESK extension)
│   ├── TicketAssignModal.tsx         (NEW — HELPDESK extension)
│   ├── TicketResolveModal.tsx        (NEW — HELPDESK extension)
│   └── TicketCommentThread.tsx       (NEW — HELPDESK extension)
└── stores/
    └── ticket-store.ts               (NEW — HELPDESK extension)
```

### Frontend (modified)

```
admin-panel/src/
├── hooks/use-auth.ts                 (REWRITE — remove CF cookie, use auth-store)
├── stores/auth-store.ts              (REWRITE — add login/logout/refresh/checkAuth + HELPDESK role)
├── lib/permissions.ts                (REWRITE — 37 atomic permissions, 5 roles including HELPDESK)
├── lib/constants.ts                  (MODIFY — remove CF_COOKIE_NAME, add auth constants)
├── lib/api-client.ts                 (MODIFY — add 401 interceptor + credential: include)
├── routes/__root.tsx                 (MODIFY — add ProtectedRoute wrapper)
└── routes/settings/users.tsx         (MODIFY — wire to real API, add HELPDESK to role selector)
```

---

## 13. Non-Goals (Out of Scope for This TODO)

- Email-based invite system (users added with initial password; email invite is Phase 3)
- Password reset via email (admin must reset via CLI or another ADMIN — Phase 3)
- SMS-based MFA (TOTP only — SMS requires Twilio and adds cost)
- WebAuthn / hardware keys (Phase 3 enterprise)
- Session activity heatmap (Phase 3)
- Multi-tenant panel access (panel is for Zynta Solutions internal use only)
