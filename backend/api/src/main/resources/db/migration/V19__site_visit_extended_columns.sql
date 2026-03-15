-- ============================================================
-- V19__site_visit_extended_columns.sql
-- Extends diagnostic_sessions with hardware scope and presentation
-- tracking for on-site visit tokens.
--
-- Split from V15 to avoid Flyway checksum mismatch — V15 was
-- already applied in production with only visit_type and
-- site_visit_token_hash columns.
-- ============================================================

-- Comma-separated hardware component identifiers the technician may access.
-- Example: 'PRINTER,SCANNER,CASH_DRAWER'
-- NULL for REMOTE sessions.
ALTER TABLE diagnostic_sessions
    ADD COLUMN IF NOT EXISTS hardware_scope TEXT;

-- Epoch-ms when the technician presented the physical site visit token at
-- the device. NULL until the token is validated on-site.
ALTER TABLE diagnostic_sessions
    ADD COLUMN IF NOT EXISTS site_visit_presented_at BIGINT;

-- Composite index for filtering sessions by visit type and status.
CREATE INDEX IF NOT EXISTS idx_diag_sessions_visit_type
    ON diagnostic_sessions (visit_type, status);
