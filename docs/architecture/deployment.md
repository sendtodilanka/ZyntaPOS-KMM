# ZyntaPOS — Deployment Architecture

**Last updated:** 2026-03-29
**ADR:** [ADR-006](../adr/ADR-006-backend-docker-build-in-ci.md)

---

## Overview

ZyntaPOS uses a fully-automated, 7-step pipeline that takes a push to a development branch
all the way to a live VPS deployment — with no human intervention after the initial push.

```
Developer / Claude pushes to claude/* branch
        │
        ▼  Step[1]: Branch Validate (~10 min)
        │  ci-branch-validate.yml — KMM build-only (no tests)
        │
        ▼  Step[2]: Auto PR (~1 min)
        │  ci-auto-pr.yml — creates PR to main, enables auto-merge
        │
        ▼  Step[3]: PR Gate (~5 min for merge, OWASP runs independently)
        │  ci-pr-gate.yml — full KMM build + lint + detekt + all tests + OWASP scan
        │  Auto-merge fires when "CI Gate Status" check passes
        │
        ▼  Step[4]: Build Images & Deploy on push to main (~10 min)
        │  ci-push-main.yml — parallel jobs:
        │    ├── build-and-test (re-verify merged code)
        │    └── build Docker images → push to GHCR
        │
        ▼  Step[5]: Deploy to VPS (~2 min)
        │  cd-deploy.yml — SSH to Contabo VPS
        │
        ▼  Step[6]: Smoke Test (~3 min)
        │  cd-smoke.yml — HTTP probes on all 6 endpoints
        │
        ▼  Step[7]: Verify Endpoints (~2 min)
           cd-verify-endpoints.yml — independent post-deploy check
```

---

## Step[4]: Build Images & Deploy — Backend Docker Image Build

When `ci-push-main.yml` runs on a push to `main` (after auto-merge), jobs run in parallel:

### `build-and-test`

Calls `_reusable-build-test.yml`:
- KMM build (all shared modules + Android APK + Desktop JVM JAR)
- Android Lint + Detekt static analysis
- All JVM tests (`./gradlew allTests`)

### `build-backend-images` (matrix: api / license / sync)

For each of the 3 backend services:

1. **`docker/login-action@v3`** — Authenticates to GHCR using `GITHUB_TOKEN`
   (automatically granted `packages: write` — no extra secret needed)

2. **`docker/build-push-action@v6`** — Runs the multi-stage Dockerfile:
   - **Stage 1 (builder):** `eclipse-temurin:21-jdk-alpine` + `./gradlew shadowJar`
     → produces `zyntapos-{service}.jar`
   - **Stage 2 (runtime):** `eclipse-temurin:21-jre-alpine` + non-root user
     → final minimal image

3. **Push two tags:**
   ```
   ghcr.io/sendtodilanka/zyntapos-api:latest        ← always updated
   ghcr.io/sendtodilanka/zyntapos-api:<full-sha>    ← immutable, for rollback
   ```

### `trigger-deploy`

Only fires when **both** `build-and-test` AND `build-backend-images` succeed. This guarantees
that the VPS deploy step only starts when verified images are in GHCR.

---

## GHCR Image Registry

| Image | Tags | Registry |
|-------|------|----------|
| REST API | `:latest`, `:<sha>` | `ghcr.io/sendtodilanka/zyntapos-api` |
| License server | `:latest`, `:<sha>` | `ghcr.io/sendtodilanka/zyntapos-license` |
| WebSocket sync | `:latest`, `:<sha>` | `ghcr.io/sendtodilanka/zyntapos-sync` |

Images are **private** (repository is private). The VPS must authenticate before pulling.

---

## Step[5]: Deploy to VPS

The deploy runs over SSH via `appleboy/ssh-action@v1.2.5` to the Contabo VPS:

