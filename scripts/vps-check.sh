#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# ZyntaPOS VPS Pre-Flight Check
# Run this on the VPS as root (or with sudo) BEFORE docker compose up
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/sendtodilanka/ZyntaPOS-KMM/\
#     claude/audit-kmp-roadmap-AjuNk/scripts/vps-check.sh | bash
#   — or —
#   bash /opt/zyntapos/scripts/vps-check.sh
#
# Exit code: 0 = all checks passed | 1 = one or more checks failed
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Colours ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

PASS="${GREEN}✔ PASS${RESET}"
FAIL="${RED}✘ FAIL${RESET}"
WARN="${YELLOW}⚠ WARN${RESET}"
INFO="${CYAN}ℹ INFO${RESET}"

FAILURES=0

pass() { echo -e "  $PASS  $1"; }
fail() { echo -e "  $FAIL  $1"; FAILURES=$((FAILURES + 1)); }
warn() { echo -e "  $WARN  $1"; }
info() { echo -e "  $INFO  $1"; }

section() {
  echo ""
  echo -e "${BOLD}${CYAN}══ $1 ══${RESET}"
}

# ── Deploy directory ───────────────────────────────────────────────────────────
DEPLOY_DIR="${DEPLOY_DIR:-/opt/zyntapos}"

# ══════════════════════════════════════════════════════════════════════════════
section "1. System"
# ══════════════════════════════════════════════════════════════════════════════

# OS
OS_ID=$(grep -oP '(?<=^ID=).+' /etc/os-release | tr -d '"' 2>/dev/null || echo "unknown")
OS_VER=$(grep -oP '(?<=^VERSION_ID=).+' /etc/os-release | tr -d '"' 2>/dev/null || echo "?")
info "OS: $OS_ID $OS_VER"

# Architecture
ARCH=$(uname -m)
info "Architecture: $ARCH"

# RAM
RAM_MB=$(awk '/MemTotal/ { printf "%.0f", $2/1024 }' /proc/meminfo)
if [ "$RAM_MB" -ge 7000 ]; then
  pass "RAM: ${RAM_MB} MB (>= 7 GB)"
else
  warn "RAM: ${RAM_MB} MB — expected >= 8 GB for production"
fi

# Disk
DISK_FREE=$(df -BG / | awk 'NR==2 {gsub("G",""); print $4}')
if [ "$DISK_FREE" -ge 20 ]; then
  pass "Disk free: ${DISK_FREE} GB"
else
  warn "Disk free: ${DISK_FREE} GB — recommend >= 20 GB"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "2. Users & Directories"
# ══════════════════════════════════════════════════════════════════════════════

# deploy user
if id "deploy" &>/dev/null; then
  pass "User 'deploy' exists"
else
  fail "User 'deploy' missing — run: useradd -m -s /bin/bash deploy"
fi

# deploy user in docker group
if id "deploy" &>/dev/null && groups deploy | grep -q docker; then
  pass "User 'deploy' is in docker group"
else
  fail "User 'deploy' not in docker group — run: usermod -aG docker deploy"
fi

# deploy directory
if [ -d "$DEPLOY_DIR" ]; then
  pass "Deploy dir exists: $DEPLOY_DIR"
  OWNER=$(stat -c '%U' "$DEPLOY_DIR")
  if [ "$OWNER" = "deploy" ]; then
    pass "Deploy dir owned by 'deploy'"
  else
    fail "Deploy dir owned by '$OWNER' (expected 'deploy') — run: chown -R deploy:deploy $DEPLOY_DIR"
  fi
else
  fail "Deploy dir missing: $DEPLOY_DIR — run: mkdir -p $DEPLOY_DIR && chown deploy:deploy $DEPLOY_DIR"
fi

# secrets dir
if [ -d "$DEPLOY_DIR/secrets" ]; then
  pass "Secrets dir exists: $DEPLOY_DIR/secrets"
  SOWNER=$(stat -c '%U:%G' "$DEPLOY_DIR/secrets")
  SPERM=$(stat -c '%a' "$DEPLOY_DIR/secrets")
  if [ "$SPERM" = "700" ] || [ "$SPERM" = "750" ]; then
    pass "Secrets dir permissions: $SPERM (restricted)"
  else
    warn "Secrets dir permissions: $SPERM — recommend 700: chmod 700 $DEPLOY_DIR/secrets"
  fi
