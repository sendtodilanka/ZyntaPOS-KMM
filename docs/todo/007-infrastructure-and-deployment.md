# TODO-007: Infrastructure, Deployment & Backend Architecture

**Status:** Pending
**Priority:** HIGH — Foundation for licensing, sync, and remote diagnostics
**Phase:** Phase 2 (Growth)
**Created:** 2026-03-01

---

## Overview

Domain: `zyntapos.com` (registered at Namecheap)
DNS: Cloudflare (free plan) — nameservers delegated from Namecheap to Cloudflare
VPS 1: Amazon Lightsail — existing instance, running 3X-UI Xray VPN (keep as-is)
VPS 2: **Contabo Cloud VPS 10** — new dedicated instance for all ZyntaPOS services

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

- **VPS 1:** 3X-UI Xray VPN (existing Lightsail instance — keep as-is, do not touch)
- **VPS 2:** ZyntaPOS services → **Contabo Cloud VPS 10** ($3.96/mo, 4 vCPU, 8 GB RAM, 75 GB NVMe)

---

## Subdomain Architecture

```
zyntapos.com            -> Marketing website (Astro static, Cloudflare Pages)
www.zyntapos.com        -> Redirects to zyntapos.com
api.zyntapos.com        -> REST API (POS app backend)
license.zyntapos.com    -> License server (token issuance, heartbeat, activation)
panel.zyntapos.com      -> Admin dashboard (license mgmt, helpdesk, etc.)
docs.zyntapos.com       -> API documentation + knowledge base
status.zyntapos.com     -> Uptime status page (99.99% SLA proof)
sync.zyntapos.com       -> WebSocket endpoint for real-time sync
```

**Why separate `sync` from `api`?** WebSocket connections are long-lived. If they share the same process, a sync storm (100 POS terminals reconnecting after internet outage) can block REST API requests. Separate subdomain allows independent scaling and rate-limiting.

**Why separate `license` from `api`?** The license server uses RS256 private keys and needs independent rate-limiting and WAF rules stricter than the general API. Isolating it simplifies security audits.

---

## DNS Configuration (Namecheap → Cloudflare → Contabo)

### Step 1: Delegate Nameservers from Namecheap to Cloudflare

1. Log in to **Cloudflare** → Add Site → enter `zyntapos.com` → select Free plan
2. Cloudflare will scan existing DNS records and show you two nameservers, e.g.:
   ```
   dana.ns.cloudflare.com
   kurt.ns.cloudflare.com
   ```
3. Log in to **Namecheap** → Domain List → `zyntapos.com` → Manage → Nameservers
4. Change from "Namecheap BasicDNS" to **Custom DNS** and paste the two Cloudflare nameservers
5. Save. Propagation takes 5 minutes – 48 hours (usually <30 minutes)

After this, **all DNS is managed in Cloudflare**, not Namecheap.

### Step 2: Cloudflare DNS Records

Add the following records in Cloudflare DNS dashboard. The **Proxy** column controls orange-cloud vs grey-cloud:

| Type | Name | Value | Proxy | Reason |
|------|------|-------|-------|--------|
| A | `@` | `<CONTABO_VPS_IP>` | ✅ Orange | CDN + DDoS on main domain |
| CNAME | `www` | `zyntapos.com` | ✅ Orange | Redirect to apex |
| A | `api` | `<CONTABO_VPS_IP>` | ✅ Orange | WAF + rate limiting on REST API |
| A | `license` | `<CONTABO_VPS_IP>` | ✅ Orange | License server — extra WAF protection |
| A | `panel` | `<CONTABO_VPS_IP>` | ✅ Orange | Admin panel behind CF |
| A | `docs` | `<CONTABO_VPS_IP>` | ✅ Orange | Static docs via CF CDN |
| A | `status` | `<CONTABO_VPS_IP>` | ✅ Orange | Status page |
| A | `sync` | `<CONTABO_VPS_IP>` | ⚠️ Grey | WebSocket — start grey, test orange later |

> **`sync` subdomain note:** Cloudflare free plan supports WebSocket proxying but adds ~50-100ms extra latency per hop on long-lived connections. Start with grey (DNS-only) for reliability. If latency is acceptable after load testing, switch to orange to gain DDoS protection.

### Step 3: Cloudflare SSL/TLS Settings

Go to Cloudflare → `zyntapos.com` → SSL/TLS:

