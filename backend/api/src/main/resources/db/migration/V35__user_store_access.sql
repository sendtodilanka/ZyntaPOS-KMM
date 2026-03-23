-- V35: User-store access junction table for multi-store permissions (C3.2)
-- Allows users to have access to multiple stores with optional per-store role overrides.

CREATE TABLE IF NOT EXISTS user_store_access (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id        TEXT        NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    role_at_store   TEXT,       -- nullable = use user's default role; values: ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    granted_by      TEXT,       -- user_id of admin who granted access
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, store_id)
);

CREATE INDEX IF NOT EXISTS idx_user_store_access_user  ON user_store_access(user_id);
CREATE INDEX IF NOT EXISTS idx_user_store_access_store ON user_store_access(store_id);
CREATE INDEX IF NOT EXISTS idx_user_store_access_active ON user_store_access(user_id, is_active) WHERE is_active = TRUE;
