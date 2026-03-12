-- ═══════════════════════════════════════════════════════════════════
-- V13: Add composite indexes on sync_operations for query performance (S3-11)
-- ═══════════════════════════════════════════════════════════════════

-- Covers pull-by-store queries filtered by status (most common sync query pattern)
CREATE INDEX IF NOT EXISTS idx_sync_operations_store_status
    ON sync_operations(store_id, status);

-- Covers time-range queries for audit, cleanup, and monitoring
CREATE INDEX IF NOT EXISTS idx_sync_operations_server_timestamp
    ON sync_operations(server_timestamp);