```bash
# 1. Update source files (docker-compose.yml, Caddyfile, nginx.conf, scripts, etc.)
git fetch origin
git reset --hard "$DEPLOY_SHA"

# 2. Authenticate with GHCR (PAT_TOKEN must have read:packages scope)
echo "$PAT_TOKEN" | docker login ghcr.io -u sendtodilanka --password-stdin

# 3. Validate compose config before touching running containers
docker compose config --quiet

# 4. Pull pre-built images (no compilation on VPS)
docker compose pull --quiet

# 5. Start/restart containers
docker compose up -d --remove-orphans

# 6. Clean up old image layers
docker image prune -f
```

### Docker Compose Services

| Service | Image | Port | Notes |
|---------|-------|------|-------|
| `caddy` | `caddy:2-alpine` | 80, 443 | Reverse proxy + TLS (Cloudflare Origin Cert) |
| `api` | `ghcr.io/sendtodilanka/zyntapos-api:latest` | 8080 (internal) | REST API |
| `license` | `ghcr.io/sendtodilanka/zyntapos-license:latest` | 8083 (internal) | License server |
| `sync` | `ghcr.io/sendtodilanka/zyntapos-sync:latest` | 8082 (internal) | WebSocket sync |
| `postgres` | `pgvector/pgvector:pg16` | 5432 (internal) | Primary database (pgvector extension included) |
| `redis` | `redis:7-alpine` | 6379 (internal) | Cache + pub/sub |
| `website` | `ghcr.io/sendtodilanka/zyntapos-website:latest` | internal | Marketing website |
| `admin-panel` | `ghcr.io/sendtodilanka/zyntapos-kmm/admin-panel:latest` | internal | Admin panel SPA |
| `canary` | `nginx:1-alpine` | internal | Placeholder for Phase 3 subdomains |
| `docs` | Built from `./zyntapos-docs/Dockerfile` | 3001 (internal) | Documentation site |
| `uptime-kuma` | `louislam/uptime-kuma:2` | internal | Uptime monitoring UI |
| `falcosidekick` | `falcosecurity/falcosidekick:latest` | 2801 | Falco security event forwarder (Slack webhook) |
| `stalwart` | `stalwartlabs/stalwart:latest` | 25, 143, 465, 587, 993, 8080 (internal) | Mail server (SMTP/IMAP/JMAP) |
| `email-relay` | Built from `backend/email-relay/` | 8025 (internal) | HTTP-to-SMTP bridge (CF Worker → Stalwart) |
| `chatwoot-web` | `chatwoot/chatwoot:latest` | internal | Customer support platform (web) |
| `chatwoot-sidekiq` | `chatwoot/chatwoot:latest` | — | Chatwoot background job worker |
| `cloudflared` | `cloudflare/cloudflared:latest` | — | Cloudflare Tunnel daemon (Zero Trust, optional profile) |

**Ktor services** (`api`, `license`, `sync`) run with full hardening:
- `read_only: true` + `tmpfs: /tmp:noexec,nosuid`
- `no-new-privileges:true`
- Custom seccomp profile (`config/seccomp/ktor.json`)
- `cap_drop: ALL` + `cap_add: NET_BIND_SERVICE` only
- Memory limits (api=512m, license/sync=256m)
- Non-root user (`zyntapos:zyntapos`) in Dockerfile

**Supporting services** (`postgres`, `redis`, `stalwart`, `uptime-kuma`) run with:
- `no-new-privileges:true`
- `cap_drop: ALL` + targeted `cap_add` (CHOWN, SETUID, SETGID, NET_BIND_SERVICE)
- Memory limits per service
- Image-default user (postgres / redis / stalwart run non-root by image convention)

---

## VPS Prerequisites

### One-time setup (run on VPS after provisioning)

