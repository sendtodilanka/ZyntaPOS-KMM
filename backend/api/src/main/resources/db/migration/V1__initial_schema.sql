-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — Initial PostgreSQL Schema
-- V1: Core tables for authentication, products, sync
-- ═══════════════════════════════════════════════════════════════════

-- ── Stores ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stores (
    id          TEXT        PRIMARY KEY,
    name        TEXT        NOT NULL,
    license_key TEXT        NOT NULL,  -- validated at app layer (license DB is separate)
    timezone    TEXT        NOT NULL DEFAULT 'Asia/Colombo',
    currency    TEXT        NOT NULL DEFAULT 'LKR',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Users ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    username        TEXT        NOT NULL,
    password_hash   TEXT        NOT NULL,   -- SHA-256 + salt, matching PinManager format
    role            TEXT        NOT NULL,   -- ADMIN, MANAGER, CASHIER, etc.
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (store_id, username)
);

-- ── Sync Queue ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sync_queue (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL,
    device_id       TEXT        NOT NULL,
    entity_type     TEXT        NOT NULL,
    entity_id       TEXT        NOT NULL,
    operation       TEXT        NOT NULL,   -- INSERT, UPDATE, DELETE
    payload         JSONB       NOT NULL,
    vector_clock    BIGINT      NOT NULL,
    client_ts       TIMESTAMPTZ NOT NULL,
    server_ts       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_processed    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sync_queue_store_vc  ON sync_queue(store_id, vector_clock);
CREATE INDEX IF NOT EXISTS idx_sync_queue_unprocessed ON sync_queue(is_processed) WHERE NOT is_processed;

-- ── Products (server-side mirror for sync) ─────────────────────────
CREATE TABLE IF NOT EXISTS products (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    sku             TEXT,
    barcode         TEXT,
    price           NUMERIC(12,4) NOT NULL DEFAULT 0,
    cost_price      NUMERIC(12,4) NOT NULL DEFAULT 0,
    stock_qty       INTEGER     NOT NULL DEFAULT 0,
    category_id     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_store       ON products(store_id);
CREATE INDEX IF NOT EXISTS idx_products_updated_at  ON products(store_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_products_barcode     ON products(barcode) WHERE barcode IS NOT NULL;
