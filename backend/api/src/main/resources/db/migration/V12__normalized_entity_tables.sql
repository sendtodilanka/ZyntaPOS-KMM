-- V12: Normalized entity tables for server-side queries
-- Mirrors KMM SQLDelight schema for categories, customers, suppliers, orders, order_items.
-- EntityApplier writes here on each sync push so admin/API queries are efficient.

-- ── Categories ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
    id          TEXT        PRIMARY KEY,
    store_id    TEXT        NOT NULL REFERENCES stores(id),
    name        TEXT        NOT NULL,
    parent_id   TEXT,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    image_url   TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version BIGINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_categories_store ON categories(store_id);

-- ── Customers ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customers (
    id          TEXT        PRIMARY KEY,
    store_id    TEXT        NOT NULL REFERENCES stores(id),
    name        TEXT        NOT NULL,
    email       TEXT,
    phone       TEXT,
    address     TEXT,
    notes       TEXT,
    loyalty_points INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version BIGINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customers_store ON customers(store_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone) WHERE phone IS NOT NULL;

-- ── Suppliers ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS suppliers (
    id          TEXT        PRIMARY KEY,
    store_id    TEXT        NOT NULL REFERENCES stores(id),
    name        TEXT        NOT NULL,
    contact_name TEXT,
    phone       TEXT,
    email       TEXT,
    address     TEXT,
    notes       TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version BIGINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_suppliers_store ON suppliers(store_id);

-- ── Orders ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    order_number    TEXT,
    customer_id     TEXT,
    cashier_id      TEXT,
    status          TEXT        NOT NULL DEFAULT 'PENDING',
    order_type      TEXT        NOT NULL DEFAULT 'DINE_IN',
    subtotal        NUMERIC(12,4) NOT NULL DEFAULT 0,
    tax_total       NUMERIC(12,4) NOT NULL DEFAULT 0,
    discount_total  NUMERIC(12,4) NOT NULL DEFAULT 0,
    grand_total     NUMERIC(12,4) NOT NULL DEFAULT 0,
    notes           TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_orders_store ON orders(store_id);
CREATE INDEX IF NOT EXISTS idx_orders_store_date ON orders(store_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id) WHERE customer_id IS NOT NULL;

-- ── Order Items ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_items (
    id              TEXT        PRIMARY KEY,
    order_id        TEXT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      TEXT        NOT NULL,
    product_name    TEXT        NOT NULL,
    quantity        NUMERIC(12,4) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,4) NOT NULL DEFAULT 0,
    discount        NUMERIC(12,4) NOT NULL DEFAULT 0,
    tax             NUMERIC(12,4) NOT NULL DEFAULT 0,
    subtotal        NUMERIC(12,4) NOT NULL DEFAULT 0,
    notes           TEXT,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items(product_id);
