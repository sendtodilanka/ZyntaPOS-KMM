-- V34: Add regional_tax_overrides table for localized tax (C2.3)
--
-- Enables per-store tax rate overrides for multi-region tax compliance.
-- Stores in different jurisdictions can override global tax group rates.
-- Also adds tax_registration_number to stores for legal compliance.
--
-- Per ADR-009: tax management is a store-level business operation.
-- Write endpoints go under /v1/taxes/* with POS JWT auth.

CREATE TABLE IF NOT EXISTS regional_tax_overrides (
    id                       TEXT         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    tax_group_id             TEXT         NOT NULL REFERENCES tax_groups(id) ON DELETE CASCADE,
    store_id                 TEXT         NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    effective_rate           NUMERIC(6,3) NOT NULL DEFAULT 0,
    jurisdiction_code        TEXT         NOT NULL DEFAULT '',
    tax_registration_number  TEXT         NOT NULL DEFAULT '',
    valid_from               TIMESTAMPTZ,
    valid_to                 TIMESTAMPTZ,
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast lookup during tax resolution at checkout
CREATE INDEX idx_regional_tax_group_store_active
    ON regional_tax_overrides(tax_group_id, store_id, is_active)
    WHERE is_active = TRUE;

-- Index for store-scoped queries
CREATE INDEX idx_regional_tax_store
    ON regional_tax_overrides(store_id);

-- Unique: one active override per tax group per store (prevents ambiguous resolution)
CREATE UNIQUE INDEX idx_regional_tax_unique_override
    ON regional_tax_overrides(tax_group_id, store_id)
    WHERE is_active = TRUE;

-- Add tax_registration_number to stores for legal compliance
ALTER TABLE stores ADD COLUMN IF NOT EXISTS tax_registration_number TEXT NOT NULL DEFAULT '';

COMMENT ON TABLE regional_tax_overrides IS 'Per-store tax rate overrides for multi-region compliance (C2.3 Localized Tax)';
COMMENT ON COLUMN regional_tax_overrides.effective_rate IS 'Override rate (0-100) used instead of the global TaxGroup rate at this store';
COMMENT ON COLUMN regional_tax_overrides.jurisdiction_code IS 'Tax jurisdiction code (e.g., LK-WP for Western Province, SG for Singapore)';
COMMENT ON COLUMN regional_tax_overrides.tax_registration_number IS 'Store-specific tax registration number for this jurisdiction';
COMMENT ON COLUMN stores.tax_registration_number IS 'Primary tax registration number for the store (e.g., VAT reg number)';
