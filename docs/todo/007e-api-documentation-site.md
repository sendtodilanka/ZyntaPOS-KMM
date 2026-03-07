# TODO-007e — API Documentation Site (docs.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P2 (LOW)
**Status:** Ready to implement
**Effort:** ~5 working days (1 week, 1 developer)
**Related:** TODO-007 (infrastructure), TODO-007a (admin panel — consumes same APIs), TODO-007g (sync engine — documented here)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-06

---

## 1. Overview

Build a comprehensive API documentation site at `docs.zyntapos.com` covering all ZyntaPOS backend services — REST API, License API, Sync WebSocket, and Admin API. The site serves as the single source of truth for internal developers, integration partners, and future third-party developers building on the ZyntaPOS platform.

### Goals

- Interactive API reference with "Try It" functionality for all endpoints
- Auto-generated from OpenAPI 3.1 spec (single source of truth — spec file, not code comments)
- Authentication guide (JWT RS256 flow, token refresh, license activation)
- Sync protocol documentation (push/pull cycle, conflict resolution, WebSocket)
- Getting started guides for each integration scenario
- SDK code samples in Kotlin, TypeScript, and cURL
- Versioned documentation (v1 current, future v2)
- Search functionality across all docs
- Changelog / release notes for API changes

### Non-Goals (deferred)

- Public developer portal with API key self-service (Phase 3)
- SDK auto-generation from OpenAPI spec (Phase 3)
- Community forum / discussion board (Phase 3)
- Localized documentation (English only)
- Video tutorials

---

## 2. Technology Choice

### Selected: Mintlify

| Criteria | Mintlify | Stoplight | Redocly | Swagger UI | Docusaurus |
|----------|----------|-----------|---------|-----------|------------|
| OpenAPI support | Native 3.1 | Native 3.1 | Native 3.1 | Native 3.0 | Plugin |
| Try It (interactive) | Built-in | Built-in | Built-in | Built-in | No |
| Markdown pages | Yes (MDX) | Yes | Yes | No | Yes (MDX) |
| Custom domain | Yes | Yes (paid) | Yes | Manual | Manual |
| Self-hosted | Yes (Docker) | No (SaaS) | Yes | Yes | Yes |
| Search | Built-in | Built-in | Built-in | No | Algolia |
| Auth-gated docs | Yes | Yes (paid) | Yes | No | No |
| Code samples | Auto-gen | Auto-gen | Auto-gen | Auto-gen | Manual |
| Dark mode | Yes | Yes | Yes | No | Yes |
| Cost | Free (self-hosted) | $99/mo | Free (community) | Free | Free |
| Look & feel | Modern, polished | Enterprise | Clean | Basic | Blog-like |

**Decision:** Mintlify (self-hosted Docker) — best balance of polish, OpenAPI integration, and interactive "Try It" panels. Falls back to **Redocly** if Mintlify Docker setup proves problematic.

**Alternative approach:** If self-hosted Mintlify is too heavy, use **Scalar** (lightweight OpenAPI renderer, single HTML file, 200KB) as the interactive API reference + **Astro Starlight** for guide pages.

---

## 3. Architecture

```
                    ┌──────────────────────┐
                    │   Cloudflare (CDN)   │
                    │   Orange-cloud proxy  │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │   Caddy (VPS)        │
                    │   docs.zyntapos.com  │
                    │   → :3001 (docs)     │
                    └──────────┬───────────┘
                               │
          ┌────────────────────▼────────────────────┐
          │          Docs Container (:3001)          │
          │                                         │
          │  Static site built from:                │
          │  ├── openapi/                           │
          │  │   ├── api-v1.yaml     (REST API)    │
          │  │   ├── license-v1.yaml (License API) │
          │  │   ├── admin-v1.yaml   (Admin API)   │
          │  │   └── sync-v1.yaml    (Sync API)    │
          │  ├── guides/                            │
          │  │   ├── getting-started.mdx            │
          │  │   ├── authentication.mdx             │
          │  │   ├── sync-protocol.mdx              │
          │  │   ├── webhooks.mdx                   │
          │  │   └── error-handling.mdx             │
          │  └── changelog/                         │
          │      └── v1.0.0.mdx                     │
          └─────────────────────────────────────────┘
```

