-- V33: Multi-currency support (C2.2)
-- Exchange rates table for currency conversion between stores

CREATE TABLE IF NOT EXISTS exchange_rates (
    id               UUID        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    source_currency  VARCHAR(3)  NOT NULL,
    target_currency  VARCHAR(3)  NOT NULL,
    rate             DECIMAL(18, 8) NOT NULL,
    effective_date   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ,
    source           VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exchange_rate_pair UNIQUE (source_currency, target_currency)
);

CREATE INDEX IF NOT EXISTS idx_exchange_rates_source ON exchange_rates(source_currency);
CREATE INDEX IF NOT EXISTS idx_exchange_rates_target ON exchange_rates(target_currency);
CREATE INDEX IF NOT EXISTS idx_exchange_rates_effective ON exchange_rates(effective_date);

-- Add currency column to orders table (store's currency at time of sale)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'LKR';

-- Seed common exchange rates (LKR base, approximate rates as of 2026-03)
INSERT INTO exchange_rates (source_currency, target_currency, rate, source)
VALUES
    ('USD', 'LKR', 298.50, 'SEED'),
    ('EUR', 'LKR', 325.00, 'SEED'),
    ('GBP', 'LKR', 378.00, 'SEED'),
    ('INR', 'LKR', 3.55, 'SEED'),
    ('AUD', 'LKR', 192.00, 'SEED'),
    ('SGD', 'LKR', 222.00, 'SEED'),
    ('AED', 'LKR', 81.30, 'SEED'),
    ('JPY', 'LKR', 2.00, 'SEED')
ON CONFLICT (source_currency, target_currency) DO NOTHING;
