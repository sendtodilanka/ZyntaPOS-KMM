#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — Uptime Kuma monitor setup script (TODO-007c)
# Creates all 19 monitors and 2 alert channels via Uptime Kuma REST API.
# Run ONCE after FTS-5 (docker stack start) on a fresh VPS, or to re-apply
# monitor config after a reset.
#
# Usage:
#   ./setup-uptime-kuma.sh
#
# Required env vars:
#   KUMA_URL          — Uptime Kuma base URL, e.g. http://localhost:3001
#   KUMA_USERNAME     — admin username (set during first-time UI setup)
#   KUMA_PASSWORD     — admin password
#
# Optional env vars:
#   DISCORD_WEBHOOK_URL  — Discord webhook for notifications
#   ALERT_EMAIL          — email address for alert notifications
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

KUMA_URL="${KUMA_URL:-http://localhost:3001}"
KUMA_USERNAME="${KUMA_USERNAME:-admin}"
KUMA_PASSWORD="${KUMA_PASSWORD:-}"

if [[ -z "$KUMA_PASSWORD" ]]; then
    echo "ERROR: KUMA_PASSWORD must be set" >&2
    exit 1
fi

echo "[kuma-setup] ── Uptime Kuma monitor setup ── $(date -u +%Y%m%dT%H%M%SZ)"
echo "[kuma-setup] Target: $KUMA_URL"

# ── Helper: authenticate and store cookie ────────────────────────────────────
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

kuma_login() {
    echo "[kuma-setup] Authenticating..."
    local response
    response=$(curl -fsS -c "$COOKIE_JAR" \
        -X POST "$KUMA_URL/api/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$KUMA_USERNAME\",\"password\":\"$KUMA_PASSWORD\"}" \
        2>&1) || {
        echo "[kuma-setup] ERROR: Login failed. Is Uptime Kuma running at $KUMA_URL?" >&2
        exit 1
    }
    echo "[kuma-setup] Login successful"
}

# ── Helper: create monitor ───────────────────────────────────────────────────
create_monitor() {
    local name="$1"
    local type="$2"
    local target="$3"
    local interval="${4:-60}"
    local extra="${5:-}"

    local body="{\"name\":\"$name\",\"type\":\"$type\",\"interval\":$interval"

    case "$type" in
        http)      body="$body,\"url\":\"$target\"" ;;
        tcp)       body="$body,\"hostname\":\"$(echo "$target" | cut -d: -f1)\",\"port\":$(echo "$target" | cut -d: -f2)" ;;
        ping)      body="$body,\"hostname\":\"$target\"" ;;
        push)      body="$body" ;;  # push monitors have no target
    esac

    [[ -n "$extra" ]] && body="$body,$extra"
    body="$body,\"active\":true}"

    local result
    result=$(curl -fsS -b "$COOKIE_JAR" \
        -X POST "$KUMA_URL/api/monitors" \
        -H "Content-Type: application/json" \
        -d "$body") || {
        echo "[kuma-setup] WARNING: Failed to create monitor '$name'" >&2
        return 0
    }

    local monitor_id
    monitor_id=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('monitorID','?'))" 2>/dev/null || echo "?")
    echo "[kuma-setup]   ✓ Created monitor '$name' (id=$monitor_id)"
}

# ── Helper: create notification channel ─────────────────────────────────────
create_notification() {
    local name="$1"
    local type="$2"
    local config="$3"

    curl -fsS -b "$COOKIE_JAR" \
        -X POST "$KUMA_URL/api/notifications" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"type\":\"$type\",\"isDefault\":true,$config}" > /dev/null || {
        echo "[kuma-setup] WARNING: Failed to create notification '$name'" >&2
        return 0
    }
    echo "[kuma-setup]   ✓ Created notification channel '$name'"
}

# ── Login ────────────────────────────────────────────────────────────────────
kuma_login

# ── Notification channels ────────────────────────────────────────────────────
echo "[kuma-setup] Creating notification channels..."

if [[ -n "${DISCORD_WEBHOOK_URL:-}" ]]; then
    create_notification "Discord Alerts" "discord" \
        "\"discordWebhookUrl\":\"$DISCORD_WEBHOOK_URL\""
fi

if [[ -n "${ALERT_EMAIL:-}" ]]; then
    create_notification "Email Alerts" "smtp" \
        "\"smtpHost\":\"localhost\",\"smtpPort\":25,\"smtpTo\":\"$ALERT_EMAIL\""
fi

# ── External monitors (public URLs) ─────────────────────────────────────────
echo "[kuma-setup] Creating external HTTP monitors..."
create_monitor "API — Health (External)"     "http" "https://api.zyntapos.com/health"    60
create_monitor "License — Health (External)" "http" "https://license.zyntapos.com/health" 60
create_monitor "Sync — Health (External)"    "http" "https://sync.zyntapos.com/health"    60
create_monitor "Panel (External)"            "http" "https://panel.zyntapos.com"           120
create_monitor "Website (External)"          "http" "https://zyntapos.com"                 120
create_monitor "Status Page (External)"      "http" "https://status.zyntapos.com"          120

# ── Internal monitors (Docker network) ───────────────────────────────────────
echo "[kuma-setup] Creating internal HTTP monitors..."
create_monitor "API — Health (Internal)"     "http" "http://api:8080/health"     30
create_monitor "License — Health (Internal)" "http" "http://license:8083/health" 30
create_monitor "Sync — Health (Internal)"    "http" "http://sync:8082/health"    30

# ── TCP port monitors ─────────────────────────────────────────────────────────
echo "[kuma-setup] Creating TCP monitors..."
create_monitor "PostgreSQL TCP"  "tcp" "postgres:5432" 60
create_monitor "Redis TCP"       "tcp" "redis:6379"    60

# ── TLS certificate monitors ─────────────────────────────────────────────────
echo "[kuma-setup] Creating TLS certificate monitors..."
create_monitor "TLS — api.zyntapos.com"     "http" "https://api.zyntapos.com/health"     86400 '"checkCertExpiry":true,"expiryNotification":true,"ignoreTls":false'
create_monitor "TLS — license.zyntapos.com" "http" "https://license.zyntapos.com/health" 86400 '"checkCertExpiry":true,"expiryNotification":true,"ignoreTls":false'
create_monitor "TLS — sync.zyntapos.com"    "http" "https://sync.zyntapos.com/health"    86400 '"checkCertExpiry":true,"expiryNotification":true,"ignoreTls":false'
create_monitor "TLS — panel.zyntapos.com"   "http" "https://panel.zyntapos.com"          86400 '"checkCertExpiry":true,"expiryNotification":true,"ignoreTls":false'

# ── Push monitors (heartbeat from backup scripts) ────────────────────────────
echo "[kuma-setup] Creating push monitors (for backup heartbeats)..."
create_monitor "Backup — Daily DB"   "push" "" 90000  # 25h timeout
create_monitor "Backup — Verify"     "push" "" 691200 # 8-day timeout

echo ""
echo "[kuma-setup] ── Setup complete ── $(date -u +%Y%m%dT%H%M%SZ)"
echo ""
echo "Next steps:"
echo "  1. Open https://status.zyntapos.com in your browser"
echo "  2. Verify all 19 monitors appear and are green"
echo "  3. Copy the push URLs for the 2 push monitors:"
echo "       Backup-Daily: KUMA_PUSH_URL=<copy from kuma UI>"
echo "       Backup-Verify: KUMA_PUSH_URL_VERIFY=<copy from kuma UI>"
echo "  4. Add those URLs to /opt/zyntapos/.env on the VPS"
echo "  5. Restart services: docker compose up -d"
