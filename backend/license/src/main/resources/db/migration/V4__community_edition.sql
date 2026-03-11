-- V4: Rename edition STARTER → COMMUNITY (C4)
-- KMP Edition domain model uses COMMUNITY; old schema used STARTER.

-- 1. Update any existing rows that use the old value
UPDATE licenses SET edition = 'COMMUNITY' WHERE edition = 'STARTER';

-- 2. Drop the old check constraint and add the updated one
ALTER TABLE licenses DROP CONSTRAINT IF EXISTS licenses_edition_check;
ALTER TABLE licenses ADD CONSTRAINT licenses_edition_check
    CHECK (edition IN ('COMMUNITY', 'PROFESSIONAL', 'ENTERPRISE'));
