-- ═══════════════════════════════════════════════════════════════════
-- V20: Email delivery log — audit trail for outbound transactional emails (TODO-008a)
--
-- Tracks every email sent through EmailService (Resend API).
-- Admin panel reads this via GET /admin/email/delivery-logs.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS email_delivery_log (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    to_address      TEXT            NOT NULL,
    from_address    TEXT            NOT NULL,
    subject         TEXT            NOT NULL,
    template_slug   TEXT,                       -- e.g. 'ticket_created', 'password_reset'
    status          TEXT            NOT NULL DEFAULT 'SENT'
                    CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'BOUNCED', 'FAILED')),
    error_message   TEXT,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_delivery_log_status     ON email_delivery_log(status);
CREATE INDEX idx_email_delivery_log_created_at ON email_delivery_log(created_at DESC);
CREATE INDEX idx_email_delivery_log_to         ON email_delivery_log(to_address);
