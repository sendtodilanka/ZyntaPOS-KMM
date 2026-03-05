# ZyntaPOS — Deployment Architecture

**Last updated:** 2026-03-05 (Session 5 — Sprint F)
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
        ▼  Step[3+4]: CI Gate on PR (~25 min)
        │  ci-gate.yml — full KMM build + lint + detekt + all tests
        │  Auto-merge fires when "Build & Test" check passes
        │
        ▼  Step[3+4]: CI Gate on push to main (~35 min)
        │  ci-gate.yml — parallel jobs:
        │    ├── build-and-test (KMM: build + lint + tests)
        │    └── build-backend-images (Docker → GHCR)
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

## Step[3+4]: CI Gate — Backend Docker Image Build

When CI runs on a push to `main` (after auto-merge), two jobs run in parallel:

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
| `postgres` | `postgres:16-alpine` | 5432 (internal) | Primary database |
| `redis` | `redis:7-alpine` | 6379 (internal) | Cache + pub/sub |
| `canary` | `nginx:1-alpine` | 80 (internal) | Placeholder for Phase 3 subdomains |

All backend services run with:
- `read_only: true` + `tmpfs: /tmp`
- `no-new-privileges:true`
- Memory limits (api=512m, license/sync=256m)
- Non-root user (`zyntapos:zyntapos`)

---

## VPS Prerequisites

### One-time setup (run on VPS after provisioning)

```bash
# 1. REDIS_PASSWORD — required by docker-compose.yml; redis container won't start without it
echo "REDIS_PASSWORD=$(openssl rand -hex 24)" >> /opt/zyntapos/.env

# 2. Verify .env contains all required variables
grep -E "REDIS_PASSWORD" /opt/zyntapos/.env

# 3. Secrets files (Docker secrets, not .env)
ls /opt/zyntapos/secrets/
# Must contain: db_password.txt, rs256_private_key.pem, rs256_public_key.pem
# See: secrets/secrets.env.template for generation instructions
```

### GitHub Secrets required

| Secret | Purpose | Scope needed |
|--------|---------|--------------|
| `PAT_TOKEN` | Repository dispatch (auto-PR, deploy-trigger) + GHCR pull on VPS | `repo` + **`read:packages`** |
| `VPS_HOST` | Contabo VPS IP address | — |
| `VPS_USER` | SSH username (`deploy`) | — |
| `VPS_PORT` | SSH port | — |
| `DEPLOY_SSH_PRIVATE_KEY` | SSH private key for `deploy` user | — |

> **Important:** `PAT_TOKEN` must have `read:packages` scope for the VPS GHCR login to
> succeed. If the PAT was created before the GHCR image pipeline was added, regenerate it
> with this scope at: GitHub → Settings → Developer settings → Personal access tokens.

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
docs.zyntapos.com       → Caddy → canary:80  (placeholder — Phase 3)
status.zyntapos.com     → Caddy → canary:80  (placeholder — Phase 3)
```

All orange-cloud subdomains use the Cloudflare Origin Certificate at
`/etc/caddy/certs/zyntapos_origin.{pem,key}` (wildcard `*.zyntapos.com`, 15-year validity).

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
