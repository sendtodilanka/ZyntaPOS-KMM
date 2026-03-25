-- V37: Add typed config and store_ids columns to promotions table (C2.4)
--
-- The original promotions table (V24) used a simple `type`/`value`/`scope` structure.
-- The KMM client now uses a richer sealed PromotionConfig (BUY_X_GET_Y, BUNDLE,
-- FLASH_SALE, SCHEDULED) stored as JSON. These columns bridge the gap so that
-- GET /v1/promotions can return KMM-compatible payloads.

ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS config     TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS store_ids  TEXT NOT NULL DEFAULT '[]';

COMMENT ON COLUMN promotions.config    IS 'Typed promotion config JSON (PromotionConfig sealed class variants)';
COMMENT ON COLUMN promotions.store_ids IS 'JSON array of store IDs this promotion targets; empty = global';
