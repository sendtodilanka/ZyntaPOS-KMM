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

### 1.4 Firewall සකසන්න (UFW)

```bash
# VPS එකේ run කරන්න
ufw default deny incoming
ufw default allow outgoing

# SSH access (ඔයාගේ port එක use කරන්න)
ufw allow 22/tcp

# HTTP + HTTPS (Cloudflare proxy එක through)
ufw allow 80/tcp
ufw allow 443/tcp

# Enable firewall
ufw enable

# Status check
ufw status verbose
```

**Cloudflare Only Access (අමතර security):**

Cloudflare IP ranges විතරක් allow කරන්න ඕනේ නම්:
```bash
# Cloudflare IPv4 ranges
for ip in 173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 \
          141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 \
          197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 \
          104.24.0.0/14 172.64.0.0/13 131.0.72.0/22; do
  ufw allow from "$ip" to any port 443 proto tcp
done
```

### 1.5 Security Hardening

```bash
# System update කරන්න
apt update && apt upgrade -y

# Automatic security updates enable කරන්න
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades

# Fail2Ban install කරන්න (brute force protection)
apt install -y fail2ban
systemctl enable fail2ban
systemctl start fail2ban

# SSH hardening
cat >> /etc/ssh/sshd_config.d/hardening.conf << 'SSHEOF'
# Max auth attempts
MaxAuthTries 3
# Disable root login with password (key only)
PermitRootLogin prohibit-password
# Disable empty passwords
PermitEmptyPasswords no
# Idle timeout (5 minutes)
ClientAliveInterval 300
ClientAliveCountMax 2
SSHEOF

systemctl restart sshd
```

### 1.6 Performance Tuning

```bash
# Swap file එකක් හදන්න (2GB — OOM protection)
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Kernel tuning for Docker
cat >> /etc/sysctl.d/99-zyntapos.conf << 'SYSEOF'
# TCP tuning
net.core.somaxconn = 1024
net.ipv4.tcp_max_syn_backlog = 1024
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_keepalive_time = 300

# File descriptor limits
fs.file-max = 65535

# VM tuning (reduce swappiness — prefer RAM)
vm.swappiness = 10
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5
SYSEOF

sysctl --system

# Docker log rotation (prevent disk fill)
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'DKEOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2"
}
DKEOF
```

### 1.7 Deploy User හදන්න (Optional — Security Best Practice)

Root වෙනුවට dedicated deploy user එකක් use කරන්න:

```bash
# Deploy user හදන්න
adduser --disabled-password --gecos "" deploy

# Docker group එකට add කරන්න
usermod -aG docker deploy

# SSH key setup
mkdir -p /home/deploy/.ssh
cp ~/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys

# Deploy directory create + ownership
mkdir -p /opt/zyntapos
chown deploy:deploy /opt/zyntapos
```

> **Note:** Deploy user use කරනවා නම් GitHub Secrets වල `VPS_USER` එක `deploy` ලෙස set කරන්න.

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

## 3. First-Time Setup Workflow එක Run කරන්න

### 3.1 Workflow Trigger කරන්න

GitHub → Actions → **"VPS First-Time Setup"** → Run workflow:
- `confirm` field එකට **`setup`** type කරන්න
- **Run workflow** click කරන්න

### 3.2 මේ Workflow එක කරන දේවල්:

**Phase 1 — Root ලෙස (system-level):**
1. **Docker install කරනවා** (VPS එකේ නැත්නම්)
2. **Deploy user create කරනවා** + Docker group එකට add
3. **Deploy user SSH key setup** — `VPS_USER_KEY` එකෙන් public key extract කරලා `authorized_keys` add
4. **`/opt/zyntapos`** directory create + deploy user ownership

**Phase 2 — Deploy user ලෙස (app-level):**
5. **Repo clone කරනවා** `/opt/zyntapos` ට
6. **Secrets generate කරනවා:**
   - `secrets/db_password.txt` — PostgreSQL password (random 64-char hex)
   - `secrets/rs256_private_key.pem` — JWT signing key (RSA 2048-bit)
   - `secrets/rs256_public_key.pem` — JWT verification key
7. **`.env` file එක හදනවා:**
   - `REDIS_PASSWORD` — Redis auth password
   - `ZYNTAPOS_DB_PASSWORD` — PostgreSQL password
8. **Self-signed TLS cert generate කරනවා** (`backend/caddy/certs/`)
9. **GHCR login + Docker images pull + stack start**

### 3.3 Verify Workflow Run

Actions tab එකේ workflow run එක green ✅ වෙන්න ඕනේ.
Fail වුණොත් [Troubleshooting](#7-troubleshooting) බලන්න.

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
