# TODO-007a — React Admin Panel (panel.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P0 (HIGH)
**Status:** ✅ ~98% IMPLEMENTED — All frontend modules complete: auth (email/password + TOTP MFA; Google SSO removed 2026-03-14), dashboard, licenses, stores, users, audit, sync (with Conflicts + Dead Letters tabs), health, config, reports, alerts, settings, tickets (full CRUD + comments + SLA + 6 components + 2 routes), **master-products** (global product catalog list + detail with store assignments, registered in routeTree.gen.ts — 2026-03-19), keyboard shortcuts, expanded test suite (TicketStatusBadge + ConfirmDialog tests), Playwright E2E scaffold. Remaining: VPS deployment, CF Access bypass (external/infrastructure)
**Effort:** ~15 working days (3 weeks, 1 developer)
**Related:** TODO-007 (infrastructure), TODO-006 (remote diagnostics), TODO-010 (security monitoring), TODO-007f (CF + Custom Auth)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-19

---

## 1. Overview

Build an internal admin panel for centralized management of all ZyntaPOS deployments. The panel is a standalone React SPA served at `panel.zyntapos.com`, protected by Cloudflare (DDoS/WAF/TLS) with a custom ZyntaPOS-branded login system (see TODO-007f). It communicates with the existing Ktor backend services (`api.zyntapos.com`, `license.zyntapos.com`, `sync.zyntapos.com`) and adds new admin-specific API endpoints where needed.

> **Implementation status (2026-03-09):** The `admin-panel/` project is fully built. Implemented: authentication (login, MFA, Google SSO, auth-store, 5-role RBAC with proper permission filtering in Sidebar), dashboard (KPI cards + 4 charts), license management (list + detail), store management, user management, audit log, sync monitoring (+ Conflicts tab + Dead Letters tab with retry/discard), system health, remote configuration, reports, alerts, settings (profile, MFA, timezone), **support tickets** (full module: DB migration V5, AdminTicketService, AdminTicketRoutes, types, API hooks, TicketStatusBadge, TicketTable, TicketCreateModal, TicketAssignModal, TicketResolveModal, TicketCommentThread, list route, detail route), keyboard shortcuts (`use-keyboard.ts` registered in AppShell), expanded Vitest test suite (TicketStatusBadge, ConfirmDialog), Playwright E2E config + smoke spec, layout shell, Dockerfile, nginx.conf, docker-compose panel service, and CI pipeline (`ci-admin-panel.yml`). Remaining: VPS deployment, CF Access bypass config (external/infrastructure actions only).

This is **not** part of the KMM app. It is a separate web project living at `admin-panel/` in the monorepo root, built with Vite, and deployed as a Docker container served by Caddy on the VPS.

### Goals

- Centralized license management (create, revoke, extend, view all licenses)
- Multi-store visibility (health, sync status, KPIs across all deployments)
- User and role management for store administrators
- Audit log viewer with search, filter, and export
- Remote configuration push (tax rates, feature flags, pricing tiers)
- System health monitoring and alerting
- Cross-store reporting with CSV/PDF export

### Non-Goals (deferred)

- Remote diagnostic WebSocket relay (TODO-006 — depends on this panel existing)
- Email-based invite system / password reset via email (Phase 3)
- WebAuthn / hardware keys (Phase 3 enterprise)
- Customer-facing self-service portal
- i18n (English only for internal tool)

> **Note:** Helpdesk ticket system was originally deferred to Phase 3 but was implemented as part of TODO-007f (HELPDESK role + `support_tickets` DB table + ticket routes). See Section 10 for the Support Tickets feature and validation checklist.

---

## 2. Technology Stack

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | **React 19** | 19.x | Latest stable, concurrent features, server components ready |
| Language | **TypeScript** | 5.x | Type safety across the entire frontend |
| Build | **Vite** | 6.x | Sub-second HMR, optimized production builds, ESM-native |
| Routing | **TanStack Router** | 1.x | Type-safe routes, file-based conventions, built-in loader/action pattern |
| Server State | **TanStack Query** | 5.x | Automatic caching, background refetching, optimistic updates, devtools |
| Client State | **Zustand** | 5.x | Minimal boilerplate, TypeScript-first, no provider wrapper needed |
| Styling | **Tailwind CSS 4** | 4.x | Utility-first, consistent with marketing site and KMM design tokens |
| Components | **shadcn/ui** | latest | Copy-paste Radix primitives, fully customizable, accessible by default |
| Charts | **Recharts** | 2.x | Composable React chart components, good TypeScript support |
| Forms | **React Hook Form** | 7.x | Performant uncontrolled forms, minimal re-renders |
| Validation | **Zod** | 3.x | Schema-first validation, integrates with RHF via `@hookform/resolvers` |
| HTTP | **ky** | 1.x | Tiny fetch wrapper with retry, timeout, hooks — lighter than Axios |
| Date/Time | **date-fns** | 4.x | Tree-shakable, immutable, no global state (unlike Moment/Day.js) |
| PDF Export | **@react-pdf/renderer** | 4.x | React component-based PDF generation |
| CSV Export | **papaparse** | 5.x | Fast CSV serialization with streaming support |
| Auth | **Custom JWT (HS256)** | N/A | ZyntaPOS-branded login + custom JWT + MFA + Google SSO (TODO-007f). CF Access replaced. |
| Testing | **Vitest** + **Testing Library** | latest | Vite-native test runner, component testing with RTL |
| E2E Testing | **Playwright** | latest | Cross-browser E2E, CI-friendly |
| Linting | **ESLint 9** + **Prettier** | latest | Flat config, consistent formatting |
| Container | **Docker** (nginx:alpine) | latest | Static SPA served by nginx, multi-stage build |

---

## 3. Project Location & Structure

**Root:** `admin-panel/` (top-level in ZyntaPOS-KMM monorepo)