else
  fail "Secrets dir missing: $DEPLOY_DIR/secrets — run: mkdir -p $DEPLOY_DIR/secrets && chmod 700 $DEPLOY_DIR/secrets"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "3. Docker"
# ══════════════════════════════════════════════════════════════════════════════

if command -v docker &>/dev/null; then
  DOCKER_VER=$(docker --version | grep -oP '[\d.]+' | head -1)
  pass "Docker installed: $DOCKER_VER"
else
  fail "Docker not installed — run setup-vps.sh"
fi

if systemctl is-active --quiet docker 2>/dev/null; then
  pass "Docker daemon running"
else
  fail "Docker daemon not running — run: systemctl start docker && systemctl enable docker"
fi

if docker compose version &>/dev/null 2>&1; then
  DC_VER=$(docker compose version --short 2>/dev/null || echo "unknown")
  pass "Docker Compose plugin: $DC_VER"
else
  fail "Docker Compose plugin missing — run: apt-get install docker-compose-plugin"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "4. Firewall"
# ══════════════════════════════════════════════════════════════════════════════

if command -v ufw &>/dev/null; then
  UFW_STATUS=$(ufw status 2>/dev/null | head -1)
  if echo "$UFW_STATUS" | grep -qi "active"; then
    pass "ufw active"
    for PORT in 22 80 443; do
      if ufw status | grep -qE "^${PORT}[/ ]"; then
        pass "Port $PORT allowed"
      else
        warn "Port $PORT not found in ufw rules — run: ufw allow $PORT"
      fi
    done
  else
    warn "ufw installed but not active — run: ufw --force enable"
  fi
else
  warn "ufw not installed — run: apt-get install ufw"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "5. fail2ban"
# ══════════════════════════════════════════════════════════════════════════════

if command -v fail2ban-client &>/dev/null; then
  if systemctl is-active --quiet fail2ban 2>/dev/null; then
    pass "fail2ban installed and running"
  else
    warn "fail2ban installed but not running — run: systemctl start fail2ban"
  fi
else
  warn "fail2ban not installed — run: apt-get install fail2ban"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "6. Secrets"
# ══════════════════════════════════════════════════════════════════════════════

check_secret() {
  local FILE="$1"
  local LABEL="$2"
  local VALIDATE_CMD="${3:-}"

  if [ -f "$DEPLOY_DIR/secrets/$FILE" ]; then
    if [ -s "$DEPLOY_DIR/secrets/$FILE" ]; then
      if [ -n "$VALIDATE_CMD" ]; then
        if eval "$VALIDATE_CMD" &>/dev/null; then
          pass "$LABEL ($FILE)"
        else
          fail "$LABEL ($FILE) — file exists but content is invalid"
        fi
      else
        pass "$LABEL ($FILE)"
      fi
    else
      fail "$LABEL ($FILE) — file is empty"
    fi
  else
    fail "$LABEL ($FILE) — file missing"
  fi
}

check_secret "db_password.txt"       "PostgreSQL password"
check_secret "rs256_private_key.pem" "RSA-2048 private key" \
  "openssl rsa -in '$DEPLOY_DIR/secrets/rs256_private_key.pem' -check -noout"
check_secret "rs256_public_key.pem"  "RSA-2048 public key" \
  "openssl rsa -in '$DEPLOY_DIR/secrets/rs256_public_key.pem' -pubin -noout"

# Check key pair matches
if [ -f "$DEPLOY_DIR/secrets/rs256_private_key.pem" ] && \
   [ -f "$DEPLOY_DIR/secrets/rs256_public_key.pem" ]; then
  PRIV_MOD=$(openssl rsa -in "$DEPLOY_DIR/secrets/rs256_private_key.pem" -modulus -noout 2>/dev/null | md5sum)
  PUB_MOD=$(openssl rsa -in "$DEPLOY_DIR/secrets/rs256_public_key.pem" -pubin -modulus -noout 2>/dev/null | md5sum)
  if [ "$PRIV_MOD" = "$PUB_MOD" ]; then
    pass "RSA key pair matches (public ↔ private)"
  else
    fail "RSA key pair MISMATCH — public and private keys do not match"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
