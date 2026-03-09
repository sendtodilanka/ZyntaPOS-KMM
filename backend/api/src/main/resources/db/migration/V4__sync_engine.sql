-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V4: Sync Engine Server-Side Tables (TODO-007g)
-- ═══════════════════════════════════════════════════════════════════
-- Stores every push operation from every POS terminal, with server-
-- assigned monotonic sequence numbers for cursor-based pull delta.
-- ═══════════════════════════════════════════════════════════════════

-- ── Main sync operations log ─────────────────────────────────────────
-- Every write from every POS terminal ends up here.
CREATE TABLE sync_operations (
    id                  TEXT        PRIMARY KEY,                -- Client-generated UUID (string)
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    device_id           TEXT        NOT NULL,
    entity_type         TEXT        NOT NULL,
    entity_id           TEXT        NOT NULL,
    operation           TEXT        NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    payload             JSONB       NOT NULL,
    client_timestamp    BIGINT      NOT NULL,
    server_seq          BIGSERIAL,                              -- Server-assigned monotonic sequence
    server_timestamp    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    vector_clock        BIGINT      NOT NULL DEFAULT 0,
    status              TEXT        NOT NULL DEFAULT 'ACCEPTED'
                        CHECK (status IN ('ACCEPTED', 'CONFLICT_RESOLVED', 'REJECTED')),
    conflict_id         TEXT
);

-- Pull queries: cursor-based pagination by (store_id, server_seq)
CREATE INDEX idx_sync_ops_pull
    ON sync_operations (store_id, server_seq)
    WHERE status != 'REJECTED';

-- Entity history: all operations for a specific entity
CREATE INDEX idx_sync_ops_entity
    ON sync_operations (store_id, entity_type, entity_id, server_seq DESC);

-- Device tracking: operations by device (for diagnostics/admin)
CREATE INDEX idx_sync_ops_device
    ON sync_operations (store_id, device_id, server_seq DESC);


-- ── Sync cursors — tracks each device's pull position ────────────────
CREATE TABLE sync_cursors (
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    device_id       TEXT        NOT NULL,
    last_seq        BIGINT      NOT NULL DEFAULT 0,
    last_pull_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (store_id, device_id)
);


-- ── Conflict log — audit trail of all conflict resolutions ───────────
CREATE TABLE sync_conflict_log (
    id                  TEXT        PRIMARY KEY DEFAULT gen_random_uuid()::text,
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    entity_type         TEXT        NOT NULL,
    entity_id           TEXT        NOT NULL,
    local_op_id         TEXT        NOT NULL,
    server_op_id        TEXT        NOT NULL,
    local_device_id     TEXT        NOT NULL,
    server_device_id    TEXT        NOT NULL,
    local_timestamp     BIGINT      NOT NULL,
    server_ts           BIGINT      NOT NULL,
    resolution          TEXT        NOT NULL CHECK (resolution IN (
                            'LWW_TIMESTAMP', 'DEVICE_ID_TIEBREAK', 'FIELD_MERGE', 'MANUAL'
                        )),
    local_payload       JSONB,
    server_payload      JSONB,
    merged_payload      JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conflict_log_entity
    ON sync_conflict_log (store_id, entity_type, entity_id);

CREATE INDEX idx_conflict_log_time
    ON sync_conflict_log (store_id, created_at DESC);


-- ── Dead letter queue — permanently failed operations ─────────────────
CREATE TABLE sync_dead_letters (
    id                  TEXT        PRIMARY KEY,
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    device_id           TEXT        NOT NULL,
    entity_type         TEXT        NOT NULL,
    entity_id           TEXT        NOT NULL,
    operation           TEXT        NOT NULL,
    payload             JSONB       NOT NULL,
    client_timestamp    BIGINT      NOT NULL,
    error_reason        TEXT        NOT NULL,
    retry_count         INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by         TEXT
);

CREATE INDEX idx_dead_letters_pending
    ON sync_dead_letters (store_id, reviewed_at)
    WHERE reviewed_at IS NULL;


-- ── Entity snapshots — latest state of each entity ────────────────────
-- Maintained by a trigger on sync_operations; used by pull + admin queries.
CREATE TABLE entity_snapshots (
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    entity_type     TEXT        NOT NULL,
    entity_id       TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    last_op_id      TEXT        NOT NULL,
    last_seq        BIGINT      NOT NULL,
    last_device_id  TEXT        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (store_id, entity_type, entity_id)
);

CREATE INDEX idx_snapshots_type
    ON entity_snapshots (store_id, entity_type, last_seq)
    WHERE NOT is_deleted;


-- ── Trigger: auto-update entity_snapshots on new sync_operations ─────
CREATE OR REPLACE FUNCTION update_entity_snapshot()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACCEPTED' OR NEW.status = 'CONFLICT_RESOLVED' THEN
        INSERT INTO entity_snapshots (
            store_id, entity_type, entity_id, payload,
            last_op_id, last_seq, last_device_id, updated_at, is_deleted
        ) VALUES (
            NEW.store_id, NEW.entity_type, NEW.entity_id, NEW.payload,
            NEW.id, NEW.server_seq, NEW.device_id, NOW(),
            NEW.operation = 'DELETE'
        )
        ON CONFLICT (store_id, entity_type, entity_id)
        DO UPDATE SET
            payload        = EXCLUDED.payload,
            last_op_id     = EXCLUDED.last_op_id,
            last_seq       = EXCLUDED.last_seq,
            last_device_id = EXCLUDED.last_device_id,
            updated_at     = EXCLUDED.updated_at,
            is_deleted     = EXCLUDED.is_deleted
        WHERE entity_snapshots.last_seq < EXCLUDED.last_seq;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_op_snapshot
    AFTER INSERT ON sync_operations
    FOR EACH ROW
    EXECUTE FUNCTION update_entity_snapshot();


-- ── Store sync flags (extended) ───────────────────────────────────────
-- Extended to be used by admin force-sync logic (already referenced in V3 routes)
CREATE TABLE IF NOT EXISTS store_sync_flags (
    store_id                TEXT        PRIMARY KEY REFERENCES stores(id),
    force_sync_requested    BOOLEAN     NOT NULL DEFAULT FALSE,
    requested_at            TIMESTAMPTZ,
    requested_by            TEXT
);