| Setting | Value | Reason |
|---------|-------|--------|
| SSL/TLS mode | **Full (Strict)** | Encrypts both legs: visitor↔CF and CF↔VPS |
| Always Use HTTPS | **On** | Redirects all HTTP to HTTPS |
| HTTP Strict Transport | **On** (max-age 6 months, include subdomains) | HSTS preload |
| Minimum TLS Version | **TLS 1.2** | Drops insecure TLS 1.0/1.1 |
| Opportunistic Encryption | **On** | |

### Step 4: Cloudflare Origin Certificate for Caddy

When Cloudflare orange-cloud is active, Cloudflare terminates the visitor's TLS connection. The request then travels from Cloudflare → VPS on a **second TLS connection**. Caddy's standard Let's Encrypt HTTP-01 challenge fails because Cloudflare intercepts port 80.

**Solution: Cloudflare Origin Certificate (free, 15-year validity)**

1. Cloudflare → SSL/TLS → Origin Server → **Create Certificate**
2. Leave key type as RSA 2048, hostnames: `zyntapos.com`, `*.zyntapos.com`
3. Validity: **15 years**
4. Download:
   - `zyntapos_origin.pem` (certificate)
   - `zyntapos_origin.key` (private key)
5. Upload both to VPS at `/etc/caddy/certs/` (owned by `root`, mode `600`)

**Caddyfile configuration for proxied subdomains:**
```caddyfile
# Proxied domains (orange-cloud) — use Cloudflare Origin Certificate
(cloudflare_tls) {
  tls /etc/caddy/certs/zyntapos_origin.pem /etc/caddy/certs/zyntapos_origin.key
}

api.zyntapos.com {
  import cloudflare_tls
  reverse_proxy api:8080
}

license.zyntapos.com {
  import cloudflare_tls
  reverse_proxy api:8083
}

panel.zyntapos.com {
  import cloudflare_tls
  reverse_proxy panel:8081
}

# Grey-cloud domain — use standard Let's Encrypt (Caddy auto-manages)
sync.zyntapos.com {
  reverse_proxy sync:8082
}
```

> The grey-cloud `sync.zyntapos.com` still gets auto-HTTPS from Let's Encrypt via Caddy's built-in HTTP-01 challenge — Cloudflare does not intercept it.

### Cloudflare WAF Rules (Free Plan)

Create these in Cloudflare → Security → WAF → Custom Rules:

| Rule | Expression | Action |
|------|-----------|--------|
| Block non-POS clients on API | `http.request.uri.path contains "/api" and not http.user_agent contains "ZyntaPOS"` | Block |
| Rate limit license heartbeat | `http.request.uri.path contains "/license/heartbeat"` | Rate limit: 10 req/min per IP |
| Block panel from non-LK IPs | `http.request.uri.path contains "/panel" and ip.geoip.country ne "LK"` | Challenge (CAPTCHA) |

---

## Technology Stack

### Reverse Proxy

```
+--------------------------------------------------+
|                 REVERSE PROXY                    |
|                    Caddy 2                       |
|  (Origin cert for CF-proxied, Let's Encrypt      |
|   for grey-cloud sync subdomain)                 |
|                                                  |
|  api.zyntapos.com      -> :8080 (API)            |
|  license.zyntapos.com  -> :8083 (license server) |
|  panel.zyntapos.com    -> :8081 (admin panel)    |
|  sync.zyntapos.com     -> :8082 (WebSocket sync) |
|  docs.zyntapos.com     -> :3001 (docs)           |
|  status.zyntapos.com   -> :3002 (status page)    |
+--------------------------------------------------+
```

**Recommendation:** Caddy with Cloudflare Origin Certificate for orange-cloud subdomains. The marketing site (`zyntapos.com`) is deployed separately to Cloudflare Pages — not served from this VPS.

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

> **SEO/ASO:** Once the Astro site is deployed (Step 11 of Implementation Order), follow **[TODO-008](008-seo-and-aso.md)** for the full SEO specification — Schema.org JSON-LD, Core Web Vitals, Open Graph, Google Search Console setup, and Google Play Store ASO. TODO-008 depends on this step.

### Admin Panel (panel.zyntapos.com)

| Option | Pros | Cons |
|--------|------|------|
| **React + TypeScript** (Recommended) | Mature, rich dashboard libraries (Recharts, TanStack Table) | Different language |
| **Compose for Web** | Share UI code with KMM app, visual consistency with ZyntaTheme | Still experimental |

---

## Docker Compose Layout