```bash
# 1. Generate REDIS_PASSWORD and add to .env
echo "REDIS_PASSWORD=$(openssl rand -hex 24)" >> /opt/zyntapos/.env

# 2. Generate the Redis ACL file (password moved off command-line to prevent
#    exposure in `docker inspect .Command` and /proc/<pid>/cmdline — 2026-03-29)
source /opt/zyntapos/.env
echo "user default on >${REDIS_PASSWORD} ~* &* +@all" > /opt/zyntapos/secrets/redis.acl
chmod 600 /opt/zyntapos/secrets/redis.acl

# 3. Verify .env contains required variables
grep -E "REDIS_PASSWORD|ADMIN_IP_ALLOWLIST" /opt/zyntapos/.env

# 4. Secrets files (Docker secrets, not .env)
ls /opt/zyntapos/secrets/
# Must contain: db_password.txt, rs256_private_key.pem, rs256_public_key.pem, redis.acl
# See: secrets/secrets.env.template for generation instructions
```

### Runtime-tunable env vars (VPS `.env`)

#### Security (`api` service) — **must review before first production deploy**

| Env var | Default | Description |
|---------|---------|-------------|
| `ADMIN_IP_ALLOWLIST` | *(none)* | **Required in production.** Comma-separated CIDRs or IPs allowed to reach `/admin/*` routes. If unset, the server refuses to start unless `ALLOW_OPEN_ADMIN_ACCESS=true` is also set. Example: `10.0.0.0/8,203.0.113.5` |
| `ALLOW_OPEN_ADMIN_ACCESS` | `false` | Set `true` only when admin access is already restricted at the network layer (Cloudflare Zero Trust tunnel, VPN). Suppresses the startup check when `ADMIN_IP_ALLOWLIST` is empty. |
| `CORS_ALLOW_LOCALHOST` | `false` | Set `true` only in local development environments. In production, `localhost` origins must never be trusted with `allowCredentials=true` — doing so enables CSRF via local-machine attack vectors. |
| `SINGLE_STORE_COMPAT_MODE` | `false` | Set `true` only for single-store deployments that have not yet populated the `user_store_access` table. Must be `false` for any multi-store deployment. When `false`, sync push/pull enforce per-user store ownership checks and return `403` for users with no grants. |

#### Performance (`api` service) — optional, safe defaults suffice for most deployments

All values below have safe defaults hardcoded in the service and only need to be set when the defaults are insufficient for the deployment's load profile.

#### HikariCP Connection Pool (`api` service)

| Env var | Default | Description |
|---------|---------|-------------|
| `DB_POOL_MAX` | `20` | Maximum connections in pool |
| `DB_POOL_MIN` | `3` | Minimum idle connections |
| `DB_CONNECTION_TIMEOUT_MS` | `30000` | Max wait to acquire a connection (ms) |
| `DB_POOL_IDLE_TIMEOUT` | `600000` | Time before idle connection is evicted (ms) |

#### Redis Connection Pool (`api` service)

| Env var | Default | Description |
|---------|---------|-------------|
| `REDIS_POOL_SIZE` | `8` | Max total pooled Redis connections |
| `REDIS_TIMEOUT_SECONDS` | `5` | Connect / command timeout (seconds) |

#### License Business Logic (`license` service)

| Env var | Default | Description |
|---------|---------|-------------|
| `LICENSE_GRACE_PERIOD_DAYS` | `7` | Days an expired license stays in GRACE state before blocking |
| `LICENSE_MAX_DEVICES` | `100` | Max simultaneous device registrations per license (soft cap) |
| `LICENSE_HEARTBEAT_INTERVAL_MIN` | `60` | Expected client heartbeat cadence (minutes); used for last-seen staleness detection |

---

### GitHub Secrets required

All 26 secrets below are configured in the repository. **Never commit any of these values to the codebase.**

#### CI/CD Core

| Secret | Purpose | Scope needed |
|--------|---------|--------------|
| `PAT_TOKEN` | Repository dispatch (auto-PR, deploy-trigger) + GHCR pull on VPS | `repo` + **`read:packages`** |
| `NVD_API_KEY` | OWASP Dependency Check NVD API (rate-limit bypass) | — |

