# TODO-007c — Monitoring & Alerting Setup (status.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P0 (HIGH)
**Status:** ✅ 100% COMPLETE — Uptime Kuma deployed in docker-compose (louislam/uptime-kuma:2), Caddy route for status.zyntapos.com configured, health endpoints (/health, /health/db, /health/redis, /ping) in all backend services, Falcosidekick Slack alerting configured. Verified 2026-03-12.
**Effort:** ~4 hours (single session)
**Related:** TODO-007 (infrastructure), TODO-007a (admin panel), TODO-010 (security monitoring)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-06

---

## 1. Overview

Set up a comprehensive monitoring and alerting system for all ZyntaPOS backend services. This includes a self-hosted Uptime Kuma instance for health checks, a public status page at `status.zyntapos.com`, and alert routing to Discord/Slack/Email for instant incident notification.

**Why this matters:** Without monitoring, service outages go undetected until customers report them. POS systems are mission-critical — a restaurant or retail store cannot operate if the API, license server, or sync engine is down. Monitoring is the first line of defense for maintaining 99.9% uptime SLA.

### Goals

- Real-time health monitoring of all 6 subdomains + internal services
- Public-facing status page proving SLA compliance to customers
- Instant alerts (< 60 seconds) when any service goes down
- Historical uptime data (90-day graphs) for SLA reporting
- Certificate expiry monitoring for all TLS endpoints
- Response time tracking and latency anomaly detection
- Integration with admin panel dashboard (7a) for unified view

### Non-Goals (deferred)

- APM-level tracing (Jaeger/OpenTelemetry — Phase 3)
- Log aggregation (Loki/Grafana stack — Phase 3)
- Custom metrics dashboards (Prometheus + Grafana — Phase 3)
- Synthetic user journey monitoring (Phase 3)

---

## 2. Technology Choice

### Selected: Uptime Kuma (Self-Hosted)

| Criteria | Uptime Kuma | BetterStack (SaaS) | Hetrixtools |
|----------|-------------|---------------------|-------------|
| Cost | **Free** (self-hosted) | $25/mo (starter) | Free tier (15 monitors) |
| Hosting | Docker on Contabo VPS | Managed | Managed |
| Status page | Built-in | Built-in | Built-in |
| Custom domain | Yes (`status.zyntapos.com`) | Yes (paid) | No (free tier) |
| Alert channels | 90+ (Discord, Slack, Email, Telegram, Webhook) | 10+ | 5 |
| Maintenance windows | Yes | Yes | Yes |
| API | REST API | REST API | No |
| Data sovereignty | Full (your VPS) | US/EU cloud | US cloud |
| Certificate monitoring | Yes | Yes | No |

**Decision:** Uptime Kuma — zero cost, full control, 90+ notification integrations, runs as a single Docker container with < 100MB RAM.

---

## 3. Architecture

```
                    ┌──────────────────────┐
                    │   Cloudflare (CDN)   │
                    │   Orange-cloud proxy  │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │   Caddy (reverse     │
                    │   proxy on VPS)      │
                    │                      │
                    │   status.zyntapos.com│
                    │   → :3001 (Kuma)     │
                    └──────────┬───────────┘
                               │
          ┌────────────────────▼────────────────────┐
          │          Uptime Kuma (:3001)             │
          │                                         │
          │  Monitors (internal Docker network):    │
          │  ├── api:8080/health         (HTTP)     │
          │  ├── license:8083/health     (HTTP)     │
          │  ├── sync:8082/health        (HTTP)     │
          │  ├── panel:8081              (HTTP)     │
          │  ├── postgres:5432           (TCP)      │
          │  ├── redis:6379              (TCP)      │
          │  │                                      │
          │  External monitors:                     │
          │  ├── api.zyntapos.com        (HTTPS)    │
          │  ├── license.zyntapos.com    (HTTPS)    │
          │  ├── sync.zyntapos.com       (HTTPS)    │
          │  ├── panel.zyntapos.com      (HTTPS)    │
          │  ├── zyntapos.com            (HTTPS)    │
          │  └── docs.zyntapos.com       (HTTPS)    │
          │                                         │
          │  Alerts → Discord / Email / Webhook     │
          │  Status Page → status.zyntapos.com      │
          └─────────────────────────────────────────┘
```

