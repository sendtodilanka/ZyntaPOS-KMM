-- ============================================================
-- V16__high_query_indexes.sql
-- S3-11: Add composite indexes on high-query columns.
-- ============================================================

-- Composite index for conflict-detection query in SyncOperationRepository.findLatestForEntity().
-- This query runs on EVERY sync push to detect conflicts and was causing full table scans.
-- Pattern: WHERE (store_id = ?) AND (entity_type = ?) AND (entity_id = ?) AND (status != 'REJECTED')
--          ORDER BY server_seq DESC
CREATE INDEX IF NOT EXISTS idx_sync_ops_store_entity
    ON sync_operations(store_id, entity_type, entity_id);

-- Partial index for pending-only pull queries in SyncCursorRepository.
-- Excludes already-applied/rejected rows which represent the majority of the table at scale.
-- Pattern: WHERE store_id = ? AND server_seq > ? AND status NOT IN ('APPLIED', 'REJECTED')
CREATE INDEX IF NOT EXISTS idx_sync_ops_pending
    ON sync_operations(store_id, server_seq)
    WHERE status NOT IN ('APPLIED', 'REJECTED');
