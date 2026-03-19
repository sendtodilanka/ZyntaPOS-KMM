-- ═══════════════════════════════════════════════════════════════════
-- V27: Master Products — Global Product Catalog (C1.1)
-- Two-tier architecture: master_products (global) + store_products (per-store overrides)
-- ═══════════════════════════════════════════════════════════════════

-- ── Master Products: central product templates, NOT scoped to any store ──
CREATE TABLE IF NOT EXISTS master_products (
    id              TEXT        PRIMARY KEY,
    sku             TEXT        UNIQUE,
    barcode         TEXT        UNIQUE,
    name            TEXT        NOT NULL,
    description     TEXT,
    base_price      NUMERIC(12,4) NOT NULL DEFAULT 0,
    cost_price      NUMERIC(12,4) NOT NULL DEFAULT 0,
    category_id     TEXT,
    unit_id         TEXT,
    tax_group_id    TEXT,
    image_url       TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_master_products_sku     ON master_products(sku) WHERE sku IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_master_products_barcode ON master_products(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_master_products_active  ON master_products(is_active);
CREATE INDEX IF NOT EXISTS idx_master_products_name    ON master_products(name);

-- ── Store Products: per-store overrides + assignment junction ──
CREATE TABLE IF NOT EXISTS store_products (
    id                  TEXT        PRIMARY KEY,
    master_product_id   TEXT        NOT NULL REFERENCES master_products(id) ON DELETE CASCADE,
    store_id            TEXT        NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    local_price         NUMERIC(12,4),       -- NULL = use master base_price
    local_cost_price    NUMERIC(12,4),       -- NULL = use master cost_price
    local_stock_qty     INTEGER     NOT NULL DEFAULT 0,
    min_stock_qty       INTEGER     NOT NULL DEFAULT 0,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version        BIGINT      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (master_product_id, store_id)
);

CREATE INDEX IF NOT EXISTS idx_store_products_store  ON store_products(store_id);
CREATE INDEX IF NOT EXISTS idx_store_products_master ON store_products(master_product_id);

-- ── Link existing products table to master catalog (nullable FK) ──
ALTER TABLE products ADD COLUMN IF NOT EXISTS master_product_id TEXT REFERENCES master_products(id);
CREATE INDEX IF NOT EXISTS idx_products_master ON products(master_product_id) WHERE master_product_id IS NOT NULL;