### Build Pipeline

```
openapi/*.yaml ─┐
                 ├──→ Build (npm run build) ──→ Static HTML/CSS/JS ──→ Docker image
guides/*.mdx ───┘
```

The docs site is **fully static** after build. No runtime dependencies on databases or backend services. The Docker container just serves static files via nginx.

---

## 4. OpenAPI Specifications

### 4.1 API Service Spec (`openapi/api-v1.yaml`)

Based on the existing 15 implemented endpoints + 35 planned admin endpoints:

```yaml
openapi: 3.1.0
info:
  title: ZyntaPOS REST API
  version: 1.0.0
  description: |
    Core REST API for ZyntaPOS Point of Sale system.
    Handles authentication, product catalog, and sync operations.
  contact:
    name: Zynta Solutions
    email: dev@zyntapos.com
  license:
    name: Proprietary
servers:
  - url: https://api.zyntapos.com
    description: Production
  - url: https://staging-api.zyntapos.com
    description: Staging (future)

security:
  - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: |
        JWT RS256 token obtained via `/v1/auth/login`.
        Include as `Authorization: Bearer <token>` header.
        Tokens expire after the duration specified in `expiresIn`.
        Use `/v1/auth/refresh` to obtain a new access token.

tags:
  - name: Auth
    description: Authentication and token management
  - name: Products
    description: Product catalog operations
  - name: Sync
    description: Offline-first sync push/pull
  - name: Admin - Licenses
    description: License management (admin panel only)
  - name: Admin - Stores
    description: Store management (admin panel only)
  - name: Admin - Users
    description: User management (admin panel only)
  - name: Admin - Audit
    description: Audit log viewer (admin panel only)
  - name: Admin - Metrics
    description: Dashboard KPIs and analytics (admin panel only)
  - name: Admin - Config
    description: Remote configuration push (admin panel only)
  - name: Admin - Alerts
    description: Alert rules and notification channels (admin panel only)
  - name: Admin - Sync
    description: Sync status and force-sync (admin panel only)
  - name: Health
    description: Service health checks
```

### 4.2 Endpoint Documentation Structure

Each endpoint spec includes:

```yaml
paths:
  /v1/auth/login:
    post:
      operationId: login
      summary: Authenticate user
      description: |
        Authenticate a POS terminal user with license key, device ID, and credentials.
        Returns JWT access and refresh tokens.

        **Rate limit:** 10 requests/minute per IP.

        **License validation:** The `licenseKey` must be active and the `deviceId`
        must be registered (or will be auto-registered if within `maxDevices` limit).
      tags: [Auth]
      security: []  # No auth required
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
            examples:
              standard:
                summary: Standard login
                value:
                  licenseKey: "ZYNTA-ABCD-1234-EFGH-5678"
                  deviceId: "android-tablet-001"
                  username: "cashier@store1.com"
                  password: "securepass123"
      responses:
        '200':
          description: Login successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        '401':
          description: Invalid credentials
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
              examples:
                invalidCreds:
                  value:
                    code: "AUTH_INVALID_CREDENTIALS"
                    message: "Invalid username or password"
        '403':
          description: License inactive or device limit reached
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '429':
          description: Rate limit exceeded
```

### 4.3 Full Endpoint Inventory for Specs

#### API Service (`api-v1.yaml`) — 20+ endpoints

