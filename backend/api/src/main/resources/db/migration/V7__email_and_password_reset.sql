-- V7: Email preferences and password reset tokens
-- Supports TODO-008a transactional email system

-- ── Email preferences per admin user ─────────────────────────────────────────
CREATE TABLE email_preferences (
    user_id              UUID        PRIMARY KEY REFERENCES admin_users(id) ON DELETE CASCADE,
    marketing_emails     BOOLEAN     NOT NULL DEFAULT true,
    ticket_notifications BOOLEAN     NOT NULL DEFAULT true,
    unsubscribe_token    VARCHAR(64) UNIQUE NOT NULL,
    unsubscribed_at      BIGINT
);

-- ── Password reset tokens for admin users ─────────────────────────────────────
-- token_hash stores SHA-256 hex of the one-time reset token sent by email.
-- Tokens expire after 1 hour (enforced at application layer via expires_at epoch-ms).
CREATE TABLE password_reset_tokens (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id  UUID        NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    expires_at     BIGINT      NOT NULL,
    used_at        BIGINT,
    created_at     BIGINT      NOT NULL
);

CREATE INDEX idx_prt_token  ON password_reset_tokens(token_hash);
CREATE INDEX idx_prt_user   ON password_reset_tokens(admin_user_id);
CREATE INDEX idx_prt_expiry ON password_reset_tokens(expires_at) WHERE used_at IS NULL;
