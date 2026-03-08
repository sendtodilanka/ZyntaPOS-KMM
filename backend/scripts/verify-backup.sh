#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# ZyntaPOS — Backup integrity verification script (TODO-007d)
# Downloads the latest backup from B2 (or uses local), restores it to a
# temporary test database, compares row counts, then tears down.
# Intended to run weekly (cron) to confirm backups are restorable.
#
# Required env vars:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD
#
# Optional env vars:
#   GPG_PASSPHRASE       — required if backups are GPG-encrypted
#   RCLONE_B2_REMOTE     — rclone remote for B2 (default: local scan only)
#   KUMA_PUSH_URL_VERIFY — Uptime Kuma push URL for the verify monitor
#   BACKUP_DIR           — local backup directory (default: /var/backups/zyntapos)
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-zyntapos}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/zyntapos}"
TEST_DB_PREFIX="zyntapos_verify"
TMP_DIR="$(mktemp -d)"
FAILED=0

trap 'cleanup' EXIT
cleanup() {
    echo "[verify] Cleaning up temporary resources..."
    rm -rf "$TMP_DIR"
    for db in "${DATABASES[@]}"; do
        TEST_DB="${TEST_DB_PREFIX}_${db}"
        psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
            -c "DROP DATABASE IF EXISTS \"$TEST_DB\";" 2>/dev/null || true
    done
}

DATABASES=("zyntapos_api" "zyntapos_license")

echo "[verify] ── ZyntaPOS backup verification started ── $(date -u +%Y%m%dT%H%M%SZ)"

for db in "${DATABASES[@]}"; do
    echo "[verify] ── Verifying $db ──"
    TEST_DB="${TEST_DB_PREFIX}_${db}"

    # ── 1. Find latest local backup ──────────────────────────────────────────
    LATEST_LOCAL="$(find "$BACKUP_DIR" -name "${db}_*.sql.gz*" | sort | tail -1 || true)"

    # ── 2. Optionally download latest from B2 ────────────────────────────────
    if [[ -n "${RCLONE_B2_REMOTE:-}" ]]; then
        echo "[verify] Checking B2 for latest backup..."
        LATEST_B2="$(rclone lsf "${RCLONE_B2_REMOTE}/" \
            --include "${db}_*.sql.gz*" | sort | tail -1 || true)"

        if [[ -n "$LATEST_B2" ]]; then
            echo "[verify] Downloading $LATEST_B2 from B2..."
            rclone copy "${RCLONE_B2_REMOTE}/${LATEST_B2}" "$TMP_DIR/" --progress
            BACKUP_FILE="$TMP_DIR/$LATEST_B2"
        else
            echo "[verify] No B2 backup found, using local: $LATEST_LOCAL"
            BACKUP_FILE="$LATEST_LOCAL"
        fi
    else
        echo "[verify] RCLONE_B2_REMOTE not set — using local: $LATEST_LOCAL"
        BACKUP_FILE="$LATEST_LOCAL"
    fi

    if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
        echo "[verify] ERROR: No backup found for $db" >&2
        FAILED=1
        continue
    fi

    echo "[verify] Using backup: $(basename "$BACKUP_FILE")"

    # ── 3. Decrypt if needed ─────────────────────────────────────────────────
    WORK_FILE="$BACKUP_FILE"
    if [[ "$BACKUP_FILE" == *.gpg ]]; then
        DECRYPTED="$TMP_DIR/$(basename "${BACKUP_FILE%.gpg}")"
        echo "[verify] Decrypting..."
        gpg --batch --yes --passphrase "${GPG_PASSPHRASE:-}" \
            --decrypt --output "$DECRYPTED" "$BACKUP_FILE"
        WORK_FILE="$DECRYPTED"
    fi

    SQL_FILE="$WORK_FILE"
    if [[ "$WORK_FILE" == *.gz ]]; then
        SQL_FILE="${WORK_FILE%.gz}"
        gunzip -c "$WORK_FILE" > "$SQL_FILE"
    fi

    # ── 4. Create temp test DB + restore ─────────────────────────────────────
    echo "[verify] Creating test database: $TEST_DB"
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
        -c "DROP DATABASE IF EXISTS \"$TEST_DB\";"
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
        -c "CREATE DATABASE \"$TEST_DB\";"

    echo "[verify] Restoring to test DB..."
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TEST_DB" \
        -f "$SQL_FILE" -v ON_ERROR_STOP=1 -q

    # ── 5. Compare row counts with production DB ──────────────────────────────
    echo "[verify] Comparing row counts..."
    TABLES=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TEST_DB" -t \
        -c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;")

    VERIFY_OK=true
    while IFS= read -r table; do
        table="$(echo "$table" | xargs)"
        [[ -z "$table" ]] && continue

        PROD_COUNT=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" -t \
            -c "SELECT COUNT(*) FROM \"$table\";" | xargs)
        TEST_COUNT=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TEST_DB" -t \
            -c "SELECT COUNT(*) FROM \"$table\";" | xargs)

        if [[ "$PROD_COUNT" == "$TEST_COUNT" ]]; then
            echo "[verify]   ✓ $table: $PROD_COUNT rows"
        else
            echo "[verify]   ✗ $table: prod=$PROD_COUNT restore=$TEST_COUNT — MISMATCH"
            VERIFY_OK=false
            FAILED=1
        fi
    done <<< "$TABLES"

    if $VERIFY_OK; then
        echo "[verify] $db ✓ — backup is restorable and data matches"
    else
        echo "[verify] $db ✗ — row count mismatches detected"
    fi
done

# ── 6. Notify Uptime Kuma ─────────────────────────────────────────────────────
if [[ -n "${KUMA_PUSH_URL_VERIFY:-}" ]]; then
    if [[ $FAILED -eq 0 ]]; then
        STATUS="up"; MSG="verify_ok"
    else
        STATUS="down"; MSG="verify_failed"
    fi
    curl -fsS "${KUMA_PUSH_URL_VERIFY}?status=${STATUS}&msg=${MSG}&ping=" > /dev/null || true
fi

echo "[verify] ── Verification complete ── $(date -u +%Y%m%dT%H%M%SZ)"
exit $FAILED