| Group | Method | Path | Status |
|-------|--------|------|--------|
| Health | GET | `/health` | Implemented |
| Health | GET | `/ping` | Implemented |
| Auth | POST | `/v1/auth/login` | Implemented |
| Auth | POST | `/v1/auth/refresh` | Implemented |
| Products | GET | `/v1/products` | Implemented |
| Sync | POST | `/v1/sync/push` | Implemented |
| Sync | GET | `/v1/sync/pull` | Implemented |
| Admin - Stores | GET | `/admin/stores` | Planned (007a) |
| Admin - Stores | GET | `/admin/stores/{storeId}` | Planned (007a) |
| Admin - Stores | PUT | `/admin/stores/{storeId}/config` | Planned (007a) |
| Admin - Users | GET | `/admin/users` | Planned (007a) |
| Admin - Users | POST | `/admin/users` | Planned (007a) |
| Admin - Users | PUT | `/admin/users/{userId}` | Planned (007a) |
| Admin - Users | DELETE | `/admin/users/{userId}` | Planned (007a) |
| Admin - Audit | GET | `/admin/audit` | Planned (007a) |
| Admin - Audit | GET | `/admin/audit/export` | Planned (007a) |
| Admin - Metrics | GET | `/admin/metrics/dashboard` | Planned (007a) |
| Admin - Metrics | GET | `/admin/metrics/sales` | Planned (007a) |
| Admin - Metrics | GET | `/admin/metrics/stores` | Planned (007a) |
| Admin - Sync | GET | `/admin/sync/status` | Planned (007a) |
| Admin - Sync | POST | `/admin/sync/{storeId}/force` | Planned (007a) |
| Admin - Config | GET | `/admin/config/flags` | Planned (007a) |
| Admin - Config | PUT | `/admin/config/flags/{flagId}` | Planned (007a) |
| Admin - Config | GET | `/admin/config/tax-rates` | Planned (007a) |
| Admin - Config | PUT | `/admin/config/tax-rates/{id}` | Planned (007a) |
| Admin - Config | POST | `/admin/config/push` | Planned (007a) |

#### License Service (`license-v1.yaml`) — 10+ endpoints

| Group | Method | Path | Status |
|-------|--------|------|--------|
| Health | GET | `/health` | Implemented |
| Health | GET | `/ping` | Implemented |
| License | POST | `/v1/license/activate` | Implemented |
| License | POST | `/v1/license/heartbeat` | Implemented |
| License | GET | `/v1/license/{key}` | Implemented |
| Admin | GET | `/admin/licenses` | Planned (007a) |
| Admin | POST | `/admin/licenses` | Planned (007a) |
| Admin | PUT | `/admin/licenses/{key}` | Planned (007a) |
| Admin | DELETE | `/admin/licenses/{key}` | Planned (007a) |
| Admin | GET | `/admin/licenses/{key}/devices` | Planned (007a) |
| Admin | DELETE | `/admin/licenses/{key}/devices/{deviceId}` | Planned (007a) |
| Admin | GET | `/admin/licenses/stats` | Planned (007a) |

#### Sync Service (`sync-v1.yaml`) — WebSocket protocol

| Type | Path | Status |
|------|------|--------|
| WebSocket | `/v1/sync/ws` | Implemented |
| Health | `/health` | Implemented |
| Health | `/ping` | Implemented |

#### Admin Alerts (`admin-v1.yaml`) — 5 endpoints

| Group | Method | Path | Status |
|-------|--------|------|--------|
| Alerts | GET | `/admin/alerts/rules` | Planned (007a) |
| Alerts | POST | `/admin/alerts/rules` | Planned (007a) |
| Alerts | PUT | `/admin/alerts/rules/{id}` | Planned (007a) |
| Alerts | DELETE | `/admin/alerts/rules/{id}` | Planned (007a) |
| Alerts | GET | `/admin/alerts/history` | Planned (007a) |
| Channels | GET | `/admin/alerts/channels` | Planned (007a) |
| Channels | POST | `/admin/alerts/channels` | Planned (007a) |

---

## 5. Guide Pages

### 5.1 Navigation Structure

