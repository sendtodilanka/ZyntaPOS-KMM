-- V23: Extended normalized entity tables for EntityApplier
-- Adds tables for entity types that flow through the sync pipeline
-- but didn't have server-side normalized tables yet.
-- EntityApplier writes here on each sync push so admin/API queries are efficient.

-- ── Stock Adjustments ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_adjustments (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    product_id      TEXT        NOT NULL,
    type            TEXT        NOT NULL CHECK (type IN ('INCREASE', 'DECREASE', 'TRANSFER')),
    quantity        NUMERIC(12,4) NOT NULL DEFAULT 0,
    reason          TEXT,
    adjusted_by     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_stock_adjustments_store ON stock_adjustments(store_id);
CREATE INDEX IF NOT EXISTS idx_stock_adjustments_product ON stock_adjustments(product_id);

-- ── Cash Registers ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cash_registers (
    id                  TEXT        PRIMARY KEY,
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    name                TEXT        NOT NULL,
    current_session_id  TEXT,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version        BIGINT      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cash_registers_store ON cash_registers(store_id);

-- ── Register Sessions ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS register_sessions (
    id                  TEXT        PRIMARY KEY,
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    register_id         TEXT        NOT NULL,
    opened_by           TEXT        NOT NULL,
    closed_by           TEXT,
    opening_balance     NUMERIC(12,4) NOT NULL DEFAULT 0,
    closing_balance     NUMERIC(12,4),
    expected_balance    NUMERIC(12,4) NOT NULL DEFAULT 0,
    actual_balance      NUMERIC(12,4),
    status              TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version        BIGINT      NOT NULL DEFAULT 1,
    opened_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_register_sessions_store ON register_sessions(store_id);
CREATE INDEX IF NOT EXISTS idx_register_sessions_register ON register_sessions(register_id);

-- ── Cash Movements ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cash_movements (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    session_id      TEXT        NOT NULL,
    type            TEXT        NOT NULL CHECK (type IN ('IN', 'OUT')),
    amount          NUMERIC(12,4) NOT NULL DEFAULT 0,
    reason          TEXT,
    recorded_by     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cash_movements_store ON cash_movements(store_id);
CREATE INDEX IF NOT EXISTS idx_cash_movements_session ON cash_movements(session_id);

-- ── Tax Groups ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tax_groups (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    rate            NUMERIC(6,3) NOT NULL DEFAULT 0,
    is_inclusive    BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tax_groups_store ON tax_groups(store_id);

-- ── Units of Measure ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS units_of_measure (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    name            TEXT        NOT NULL,
    abbreviation    TEXT,
    is_base_unit    BOOLEAN     NOT NULL DEFAULT FALSE,
    conversion_rate NUMERIC(12,6) NOT NULL DEFAULT 1.0,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_units_of_measure_store ON units_of_measure(store_id);

-- ── Payment Splits ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_splits (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    order_id        TEXT        NOT NULL,
    method          TEXT        NOT NULL,
    amount          NUMERIC(12,4) NOT NULL DEFAULT 0,
    reference       TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_payment_splits_store ON payment_splits(store_id);
CREATE INDEX IF NOT EXISTS idx_payment_splits_order ON payment_splits(order_id);

-- ── Coupons ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS coupons (
    id                  TEXT        PRIMARY KEY,
    store_id            TEXT        NOT NULL REFERENCES stores(id),
    code                TEXT        NOT NULL,
    name                TEXT        NOT NULL,
    discount_type       TEXT        NOT NULL,
    discount_value      NUMERIC(12,4) NOT NULL DEFAULT 0,
    minimum_purchase    NUMERIC(12,4) NOT NULL DEFAULT 0,
    maximum_discount    NUMERIC(12,4),
    usage_limit         INTEGER,
    usage_count         INTEGER     NOT NULL DEFAULT 0,
    per_customer_limit  INTEGER,
    scope               TEXT        NOT NULL DEFAULT 'CART',
    scope_ids           TEXT,
    valid_from          BIGINT,
    valid_to            BIGINT,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version        BIGINT      NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_coupons_store ON coupons(store_id);
CREATE INDEX IF NOT EXISTS idx_coupons_code ON coupons(store_id, code);

-- ── Expenses ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS expenses (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    category        TEXT,
    amount          NUMERIC(12,4) NOT NULL DEFAULT 0,
    description     TEXT,
    recorded_by     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_expenses_store ON expenses(store_id);

-- ── Settings (key-value per store) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS settings (
    id              TEXT        PRIMARY KEY,
    store_id        TEXT        NOT NULL REFERENCES stores(id),
    key             TEXT        NOT NULL,
    value           TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    sync_version    BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_settings_store ON settings(store_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_settings_store_key ON settings(store_id, key);