```
admin-panel/
├── Dockerfile                          # Multi-stage build: node → nginx
├── nginx.conf                          # SPA fallback, security headers, gzip
├── package.json                        # Dependencies + scripts
├── tsconfig.json                       # TypeScript config (strict mode)
├── tsconfig.node.json                  # Vite/Node TypeScript config
├── vite.config.ts                      # Vite config (proxy, env, aliases)
├── tailwind.config.ts                  # Tailwind theme tokens (brand colors)
├── postcss.config.js                   # PostCSS + Tailwind
├── .env.example                        # Environment variable template
├── .nvmrc                              # Node 22
├── .eslintrc.cjs                       # ESLint flat config
├── .prettierrc                         # Prettier config
├── index.html                          # SPA entry point
├── public/
│   ├── favicon.svg                     # ZyntaPOS logomark
│   └── logo.svg                        # Full wordmark
├── src/
│   ├── main.tsx                        # React root, providers, router
│   ├── App.tsx                         # Router outlet, global layout
│   ├── globals.css                     # Tailwind directives, font-face
│   ├── lib/
│   │   ├── api-client.ts              # ky instance with auth interceptor
│   │   ├── query-client.ts            # TanStack Query client config
│   │   ├── constants.ts               # API base URLs, pagination defaults
│   │   ├── utils.ts                   # cn(), formatCurrency(), etc.
│   │   └── export.ts                  # CSV/PDF export helpers
│   ├── hooks/
│   │   ├── use-auth.ts                # Auth-store reader + 37-permission hasPermission() helper ✅
│   │   ├── use-debounce.ts            # Debounced value hook ✅
│   │   ├── use-timezone.ts            # Timezone-aware date formatting ✅
│   │   ├── use-media-query.ts         # Responsive breakpoint hook ✅
│   │   └── use-keyboard.ts            # Keyboard shortcuts (/, Escape, Ctrl+K) — ⬜ NOT IMPLEMENTED
│   ├── stores/
│   │   ├── auth-store.ts              # Zustand: AdminUser | null, isLoading, setUser/clearUser ✅
│   │   ├── timezone-store.ts          # Persisted display timezone preference ✅
│   │   ├── ui-store.ts                # Sidebar collapsed, theme, toasts ✅
│   │   └── filter-store.ts            # Persisted filter state per page
│   ├── types/
│   │   ├── license.ts                 # License, Device, LicenseStatus types ✅
│   │   ├── store.ts                   # Store, StoreHealth, StoreConfig types ✅
│   │   ├── user.ts                    # AdminUser, Role, Permission types ✅
│   │   ├── audit.ts                   # AuditEntry, AuditFilter types ✅
│   │   ├── sync.ts                    # SyncStatus, SyncOperation types ✅
│   │   ├── metrics.ts                 # KPI, ChartData, TimeSeriesPoint types ✅
│   │   ├── config.ts                  # RemoteConfig, FeatureFlag, TaxRate types ✅
│   │   ├── alert.ts                   # Alert, AlertRule, NotificationChannel types ✅
│   │   ├── api.ts                     # PagedResponse<T>, ErrorResponse, etc. ✅
│   │   ├── master-product.ts          # MasterProduct, StoreProductAssignment, CreateMasterProductRequest types ✅
│   │   └── ticket.ts                  # Ticket, TicketComment, TicketStatus types — ⬜ NOT IMPLEMENTED
│   ├── api/                           # TanStack Query hooks per domain
│   │   ├── auth.ts                    # useCurrentUser, useAdminLogin, useAdminMfaVerify, useAdminMfaSetup, useChangePassword, useListSessions ✅
│   │   ├── licenses.ts                # useLicenses, useCreateLicense, etc.
│   │   ├── stores.ts                  # useStores, useStoreHealth, etc.
│   │   ├── users.ts                   # useAdminUsers, useCreateUser, useUpdateUser, useDeactivateUser, useRevokeSessions ✅
│   │   ├── audit.ts                   # useAuditLogs, useAuditExport, etc.
│   │   ├── sync.ts                    # useSyncStatus, useForceSync, etc.
│   │   ├── metrics.ts                 # useDashboardKPIs, useSalesChart, etc.
│   │   ├── config.ts                  # useRemoteConfig, usePushConfig, etc.
│   │   ├── alerts.ts                  # useAlerts, useAlertRules, etc.
│   │   ├── master-products.ts         # useMasterProducts, useMasterProduct, useCreateMasterProduct, useDeleteMasterProduct, useAssignToStore, useRemoveFromStore ✅
│   │   └── health.ts                  # useSystemHealth, useServiceStatus, etc.
│   ├── components/
│   │   ├── ui/                        # shadcn/ui primitives (button, card, dialog, etc.)
│   │   ├── layout/
│   │   │   ├── AppShell.tsx           # Sidebar + header + main content area
│   │   │   ├── Sidebar.tsx            # Collapsible nav sidebar with route groups
│   │   │   ├── Header.tsx             # Top bar: breadcrumbs, user info, notifications
│   │   │   ├── Breadcrumbs.tsx        # Auto-generated from route hierarchy
│   │   │   └── UserMenu.tsx           # CF Access user display, sign out
│   │   ├── shared/
│   │   │   ├── DataTable.tsx          # Generic paginated table with sort/filter
│   │   │   ├── StatusBadge.tsx        # Color-coded status pill (ACTIVE, EXPIRED, etc.)
│   │   │   ├── KpiCard.tsx            # Stat card with icon, value, trend arrow
│   │   │   ├── DateRangePicker.tsx    # Date range selector for reports/filters
│   │   │   ├── SearchInput.tsx        # Debounced search with clear button
│   │   │   ├── ConfirmDialog.tsx      # Reusable confirmation modal
│   │   │   ├── EmptyState.tsx         # Empty data placeholder
│   │   │   ├── LoadingState.tsx       # Skeleton/spinner states
│   │   │   ├── ErrorBoundary.tsx      # Error boundary with retry
│   │   │   └── ExportButton.tsx       # CSV/PDF export dropdown
│   │   ├── charts/
│   │   │   ├── SalesChart.tsx         # Line/bar chart for revenue over time
│   │   │   ├── StoreComparisonChart.tsx  # Multi-store bar comparison
│   │   │   ├── SyncHealthChart.tsx    # Sync queue depth / latency over time
│   │   │   ├── LicenseDistribution.tsx   # Pie chart: edition breakdown
│   │   │   └── UptimeChart.tsx        # Uptime percentage timeline
│   │   ├── licenses/
│   │   │   ├── LicenseTable.tsx       # License list with inline actions
│   │   │   ├── LicenseCreateForm.tsx  # Create new license dialog
│   │   │   ├── LicenseDetailCard.tsx  # License detail view with devices
│   │   │   ├── LicenseExtendDialog.tsx   # Extend expiry dialog
│   │   │   └── DeviceList.tsx         # Registered devices per license
│   │   ├── stores/
│   │   │   ├── StoreTable.tsx         # Store list with health indicators
│   │   │   ├── StoreDetailPanel.tsx   # Store detail: config, devices, sync
│   │   │   ├── StoreConfigForm.tsx    # Edit store-level configuration
│   │   │   └── StoreHealthCard.tsx    # Health metrics card per store
│   │   ├── auth/
│   │   │   ├── LoginForm.tsx          # Email/password form with show/hide ✅
│   │   │   ├── MfaVerifyForm.tsx      # 6-digit TOTP input step ✅
│   │   │   └── ProtectedRoute.tsx     # Redirects to /login when not authenticated ✅
│   │   ├── users/
│   │   │   ├── UserTable.tsx          # Admin user list + MFA badge + revoke sessions action ✅
│   │   │   ├── UserCreateForm.tsx     # Create/edit admin user dialog ✅
│   │   │   └── RoleAssignment.tsx     # Role picker (ADMIN/OPERATOR/FINANCE/AUDITOR/HELPDESK) ✅
│   │   ├── tickets/                   # ⬜ NOT IMPLEMENTED — directory does not exist
│   │   │   ├── TicketTable.tsx        # ⬜ Support ticket list with status/priority badges
│   │   │   ├── TicketCreateModal.tsx  # ⬜ Create ticket dialog
│   │   │   ├── TicketStatusBadge.tsx  # ⬜ OPEN/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED badge
│   │   │   ├── TicketAssignModal.tsx  # ⬜ Assign to OPERATOR dialog
│   │   │   ├── TicketResolveModal.tsx # ⬜ Resolve with notes + time_spent_min
│   │   │   └── TicketCommentThread.tsx # ⬜ Comment thread with internal/external toggle
│   │   ├── audit/
│   │   │   ├── AuditLogTable.tsx      # Paginated audit log with filters ✅
│   │   │   ├── AuditFilterPanel.tsx   # Filter sidebar (date, event type, user, store) ✅
│   │   │   └── AuditDetailModal.tsx   # Full detail view of single entry ✅
│   │   ├── sync/
│   │   │   ├── SyncDashboard.tsx      # Per-store sync status grid ✅
│   │   │   ├── SyncQueueView.tsx      # Pending operations queue view ✅
│   │   │   └── ForceSyncButton.tsx    # Force re-sync action button ✅
│   │   ├── config/
│   │   │   ├── ConfigEditor.tsx       # JSON/form editor for remote config ✅
│   │   │   ├── FeatureFlagTable.tsx   # Feature flag toggle table ✅
│   │   │   └── TaxRateEditor.tsx      # Tax rate CRUD form ✅
│   │   └── alerts/                    # Alert UI implemented inline in routes/alerts/index.tsx ✅
│   │       ├── AlertRuleTable.tsx     # (inline) Alert rule list with enable/disable
│   │       ├── AlertRuleForm.tsx      # (inline) Create/edit alert rule
│   │       ├── AlertHistory.tsx       # (inline) Alert firing history
│   │       └── NotificationChannelForm.tsx  # (inline) Slack/email/webhook config
│   ├── routes/                        # TanStack Router file-based routes
│   │   ├── __root.tsx                 # Root layout (AppShell) — auth hydration + bootstrap redirect ✅
│   │   ├── login.tsx                  # Custom login page (email/password + MFA + Google SSO) ✅
│   │   ├── setup/
│   │   │   └── index.tsx              # First-run admin bootstrap (/setup) ✅
│   │   ├── index.tsx                  # Dashboard (/) ✅
│   │   ├── licenses/
│   │   │   ├── index.tsx              # License list (/licenses) ✅
│   │   │   └── $licenseKey.tsx        # License detail (/licenses/:key) ✅
│   │   ├── stores/
│   │   │   ├── index.tsx              # Store list (/stores) ✅
│   │   │   └── $storeId.tsx           # Store detail (/stores/:id) ✅
│   │   ├── users/
│   │   │   └── index.tsx              # Admin user management (/users) ✅
│   │   ├── tickets/                   # ⬜ NOT IMPLEMENTED — no files exist
│   │   │   ├── index.tsx              # Support ticket list (/tickets) ⬜
│   │   │   └── $ticketId.tsx          # Ticket detail (/tickets/:id) ⬜
│   │   ├── master-products/
│   │   │   ├── index.tsx              # Global product catalog list (/master-products) ✅
│   │   │   └── $masterProductId.tsx   # Product detail + store assignments (/master-products/:id) ✅
│   │   ├── audit/
│   │   │   └── index.tsx              # Audit log (/audit) ✅
│   │   ├── sync/
│   │   │   └── index.tsx              # Sync monitoring (/sync) ✅
│   │   ├── config/
│   │   │   └── index.tsx              # Remote configuration (/config) ✅
│   │   ├── reports/
│   │   │   └── index.tsx              # Cross-store reports (/reports) ✅
│   │   ├── health/
│   │   │   ├── index.tsx              # System health (/health) ✅
│   │   │   └── $storeId.tsx           # Store-specific health (/health/:storeId) ✅
│   │   ├── alerts/
│   │   │   └── index.tsx              # Alert management (/alerts) ✅
│   │   └── settings/
│   │       ├── index.tsx              # Preferences (timezone) ✅
│   │       ├── profile.tsx            # Change password + active sessions ✅
│   │       └── mfa.tsx                # MFA setup/disable (TOTP + backup codes) ✅
│   └── test/
│       ├── setup.ts                   # Vitest setup (RTL matchers) ✅
│       ├── mocks/
│       │   ├── handlers.ts            # MSW request handlers ✅
│       │   ├── browser.ts             # MSW browser setup ✅
│       │   └── server.ts              # MSW server setup ✅
│       ├── components/
│       │   ├── KpiCard.test.tsx       # ✅
│       │   ├── SearchInput.test.tsx   # ✅
│       │   └── StatusBadge.test.tsx   # ✅
│       ├── hooks/
│       │   └── use-auth.test.ts       # ✅
│       ├── lib/
│       │   └── utils.test.ts          # ✅
│       └── utils.tsx                  # Render helpers with providers ✅
```

