-- V10: JWT token revocation support (B2)
-- Enables the server to invalidate POS access tokens before their natural expiry.
-- A background cleanup job (or pg_cron) should periodically DELETE rows where
-- revoked_at < now() - interval '24 hours' to bound table growth.

CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti        TEXT PRIMARY KEY,                           -- JWT ID claim
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason     TEXT                                        -- optional: "logout", "device_revoked", etc.
);

-- Index to support fast lookups during JWT validation
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_revoked_at ON revoked_tokens (revoked_at);
