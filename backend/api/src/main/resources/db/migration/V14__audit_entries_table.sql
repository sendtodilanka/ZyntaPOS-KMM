-- V14: Server-side audit_entries table for S4-11 (audit trail sync from POS devices).
-- Mirrors the KMM client audit_entries schema so synced audit entries are stored
-- centrally for compliance, GDPR, and tamper-detection verification.

CREATE TABLE IF NOT EXISTS audit_entries (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL,
    device_id       TEXT        NOT NULL DEFAULT '',
    event_type      TEXT        NOT NULL,
    user_id         TEXT        NOT NULL,
    user_name       TEXT        DEFAULT '',
    user_role       TEXT        DEFAULT '',
    entity_type     TEXT,
    entity_id       TEXT,
    details         JSONB       NOT NULL DEFAULT '{}',
    previous_value  TEXT,
    new_value       TEXT,
    success         BOOLEAN     NOT NULL DEFAULT TRUE,
    ip_address      TEXT,
    hash            TEXT        NOT NULL DEFAULT '',
    previous_hash   TEXT        NOT NULL DEFAULT '',
    timestamp       BIGINT      NOT NULL,
    sync_version    BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_audit_entries_store ON audit_entries(store_id);
CREATE INDEX IF NOT EXISTS idx_audit_entries_event_type ON audit_entries(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_entries_user ON audit_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entries_entity ON audit_entries(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_entries_timestamp ON audit_entries(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_entries_store_timestamp ON audit_entries(store_id, timestamp DESC);
