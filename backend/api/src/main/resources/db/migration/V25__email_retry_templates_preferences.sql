-- V25: Email retry support, email templates, and email preferences enhancements
-- Supports A2 completion: retry logic, template editor, preference management

-- ── Email retry columns on delivery log ──────────────────────────────────────
ALTER TABLE email_delivery_log ADD COLUMN retry_count   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE email_delivery_log ADD COLUMN next_retry_at TIMESTAMPTZ;
ALTER TABLE email_delivery_log ADD COLUMN html_body     TEXT;

CREATE INDEX idx_email_delivery_log_retry
    ON email_delivery_log (next_retry_at)
    WHERE status = 'FAILED' AND retry_count < 3 AND next_retry_at IS NOT NULL;

-- ── Email templates (admin-editable) ─────────────────────────────────────────
CREATE TABLE email_templates (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       VARCHAR(100) NOT NULL UNIQUE,
    name       TEXT         NOT NULL,
    subject    TEXT         NOT NULL,
    html_body  TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed default templates matching EmailService.kt template slugs
INSERT INTO email_templates (slug, name, subject, html_body) VALUES
('password_reset', 'Password Reset', 'Reset your ZyntaPOS password',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><h2>Reset your ZyntaPOS password</h2><p>You requested a password reset for your ZyntaPOS Admin Panel account.</p><p>Click the link below to set a new password. This link expires in 1 hour.</p><p><a href="{{resetLink}}" style="background:#1976d2;color:#fff;padding:12px 24px;border-radius:4px;text-decoration:none;display:inline-block">Reset Password</a></p><p>If you did not request this, you can safely ignore this email.</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>'),
('welcome_admin', 'Welcome Admin', 'Welcome to ZyntaPOS Admin Panel',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><h2>Welcome to ZyntaPOS, {{name}}!</h2><p>Your ZyntaPOS Admin Panel account has been created.</p><p>You can access the admin panel at <a href="{{adminPanelUrl}}">{{adminPanelUrl}}</a>.</p><p>Use the credentials set during account creation to log in. We recommend enabling MFA immediately.</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>'),
('ticket_created', 'Ticket Created', 'Support Ticket Created: #{{ticketNumber}}',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><h2>Support Ticket Created: #{{ticketNumber}}</h2><p>Your support ticket has been received and will be addressed shortly.</p><p><strong>Title:</strong> {{title}}</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>'),
('ticket_updated', 'Ticket Updated', 'Ticket #{{ticketNumber}} status updated: {{newStatus}}',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><h2>Ticket #{{ticketNumber}} Updated</h2><p>The status of your support ticket has been updated to: <strong>{{newStatus}}</strong></p><p>Log in to the ZyntaPOS Admin Panel for more details.</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>'),
('sla_breach', 'SLA Breach Alert', 'SLA Breach: {{ticketNumber}} [{{priority}}]',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><h2 style="color:#dc2626">SLA Breach Alert</h2><p>Ticket <strong>#{{ticketNumber}}</strong> has breached its SLA deadline.</p><p><strong>Title:</strong> {{title}}</p><p><strong>Priority:</strong> {{priority}}</p><p style="color:#dc2626;font-weight:600">Please take immediate action to resolve this ticket.</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>'),
('ticket_reply', 'Ticket Reply', 'Re: [{{ticketNumber}}] Your support request',
 '<!DOCTYPE html><html><body style="font-family:sans-serif;max-width:600px;margin:0 auto"><p>Dear {{customerName}},</p><p>{{messageBody}}</p><hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0"/><p style="color:#666;font-size:12px">Replied by {{agentName}} &mdash; Ticket #{{ticketNumber}}</p><p style="color:#666;font-size:12px">ZyntaPOS &mdash; Enterprise Point of Sale</p></body></html>');

-- ── Email preferences enhancements ───────────────────────────────────────────
ALTER TABLE email_preferences ADD COLUMN IF NOT EXISTS sla_breach_notifications BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE email_preferences ADD COLUMN IF NOT EXISTS daily_digest BOOLEAN NOT NULL DEFAULT false;
