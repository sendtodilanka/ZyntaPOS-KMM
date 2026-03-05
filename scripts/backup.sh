#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# ZyntaPOS — Automated Database Backup
#
# Runs daily at 03:00 LKT (21:00 UTC) via cron:
#   0 21 * * * deploy /opt/zyntapos/scripts/backup.sh >> /var/log/zyntapos-backup.log 2>&1
#
# Requirements on VPS:
#   apt install postgresql-client rclone
#   rclone configure → add 'b2' remote with Backblaze B2 credentials
#   B2 bucket: zyntapos-backups
#
# Retention: 30 days of daily backups on Backblaze B2
# Cost: ~$0/month for first 10 GB (typical POS DB < 1 GB/month)
# ═══════════════════════════════════════════════════════════════════

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────
DEPLOY_PATH="${DEPLOY_PATH:-/opt/zyntapos}"
B2_REMOTE="${B2_REMOTE:-b2:zyntapos-backups/db}"
RETENTION_HOURS="${RETENTION_HOURS:-720}"  # 30 days
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="/tmp/zyntapos_${TIMESTAMP}.sql.gz"
LOG_PREFIX="[$(date '+%Y-%m-%d %H:%M:%S')] BACKUP"

echo "${LOG_PREFIX} Starting backup: ${TIMESTAMP}"

# ── Read PostgreSQL password from Docker secret ────────────────────
DB_PASSWORD_FILE="${DEPLOY_PATH}/secrets/db_password.txt"
if [ ! -f "$DB_PASSWORD_FILE" ]; then
    echo "${LOG_PREFIX} ERROR: DB password file not found: ${DB_PASSWORD_FILE}" >&2
    exit 1
fi
export PGPASSWORD
PGPASSWORD=$(cat "$DB_PASSWORD_FILE")

# ── Dump database ─────────────────────────────────────────────────
echo "${LOG_PREFIX} Dumping database..."
pg_dump \
    --host=127.0.0.1 \
    --port=5432 \
    --username=zyntapos \
    --dbname=zyntapos \
    --no-password \
    --format=custom \
    --compress=9 \
    --file="$BACKUP_FILE"

BACKUP_SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
echo "${LOG_PREFIX} Dump complete: ${BACKUP_FILE} (${BACKUP_SIZE})"

# ── Upload to Backblaze B2 ─────────────────────────────────────────
echo "${LOG_PREFIX} Uploading to B2..."
rclone copy "$BACKUP_FILE" "$B2_REMOTE/" \
    --b2-chunk-size=5M \
    --progress=false

echo "${LOG_PREFIX} Upload complete: ${B2_REMOTE}/$(basename "$BACKUP_FILE")"

# ── Clean local temp file ─────────────────────────────────────────
rm -f "$BACKUP_FILE"
echo "${LOG_PREFIX} Local temp file removed"

# ── Prune old backups from B2 ─────────────────────────────────────
echo "${LOG_PREFIX} Pruning backups older than ${RETENTION_HOURS}h from B2..."
rclone delete "$B2_REMOTE" --min-age "${RETENTION_HOURS}h"
echo "${LOG_PREFIX} Pruning complete"

# ── Verify recent backup exists ───────────────────────────────────
BACKUP_COUNT=$(rclone ls "$B2_REMOTE" | wc -l)
echo "${LOG_PREFIX} Backup complete. Total backups in B2: ${BACKUP_COUNT}"

# Unset password from environment
unset PGPASSWORD