```
docs.zyntapos.com
├── Getting Started
│   ├── Introduction
│   ├── Quick Start (5-min tutorial)
│   ├── API Base URLs & Environments
│   └── Rate Limits & Quotas
├── Authentication
│   ├── Overview (JWT RS256 flow)
│   ├── Login Flow (license + credentials)
│   ├── Token Refresh
│   ├── License Activation
│   ├── Device Registration
│   └── Error Codes
├── POS API
│   ├── Products (GET /v1/products)
│   └── (future: Orders, Customers, Categories, etc.)
├── Sync Protocol
│   ├── Overview (offline-first architecture)
│   ├── Push Operations (POST /v1/sync/push)
│   ├── Pull Operations (GET /v1/sync/pull)
│   ├── WebSocket Real-time Sync
│   ├── Conflict Resolution (LWW + CRDT roadmap)
│   ├── Sync Status & Entity Types
│   └── Troubleshooting Sync Issues
├── License API
│   ├── Activation Flow
│   ├── Heartbeat Protocol
│   ├── Offline Grace Period
│   ├── Edition & Feature Gating
│   └── Error Codes
├── Admin API (internal)
│   ├── Overview & Authentication (CF Access)
│   ├── License Management
│   ├── Store Management
│   ├── User Management
│   ├── Audit Logs
│   ├── Metrics & Analytics
│   ├── Remote Configuration
│   ├── Alert Management
│   └── Sync Management
├── Webhooks (future)
│   ├── Overview
│   ├── Event Types
│   ├── Payload Format
│   └── Verification
├── Error Handling
│   ├── Error Response Format
│   ├── HTTP Status Codes
│   ├── Error Code Reference
│   └── Retry Strategy
├── SDKs & Tools
│   ├── Kotlin/KMM (built-in)
│   ├── TypeScript/JavaScript (future)
│   └── cURL Examples
├── API Reference (auto-generated from OpenAPI)
│   ├── REST API
│   ├── License API
│   ├── Admin API
│   └── Sync WebSocket
└── Changelog
    ├── v1.0.0 (current)
    └── Migration Guides
```

### 5.2 Key Guide Content

#### Getting Started (`guides/getting-started.mdx`)

```mdx
---
title: Getting Started
description: Get up and running with the ZyntaPOS API in 5 minutes
---

## Prerequisites

- A valid ZyntaPOS license key (contact sales@zyntapos.com)
- A registered device ID (assigned during onboarding)
- User credentials (username/password created by store admin)

## Base URLs

| Environment | Base URL |
|-------------|----------|
| Production  | `https://api.zyntapos.com` |
| License     | `https://license.zyntapos.com` |
| Sync (WS)   | `wss://sync.zyntapos.com` |

## Quick Start

### Step 1: Activate your license

```bash
curl -X POST https://license.zyntapos.com/v1/license/activate \
  -H "Content-Type: application/json" \
  -d '{
    "licenseKey": "ZYNTA-ABCD-1234-EFGH-5678",
    "deviceId": "my-device-001",
    "deviceName": "Counter Tablet",
    "appVersion": "1.0.0"
  }'
```

### Step 2: Login

```bash
curl -X POST https://api.zyntapos.com/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "licenseKey": "ZYNTA-ABCD-1234-EFGH-5678",
    "deviceId": "my-device-001",
    "username": "cashier@store1.com",
    "password": "your-password"
  }'
```

### Step 3: Fetch products

```bash
curl https://api.zyntapos.com/v1/products \
  -H "Authorization: Bearer <access_token>"
```
```

#### Sync Protocol Guide (`guides/sync-protocol.mdx`)

```mdx
---
title: Sync Protocol
description: How ZyntaPOS offline-first sync works
---

## Architecture

ZyntaPOS uses an **offline-first** architecture. All writes go to the local
SQLite database immediately. A background sync engine periodically pushes
local changes to the server and pulls remote changes.

## Push/Pull Cycle

```
Local DB → [pending_operations queue] → POST /v1/sync/push → Server
Server → GET /v1/sync/pull?since=<timestamp> → [apply delta] → Local DB
```

## Entity Types

30+ entity types are synced. Phase 1 includes:
- `PRODUCT`, `CATEGORY`, `CUSTOMER`, `ORDER`, `ORDER_ITEM`
- `SUPPLIER`, `TAX_GROUP`, `STOCK`, `SETTINGS`

## Conflict Resolution

**Phase 1:** Last-Write-Wins (LWW) by timestamp.
**Phase 2+:** Field-level CRDT merge with version vectors.

### LWW Algorithm

1. Compare `createdAt` timestamps → later wins
2. If timestamps equal → lexicographically larger `deviceId` wins
3. For PRODUCT entities → field-level merge (non-null fields from loser fill blanks)