```yaml
# docker-compose.yml (ZyntaPOS Contabo VPS)
services:
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - ./certs:/etc/caddy/certs:ro          # Cloudflare Origin Cert (read-only)
      - caddy_data:/data
      - caddy_config:/config
    # Orange-cloud subdomains use Origin Cert; grey-cloud sync uses Let's Encrypt

  api:
    build: ./zyntapos-api
    expose: ["8080"]
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/zyntapos
      - REDIS_URL=redis://redis:6379
    depends_on: [postgres, redis]
    # Ktor server — REST API

  license:
    build: ./zyntapos-license
    expose: ["8083"]
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/zyntapos
      - REDIS_URL=redis://redis:6379
      - RS256_PRIVATE_KEY_PATH=/run/secrets/rs256_private_key
    depends_on: [postgres, redis]
    secrets: [rs256_private_key]
    # Ktor server — license issuance, heartbeat, terminal activation

  sync:
    build: ./zyntapos-sync
    expose: ["8082"]
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/zyntapos
      - REDIS_URL=redis://redis:6379
    depends_on: [postgres, redis]
    # Ktor server — WebSocket sync engine

  panel:
    build: ./zyntapos-panel
    expose: ["8081"]
    depends_on: [api]
    # Admin dashboard frontend + BFF

  postgres:
    image: postgres:16-alpine
    volumes: ["pgdata:/var/lib/postgresql/data"]
    environment:
      - POSTGRES_DB=zyntapos
      - POSTGRES_USER=zyntapos
      - POSTGRES_PASSWORD_FILE=/run/secrets/db_password
    secrets: [db_password]
    # Data stored on Contabo NVMe; backup via pg_dump → Backblaze B2

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes: ["redisdata:/data"]
    # Session cache, rate limiting, pub/sub

  docs:
    build: ./zyntapos-docs
    expose: ["3001"]
    # Static docs site

secrets:
  db_password:
    file: ./secrets/db_password.txt
  rs256_private_key:
    file: ./secrets/rs256_private_key.pem

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

## VPS Sizing — Contabo Cloud VPS 10

### Chosen: Contabo Cloud VPS 10

| Spec | Value |
|------|-------|
| vCPU | 4 cores |
| RAM | 8 GB |
| Storage | 75 GB NVMe SSD |
| Bandwidth | 32 TB/month (unlimited for typical POS usage) |
| Monthly cost | **$3.96 USD** |
| Data center | EU (Germany) or US — choose closest to Sri Lanka customers (EU is fine; both are fast) |

### Why Contabo over AWS Lightsail

| | Contabo VPS 10 | Lightsail "Growth" |
|---|---|---|
| vCPU | **4** | 2 |
| RAM | **8 GB** | 4 GB |
| Storage | 75 GB NVMe | 80 GB SSD |
| Cost/month | **$3.96** | $24 |
| Managed snapshots | ❌ manual | ✅ built-in |
| Managed DB | ❌ self-hosted | ✅ RDS option |

**Cost saving: ~$240/year** — reinvest into Backblaze B2 backup storage instead.

### Expected RAM Footprint (all services running)

| Service | Est. RAM |
|---------|---------|
| Caddy | ~50 MB |
| Ktor API | ~600 MB |
| Ktor License server | ~300 MB |
| Ktor Sync | ~400 MB |
| React Panel (BFF) | ~150 MB |
| PostgreSQL | ~400 MB |
| Redis | ~100 MB |
| OS + kernel + buffer | ~1 GB |
| **Total** | **~3 GB** |

Comfortable on 8 GB — ~5 GB headroom for traffic spikes and future services.

### Backup Strategy (replaces Lightsail snapshots)

Contabo does not include managed snapshots. Use this instead:

```bash
# Daily pg_dump → Backblaze B2 (free 10 GB, $0.006/GB after)
# Runs as a cron job on the VPS at 03:00 LKT (UTC+05:30)

0 21 * * * deploy /opt/zyntapos/scripts/backup.sh

