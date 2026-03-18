-- V24: Add 5 new normalized entity tables for EntityApplier
-- Entity types: EMPLOYEE, EXPENSE_CATEGORY, COUPON_USAGE, PROMOTION, CUSTOMER_GROUP
-- These flow through the sync pipeline and need server-side normalized tables
-- for efficient admin/API queries.

-- ── Employees ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS employees (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    email           TEXT,
    phone           TEXT,
    role            TEXT        NOT NULL DEFAULT 'STAFF',
    department      TEXT,
    hire_date       BIGINT,
    hourly_rate     NUMERIC(12,4) NOT NULL DEFAULT 0,
    notes           TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_employees_store ON employees(store_id);
CREATE INDEX IF NOT EXISTS idx_employees_role ON employees(store_id, role);

-- ── Expense Categories ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS expense_categories (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    description     TEXT,
    parent_id       TEXT,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_expense_categories_store ON expense_categories(store_id);

-- ── Coupon Usage (redemption tracking) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS coupon_usages (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    coupon_id       TEXT        NOT NULL,
    order_id        TEXT        NOT NULL,
    customer_id     TEXT,
    discount_amount NUMERIC(12,4) NOT NULL DEFAULT 0,
    redeemed_by     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_store ON coupon_usages(store_id);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_coupon ON coupon_usages(coupon_id);
CREATE INDEX IF NOT EXISTS idx_coupon_usages_order ON coupon_usages(order_id);

-- ── Promotions ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS promotions (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    description     TEXT,
    type            TEXT        NOT NULL DEFAULT 'PERCENTAGE',
    value           NUMERIC(12,4) NOT NULL DEFAULT 0,
    minimum_purchase NUMERIC(12,4) NOT NULL DEFAULT 0,
    scope           TEXT        NOT NULL DEFAULT 'CART',
    scope_ids       TEXT,
    valid_from      BIGINT,
    valid_to        BIGINT,
    priority        INTEGER     NOT NULL DEFAULT 0,
    is_stackable    BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_promotions_store ON promotions(store_id);
CREATE INDEX IF NOT EXISTS idx_promotions_active ON promotions(store_id, is_active) WHERE is_active = TRUE;

-- ── Customer Groups ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customer_groups (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    description     TEXT,
    discount_rate   NUMERIC(6,3) NOT NULL DEFAULT 0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customer_groups_store ON customer_groups(store_id);