### Why Both Internal + External Monitors?

| Type | What it tests | Catches |
|------|--------------|---------|
| **Internal** (Docker network) | Service health directly | Container crashes, OOM kills, DB connection pool exhaustion |
| **External** (public URL) | Full request path via Cloudflare + Caddy | DNS failures, Cloudflare outages, TLS cert issues, Caddy misconfig |

If internal is UP but external is DOWN → Cloudflare or Caddy issue.
If internal is DOWN but external is also DOWN → service itself is dead.

---

## 4. Docker Compose Integration

### 4.1 Add to `docker-compose.yml`

```yaml
  # Monitoring — Uptime Kuma
  uptime-kuma:
    image: louislam/uptime-kuma:1
    container_name: zyntapos-uptime-kuma
    restart: unless-stopped
    expose:
      - "3001"
    volumes:
      - uptime_kuma_data:/app/data
    networks:
      - zyntapos-net
    environment:
      - UPTIME_KUMA_DISABLE_FRAME_SAMEORIGIN=true  # Allow iframe in admin panel
    healthcheck:
      test: ["CMD", "node", "-e", "require('http').get('http://localhost:3001', (r) => r.statusCode === 200 ? process.exit(0) : process.exit(1))"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          memory: 256M
        reservations:
          memory: 64M
```

### 4.2 Add Volume

```yaml
volumes:
  uptime_kuma_data:
```

### 4.3 Add Caddy Route

```caddyfile
status.zyntapos.com {
  import cloudflare_tls
  reverse_proxy uptime-kuma:3001
}
```

### 4.4 Cloudflare DNS Record

| Type | Name | Value | Proxy |
|------|------|-------|-------|
| A | `status` | `<CONTABO_VPS_IP>` | ✅ Orange |

---

## 5. Monitor Configuration

### 5.1 Internal Service Monitors (Docker Network)

| # | Monitor Name | Type | URL / Host | Interval | Retries | Alert After |
|---|-------------|------|-----------|----------|---------|-------------|
| 1 | API (internal) | HTTP | `http://api:8080/health` | 30s | 3 | 90s |
| 2 | License Server (internal) | HTTP | `http://license:8083/health` | 30s | 3 | 90s |
| 3 | Sync Engine (internal) | HTTP | `http://sync:8082/health` | 30s | 3 | 90s |
| 4 | Admin Panel (internal) | HTTP | `http://panel:8081` | 60s | 2 | 120s |
| 5 | PostgreSQL | TCP Port | `postgres:5432` | 30s | 3 | 90s |
| 6 | Redis | TCP Port | `redis:6379` | 30s | 3 | 90s |

### 5.2 External Monitors (Public URLs)

| # | Monitor Name | Type | URL | Interval | Retries | Alert After |
|---|-------------|------|-----|----------|---------|-------------|
| 7 | API (public) | HTTPS (keyword) | `https://api.zyntapos.com/health` | 60s | 3 | 180s |
| 8 | License (public) | HTTPS (keyword) | `https://license.zyntapos.com/health` | 60s | 3 | 180s |
| 9 | Sync (public) | HTTPS | `https://sync.zyntapos.com/health` | 60s | 3 | 180s |
| 10 | Panel (public) | HTTPS | `https://panel.zyntapos.com` | 120s | 2 | 240s |
| 11 | Marketing Site | HTTPS (keyword) | `https://zyntapos.com` | 300s | 2 | 600s |
| 12 | Docs Site | HTTPS | `https://docs.zyntapos.com` | 300s | 2 | 600s |

### 5.3 TLS Certificate Monitors

