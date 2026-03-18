-- V22: Add customer_access_token for public ticket status check.
-- Allows customers to check their ticket status via a unique URL token
-- without requiring authentication to the admin panel.

ALTER TABLE support_tickets
    ADD COLUMN customer_access_token UUID DEFAULT gen_random_uuid() NOT NULL;

-- Unique constraint and index for fast lookup
ALTER TABLE support_tickets
    ADD CONSTRAINT uq_support_tickets_customer_token UNIQUE (customer_access_token);

CREATE INDEX idx_support_tickets_customer_token ON support_tickets(customer_access_token);
