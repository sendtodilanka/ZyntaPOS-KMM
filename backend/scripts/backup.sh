#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — PostgreSQL backup script (TODO-007d)
# Dumps both databases, optionally GPG-encrypts, optionally uploads to
# Backblaze B2 via rclone, then notifies Uptime Kuma push monitor.
#
# Required env vars:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD
#
# Optional env vars (if not set, the step is skipped):
#   GPG_PASSPHRASE       — AES-256 symmetric encryption for dump files
#   RCLONE_B2_REMOTE     — rclone remote name pointing to B2, e.g. "b2:zyntapos-backups"
#   KUMA_PUSH_URL        — Uptime Kuma push URL for backup heartbeat monitor
#
# Usage:
#   ./backup.sh [backup_dir]          # local backup only
#   RCLONE_B2_REMOTE=b2:... ./backup.sh   # backup + upload to B2
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BACKUP_DIR="${1:-/var/backups/zyntapos}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RETENTION_DAYS=30
DATABASES=("zyntapos_api" "zyntapos_license")

PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-zyntapos}"

mkdir -p "$BACKUP_DIR"
LOG_FILE="$BACKUP_DIR/backup_${TIMESTAMP}.log"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "[backup] ── ZyntaPOS backup started ── $TIMESTAMP"

# ── 1. Dump each database ────────────────────────────────────────────────────
for db in "${DATABASES[@]}"; do
    DUMP_FILE="$BACKUP_DIR/${db}_${TIMESTAMP}.sql"
    COMPRESSED="$DUMP_FILE.gz"

    echo "[backup] Dumping $db..."
    pg_dump \
        -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" \
        --no-owner --no-privileges --format=plain \
        > "$DUMP_FILE"

    gzip "$DUMP_FILE"
    echo "[backup] Compressed: $(du -h "$COMPRESSED" | cut -f1)"

    # ── 2. GPG encrypt (optional) ────────────────────────────────────────────
    if [[ -n "${GPG_PASSPHRASE:-}" ]]; then
        ENCRYPTED="$COMPRESSED.gpg"
        echo "[backup] Encrypting $db dump..."
        gpg --batch --yes --passphrase "$GPG_PASSPHRASE" \
            --symmetric --cipher-algo AES256 \
            --output "$ENCRYPTED" "$COMPRESSED"
        rm -f "$COMPRESSED"
        FINAL_FILE="$ENCRYPTED"
        echo "[backup] Encrypted: $(du -h "$FINAL_FILE" | cut -f1)"
    else
        FINAL_FILE="$COMPRESSED"
        echo "[backup] GPG_PASSPHRASE not set — skipping encryption"
    fi

    # ── 3. Upload to Backblaze B2 (optional) ─────────────────────────────────
    if [[ -n "${RCLONE_B2_REMOTE:-}" ]]; then
        echo "[backup] Uploading $db to B2..."
        rclone copy "$FINAL_FILE" "${RCLONE_B2_REMOTE}/" \
            --progress \
            --retries 3 \
            --low-level-retries 5
        echo "[backup] B2 upload complete"
    else
        echo "[backup] RCLONE_B2_REMOTE not set — skipping B2 upload (local backup only)"
    fi
done

# ── 4. Prune old local backups ────────────────────────────────────────────────
echo "[backup] Pruning local backups older than ${RETENTION_DAYS} days..."
find "$BACKUP_DIR" -name "zyntapos_*.sql.gz*" -mtime "+${RETENTION_DAYS}" -delete
find "$BACKUP_DIR" -name "backup_*.log" -mtime "+${RETENTION_DAYS}" -delete

# ── 5. Notify Uptime Kuma (optional) ─────────────────────────────────────────
if [[ -n "${KUMA_PUSH_URL:-}" ]]; then
    echo "[backup] Notifying Uptime Kuma..."
    curl -fsS "${KUMA_PUSH_URL}?status=up&msg=backup_ok&ping=" > /dev/null || \
        echo "[backup] WARNING: Kuma ping failed (non-fatal)"
fi

echo "[backup] ── Backup complete ── $(date -u +%Y%m%dT%H%M%SZ)"