section "7. Environment File (.env)"
# ══════════════════════════════════════════════════════════════════════════════

ENV_FILE="$DEPLOY_DIR/.env"
if [ -f "$ENV_FILE" ]; then
  pass ".env file exists"
  if grep -q "REDIS_PASSWORD=" "$ENV_FILE" && \
     [ -n "$(grep 'REDIS_PASSWORD=' "$ENV_FILE" | cut -d= -f2)" ]; then
    pass "REDIS_PASSWORD set in .env"
  else
    fail "REDIS_PASSWORD missing or empty in .env"
  fi
else
  fail ".env file missing at $DEPLOY_DIR/.env"
  info "Create it: echo 'REDIS_PASSWORD=$(openssl rand -hex 24)' > $DEPLOY_DIR/.env"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "8. TLS Certificates (Cloudflare Origin)"
# ══════════════════════════════════════════════════════════════════════════════

CERT_DIR="/etc/caddy/certs"
if [ -d "$CERT_DIR" ]; then
  pass "TLS cert dir exists: $CERT_DIR"
  CERT_FILES=$(find "$CERT_DIR" -name "*.pem" -o -name "*.crt" 2>/dev/null | wc -l)
  KEY_FILES=$(find "$CERT_DIR" -name "*.key" 2>/dev/null | wc -l)
  if [ "$CERT_FILES" -gt 0 ]; then
    pass "Certificate file found ($CERT_FILES .pem/.crt)"
    # Check expiry of first cert found
    FIRST_CERT=$(find "$CERT_DIR" -name "*.pem" -o -name "*.crt" 2>/dev/null | head -1)
    if [ -n "$FIRST_CERT" ]; then
      EXPIRY=$(openssl x509 -in "$FIRST_CERT" -noout -enddate 2>/dev/null | cut -d= -f2)
      info "Certificate expiry: $EXPIRY"
    fi
  else
    fail "No certificate files (.pem/.crt) found in $CERT_DIR"
    info "Generate at: Cloudflare → SSL/TLS → Origin Server → Create Certificate"
  fi
  if [ "$KEY_FILES" -gt 0 ]; then
    pass "Private key file found ($KEY_FILES .key)"
  else
    fail "No .key file found in $CERT_DIR"
  fi
else
  fail "TLS cert dir missing: $CERT_DIR — run: mkdir -p $CERT_DIR"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "9. Repo & Config Files"
# ══════════════════════════════════════════════════════════════════════════════

check_file() {
  local REL="$1"
  local LABEL="$2"
  if [ -f "$DEPLOY_DIR/$REL" ]; then
    pass "$LABEL"
  else
    fail "$LABEL — missing: $DEPLOY_DIR/$REL"
  fi
}

check_file "docker-compose.yml"              "docker-compose.yml"
check_file "Caddyfile"                       "Caddyfile"
check_file "backend/postgres/postgresql.conf" "PostgreSQL tuning config"
check_file "scripts/backup.sh"               "Backup script"

# backup.sh executable?
if [ -f "$DEPLOY_DIR/scripts/backup.sh" ]; then
  if [ -x "$DEPLOY_DIR/scripts/backup.sh" ]; then
    pass "backup.sh is executable"
  else
    fail "backup.sh not executable — run: chmod +x $DEPLOY_DIR/scripts/backup.sh"
  fi
fi

# ══════════════════════════════════════════════════════════════════════════════
section "10. Backup (rclone + cron)"
# ══════════════════════════════════════════════════════════════════════════════

if command -v rclone &>/dev/null; then
  RCLONE_VER=$(rclone --version 2>/dev/null | head -1)
  pass "rclone installed: $RCLONE_VER"
  # Check B2 remote configured
  if rclone listremotes 2>/dev/null | grep -qi "b2\|backblaze\|zyntapos"; then
    pass "rclone remote configured (Backblaze B2)"
  else
    warn "No rclone remote found — configure B2: rclone config"
  fi
