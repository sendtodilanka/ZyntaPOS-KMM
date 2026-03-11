-- V8: Remote diagnostic sessions
-- Supports TODO-006 remote technician access with store-side consent

-- ── Diagnostic sessions ───────────────────────────────────────────────────────
CREATE TABLE diagnostic_sessions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    technician_id       UUID        NOT NULL REFERENCES admin_users(id),
    requested_by        UUID        NOT NULL REFERENCES admin_users(id),
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    data_scope          VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY_DIAGNOSTICS'
                            CHECK (data_scope IN ('READ_ONLY_DIAGNOSTICS', 'FULL_READ_ONLY')),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONSENT'
                            CHECK (status IN ('PENDING_CONSENT', 'ACTIVE', 'EXPIRED', 'REVOKED')),
    consent_granted_at  BIGINT,
    expires_at          BIGINT      NOT NULL,
    revoked_at          BIGINT,
    revoked_by          UUID        REFERENCES admin_users(id),
    created_at          BIGINT      NOT NULL
);

CREATE INDEX idx_ds_store_active ON diagnostic_sessions(store_id, status)
    WHERE status IN ('PENDING_CONSENT', 'ACTIVE');
CREATE INDEX idx_ds_expiry ON diagnostic_sessions(expires_at)
    WHERE status = 'PENDING_CONSENT';

-- ── Seed REMOTE_DIAGNOSTICS feature flag ──────────────────────────────────────
INSERT INTO feature_flags (key, name, description, enabled, category, editions_available)
VALUES ('REMOTE_DIAGNOSTICS', 'Remote Diagnostics', 'Remote technician diagnostic access sessions', false, 'support', '{"ENTERPRISE"}')
ON CONFLICT (key) DO NOTHING;
