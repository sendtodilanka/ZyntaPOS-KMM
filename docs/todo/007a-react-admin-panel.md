# TODO-007a — React Admin Panel (panel.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P0 (HIGH)
**Status:** Ready to implement
**Effort:** ~15 working days (3 weeks, 1 developer)
**Related:** TODO-007 (infrastructure), TODO-006 (remote diagnostics), TODO-010 (security monitoring)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-06

---

## 1. Overview

Build an internal admin panel for centralized management of all ZyntaPOS deployments. The panel is a standalone React SPA served at `panel.zyntapos.com`, protected by Cloudflare Zero Trust (CF Access). It communicates with the existing Ktor backend services (`api.zyntapos.com`, `license.zyntapos.com`, `sync.zyntapos.com`) and adds new admin-specific API endpoints where needed.

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
- Helpdesk ticket system (Phase 3)
- Customer-facing self-service portal
- i18n (English only for internal tool)

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
| Auth | **Cloudflare Access JWT** | N/A | Zero Trust authentication — no custom auth UI needed |
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
│   │   ├── use-auth.ts                # CF Access JWT decoding, role check
│   │   ├── use-debounce.ts            # Debounced value hook
│   │   └── use-media-query.ts         # Responsive breakpoint hook
│   ├── stores/
│   │   ├── ui-store.ts                # Sidebar collapsed, theme, toasts
│   │   └── filter-store.ts            # Persisted filter state per page
│   ├── types/
│   │   ├── license.ts                 # License, Device, LicenseStatus types
│   │   ├── store.ts                   # Store, StoreHealth, StoreConfig types
│   │   ├── user.ts                    # AdminUser, Role, Permission types
│   │   ├── audit.ts                   # AuditEntry, AuditFilter types
│   │   ├── sync.ts                    # SyncStatus, SyncOperation types
│   │   ├── metrics.ts                 # KPI, ChartData, TimeSeriesPoint types
│   │   ├── config.ts                  # RemoteConfig, FeatureFlag, TaxRate types
│   │   ├── alert.ts                   # Alert, AlertRule, NotificationChannel types
│   │   └── api.ts                     # PagedResponse<T>, ErrorResponse, etc.
│   ├── api/                           # TanStack Query hooks per domain
│   │   ├── licenses.ts                # useLicenses, useCreateLicense, etc.
│   │   ├── stores.ts                  # useStores, useStoreHealth, etc.
│   │   ├── users.ts                   # useAdminUsers, useCreateUser, etc.
│   │   ├── audit.ts                   # useAuditLogs, useAuditExport, etc.
│   │   ├── sync.ts                    # useSyncStatus, useForceSync, etc.
│   │   ├── metrics.ts                 # useDashboardKPIs, useSalesChart, etc.
│   │   ├── config.ts                  # useRemoteConfig, usePushConfig, etc.
│   │   ├── alerts.ts                  # useAlerts, useAlertRules, etc.
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
│   │   ├── users/
│   │   │   ├── UserTable.tsx          # Admin user list
│   │   │   ├── UserCreateForm.tsx     # Create/edit admin user dialog
│   │   │   └── RoleAssignment.tsx     # Role picker component
│   │   ├── audit/
│   │   │   ├── AuditLogTable.tsx      # Paginated audit log with filters
│   │   │   ├── AuditFilterPanel.tsx   # Filter sidebar (date, event type, user, store)
│   │   │   └── AuditDetailModal.tsx   # Full detail view of single entry
│   │   ├── sync/
│   │   │   ├── SyncDashboard.tsx      # Per-store sync status grid
│   │   │   ├── SyncQueueView.tsx      # Pending operations queue view
│   │   │   └── ForceSyncButton.tsx    # Force re-sync action button
│   │   ├── config/
│   │   │   ├── ConfigEditor.tsx       # JSON/form editor for remote config
│   │   │   ├── FeatureFlagTable.tsx   # Feature flag toggle table
│   │   │   └── TaxRateEditor.tsx      # Tax rate CRUD form
│   │   └── alerts/
│   │       ├── AlertRuleTable.tsx     # Alert rule list with enable/disable
│   │       ├── AlertRuleForm.tsx      # Create/edit alert rule
│   │       ├── AlertHistory.tsx       # Alert firing history
│   │       └── NotificationChannelForm.tsx  # Slack/email/webhook config
│   ├── routes/                        # TanStack Router file-based routes
│   │   ├── __root.tsx                 # Root layout (AppShell)
│   │   ├── index.tsx                  # Dashboard (/)
│   │   ├── licenses/
│   │   │   ├── index.tsx              # License list (/licenses)
│   │   │   └── $licenseKey.tsx        # License detail (/licenses/:key)
│   │   ├── stores/
│   │   │   ├── index.tsx              # Store list (/stores)
│   │   │   └── $storeId.tsx           # Store detail (/stores/:id)
│   │   ├── users/
│   │   │   └── index.tsx              # User management (/users)
│   │   ├── audit/
│   │   │   └── index.tsx              # Audit log (/audit)
│   │   ├── sync/
│   │   │   └── index.tsx              # Sync monitoring (/sync)
│   │   ├── config/
│   │   │   └── index.tsx              # Remote configuration (/config)
│   │   ├── reports/
│   │   │   └── index.tsx              # Cross-store reports (/reports)
│   │   ├── health/
│   │   │   └── index.tsx              # System health (/health)
│   │   └── alerts/
│   │       └── index.tsx              # Alert management (/alerts)
│   └── test/
│       ├── setup.ts                   # Vitest setup (RTL matchers)
│       ├── mocks/
│       │   ├── handlers.ts            # MSW request handlers
│       │   └── server.ts              # MSW server setup
│       └── utils.tsx                  # Render helpers with providers
```

---

## 4. Authentication & Authorization

### 4.1 Cloudflare Access (Zero Trust)

The panel is protected by Cloudflare Access. No custom login page is needed.

**Flow:**
1. User navigates to `panel.zyntapos.com`
2. Cloudflare Access intercepts, presents OTP login (email: `*@zyntasolutions.com`)
3. On success, Cloudflare sets `CF_Authorization` cookie containing a signed JWT
4. The React app reads the JWT to extract user identity (`email`, `name`)
5. All API requests include the `CF_Authorization` cookie (same-origin via Caddy proxy)

**JWT validation:** The panel itself does NOT validate the JWT signature (Cloudflare does that at the edge). It only decodes the payload for display purposes. The backend API validates the JWT using Cloudflare's public key endpoint (`https://<team>.cloudflareaccess.com/cdn-cgi/access/certs`).

