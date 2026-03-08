-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V2: Admin Panel Auth Tables
-- Adds admin_users, admin_sessions, admin_mfa_backup_codes
-- Timestamps stored as BIGINT epoch-ms (consistent with Exposed long mapping)
-- ═══════════════════════════════════════════════════════════════════

-- ── Admin Users ────────────────────────────────────────────────────
CREATE TABLE admin_users (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT    NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    role            TEXT    NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK')),
    password_hash   TEXT,                          -- NULL if Google SSO only
    google_sub      TEXT    UNIQUE,                -- NULL if password only
    mfa_secret      TEXT,                          -- TOTP secret (AES-GCM encrypted at rest)
    mfa_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    BIGINT,                        -- epoch-ms; NULL = not locked
    last_login_at   BIGINT,                        -- epoch-ms
    last_login_ip   TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      BIGINT  NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000
);

-- ── Admin Sessions (refresh token store — single-use rotation) ─────
CREATE TABLE admin_sessions (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID    NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash  TEXT    NOT NULL UNIQUE,           -- SHA-256 of opaque refresh token
    user_agent  TEXT,
    ip_address  TEXT,
    created_at  BIGINT  NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000,
    expires_at  BIGINT  NOT NULL,                  -- epoch-ms
    revoked_at  BIGINT                             -- epoch-ms; NULL = valid
);

CREATE INDEX idx_admin_sessions_user_id    ON admin_sessions(user_id);
CREATE INDEX idx_admin_sessions_token_hash ON admin_sessions(token_hash);

-- ── Admin MFA Backup Codes (one-time use) ──────────────────────────
CREATE TABLE admin_mfa_backup_codes (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID    NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    code_hash   TEXT    NOT NULL,                  -- bcrypt of backup code
    used_at     BIGINT                             -- epoch-ms; NULL = unused
);

CREATE INDEX idx_admin_mfa_backup_codes_user ON admin_mfa_backup_codes(user_id);
