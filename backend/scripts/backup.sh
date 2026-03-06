#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — PostgreSQL backup script
# Dumps zyntapos_api and zyntapos_license databases to timestamped .sql.gz files.
# Intended for cron or manual execution on the VPS.
#
# Usage:  ./backup.sh [backup_dir]
#   backup_dir defaults to /var/backups/zyntapos
#
# Environment:
#   PGHOST     (default: localhost)
#   PGPORT     (default: 5432)
#   PGUSER     (default: zyntapos)
#   PGPASSWORD (must be set or use .pgpass)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BACKUP_DIR="${1:-/var/backups/zyntapos}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RETENTION_DAYS=30

DATABASES=("zyntapos_api" "zyntapos_license")

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-zyntapos}"

mkdir -p "$BACKUP_DIR"

echo "[backup] Starting PostgreSQL backup — $TIMESTAMP"

for db in "${DATABASES[@]}"; do
    outfile="$BACKUP_DIR/${db}_${TIMESTAMP}.sql.gz"
    echo "[backup] Dumping $db → $outfile"
    pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" \
        --no-owner --no-privileges --format=plain \
        | gzip > "$outfile"
    echo "[backup] Done: $(du -h "$outfile" | cut -f1)"
done

# Prune backups older than RETENTION_DAYS
echo "[backup] Pruning backups older than ${RETENTION_DAYS} days"
find "$BACKUP_DIR" -name "zyntapos_*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete

echo "[backup] Complete."
