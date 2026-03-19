-- ═══════════════════════════════════════════════════════════════════
-- V28: Inter-Store Stock Transfer (IST) Workflow (C1.3)
-- Extends stock_transfers table with multi-step approval workflow:
--   PENDING → APPROVED → IN_TRANSIT → RECEIVED
-- Also adds store_transfers view for store-level grouping.
-- ═══════════════════════════════════════════════════════════════════

-- ── Add IST workflow columns to stock_transfers ──────────────────────────────

ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS created_by     TEXT;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS approved_by    TEXT;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS approved_at    TIMESTAMPTZ;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS dispatched_by  TEXT;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS dispatched_at  TIMESTAMPTZ;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS received_by    TEXT;
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS received_at    TIMESTAMPTZ;

-- Extend status CHECK constraint to allow new IST statuses
-- (PostgreSQL requires dropping and re-adding constraint)
ALTER TABLE stock_transfers DROP CONSTRAINT IF EXISTS stock_transfers_status_check;
ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'COMMITTED', 'CANCELLED'));

-- ── Add index for status-based queries ───────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_stock_transfers_status ON stock_transfers(status);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_store_source ON stock_transfers(source_store_id)
    WHERE source_store_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_transfers_store_dest ON stock_transfers(dest_store_id)
    WHERE dest_store_id IS NOT NULL;

-- ── Add store-level columns (store grouping for multi-store IST) ──────────────

ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS source_store_id TEXT REFERENCES stores(id);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS dest_store_id   TEXT REFERENCES stores(id);

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