#### VPS / SSH

| Secret | Purpose |
|--------|---------|
| `VPS_HOST` | Contabo VPS IP address |
| `VPS_USER` | SSH username (`deploy`) |
| `VPS_PORT` | SSH port |
| `VPS_USER_KEY` | SSH private key for `deploy` user |
| `VPS_ROOT` | VPS root username (used by one-time setup scripts only) |
| `VPS_ROOT_KEY` | SSH private key for root (one-time setup scripts only) |
| `DEPLOY_SSH_PRIVATE_KEY` | Alternate deploy key (used by some ad-hoc workflows) |

#### Cloudflare

| Secret | Purpose |
|--------|---------|
| `CF_ORIGIN_CERT` | Cloudflare Origin Certificate (PEM) — TLS between CF edge and VPS |
| `CF_ORIGIN_KEY` | Cloudflare Origin Certificate private key (PEM) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Tunnel token for Zero Trust access (optional) |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare Account ID (used by Pages / Workers deployments) |
| `CLOUDFLARE_API_TOKEN` | Cloudflare API Token (Pages deploy, DNS automation) |

#### Google / Firebase

| Secret | Purpose |
|--------|---------|
| ~~`GOOGLE_OAUTH_CLIENT_ID`~~ | ~~Google SSO OAuth2 Client ID~~ — **removed 2026-03-14** (Google OAuth removed; admin auth is email/password + TOTP only) |
| ~~`GOOGLE_OAUTH_CLIENT_SECRET`~~ | ~~Google SSO OAuth2 Client Secret~~ — **removed 2026-03-14** |
| ~~`GOOGLE_SERVICES_JSON`~~ | ~~`google-services.json` for Firebase Android SDK~~ — **removed 2026-03-28** (ADR-012: Firebase removed) |
| ~~`GA4_MEASUREMENT_ID`~~ | ~~Google Analytics 4 Measurement ID~~ — **removed 2026-03-28** (ADR-012: Firebase removed) |

> **Push Notifications:** FCM/VAPID push notifications have been removed. SMS gateway integration is planned for Phase 4.
> **Firebase:** All Firebase components removed (ADR-012, 2026-03-28). Analytics via Kermit + Sentry. Remote Config via `FeatureRegistryRepository`.

#### Monitoring / Alerting

| Secret | Purpose |
|--------|---------|
| `SENTRY_AUTH_TOKEN` | Sentry CLI auth token (source maps upload, release creation) |
| `SENTRY_DSN_API` | Sentry DSN for `zyntapos-api` backend service |
| `SENTRY_DSN_LICENSE` | Sentry DSN for `zyntapos-license` backend service |
| `SENTRY_DSN_SYNC` | Sentry DSN for `zyntapos-sync` backend service |
| `SLACK_WEBHOOK_URL` | Slack webhook for Falco security alerts |

> **Important:** `PAT_TOKEN` must have `read:packages` scope for the VPS GHCR login to
> succeed. If the PAT was created before the GHCR image pipeline was added, regenerate it
> with this scope at: GitHub → Settings → Developer settings → Personal access tokens.

### Cloudflare Origin Certificate Setup

The Cloudflare Origin Certificate provides TLS encryption between Cloudflare's edge and the VPS
(required for **Full (Strict)** SSL mode). FTS Step 4 deploys it automatically from GitHub Secrets.

**How to generate and store:**

1. Go to **Cloudflare Dashboard → SSL/TLS → Origin Server → Create Certificate**
2. Select: RSA (2048), hostnames `*.zyntapos.com` and `zyntapos.com`, validity 15 years
3. Copy the **Origin Certificate** (PEM) → add as GitHub Secret `CF_ORIGIN_CERT`
4. Copy the **Private Key** (PEM) → add as GitHub Secret `CF_ORIGIN_KEY`
5. Run **FTS Step 4** — it deploys the cert to `backend/caddy/certs/` on the VPS