---

## 4. Authentication & Authorization

> **✅ IMPLEMENTED** — Full auth system is live. See TODO-007f for the complete architecture, DB schema, and implementation details.

### 4.1 CF + Custom Hybrid Auth (Implemented)

Cloudflare handles network-level security (DDoS, WAF, TLS) while a custom ZyntaPOS-branded login system manages identity and access management. The original plan of using CF Access for identity was superseded by TODO-007f.

**Login flow:**
1. User navigates to `panel.zyntapos.com/login` — ZyntaPOS-branded login page (not CF Access)
2. Email + password → `POST /admin/auth/login` → backend issues HS256 JWT (15-min access) + opaque refresh token (7-day, single-use rotation)
3. Both tokens stored as `httpOnly; Secure; SameSite=Strict` cookies
4. MFA required? → `POST /admin/auth/mfa/verify` with TOTP code from authenticator app
5. Google SSO? → `GET /admin/auth/google` → server-side redirect flow, `@zyntapos.com` domain enforced
6. All API calls use `credentials: 'include'` — cookies sent automatically, no `Authorization` header needed

**Token architecture:** Access token (HS256 JWT, 15 min, payload contains `sub`, `email`, `name`, `role`, `mfa`) + opaque refresh token (7-day, SHA-256-hashed in `admin_sessions` table, single-use rotation).

### 4.2 Auth State Management (Implemented)

Auth state is managed by Zustand (`src/stores/auth-store.ts`). On app load, `GET /admin/auth/me` is called to hydrate the user from the access token. On 401, the API client automatically calls `POST /admin/auth/refresh` and retries. On refresh failure, the user is redirected to `/login`.

```
App loads → GET /admin/auth/me
  ├── 200 → setUser(adminUser), show protected content
  ├── 401 → POST /admin/auth/refresh → success → retry /me
  └── 401 (no refresh) → redirect to /login
```

### 4.3 Role-Based Access Control — 5 Roles, 37 Permissions (Implemented)

| Role | Description | Key Permissions |
|------|-------------|-----------------|
| `ADMIN` | Full access to all panel features | All 37 permissions |
| `OPERATOR` | Store ops, sync, diagnostics, support tickets | `dashboard:ops`, `store:*`, `sync:*`, `diagnostics:*`, `tickets:*`, `alerts:read` |
| `FINANCE` | Financial reports and license exports | `dashboard:financial`, `license:read/export`, `reports:financial/export` |
| `AUDITOR` | Read-only compliance view | `license:read`, `reports:read`, `audit:read/export` |
| `HELPDESK` | Customer-facing support and ticket management | `dashboard:support`, `store:read`, `diagnostics:read`, `tickets:*`, `reports:support` |

Permission strings follow a `resource:action` convention (e.g., `license:revoke`, `audit:export`, `tickets:resolve`). The full 37-permission map is defined in `src/hooks/use-auth.ts`.

Roles are enforced **client-side** (sidebar items, action buttons hidden via `hasPermission()`) and **server-side** (Ktor middleware returns 403 for unauthorized calls).

```typescript
// src/types/user.ts — actual implementation
export type AdminRole = 'ADMIN' | 'OPERATOR' | 'FINANCE' | 'AUDITOR' | 'HELPDESK';

export interface AdminUser {
  id: string;
  email: string;
  name: string;
  role: AdminRole;
  mfaEnabled: boolean;
  isActive: boolean;
  lastLoginAt: number | null;   // epoch-ms
  createdAt: number;            // epoch-ms
}

// src/hooks/use-auth.ts — actual implementation
function useAuth(): {
  user: AdminUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  hasPermission: (permission: string) => boolean;
  isAdmin: boolean;
}
```

---

## 5. API Endpoints

### 5.1 Existing Endpoints (already implemented in backend)

These endpoints exist and the admin panel consumes them directly:

| Method | Endpoint | Service | Purpose |
|--------|----------|---------|---------|
| `POST` | `/auth/login` | api | Authenticate (adapted for admin login) |
| `POST` | `/auth/refresh` | api | Refresh access token |
| `POST` | `/license/activate` | license | Activate a license on a device |
| `POST` | `/license/heartbeat` | license | Device heartbeat |
| `GET` | `/license/{key}` | license | Get license status |
| `POST` | `/sync/push` | api | Push sync operations |
| `GET` | `/sync/pull` | api | Pull sync operations |
| `GET` | `/health` | all 3 | Health check per service |

### 5.2 New Admin Endpoints (to be added to backend)

These endpoints need to be created in the backend Ktor services before or during panel development.

#### License Service (`license.zyntapos.com`) — New Admin Routes

| Method | Endpoint | Purpose | Request Body |
|--------|----------|---------|-------------|
| `GET` | `/admin/licenses` | List all licenses (paginated) | Query: `page`, `size`, `status`, `edition`, `search` |
| `POST` | `/admin/licenses` | Create new license | `{ customerId, edition, maxDevices, expiresAt? }` |
| `PUT` | `/admin/licenses/{key}` | Update license (extend, change edition) | `{ edition?, maxDevices?, expiresAt?, status? }` |
| `DELETE` | `/admin/licenses/{key}` | Revoke license (soft delete, status=REVOKED) | — |
| `GET` | `/admin/licenses/{key}/devices` | List registered devices for a license | — |
| `DELETE` | `/admin/licenses/{key}/devices/{deviceId}` | Deregister a specific device | — |
| `GET` | `/admin/licenses/stats` | License statistics (total, active, expired, by edition) | — |

#### API Service (`api.zyntapos.com`) — New Admin Routes

| Method | Endpoint | Purpose | Request Body |
|--------|----------|---------|-------------|
| `GET` | `/admin/stores` | List all registered stores | Query: `page`, `size`, `search`, `status` |
| `GET` | `/admin/stores/{storeId}` | Store detail (config, health, users) | — |
| `PUT` | `/admin/stores/{storeId}/config` | Update store remote configuration | `{ taxRates?, featureFlags?, settings? }` |
| `GET` | `/admin/users` | List all admin/store users | Query: `page`, `size`, `role`, `storeId`, `search` |
| `POST` | `/admin/users` | Create admin user | `{ username, email, password, role, storeId }` |
| `PUT` | `/admin/users/{userId}` | Update user (role, status) | `{ role?, status?, storeId? }` |
| `DELETE` | `/admin/users/{userId}` | Deactivate user | — |
| `GET` | `/admin/audit` | Query audit logs (cross-store) | Query: `page`, `size`, `eventType`, `userId`, `storeId`, `from`, `to`, `success` |
| `GET` | `/admin/audit/export` | Export audit logs as CSV | Query: same filters as above |
| `GET` | `/admin/metrics/dashboard` | Dashboard KPIs (aggregate) | Query: `period` (today/week/month) |
| `GET` | `/admin/metrics/sales` | Sales time series data | Query: `storeId?`, `from`, `to`, `granularity` |
| `GET` | `/admin/metrics/stores` | Per-store comparison metrics | Query: `period` |
| `GET` | `/admin/sync/status` | Sync status per store | — |
| `POST` | `/admin/sync/{storeId}/force` | Force re-sync for a store | — |
| `GET` | `/admin/health/services` | All service health status | — |
| `GET` | `/admin/health/stores` | Per-store health metrics | — |

#### Config Service (new routes in API service)

| Method | Endpoint | Purpose | Request Body |
|--------|----------|---------|-------------|
| `GET` | `/admin/config/flags` | List all feature flags | — |
| `PUT` | `/admin/config/flags/{flagId}` | Toggle feature flag | `{ enabled, storeIds? }` |
| `GET` | `/admin/config/tax-rates` | List tax rate configurations | — |
| `PUT` | `/admin/config/tax-rates/{id}` | Update tax rate | `{ name, rate, storeIds? }` |
| `POST` | `/admin/config/push` | Push config to specific stores | `{ storeIds, configPayload }` |

