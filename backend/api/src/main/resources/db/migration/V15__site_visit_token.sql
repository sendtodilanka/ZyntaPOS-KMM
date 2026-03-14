-- ============================================================
-- V15__site_visit_token.sql
-- Adds site-visit token support to diagnostic_sessions.
--
-- A site visit token is an HMAC-SHA256 token scoped to specific
-- hardware components (e.g. "PRINTER,SCANNER,CASH_DRAWER") that a
-- Zynta technician must present physically at the customer site
-- before hardware-level diagnostic access is granted.
--
-- Only on-site sessions (visit_type = 'ON_SITE') carry a token;
-- standard remote sessions (visit_type = 'REMOTE', the default)
-- remain unchanged.
-- ============================================================

ALTER TABLE diagnostic_sessions
    ADD COLUMN visit_type TEXT NOT NULL DEFAULT 'REMOTE';
--   REMOTE | ON_SITE

ALTER TABLE diagnostic_sessions
    ADD COLUMN site_visit_token_hash TEXT;
--   SHA-256 hex hash of the raw site visit token (single-use, 15-min TTL).
--   NULL for REMOTE sessions. The raw token is returned only at creation.

ALTER TABLE diagnostic_sessions
    ADD COLUMN hardware_scope TEXT;
--   Comma-separated hardware component identifiers the technician may access.
--   Example: 'PRINTER,SCANNER,CASH_DRAWER'
--   NULL for REMOTE sessions.

ALTER TABLE diagnostic_sessions
    ADD COLUMN site_visit_presented_at BIGINT;
--   Epoch-ms when the technician presented the physical site visit token at
--   the device. NULL until the token is validated on-site.

CREATE INDEX IF NOT EXISTS idx_diag_sessions_visit_type
    ON diagnostic_sessions (visit_type, status);

CREATE INDEX idx_diag_sessions_site_visit_token
    ON diagnostic_sessions(site_visit_token_hash)
    WHERE site_visit_token_hash IS NOT NULL;