# backup.sh:
#!/bin/bash
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="/tmp/zyntapos_${TIMESTAMP}.sql.gz"
pg_dump -U zyntapos zyntapos | gzip > "$BACKUP_FILE"
rclone copy "$BACKUP_FILE" b2:zyntapos-backups/db/
rm "$BACKUP_FILE"
# Retention: rclone delete files older than 30 days
rclone delete --min-age 720h b2:zyntapos-backups/db/
```

Tools: `rclone` (configured with Backblaze B2 API key), `pg_dump`.
Backblaze B2 cost: ~$0 for first 10 GB, $0.006/GB after (a 30-day backup set is typically <1 GB).

### Scale Point

When active licenses exceed ~500 or RAM usage consistently > 6 GB: migrate PostgreSQL to a managed instance (PlanetScale, Neon, or Supabase free tier → paid) and keep API/sync on the Contabo VPS. Full migration to Kubernetes is only needed at 2,000+ concurrent connections.

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

- **ufw firewall** (Contabo VPS): only ports 80, 443, and SSH port open. Enable with: `ufw allow 80/tcp && ufw allow 443/tcp && ufw allow <SSH_PORT>/tcp && ufw enable`
- SSH key-only authentication (no password)
- Docker networks: services communicate internally only
- PostgreSQL: not exposed to public network
- Redis: password-protected, not exposed to public network
- Regular automated backups: `pg_dump` → Backblaze B2 via `rclone` (see Backup Strategy section)
- Fail2ban on SSH

### Monitoring

- **Uptime Kuma** (self-hosted) or **BetterStack** (managed) for status page
- **Sentry** for error tracking (DSN already in local.properties template)
- **PostgreSQL** slow query logging
- **Caddy** access logs with request timing

---

---

## Claude Code Deploy Access

This section documents how to give Claude Code (running locally via the Claude Code CLI) SSH access to the Contabo VPS so it can deploy, configure, and maintain the server on your behalf — interactively and via GitHub Actions automation.

### Why Two Layers?

| Layer | Method | Use Case |
|-------|--------|---------|
| **A — Interactive** | SSH from local machine via Claude Code Bash tool | Initial server setup, migrations, debugging, config changes |
| **B — Automated** | GitHub Actions → SSH on push to `main` | Routine deploys after every code change |

Both layers use the **same SSH key and `deploy` user** on the VPS.

### One-Time Setup (You Do This Once)

**Step 1: Generate deploy SSH key (on your local machine)**
```bash
ssh-keygen -t ed25519 -C "zyntapos-claude-deploy" -f ~/.ssh/zyntapos_deploy -N ""
# This creates:
#   ~/.ssh/zyntapos_deploy       (private key — keep secret, never share)
#   ~/.ssh/zyntapos_deploy.pub   (public key — safe to copy to VPS)
cat ~/.ssh/zyntapos_deploy.pub   # Copy this output for the next step
```

**Step 2: Create deploy user on VPS (SSH in as root first, then run)**
```bash
# Run as root on the Contabo VPS
adduser deploy --disabled-password --gecos ""
usermod -aG docker deploy

# Grant deploy user docker-only sudo (no full root access)
echo "deploy ALL=(ALL) NOPASSWD: /usr/bin/docker, /usr/local/bin/docker" \
  > /etc/sudoers.d/deploy-docker
chmod 440 /etc/sudoers.d/deploy-docker

# Install the public key
mkdir -p /home/deploy/.ssh
echo "ssh-ed25519 AAAA...paste-your-public-key-here... zyntapos-claude-deploy" \
  >> /home/deploy/.ssh/authorized_keys
chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh

# Create the app directory
mkdir -p /opt/zyntapos && chown deploy:deploy /opt/zyntapos
```

**Step 3: Add SSH config entry (on your local machine)**

Add to `~/.ssh/config` (create if it doesn't exist):
```
Host zyntapos-vps
  HostName <CONTABO_VPS_IP>
  User deploy
  IdentityFile ~/.ssh/zyntapos_deploy
  Port 22
  ServerAliveInterval 60
  ServerAliveCountMax 3
