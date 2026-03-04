-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V2: Licenses table
-- Note: licenses table is also used by the license server.
-- Both services share the same PostgreSQL database.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS licenses (
    key                 TEXT        PRIMARY KEY,
    customer_id         TEXT        NOT NULL,
    edition             TEXT        NOT NULL,   -- STARTER, PROFESSIONAL, ENTERPRISE
    max_devices         INTEGER     NOT NULL DEFAULT 1,
    status              TEXT        NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, SUSPENDED, REVOKED
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    last_heartbeat_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE INDEX IF NOT EXISTS idx_device_reg_license ON device_registrations(license_key);