**Cloudflare Tunnel (optional):**

1. Go to **Cloudflare Zero Trust → Networks → Tunnels → Create Tunnel**
2. Copy the tunnel token → add as GitHub Secret `CLOUDFLARE_TUNNEL_TOKEN`
3. FTS Step 4 writes the token to `.env` on the VPS
4. FTS Step 5 starts the `cloudflared` container (Docker Compose `tunnel` profile)

> **Note:** If `CF_ORIGIN_CERT` and `CF_ORIGIN_KEY` are not set, FTS Step 4 falls back to
> generating a self-signed certificate. This works for testing but will cause browser
> warnings. For production, always use the Cloudflare Origin Certificate.

---

## Rollback Procedure

### Option A — Redeploy previous SHA (recommended)

1. Find the SHA of the last good deploy in GitHub Actions → `Step[5]: Deploy to VPS` run logs
2. Trigger `Step[5]` manually via `workflow_dispatch` with that SHA, OR
3. Edit `docker-compose.yml` to pin to the SHA tag:
   ```yaml
   api:
     image: ghcr.io/sendtodilanka/zyntapos-api:<previous-sha>
   ```
   Commit and push to trigger normal pipeline.

### Option B — Emergency (SSH to VPS directly)

```bash
ssh zyntapos-vps
cd /opt/zyntapos

# Pull a specific immutable image tag
docker pull ghcr.io/sendtodilanka/zyntapos-api:<previous-sha>

# Override the running container
docker compose up -d api  # after editing docker-compose.yml image tag
```

---

## Subdomain Architecture

```
zyntapos.com            → Cloudflare Pages (Astro — Phase 3)
api.zyntapos.com        → Caddy → api:8080
license.zyntapos.com    → Caddy → license:8083
sync.zyntapos.com       → Caddy → sync:8082  (WebSocket)
panel.zyntapos.com      → Caddy → canary:80  (placeholder — Phase 3)
docs.zyntapos.com       → Cloudflare Pages (zyntapos-docs project, deployed via cd-docs.yml)
status.zyntapos.com     → Caddy → canary:80  (placeholder — Phase 3)
```

All orange-cloud subdomains use the Cloudflare Origin Certificate at
`/etc/caddy/certs/zyntapos_origin.{pem,key}` (wildcard `*.zyntapos.com`, 15-year validity).

**Cloudflare configuration:**
- **SSL/TLS mode:** Full (Strict) — requires valid Origin Certificate
- **Origin Certificate:** Deployed via `CF_ORIGIN_CERT` / `CF_ORIGIN_KEY` GitHub Secrets (FTS Step 4)
- **Tunnel:** Optional `cloudflared` container for Zero Trust access (via `CLOUDFLARE_TUNNEL_TOKEN`)
- **HSTS:** On, max-age 6 months, include subdomains
- **Min TLS:** 1.2

---

## Security Scanning

The `sec-backend-scan.yml` workflow runs weekly (Monday 01:00 UTC) and on any push to
`backend/**`:

- **OWASP Dependency Check** — Gradle plugin 10.0.4; fails on CVSS ≥ 9.0 (CRITICAL)
- **Trivy container scan** — Scans built images; results uploaded to GitHub Security tab as SARIF

See `docs/todo/009-ktor-security-hardening.md` for full security hardening details.

---

## Related Documents

| Document | Path |
|----------|------|
| ADR-006: Docker build in CI | `docs/adr/ADR-006-backend-docker-build-in-ci.md` |
| TODO-007: Infrastructure & Deployment | `docs/todo/007-infrastructure-and-deployment.md` |
| TODO-009: Backend Security Hardening | `docs/todo/009-ktor-security-hardening.md` |
| GitHub Workflows Audit | `docs/audit/github-workflows-audit.md` |
| Secrets template | `secrets/secrets.env.template` |
| VPS pre-flight check | `scripts/vps-check.sh` |