## Sync Status Lifecycle

```
PENDING → SYNCING → SYNCED
                  ↘ FAILED (retry up to 5x)
```
```

#### Authentication Guide (`guides/authentication.mdx`)

```mdx
---
title: Authentication
description: JWT RS256 authentication flow for ZyntaPOS
---

## Overview

ZyntaPOS uses **JWT RS256** (asymmetric) tokens:
- **Private key:** On the server (signs tokens)
- **Public key:** In the POS app (verifies tokens locally)

## Token Claims

| Claim | Description |
|-------|-------------|
| `sub` | User ID (UUID) |
| `iss` | Token issuer |
| `aud` | Token audience |
| `exp` | Expiration (Unix epoch) |
| `iat` | Issued at (Unix epoch) |
| `role` | User role (ADMIN, MANAGER, CASHIER, etc.) |
| `storeId` | Store ID for multi-store isolation |
| `type` | "access" or "refresh" |

## Token Refresh Flow

```
┌──────────┐        ┌──────────┐
│ POS App  │        │  Server  │
└────┬─────┘        └────┬─────┘
     │  POST /v1/auth/login       │
     │──────────────────────────→│
     │  { accessToken, refreshToken, expiresIn }
     │←──────────────────────────│
     │                           │
     │  (token expires)          │
     │                           │
     │  POST /v1/auth/refresh    │
     │  { refreshToken }         │
     │──────────────────────────→│
     │  { accessToken, expiresIn }│
     │←──────────────────────────│
```

## Rate Limits

| Endpoint | Limit |
|----------|-------|
| Auth (`/v1/auth/*`) | 10 req/min per IP |
| API (`/v1/*`) | 300 req/min per IP |
| Sync (`/v1/sync/*`) | 60 req/min per IP |
| License activation | Strict (per license key) |
```

---

## 6. Error Code Reference

### 6.1 Standard Error Response

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid username or password"
}
```

### 6.2 Error Code Registry

| Code | HTTP Status | Description |
|------|------------|-------------|
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong username or password |
| `AUTH_TOKEN_EXPIRED` | 401 | JWT access token expired — use refresh |
| `AUTH_REFRESH_EXPIRED` | 401 | Refresh token expired — re-login required |
| `AUTH_INVALID_TOKEN` | 401 | Malformed or tampered JWT |
| `LICENSE_INACTIVE` | 403 | License is suspended, expired, or revoked |
| `LICENSE_DEVICE_LIMIT` | 403 | Maximum devices reached for this license |
| `LICENSE_NOT_FOUND` | 404 | License key not recognized |
| `SYNC_BATCH_TOO_LARGE` | 400 | Push batch exceeds 50 operations |
| `SYNC_CONFLICT` | 409 | Conflict detected — resolve via conflict log |
| `SYNC_INVALID_ENTITY` | 400 | Unknown entity type in sync operation |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests — retry after Retry-After header |
| `VALIDATION_ERROR` | 422 | Request body failed validation |
| `INTERNAL_ERROR` | 500 | Unexpected server error — retry with backoff |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

---

## 7. Docker Configuration

### 7.1 Dockerfile

**File:** `zyntapos-docs/Dockerfile`

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3001
CMD ["nginx", "-g", "daemon off;"]
```

### 7.2 nginx.conf

```nginx
server {
    listen 3001;
    server_name docs.zyntapos.com;
    root /usr/share/nginx/html;
    index index.html;

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets aggressively
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # Security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header Referrer-Policy strict-origin-when-cross-origin;

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
    gzip_min_length 256;
}
```

### 7.3 Docker Compose Addition

```yaml
  docs:
    build: ./zyntapos-docs
    container_name: zyntapos-docs
    restart: unless-stopped
    expose:
      - "3001"
    networks:
      - zyntapos-net
    deploy:
      resources:
        limits:
          memory: 64M