| # | Monitor Name | Type | Domain | Alert Before Expiry |
|---|-------------|------|--------|-------------------|
| 13 | TLS — api.zyntapos.com | Certificate Expiry | `api.zyntapos.com` | 30 days |
| 14 | TLS — license.zyntapos.com | Certificate Expiry | `license.zyntapos.com` | 30 days |
| 15 | TLS — sync.zyntapos.com | Certificate Expiry | `sync.zyntapos.com` | 30 days |
| 16 | TLS — zyntapos.com | Certificate Expiry | `zyntapos.com` | 30 days |

> **Note:** Cloudflare Origin Certificates are 15-year validity, so these won't fire often. But they catch edge cases (accidental cert deletion, Cloudflare config changes).

### 5.4 Custom Health Check Monitors

| # | Monitor Name | Type | URL | Expected Response | Purpose |
|---|-------------|------|-----|------------------|---------|
| 17 | DB Connection Pool | HTTP (keyword) | `http://api:8080/health/db` | `"status":"ok"` | Detects pool exhaustion |
| 18 | Redis Connection | HTTP (keyword) | `http://api:8080/health/redis` | `"status":"ok"` | Detects Redis auth failures |
| 19 | Disk Space | HTTP (keyword) | `http://api:8080/health/disk` | `"status":"ok"` | Alert when disk > 85% |

**Total monitors: 19**

---

## 6. Health Check Endpoints (Backend Implementation)

The API, License, and Sync servers need health endpoints. Add to each Ktor service:

### 6.1 Basic Health Route

**File:** `backend/api/src/main/kotlin/routes/HealthRoutes.kt`

```kotlin
fun Route.healthRoutes() {
    route("/health") {
        // Basic liveness — returns 200 if process is running
        get {
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to "ok",
                "service" to "zyntapos-api",
                "timestamp" to Clock.System.now().toString(),
                "version" to BuildConfig.VERSION
            ))
        }

        // DB connection check
        get("/db") {
            try {
                // Execute a simple query to verify DB connectivity
                val result = database.healthCheck() // SELECT 1
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ok",
                    "pool_active" to result.activeConnections,
                    "pool_idle" to result.idleConnections,
                    "pool_max" to result.maxConnections
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "status" to "error",
                    "error" to e.message
                ))
            }
        }

        // Redis connection check
        get("/redis") {
            try {
                val pong = redis.ping() // PING → PONG
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ok",
                    "latency_ms" to pong.latencyMs
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf(
                    "status" to "error",
                    "error" to e.message
                ))
            }
        }

        // Disk space check
        get("/disk") {
            val usedPercent = diskUsagePercent("/var/lib/postgresql/data")
            val status = if (usedPercent < 85) "ok" else "warning"
            call.respond(
                if (usedPercent < 95) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                mapOf(
                    "status" to status,
                    "disk_used_percent" to usedPercent
                )
            )
        }
    }
}
```

### 6.2 Health Check Response Contract

```json
// GET /health — 200 OK
{
  "status": "ok",
  "service": "zyntapos-api",
  "timestamp": "2026-03-06T10:30:00Z",
  "version": "1.0.0-build.42"
}

// GET /health/db — 200 OK or 503 Service Unavailable
{
  "status": "ok",
  "pool_active": 3,
  "pool_idle": 7,
  "pool_max": 20
}

// GET /health/redis — 200 OK or 503 Service Unavailable
{
  "status": "ok",
  "latency_ms": 1.2
}

// GET /health/disk — 200 OK or 503 Service Unavailable
{
  "status": "ok | warning | error",
  "disk_used_percent": 42.3
}
```

---

## 7. Alert Configuration

### 7.1 Alert Channels

| Channel | Type | Target | Use Case |
|---------|------|--------|----------|
| Discord `#alerts` | Discord Webhook | `https://discord.com/api/webhooks/...` | Primary — all alerts |
| Email (admin) | SMTP | `admin@zyntapos.com` | Backup — critical only |
| Webhook → Admin Panel | HTTP POST | `http://panel:8081/api/webhooks/uptime` | Dashboard integration |

