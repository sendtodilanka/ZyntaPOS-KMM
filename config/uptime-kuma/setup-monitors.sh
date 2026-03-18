#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# Uptime Kuma Monitor Setup Script (B3)
#
# Seeds monitors for all ZyntaPOS subdomains and Docker services.
# Run once after Uptime Kuma first boot, or re-run to update.
#
# Prerequisites:
#   1. Uptime Kuma running at UPTIME_KUMA_URL (default: http://localhost:3001)
#   2. Admin account created via Uptime Kuma web UI
#   3. Set env vars: UPTIME_KUMA_USER, UPTIME_KUMA_PASSWORD
#
# Usage:
#   UPTIME_KUMA_USER=admin UPTIME_KUMA_PASSWORD=secret ./setup-monitors.sh
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

KUMA_URL="${UPTIME_KUMA_URL:-http://localhost:3001}"
KUMA_USER="${UPTIME_KUMA_USER:?Set UPTIME_KUMA_USER}"
KUMA_PASS="${UPTIME_KUMA_PASSWORD:?Set UPTIME_KUMA_PASSWORD}"

echo "🔧 Uptime Kuma Monitor Setup"
echo "   URL: $KUMA_URL"

# ── Login and get token ──────────────────────────────────────────────
TOKEN=$(curl -sf "$KUMA_URL/api/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$KUMA_USER\",\"password\":\"$KUMA_PASS\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

AUTH="Authorization: Bearer $TOKEN"

add_monitor() {
  local name="$1" type="$2" url="$3" interval="${4:-60}"
  echo "  + $name ($type → $url)"
  curl -sf "$KUMA_URL/api/monitors" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$name\",
      \"type\": \"$type\",
      \"url\": \"$url\",
      \"interval\": $interval,
      \"retryInterval\": 30,
      \"maxretries\": 3,
      \"active\": true
    }" > /dev/null 2>&1 || echo "    (already exists or error)"
}

add_docker_monitor() {
  local name="$1" container="$2" interval="${3:-60}"
  echo "  + $name (docker → $container)"
  curl -sf "$KUMA_URL/api/monitors" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d "{
      \"name\": \"$name\",
      \"type\": \"docker\",
      \"docker_container\": \"$container\",
      \"docker_host\": \"\",
      \"interval\": $interval,
      \"retryInterval\": 30,
      \"maxretries\": 3,
      \"active\": true
    }" > /dev/null 2>&1 || echo "    (already exists or error)"
}

echo ""
echo "── HTTP(S) Subdomain Monitors ──────────────────────────────────"
add_monitor "API (api.zyntapos.com)"       "http" "https://api.zyntapos.com/health"       30
add_monitor "License (license.zyntapos.com)" "http" "https://license.zyntapos.com/health"  60
add_monitor "Sync (sync.zyntapos.com)"     "http" "https://sync.zyntapos.com/health"      30
add_monitor "Admin Panel (panel.zyntapos.com)" "http" "https://panel.zyntapos.com"         60
add_monitor "Docs (docs.zyntapos.com)"     "http" "https://docs.zyntapos.com"             120
add_monitor "Status (status.zyntapos.com)" "http" "https://status.zyntapos.com"           120
add_monitor "Website (www.zyntapos.com)"   "http" "https://www.zyntapos.com"              120

echo ""
echo "── Docker Container Health Monitors ────────────────────────────"
add_docker_monitor "Docker: API"        "zyntapos_api"        30
add_docker_monitor "Docker: License"    "zyntapos_license"    60
add_docker_monitor "Docker: Sync"       "zyntapos_sync"       30
add_docker_monitor "Docker: PostgreSQL" "zyntapos_postgres"   30
add_docker_monitor "Docker: Redis"      "zyntapos_redis"      30
add_docker_monitor "Docker: Caddy"      "zyntapos_caddy"      60

echo ""
echo "── Database Connection Monitors ────────────────────────────────"
# These use the API health endpoint which includes DB connectivity checks
add_monitor "DB: API (via /health)"     "http" "https://api.zyntapos.com/health"     60
add_monitor "DB: License (via /health)" "http" "https://license.zyntapos.com/health" 60

echo ""
echo "── Notification Channels ───────────────────────────────────────"
echo "  Configure in Uptime Kuma UI:"
echo "  1. Slack: paste SLACK_WEBHOOK_URL as Slack webhook notification"
echo "  2. Email: configure SMTP settings for admin email alerts"
echo ""
echo "── Status Page Branding ────────────────────────────────────────"
echo "  Configure in Uptime Kuma UI → Status Pages:"
echo "  - Title: ZyntaPOS Service Status"
echo "  - Logo: https://www.zyntapos.com/logo.svg"
echo "  - Description: Real-time service status for ZyntaPOS infrastructure"
echo "  - Custom CSS: Set brand colors (#1976d2 primary)"
echo ""
echo "✅ Monitor setup complete. Check $KUMA_URL for status."