```

### 7.4 Caddy Route (already planned in 007)

```caddyfile
docs.zyntapos.com {
  import cloudflare_tls
  reverse_proxy docs:3001
}
```

---

## 8. Project Structure

```
zyntapos-docs/
├── openapi/
│   ├── api-v1.yaml                # REST API spec (Auth, Products, Sync)
│   ├── license-v1.yaml            # License API spec (Activate, Heartbeat)
│   ├── admin-v1.yaml              # Admin API spec (all /admin/* endpoints)
│   └── sync-v1.yaml               # Sync WebSocket protocol spec
├── guides/
│   ├── getting-started.mdx        # 5-min quick start
│   ├── authentication.mdx         # JWT RS256 flow, token refresh
│   ├── sync-protocol.mdx          # Offline-first sync architecture
│   ├── license-activation.mdx     # License lifecycle, grace periods
│   ├── webhooks.mdx               # Webhook event types (future)
│   ├── error-handling.mdx         # Error codes, retry strategy
│   └── rate-limits.mdx            # Per-endpoint rate limits
├── reference/
│   ├── error-codes.mdx            # Full error code registry
│   ├── entity-types.mdx           # 30+ sync entity type reference
│   └── roles-permissions.mdx      # RBAC roles and permission matrix
├── changelog/
│   └── v1.0.0.mdx                 # Initial release notes
├── assets/
│   ├── logo-dark.svg              # ZyntaPOS logo (dark mode)
│   ├── logo-light.svg             # ZyntaPOS logo (light mode)
│   └── diagrams/
│       ├── sync-flow.svg          # Sync push/pull sequence diagram
│       ├── auth-flow.svg          # Authentication flow diagram
│       └── license-lifecycle.svg  # License state machine
├── snippets/
│   ├── kotlin/                    # Kotlin code examples
│   ├── typescript/                # TypeScript code examples
│   └── curl/                      # cURL command examples
├── mint.json                      # Mintlify configuration
├── package.json
├── Dockerfile
├── nginx.conf
└── README.md
```

---

## 9. Mintlify Configuration

**File:** `zyntapos-docs/mint.json`

```json
{
  "$schema": "https://mintlify.com/schema.json",
  "name": "ZyntaPOS API Documentation",
  "logo": {
    "dark": "/assets/logo-dark.svg",
    "light": "/assets/logo-light.svg"
  },
  "favicon": "/assets/favicon.svg",
  "colors": {
    "primary": "#3b82f6",
    "light": "#60a5fa",
    "dark": "#1e40af",
    "background": {
      "dark": "#0f172a",
      "light": "#ffffff"
    }
  },
  "topbarLinks": [
    { "name": "Status", "url": "https://status.zyntapos.com" }
  ],
  "topbarCtaButton": {
    "name": "Admin Panel",
    "url": "https://panel.zyntapos.com"
  },
  "anchors": [
    { "name": "API Reference", "icon": "code", "url": "/api-reference" },
    { "name": "Changelog", "icon": "clock", "url": "/changelog" }
  ],
  "navigation": [
    {
      "group": "Getting Started",
      "pages": [
        "guides/getting-started",
        "guides/authentication",
        "guides/rate-limits"
      ]
    },
    {
      "group": "POS API",
      "pages": [
        "guides/sync-protocol",
        "guides/error-handling"
      ]
    },
    {
      "group": "License",
      "pages": [
        "guides/license-activation"
      ]
    },
    {
      "group": "Reference",
      "pages": [
        "reference/error-codes",
        "reference/entity-types",
        "reference/roles-permissions"
      ]
    },
    {
      "group": "API Reference",
      "pages": [
        { "openapi": "openapi/api-v1.yaml" },
        { "openapi": "openapi/license-v1.yaml" },
        { "openapi": "openapi/admin-v1.yaml" }
      ]
    },
    {
      "group": "Changelog",
      "pages": [
        "changelog/v1.0.0"
      ]
    }
  ],
  "api": {
    "baseUrl": ["https://api.zyntapos.com", "https://license.zyntapos.com"],
    "auth": {
      "method": "bearer"
    },
    "playground": {
      "mode": "simple"
    }
  },
  "footerSocials": {
    "github": "https://github.com/sendtodilanka/ZyntaPOS-KMM"
  }
}
```

---

## 10. Code Sample Strategy

Every endpoint gets examples in 3 formats:

### 10.1 cURL

```bash
# Login
curl -X POST https://api.zyntapos.com/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "licenseKey": "ZYNTA-ABCD-1234-EFGH-5678",
    "deviceId": "device-001",
    "username": "cashier@store1.com",
    "password": "password123"
  }'