### 7.2 Alert Rules

| Severity | Condition | Channels | Repeat |
|----------|----------|----------|--------|
| **CRITICAL** | API, License, or PostgreSQL DOWN > 90s | Discord + Email + Webhook | Every 5 min |
| **HIGH** | Sync, Redis, or Panel DOWN > 2 min | Discord + Webhook | Every 10 min |
| **WARNING** | Marketing/Docs site DOWN > 5 min | Discord | Every 30 min |
| **WARNING** | TLS cert expiring in < 30 days | Discord + Email | Daily |
| **WARNING** | Disk usage > 85% | Discord + Email | Every 6 hours |
| **INFO** | Service recovered (UP after DOWN) | Discord + Webhook | Once |

### 7.3 Discord Webhook Message Format

```json
{
  "embeds": [{
    "title": "🔴 CRITICAL: API Server DOWN",
    "description": "api.zyntapos.com is not responding",
    "color": 16711680,
    "fields": [
      { "name": "Monitor", "value": "API (public)", "inline": true },
      { "name": "Duration", "value": "2m 30s", "inline": true },
      { "name": "Status Code", "value": "503", "inline": true },
      { "name": "Response Time", "value": "Timeout (30s)", "inline": true }
    ],
    "footer": { "text": "Uptime Kuma — status.zyntapos.com" },
    "timestamp": "2026-03-06T10:30:00.000Z"
  }]
}
```

### 7.4 Webhook Payload to Admin Panel

When Uptime Kuma fires an alert, it sends a JSON payload to the admin panel webhook endpoint. This powers the real-time health dashboard in TODO-007a.

```json
// POST http://panel:8081/api/webhooks/uptime
{
  "monitor_id": 1,
  "monitor_name": "API (internal)",
  "monitor_url": "http://api:8080/health",
  "heartbeat": {
    "status": 0,
    "time": "2026-03-06T10:30:00.000Z",
    "msg": "Connection refused",
    "ping": null,
    "duration": 90
  },
  "type": "down"
}
```

---

## 8. Status Page Configuration

### 8.1 Public Status Page Groups

```
status.zyntapos.com
├── Core Services
│   ├── POS API                    (api.zyntapos.com)
│   ├── License Server             (license.zyntapos.com)
│   └── Real-time Sync             (sync.zyntapos.com)
├── Web Properties
│   ├── Admin Panel                (panel.zyntapos.com)
│   ├── Website                    (zyntapos.com)
│   └── Documentation              (docs.zyntapos.com)
└── Infrastructure
    ├── Database                   (PostgreSQL)
    └── Cache                      (Redis)
```

### 8.2 Status Page Settings

