-- ═══════════════════════════════════════════════════════════════════
-- V28: Warehouse Stock — Per-warehouse product stock levels (C1.2)
-- Mirrors the KMM warehouse_stock.sq SQLDelight schema.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS warehouse_stock (
    id              TEXT            PRIMARY KEY,
    warehouse_id    TEXT            NOT NULL,
    product_id      TEXT            NOT NULL,
    store_id        TEXT            NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    quantity        NUMERIC(14,4)   NOT NULL DEFAULT 0,
    min_quantity    NUMERIC(14,4)   NOT NULL DEFAULT 0,
    sync_version    BIGINT          NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (warehouse_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_ws_warehouse  ON warehouse_stock(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_ws_product    ON warehouse_stock(product_id);
CREATE INDEX IF NOT EXISTS idx_ws_store      ON warehouse_stock(store_id);
CREATE INDEX IF NOT EXISTS idx_ws_low_stock  ON warehouse_stock(warehouse_id)
    WHERE quantity <= min_quantity AND min_quantity > 0;
