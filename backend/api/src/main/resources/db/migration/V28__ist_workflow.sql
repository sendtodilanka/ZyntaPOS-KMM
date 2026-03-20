-- ═══════════════════════════════════════════════════════════════════
-- V28: Inter-Store Stock Transfer (IST) Workflow (C1.3)
-- Creates the stock_transfers table for backend-side IST management
-- with multi-step approval workflow:
--   PENDING → APPROVED → IN_TRANSIT → RECEIVED
-- Also creates purchase_orders and purchase_order_items tables.
-- ═══════════════════════════════════════════════════════════════════

-- ── Stock transfers table ─────────────────────────────────────────────────────
-- This table is new in the PostgreSQL backend (was SQLite-only before C1.3).
-- source_warehouse_id / dest_warehouse_id are opaque IDs (no FK — warehouses
-- are managed client-side in the KMM SQLite layer).

CREATE TABLE IF NOT EXISTS stock_transfers (
    id                  TEXT            PRIMARY KEY,
    source_warehouse_id TEXT            NOT NULL,
    dest_warehouse_id   TEXT            NOT NULL,
    source_store_id     TEXT            REFERENCES stores(id),
    dest_store_id       TEXT            REFERENCES stores(id),
    product_id          TEXT            NOT NULL,
    quantity            NUMERIC(12,4)   NOT NULL,
    status              TEXT            NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'COMMITTED', 'CANCELLED')),
    notes               TEXT,
    -- Legacy commit fields (backward compat with warehouse-level two-phase commit)
    transferred_by      TEXT,
    transferred_at      TIMESTAMPTZ,
    -- IST multi-step workflow fields
    created_by          TEXT,
    approved_by         TEXT,
    approved_at         TIMESTAMPTZ,
    dispatched_by       TEXT,
    dispatched_at       TIMESTAMPTZ,
    received_by         TEXT,
    received_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_status       ON stock_transfers(status);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_source_store ON stock_transfers(source_store_id)
    WHERE source_store_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_transfers_dest_store   ON stock_transfers(dest_store_id)
    WHERE dest_store_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_transfers_product      ON stock_transfers(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_created_at   ON stock_transfers(created_at DESC);

-- ── Purchase orders table (C1.3 / C1.5) ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS purchase_orders (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    supplier_id     TEXT        NOT NULL,
    order_number    TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PARTIAL', 'RECEIVED', 'CANCELLED')),
    order_date      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expected_date   TIMESTAMPTZ,
    received_date   TIMESTAMPTZ,
    total_amount    NUMERIC(12,4) NOT NULL DEFAULT 0,
    currency        TEXT        NOT NULL DEFAULT 'LKR',
    notes           TEXT,
    created_by      TEXT        NOT NULL,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                  TEXT        PRIMARY KEY,
    purchase_order_id   TEXT        NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id          TEXT        NOT NULL,
    quantity_ordered    NUMERIC(12,4) NOT NULL,
    quantity_received   NUMERIC(12,4) NOT NULL DEFAULT 0,
    unit_cost           NUMERIC(12,4) NOT NULL,
    line_total          NUMERIC(12,4) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_store    ON purchase_orders(store_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status   ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_date     ON purchase_orders(order_date DESC);
CREATE INDEX IF NOT EXISTS idx_poi_order                ON purchase_order_items(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_poi_product              ON purchase_order_items(product_id);
