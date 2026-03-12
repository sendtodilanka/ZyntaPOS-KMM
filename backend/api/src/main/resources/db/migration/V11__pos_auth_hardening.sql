-- V11: POS auth hardening (Audit Phase A)
-- A1: Brute-force protection — failed_attempts + locked_until on users table
-- A3: POS refresh token sessions — mirrors admin_sessions pattern for single-use rotation

-- ── A1: Brute-force protection columns ──────────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until    TIMESTAMPTZ;

-- ── A3: POS refresh token sessions ──────────────────────────────────────────
-- Mirrors the admin_sessions pattern: opaque refresh tokens stored as hashes,
-- single-use rotation on refresh, revocation on logout/password-change.
CREATE TABLE IF NOT EXISTS pos_sessions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id     TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL,
    device_id    TEXT,
    user_agent   TEXT,
    ip_address   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pos_sessions_token_hash ON pos_sessions (token_hash);
CREATE INDEX IF NOT EXISTS idx_pos_sessions_user_id    ON pos_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_pos_sessions_expires_at ON pos_sessions (expires_at);
