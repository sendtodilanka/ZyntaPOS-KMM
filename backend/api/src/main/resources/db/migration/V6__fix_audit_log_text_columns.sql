-- V6: Convert admin_audit_log.previous_values and new_values from JSONB to TEXT
--
-- The Exposed ORM maps these columns as text() which sends a character varying
-- binding. PostgreSQL JSONB columns reject varchar bindings without an explicit
-- cast, causing a 500 on every admin login and mutation.
-- Converting to TEXT preserves all data (JSONB::TEXT round-trips losslessly)
-- and aligns the DB type with what Exposed actually sends.

ALTER TABLE admin_audit_log
    ALTER COLUMN previous_values TYPE TEXT USING previous_values::TEXT,
    ALTER COLUMN new_values       TYPE TEXT USING new_values::TEXT;
