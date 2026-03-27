-- V38: Make customers.store_id nullable to support global (cross-store) customers
--
-- Global customers are not tied to a specific store; store_id = NULL means the
-- customer record belongs to the tenant account and can be referenced by any store.
-- Existing rows retain their store_id values (NULL backfill not required).

ALTER TABLE customers ALTER COLUMN store_id DROP NOT NULL;