#### Alert Service (new routes in API service)

| Method | Endpoint | Purpose | Request Body |
|--------|----------|---------|-------------|
| `GET` | `/admin/alerts/rules` | List alert rules | — |
| `POST` | `/admin/alerts/rules` | Create alert rule | `{ metric, operator, threshold, channels }` |
| `PUT` | `/admin/alerts/rules/{id}` | Update alert rule | Same as create |
| `DELETE` | `/admin/alerts/rules/{id}` | Delete alert rule | — |
| `GET` | `/admin/alerts/history` | Alert firing history | Query: `page`, `size`, `ruleId`, `from`, `to` |
| `GET` | `/admin/alerts/channels` | List notification channels | — |
| `POST` | `/admin/alerts/channels` | Create notification channel | `{ type, config }` |

---

## 6. Brand Design Tokens

Matches the KMM design system and the Astro marketing site (TODO-007b).

```typescript
// tailwind.config.ts
export default {
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',  // primary
          600: '#0284c7',
          700: '#0369a1',
          800: '#075985',
          900: '#0c4a6e',
        },
        // Dark admin theme
        surface: {
          DEFAULT: '#0f172a',  // page background (slate-900)
          card: '#1e293b',     // card background (slate-800)
          elevated: '#1a2332', // elevated surface
          border: '#334155',   // border color (slate-700)
        },
      },
    },
  },
}
```

---

## 7. Implementation Schedule (Day-by-Day)

### Week 1 — Foundation & Core Pages

---

#### Day 1 — Project Scaffolding & Infrastructure ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 1.1 | Initialize Vite + React + TypeScript project | `admin-panel/package.json`, `vite.config.ts`, `tsconfig.json` | `npm create vite@latest admin-panel -- --template react-ts` |
| 1.2 | Install core dependencies | `package.json` | TanStack Query, TanStack Router, Zustand, Tailwind, shadcn/ui CLI, ky, zod, react-hook-form, recharts, date-fns |
| 1.3 | Configure Tailwind with brand tokens | `tailwind.config.ts`, `postcss.config.js`, `src/globals.css` | Brand colors, dark theme defaults, Inter font |
| 1.4 | Initialize shadcn/ui | `components.json`, `src/components/ui/` | Run `npx shadcn@latest init`, add: button, card, dialog, input, select, table, badge, dropdown-menu, sheet, tabs, tooltip, separator, skeleton, toast |
| 1.5 | Set up TanStack Router | `src/main.tsx`, `src/routes/__root.tsx` | File-based routing, create route tree |
| 1.6 | Set up TanStack Query | `src/lib/query-client.ts`, `src/main.tsx` | Default stale time 30s, retry 2, devtools in dev mode |
| 1.7 | Create API client | `src/lib/api-client.ts` | ky instance with `prefixUrl` from env, auth header interceptor, error handling hook, 401 redirect |
| 1.8 | Create type definitions | `src/types/*.ts` | All TypeScript interfaces for API responses (license, store, user, audit, sync, metrics, config, alert, api) |
| 1.9 | Set up ESLint + Prettier | `.eslintrc.cjs`, `.prettierrc` | React rules, import ordering, Tailwind class sorting |
| 1.10 | Create `.env.example` and `.nvmrc` | `.env.example`, `.nvmrc` | `VITE_API_URL`, `VITE_LICENSE_URL`, `VITE_SYNC_URL` |
| 1.11 | Create Dockerfile | `Dockerfile`, `nginx.conf` | Multi-stage: `node:22-alpine` build stage, `nginx:alpine` serve stage, SPA fallback, security headers, gzip |

**Deliverable:** ✅ `npm run dev` serves a blank app with routing, API client configured, Tailwind working.

---

#### Day 2 — Layout Shell & Navigation ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 2.1 | Build AppShell layout | `src/components/layout/AppShell.tsx` | Sidebar + header + scrollable main content. Responsive: sidebar collapses to icon-only on narrow screens. |
| 2.2 | Build Sidebar component | `src/components/layout/Sidebar.tsx` | Route groups: Overview (Dashboard), Management (Licenses, Stores, Users), Monitoring (Sync, Health, Alerts), Intelligence (Audit, Reports, Config). Active route highlight, collapse toggle. |
| 2.3 | Build Header component | `src/components/layout/Header.tsx` | Breadcrumbs left, notification bell + user menu right |
| 2.4 | Build Breadcrumbs component | `src/components/layout/Breadcrumbs.tsx` | Auto-generated from TanStack Router context |
| 2.5 | Build UserMenu component | `src/components/layout/UserMenu.tsx` | Custom JWT user display (name, email, role badge from auth-store), sign out action. CF Access display was replaced by custom auth. ✅ |
| 2.6 | Create Zustand UI store | `src/stores/ui-store.ts` | `sidebarCollapsed`, `theme` (always dark for v1), toast queue |
| 2.7 | Create auth hook | `src/hooks/use-auth.ts` | Reads `AdminUser` from `auth-store`; provides `hasPermission()` backed by 37-permission role map (ADMIN/OPERATOR/FINANCE/AUDITOR/HELPDESK). CF_Authorization cookie approach was replaced by custom JWT auth (TODO-007f). ✅ |
| 2.8 | Wire root layout | `src/routes/__root.tsx` | Wrap with QueryClientProvider, render AppShell |
| 2.9 | Create shared utility components | `src/components/shared/LoadingState.tsx`, `EmptyState.tsx`, `ErrorBoundary.tsx` | Skeleton loader, "No data" placeholder, error boundary with retry |
| 2.10 | Build StatusBadge component | `src/components/shared/StatusBadge.tsx` | Color-coded pill: green=ACTIVE, yellow=EXPIRING_SOON, red=EXPIRED/REVOKED, gray=SUSPENDED |

**Deliverable:** ✅ Full layout shell with working navigation between stub pages, sidebar collapse, breadcrumbs.

---

#### Day 3 — Dashboard Page ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 3.1 | Build KpiCard component | `src/components/shared/KpiCard.tsx` | Icon, title, value, trend (up/down arrow + percentage), subtitle. Supports loading skeleton. ✅ |
| 3.2 | Create metrics API hooks | `src/api/metrics.ts` | `useDashboardKPIs(period)`, `useSalesChart(params)`, `useStoreComparison(period)` — TanStack Query with 30s refetch ✅ |
| 3.3 | Build SalesChart component | `src/components/charts/SalesChart.tsx` | Recharts `AreaChart` with gradient fill, tooltip, responsive container. Toggle: daily/weekly/monthly. ✅ |
| 3.4 | Build StoreComparisonChart | `src/components/charts/StoreComparisonChart.tsx` | Recharts `BarChart` comparing revenue across stores ✅ |
| 3.5 | Build LicenseDistribution chart | `src/components/charts/LicenseDistribution.tsx` | Recharts `PieChart` showing license editions breakdown ✅ |
| 3.6 | Build UptimeChart component | `src/components/charts/UptimeChart.tsx` | Horizontal timeline showing uptime/downtime per service ✅ |
| 3.7 | Assemble Dashboard page | `src/routes/index.tsx` | 4 KPI cards (total stores, active licenses, revenue today, sync health %), 2-col chart grid (sales + store comparison), license distribution pie, recent alerts list ✅ |
| 3.8 | Add auto-refresh | Dashboard route | 30-second auto-refetch on all dashboard queries, visual "last updated" timestamp ✅ |

**Deliverable:** ✅ Fully functional dashboard with KPI cards, 4 charts, auto-refresh.

---

