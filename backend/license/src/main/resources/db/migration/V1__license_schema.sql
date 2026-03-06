-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS License Server — PostgreSQL Schema
-- V1: Licenses and device registrations
-- Note: License server uses its own database (zyntapos_license).
-- Fully isolated from the API database (zyntapos_api).
-- ═══════════════════════════════════════════════════════════════════

-- Licenses table (master registry)
CREATE TABLE IF NOT EXISTS licenses (
    key                 TEXT        PRIMARY KEY,
    customer_id         TEXT        NOT NULL,
    edition             TEXT        NOT NULL CHECK (edition IN ('STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    max_devices         INTEGER     NOT NULL DEFAULT 1 CHECK (max_devices >= 1),
    status              TEXT        NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'EXPIRED', 'SUSPENDED', 'REVOKED')),
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,                        -- NULL = perpetual
    last_heartbeat_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Device registrations (one row per device per license)
CREATE TABLE IF NOT EXISTS device_registrations (
    id              TEXT        PRIMARY KEY,
    license_key     TEXT        NOT NULL REFERENCES licenses(key) ON DELETE CASCADE,
    device_id       TEXT        NOT NULL,
    device_name     TEXT,
    app_version     TEXT,
    os_version      TEXT,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (license_key, device_id)
);

CREATE INDEX IF NOT EXISTS idx_device_reg_license    ON device_registrations(license_key);
CREATE INDEX IF NOT EXISTS idx_device_reg_last_seen  ON device_registrations(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_licenses_status       ON licenses(status);
CREATE INDEX IF NOT EXISTS idx_licenses_expires_at   ON licenses(expires_at) WHERE expires_at IS NOT NULL;