```

### 10.2 Kotlin (KMM — using existing ApiService)

```kotlin
// Using the built-in KtorApiService
val response = apiService.login(
    AuthRequestDto(
        email = "cashier@store1.com",
        password = "password123",
        storeId = "store-001"
    )
)
// response.accessToken, response.refreshToken, response.expiresIn
```

### 10.3 TypeScript (for admin panel / third-party)

```typescript
const response = await fetch('https://api.zyntapos.com/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    licenseKey: 'ZYNTA-ABCD-1234-EFGH-5678',
    deviceId: 'device-001',
    username: 'cashier@store1.com',
    password: 'password123',
  }),
});
const { accessToken, refreshToken, expiresIn } = await response.json();
```

---

## 11. CI/CD for Docs

### 11.1 Auto-rebuild on Spec Changes

Add to `.github/workflows/docs.yml`:

```yaml
name: Build & Deploy Docs

on:
  push:
    branches: [main]
    paths:
      - 'zyntapos-docs/**'
      - 'backend/*/src/main/kotlin/**/routes/**'  # Rebuild on route changes

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: 'npm'
          cache-dependency-path: zyntapos-docs/package-lock.json

      - name: Install & Build
        working-directory: zyntapos-docs
        run: |
          npm ci
          npm run build

      - name: Build Docker image
        run: |
          docker build -t ghcr.io/sendtodilanka/zyntapos-docs:latest zyntapos-docs/

      - name: Push to GHCR
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker push ghcr.io/sendtodilanka/zyntapos-docs:latest

      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_USER_KEY }}
          port: ${{ secrets.VPS_PORT }}
          script: |
            cd /opt/zyntapos
            docker compose pull docs
            docker compose up -d docs
```

### 11.2 OpenAPI Lint in CI

Validate specs on every PR:

```yaml
      - name: Lint OpenAPI specs
        run: |
          npx @redocly/cli lint zyntapos-docs/openapi/api-v1.yaml
          npx @redocly/cli lint zyntapos-docs/openapi/license-v1.yaml
          npx @redocly/cli lint zyntapos-docs/openapi/admin-v1.yaml
