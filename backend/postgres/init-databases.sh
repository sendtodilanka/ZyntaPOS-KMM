#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# PostgreSQL init script — creates separate databases for each service.
# Runs ONCE when pgdata volume is first initialized.
#
# Databases:
#   • zyntapos_api     — REST API (Flyway-managed schema)
#   • zyntapos_license — License server (Flyway-managed schema)
#
# The default "zyntapos" database is created by POSTGRES_DB env var.
# This script creates the additional service-specific databases.
# ═══════════════════════════════════════════════════════════════════
set -e

echo "=== Creating service databases ==="

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE zyntapos_api OWNER $POSTGRES_USER;
    CREATE DATABASE zyntapos_license OWNER $POSTGRES_USER;
EOSQL

echo "=== Service databases created: zyntapos_api, zyntapos_license ==="
