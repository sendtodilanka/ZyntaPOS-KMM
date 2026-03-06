# ZyntaPOS VPS Setup Guide (සිංහල)

> **අවසන් යාවත්කාලීනය:** 2026-03-06
> **VPS Provider:** Contabo Cloud VPS 10 (8 GB RAM, 4 vCPU, 200 GB SSD)
> **OS:** Ubuntu 24.04 LTS

---

## පටුන

1. [VPS එක ලැබුණාට පස්සේ කළ යුතු දේ](#1-vps-එක-ලැබුණාට-පස්සේ-කළ-යුතු-දේ)
2. [GitHub Secrets සකසන්න](#2-github-secrets-සකසන්න)
3. [First-Time Setup Workflow එක Run කරන්න](#3-first-time-setup-workflow-එක-run-කරන්න)
4. [Cloudflare DNS + SSL සකසන්න](#4-cloudflare-dns--ssl-සකසන්න)
5. [Pipeline එක Test කරන්න](#5-pipeline-එක-test-කරන්න)
6. [Verify කරන්න](#6-verify-කරන්න)
7. [Troubleshooting](#7-troubleshooting)

---

## 1. VPS එක ලැබුණාට පස්සේ කළ යුතු දේ

### 1.1 VPS Login Details ලබාගන්න

Contabo control panel එකෙන් මේවා ලබාගන්න:
- **IP Address** (උදා: `217.216.72.102`)
- **Root Password**
- **SSH Port** (default: `22`)

### 1.2 SSH Key Pairs හදන්න (Local Machine එකේ)

**Root සහ Deploy user ට වෙන වෙනම keys ඕනේ:**

```bash
# ── Root key pair (system-level workflows: first-time-setup, postgres-fix) ──
ssh-keygen -t ed25519 -C "zyntapos-vps-root" -f ~/.ssh/zyntapos_root

# ── Deploy key pair (daily workflows: deploy, verify, debug, full-fix) ──
ssh-keygen -t ed25519 -C "zyntapos-vps-deploy" -f ~/.ssh/zyntapos_deploy

# Private keys display (GitHub Secrets වලට copy කරන්න)
cat ~/.ssh/zyntapos_root       # → VPS_ROOT_KEY secret
cat ~/.ssh/zyntapos_deploy     # → VPS_USER_KEY secret
```

### 1.3 VPS එකට Root SSH Key එක Add කරන්න

```bash
# VPS එකට SSH කරන්න (Contabo password එක use කරන්න)
ssh root@<VPS_IP>

# Root key add කරන්න
mkdir -p ~/.ssh
chmod 700 ~/.ssh

# Root public key එක paste කරන්න
echo "ssh-ed25519 AAAA... zyntapos-vps-root" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

> **Note:** Deploy user key එක add කරන්න ඕනේ නැහැ — First-Time Setup workflow එක automatically deploy user create කරලා key add කරනවා `VPS_USER_KEY` secret එකෙන්.

### 1.4 Manual Steps ඕනේ නැහැ!

> **Important:** UFW, Fail2Ban, SSH hardening, performance tuning, deploy user creation — මේ ඔක්කොම **FTS Steps 1-3** workflows එකෙන් automatically handle කරනවා.
>
> Manual commands run කරන්න ඕනේ **නැහැ**. Section 2 (GitHub Secrets) set කරලා Section 3 (FTS Steps) run කරන්න.

---

## 2. GitHub Secrets සකසන්න

GitHub repo → Settings → Secrets and variables → Actions → **New repository secret**

### Connection Secrets

| Secret Name | Value | Description |
|---|---|---|
| `VPS_HOST` | `217.216.72.102` | VPS IP address |
| `VPS_PORT` | `22` | SSH port |

### Root Secrets (system-level workflows only)

| Secret Name | Value | Used By |
|---|---|---|
| `VPS_ROOT` | `root` | `vps-first-time-setup.yml`, `vps-postgres-fix.yml` |
| `VPS_ROOT_KEY` | `~/.ssh/zyntapos_root` content | Same as above |

### Deploy Secrets (daily workflows)

| Secret Name | Value | Used By |
|---|---|---|
| `VPS_USER` | `deploy` | `cd-deploy.yml`, `cd-smoke-rollback.yml`, `vps-verify.yml`, `vps-debug.yml`, `vps-full-fix.yml` |
| `VPS_USER_KEY` | `~/.ssh/zyntapos_deploy` content | Same as above |

### Other Secrets

| Secret Name | Value | Description |
|---|---|---|
| `PAT_TOKEN` | `ghp_xxxx...` | GitHub PAT (repo + packages scope) |

### Workflow → User Mapping

```
┌─────────────────────────────────────────────────────────────────┐
│  ROOT (VPS_ROOT / VPS_ROOT_KEY)                                 │
│  • vps-first-time-setup.yml — Docker install, deploy user create│
│  • vps-postgres-fix.yml     — System config modify              │
├─────────────────────────────────────────────────────────────────┤
│  DEPLOY (VPS_USER / VPS_USER_KEY)                     │
│  • cd-deploy.yml            — Git pull, docker compose up       │
│  • cd-smoke-rollback.yml    — Rollback on failure               │
│  • vps-verify.yml           — Read-only health checks           │
│  • vps-debug.yml            — Container logs + debugging        │
│  • vps-full-fix.yml         — App reset, docker compose restart │
└─────────────────────────────────────────────────────────────────┘
```

### PAT Token හදන විදිය:

GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens:
- **Repository access:** `sendtodilanka/ZyntaPOS-KMM`
- **Permissions needed:**
  - `Contents: Read and Write` (deploy dispatch)
  - `Packages: Read` (GHCR image pull)
  - `Actions: Read and Write` (workflow dispatch)

### GitHub CLI එකෙන් Secrets Set කරන්න (Optional):

```bash
# Login
gh auth login

REPO="sendtodilanka/ZyntaPOS-KMM"

# Connection
gh secret set VPS_HOST --body "217.216.72.102" --repo "$REPO"
gh secret set VPS_PORT --body "22" --repo "$REPO"

# Root (system-level)
gh secret set VPS_ROOT --body "root" --repo "$REPO"
gh secret set VPS_ROOT_KEY < ~/.ssh/zyntapos_root --repo "$REPO"

# Deploy (daily operations)
gh secret set VPS_USER --body "deploy" --repo "$REPO"
gh secret set VPS_USER_KEY < ~/.ssh/zyntapos_deploy --repo "$REPO"

# GitHub PAT
gh secret set PAT_TOKEN --body "ghp_xxxx..." --repo "$REPO"
```

---

## 3. First-Time Setup (FTS) — 6-Step Workflow

### 3.0 Overview

Fresh VPS එකක් setup කරන්න **6 steps** තියෙනවා. හැම step එකක්ම **separate workflow** එකක්. Step එකක් **green** වුණාම (✅) ඊළඟ step එක run කරන්න.

```
┌─────────────────────────────────────────────────────────────────────┐
│  FTS Step 1 (ROOT)  → System + Docker + Deploy User                │
│  FTS Step 2 (ROOT)  → Security Hardening (UFW, Fail2Ban, SSH)      │
│  FTS Step 3 (ROOT)  → Performance Tuning (Swap, Kernel, Docker)    │
│  FTS Step 4 (DEPLOY)→ Repo Clone + Secrets + TLS Certificate       │
│  FTS Step 5 (DEPLOY)→ GHCR Login + Docker Stack Start              │
│  FTS Step 6 (BOTH)  → Full End-to-End Verification                 │
└─────────────────────────────────────────────────────────────────────┘
```

**Rules:**
- හැම step එකක්ම **green** වෙන්න ඕනේ ඊළඟ step එකට යන්න කලින්
- Step එකක් **red** (❌) වුණොත් — ඒක fix කරලා **same step එකම** re-run කරන්න
- Manual commands run කරන්න ඕනේ **නැහැ** — okkoma automated
- Steps 1-3: **root** SSH key use කරනවා (system-level changes)
- Steps 4-6: **deploy** SSH key use කරනවා (app-level changes)

### 3.1 FTS Step 1: System + Docker + Deploy User

**Runs as:** Root | **Confirm:** `step1`

GitHub → Actions → **"FTS Step 1: System + Docker + Deploy User"** → Run workflow

මේ step එක කරන දේවල්:
1. System packages update (`apt update + upgrade`)
2. Docker + Docker Compose plugin install
3. Deploy user create + docker group add
4. Docker-only sudo configure (full root access දෙන්නේ නැහැ)
5. Deploy user SSH key setup (`VPS_USER_KEY` එකෙන්)
6. `/opt/zyntapos` directory create + deploy user ownership

**Verifies:** Docker running, deploy user exists, SSH key present, directory ownership

### 3.2 FTS Step 2: Security Hardening

**Runs as:** Root | **Confirm:** `step2`

GitHub → Actions → **"FTS Step 2: Security Hardening"** → Run workflow

මේ step එක කරන දේවල්:
1. **UFW Firewall** — deny all incoming, allow SSH/80/443 only
2. **Fail2Ban** — SSH brute force protection (3 retries, 2h ban)
3. **SSH Hardening** — key-only auth, MaxAuthTries 3, idle timeout 5min, no X11/TCP forwarding
4. **Unattended Upgrades** — automatic security patches (daily)
5. **Kernel Security** — SYN cookies, ASLR, ICMP redirect block, core dump disable

**Verifies:** UFW active, Fail2Ban SSH jail, SSH config, auto-upgrades, kernel params

### 3.3 FTS Step 3: Performance Tuning

**Runs as:** Root | **Confirm:** `step3`

GitHub → Actions → **"FTS Step 3: Performance Tuning"** → Run workflow

මේ step එක කරන දේවල්:
1. **2GB Swap File** — OOM protection
2. **Kernel TCP Tuning** — somaxconn=1024, keepalive, fast open
3. **File Descriptor Limits** — 65535 (soft + hard)
4. **VM Tuning** — swappiness=10, dirty ratios optimized
5. **Docker Daemon Config** — log rotation (10m×3), overlay2, live-restore, ulimits

**Verifies:** Swap active, kernel params, Docker daemon.json, file limits

### 3.4 FTS Step 4: Repo + Secrets + TLS

**Runs as:** Deploy | **Confirm:** `step4`

GitHub → Actions → **"FTS Step 4: Repo + Secrets + TLS"** → Run workflow

මේ step එක කරන දේවල්:
1. Repository clone (`/opt/zyntapos`)
2. DB password generate (64-char hex, cryptographically random)
3. RS256 JWT key pair generate (RSA 2048-bit)
4. `.env` file create (REDIS_PASSWORD, ZYNTAPOS_DB_PASSWORD)
5. Self-signed TLS certificate generate (365 days)
6. File permissions fix

**Verifies:** Repo files, secrets, RS256 key match, .env vars, TLS cert

### 3.5 FTS Step 5: Docker Stack Start

**Runs as:** Deploy | **Confirm:** `step5`

GitHub → Actions → **"FTS Step 5: Docker Stack Start"** → Run workflow

මේ step එක කරන දේවල්:
1. GHCR authenticate (GitHub Container Registry)
2. docker-compose.yml validate
3. Docker images pull
4. Full stack start (7 containers)
5. Health check wait (max 120s)

**Verifies:** All 7 containers running, volumes created, network created

### 3.6 FTS Step 6: Full Verification

**Runs as:** Root + Deploy (2 jobs) | **Confirm:** `step6`

GitHub → Actions → **"FTS Step 6: Full Verification"** → Run workflow

**End-to-end verification — okkoma check කරනවා:**
- Security: UFW, Fail2Ban, SSH, auto-upgrades, kernel security
- Performance: Swap, kernel tuning, Docker config, file limits
- Users: Deploy user, docker group, sudoers
- Repo: Git, config files, healthcheck directory
- Secrets: DB password, RS256 keys (match verify), TLS cert
- Environment: .env variables
- Containers: All 7 running + healthy
- Database: PostgreSQL TCP auth + per-service DB access
- Redis: PING → PONG
- HTTP: Caddy /ping + API/License/Sync /health
- Volumes + Network
- Disk + Memory

**Green (🟢)** = VPS fully ready for production
**Red (🔴)** = Fix issues and re-run

---

## 4. Cloudflare DNS + SSL සකසන්න

### 4.1 DNS Records Add කරන්න

Cloudflare Dashboard → DNS → Records:

| Type | Name | Content | Proxy |
|---|---|---|---|
| A | `api` | `<VPS_IP>` | Proxied (orange cloud) |
| A | `license` | `<VPS_IP>` | Proxied |
| A | `sync` | `<VPS_IP>` | Proxied |
| A | `panel` | `<VPS_IP>` | Proxied |
| A | `docs` | `<VPS_IP>` | Proxied |
| A | `status` | `<VPS_IP>` | Proxied |

### 4.2 SSL/TLS Mode Set කරන්න

Cloudflare → SSL/TLS → Overview:

> **"Full"** select කරන්න (NOT "Full (Strict)")

**ඇයි "Full" use කරන්නේ:**
- VPS එකේ self-signed certificate එකක් use කරනවා
- "Full (Strict)" නම් Cloudflare trusted CA cert එකක් ඕනේ — self-signed reject කරනවා (Error 526)
- "Full" නම් Cloudflare origin cert validate කරන්නේ නැහැ — self-signed accept කරනවා
- Traffic තාමත් encrypted (Cloudflare ↔ VPS)

### 4.3 Origin Certificate Use කරන්න (Optional — For "Full Strict")

"Full (Strict)" use කරන්න ඕනේ නම්:

1. Cloudflare → SSL/TLS → Origin Server → **Create Certificate**
2. Hostnames: `zyntapos.com`, `*.zyntapos.com`
3. Certificate + Private Key download කරන්න
4. VPS එකට copy කරන්න:

```bash
# VPS එකේ run කරන්න
scp zyntapos_origin.pem root@<VPS_IP>:/opt/zyntapos/backend/caddy/certs/
scp zyntapos_origin.key root@<VPS_IP>:/opt/zyntapos/backend/caddy/certs/

# Caddy restart
ssh root@<VPS_IP> "cd /opt/zyntapos && docker compose restart caddy"
```

ඊට පස්සේ Cloudflare SSL mode එක **"Full (Strict)"** ට change කරන්න.

---

## 5. Pipeline එක Test කරන්න

### 5.1 Full Pipeline Flow

```
Code Push to main
       ↓
[Step 3+4] CI Gate — build, test, lint, detekt
       ↓ (pass වුණොත්)
[Step 3+4] Build Backend Docker Images → push to GHCR
       ↓
[Step 5] Deploy to VPS — SSH, git pull, docker compose pull + up
       ↓
[Step 6] Smoke Test — HTTPS endpoints check (6 subdomains)
       ↓ (pass වුණොත්)
[Step 7] Verify Endpoints — final health check
```

### 5.2 Manual Deploy Trigger

Pipeline test කරන්න GitHub Actions → **"Step[5]: Deploy to VPS"** → Run workflow.

### 5.3 VPS Verify Workflow

Docker containers healthy ද check කරන්න:
GitHub Actions → **"VPS Verify"** → Run workflow.

---

## 6. Verify කරන්න

### 6.1 Endpoints Check

Browser එකෙන් check කරන්න:

| URL | Expected Response |
|---|---|
| `https://api.zyntapos.com/health` | `{"status":"ok"}` |
| `https://api.zyntapos.com/ping` | `ok` |
| `https://license.zyntapos.com/health` | `{"status":"ok"}` |
| `https://sync.zyntapos.com/health` | `{"status":"ok"}` |
| `https://panel.zyntapos.com/` | Canary page (coming soon) |
| `https://docs.zyntapos.com/` | Canary page |
| `https://status.zyntapos.com/` | Canary page |

### 6.2 VPS එකේ Manual Check (SSH)

```bash
ssh root@<VPS_IP>
cd /opt/zyntapos

# Container status check
docker compose ps

# Expected output — සියලුම containers "Up" + "healthy" විය යුතුයි:
# zyntapos_api       Up (healthy)
# zyntapos_license   Up (healthy)
# zyntapos_sync      Up (healthy)
# zyntapos_postgres  Up (healthy)
# zyntapos_redis     Up (healthy)
# zyntapos_caddy     Up (healthy)
# zyntapos_canary    Up

# Individual container health check
docker exec zyntapos_api wget -qO- http://localhost:8080/health
docker exec zyntapos_license wget -qO- http://localhost:8083/health
docker exec zyntapos_sync wget -qO- http://localhost:8082/health

# Database check
docker exec zyntapos_postgres psql -U zyntapos -d zyntapos_api -c "SELECT current_database();"
docker exec zyntapos_postgres psql -U zyntapos -d zyntapos_license -c "SELECT current_database();"

# Logs check (error තියෙනවා ද?)
docker logs zyntapos_api --tail 20
docker logs zyntapos_license --tail 20

# Disk usage
df -h /
docker system df
```

---

## 7. Troubleshooting

### Error: "RS256_PRIVATE_KEY_PATH or RS256_PRIVATE_KEY must be set"

**හේතුව:** RS256 key file permissions — container user (non-root) file read කරන්න බැහැ.

**Fix:**
```bash
ssh root@<VPS_IP>
chmod 644 /opt/zyntapos/secrets/rs256_private_key.pem
chmod 644 /opt/zyntapos/secrets/rs256_public_key.pem
cd /opt/zyntapos && docker compose restart api license sync
```

### Error: Cloudflare Error 526 (Invalid SSL Certificate)

**හේතුව:** Cloudflare SSL mode "Full (Strict)" + self-signed cert.

**Fix:** Cloudflare → SSL/TLS → Overview → **"Full"** (not strict) select කරන්න.

### Error: API Container Restarting

```bash
# Logs check
docker logs zyntapos_api --tail 50

# Secrets file check
ls -la /opt/zyntapos/secrets/
cat /opt/zyntapos/.env

# Full reset (database reset included)
# GitHub Actions → "VPS Full Fix" → confirm: "fullfix", reset_db: "yes"
```

### Error: Database "zyntapos_api" does not exist

**හේතුව:** `init-databases.sh` run නොවුණා (pgdata volume already initialized).

**Fix:**
```bash
cd /opt/zyntapos
docker compose down
docker volume rm zyntapos_pgdata
docker compose up -d
# init-databases.sh automatically run වෙනවා fresh volume එකට
```

### Error: SSH Connection Timeout (GitHub Actions)

**Check list:**
1. VPS IP correct ද? → `VPS_HOST` secret check
2. SSH port correct ද? → `VPS_PORT` secret check
3. Firewall SSH allow කරලා ද? → `ufw status`
4. SSH key correct ද? → `VPS_USER_KEY` secret re-set

### Error: Docker Image Pull Failed

**හේතුව:** GHCR authentication fail.

**Fix:**
1. `PAT_TOKEN` secret re-set (expired token)
2. Token permissions check: `packages: read` scope ඕනේ
3. Manual test:
```bash
echo "ghp_xxxx" | docker login ghcr.io -u sendtodilanka --password-stdin
docker pull ghcr.io/sendtodilanka/zyntapos-api:latest
```

### Smoke Test Always Failing

**හේතුව:** Smoke test external HTTPS endpoints check කරනවා (GitHub runner → Cloudflare → VPS).

**Check list:**
1. Cloudflare DNS records add කරලා ද?
2. Cloudflare SSL mode "Full" ද?
3. VPS firewall port 80 + 443 allow ද?
4. Caddy container healthy ද?

```bash
# Caddy logs
docker logs zyntapos_caddy --tail 20

# Cert files exist ද?
ls -la /opt/zyntapos/backend/caddy/certs/
```

---

## Architecture Overview

```
                    Internet
                       │
                 ┌─────┴─────┐
                 │ Cloudflare │  DNS + CDN + WAF + TLS termination
                 └─────┬─────┘
                       │ HTTPS (port 443)
                 ┌─────┴─────┐
                 │   Caddy    │  Reverse proxy (origin TLS cert)
                 └─────┬─────┘
            ┌──────────┼──────────┐
            │          │          │
     ┌──────┴──┐ ┌─────┴───┐ ┌───┴────┐
     │   API   │ │ License │ │  Sync  │
     │  :8080  │ │  :8083  │ │ :8082  │
     └────┬────┘ └────┬────┘ └───┬────┘
          │           │          │
     ┌────┴───────────┴────┐    │
     │     PostgreSQL      │    │
     │  zyntapos_api (DB)  │    │
     │  zyntapos_license   │    │
     └────────────────────-┘    │
                           ┌────┴────┐
                           │  Redis  │
                           │  :6379  │
                           └─────────┘
```

### Docker Compose Services (7)

| Service | Image | Port | Purpose |
|---|---|---|---|
| `caddy` | `caddy:2-alpine` | 80, 443 | TLS reverse proxy |
| `api` | `ghcr.io/.../zyntapos-api` | 8080 | REST API (Ktor) |
| `license` | `ghcr.io/.../zyntapos-license` | 8083 | License server |
| `sync` | `ghcr.io/.../zyntapos-sync` | 8082 | WebSocket sync |
| `postgres` | `postgres:16-alpine` | 5432 | Database |
| `redis` | `redis:7-alpine` | 6379 | Cache + pub/sub |
| `canary` | `nginx:1-alpine` | 80 | Placeholder pages |

### VPS File Structure

```
/opt/zyntapos/                      # Deploy root
├── docker-compose.yml              # Service definitions
├── Caddyfile                       # Reverse proxy config
├── nginx.conf                      # Canary page routing
├── .env                            # Runtime env vars (git-ignored)
│   ├── REDIS_PASSWORD=xxx
│   └── ZYNTAPOS_DB_PASSWORD=xxx
├── secrets/                        # Secret files (git-ignored)
│   ├── db_password.txt             # PostgreSQL password
│   ├── rs256_private_key.pem       # JWT signing key
│   └── rs256_public_key.pem        # JWT verification key
├── backend/
│   ├── caddy/certs/                # TLS certificates (git-ignored)
│   │   ├── zyntapos_origin.pem
│   │   └── zyntapos_origin.key
│   └── postgres/
│       ├── init-databases.sh       # Creates per-service DBs on first run
│       └── postgresql.conf         # Performance tuning
└── healthcheck/                    # Canary HTML pages
```

---

## Quick Reference Commands

```bash
# Container status
docker compose ps

# Restart specific service
docker compose restart api

# View logs
docker logs zyntapos_api --tail 50 -f

# Enter container shell
docker exec -it zyntapos_api sh

# Database access
docker exec -it zyntapos_postgres psql -U zyntapos -d zyntapos_api

# Full restart (keep data)
docker compose down && docker compose up -d

# Full reset (lose data)
docker compose down && docker volume rm zyntapos_pgdata && docker compose up -d

# Disk cleanup
docker system prune -af --volumes

# Check secret files
ls -la secrets/
cat .env
```