```

---

## 12. Implementation Steps (Ordered)

| Step | Task | Time | Dependencies |
|------|------|------|-------------|
| 1 | Initialize `zyntapos-docs/` project with Mintlify or Scalar | 30 min | — |
| 2 | Write `openapi/api-v1.yaml` — Auth + Products + Sync endpoints | 3 hrs | Backend routes (existing) |
| 3 | Write `openapi/license-v1.yaml` — License endpoints | 1.5 hrs | License routes (existing) |
| 4 | Write `openapi/admin-v1.yaml` — All admin endpoints | 2 hrs | 007a plan |
| 5 | Write `openapi/sync-v1.yaml` — WebSocket protocol spec | 1 hr | 007g plan |
| 6 | Write `guides/getting-started.mdx` | 1 hr | Steps 2-3 |
| 7 | Write `guides/authentication.mdx` | 1 hr | Step 2 |
| 8 | Write `guides/sync-protocol.mdx` | 1.5 hrs | Step 5 |
| 9 | Write `guides/license-activation.mdx` | 1 hr | Step 3 |
| 10 | Write `guides/error-handling.mdx` + `reference/error-codes.mdx` | 1 hr | Steps 2-4 |
| 11 | Write `reference/entity-types.mdx` + `reference/roles-permissions.mdx` | 1 hr | Domain models |
| 12 | Add code samples (cURL, Kotlin, TypeScript) for all endpoints | 2 hrs | Steps 2-5 |
| 13 | Create architecture diagrams (SVG) | 1 hr | — |
| 14 | Configure `mint.json` navigation + branding | 30 min | Steps 6-11 |
| 15 | Create `Dockerfile` + `nginx.conf` | 30 min | — |
| 16 | Add to `docker-compose.yml` + Caddy route | 15 min | Docker Compose running |
| 17 | Add Cloudflare DNS A record for `docs` subdomain | 5 min | Cloudflare access |
| 18 | Deploy and verify `docs.zyntapos.com` loads | 15 min | Steps 15-17 |
| 19 | Add docs CI workflow (`.github/workflows/docs.yml`) | 30 min | — |
| 20 | Add OpenAPI lint to PR checks | 15 min | Step 19 |

**Total estimated time: ~5 working days**

---

## 13. Files to Create

```
zyntapos-docs/
├── openapi/
│   ├── api-v1.yaml                    # NEW — REST API OpenAPI spec
│   ├── license-v1.yaml                # NEW — License API OpenAPI spec
│   ├── admin-v1.yaml                  # NEW — Admin API OpenAPI spec
│   └── sync-v1.yaml                   # NEW — Sync WebSocket protocol spec
├── guides/
│   ├── getting-started.mdx            # NEW
│   ├── authentication.mdx             # NEW
│   ├── sync-protocol.mdx              # NEW
│   ├── license-activation.mdx         # NEW
│   ├── error-handling.mdx             # NEW
│   ├── rate-limits.mdx                # NEW
│   └── webhooks.mdx                   # NEW (placeholder)
├── reference/
│   ├── error-codes.mdx                # NEW
│   ├── entity-types.mdx               # NEW
│   └── roles-permissions.mdx          # NEW
├── changelog/
│   └── v1.0.0.mdx                     # NEW
├── assets/                            # NEW — logos, diagrams
├── snippets/                          # NEW — code samples
├── mint.json                          # NEW — site configuration
├── package.json                       # NEW
├── Dockerfile                         # NEW
├── nginx.conf                         # NEW
└── README.md                          # NEW

.github/workflows/docs.yml             # NEW — docs CI/CD
docker-compose.yml                     # MODIFY — add docs service
Caddyfile                              # MODIFY — add docs.zyntapos.com route
```

---

## 14. Validation Checklist

### Site Running
- [ ] `docs.zyntapos.com` loads in browser
- [ ] Dark mode toggle working
- [ ] Search returns results for "authentication", "sync", "license"
- [ ] ZyntaPOS branding (logo, colors) applied
- [ ] Mobile responsive layout working

### OpenAPI Specs
- [ ] `api-v1.yaml` passes Redocly lint (zero errors)
- [ ] `license-v1.yaml` passes Redocly lint
- [ ] `admin-v1.yaml` passes Redocly lint
- [ ] All specs render in interactive "Try It" panel
- [ ] Request/response examples shown for every endpoint
- [ ] Error responses documented for every endpoint

### Guide Pages
- [ ] Getting Started quick tutorial works end-to-end
- [ ] Authentication guide covers login → refresh → expiry flow
- [ ] Sync Protocol guide explains push/pull/conflict resolution
- [ ] License guide covers activation → heartbeat → grace period
- [ ] Error handling guide lists all error codes

### Code Samples
- [ ] cURL examples are copy-pasteable and work against production
- [ ] Kotlin examples match actual `ApiService` interface
- [ ] TypeScript examples use modern fetch API

### CI/CD
- [ ] Docs rebuild triggers on push to `main` (paths filter working)
- [ ] OpenAPI lint runs on PRs touching spec files
- [ ] Docker image builds and pushes to GHCR
- [ ] VPS deployment updates docs container automatically

### Infrastructure
- [ ] Docker container uses < 64MB RAM
- [ ] Cloudflare DNS record for `docs` subdomain active
- [ ] Caddy reverse proxy route working
- [ ] HTTPS (Cloudflare Origin cert) verified

---

## 15. Cost Analysis

| Item | Cost |
|------|------|
| Mintlify (self-hosted) | **Free** (MIT license) |
| Docker container RAM | ~20MB (nginx serving static files) |
| Cloudflare DNS + CDN | **Free** |
| CI build (GitHub Actions) | **Free** (public repo / included minutes) |
| **Total monthly cost** | **$0.00** |