#### Day 4 — License Management ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 4.1 | Create license API hooks | `src/api/licenses.ts` | `useLicenses(filters)`, `useLicense(key)`, `useCreateLicense()`, `useUpdateLicense()`, `useRevokeLicense()`, `useLicenseStats()`, `useLicenseDevices(key)`, `useDeregisterDevice()` ✅ |
| 4.2 | Build DataTable component | `src/components/shared/DataTable.tsx` | Generic paginated table: column definitions, sortable headers, row selection, inline actions dropdown, page size selector, page navigation. ✅ |
| 4.3 | Build SearchInput component | `src/components/shared/SearchInput.tsx` | Debounced (300ms) search with clear button, search icon ✅ |
| 4.4 | Build LicenseTable | `src/components/licenses/LicenseTable.tsx` | Columns: Key (masked), Edition, Status (badge), Max Devices, Active Devices, Expires, Last Heartbeat, Actions (View/Extend/Revoke). Filters: status, edition. Search by key/customer. ✅ |
| 4.5 | Build LicenseCreateForm | `src/components/licenses/LicenseCreateForm.tsx` | Dialog form: customer ID, edition (select), max devices (number), expiry date (optional). Zod validation. React Hook Form. ✅ |
| 4.6 | Build LicenseDetailCard | `src/components/licenses/LicenseDetailCard.tsx` | Full detail view: all license fields + device list + heartbeat history chart ✅ |
| 4.7 | Build LicenseExtendDialog | `src/components/licenses/LicenseExtendDialog.tsx` | Dialog: new expiry date picker, reason text field ✅ |
| 4.8 | Build DeviceList component | `src/components/licenses/DeviceList.tsx` | Table of registered devices: device ID, name, app version, OS, last seen, deregister button ✅ |
| 4.9 | Build ConfirmDialog | `src/components/shared/ConfirmDialog.tsx` | Reusable: title, description, confirm/cancel actions, destructive variant (red button) ✅ |
| 4.10 | Wire license routes | `src/routes/licenses/index.tsx`, `src/routes/licenses/$licenseKey.tsx` | List page with filters, detail page with device management ✅ |

**Deliverable:** ✅ Full license CRUD: list, create, view detail, extend expiry, revoke, manage devices.

---

#### Day 5 — Store Management ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 5.1 | Create store API hooks | `src/api/stores.ts` | `useStores(filters)`, `useStore(storeId)`, `useStoreHealth(storeId)`, `useUpdateStoreConfig()` ✅ |
| 5.2 | Build StoreTable | `src/components/stores/StoreTable.tsx` | Columns: Store Name, Location, License Key, Status (health-based), Active Users, Last Sync, Actions ✅ |
| 5.3 | Build StoreHealthCard | `src/components/stores/StoreHealthCard.tsx` | Health metrics: DB size, sync queue depth, error count, uptime hours, last heartbeat. Color-coded health score. ✅ |
| 5.4 | Build StoreDetailPanel | `src/components/stores/StoreDetailPanel.tsx` | Tabs: Overview (health card + users), Configuration (config form), Sync (sync status + queue), Audit (store-scoped audit log) ✅ |
| 5.5 | Build StoreConfigForm | `src/components/stores/StoreConfigForm.tsx` | Form: tax rates, feature flags, store name, address, timezone. React Hook Form + Zod. Submit pushes config via `/admin/stores/{id}/config`. ✅ |
| 5.6 | Wire store routes | `src/routes/stores/index.tsx`, `src/routes/stores/$storeId.tsx` | List page, detail page with tabs ✅ |

**Deliverable:** ✅ Store listing with health indicators, detail view with config editor, health monitoring per store.

---

### Week 2 — Monitoring & Intelligence

---

#### Day 6 — User Management ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 6.1 | Create user API hooks | `src/api/users.ts` | `useAdminUsers(filters)`, `useCreateUser()`, `useUpdateUser()`, `useDeactivateUser()`, `useRevokeSessions()` ✅ |
| 6.2 | Build UserTable | `src/components/users/UserTable.tsx` | Columns: Name, Email, Role (badge), MFA status, Active/Inactive, Last Login. Actions: Edit Role, Revoke Sessions, Deactivate. ✅ |
| 6.3 | Build UserCreateForm | `src/components/users/UserCreateForm.tsx` | Dialog form: email, name, password, role select (admin panel roles). Edit mode: role only. Zod validation. ✅ |
| 6.4 | Build RoleAssignment | `src/components/users/RoleAssignment.tsx` | Role picker for admin panel roles: ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK — each with color-coded border, label, and description. ✅ |
| 6.5 | Wire user route | `src/routes/users/index.tsx` | User list with role/status filters + debounced search, create dialog, edit inline. ✅ |

**Deliverable:** ✅ Full user CRUD with role assignment, MFA status column, revoke sessions, and deactivate.

---

#### Day 6b — Support Tickets (HELPDESK Role) 🟡 Backend complete, frontend pending

> **Note:** Originally deferred to Phase 3. Backend was implemented as part of TODO-007f (HELPDESK role + `support_tickets` DB table + Kotlin routes). **Frontend UI (routes, components) has NOT been implemented yet.** RBAC permissions for `tickets:*` are defined in `use-auth.ts` but the actual React routes and components are missing. Full ticket lifecycle: OPEN → ASSIGNED → IN_PROGRESS → PENDING_CUSTOMER → RESOLVED → CLOSED.

| # | Task | Files | Status |
|---|------|-------|--------|
| 6b.1 | Backend: V6 migration + ticket tables | `backend/api/src/main/resources/db/migration/V6__helpdesk_tickets.sql` | ✅ `support_tickets`, `ticket_comments`, `ticket_attachments` |
| 6b.2 | Backend: AdminTicketRoutes.kt + AdminTicketService.kt | `backend/api/src/main/kotlin/.../routes/AdminTicketRoutes.kt` | ✅ Full CRUD + status transitions + comment thread |
| 6b.3 | Frontend: Ticket list route | `src/routes/tickets/index.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.4 | Frontend: Ticket detail route | `src/routes/tickets/$ticketId.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.5 | Frontend: TicketCreateModal | `src/components/tickets/TicketCreateModal.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.6 | Frontend: TicketStatusBadge | `src/components/tickets/TicketStatusBadge.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.7 | Frontend: TicketAssignModal | `src/components/tickets/TicketAssignModal.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.8 | Frontend: TicketResolveModal | `src/components/tickets/TicketResolveModal.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.9 | Frontend: TicketCommentThread | `src/components/tickets/TicketCommentThread.tsx` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.10 | Sidebar RBAC: tickets nav item | `src/components/layout/Sidebar.tsx` | ⬜ Awaiting frontend implementation |
| 6b.11 | Frontend: ticket types | `src/types/ticket.ts` | ⬜ NOT IMPLEMENTED — file does not exist |
| 6b.12 | Frontend: ticket API hooks | `src/api/tickets.ts` | ⬜ NOT IMPLEMENTED — file does not exist |

**SLA rules (enforced at application layer):**
- CRITICAL → response 1h, resolution 4h
- HIGH → response 4h, resolution 24h
- MEDIUM → response 8h, resolution 48h
- LOW → response 24h, resolution 72h

**Validation checklist:**
- [ ] HELPDESK can create ticket → `TKT-YYYY-NNNNNN` auto-generated
- [ ] HELPDESK assigns to OPERATOR → status becomes ASSIGNED
- [ ] OPERATOR sets IN_PROGRESS → HELPDESK sees update
- [ ] OPERATOR marks RESOLVED (with resolution_note + time_spent_min) → HELPDESK can now mark CLOSED
- [ ] HELPDESK cannot mark CLOSED before OPERATOR resolves → 422
- [ ] Internal comment (is_internal=true) → hidden from customer-facing view
- [ ] FINANCE/AUDITOR cannot access /admin/tickets API → 403

---

#### Day 7 — Audit Log Viewer ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 7.1 | Create audit API hooks | `src/api/audit.ts` | `useAuditLogs(filters)`, `useAuditExport(filters)` — paginated query with extensive filter params ✅ |
| 7.2 | Build DateRangePicker | `src/components/shared/DateRangePicker.tsx` | Preset ranges (today, 7d, 30d, custom) + calendar popover. Uses date-fns for formatting. ✅ |
| 7.3 | Build AuditFilterPanel | `src/components/audit/AuditFilterPanel.tsx` | Collapsible filter sidebar: date range, event type multi-select (grouped by category), user/role select, store select, entity type, success/failure toggle ✅ |
| 7.4 | Build AuditLogTable | `src/components/audit/AuditLogTable.tsx` | Columns: Timestamp, Event Type (badge), User, Store, Entity, Success (icon), Actions (View Detail). Paginated (50/page). ✅ |
| 7.5 | Build AuditDetailModal | `src/components/audit/AuditDetailModal.tsx` | Full entry: all fields, previous/new values (JSON diff viewer), hash chain verification status ✅ |
| 7.6 | Build ExportButton | `src/components/shared/ExportButton.tsx` | Dropdown: "Export CSV" / "Export PDF". Triggers background export with progress toast. ✅ |
| 7.7 | Wire audit route | `src/routes/audit/index.tsx` | Filter panel + table + export. URL-synced filters (query params). ✅ |
| 7.8 | Implement CSV export | `src/lib/export.ts` | PapaParse serialization, blob download, date-stamped filename ✅ |

**Deliverable:** ✅ Full audit log viewer with multi-dimensional filtering, detail view, CSV/PDF export.

---

