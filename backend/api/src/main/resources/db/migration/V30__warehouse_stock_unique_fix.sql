-- ═══════════════════════════════════════════════════════════════════
-- V30: Fix warehouse_stock unique constraint (C1.2)
--
-- The V29 constraint UNIQUE(warehouse_id, product_id) is too strict
-- for multi-store deployments: the same physical warehouse can track
-- different stock levels for the same product across multiple stores.
-- Replace it with UNIQUE(warehouse_id, product_id, store_id).
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE warehouse_stock
    DROP CONSTRAINT IF EXISTS warehouse_stock_warehouse_id_product_id_key;

ALTER TABLE warehouse_stock
    ADD CONSTRAINT warehouse_stock_warehouse_id_product_id_store_id_key
    UNIQUE (warehouse_id, product_id, store_id);
