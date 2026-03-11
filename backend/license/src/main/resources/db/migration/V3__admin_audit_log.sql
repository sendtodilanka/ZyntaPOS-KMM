-- V3: Admin audit log for license management actions
CREATE TABLE admin_audit_log (
    id           TEXT        NOT NULL PRIMARY KEY,
    admin_id     TEXT        NOT NULL,
    action       TEXT        NOT NULL,
    license_key  TEXT,
    details      TEXT,
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_license ON admin_audit_log(license_key);
CREATE INDEX IF NOT EXISTS idx_audit_log_admin   ON admin_audit_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_time    ON admin_audit_log(performed_at DESC);
