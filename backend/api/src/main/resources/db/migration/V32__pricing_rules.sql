-- V32: Add pricing_rules table for region-based pricing (C2.1)
--
-- Enables store-specific and time-bounded product price overrides.
-- Rules are synced down to POS devices for offline-first price resolution.

CREATE TABLE IF NOT EXISTS pricing_rules (
    id            TEXT         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    product_id    TEXT         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    store_id      TEXT         REFERENCES stores(id) ON DELETE CASCADE,  -- NULL = global rule
    price         NUMERIC(14,4) NOT NULL,
    cost_price    NUMERIC(14,4),
    priority      INTEGER      NOT NULL DEFAULT 0,
    valid_from    TIMESTAMPTZ,
    valid_to      TIMESTAMPTZ,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    description   TEXT         NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast product+store lookup during price resolution
CREATE INDEX idx_pricing_rules_product_store_active
    ON pricing_rules(product_id, store_id, is_active)
    WHERE is_active = TRUE;

-- Index for time-bounded queries
CREATE INDEX idx_pricing_rules_validity
    ON pricing_rules(valid_from, valid_to)
    WHERE is_active = TRUE;

-- Index for store-scoped queries (admin panel)
CREATE INDEX idx_pricing_rules_store
    ON pricing_rules(store_id);

-- Partial unique: only one active rule per product+store+priority
-- (prevents ambiguous resolution when two rules have the same priority)
CREATE UNIQUE INDEX idx_pricing_rules_unique_priority
    ON pricing_rules(product_id, COALESCE(store_id, ''), priority)
    WHERE is_active = TRUE;

COMMENT ON TABLE pricing_rules IS 'Store-specific and time-bounded product price overrides (C2.1 Region-Based Pricing)';
COMMENT ON COLUMN pricing_rules.store_id IS 'NULL = global rule applicable to all stores without a specific override';
COMMENT ON COLUMN pricing_rules.priority IS 'Higher value = higher precedence. Store-specific rules always beat global rules regardless of priority.';
