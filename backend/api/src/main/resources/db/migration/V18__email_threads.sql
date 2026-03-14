-- ═══════════════════════════════════════════════════════════════════
-- V18: Email threads — inbound email storage for support inbox (TODO-008a)
--
-- Stores inbound email messages delivered by the CF Email Worker.
-- Each row is one email (a thread is identified by the same ticket_id).
-- Thread linking uses standard email In-Reply-To / References headers.
--
-- Integration flow:
--   CF Email Worker → POST /internal/email/inbound
--     → InboundEmailProcessor
--       → upsert support_ticket (by messageId thread or new)
--       → INSERT email_threads
--       → ChatwootService.createConversation()
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS email_threads (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Ticket linkage
    ticket_id       UUID            REFERENCES support_tickets(id) ON DELETE SET NULL,

    -- RFC 2822 message identifiers for thread linking
    message_id      TEXT            UNIQUE,           -- <msg-id@domain> from Message-ID header
    in_reply_to     TEXT,                             -- In-Reply-To header value
    references      TEXT,                             -- References header (space-separated IDs)

    -- Sender / recipient
    from_address    TEXT            NOT NULL,
    from_name       TEXT,
    to_address      TEXT            NOT NULL,

    -- Content
    subject         TEXT            NOT NULL,
    body_text       TEXT,
    body_html       TEXT,

    -- Chatwoot conversation linkage
    chatwoot_conversation_id  INTEGER,

    -- Timestamps
    received_at     TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes for thread lookup
CREATE INDEX idx_email_threads_ticket_id    ON email_threads(ticket_id);
CREATE INDEX idx_email_threads_message_id   ON email_threads(message_id) WHERE message_id IS NOT NULL;
CREATE INDEX idx_email_threads_from_address ON email_threads(from_address);
CREATE INDEX idx_email_threads_received_at  ON email_threads(received_at DESC);
