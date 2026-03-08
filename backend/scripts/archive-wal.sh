#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — PostgreSQL WAL archiving script (TODO-007d)
# Uploads completed WAL segment files to Backblaze B2 for point-in-time recovery.
#
# WAL archiving requires PostgreSQL to be configured with:
#   archive_mode = on
#   archive_command = '/opt/zyntapos/scripts/archive-wal.sh %p %f'
#
# This script is called by PostgreSQL with two arguments:
#   %p = absolute path to the WAL file
#   %f = WAL filename (no path)
#
# Required env vars:
#   RCLONE_B2_REMOTE     — rclone remote for B2, e.g. "b2:zyntapos-backups"
#
# Optional env vars:
#   WAL_ARCHIVE_PREFIX   — B2 path prefix (default: wal/)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

WAL_PATH="${1:-}"
WAL_FILE="${2:-}"

if [[ -z "$WAL_PATH" || -z "$WAL_FILE" ]]; then
    echo "[wal-archive] Usage: $0 <wal_path> <wal_filename>" >&2
    exit 1
fi

if [[ ! -f "$WAL_PATH" ]]; then
    echo "[wal-archive] ERROR: WAL file not found: $WAL_PATH" >&2
    exit 1
fi

if [[ -z "${RCLONE_B2_REMOTE:-}" ]]; then
    echo "[wal-archive] ERROR: RCLONE_B2_REMOTE must be set" >&2
    exit 1
fi

WAL_ARCHIVE_PREFIX="${WAL_ARCHIVE_PREFIX:-wal/}"
DEST="${RCLONE_B2_REMOTE}/${WAL_ARCHIVE_PREFIX}${WAL_FILE}"

echo "[wal-archive] Uploading $WAL_FILE to B2..."
rclone copyto "$WAL_PATH" "$DEST" \
    --retries 5 \
    --low-level-retries 10 \
    --retries-sleep 5s

echo "[wal-archive] Done: $WAL_FILE"