| Setting | Value |
|---------|-------|
| Title | ZyntaPOS System Status |
| Description | Current status and uptime history for ZyntaPOS services |
| Custom domain | `status.zyntapos.com` |
| Theme | Dark |
| Show powered by | No |
| Show certificate expiry | Yes |
| History length | 90 days |
| Favicon | ZyntaPOS logo |
| Custom CSS | Brand colors (#0f172a background, #3b82f6 primary) |

### 8.3 Maintenance Windows

Use Uptime Kuma's maintenance feature to schedule planned downtime:

| Window | Schedule | Affected Services |
|--------|----------|-------------------|
| Database maintenance | 1st Sunday of month, 03:00-04:00 LKT | PostgreSQL, API, License |
| VPS OS updates | 2nd Saturday of month, 02:00-03:00 LKT | All services |

During maintenance windows, the status page shows "Scheduled Maintenance" instead of "DOWN", and alerts are suppressed.

---

## 9. Admin Panel Integration (with TODO-007a)

### 9.1 Uptime Kuma API Access

Uptime Kuma provides a REST API for programmatic access. The admin panel fetches monitor data via this API.

**API Base URL (internal):** `http://uptime-kuma:3001/api`

### 9.2 Panel Dashboard Widget

The admin panel (007a) should display a health overview widget:

```
┌─────────────────────────────────────────────┐
│  System Health                    ● All Up   │
│  ┌────────┐ ┌────────┐ ┌────────┐          │
│  │ API    │ │License │ │ Sync   │          │
│  │ ● UP   │ │ ● UP   │ │ ● UP   │          │
│  │ 99.99% │ │ 99.98% │ │ 99.95% │          │
│  │ 12ms   │ │ 8ms    │ │ 15ms   │          │
│  └────────┘ └────────┘ └────────┘          │
│                                             │
│  Avg Response Time (24h)  ████████▓░ 14ms   │
│  Uptime (30d)             ██████████ 99.97% │
│                                             │
│  Recent Incidents                           │
│  • 2026-03-05 14:22 — Sync latency spike   │
│  • 2026-03-03 02:00 — Scheduled maintenance│
└─────────────────────────────────────────────┘
```

### 9.3 Iframe Embed (Alternative)

If API integration is too complex initially, embed Uptime Kuma's status page directly:

```tsx
// admin-panel/src/components/SystemHealthEmbed.tsx
<iframe
  src="http://uptime-kuma:3001/status/zyntapos"
  className="w-full h-[600px] rounded-lg border-0"
  title="System Health"
/>
```

> `UPTIME_KUMA_DISABLE_FRAME_SAMEORIGIN=true` is set in docker-compose to allow this.

---

## 10. Incident Response Runbook

### 10.1 When an Alert Fires

```
1. Check Discord #alerts for alert details
2. Open status.zyntapos.com to see which services are affected
3. SSH into VPS: ssh zyntapos-vps
4. Check Docker status: docker compose ps
5. Check logs of affected service: docker compose logs <service> --tail=100
6. Common fixes:
   a. Container crashed → docker compose restart <service>
   b. OOM killed → check docker stats, increase memory limit
   c. DB connection pool exhausted → docker compose restart api
   d. Disk full → clean Docker images: docker system prune -af
   e. Redis OOM → check Redis memory: docker compose exec redis redis-cli INFO memory
7. Verify recovery on status.zyntapos.com
8. Post incident summary in Discord #alerts
```

### 10.2 Escalation Path

| Level | Trigger | Action |
|-------|---------|--------|
| L1 | Any service DOWN | Auto-alert to Discord. Check within 15 min. |
| L2 | Service DOWN > 15 min | Manual investigation via SSH |
| L3 | Service DOWN > 1 hour | Full incident response, consider rollback |

---

## 11. Implementation Steps (Ordered)

| Step | Task | Time | Dependencies |
|------|------|------|-------------|
| 1 | Add `uptime-kuma` service to `docker-compose.yml` | 10 min | Docker Compose running |
| 2 | Add `uptime_kuma_data` volume | 2 min | Step 1 |
| 3 | Add Caddy route for `status.zyntapos.com` | 5 min | Caddyfile exists |
| 4 | Add Cloudflare DNS A record for `status` subdomain | 5 min | Cloudflare access |
| 5 | `docker compose up -d uptime-kuma` — start the service | 2 min | Steps 1-3 |
| 6 | Open `status.zyntapos.com`, create admin account | 5 min | Step 5 |
| 7 | Add health endpoints to API server (`/health`, `/health/db`, `/health/redis`, `/health/disk`) | 30 min | Backend code access |
| 8 | Add health endpoints to License server (`/health`) | 15 min | Backend code access |
| 9 | Add health endpoints to Sync server (`/health`) | 15 min | Backend code access |
| 10 | Configure 6 internal monitors (Docker network) | 15 min | Steps 7-9 |
| 11 | Configure 6 external monitors (public URLs) | 10 min | DNS configured |
| 12 | Configure 4 TLS certificate monitors | 5 min | — |
| 13 | Configure 3 custom health monitors (DB, Redis, Disk) | 10 min | Step 7 |
| 14 | Create Discord webhook + configure alert channel | 10 min | Discord server access |
| 15 | Configure email alert channel | 10 min | SMTP credentials |
| 16 | Configure webhook alert to admin panel | 10 min | 007a running |
| 17 | Set up status page groups and branding | 15 min | Step 6 |
| 18 | Configure maintenance windows | 5 min | Step 17 |
| 19 | Test all alerts (manually stop a service, verify notification) | 15 min | Steps 14-16 |
| 20 | Document incident response runbook in team wiki | 15 min | — |

**Total estimated time: ~4 hours**

---

## 12. Files to Create / Modify

```
backend/
├── api/src/main/kotlin/routes/
│   └── HealthRoutes.kt                    # NEW — /health, /health/db, /health/redis, /health/disk
├── license/src/main/kotlin/routes/
│   └── HealthRoutes.kt                    # NEW — /health
└── sync/src/main/kotlin/routes/
    └── HealthRoutes.kt                    # NEW — /health

docker-compose.yml                          # MODIFY — add uptime-kuma service + volume
Caddyfile                                   # MODIFY — add status.zyntapos.com route

admin-panel/src/
├── components/dashboard/
│   └── SystemHealthWidget.tsx              # NEW — health overview widget (007a integration)
└── api/
    └── uptime.ts                           # NEW — Uptime Kuma API client
```

---

## 13. Validation Checklist

### Service Running (code/config in place — VPS runtime verification pending)
- [x] Uptime Kuma service defined in docker-compose.yml (louislam/uptime-kuma:2, healthcheck, volume)
- [x] `status.zyntapos.com` route configured in Caddyfile → uptime-kuma:3001
- [ ] `docker compose ps` shows `uptime-kuma` as healthy (VPS runtime)
- [ ] Admin login works with created credentials (VPS runtime)
- [ ] Container uses < 256MB RAM (VPS runtime)

### Monitors Active (VPS runtime verification — requires running instance)
- [ ] All 6 internal monitors showing "UP" (green)
- [ ] All 6 external monitors showing "UP" (green)
- [ ] All 4 TLS certificate monitors configured
- [ ] All 3 custom health monitors responding

### Alerts Working (VPS runtime verification)
- [x] Falcosidekick Slack webhook configured in docker-compose.yml
- [ ] Stop API container → Discord/Slack alert fires within 90s (VPS runtime)
- [ ] Restart API container → Recovery notification fires (VPS runtime)
- [ ] Webhook payload received by admin panel (VPS runtime)

### Status Page (VPS runtime verification)
- [x] Caddyfile routing configured for status.zyntapos.com
- [ ] Public status page shows all service groups (VPS runtime)
- [ ] 90-day history graphs rendering (VPS runtime)
- [ ] Custom domain `status.zyntapos.com` working (VPS runtime)
- [ ] Branding applied (VPS runtime)

### Health Endpoints (code implemented)
- [x] `GET /health` returns 200 with service name + version (HealthRoutes.kt)
- [x] Health endpoints routed via Caddyfile for all services
- [ ] `GET /health/db` returns pool stats or 503 (VPS runtime)
- [ ] `GET /health/redis` returns latency or 503 (VPS runtime)

---

## 14. Cost Analysis

| Item | Cost |
|------|------|
| Uptime Kuma | **Free** (self-hosted, MIT license) |
| VPS RAM overhead | ~100MB (within existing 8GB Contabo) |
| Discord webhook | **Free** |
| Email (SMTP) | **Free** (existing email provider) |
| Cloudflare DNS record | **Free** (included in free plan) |
| **Total monthly cost** | **$0.00** |

---

## 15. Future Enhancements (Phase 3+)

- **Prometheus + Grafana:** Custom metrics dashboards (request rates, error rates, p95 latency)
- **Loki:** Centralized log aggregation with search
- **Alertmanager:** Advanced alert routing with silencing, grouping, inhibition
- **Synthetic monitoring:** Automated user journey tests (login → create order → payment)
- **PagerDuty integration:** For on-call rotation when team grows
- **SLA reporting automation:** Monthly uptime reports auto-generated and emailed to stakeholders
