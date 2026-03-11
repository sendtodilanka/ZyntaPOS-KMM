-- V9: API contract alignment
-- Adds email + name columns to users (A9), and adds missing product columns (A8).

-- ── users table ───────────────────────────────────────────────────────────────
-- KMP AuthRequestDto sends { "email": "..." }; legacy rows used "username".
-- Add email column (nullable initially so existing rows aren't rejected).
ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS name  TEXT;

-- Populate email from username for legacy rows that don't have it yet
UPDATE users SET email = username WHERE email IS NULL;

-- Create unique index for email lookups (allow NULL during migration; enforced at app layer)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email) WHERE email IS NOT NULL;

-- ── products table ────────────────────────────────────────────────────────────
-- Adds columns required by KMP ProductDto (17-field contract).
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS unit_id      TEXT,
    ADD COLUMN IF NOT EXISTS tax_group_id TEXT,
    ADD COLUMN IF NOT EXISTS min_stock_qty NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS image_url    TEXT,
    ADD COLUMN IF NOT EXISTS description  TEXT,
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMPTZ DEFAULT now();

-- Migrate stock_qty column type from INTEGER to NUMERIC to match KMP Double
ALTER TABLE products
    ALTER COLUMN stock_qty TYPE NUMERIC(12, 4) USING stock_qty::NUMERIC(12, 4);