else
  warn "rclone not installed — backup will not work until installed"
  info "Install: curl https://rclone.org/install.sh | sudo bash"
fi

# Cron job check
if crontab -l 2>/dev/null | grep -q "backup.sh"; then
  pass "Backup cron job configured"
else
  warn "Backup cron job not found — add with: crontab -e"
  info "Add line: 0 21 * * * /opt/zyntapos/scripts/backup.sh >> /var/log/zyntapos-backup.log 2>&1"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "11. Docker Services (if running)"
# ══════════════════════════════════════════════════════════════════════════════

EXPECTED_SERVICES=("zyntapos_caddy" "zyntapos_api" "zyntapos_license" "zyntapos_sync" "zyntapos_postgres" "zyntapos_redis" "zyntapos_canary")

if docker ps &>/dev/null 2>&1; then
  ALL_RUNNING=true
  for SVC in "${EXPECTED_SERVICES[@]}"; do
    STATUS=$(docker inspect --format='{{.State.Status}}' "$SVC" 2>/dev/null || echo "not_found")
    HEALTH=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$SVC" 2>/dev/null || echo "?")
    if [ "$STATUS" = "running" ]; then
      if [ "$HEALTH" = "healthy" ] || [ "$HEALTH" = "no-healthcheck" ]; then
        pass "$SVC — running ($HEALTH)"
      else
        warn "$SVC — running but health: $HEALTH"
      fi
    elif [ "$STATUS" = "not_found" ]; then
      warn "$SVC — not started yet (run: docker compose up -d)"
      ALL_RUNNING=false
    else
      fail "$SVC — status: $STATUS"
      ALL_RUNNING=false
    fi
  done
else
  warn "Cannot connect to Docker daemon — skipping container checks"
fi

# ══════════════════════════════════════════════════════════════════════════════
section "12. DNS Resolution"
# ══════════════════════════════════════════════════════════════════════════════

check_dns() {
  local HOST="$1"
  if command -v dig &>/dev/null; then
    IP=$(dig +short "$HOST" 2>/dev/null | head -1)
  elif command -v nslookup &>/dev/null; then
    IP=$(nslookup "$HOST" 2>/dev/null | awk '/^Address: / { print $2 }' | head -1)
  else
    IP=""
  fi
  if [ -n "$IP" ]; then
    pass "DNS: $HOST → $IP"
  else
    warn "DNS: $HOST — not resolving (Cloudflare DNS not configured yet?)"
  fi
}

check_dns "api.zyntapos.com"
check_dns "license.zyntapos.com"
check_dns "sync.zyntapos.com"
check_dns "panel.zyntapos.com"

# ══════════════════════════════════════════════════════════════════════════════
section "13. Health Endpoints (if services running)"
# ══════════════════════════════════════════════════════════════════════════════

check_health() {
  local URL="$1"
  local LABEL="$2"
  if command -v curl &>/dev/null; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$URL" 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
      pass "$LABEL → HTTP $HTTP_CODE"
    elif [ "$HTTP_CODE" = "000" ]; then
      warn "$LABEL → connection refused (service not running?)"
    else
      warn "$LABEL → HTTP $HTTP_CODE"
    fi
  fi
}

check_health "http://localhost:8080/health"  "API health"
check_health "http://localhost:8083/health"  "License health"
check_health "http://localhost:8082/health"  "Sync health"
check_health "http://localhost:80/ping"      "Caddy ping"

# ══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ══════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${BOLD}════════════════════════════════════════${RESET}"
if [ "$FAILURES" -eq 0 ]; then
  echo -e "${BOLD}${GREEN}  ALL CHECKS PASSED — ready to deploy${RESET}"
  echo -e "${BOLD}════════════════════════════════════════${RESET}"
  echo ""
  echo "  Next step:"
  echo "    cd $DEPLOY_DIR"
  echo "    docker compose pull"
  echo "    docker compose up -d"
  echo ""
  exit 0
else
  echo -e "${BOLD}${RED}  $FAILURES CHECK(S) FAILED — fix above issues first${RESET}"
  echo -e "${BOLD}════════════════════════════════════════${RESET}"
  echo ""
  echo "  Fix the FAIL items above, then re-run this script."
  echo ""
  exit 1
fi
