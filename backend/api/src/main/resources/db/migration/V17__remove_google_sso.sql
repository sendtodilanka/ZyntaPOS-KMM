-- V17: Remove Google SSO column — Google OAuth authentication removed from admin panel.
-- Admin authentication is email/password + TOTP MFA only.

ALTER TABLE admin_users DROP COLUMN IF EXISTS google_sub;