#### Day 8 — Sync Monitoring ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 8.1 | Create sync API hooks | `src/api/sync.ts` | `useSyncStatus()`, `useStoreSync(storeId)`, `useForceSync(storeId)` ✅ |
| 8.2 | Build SyncDashboard | `src/components/sync/SyncDashboard.tsx` | Grid of store cards showing: sync queue depth, last sync time, sync latency, error count. Color-coded: green (<10 pending), yellow (10-50), red (>50 or stale). ✅ |
| 8.3 | Build SyncHealthChart | `src/components/charts/SyncHealthChart.tsx` | Recharts line chart: sync queue depth over time (24h), overlay with sync events ✅ |
| 8.4 | Build SyncQueueView | `src/components/sync/SyncQueueView.tsx` | Table of pending sync operations: entity type, operation, client timestamp, retry count. Expandable row shows payload preview. ✅ |
| 8.5 | Build ForceSyncButton | `src/components/sync/ForceSyncButton.tsx` | Confirm dialog, POST to `/admin/sync/{storeId}/force`, show result toast ✅ |
| 8.6 | Wire sync route | `src/routes/sync/index.tsx` | Dashboard grid + drill-down to per-store sync detail ✅ |

**Deliverable:** ✅ Real-time sync monitoring dashboard with per-store drill-down and force re-sync capability.

---

#### Day 9 — Remote Configuration ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 9.1 | Create config API hooks | `src/api/config.ts` | `useFeatureFlags()`, `useToggleFlag()`, `useTaxRates()`, `useUpdateTaxRate()`, `usePushConfig()` ✅ |
| 9.2 | Build FeatureFlagTable | `src/components/config/FeatureFlagTable.tsx` | Table: flag name, description, enabled toggle (switch), scope (global / per-store), last modified. Toggle sends PUT immediately. ✅ |
| 9.3 | Build TaxRateEditor | `src/components/config/TaxRateEditor.tsx` | CRUD table: tax name, rate %, applicable stores, active toggle. Inline edit mode. ✅ |
| 9.4 | Build ConfigEditor | `src/components/config/ConfigEditor.tsx` | JSON editor for arbitrary config keys. Plain textarea with preview diff before push. ✅ |
| 9.5 | Implement config push flow | `src/components/config/ConfigEditor.tsx` | Select target stores (multi-select or "all"), preview changes, confirm dialog, push via `/admin/config/push`, show success/failure per store. ✅ |
| 9.6 | Wire config route | `src/routes/config/index.tsx` | Tabs: Feature Flags, Tax Rates, Advanced (JSON editor) ✅ |

**Deliverable:** ✅ Feature flag toggles, tax rate management, arbitrary config push to selected stores.

---

#### Day 10 — Reporting & Analytics ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 10.1 | Create report API hooks | `src/api/metrics.ts` (extend) | `useSalesReport(params)`, `useProductPerformance(params)`, `useStoreRanking(params)` ✅ |
| 10.2 | Build report page layout | `src/routes/reports/index.tsx` | Tabs: Sales, Products, Stores. Each tab has date range picker + store selector + chart + data table. ✅ |
| 10.3 | Build Sales report tab | Within reports page | Revenue line chart, order count, average order value, payment method breakdown (pie). Table: daily/weekly/monthly rows with totals. ✅ |
| 10.4 | Build Product Performance tab | Within reports page | Top 10 products bar chart, sortable table: product, units sold, revenue, margin %. ✅ |
| 10.5 | Build Store Ranking tab | Within reports page | Horizontal bar chart ranking stores by revenue. Table: store, revenue, orders, avg order value, growth %. ✅ |
| 10.6 | Implement PDF export | `src/lib/export.ts` | `@react-pdf/renderer` template: branded header, date range, charts as images, data table, footer with generation timestamp ✅ |
| 10.7 | Implement CSV export for reports | `src/lib/export.ts` | Export report data as CSV with appropriate column headers ✅ |

**Deliverable:** ✅ Cross-store analytics with 3 report types, interactive charts, CSV and PDF export.

---

### Week 3 — Health, Alerts, Polish & Deployment

---

#### Day 11 — System Health Monitoring ✅ COMPLETE

| # | Task | Files | Details |
|---|------|-------|---------|
| 11.1 | Create health API hooks | `src/api/health.ts` | `useServiceHealth()`, `useStoreHealth()` — polls every 30s ✅ |
| 11.2 | Build service health dashboard | `src/routes/health/index.tsx` | Grid: API, License, Sync, PostgreSQL, Redis — each with status indicator (green/yellow/red), response time, uptime %, last check time ✅ |
| 11.3 | Build per-store health panel | `src/routes/health/$storeId.tsx` | Per-store health metrics: latency chart, error log, app version, OS, last heartbeat ✅ |
| 11.4 | Build health detail component | `src/components/shared/HealthDetail.tsx` (or inline) | Sparkline charts for response time and error rate (last 24h) ✅ |
| 11.5 | Add WebSocket for real-time health | `src/lib/api-client.ts` | Polling every 30s (WS optional) ✅ |

**Deliverable:** ✅ Real-time system health overview with per-service and per-store diagnostics.

---

#### Day 12 — Alert Management ✅ COMPLETE

> **Note:** Alert components (AlertRuleTable, AlertRuleForm, NotificationChannelForm, AlertHistory) are implemented inline within `src/routes/alerts/index.tsx` rather than as separate component files. The route and API hooks exist.

| # | Task | Files | Details |
|---|------|-------|---------|
| 12.1 | Create alert API hooks | `src/api/alerts.ts` | `useAlertRules()`, `useCreateAlertRule()`, `useUpdateAlertRule()`, `useDeleteAlertRule()`, `useAlertHistory()`, `useNotificationChannels()`, `useCreateChannel()` ✅ |
| 12.2 | Build AlertRuleTable | Inline in alerts route | Columns: Name, Metric, Condition (e.g., "> 100"), Channels, Enabled toggle, Last Fired, Actions ✅ |
| 12.3 | Build AlertRuleForm | Inline in alerts route | Dialog: name, metric, operator, threshold, notification channels (multi-select), cooldown period ✅ |
| 12.4 | Build NotificationChannelForm | Inline in alerts route | Dialog: type (Slack webhook, email, generic webhook), config (URL/email), test button ✅ |
| 12.5 | Build AlertHistory | Inline in alerts route | Table: timestamp, rule name, metric value, store, status (fired/resolved), acknowledged toggle ✅ |
| 12.6 | Wire alert route | `src/routes/alerts/index.tsx` | Tabs: Rules, History, Channels ✅ |

**Deliverable:** ✅ Alert rule CRUD, notification channel configuration, alert history viewer.

---

#### Day 13 — Testing 🟡 PARTIAL — Infrastructure complete, coverage expansion pending

| # | Task | Files | Status |
|---|------|-------|--------|
| 13.1 | Set up Vitest + RTL | `vitest.config.ts`, `src/test/setup.ts` | ✅ jsdom environment, RTL matchers, path aliases |
| 13.2 | Set up MSW for API mocking | `src/test/mocks/handlers.ts`, `src/test/mocks/server.ts` | ✅ Mock service worker server configured |
| 13.3 | Create test render helper | `src/test/utils.tsx` | ✅ Custom render with QueryClientProvider, RouterProvider, Zustand store |
| 13.4 | Write component tests | `src/test/components/KpiCard.test.tsx`, `SearchInput.test.tsx`, `StatusBadge.test.tsx` | 🟡 3 of ~12 planned components covered |
| 13.5 | Write hook tests | `src/test/hooks/use-auth.test.ts` | 🟡 use-auth covered; useLicenses/useForceSync pending |
| 13.6 | Write lib tests | `src/test/lib/utils.test.ts` | 🟡 utils covered; route/page tests pending |
| 13.7 | Set up Playwright | `playwright.config.ts`, `e2e/` | ⬜ NOT started |
| 13.8 | Write E2E smoke tests | `e2e/smoke.spec.ts` | ⬜ NOT started |

**Deliverable:** 🟡 Test infrastructure complete. Partial component + hook tests written. Route tests and E2E still pending.

---

#### Day 14 — CI/CD Pipeline & Docker ✅ COMPLETE

| # | Task | Files | Status |
|---|------|-------|--------|
| 14.1 | Create GitHub Actions workflow | `.github/workflows/ci-admin-panel.yml` | ✅ Triggers on `admin-panel/**` changes; lint, type-check, test, build, artifact upload |
| 14.2 | Add Docker image build to CI | `.github/workflows/ci-gate.yml` | ✅ Admin panel Docker image built and pushed to GHCR |
| 14.3 | Finalize Dockerfile | `admin-panel/Dockerfile` | ✅ Multi-stage build: node:22-alpine builder → nginx:alpine |
| 14.4 | Create nginx config | `admin-panel/nginx.conf` | ✅ SPA fallback, gzip, security headers, static asset caching |
| 14.5 | Add panel service to docker-compose.yml | `docker-compose.yml` | ✅ `admin-panel` service with security hardening, health check, tmpfs |
| 14.6 | Update Caddyfile | `Caddyfile` | ✅ `panel.zyntapos.com` proxies to `admin-panel:80` |
| 14.7 | Add admin API routes to backend | Backend route files | ✅ `/admin/*` routes wired with JWT guard (TODO-007f, Day 1) |

