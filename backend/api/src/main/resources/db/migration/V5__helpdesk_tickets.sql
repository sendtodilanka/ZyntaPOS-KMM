-- ═══════════════════════════════════════════════════════════════════
-- ZyntaPOS API — V5: Helpdesk Support Tickets
-- Timestamps stored as BIGINT epoch-ms (consistent with V2 pattern)
-- ═══════════════════════════════════════════════════════════════════

-- ── Support Tickets ─────────────────────────────────────────────────
CREATE TABLE support_tickets (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number       TEXT        NOT NULL UNIQUE,       -- TKT-YYYY-NNNNNN
    store_id            TEXT,                              -- FK to stores.id (nullable — not all tickets are store-linked)
    license_id          TEXT,                              -- FK to licenses.key (nullable)
    created_by          UUID        NOT NULL REFERENCES admin_users(id),
    customer_name       TEXT        NOT NULL,
    customer_email      TEXT,
    customer_phone      TEXT,
    assigned_to         UUID        REFERENCES admin_users(id),
    assigned_at         BIGINT,                            -- epoch-ms
    title               TEXT        NOT NULL,
    description         TEXT        NOT NULL DEFAULT '',
    category            TEXT        NOT NULL CHECK (category IN ('HARDWARE', 'SOFTWARE', 'SYNC', 'BILLING', 'OTHER')),
    priority            TEXT        NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status              TEXT        NOT NULL DEFAULT 'OPEN'
                                    CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED')),
    resolved_by         UUID        REFERENCES admin_users(id),
    resolved_at         BIGINT,                            -- epoch-ms
    resolution_note     TEXT,
    time_spent_min      INTEGER,                           -- minutes spent resolving
    sla_due_at          BIGINT,                            -- epoch-ms, computed from priority
    sla_breached        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          BIGINT      NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000,
    updated_at          BIGINT      NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000
);

CREATE INDEX idx_support_tickets_status      ON support_tickets(status);
CREATE INDEX idx_support_tickets_priority    ON support_tickets(priority);
CREATE INDEX idx_support_tickets_created_by  ON support_tickets(created_by);
CREATE INDEX idx_support_tickets_assigned_to ON support_tickets(assigned_to);
CREATE INDEX idx_support_tickets_store_id    ON support_tickets(store_id);
CREATE INDEX idx_support_tickets_created_at  ON support_tickets(created_at DESC);

-- ── Ticket Comments ─────────────────────────────────────────────────
CREATE TABLE ticket_comments (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID    NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id   UUID    NOT NULL REFERENCES admin_users(id),
    body        TEXT    NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,            -- TRUE = hidden from customer view
    created_at  BIGINT  NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000
);

CREATE INDEX idx_ticket_comments_ticket_id ON ticket_comments(ticket_id);
CREATE INDEX idx_ticket_comments_author_id ON ticket_comments(author_id);

-- ── Ticket Attachments ──────────────────────────────────────────────
CREATE TABLE ticket_attachments (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID    NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    uploaded_by     UUID    NOT NULL REFERENCES admin_users(id),
    file_name       TEXT    NOT NULL,
    file_url        TEXT    NOT NULL,
    attachment_type TEXT    NOT NULL DEFAULT 'FILE',       -- FILE | IMAGE | LOG
    created_at      BIGINT  NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW()) * 1000
);

CREATE INDEX idx_ticket_attachments_ticket_id ON ticket_attachments(ticket_id);

-- ── Ticket number sequence ──────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS ticket_seq START 1;
