-- ==========================================================================
-- V31 — Warehouse-to-store replenishment rules (C1.5)
-- Per-product thresholds for auto-PO generation.
-- ==========================================================================

CREATE TABLE replenishment_rules (
    id              TEXT        NOT NULL PRIMARY KEY,
    product_id      TEXT        NOT NULL,
    warehouse_id    TEXT        NOT NULL,
    supplier_id     TEXT        NOT NULL,
    reorder_point   DECIMAL(14,4) NOT NULL DEFAULT 0,
    reorder_qty     DECIMAL(14,4) NOT NULL DEFAULT 1,
    auto_approve    BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, warehouse_id)
);

CREATE INDEX idx_rr_product    ON replenishment_rules(product_id);
CREATE INDEX idx_rr_warehouse  ON replenishment_rules(warehouse_id);
CREATE INDEX idx_rr_supplier   ON replenishment_rules(supplier_id);
CREATE INDEX idx_rr_active     ON replenishment_rules(is_active);
