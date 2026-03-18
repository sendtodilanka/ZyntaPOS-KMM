-- V21: Add parent_thread_id for reply-to-reply chain tracking in email threads.
-- Enables nested conversation rendering in the admin panel.

ALTER TABLE email_threads
    ADD COLUMN parent_thread_id UUID REFERENCES email_threads(id) ON DELETE SET NULL;

CREATE INDEX idx_email_threads_parent ON email_threads(parent_thread_id);
