-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V2: Admin Panel Auth Tables
-- Adds admin_users, admin_sessions, admin_mfa_backup_codes
-- These are internal Zynta Solutions staff accounts (not POS store users)
-- ═══════════════════════════════════════════════════════════════════

-- ── Admin Users ────────────────────────────────────────────────────
CREATE TABLE admin_users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT        NOT NULL UNIQUE,
    name            TEXT        NOT NULL,
    role            TEXT        NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK')),
    password_hash   TEXT,                          -- NULL if Google SSO only
    google_sub      TEXT        UNIQUE,            -- NULL if password only
    mfa_secret      TEXT,                          -- TOTP secret (AES-GCM encrypted at rest)
    mfa_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    failed_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,                   -- NULL = not locked
    last_login_at   TIMESTAMPTZ,
    last_login_ip   TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Admin Sessions (refresh token store — single-use rotation) ─────
CREATE TABLE admin_sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash      TEXT        NOT NULL UNIQUE,   -- SHA-256 of opaque refresh token
    user_agent      TEXT,
    ip_address      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ                    -- NULL = valid
);

CREATE INDEX idx_admin_sessions_user_id    ON admin_sessions(user_id);
CREATE INDEX idx_admin_sessions_token_hash ON admin_sessions(token_hash);

-- ── Admin MFA Backup Codes (one-time use) ──────────────────────────
CREATE TABLE admin_mfa_backup_codes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    code_hash   TEXT        NOT NULL,              -- bcrypt of backup code
    used_at     TIMESTAMPTZ                        -- NULL = unused
);

CREATE INDEX idx_admin_mfa_backup_codes_user ON admin_mfa_backup_codes(user_id);
