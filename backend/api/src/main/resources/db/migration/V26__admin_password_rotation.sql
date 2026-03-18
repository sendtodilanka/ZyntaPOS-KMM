-- V26: Admin password rotation policy (B2)
-- Tracks when admin passwords were last changed (epoch-ms, matching created_at column type).
-- ADMIN_PASSWORD_MAX_AGE_DAYS env var configures forced rotation (0 = disabled).

ALTER TABLE admin_users ADD COLUMN password_changed_at BIGINT;

-- Backfill: set to created_at for existing users so they aren't immediately locked out
UPDATE admin_users SET password_changed_at = created_at WHERE password_changed_at IS NULL;
