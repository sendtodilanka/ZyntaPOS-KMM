#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — Database restore script (TODO-007d)
# Restores a database from a local or B2-stored backup file.
# Supports both plain .sql.gz and GPG-encrypted .sql.gz.gpg files.
#
# Usage:
#   ./restore.sh <database> <backup_file>
#     <database>     — zyntapos_api | zyntapos_license
#     <backup_file>  — path to .sql.gz or .sql.gz.gpg file
#                      prefix with "b2:" to download from B2 first, e.g.
#                      b2:zyntapos_api_20260301T030000Z.sql.gz.gpg
#
# Required env vars:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD
#
# Optional env vars:
#   GPG_PASSPHRASE       — required if backup file is .gpg encrypted
#   RCLONE_B2_REMOTE     — required if using "b2:" prefix for backup_file
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <database> <backup_file>" >&2
    echo "  database:    zyntapos_api | zyntapos_license" >&2
    echo "  backup_file: /path/to/file.sql.gz[.gpg] or b2:filename" >&2
    exit 1
fi

DB="$1"
BACKUP_ARG="$2"
PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-zyntapos}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "[restore] ── ZyntaPOS restore started ── $(date -u +%Y%m%dT%H%M%SZ)"
echo "[restore] Target database: $DB"

# ── 1. Resolve backup file (download from B2 if needed) ──────────────────────
if [[ "$BACKUP_ARG" == b2:* ]]; then
    FILENAME="${BACKUP_ARG#b2:}"
    LOCAL_FILE="$TMP_DIR/$FILENAME"
    echo "[restore] Downloading from B2: $FILENAME"
    if [[ -z "${RCLONE_B2_REMOTE:-}" ]]; then
        echo "[restore] ERROR: RCLONE_B2_REMOTE must be set for B2 downloads" >&2
        exit 1
    fi
    rclone copy "${RCLONE_B2_REMOTE}/${FILENAME}" "$TMP_DIR/" --progress
    echo "[restore] Downloaded to $LOCAL_FILE"
else
    LOCAL_FILE="$BACKUP_ARG"
    echo "[restore] Using local file: $LOCAL_FILE"
fi

if [[ ! -f "$LOCAL_FILE" ]]; then
    echo "[restore] ERROR: file not found: $LOCAL_FILE" >&2
    exit 1
fi

# ── 2. Decrypt if GPG-encrypted ───────────────────────────────────────────────
WORK_FILE="$LOCAL_FILE"
if [[ "$LOCAL_FILE" == *.gpg ]]; then
    if [[ -z "${GPG_PASSPHRASE:-}" ]]; then
        echo "[restore] ERROR: GPG_PASSPHRASE must be set to decrypt this file" >&2
        exit 1
    fi
    DECRYPTED="$TMP_DIR/$(basename "${LOCAL_FILE%.gpg}")"
    echo "[restore] Decrypting..."
    gpg --batch --yes --passphrase "$GPG_PASSPHRASE" \
        --decrypt --output "$DECRYPTED" "$LOCAL_FILE"
    WORK_FILE="$DECRYPTED"
    echo "[restore] Decrypted to $WORK_FILE"
fi

# ── 3. Decompress if gzip ─────────────────────────────────────────────────────
SQL_FILE="$WORK_FILE"
if [[ "$WORK_FILE" == *.gz ]]; then
    SQL_FILE="${WORK_FILE%.gz}"
    echo "[restore] Decompressing..."
    gunzip -c "$WORK_FILE" > "$SQL_FILE"
fi

# ── 4. Drop + recreate schema, then restore ───────────────────────────────────
echo "[restore] WARNING: This will DROP and recreate schema in '$DB'. Ctrl-C within 5s to abort."
sleep 5

echo "[restore] Dropping public schema in $DB..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB" \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

echo "[restore] Restoring from SQL..."
psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB" \
    -f "$SQL_FILE" -v ON_ERROR_STOP=1

echo "[restore] ── Restore complete ── $(date -u +%Y%m%dT%H%M%SZ)"