```
Replace `<CONTABO_VPS_IP>` with your Contabo VPS IP address.

**Step 4: Test the connection**
```bash
ssh zyntapos-vps "echo 'Deploy access working ✓'"
```

After this, Claude Code can run commands like:
```bash
ssh zyntapos-vps "cd /opt/zyntapos && docker compose up -d --build"
ssh zyntapos-vps "docker compose logs api --tail=50"
ssh zyntapos-vps "docker compose exec postgres psql -U zyntapos -c 'SELECT COUNT(*) FROM licenses;'"
```

**Step 5: Add GitHub Secrets for automated deploys**

In GitHub → Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret name | Value |
|-------------|-------|
| `VPS_HOST` | Your Contabo VPS IP address |
| `VPS_USER` | `deploy` |
| `VPS_PORT` | `22` (or your custom SSH port) |
| `DEPLOY_SSH_PRIVATE_KEY` | Full contents of `~/.ssh/zyntapos_deploy` (the private key file) |
| `SENTRY_AUTH_TOKEN` | From Sentry → Settings → Auth Tokens |

> The deploy workflow file (`.github/workflows/deploy.yml`) is already in the repository and reads these secrets automatically.

### Security Notes

- The `deploy` user has **no shell login password** and **no sudo for anything except docker**
- SSH is key-only (password auth disabled — see Infrastructure Security section)
- The private key (`~/.ssh/zyntapos_deploy`) stays on your local machine only — never commit it
- Rotate the key by generating a new one and replacing `authorized_keys` on the VPS

---

## GitHub Actions Deploy Workflow

The deploy workflow is defined in `.github/workflows/deploy.yml` (already added to the repository). It triggers automatically on every push to `main` and can also be triggered manually from the GitHub Actions UI.

**What it does on each deploy:**
1. SSH into Contabo VPS as `deploy` user
2. `cd /opt/zyntapos && git pull origin main`
3. `docker compose pull` — fetch latest images
4. `docker compose up -d --build` — rebuild and restart changed services
5. `docker system prune -f` — clean up dangling images
6. Notify Sentry of the new release (for error tracking correlation)

**Manual trigger:** GitHub → Actions tab → "Deploy to VPS" → Run workflow

---

## Implementation Order

1. **Provision Contabo Cloud VPS 10** — separate from Lightsail VPN instance. Note the VPS IP.
2. **Set up deploy access** — create `deploy` user, install SSH public key, test `ssh zyntapos-vps` (see Claude Code Deploy Access section)
3. **Delegate DNS to Cloudflare** — change Namecheap nameservers to Cloudflare; add A records pointing to Contabo VPS IP
4. **Get Cloudflare Origin Certificate** — Cloudflare → SSL/TLS → Origin Server; save cert + key to VPS
5. **Install Docker + ufw on VPS** — `apt install docker.io docker-compose-plugin ufw`; configure firewall
6. **Set up Docker Compose** — clone repo to `/opt/zyntapos`, configure Caddyfile, start Caddy + PostgreSQL + Redis
7. **Create Ktor Server projects** — `zyntapos-api`, `zyntapos-license`, `zyntapos-sync` — shared domain models with KMM app
8. **Implement license system** — token generation (RS256), terminal activation endpoint, heartbeat endpoint
9. **Implement authentication** — JWT RS256 issuance and validation
10. **Implement sync engine server-side** — push/pull operations API
11. **Build panel frontend** — React + TypeScript admin dashboard
12. **Add remote diagnostic relay** — WebSocket tunnel via panel
13. **Set up monitoring** — Uptime Kuma + Sentry DSN
14. **Configure automated backups** — `pg_dump` cron → Backblaze B2 via rclone
15. **Deploy marketing website** — Astro on Cloudflare Pages
16. **Add GitHub Actions secrets** — `VPS_HOST`, `VPS_USER`, `VPS_PORT`, `DEPLOY_SSH_PRIVATE_KEY`; verify deploy workflow green

---

## Validation Checklist

### Infrastructure
- [ ] VPN (Lightsail) and POS services (Contabo) on separate VPS instances
- [ ] Namecheap nameservers pointing to Cloudflare (verify: `dig NS zyntapos.com`)
- [ ] All A records in Cloudflare pointing to Contabo VPS IP
- [ ] Cloudflare SSL/TLS mode set to Full (Strict)
- [ ] Cloudflare Origin Certificate installed on VPS at `/etc/caddy/certs/`
- [ ] ufw firewall active: only ports 80, 443, and SSH port open
- [ ] SSH hardened: key-only auth, Fail2ban active, non-default port
- [ ] Docker Compose running: Caddy, API, License, Sync, Panel, PostgreSQL, Redis

### Deploy Access
- [ ] `deploy` user created with docker-only sudo
- [ ] SSH public key installed for `deploy` user
- [ ] `ssh zyntapos-vps "echo ok"` works from local machine
- [ ] GitHub Actions secrets configured: VPS_HOST, VPS_USER, VPS_PORT, DEPLOY_SSH_PRIVATE_KEY
- [ ] GitHub Actions deploy workflow green on push to `main`

### Services
- [ ] License system: generate, activate, heartbeat, expire all working
- [ ] Offline grace period enforced (7-day warning, 14-day read-only)
- [ ] JWT RS256 authentication working (private key in Docker secret, public key in KMM app)
- [ ] Rate limiting active on all API endpoints (Redis-backed)
- [ ] Sync engine: push/pull operations between POS app and server
- [ ] Panel: license management, deployment health, helpdesk views working
- [ ] Remote diagnostic WebSocket relay functional

### Reliability
- [ ] Monitoring: Uptime Kuma alerts configured, Sentry DSN active
- [ ] Automated backup: `pg_dump` cron job running, test restore verified
- [ ] Backblaze B2 bucket receiving daily backups
- [ ] Marketing website live on Cloudflare Pages (zyntapos.com)