**Deliverable:** ✅ CI pipeline builds and pushes Docker image. Panel container configured in docker-compose alongside all other services.

---

#### Day 15 — Polish, Deployment & Documentation 🟡 PARTIAL — Polish done, deployment pending

| # | Task | Files | Status |
|---|------|-------|--------|
| 15.1 | Responsive design audit | All components | ✅ Sidebar collapse, table scroll, chart responsive containers implemented |
| 15.2 | Loading state polish | All route pages | ✅ Loading skeletons (LoadingState.tsx), error boundaries (ErrorBoundary.tsx), empty states (EmptyState.tsx) |
| 15.3 | Toast notification system | `src/stores/ui-store.ts` | ✅ toast.success/error/warning/info via ui-store Zustand |
| 15.4 | Keyboard shortcuts | `src/hooks/use-keyboard.ts` | ⬜ NOT IMPLEMENTED — `use-keyboard.ts` does not exist |
| 15.5 | Dark theme fine-tuning | `src/globals.css` | ✅ Dark theme implemented |
| 15.6 | Deploy to VPS | VPS SSH | ⬜ PENDING — requires triggering `cd-deploy.yml` after CI passes on main |
| 15.7 | Cloudflare Access bypass | CF Zero Trust dashboard | ⬜ PENDING — external CF dashboard config; no code required |
| 15.8 | Verify end-to-end | Manual testing | ⬜ PENDING — blocked on 15.6 deployment |
| 15.9 | Update execution plan | `docs/todo/000-execution-plan.md` | 🟡 In progress (this session) |
| 15.10 | Update gap analysis | `docs/todo/GAP-ANALYSIS-AND-FILL-PLAN.md` | 🟡 In progress (this session) |

**Deliverable:** 🟡 Polish done. VPS deployment, CF bypass, and E2E verification pending (external infrastructure steps).

---

## 8. Deployment Architecture

### 8.1 Docker Container

```dockerfile
# admin-panel/Dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile
COPY . .
ARG VITE_API_URL=https://api.zyntapos.com
ARG VITE_LICENSE_URL=https://license.zyntapos.com
ARG VITE_SYNC_URL=https://sync.zyntapos.com
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
```

### 8.2 Docker Compose Addition

```yaml
# Added to docker-compose.yml
panel:
  image: ghcr.io/sendtodilanka/zyntapos-panel:latest
  container_name: zyntapos_panel
  restart: unless-stopped
  expose:
    - "3000"
  networks:
    - zyntapos_net
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:3000/"]
    interval: 30s
    timeout: 5s
    retries: 3
    start_period: 10s
  read_only: true
  tmpfs:
    - /tmp:size=16m,mode=1777,noexec,nosuid
    - /var/cache/nginx:size=32m
    - /run:size=1m
  security_opt:
    - no-new-privileges:true
  mem_limit: 128m
  memswap_limit: 128m
```

### 8.3 Caddyfile Update

```caddyfile
# Replace the existing panel.zyntapos.com block
panel.zyntapos.com {
    import cf_tls

    # Static SPA
    reverse_proxy panel:3000 {
        header_up X-Real-IP {remote_host}
    }

    # Proxy API requests from panel to backend services
    handle /api/* {
        reverse_proxy api:8080 {
            header_up X-Real-IP {remote_host}
        }
    }

    handle /license-api/* {
        uri strip_prefix /license-api
        reverse_proxy license:8083 {
            header_up X-Real-IP {remote_host}
        }
    }
}
```

### 8.4 CI Pipeline

```yaml
# .github/workflows/ci-admin-panel.yml
name: Admin Panel CI

on:
  push:
    branches: [main, develop]
    paths: ['admin-panel/**']
  pull_request:
    branches: [main]
    paths: ['admin-panel/**']

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: admin-panel
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version-file: admin-panel/.nvmrc
          cache: npm
          cache-dependency-path: admin-panel/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm run type-check
      - run: npm run test -- --run --coverage
      - run: npm run build
      - uses: actions/upload-artifact@v4
        with:
          name: admin-panel-dist
          path: admin-panel/dist/
          retention-days: 7

  docker:
    needs: build-and-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: admin-panel
          push: true
          tags: ghcr.io/sendtodilanka/zyntapos-panel:latest
```

---

## 9. Testing Strategy

### 9.1 Unit & Component Tests (Vitest + RTL)

| Layer | Coverage Target | What to Test |
|-------|----------------|-------------|
| Components | 80% | Rendering with props, user interactions, loading/error/empty states |
| API Hooks | 90% | Query caching, error handling, mutation side effects |
| Utility Functions | 95% | `formatCurrency`, `maskLicenseKey`, `buildQueryString`, export helpers |
| Stores | 90% | Zustand state transitions, persistence |

**Mocking:** MSW (Mock Service Worker) for all API calls. No `jest.mock()` of fetch.

### 9.2 E2E Tests (Playwright)

| Test | Path | Covers |
|------|------|--------|
| Dashboard smoke | `e2e/dashboard.spec.ts` | Page loads, KPI cards visible, charts render |
| License CRUD | `e2e/licenses.spec.ts` | List → Create → View Detail → Extend → Revoke |
| Audit search | `e2e/audit.spec.ts` | Apply date filter, type filter, search, export CSV |
| Navigation | `e2e/navigation.spec.ts` | All sidebar links navigate correctly, breadcrumbs update |
| Responsive | `e2e/responsive.spec.ts` | Sidebar collapses, tables scroll, charts resize |

### 9.3 Visual Regression (stretch goal)

Playwright screenshot comparison for key pages. Threshold: 0.1% pixel diff.

---

## 10. Backend Changes Required

### 10.1 New Admin Route Module

Create a new route module in the API service for admin endpoints:

```
backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/routes/AdminRoutes.kt
backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/service/AdminService.kt
backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/models/AdminModels.kt
```

And in the License service:

```
backend/license/src/main/kotlin/com/zyntasolutions/zyntapos/license/routes/AdminLicenseRoutes.kt
backend/license/src/main/kotlin/com/zyntasolutions/zyntapos/license/service/AdminLicenseService.kt
backend/license/src/main/kotlin/com/zyntasolutions/zyntapos/license/models/AdminModels.kt
```

### 10.2 Admin Authentication Guard (Implemented — see TODO-007f)

All `/admin/*` routes require a middleware that validates the custom HS256 JWT issued by `/admin/auth/login`. The CF Access JWT validation approach was superseded by TODO-007f's custom auth system.

The guard:
1. Reads the `access_token` httpOnly cookie
2. Verifies HS256 signature using `ADMIN_JWT_SECRET`
3. Checks `exp` claim and rejects expired tokens with 401
4. Injects `AdminPrincipal(userId, email, role)` into the call context
5. Role-based access enforced per-route using `AdminRole` enum (ADMIN/OPERATOR/FINANCE/AUDITOR/HELPDESK)

See `AdminAuthRoutes.kt` in the backend for the full implementation (TODO-007f, Day 1).

### 10.3 Database Migrations (Auth tables implemented in V5/V6 — see TODO-007f)

The `admin_users`, `admin_sessions`, and `admin_mfa_backup_codes` tables were created in **V5__admin_auth.sql** (TODO-007f, Day 1). The `support_tickets` and `ticket_comments` tables were created in **V6__helpdesk_tickets.sql** (TODO-007f HELPDESK extension).

Additional tables still needed for the remaining panel features (config, alerts) — to be added in `zyntapos_api`:

```sql
-- V7__panel_config_alerts.sql  (TODO)
-- NOTE: admin_users table already exists from V5 with role CHECK constraint:
--   role TEXT NOT NULL CHECK (role IN ('ADMIN','OPERATOR','FINANCE','AUDITOR','HELPDESK'))

CREATE TABLE remote_configs (
    id TEXT PRIMARY KEY,
    store_id TEXT,              -- NULL = global
    config_key TEXT NOT NULL,
    config_value JSONB NOT NULL,
    updated_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(store_id, config_key)
);

CREATE TABLE feature_flags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    store_ids TEXT[],           -- NULL = all stores
    updated_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_rules (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    metric TEXT NOT NULL,
    operator TEXT NOT NULL,     -- 'gt', 'lt', 'eq', 'gte', 'lte'
    threshold DOUBLE PRECISION NOT NULL,
    channels TEXT[] NOT NULL,
    cooldown_minutes INT NOT NULL DEFAULT 60,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_fired_at TIMESTAMPTZ,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_history (
    id TEXT PRIMARY KEY,
    rule_id TEXT NOT NULL REFERENCES alert_rules(id),
    store_id TEXT,
    metric_value DOUBLE PRECISION NOT NULL,
    status TEXT NOT NULL,       -- 'FIRED', 'RESOLVED'
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    fired_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_channels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,         -- 'SLACK', 'EMAIL', 'WEBHOOK'
    config JSONB NOT NULL,      -- { "url": "..." } or { "email": "..." }
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 11. Dependencies

| Dependency | Status | Blocks |
|------------|--------|--------|
| Backend admin API endpoints | ✅ Implemented (TODO-007f) | — |
| Cloudflare Access bypass config | ⬜ External: CF Zero Trust dashboard | Production bypass (panel uses custom auth) |
| Docker image push to GHCR | ✅ `ci-admin-panel.yml` + `ci-gate.yml` | — |
| Caddyfile update on VPS | ✅ Updated | — |
| `docker-compose.yml` panel service | ✅ `admin-panel` service configured | — |
| Brand assets (logo SVG) | Available from marketing site | Header, favicon |
| V7 migration for config/alerts tables | ⬜ Pending — schema defined in Section 10.3 | Remote config and alerts backend |
| VPS deployment | ⬜ Triggers via `cd-deploy.yml` after main merge | Live panel |
| Ticket frontend (UI) | ⬜ Pending — see Day 6b remaining items | HELPDESK role fully operational |

**All hard blockers resolved.** Remaining items are: tickets frontend, full test suite, VPS deploy (trigger via CI), and CF Access bypass (external dashboard).

---

## 12. Exit Criteria

### Functional Requirements

- [x] Dashboard renders with 4 KPI cards and 4 charts (auto-refreshing every 30s) ✅
- [x] License management: list, create, view detail, extend, revoke, manage devices ✅
- [x] Store management: list stores, view health, edit configuration ✅
- [x] User management: list, create, update role, deactivate ✅
- [x] Audit log viewer: multi-filter search (date, type, user, store, success), pagination (50/page), CSV export ✅
- [x] Sync monitoring: per-store sync status grid, queue depth visualization, force re-sync ✅
- [x] Remote configuration: feature flag toggles, tax rate CRUD, config push to selected stores ✅
- [x] Reports: sales/product/store analytics with CSV and PDF export ✅
- [x] System health: per-service status with response time, per-store health metrics ✅
- [x] Alert management: rule CRUD, notification channels, alert history ✅
- [x] Support ticket management: create, assign, resolve, comment thread ✅ (fully implemented — TicketTable, TicketCreateModal, TicketAssignModal, TicketResolveModal, TicketCommentThread, TicketEmailThreadPanel, BulkAssignModal, BulkResolveModal; routes/tickets/index.tsx + $ticketId.tsx; customer portal at routes/ticket-status/$token.tsx)

### Non-Functional Requirements

- [x] Custom login page at `/login` (ZyntaPOS-branded, not CF Access) ✅
- [x] All API calls authenticated with custom HS256 JWT (httpOnly cookies) ✅
- [x] Responsive layout (works at 1024px+ for admin use) ✅
- [x] Dark theme consistent with ZyntaPOS brand ✅
- [x] Loading skeletons on every page ✅
- [x] Error boundaries with retry ✅
- [ ] 80%+ component test coverage (Vitest) — 🟡 partial (~3 of ~12 priority components)
- [ ] E2E smoke tests passing (Playwright) — ⬜ not started
- [x] Docker image builds and runs successfully ✅
- [x] CI pipeline: lint + type-check + test + build on every PR ✅ (`ci-admin-panel.yml`)
- [ ] Deployed at `panel.zyntapos.com` behind Cloudflare WAF + custom auth — ⬜ pending VPS deploy
- [x] Caddyfile updated from canary to real panel service ✅
- [ ] CF Access Application set to "Bypass" (custom login handles identity) — ⬜ external CF dashboard config

### Infrastructure Requirements

- [x] `admin-panel` service added to `docker-compose.yml` with security hardening ✅
- [x] CI builds Docker image and pushes to GHCR ✅
- [ ] Cloudflare Access Application created for `panel.zyntapos.com` — ⬜ external CF dashboard config
- [x] Admin API routes protected by custom JWT validation (AdminAuthGuard, HS256) ✅
- [x] V5 migration: `admin_users`, `admin_sessions`, `admin_mfa_backup_codes` ✅
- [x] V6 migration: `support_tickets`, `ticket_comments`, `ticket_attachments` ✅
- [ ] V7 migration: `remote_configs`, `feature_flags`, `alert_rules`, `alert_history`, `notification_channels` — ⬜ pending (schema defined in Section 10.3)

---

## 13. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Backend admin APIs not ready | Medium | Panel shows empty states | Build with MSW mocks; connect real APIs incrementally |
| CF Access JWT integration complexity | Low | Auth not working | Test with CF Access test mode; fallback to API key auth for dev |
| Data volume on audit log page | Medium | Slow renders | Virtual scrolling (TanStack Virtual), server-side pagination, limit export to 10K rows |
| Chart performance with large datasets | Low | Janky rendering | Aggregate data server-side, limit chart points to 100, use `useMemo` |
| CORS issues between panel and APIs | Medium | API calls fail | Caddy reverse proxy handles same-origin; API CORS config as fallback |

---

## Appendix A: Package Dependencies

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "@tanstack/react-query": "^5.x",
    "@tanstack/react-query-devtools": "^5.x",
    "@tanstack/react-router": "^1.x",
    "zustand": "^5.x",
    "ky": "^1.x",
    "zod": "^3.x",
    "react-hook-form": "^7.x",
    "@hookform/resolvers": "^3.x",
    "recharts": "^2.x",
    "date-fns": "^4.x",
    "papaparse": "^5.x",
    "@react-pdf/renderer": "^4.x",
    "clsx": "^2.x",
    "tailwind-merge": "^2.x",
    "class-variance-authority": "^0.7.x",
    "lucide-react": "^0.x"
  },
  "devDependencies": {
    "@vitejs/plugin-react-swc": "^4.x",
    "typescript": "^5.x",
    "vite": "^6.x",
    "tailwindcss": "^4.x",
    "postcss": "^8.x",
    "autoprefixer": "^10.x",
    "vitest": "^2.x",
    "@testing-library/react": "^16.x",
    "@testing-library/jest-dom": "^6.x",
    "@testing-library/user-event": "^14.x",
    "msw": "^2.x",
    "jsdom": "^25.x",
    "eslint": "^9.x",
    "prettier": "^3.x",
    "eslint-plugin-react-hooks": "^5.x",
    "prettier-plugin-tailwindcss": "^0.6.x",
    "@playwright/test": "^1.x"
  }
}
```

---

## Appendix B: Relationship to Other TODOs

```
TODO-007 (Infrastructure)
    ├── 7a: This plan  <-- YOU ARE HERE
    │    ├── TODO-006 (Remote Diagnostics) — needs panel WebSocket relay
    │    └── TODO-010 (CF Zero Trust for panel) — configures CF Access
    ├── 7b: Astro Marketing Website (parallel, no dependency)
    └── 7c-7h: Monitoring, backup, docs, status (partially done)
```

**Key dependency chain:**
- TODO-006 (Remote Diagnostic Access) is **blocked on this panel** — the diagnostic WebSocket relay runs through the admin panel
- TODO-010 item 10a (Cloudflare Zero Trust) protects this panel — configure CF Access as part of Day 15 deployment
- The panel replaces the `canary:80` placeholder currently serving `panel.zyntapos.com`

---

## Appendix C: Admin API Authentication Flow (Implemented — see TODO-007f)

```
Browser → panel.zyntapos.com/login (ZyntaPOS branded login page)
   │
   ├── Cloudflare (DDoS/WAF/TLS — network security only; identity bypassed)
   │
   ├── Caddy (reverse proxy)
   │   └── Routes /admin/auth/* to api:8080
   │   └── Routes /admin/* to api:8080 (with JWT guard)
   │
   ├── Custom Login System (Ktor backend — TODO-007f)
   │   ├── POST /admin/auth/login → bcrypt verify → issue HS256 JWT + refresh token
   │   ├── POST /admin/auth/mfa/verify → TOTP check → issue full tokens
   │   ├── GET  /admin/auth/google → Google OAuth redirect (@zyntapos.com enforced)
   │   ├── POST /admin/auth/refresh → rotate refresh token
   │   └── POST /admin/auth/logout → clear cookies
   │
   └── AdminAuthGuard middleware
       └── Validates HS256 JWT from httpOnly cookie
       └── Injects AdminPrincipal(userId, email, role)
       └── Role-based access: ADMIN/OPERATOR/FINANCE/AUDITOR/HELPDESK
```

ZyntaPOS-branded login page. Custom password + MFA + Google SSO. Cloudflare handles DDoS/WAF/TLS only.
