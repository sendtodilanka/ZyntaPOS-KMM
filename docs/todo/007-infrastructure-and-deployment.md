# TODO-007: Infrastructure, Deployment & Backend Architecture

**Status:** Pending
**Priority:** HIGH — Foundation for licensing, sync, and remote diagnostics
**Phase:** Phase 2 (Growth)
**Created:** 2026-03-01

---

## Overview

Domain: `zyntapos.com` (registered at Namecheap)
VPS: Amazon Lightsail (currently running 3X-UI Xray VPN)

---

## Critical Issue: VPN + Production POS on Same VPS

**Running 3X-UI Xray VPN alongside production POS API on the same VPS is a serious risk.**

| Risk | Impact |
|------|--------|
| **IP reputation** | VPN exit traffic can get the IP blacklisted — API and email delivery breaks |
| **Attack surface** | VPN panels are heavily targeted by scanners/bots — breach exposes POS customer data |
| **Resource contention** | VPN traffic spikes starve the API of CPU/bandwidth |
| **PCI-DSS violation** | Payment-adjacent services must not share infrastructure with unrelated services |
| **Legal liability** | If VPN users do something illegal through the IP, the POS business domain is associated |

### Decision: Separate VPS Instances

- **VPS 1:** 3X-UI Xray VPN (existing Lightsail instance, keep as-is)
- **VPS 2:** ZyntaPOS services (new Lightsail instance, dedicated)

---

## Subdomain Architecture

```
zyntapos.com            -> Marketing website (static/SSR)
api.zyntapos.com        -> REST API (POS app backend)
panel.zyntapos.com      -> Admin dashboard (license mgmt, helpdesk, etc.)
docs.zyntapos.com       -> API documentation + knowledge base
status.zyntapos.com     -> Uptime status page (99.99% SLA proof)
sync.zyntapos.com       -> WebSocket endpoint for real-time sync
```

**Why separate `sync` from `api`?** WebSocket connections are long-lived. If they share the same process, a sync storm (100 POS terminals reconnecting after internet outage) can block REST API requests. Separate subdomain allows independent scaling and rate-limiting.

---

## DNS Configuration (Namecheap)

```
Type    Host      Value                          TTL
A       @         <lightsail-static-ip>          300
A       api       <lightsail-static-ip>          300
A       panel     <lightsail-static-ip>          300
A       sync      <lightsail-static-ip>          300
A       docs      <lightsail-static-ip>          300
CNAME   www       zyntapos.com                   300
CNAME   status    <statuspage-provider>          300
```

If using Cloudflare Pages for marketing site:
```
CNAME   @         zyntapos-site.pages.dev        300
```

---

## Technology Stack

### Reverse Proxy

```
+--------------------------------------------------+
|                 REVERSE PROXY                    |
|           Caddy (auto HTTPS, simpler)            |
|           OR Nginx + Certbot                     |
|                                                  |
|  zyntapos.com      -> :3000 (website)            |
|  api.zyntapos.com  -> :8080 (API)                |
|  panel.zyntapos.com -> :8081 (admin panel)       |
|  sync.zyntapos.com  -> :8082 (WebSocket sync)    |
|  docs.zyntapos.com  -> :3001 (docs)              |
|  status.zyntapos.com -> :3002 (status page)      |
+--------------------------------------------------+
```

**Recommendation:** Caddy — automatic Let's Encrypt HTTPS for all subdomains with zero configuration.

### Backend Services

