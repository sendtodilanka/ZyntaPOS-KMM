-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V3: Admin Panel Config, Alerts & Audit Tables
-- ═══════════════════════════════════════════════════════════════════

-- ── Feature Flags ───────────────────────────────────────────────────
CREATE TABLE feature_flags (
    key                 TEXT        PRIMARY KEY,
    name                TEXT        NOT NULL,
    description         TEXT        NOT NULL DEFAULT '',
    enabled             BOOLEAN     NOT NULL DEFAULT FALSE,
    category            TEXT        NOT NULL DEFAULT 'general',
    editions_available  TEXT[]      NOT NULL DEFAULT '{}',
    modified_by         TEXT        NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO feature_flags (key, name, description, enabled, category, editions_available) VALUES
    ('multi_store',       'Multi-Store',        'Enable multi-store dashboard',      FALSE, 'growth',     '{"ENTERPRISE"}'),
    ('remote_diagnostic', 'Remote Diagnostics', 'Allow remote diagnostic sessions',  FALSE, 'support',    '{"PROFESSIONAL","ENTERPRISE"}'),
    ('crdt_sync',         'CRDT Sync Engine',   'Enable conflict-free sync (beta)',   FALSE, 'sync',       '{"ENTERPRISE"}'),
    ('e_invoicing',       'E-Invoicing (IRD)',   'Enable IRD e-invoice submission',    FALSE, 'compliance', '{"ENTERPRISE"}');

-- ── Tax Rates ────────────────────────────────────────────────────────
CREATE TABLE tax_rates (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT        NOT NULL,
    rate            NUMERIC(5,2) NOT NULL,
    description     TEXT        NOT NULL DEFAULT '',
    applicable_to   TEXT[]      NOT NULL DEFAULT '{"ALL"}',
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    country         TEXT        NOT NULL DEFAULT 'LK',
    region          TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO tax_rates (name, rate, description, applicable_to, is_default, country) VALUES
    ('VAT 18%',   18.00, 'Standard VAT rate for Sri Lanka',       '{"ALL"}',      TRUE,  'LK'),
    ('VAT 0%',    0.00,  'Zero-rated goods (essential items)',     '{"FOOD"}',     FALSE, 'LK'),
    ('SVT 15%',   15.00, 'Social Value Tax on services',          '{"SERVICES"}', FALSE, 'LK');

-- ── System Config ────────────────────────────────────────────────────
CREATE TABLE system_config (
    key         TEXT        PRIMARY KEY,
    value       TEXT        NOT NULL,
    type        TEXT        NOT NULL DEFAULT 'string', -- string | number | boolean | json
    description TEXT        NOT NULL DEFAULT '',
    category    TEXT        NOT NULL DEFAULT 'system',  -- system | security | sync | notifications
    editable    BOOLEAN     NOT NULL DEFAULT TRUE,
    sensitive   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (key, value, type, description, category, editable, sensitive) VALUES
    ('max_devices_per_license',     '5',    'number',  'Default max devices per license',          'system',  TRUE,  FALSE),
    ('sync_interval_seconds',       '300',  'number',  'KMP app sync interval in seconds',         'sync',    TRUE,  FALSE),
    ('heartbeat_timeout_minutes',   '10',   'number',  'Minutes before store flagged as stale',    'sync',    TRUE,  FALSE),
    ('session_timeout_minutes',     '30',   'number',  'Admin panel session idle timeout',         'security',TRUE,  FALSE),
    ('max_failed_login_attempts',   '5',    'number',  'Lockout threshold for admin login',        'security',TRUE,  FALSE),
    ('lockout_duration_minutes',    '15',   'number',  'Admin account lockout duration',           'security',TRUE,  FALSE),
    ('alert_check_interval_secs',   '60',   'number',  'Alert rule evaluation interval',           'system',  TRUE,  FALSE),
    ('backup_retention_days',       '30',   'number',  'Days to retain automated backups',         'system',  TRUE,  FALSE);

-- ── Alert Rules ──────────────────────────────────────────────────────
CREATE TABLE alert_rules (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT        NOT NULL,
    description     TEXT        NOT NULL DEFAULT '',
    category        TEXT        NOT NULL,  -- sync | license | payment | security | system | store
    severity        TEXT        NOT NULL DEFAULT 'medium', -- critical | high | medium | low | info
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    conditions      JSONB       NOT NULL DEFAULT '{}',
    notify_channels TEXT[]      NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO alert_rules (name, description, category, severity, conditions) VALUES
    ('High sync queue depth',     'Fires when pending sync operations exceed threshold',  'sync',    'high',     '{"metric":"sync_queue_depth","op":"gt","threshold":50}'),
    ('Store heartbeat timeout',   'Fires when a store has not sent a heartbeat recently', 'store',   'critical', '{"metric":"heartbeat_age_minutes","op":"gt","threshold":10}'),
    ('Large database size',       'Fires when store DB exceeds 1 GB',                    'system',  'medium',   '{"metric":"db_size_bytes","op":"gt","threshold":1073741824}'),
    ('High sync failure rate',    'Fires when sync errors exceed threshold',              'sync',    'high',     '{"metric":"failed_sync_ops","op":"gt","threshold":10}'),
    ('License expiring soon',     'Fires when a license expires within 14 days',         'license', 'medium',   '{"metric":"days_until_expiry","op":"lt","threshold":14}'),
    ('All devices offline',       'Fires when all devices on a license go offline',      'store',   'critical', '{"metric":"active_devices","op":"eq","threshold":0}');

-- ── Alert Instances ──────────────────────────────────────────────────
CREATE TABLE alerts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID        REFERENCES alert_rules(id) ON DELETE SET NULL,
    title           TEXT        NOT NULL,
    message         TEXT        NOT NULL,
    severity        TEXT        NOT NULL,   -- lowercase: critical | high | medium | low | info
    status          TEXT        NOT NULL DEFAULT 'active', -- active | acknowledged | resolved | silenced
    category        TEXT        NOT NULL,   -- lowercase: sync | license | etc.
    store_id        TEXT,                   -- NULL for system-level alerts
    store_name      TEXT,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    fired_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_by UUID        REFERENCES admin_users(id) ON DELETE SET NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    silenced_until  TIMESTAMPTZ
);

CREATE INDEX idx_alerts_status   ON alerts(status);
CREATE INDEX idx_alerts_store    ON alerts(store_id);
CREATE INDEX idx_alerts_fired_at ON alerts(fired_at DESC);

-- ── Admin Audit Log ──────────────────────────────────────────────────
CREATE TABLE admin_audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      TEXT        NOT NULL,     -- e.g. LICENSE_CREATED, USER_UPDATED
    category        TEXT        NOT NULL,     -- AUTH | LICENSE | USER | SETTINGS | SYSTEM | SYNC
    admin_id        UUID        REFERENCES admin_users(id) ON DELETE SET NULL,
    admin_name      TEXT,                     -- snapshot of name at time of action
    store_id        TEXT,
    store_name      TEXT,
    entity_type     TEXT,
    entity_id       TEXT,
    previous_values JSONB,
    new_values      JSONB,
    ip_address      TEXT,
    user_agent      TEXT,
    success         BOOLEAN     NOT NULL DEFAULT TRUE,
    error_message   TEXT,
    hash_chain      TEXT        NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_admin    ON admin_audit_log(admin_id);
CREATE INDEX idx_admin_audit_category ON admin_audit_log(category);
CREATE INDEX idx_admin_audit_created  ON admin_audit_log(created_at DESC);

-- ── Force-Sync Flags (simple per-store signal) ───────────────────────
CREATE TABLE store_sync_flags (
    store_id            TEXT        PRIMARY KEY,
    force_sync_requested BOOLEAN    NOT NULL DEFAULT FALSE,
    requested_at        TIMESTAMPTZ,
    requested_by        UUID        REFERENCES admin_users(id) ON DELETE SET NULL
);