### 4.2 Backend Admin Authentication

New admin-specific API endpoints require a separate admin JWT (not the POS app JWT). The flow:

1. CF Access JWT is validated at the Caddy/API layer
2. The API issues a short-lived admin session token scoped to `role: SUPER_ADMIN`
3. All admin API calls include `Authorization: Bearer <admin-token>`
4. Admin tokens have a 1-hour TTL, auto-refreshed by the API client interceptor

### 4.3 Role-Based Access

| Role | Permissions |
|------|-------------|
| `SUPER_ADMIN` | Full access to all panel features |
| `SUPPORT` | Read-only access + force sync + view audit logs (no license create/revoke) |
| `VIEWER` | Read-only access to dashboards and reports |

Roles are checked client-side for UI gating and server-side for enforcement.

```typescript
// src/hooks/use-auth.ts
interface AdminUser {
  email: string;
  name: string;
  role: 'SUPER_ADMIN' | 'SUPPORT' | 'VIEWER';
  avatarUrl?: string;
}

function useAuth(): {
  user: AdminUser | null;
  isAuthenticated: boolean;
  hasPermission: (permission: string) => boolean;
  signOut: () => void;
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

#### Day 1 — Project Scaffolding & Infrastructure

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

**Deliverable:** `npm run dev` serves a blank app with routing, API client configured, Tailwind working.

---

#### Day 2 — Layout Shell & Navigation

| # | Task | Files | Details |
|---|------|-------|---------|
| 2.1 | Build AppShell layout | `src/components/layout/AppShell.tsx` | Sidebar + header + scrollable main content. Responsive: sidebar collapses to icon-only on narrow screens. |
| 2.2 | Build Sidebar component | `src/components/layout/Sidebar.tsx` | Route groups: Overview (Dashboard), Management (Licenses, Stores, Users), Monitoring (Sync, Health, Alerts), Intelligence (Audit, Reports, Config). Active route highlight, collapse toggle. |
| 2.3 | Build Header component | `src/components/layout/Header.tsx` | Breadcrumbs left, notification bell + user menu right |
| 2.4 | Build Breadcrumbs component | `src/components/layout/Breadcrumbs.tsx` | Auto-generated from TanStack Router context |
| 2.5 | Build UserMenu component | `src/components/layout/UserMenu.tsx` | CF Access email display, role badge, sign out action |
| 2.6 | Create Zustand UI store | `src/stores/ui-store.ts` | `sidebarCollapsed`, `theme` (always dark for v1), toast queue |
| 2.7 | Create auth hook | `src/hooks/use-auth.ts` | Decode `CF_Authorization` cookie JWT, extract email/name, provide `hasPermission()` helper |
| 2.8 | Wire root layout | `src/routes/__root.tsx` | Wrap with QueryClientProvider, render AppShell |
| 2.9 | Create shared utility components | `src/components/shared/LoadingState.tsx`, `EmptyState.tsx`, `ErrorBoundary.tsx` | Skeleton loader, "No data" placeholder, error boundary with retry |
| 2.10 | Build StatusBadge component | `src/components/shared/StatusBadge.tsx` | Color-coded pill: green=ACTIVE, yellow=EXPIRING_SOON, red=EXPIRED/REVOKED, gray=SUSPENDED |

**Deliverable:** Full layout shell with working navigation between stub pages, sidebar collapse, breadcrumbs.

---

#### Day 3 — Dashboard Page

| # | Task | Files | Details |
|---|------|-------|---------|
| 3.1 | Build KpiCard component | `src/components/shared/KpiCard.tsx` | Icon, title, value, trend (up/down arrow + percentage), subtitle. Supports loading skeleton. |
| 3.2 | Create metrics API hooks | `src/api/metrics.ts` | `useDashboardKPIs(period)`, `useSalesChart(params)`, `useStoreComparison(period)` — TanStack Query with 30s refetch |
| 3.3 | Build SalesChart component | `src/components/charts/SalesChart.tsx` | Recharts `AreaChart` with gradient fill, tooltip, responsive container. Toggle: daily/weekly/monthly. |
| 3.4 | Build StoreComparisonChart | `src/components/charts/StoreComparisonChart.tsx` | Recharts `BarChart` comparing revenue across stores |
| 3.5 | Build LicenseDistribution chart | `src/components/charts/LicenseDistribution.tsx` | Recharts `PieChart` showing license editions breakdown |
| 3.6 | Build UptimeChart component | `src/components/charts/UptimeChart.tsx` | Horizontal timeline showing uptime/downtime per service |
| 3.7 | Assemble Dashboard page | `src/routes/index.tsx` | 4 KPI cards (total stores, active licenses, revenue today, sync health %), 2-col chart grid (sales + store comparison), license distribution pie, recent alerts list |
| 3.8 | Add auto-refresh | Dashboard route | 30-second auto-refetch on all dashboard queries, visual "last updated" timestamp |

**Deliverable:** Fully functional dashboard with KPI cards, 4 charts, auto-refresh. Uses mock data if backend admin endpoints are not yet deployed.

---

#### Day 4 — License Management

| # | Task | Files | Details |
|---|------|-------|---------|
| 4.1 | Create license API hooks | `src/api/licenses.ts` | `useLicenses(filters)`, `useLicense(key)`, `useCreateLicense()`, `useUpdateLicense()`, `useRevokeLicense()`, `useLicenseStats()`, `useLicenseDevices(key)`, `useDeregisterDevice()` |
| 4.2 | Build DataTable component | `src/components/shared/DataTable.tsx` | Generic paginated table: column definitions, sortable headers, row selection, inline actions dropdown, page size selector, page navigation. Uses shadcn/ui Table. |
| 4.3 | Build SearchInput component | `src/components/shared/SearchInput.tsx` | Debounced (300ms) search with clear button, search icon |
| 4.4 | Build LicenseTable | `src/components/licenses/LicenseTable.tsx` | Columns: Key (masked), Edition, Status (badge), Max Devices, Active Devices, Expires, Last Heartbeat, Actions (View/Extend/Revoke). Filters: status, edition. Search by key/customer. |
| 4.5 | Build LicenseCreateForm | `src/components/licenses/LicenseCreateForm.tsx` | Dialog form: customer ID, edition (select), max devices (number), expiry date (optional). Zod validation. React Hook Form. |
| 4.6 | Build LicenseDetailCard | `src/components/licenses/LicenseDetailCard.tsx` | Full detail view: all license fields + device list + heartbeat history chart |
| 4.7 | Build LicenseExtendDialog | `src/components/licenses/LicenseExtendDialog.tsx` | Dialog: new expiry date picker, reason text field |
| 4.8 | Build DeviceList component | `src/components/licenses/DeviceList.tsx` | Table of registered devices: device ID, name, app version, OS, last seen, deregister button |
| 4.9 | Build ConfirmDialog | `src/components/shared/ConfirmDialog.tsx` | Reusable: title, description, confirm/cancel actions, destructive variant (red button) |
| 4.10 | Wire license routes | `src/routes/licenses/index.tsx`, `src/routes/licenses/$licenseKey.tsx` | List page with filters, detail page with device management |

**Deliverable:** Full license CRUD: list, create, view detail, extend expiry, revoke, manage devices.

---

#### Day 5 — Store Management

| # | Task | Files | Details |
|---|------|-------|---------|
| 5.1 | Create store API hooks | `src/api/stores.ts` | `useStores(filters)`, `useStore(storeId)`, `useStoreHealth(storeId)`, `useUpdateStoreConfig()` |
| 5.2 | Build StoreTable | `src/components/stores/StoreTable.tsx` | Columns: Store Name, Location, License Key, Status (health-based), Active Users, Last Sync, Actions |
| 5.3 | Build StoreHealthCard | `src/components/stores/StoreHealthCard.tsx` | Health metrics: DB size, sync queue depth, error count, uptime hours, last heartbeat. Color-coded health score. |
| 5.4 | Build StoreDetailPanel | `src/components/stores/StoreDetailPanel.tsx` | Tabs: Overview (health card + users), Configuration (config form), Sync (sync status + queue), Audit (store-scoped audit log) |
| 5.5 | Build StoreConfigForm | `src/components/stores/StoreConfigForm.tsx` | Form: tax rates, feature flags, store name, address, timezone. React Hook Form + Zod. Submit pushes config via `/admin/stores/{id}/config`. |
| 5.6 | Wire store routes | `src/routes/stores/index.tsx`, `src/routes/stores/$storeId.tsx` | List page, detail page with tabs |

**Deliverable:** Store listing with health indicators, detail view with config editor, health monitoring per store.

---

### Week 2 — Monitoring & Intelligence

---

#### Day 6 — User Management

| # | Task | Files | Details |
|---|------|-------|---------|
| 6.1 | Create user API hooks | `src/api/users.ts` | `useAdminUsers(filters)`, `useCreateUser()`, `useUpdateUser()`, `useDeactivateUser()` |
| 6.2 | Build UserTable | `src/components/users/UserTable.tsx` | Columns: Name, Email, Role (badge), Store, Status, Last Login, Actions (Edit/Deactivate) |
| 6.3 | Build UserCreateForm | `src/components/users/UserCreateForm.tsx` | Dialog form: username, email, password (generate or manual), role select, store assignment. Zod validation. |
| 6.4 | Build RoleAssignment | `src/components/users/RoleAssignment.tsx` | Role picker with permission preview. Roles: ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER (matches KMM RBAC). |
| 6.5 | Wire user route | `src/routes/users/index.tsx` | User list with role/store filters, create dialog, edit inline |

**Deliverable:** Full user CRUD with role assignment and store scoping.

---

#### Day 7 — Audit Log Viewer

| # | Task | Files | Details |
|---|------|-------|---------|
| 7.1 | Create audit API hooks | `src/api/audit.ts` | `useAuditLogs(filters)`, `useAuditExport(filters)` — paginated query with extensive filter params |
| 7.2 | Build DateRangePicker | `src/components/shared/DateRangePicker.tsx` | Preset ranges (today, 7d, 30d, custom) + calendar popover. Uses date-fns for formatting. |
| 7.3 | Build AuditFilterPanel | `src/components/audit/AuditFilterPanel.tsx` | Collapsible filter sidebar: date range, event type multi-select (grouped by category), user/role select, store select, entity type, success/failure toggle |
| 7.4 | Build AuditLogTable | `src/components/audit/AuditLogTable.tsx` | Columns: Timestamp, Event Type (badge), User, Store, Entity, Success (icon), Actions (View Detail). Infinite scroll or paginated (50/page). |
| 7.5 | Build AuditDetailModal | `src/components/audit/AuditDetailModal.tsx` | Full entry: all fields, previous/new values (JSON diff viewer), hash chain verification status |
| 7.6 | Build ExportButton | `src/components/shared/ExportButton.tsx` | Dropdown: "Export CSV" / "Export PDF". Triggers background export with progress toast. |
| 7.7 | Wire audit route | `src/routes/audit/index.tsx` | Filter panel + table + export. URL-synced filters (query params). |
| 7.8 | Implement CSV export | `src/lib/export.ts` | PapaParse serialization, blob download, date-stamped filename |

**Deliverable:** Full audit log viewer with multi-dimensional filtering, detail view, CSV/PDF export.

---

#### Day 8 — Sync Monitoring

| # | Task | Files | Details |
|---|------|-------|---------|
| 8.1 | Create sync API hooks | `src/api/sync.ts` | `useSyncStatus()`, `useStoreSync(storeId)`, `useForceSync(storeId)` |
| 8.2 | Build SyncDashboard | `src/components/sync/SyncDashboard.tsx` | Grid of store cards showing: sync queue depth, last sync time, sync latency, error count. Color-coded: green (<10 pending), yellow (10-50), red (>50 or stale). |
| 8.3 | Build SyncHealthChart | `src/components/charts/SyncHealthChart.tsx` | Recharts line chart: sync queue depth over time (24h), overlay with sync events |
| 8.4 | Build SyncQueueView | `src/components/sync/SyncQueueView.tsx` | Table of pending sync operations: entity type, operation, client timestamp, retry count. Expandable row shows payload preview. |
| 8.5 | Build ForceSyncButton | `src/components/sync/ForceSyncButton.tsx` | Confirm dialog, POST to `/admin/sync/{storeId}/force`, show result toast |
| 8.6 | Wire sync route | `src/routes/sync/index.tsx` | Dashboard grid + drill-down to per-store sync detail |

**Deliverable:** Real-time sync monitoring dashboard with per-store drill-down and force re-sync capability.

---

#### Day 9 — Remote Configuration

| # | Task | Files | Details |
|---|------|-------|---------|
| 9.1 | Create config API hooks | `src/api/config.ts` | `useFeatureFlags()`, `useToggleFlag()`, `useTaxRates()`, `useUpdateTaxRate()`, `usePushConfig()` |
| 9.2 | Build FeatureFlagTable | `src/components/config/FeatureFlagTable.tsx` | Table: flag name, description, enabled toggle (switch), scope (global / per-store), last modified. Toggle sends PUT immediately. |
| 9.3 | Build TaxRateEditor | `src/components/config/TaxRateEditor.tsx` | CRUD table: tax name, rate %, applicable stores, active toggle. Inline edit mode. |
| 9.4 | Build ConfigEditor | `src/components/config/ConfigEditor.tsx` | JSON editor for arbitrary config keys. Monaco-like textarea with syntax highlighting (use a simple code editor or plain textarea for v1). Preview diff before push. |
| 9.5 | Implement config push flow | `src/components/config/ConfigEditor.tsx` | Select target stores (multi-select or "all"), preview changes, confirm dialog, push via `/admin/config/push`, show success/failure per store. |
| 9.6 | Wire config route | `src/routes/config/index.tsx` | Tabs: Feature Flags, Tax Rates, Advanced (JSON editor) |

**Deliverable:** Feature flag toggles, tax rate management, arbitrary config push to selected stores.

---

#### Day 10 — Reporting & Analytics

| # | Task | Files | Details |
|---|------|-------|---------|
| 10.1 | Create report API hooks | `src/api/metrics.ts` (extend) | `useSalesReport(params)`, `useProductPerformance(params)`, `useStoreRanking(params)` |
| 10.2 | Build report page layout | `src/routes/reports/index.tsx` | Tabs: Sales, Products, Stores. Each tab has date range picker + store selector + chart + data table. |
| 10.3 | Build Sales report tab | Within reports page | Revenue line chart, order count, average order value, payment method breakdown (pie). Table: daily/weekly/monthly rows with totals. |
| 10.4 | Build Product Performance tab | Within reports page | Top 10 products bar chart, sortable table: product, units sold, revenue, margin %. |
| 10.5 | Build Store Ranking tab | Within reports page | Horizontal bar chart ranking stores by revenue. Table: store, revenue, orders, avg order value, growth %. |
| 10.6 | Implement PDF export | `src/lib/export.ts` | `@react-pdf/renderer` template: branded header, date range, charts as images, data table, footer with generation timestamp |
| 10.7 | Implement CSV export for reports | `src/lib/export.ts` | Export report data as CSV with appropriate column headers |

**Deliverable:** Cross-store analytics with 3 report types, interactive charts, CSV and PDF export.

---

### Week 3 — Health, Alerts, Polish & Deployment

---

#### Day 11 — System Health Monitoring

| # | Task | Files | Details |
|---|------|-------|---------|
| 11.1 | Create health API hooks | `src/api/health.ts` | `useServiceHealth()`, `useStoreHealth()` — polls every 30s |
| 11.2 | Build service health dashboard | `src/routes/health/index.tsx` | Grid: API, License, Sync, PostgreSQL, Redis — each with status indicator (green/yellow/red), response time, uptime %, last check time |
| 11.3 | Build per-store health panel | Within health page | Expandable rows per store: DB size, sync queue, error rate, app version, OS, last heartbeat |
| 11.4 | Build health detail component | `src/components/shared/HealthDetail.tsx` | Sparkline charts for response time and error rate (last 24h) |
| 11.5 | Add WebSocket for real-time health | `src/lib/api-client.ts` | Optional: WebSocket connection for live health updates (falls back to polling if WS unavailable) |

**Deliverable:** Real-time system health overview with per-service and per-store diagnostics.

---

#### Day 12 — Alert Management

| # | Task | Files | Details |
|---|------|-------|---------|
| 12.1 | Create alert API hooks | `src/api/alerts.ts` | `useAlertRules()`, `useCreateAlertRule()`, `useUpdateAlertRule()`, `useDeleteAlertRule()`, `useAlertHistory()`, `useNotificationChannels()`, `useCreateChannel()` |
| 12.2 | Build AlertRuleTable | `src/components/alerts/AlertRuleTable.tsx` | Columns: Name, Metric, Condition (e.g., "> 100"), Channels, Enabled toggle, Last Fired, Actions |
| 12.3 | Build AlertRuleForm | `src/components/alerts/AlertRuleForm.tsx` | Dialog: name, metric (dropdown: sync_queue_depth, error_rate, heartbeat_age, db_size, response_time), operator (>, <, =), threshold, notification channels (multi-select), cooldown period |
| 12.4 | Build NotificationChannelForm | `src/components/alerts/NotificationChannelForm.tsx` | Dialog: type (Slack webhook, email, generic webhook), config (URL/email), test button |
| 12.5 | Build AlertHistory | `src/components/alerts/AlertHistory.tsx` | Table: timestamp, rule name, metric value, store, status (fired/resolved), acknowledged toggle |
| 12.6 | Wire alert route | `src/routes/alerts/index.tsx` | Tabs: Rules, History, Channels |

**Deliverable:** Alert rule CRUD, notification channel configuration, alert history viewer.

---

#### Day 13 — Testing

| # | Task | Files | Details |
|---|------|-------|---------|
| 13.1 | Set up Vitest + RTL | `vitest.config.ts`, `src/test/setup.ts` | jsdom environment, RTL matchers, path aliases |
| 13.2 | Set up MSW for API mocking | `src/test/mocks/handlers.ts`, `src/test/mocks/server.ts` | Mock all admin API endpoints with realistic data |
| 13.3 | Create test render helper | `src/test/utils.tsx` | Custom render with QueryClientProvider, RouterProvider, Zustand store |
| 13.4 | Write component tests | `src/components/**/*.test.tsx` | Priority: DataTable, KpiCard, LicenseTable, AuditLogTable, StatusBadge, ConfirmDialog |
| 13.5 | Write hook tests | `src/hooks/*.test.ts`, `src/api/*.test.ts` | Test useAuth, useLicenses (query caching, error states), useForceSync (mutation) |
| 13.6 | Write route/page tests | `src/routes/**/*.test.tsx` | Test Dashboard renders KPIs, License list renders table, Audit filters work |
| 13.7 | Set up Playwright | `playwright.config.ts`, `e2e/` | Configure 3 browsers, CI-friendly settings |
| 13.8 | Write E2E smoke tests | `e2e/smoke.spec.ts` | Dashboard loads, navigate to licenses, create license flow, audit log search |

**Deliverable:** 80%+ component test coverage, E2E smoke test suite passing.

---

#### Day 14 — CI/CD Pipeline & Docker

| # | Task | Files | Details |
|---|------|-------|---------|
| 14.1 | Create GitHub Actions workflow | `.github/workflows/ci-admin-panel.yml` | Trigger on push/PR to `admin-panel/**`. Steps: install, lint, type-check, test, build, upload artifact |
| 14.2 | Add Docker image build to CI | `.github/workflows/ci-gate.yml` (modify) | Add `build-admin-panel` job: build Docker image → push to GHCR as `ghcr.io/sendtodilanka/zyntapos-panel:latest` |
| 14.3 | Finalize Dockerfile | `admin-panel/Dockerfile` | Multi-stage build. Stage 1: `node:22-alpine`, `npm ci`, `npm run build`. Stage 2: `nginx:alpine`, copy `dist/`, add `nginx.conf`. |
| 14.4 | Create nginx config | `admin-panel/nginx.conf` | SPA fallback (`try_files $uri $uri/ /index.html`), gzip, security headers (CSP, X-Frame-Options, etc.), static asset caching |
| 14.5 | Add panel service to docker-compose.yml | `docker-compose.yml` | New `panel` service: image from GHCR, expose 3000, health check, read_only, security hardening (same pattern as other services) |
| 14.6 | Update Caddyfile | `Caddyfile` | Change `panel.zyntapos.com` from `canary:80` to `panel:3000`. Add proxy headers for API passthrough. |
| 14.7 | Add admin API routes to backend | Backend route files | Wire `/admin/*` routes in API and License services with admin JWT guard |

**Deliverable:** CI pipeline builds and pushes Docker image. Panel container runs alongside existing services.

---

#### Day 15 — Polish, Deployment & Documentation

| # | Task | Files | Details |
|---|------|-------|---------|
| 15.1 | Responsive design audit | All components | Verify sidebar collapse at <1024px, table horizontal scroll on mobile, chart responsive containers |
| 15.2 | Loading state polish | All route pages | Every page has proper loading skeletons, error states with retry buttons, empty states |
| 15.3 | Toast notification system | `src/stores/ui-store.ts`, AppShell | Wire success/error toasts for all mutations (create, update, delete, force sync) |
| 15.4 | Keyboard shortcuts | `src/hooks/use-keyboard.ts` | `/` focus search, `Escape` close dialogs, `Ctrl+K` command palette (stretch goal) |
| 15.5 | Dark theme fine-tuning | `src/globals.css` | Ensure all components work in dark theme, proper contrast ratios (WCAG AA) |
| 15.6 | Deploy to VPS | VPS SSH | Pull latest images, `docker compose up -d`, verify `panel.zyntapos.com` loads |
| 15.7 | Configure Cloudflare Access | CF Zero Trust dashboard | Create Access Application for `panel.zyntapos.com`, policy: Allow `*@zyntasolutions.com` |
| 15.8 | Verify end-to-end | Manual testing | Login via CF Access, navigate all pages, create/revoke license, view audit logs, export report |
| 15.9 | Update execution plan | `docs/todo/000-execution-plan.md` | Mark 007a as complete, update Phase 2 status |
| 15.10 | Update gap analysis | `docs/todo/GAP-ANALYSIS-AND-FILL-PLAN.md` | Mark 7a as done |

**Deliverable:** Production-ready admin panel live at `panel.zyntapos.com` behind CF Access.

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

### 10.2 Admin Authentication Guard

All `/admin/*` routes require a middleware that:
1. Validates the CF Access JWT (via Cloudflare's public key)
2. Checks the user email is in the admin allowlist
3. Injects admin context into the request

```kotlin
// backend/common/src/main/kotlin/.../auth/AdminAuthGuard.kt
fun Route.adminAuth(block: Route.() -> Unit) {
    authenticate("cf-access") {
        // Verify email is in admin allowlist
        // Inject AdminPrincipal
        block()
    }
}
```

### 10.3 Database Migrations

New tables needed in `zyntapos_api`:

```sql
-- V3__admin_tables.sql

CREATE TABLE admin_users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'VIEWER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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
| Backend admin API endpoints | Needs implementation | Full functionality (can use mock data during dev) |
| Cloudflare Access configuration | Needs CF dashboard config | Production auth |
| Docker image push to GHCR | Needs CI pipeline update | Deployment |
| Caddyfile update on VPS | Needs VPS SSH | Panel accessibility |
| Brand assets (logo SVG) | Available from marketing site | Header, favicon |

**No hard blockers for starting development.** The panel can be built against mock data and connected to real APIs incrementally.

---

## 12. Exit Criteria

### Functional Requirements

- [ ] Dashboard renders with 4 KPI cards and 4 charts (auto-refreshing every 30s)
- [ ] License management: list, create, view detail, extend, revoke, manage devices
- [ ] Store management: list stores, view health, edit configuration
- [ ] User management: list, create, update role, deactivate
- [ ] Audit log viewer: multi-filter search (date, type, user, store, success), pagination (50/page), CSV export
- [ ] Sync monitoring: per-store sync status grid, queue depth visualization, force re-sync
- [ ] Remote configuration: feature flag toggles, tax rate CRUD, config push to selected stores
- [ ] Reports: sales/product/store analytics with CSV and PDF export
- [ ] System health: per-service status with response time, per-store health metrics
- [ ] Alert management: rule CRUD, notification channels, alert history

### Non-Functional Requirements

- [ ] Authentication via Cloudflare Access (no custom login page)
- [ ] All API calls authenticated with admin JWT
- [ ] Responsive layout (works at 1024px+ for admin use)
- [ ] Dark theme consistent with ZyntaPOS brand
- [ ] Loading skeletons on every page
- [ ] Error boundaries with retry
- [ ] 80%+ component test coverage (Vitest)
- [ ] E2E smoke tests passing (Playwright)
- [ ] Docker image builds and runs successfully
- [ ] CI pipeline: lint + type-check + test + build on every PR
- [ ] Deployed at `panel.zyntapos.com` behind CF Access
- [ ] Caddyfile updated from canary to real panel service

### Infrastructure Requirements

- [ ] `panel` service added to `docker-compose.yml` with security hardening
- [ ] CI builds Docker image and pushes to GHCR
- [ ] Cloudflare Access Application created for `panel.zyntapos.com`
- [ ] Admin API routes protected by CF Access JWT validation
- [ ] Database migrations applied for admin tables

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

## Appendix C: Admin API Authentication Flow

```
Browser → panel.zyntapos.com
   │
   ├── Cloudflare Access (edge)
   │   └── OTP to @zyntasolutions.com
   │   └── Sets CF_Authorization cookie (signed JWT)
   │
   ├── Caddy (reverse proxy)
   │   └── Routes /api/* to api:8080
   │   └── Routes /license-api/* to license:8083
   │
   └── Ktor Backend
       └── AdminAuthGuard middleware
           └── Validates CF Access JWT signature
           └── Checks email in admin allowlist
           └── Returns admin-scoped data
```

No custom login page. No password management. Cloudflare handles all authentication. The backend only needs to verify the CF Access JWT and check authorization.
