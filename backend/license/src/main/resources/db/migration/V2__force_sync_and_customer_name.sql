-- V2: Add force_sync_requested flag and customer_name to licenses
ALTER TABLE licenses ADD COLUMN force_sync_requested BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE licenses ADD COLUMN customer_name TEXT;