| Component | Technology | Reason |
|-----------|-----------|--------|
| **API Framework** | Ktor Server (Kotlin) | Matches client-side Ktor. Shared serialization models. |
| **Database** | PostgreSQL 16 | Production-grade, JSONB for flexible payloads, excellent full-text search |
| **Cache** | Redis 7 | Session store, rate limiting, pub/sub for real-time sync |
| **Auth** | JWT (RS256) | Reuse existing `JwtManager` — shared validation on server side |
| **WebSocket** | Ktor WebSocket plugin | Real-time sync, diagnostic session relay |
| **Containerization** | Docker Compose | Isolate services, reproducible deployments |
| **SSL** | Caddy (auto Let's Encrypt) | Zero-config HTTPS for all subdomains |

### Website (zyntapos.com)

| Option | Pros | Cons |
|--------|------|------|
| **Astro** (Recommended) | Static-first, very fast, minimal JS | Newer ecosystem |
| **Next.js (SSR)** | SEO-friendly, huge ecosystem | Adds Node.js to stack |
| **Ktor + kotlinx.html** | Pure Kotlin | Smaller ecosystem for marketing sites |

**Recommendation:** Astro for the marketing site. It's static (cacheable on CDN), fast, and a marketing site doesn't need SSR complexity. Deploy to Cloudflare Pages for free (separate from VPS entirely).

### Admin Panel (panel.zyntapos.com)

| Option | Pros | Cons |
|--------|------|------|
| **React + TypeScript** (Recommended) | Mature, rich dashboard libraries (Recharts, TanStack Table) | Different language |
| **Compose for Web** | Share UI code with KMM app, visual consistency with ZyntaTheme | Still experimental |

---

## Docker Compose Layout

```yaml
# docker-compose.yml (ZyntaPOS VPS)
services:
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config
    # Auto-HTTPS for all subdomains

  api:
    build: ./zyntapos-api
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/zyntapos
      - REDIS_URL=redis://redis:6379
    depends_on: [postgres, redis]
    # Ktor server — REST API

  sync:
    build: ./zyntapos-sync
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/zyntapos
      - REDIS_URL=redis://redis:6379
    depends_on: [postgres, redis]
    # Ktor server — WebSocket sync engine

  panel:
    build: ./zyntapos-panel
    depends_on: [api]
    # Admin dashboard frontend + BFF

  postgres:
    image: postgres:16-alpine
    volumes: ["pgdata:/var/lib/postgresql/data"]
    environment:
      - POSTGRES_DB=zyntapos
      - POSTGRES_USER=zyntapos
      - POSTGRES_PASSWORD_FILE=/run/secrets/db_password
    # Encrypted at rest (Lightsail volume encryption)

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes: ["redisdata:/data"]
    # Session cache, rate limiting, pub/sub

  docs:
    build: ./zyntapos-docs
    # Static docs site

volumes:
  pgdata:
  redisdata:
  caddy_data:
  caddy_config:
```

---

## Panel Features Architecture

```
panel.zyntapos.com
|-- /dashboard          -> Overview: total licenses, active deployments, alerts
|-- /licenses           -> License CRUD, activation/deactivation, expiry management
|   |-- /licenses/:id   -> Single license detail + deployment status
|   +-- /licenses/generate -> Bulk license generation
|-- /deployments        -> Live deployment health (heartbeat monitoring)
|   |-- /deployments/:id -> Single deployment: version, last sync, errors
|   +-- /deployments/:id/diagnostic -> Remote diagnostic session launcher
|-- /helpdesk           -> Support ticket system
|   |-- /helpdesk/tickets -> Ticket list (open/in-progress/resolved)
|   +-- /helpdesk/:id    -> Ticket detail + chat thread
|-- /sync               -> Sync management
|   |-- /sync/queue      -> Pending sync operations across all deployments
|   +-- /sync/conflicts  -> Conflict resolution dashboard
|-- /updates            -> OTA update management
|   |-- /updates/versions -> Version registry
|   +-- /updates/rollout  -> Staged rollout controls
|-- /analytics          -> Aggregate (anonymized) usage metrics
+-- /settings           -> Panel user management, API keys, webhook config
```

---

## License System Design

### License Lifecycle

```
Generate -> Activate -> Active -> Renew / Expire
                          |
                    Heartbeat every 24h
                    (grace period: 7 days offline)
```

### License Model (Server-Side)

```kotlin
data class License(
    val id: String,                    // UUID
    val key: String,                   // ZYNTA-XXXX-XXXX-XXXX-XXXX (human-readable)
    val customerId: String,            // Customer account
    val edition: Edition,              // STARTER, PROFESSIONAL, ENTERPRISE
    val maxDevices: Int,               // Devices allowed under this license
    val activeDevices: List<DeviceRegistration>,
    val features: Set<FeatureFlag>,    // Feature gating per edition
    val issuedAt: Instant,
    val expiresAt: Instant,
    val lastHeartbeat: Instant?,       // Last check-in from POS app
    val status: LicenseStatus,         // ACTIVE, EXPIRED, SUSPENDED, REVOKED
)

enum class Edition {
    STARTER,        // Single store, basic POS
    PROFESSIONAL,   // Multi-device, reports, inventory
    ENTERPRISE,     // Multi-store, accounting, staff, API access
}

enum class LicenseStatus {
    ACTIVE,
    EXPIRED,
    SUSPENDED,      // Non-payment or policy violation
    REVOKED,        // Permanent deactivation
}
```

### Heartbeat Payload (POS App -> Server)

```kotlin
data class Heartbeat(
    val licenseKey: String,
    val deviceId: String,
    val appVersion: String,
    val osVersion: String,
    val dbSize: Long,
    val syncQueueDepth: Int,
    val lastErrorCount: Int,           // Errors in last 24h
    val uptimeHours: Double,
)
```

### Offline Grace Period

The POS app works fully offline. If it cannot reach the license server:
- **0-7 days:** Full functionality, no warnings
- **7-14 days:** Full functionality, warning banner shown
- **14+ days:** Read-only mode (can view data but not create new orders)

This prevents bricking a customer's POS during internet outages while still enforcing licensing.

---

## Lightsail VPS Sizing

| Tier | Specs | Cost | Capacity |
|------|-------|------|----------|
| **Starting** | 2 GB RAM / 1 vCPU | $12/mo | ~50 active licenses |
| **Growth** | 4 GB RAM / 2 vCPU | $24/mo | ~200 active licenses |
| **Scale point** | Migrate to AWS ECS/RDS | Variable | 500+ licenses |

---

## Security Measures

### API Security

- Rate limiting via Redis (per-IP and per-license-key)
- JWT RS256 (asymmetric — public key on POS app, private key on server)
- CORS whitelist (only POS app domains)
- Request body size limits
- SQL injection prevention (parameterized queries via Exposed/JOOQ)
- Input validation at API boundary
- HTTPS enforced (Caddy auto-redirect)

### Infrastructure Security

- Lightsail firewall: only ports 80, 443 open
- SSH key-only authentication (no password)
- Docker networks: services communicate internally only
- PostgreSQL: not exposed to public network
- Redis: password-protected, not exposed to public network
- Regular automated backups (Lightsail snapshots + pg_dump)
- Fail2ban on SSH

### Monitoring

- **Uptime Kuma** (self-hosted) or **BetterStack** (managed) for status page
- **Sentry** for error tracking (DSN already in local.properties template)
- **PostgreSQL** slow query logging
- **Caddy** access logs with request timing

---

## Implementation Order

1. **Provision new Lightsail VPS** — separate from VPN instance
2. **Configure DNS** — point subdomains to new VPS IP
3. **Set up Docker Compose** — Caddy + PostgreSQL + Redis
4. **Create Ktor Server API project** — shared domain models with KMM app
5. **Implement license system** — generation, activation, heartbeat
6. **Implement authentication** — JWT RS256 token issuance
7. **Implement sync engine server-side** — receive/send sync operations
8. **Build panel frontend** — license management dashboard
9. **Add remote diagnostic relay** — WebSocket tunnel via panel
10. **Set up monitoring** — Uptime Kuma + Sentry
11. **Deploy marketing website** — Astro on Cloudflare Pages
12. **Set up CI/CD** — GitHub Actions deploy to Lightsail

---

## Validation Checklist

- [ ] VPN and POS services on separate VPS instances
- [ ] All subdomains configured with auto-HTTPS
- [ ] Docker Compose running: Caddy, API, Sync, Panel, PostgreSQL, Redis
- [ ] License system: generate, activate, heartbeat, expire
- [ ] Offline grace period (7-day warning, 14-day read-only)
- [ ] JWT RS256 authentication working
- [ ] Rate limiting active on all API endpoints
- [ ] Sync engine: push/pull operations between POS app and server
- [ ] Panel: license management, deployment health, helpdesk
- [ ] Remote diagnostic WebSocket relay functional
- [ ] Monitoring: uptime checks, error tracking, status page
- [ ] Automated backups: Lightsail snapshots + pg_dump
- [ ] SSH hardened: key-only, Fail2ban, non-default port
- [ ] Marketing website live on Cloudflare Pages
