# TODO-008a — Enterprise Email Management System

**Phase:** 2 — Growth
**Priority:** P1 (HIGH — required before enterprise customer onboarding)
**Status:** 🟢 ~98% COMPLETE — All backend code, infrastructure, and DNS records deployed and verified. Inbound and outbound email working end-to-end. Updated 2026-03-17.
- ✅ `InboundEmailProcessor` — HMAC verification, deduplication, ticket threading, auto-reply
- ✅ `POST /internal/email/inbound` route — HMAC-signed, outside JWT auth
- ✅ `ChatwootService` — Chatwoot REST API client for conversation creation
- ✅ `PlayIntegrityService` + `POST /v1/integrity/verify` — Android attestation (soft enforcement)
- ✅ V18 Flyway migration — `email_threads` table with ticket FK, message threading, Chatwoot conversation ID
- ✅ `EmailThreads` Exposed table object in `db/Tables.kt`
- ✅ CF Email Worker — `cf-workers/email-inbound-handler/` (TypeScript, HMAC-signed, MIME parsing, HTTP relay delivery)
- ✅ Stalwart + Chatwoot + email-relay Docker services in `docker-compose.yml`
- ✅ `stalwart/config/config.toml` — Stalwart mail server configuration
- ✅ `backend/email-relay/relay.py` — HTTP-to-SMTP bridge (bypasses VPS port 25 block)
- ✅ `backend/postgres/init-databases.sh` — `zyntapos_chatwoot` database created
- ✅ `Caddyfile` — `mail.zyntapos.com` + `support.zyntapos.com` reverse proxy + `/relay/*` endpoint
- ✅ `AppConfig.kt` — HMAC secret + Chatwoot + Play Integrity env vars added
- ✅ `di/AppModule.kt` — `PlayIntegrityService`, `ChatwootService`, `InboundEmailProcessor` registered
- ✅ DNS records — MX, SPF (`-all`), DKIM (RSA `202603r` + Ed25519 `202603e`), DMARC (`quarantine`) all published
- ✅ Stalwart deployed — accounts created, domains configured, IMAP/SMTP working
- ✅ CF Worker deployed — `email-inbound-handler` with `deliverViaRelay()` + `EMAIL_RELAY_SECRET`
- ✅ Inbound email verified — Gmail → dilanka@zyntapos.com ✓
- ✅ Outbound email verified — zyntapos.com → Gmail (inbox, not spam) ✓
- ⏳ Chatwoot first-run — requires `support.zyntapos.com` setup + API token retrieval (see manual steps)
- ⏳ Admin panel email delivery log UI (`admin-panel/src/routes/settings/email.tsx`)
**Effort:** ~7 working days (1 developer)
**Related:** TODO-007f (admin panel + HELPDESK role + ticket system), TODO-009 (Ktor security hardening), TODO-010 (security monitoring)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-17

---

## 1. Overview

ZyntaPOS is targeting enterprise-grade customers (retail chains, franchise operations). An enterprise email
system is table-stakes: customers expect to reach support at `support@zyntapos.com`, staff need
`name@zyntapos.com` addresses, and every platform event (license expiry, sync failure, ticket update)
should trigger a well-designed email notification.

### Current State

- No `@zyntapos.com` email infrastructure exists
- `EmailPort` in `:shared:domain` handles on-device POS receipt emails only (Android Intent / javax.mail)
- No server-side email sending or receiving
- Support tickets (TODO-007f V6) have no email notifications
- License expiry, sync failure alerts are in-app only — no email channel

### Target State

```
Company Email (@zyntapos.com)
  ├── Staff mailboxes — name@zyntapos.com (IMAP/SMTP via Stalwart)
  ├── Support inbox  — support@zyntapos.com → Chatwoot → auto-creates ticket
  ├── Billing inbox  — billing@zyntapos.com → Chatwoot → FINANCE team
  ├── Bugs inbox     — bugs@zyntapos.com → Chatwoot → OPERATOR team
  └── Transactional  — noreply@, panel@, alerts@ (outbound only via Stalwart SMTP)

Integration Chain
  Customer email → CF Email Routing → CF Worker → ZyntaPOS API → support_ticket created
  Ticket event → StalwartEmailService → JMAP API → Email delivered to customer
  Admin panel → Email Settings page → delivery log, template editor (ADMIN only)
```

### Why This Architecture? (Not Google Workspace / Microsoft 365)

| Option | Cost | Control | API | Self-hosted | Verdict |
|--------|------|---------|-----|-------------|---------|
| Google Workspace | $6/user/month | None | Limited | No | ❌ Vendor lock-in, recurring cost |
| Microsoft 365 | $6/user/month | None | MS Graph | No | ❌ Same problem |
| Mailu (self-hosted) | Free | Full | None | Yes | ❌ No REST API, conflicts with CF Email Routing |
| Postal (self-hosted) | Free | Full | Yes | Yes | ⚠️ Heavy: requires MySQL + RabbitMQ + Redis |
| **Stalwart + Chatwoot** | Free | Full | JMAP + REST | Yes | ✅ Recommended |

**Stalwart Mail Server** — Rust-based, ~100MB RAM, SMTP/IMAP/JMAP REST API. Official Docker image.
**Chatwoot** — MIT licensed customer support platform with shared inbox, REST API, webhooks. Official Docker Compose.
**Cloudflare Email Routing** — Free MX routing layer (already have CF), routes @zyntapos.com emails to destinations.

---

## 2. Architecture

### Full System Diagram

```
                         INBOUND FLOW
                         ────────────
Customer/Staff
  sends email to
  @zyntapos.com
       │
       ▼
Cloudflare Edge (MX records: route1.mx.cloudflare.net)
  Cloudflare Email Routing (FREE — no server needed)
       │
       ├── support@, billing@, bugs@ ──────────────────────────────────────►
       │                                                                    │
       │    CF Email Worker (TypeScript, ~60 lines)                        │
       │      • Parses email headers + body                                │
       │      • HMAC-signs payload                                         │
       │      • POST /internal/email/inbound                               │
       │                                                                    ▼
       │                                              ZyntaPOS API (Ktor)
       │                                                InboundEmailProcessor
       │                                                  • Match customer by email
       │                                                  • Upsert support_ticket
       │                                                  • Store in email_threads
       │                                                  • Notify via Chatwoot API
       │                                                    ▼
       │                                              Chatwoot
       │                                                HELPDESK agent sees conversation
       │                                                Agent replies → customer receives
       │
       └── name@zyntapos.com ─────────────────────────────────────────────►
                                                                           │
                                                              Stalwart Mail Server
                                                                IMAP mailbox delivery
                                                                Staff reads via email client

                         OUTBOUND FLOW
                         ─────────────
ZyntaPOS API (Ktor)
  StalwartEmailService
    • Sends via JMAP REST API → http://stalwart:8080
       │
       ├── noreply@zyntapos.com → License expiry warnings, sync failure alerts
       ├── panel@zyntapos.com  → Admin invite, MFA codes, password reset
       └── support@zyntapos.com → Ticket created/assigned/resolved confirmations
```

### Service Responsibilities

| Service | Role | Memory |
|---------|------|--------|
| **Stalwart** | SMTP/IMAP infrastructure for @zyntapos.com. JMAP API for programmatic sending from Ktor. | ~100MB |
| **Chatwoot (web)** | Support team shared inbox UI. Receives conversations from Ktor. REST API for automation. | ~1GB |
| **Chatwoot (worker)** | Background jobs: email polling, webhook delivery, notification sending. | ~512MB |
| **CF Email Routing** | Free MX layer — routes @zyntapos.com inbound email. No Docker required. | 0MB |
| **CF Email Worker** | TypeScript Worker deployed at CF edge — parses inbound, POSTs to API. | 0MB |

**Total new VPS memory:** ~1.6GB (within 8GB budget — 5GB+ headroom remains)

---

## 3. Email Address Map

```
@zyntapos.com addresses
│
├── STAFF MAILBOXES (Stalwart IMAP — provisioned per employee)
│   ├── name@zyntapos.com           → Individual staff mailbox (IMAP client access)
│   ├── admin@zyntapos.com          → ADMIN team alias
│   └── ops@zyntapos.com            → OPERATOR team alias
│
├── SUPPORT INBOXES (CF Email Routing → Chatwoot)
│   ├── support@zyntapos.com        → HELPDESK team (primary customer-facing)
│   ├── billing@zyntapos.com        → FINANCE team (billing disputes, invoice requests)
│   └── bugs@zyntapos.com           → OPERATOR team (bug reports, technical issues)
│
├── TRANSACTIONAL (Stalwart SMTP — outbound only, no inbound expected)
│   ├── noreply@zyntapos.com        → License alerts, sync failures, system notifications
│   ├── panel@zyntapos.com          → Admin panel: login codes, invites, MFA backup
│   └── alerts@zyntapos.com         → Internal ops alerts (CF Worker → API webhook)
│
└── POSTMASTER (Stalwart — required for DMARC/bounce handling)
    └── postmaster@zyntapos.com     → Bounce reports, DMARC aggregate reports
```

---

## 4. Infrastructure Setup (Day 1–2)

### 4.1 DNS Records (Cloudflare Dashboard)

```
# MX — Cloudflare Email Routing handles inbound
MX  zyntapos.com  route1.mx.cloudflare.net  (priority 13)
MX  zyntapos.com  route2.mx.cloudflare.net  (priority 86)

# A — VPS IP for outbound SMTP reputation (PTR must match)
A   mail  <VPS_IP>   # e.g. 123.45.67.89 (Contabo VPS — PTR set in Contabo panel)

# SPF — authorize Stalwart VPS as sender
TXT  zyntapos.com  "v=spf1 ip4:<VPS_IP> ~all"

# DKIM — add DKIM public key from Stalwart admin console
TXT  stalwart._domainkey.zyntapos.com  "v=DKIM1; k=rsa; p=<STALWART_DKIM_PUBLIC_KEY>"

# DMARC — quarantine policy, aggregate reports to postmaster
TXT  _dmarc.zyntapos.com  "v=DMARC1; p=quarantine; rua=mailto:postmaster@zyntapos.com; ruf=mailto:postmaster@zyntapos.com; adkim=r; aspf=r"
```

> **PTR Record:** Set reverse DNS for `<VPS_IP>` → `mail.zyntapos.com` in Contabo control panel.
> This is critical for outbound email deliverability — many spam filters reject mail without valid PTR.

### 4.2 Caddy Reverse Proxy Additions

```caddyfile
# Add to existing Caddyfile

support.zyntapos.com {
  reverse_proxy chatwoot-web:3002
  # Chatwoot handles its own WebSocket upgrade for real-time
}

mail.zyntapos.com {
  reverse_proxy stalwart:8080
  # Stalwart admin console + JMAP API (JMAP is HTTP-based)
}
```

### 4.3 Docker Compose Additions

```yaml
# Add to docker-compose.yml

services:

  # ── Stalwart Mail Server ────────────────────────────────────────────
  stalwart:
    image: stalwartlabs/mail-server:latest
    container_name: stalwart
    restart: unless-stopped
    ports:
      - "25:25"         # SMTP (inbound from internet — must be open on VPS firewall)
      - "587:587"       # SMTP submission (outbound from apps, STARTTLS)
      - "465:465"       # SMTPS (outbound, TLS-wrapped)
      - "993:993"       # IMAPS (staff email clients — Thunderbird, Apple Mail, etc.)
      - "4190:4190"     # ManageSieve (server-side mail filtering rules)
    expose:
      - "8080"          # JMAP API — internal only, accessed via Caddy at mail.zyntapos.com
    volumes:
      - stalwart_data:/opt/stalwart-mail
      - ./config/stalwart/config.toml:/opt/stalwart-mail/etc/config.toml:ro
    environment:
      TZ: Asia/Colombo
    mem_limit: 256m
    networks:
      - zynta_net

  # ── Chatwoot Web (Rails) ─────────────────────────────────────────────
  chatwoot-web:
    image: chatwoot/chatwoot:latest
    container_name: chatwoot-web
    restart: unless-stopped
    command: bundle exec rails s -b 0.0.0.0 -p 3002
    expose:
      - "3002"
    environment:
      DATABASE_URL: postgres://chatwoot:${CHATWOOT_DB_PASS}@postgres:5432/zyntapos_chatwoot
      REDIS_URL: redis://redis:6379/2
      SECRET_KEY_BASE: ${CHATWOOT_SECRET}
      FRONTEND_URL: https://support.zyntapos.com
      DEFAULT_LOCALE: en
      INSTALLATION_ENV: self_hosted
      SMTP_ADDRESS: stalwart
      SMTP_PORT: 587
      SMTP_USERNAME: chatwoot@zyntapos.com
      SMTP_PASSWORD: ${CHATWOOT_SMTP_PASS}
      SMTP_DOMAIN: zyntapos.com
      SMTP_AUTHENTICATION: plain
      SMTP_ENABLE_STARTTLS_AUTO: "true"
      MAILER_SENDER_EMAIL: support@zyntapos.com
    depends_on:
      - postgres
      - redis
      - stalwart
    mem_limit: 1g
    networks:
      - zynta_net

  # ── Chatwoot Worker (Sidekiq) ────────────────────────────────────────
  chatwoot-worker:
    image: chatwoot/chatwoot:latest
    container_name: chatwoot-worker
    restart: unless-stopped
    command: bundle exec sidekiq -C config/sidekiq.yml
    environment:
      DATABASE_URL: postgres://chatwoot:${CHATWOOT_DB_PASS}@postgres:5432/zyntapos_chatwoot
      REDIS_URL: redis://redis:6379/2
      SECRET_KEY_BASE: ${CHATWOOT_SECRET}
      SMTP_ADDRESS: stalwart
      SMTP_PORT: 587
      SMTP_USERNAME: chatwoot@zyntapos.com
      SMTP_PASSWORD: ${CHATWOOT_SMTP_PASS}
    depends_on:
      - chatwoot-web
    mem_limit: 512m
    networks:
      - zynta_net

volumes:
  stalwart_data:
```

### 4.4 Stalwart Configuration

```toml
# config/stalwart/config.toml (minimal — full config generated by setup wizard)

[server]
hostname = "mail.zyntapos.com"

[server.listener.smtp]
bind = ["0.0.0.0:25"]
protocol = "smtp"

[server.listener.submission]
bind = ["0.0.0.0:587"]
protocol = "smtp"
tls.implicit = false   # STARTTLS

[server.listener.imaps]
bind = ["0.0.0.0:993"]
protocol = "imap"
tls.implicit = true

[server.listener.jmap]
bind = ["0.0.0.0:8080"]
protocol = "http"
# Internal only — Caddy proxies this at mail.zyntapos.com

[storage]
data = "rocksdb"       # Built-in embedded DB — no external DB required

[directory]
type = "internal"      # Built-in user directory (no LDAP needed at this scale)

[auth.dkim]
sign = ["rsa-2048"]    # Auto-generates DKIM key on first start

[spam]
enabled = true
```

### 4.5 init-databases.sh Addition

```bash
# Add to backend/postgres/init-databases.sh
# ADR-007: database-per-service

if ! psql -U "$POSTGRES_USER" -lqt | cut -d \| -f 1 | grep -qw zyntapos_chatwoot; then
    psql -U "$POSTGRES_USER" -c "CREATE DATABASE zyntapos_chatwoot;"
    psql -U "$POSTGRES_USER" -c "CREATE USER chatwoot WITH ENCRYPTED PASSWORD '$CHATWOOT_DB_PASS';"
    psql -U "$POSTGRES_USER" -c "GRANT ALL PRIVILEGES ON DATABASE zyntapos_chatwoot TO chatwoot;"
    echo "Created database: zyntapos_chatwoot"
fi
```

---

## 5. Database Changes (Day 6)

### 5.1 V7 Migration — Email Threading

```sql
-- File: backend/api/src/main/resources/db/migration/V7__email_threading.sql

-- ── Email threads linked to support tickets ───────────────────────────
CREATE TABLE email_threads (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id         UUID REFERENCES support_tickets(id) ON DELETE SET NULL,
    message_id        TEXT NOT NULL UNIQUE,   -- RFC 2822 Message-ID header
    in_reply_to       TEXT,                   -- Parent Message-ID (for threading)
    thread_id         TEXT NOT NULL,          -- Shared across all emails in same thread
    direction         TEXT NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    from_address      TEXT NOT NULL,
    from_name         TEXT,
    to_address        TEXT NOT NULL,
    cc_addresses      TEXT[],
    subject           TEXT NOT NULL,
    body_text         TEXT,
    body_html         TEXT,
    attachments       JSONB,                  -- [{filename, url, size, content_type}]
    chatwoot_convo_id INTEGER,                -- Cross-ref to Chatwoot conversation ID
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_threads_ticket_id  ON email_threads(ticket_id);
CREATE INDEX idx_email_threads_thread_id  ON email_threads(thread_id);
CREATE INDEX idx_email_threads_message_id ON email_threads(message_id);
CREATE INDEX idx_email_threads_direction  ON email_threads(direction);

-- ── Email notification preferences per admin user ─────────────────────
ALTER TABLE admin_users
    ADD COLUMN IF NOT EXISTS email_notifications JSONB NOT NULL DEFAULT '{
        "ticket_assigned": true,
        "ticket_resolved": true,
        "ticket_sla_breach": true,
        "license_expiry_warning": true,
        "sync_failure": true,
        "daily_digest": false
    }'::JSONB;

-- ── Transactional email delivery audit log ────────────────────────────
CREATE TABLE email_delivery_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    to_address          TEXT NOT NULL,
    from_address        TEXT NOT NULL,
    subject             TEXT NOT NULL,
    template_slug       TEXT,                 -- e.g. 'ticket_created', 'license_expiry'
    entity_type         TEXT,                 -- 'ticket', 'license', 'admin_user', 'alert'
    entity_id           UUID,
    status              TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN (
                          'QUEUED', 'SENDING', 'SENT', 'DELIVERED', 'BOUNCED', 'FAILED')),
    stalwart_message_id TEXT,                 -- JMAP response message ID
    error_message       TEXT,
    attempts            INTEGER NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_log_status      ON email_delivery_log(status);
CREATE INDEX idx_email_log_entity      ON email_delivery_log(entity_type, entity_id);
CREATE INDEX idx_email_log_created_at  ON email_delivery_log(created_at);

-- ── Email templates (managed in admin panel) ──────────────────────────
CREATE TABLE email_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            TEXT NOT NULL UNIQUE,     -- 'ticket_created', 'license_expiry_warning'
    display_name    TEXT NOT NULL,
    subject_tpl     TEXT NOT NULL,            -- Mustache template e.g. "Ticket {{ticket_number}} created"
    body_html_tpl   TEXT NOT NULL,            -- Mustache HTML template
    body_text_tpl   TEXT NOT NULL,            -- Mustache plain text fallback
    variables       JSONB NOT NULL DEFAULT '{}', -- Schema: {name: description}
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed built-in templates (UPDATE only — never replace ADMIN-edited templates)
INSERT INTO email_templates (slug, display_name, subject_tpl, body_html_tpl, body_text_tpl, variables)
VALUES
  ('ticket_created',
   'Ticket Created (Customer Auto-Reply)',
   'We received your request — {{ticket_number}}',
   '<p>Hi {{customer_name}},</p><p>We''ve received your support request and assigned it <strong>{{ticket_number}}</strong>. Our team will respond within {{sla_hours}} hours.</p><p>— ZyntaPOS Support</p>',
   'Hi {{customer_name}},\n\nWe received your request ({{ticket_number}}). Response within {{sla_hours}} hours.\n\n— ZyntaPOS Support',
   '{"customer_name": "Recipient name", "ticket_number": "TKT-YYYY-NNNNNN", "sla_hours": "SLA response window in hours"}'
  ),
  ('ticket_assigned',
   'Ticket Assigned (Agent Notification)',
   '[{{ticket_number}}] Ticket assigned to you',
   '<p>Hi {{agent_name}},</p><p>Ticket <a href="{{ticket_url}}">{{ticket_number}}</a> — "{{ticket_title}}" has been assigned to you. Priority: <strong>{{priority}}</strong>. SLA due: {{sla_due_at}}.</p>',
   'Hi {{agent_name}},\n\nTicket {{ticket_number}} ("{{ticket_title}}") assigned to you.\nPriority: {{priority}} | SLA due: {{sla_due_at}}\n{{ticket_url}}',
   '{"agent_name": "Agent full name", "ticket_number": "TKT-YYYY-NNNNNN", "ticket_title": "Ticket title", "priority": "LOW/MEDIUM/HIGH/CRITICAL", "sla_due_at": "ISO8601 datetime", "ticket_url": "Admin panel deep link"}'
  ),
  ('ticket_resolved',
   'Ticket Resolved (Customer Notification)',
   'Your issue has been resolved — {{ticket_number}}',
   '<p>Hi {{customer_name}},</p><p>Your support request <strong>{{ticket_number}}</strong> has been resolved.</p><p><strong>Resolution:</strong> {{resolution_note}}</p><p>If the issue persists, reply to this email and we''ll reopen your ticket.</p>',
   'Hi {{customer_name}},\n\nTicket {{ticket_number}} has been resolved.\n\nResolution: {{resolution_note}}\n\nStill having issues? Reply to this email.',
   '{"customer_name": "Recipient name", "ticket_number": "TKT-YYYY-NNNNNN", "resolution_note": "OPERATOR resolution summary"}'
  ),
  ('license_expiry_warning',
   'License Expiry Warning',
   'Your ZyntaPOS license expires in {{days_remaining}} days',
   '<p>Hi {{store_name}},</p><p>Your ZyntaPOS license for <strong>{{license_key}}</strong> will expire on <strong>{{expiry_date}}</strong> — {{days_remaining}} days from now.</p><p>To avoid service interruption, please renew at <a href="{{renewal_url}}">{{renewal_url}}</a>.</p>',
   'Hi {{store_name}},\n\nLicense {{license_key}} expires on {{expiry_date}} ({{days_remaining}} days).\nRenew: {{renewal_url}}',
   '{"store_name": "Store display name", "license_key": "LICENSE-XXXX", "expiry_date": "YYYY-MM-DD", "days_remaining": "Integer", "renewal_url": "Renewal page URL"}'
  ),
  ('sync_failure_alert',
   'Sync Failure Alert (Operator)',
   '[ALERT] Store sync failure — {{store_name}}',
   '<p>Store <strong>{{store_name}}</strong> ({{store_id}}) failed to sync at {{failed_at}}.</p><p><strong>Error:</strong> {{error_message}}</p><p>Check the <a href="{{sync_dashboard_url}}">sync dashboard</a> for details.</p>',
   '[ALERT] Store {{store_name}} ({{store_id}}) sync failed at {{failed_at}}.\n\nError: {{error_message}}\n\nDashboard: {{sync_dashboard_url}}',
   '{"store_name": "Store name", "store_id": "Store UUID", "failed_at": "ISO8601 datetime", "error_message": "Error details", "sync_dashboard_url": "Admin panel sync URL"}'
  ),
  ('admin_invite',
   'Admin Panel Invitation',
   'You''ve been invited to ZyntaPOS Admin Panel',
   '<p>Hi {{invitee_name}},</p><p><strong>{{inviter_name}}</strong> has invited you to the ZyntaPOS Admin Panel as <strong>{{role}}</strong>.</p><p><a href="{{accept_url}}">Accept Invitation</a> — link expires in 48 hours.</p>',
   'Hi {{invitee_name}},\n\n{{inviter_name}} invited you to ZyntaPOS Admin Panel as {{role}}.\n\nAccept: {{accept_url}}\nExpires in 48 hours.',
   '{"invitee_name": "New user name", "inviter_name": "Admin who sent invite", "role": "ADMIN/OPERATOR/etc", "accept_url": "One-time invite URL"}'
  ),
  ('mfa_backup_code',
   'MFA Backup Login Code',
   'Your ZyntaPOS login code: {{code}}',
   '<p>Your one-time login code for ZyntaPOS Admin Panel:</p><h1 style="letter-spacing:4px">{{code}}</h1><p>This code expires in 10 minutes. Do not share it.</p>',
   'Your ZyntaPOS login code: {{code}}\n\nExpires in 10 minutes. Do not share.',
   '{"code": "6-digit OTP code"}'
  )
ON CONFLICT (slug) DO NOTHING;
```

---

## 6. Backend Changes (Day 5–6)

### 6.1 New Koin Module

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/di/EmailModule.kt

val emailModule = module {
    single {
        StalwartEmailService(
            httpClient = get(),
            jmapEndpoint = getProperty("STALWART_JMAP_ENDPOINT"),  // http://stalwart:8080
            jmapToken = getProperty("STALWART_JMAP_TOKEN")
        )
    }
    single {
        MustacheTemplateEngine(
            templateRepository = get()  // loads from email_templates table
        )
    }
    single {
        EmailNotificationService(
            emailService = get(),
            templateEngine = get(),
            deliveryLogRepository = get()
        )
    }
    single {
        InboundEmailProcessor(
            ticketRepository = get(),
            emailThreadRepository = get(),
            chatwootClient = get(),
            notificationService = get()
        )
    }
    single {
        ChatwootClient(
            httpClient = get(),
            baseUrl = getProperty("CHATWOOT_BASE_URL"),    // http://chatwoot-web:3002
            apiToken = getProperty("CHATWOOT_API_TOKEN"),  // From GitHub Secrets
            accountId = getProperty("CHATWOOT_ACCOUNT_ID")
        )
    }
}
```

### 6.2 StalwartEmailService

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/email/StalwartEmailService.kt
//
// Sends transactional emails via Stalwart JMAP REST API.
// JMAP spec: RFC 8620 + RFC 8621 (Email)

class StalwartEmailService(
    private val httpClient: HttpClient,
    private val jmapEndpoint: String,
    private val jmapToken: String
) {
    // POST /jmap — JMAP method call
    suspend fun send(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String,
        from: String = "noreply@zyntapos.com",
        fromName: String = "ZyntaPOS"
    ): Result<String>  // Returns Message-ID on success

    // Convenience: reply to customer on behalf of ticket
    suspend fun sendTicketReply(
        ticket: SupportTicket,
        agent: AdminUser,
        htmlReplyBody: String
    ): Result<String>  // Sends from support@zyntapos.com, In-Reply-To original message_id

    // Convenience: send from template
    suspend fun sendTemplate(
        to: String,
        templateSlug: String,
        variables: Map<String, String>,
        from: String = "noreply@zyntapos.com"
    ): Result<String>
}
```

### 6.3 InboundEmailProcessor

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/email/InboundEmailProcessor.kt
//
// Processes inbound emails from CF Worker webhook.
// Parses email → upserts ticket → notifies Chatwoot.

class InboundEmailProcessor(
    private val ticketRepository: TicketRepository,
    private val emailThreadRepository: EmailThreadRepository,
    private val chatwootClient: ChatwootClient,
    private val emailService: StalwartEmailService,
    private val templateEngine: MustacheTemplateEngine
) {
    // Entry point: called by POST /internal/email/inbound
    suspend fun process(inboundEmail: InboundEmailRequest): Result<ProcessedEmail> {
        // 1. Find existing open ticket by thread (In-Reply-To or Subject match)
        val existingTicket = findExistingTicket(inboundEmail)

        val ticket = if (existingTicket != null) {
            // Add comment to existing ticket
            ticketRepository.addComment(existingTicket.id, body = inboundEmail.bodyText, isInternal = false)
            existingTicket
        } else {
            // Create new ticket
            val newTicket = ticketRepository.create(
                customerEmail = inboundEmail.fromAddress,
                customerName = inboundEmail.fromName ?: inboundEmail.fromAddress,
                title = inboundEmail.subject,
                description = inboundEmail.bodyText ?: "",
                category = inferCategory(inboundEmail),  // heuristic: "billing" → BILLING etc
                priority = TicketPriority.MEDIUM,
                status = TicketStatus.OPEN
            )

            // Auto-reply to customer
            emailService.sendTemplate(
                to = inboundEmail.fromAddress,
                templateSlug = "ticket_created",
                variables = mapOf(
                    "customer_name" to (inboundEmail.fromName ?: "Customer"),
                    "ticket_number" to newTicket.ticketNumber,
                    "sla_hours" to "8"
                )
            )

            newTicket
        }

        // 2. Store email in email_threads
        emailThreadRepository.save(
            EmailThread(
                ticketId = ticket.id,
                messageId = inboundEmail.messageId,
                inReplyTo = inboundEmail.inReplyTo,
                threadId = inboundEmail.references?.firstOrNull() ?: inboundEmail.messageId,
                direction = EmailDirection.INBOUND,
                fromAddress = inboundEmail.fromAddress,
                fromName = inboundEmail.fromName,
                toAddress = inboundEmail.toAddress,
                subject = inboundEmail.subject,
                bodyText = inboundEmail.bodyText,
                bodyHtml = inboundEmail.bodyHtml
            )
        )

        // 3. Create/update Chatwoot conversation
        val chatwootConvoId = chatwootClient.upsertConversation(ticket, inboundEmail)
        emailThreadRepository.updateChatwootConvoId(inboundEmail.messageId, chatwootConvoId)

        return Result.success(ProcessedEmail(ticket.id, ticket.ticketNumber, isNew = existingTicket == null))
    }
}
```

### 6.4 EmailNotificationService

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/email/EmailNotificationService.kt
//
// Central hub: all system events that trigger emails go through here.

class EmailNotificationService(
    private val emailService: StalwartEmailService,
    private val templateEngine: MustacheTemplateEngine,
    private val deliveryLogRepo: EmailDeliveryLogRepository,
    private val adminUserRepo: AdminUserRepository
) {
    // Called by TicketService when ticket is assigned
    suspend fun onTicketAssigned(ticket: SupportTicket, assignedAgent: AdminUser) {
        if (assignedAgent.emailNotifications.ticketAssigned) {
            emailService.sendTemplate(
                to = assignedAgent.email,
                templateSlug = "ticket_assigned",
                variables = mapOf(
                    "agent_name" to assignedAgent.displayName,
                    "ticket_number" to ticket.ticketNumber,
                    "ticket_title" to ticket.title,
                    "priority" to ticket.priority.name,
                    "sla_due_at" to ticket.slaDueAt?.toString() ?: "N/A",
                    "ticket_url" to "https://panel.zyntapos.com/tickets/${ticket.id}"
                )
            )
        }
    }

    // Called by TicketService when ticket is resolved
    suspend fun onTicketResolved(ticket: SupportTicket, resolutionNote: String) {
        if (ticket.customerEmail != null) {
            emailService.sendTemplate(
                to = ticket.customerEmail,
                templateSlug = "ticket_resolved",
                variables = mapOf(
                    "customer_name" to ticket.customerName,
                    "ticket_number" to ticket.ticketNumber,
                    "resolution_note" to resolutionNote
                )
            )
        }
    }

    // Called by LicenseScheduler (cron job) — 30/7/1 days before expiry
    suspend fun onLicenseExpiring(license: License, daysRemaining: Int) {
        emailService.sendTemplate(
            to = license.contactEmail,
            templateSlug = "license_expiry_warning",
            variables = mapOf(
                "store_name" to license.storeName,
                "license_key" to license.licenseKey,
                "expiry_date" to license.expiresAt.date.toString(),
                "days_remaining" to daysRemaining.toString(),
                "renewal_url" to "https://zyntapos.com/renew?key=${license.licenseKey}"
            )
        )
    }

    // Called by SyncEngine when store sync fails
    suspend fun onSyncFailure(storeId: String, storeName: String, errorMessage: String) {
        // Notify all OPERATOR users who have sync_failure alerts enabled
        adminUserRepo.findByRolesWithNotificationEnabled(
            roles = listOf(AdminRole.ADMIN, AdminRole.OPERATOR),
            notificationKey = "sync_failure"
        ).forEach { operator ->
            emailService.sendTemplate(
                to = operator.email,
                templateSlug = "sync_failure_alert",
                variables = mapOf(
                    "store_name" to storeName,
                    "store_id" to storeId,
                    "failed_at" to Clock.System.now().toString(),
                    "error_message" to errorMessage,
                    "sync_dashboard_url" to "https://panel.zyntapos.com/stores/$storeId"
                )
            )
        }
    }

    // Called by AdminUserService when inviting a new admin user
    suspend fun onAdminUserInvited(inviter: AdminUser, invitee: InviteRequest, acceptUrl: String) {
        emailService.sendTemplate(
            to = invitee.email,
            templateSlug = "admin_invite",
            variables = mapOf(
                "invitee_name" to invitee.displayName,
                "inviter_name" to inviter.displayName,
                "role" to invitee.role.name,
                "accept_url" to acceptUrl
            )
        )
    }

    // Called by AuthService when MFA backup email is requested
    suspend fun onMfaBackupCodeRequested(adminUser: AdminUser, code: String) {
        emailService.sendTemplate(
            to = adminUser.email,
            templateSlug = "mfa_backup_code",
            variables = mapOf("code" to code),
            from = "panel@zyntapos.com"
        )
    }
}
```

### 6.5 New Backend Routes

```kotlin
// backend/api/src/main/kotlin/com/zyntasolutions/api/routes/EmailRoutes.kt

fun Route.emailRoutes() {
    // ── Internal (CF Worker webhook — not admin-authed, uses HMAC) ────
    post("/internal/email/inbound") {
        val hmacHeader = call.request.headers["Authorization"]
            ?: return@post call.respond(HttpStatusCode.Unauthorized)
        if (!hmacVerifier.verify(hmacHeader, call.receiveText())) {
            return@post call.respond(HttpStatusCode.Unauthorized)
        }
        val payload = call.receive<InboundEmailRequest>()
        inboundEmailProcessor.process(payload).fold(
            onSuccess = { call.respond(HttpStatusCode.OK, it) },
            onFailure = { call.respond(HttpStatusCode.InternalServerError) }
        )
    }

    // ── Admin routes (JWT-authed) ─────────────────────────────────────
    authenticate("admin-jwt") {

        route("/admin/email") {

            // Email settings (ADMIN only)
            get("/settings") {
                requirePermission("email:settings")
                call.respond(emailSettingsService.getSettings())
            }

            put("/settings") {
                requirePermission("email:settings")
                val body = call.receive<EmailSettingsRequest>()
                emailSettingsService.update(body)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/test") {
                requirePermission("email:settings")
                val body = call.receive<TestEmailRequest>()   // { to: string }
                emailService.send(
                    to = body.to,
                    subject = "[TEST] ZyntaPOS Email System",
                    htmlBody = "<p>Test email sent from ZyntaPOS admin panel.</p>",
                    textBody = "Test email sent from ZyntaPOS admin panel."
                )
                call.respond(HttpStatusCode.OK)
            }

            // Delivery log (ADMIN + OPERATOR)
            get("/delivery-log") {
                requirePermission("email:logs")
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val status = call.request.queryParameters["status"]
                call.respond(deliveryLogRepo.findAll(page = page, status = status))
            }

            // Template management (ADMIN only)
            get("/templates") {
                requirePermission("email:settings")
                call.respond(templateRepo.findAll())
            }

            get("/templates/{slug}") {
                requirePermission("email:settings")
                val slug = call.parameters["slug"]!!
                call.respond(templateRepo.findBySlug(slug) ?: return@get call.respond(HttpStatusCode.NotFound))
            }

            put("/templates/{slug}") {
                requirePermission("email:settings")
                val slug = call.parameters["slug"]!!
                val body = call.receive<UpdateTemplateRequest>()
                templateRepo.update(slug, body)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
```

### 6.6 Permissions Extension

Add to `AdminPermissions.kt` (2 new permissions, total → 39):

```kotlin
// In AdminPermissions.kt permissions map — add these entries:
"email:settings"  to setOf(ADMIN),
"email:logs"      to setOf(ADMIN, OPERATOR),
```

Add to `permissions.ts` in admin-panel:

```typescript
// In PERMISSIONS constant — add these entries:
'email:settings':  ['ADMIN'],
'email:logs':      ['ADMIN', 'OPERATOR'],
```

---

## 7. Cloudflare Email Worker (Day 4)

### 7.1 Worker Code

```typescript
// cloudflare-workers/email-inbound-handler.ts
// Deploy at: Cloudflare Dashboard → Email Routing → Email Workers

import { EmailMessage } from "cloudflare:email"
import { createMimeMessage } from "mimetext"

interface Env {
  INBOUND_HMAC_SECRET: string      // Workers secret — matches backend env var
  ZYNTA_API_ENDPOINT: string       // "https://api.zyntapos.com"
  CHATWOOT_INBOX_EMAIL: string     // "support@zyntapos.com"
}

// Addresses that trigger ticket creation
const SUPPORT_INBOXES = ["support@zyntapos.com", "billing@zyntapos.com", "bugs@zyntapos.com"]

export default {
  async email(message: ForwardableEmailMessage, env: Env, ctx: ExecutionContext) {
    const to = message.to.toLowerCase()
    const from = message.from.toLowerCase()

    if (SUPPORT_INBOXES.some(inbox => to === inbox || to.endsWith(`+${inbox}`))) {
      // Parse email to JSON payload
      const rawBody = await new Response(message.raw).text()
      const payload = {
        messageId: extractHeader(rawBody, "Message-ID"),
        inReplyTo: extractHeader(rawBody, "In-Reply-To"),
        references: extractHeader(rawBody, "References"),
        fromAddress: from,
        fromName: message.headers.get("From")?.split("<")[0]?.trim().replace(/"/g, "") ?? null,
        toAddress: to,
        subject: message.headers.get("Subject") ?? "(no subject)",
        bodyText: extractTextBody(rawBody),
        bodyHtml: extractHtmlBody(rawBody),
        receivedAt: new Date().toISOString()
      }

      // HMAC-SHA256 sign the payload for backend verification
      const payloadStr = JSON.stringify(payload)
      const signature = await sign(payloadStr, env.INBOUND_HMAC_SECRET)

      const resp = await fetch(`${env.ZYNTA_API_ENDPOINT}/internal/email/inbound`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `HMAC-SHA256 ${signature}`
        },
        body: payloadStr
      })

      if (!resp.ok) {
        // On API failure, forward to postmaster so email isn't lost
        await message.forward("postmaster@zyntapos.com")
        throw new Error(`API returned ${resp.status}`)
      }
    } else {
      // Staff mailbox — Stalwart handles IMAP delivery via direct MX
      // CF Email Routing forwards to Stalwart SMTP (configured as destination)
      await message.forward(to)
    }
  }
}

async function sign(payload: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  )
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload))
  return btoa(String.fromCharCode(...new Uint8Array(sig)))
}

function extractHeader(raw: string, name: string): string | null {
  const match = new RegExp(`^${name}:\\s*(.+)$`, "mi").exec(raw)
  return match ? match[1].trim() : null
}

function extractTextBody(raw: string): string | null {
  const match = /Content-Type: text\/plain[\s\S]*?\r?\n\r?\n([\s\S]*?)(?:\r?\n--|\r?\n$)/i.exec(raw)
  return match ? match[1].trim() : null
}

function extractHtmlBody(raw: string): string | null {
  const match = /Content-Type: text\/html[\s\S]*?\r?\n\r?\n([\s\S]*?)(?:\r?\n--|\r?\n$)/i.exec(raw)
  return match ? match[1].trim() : null
}
```

### 7.2 CF Email Routing Configuration

In Cloudflare Dashboard → Email Routing → Routes:

```
support@zyntapos.com  →  Send to Worker: email-inbound-handler
billing@zyntapos.com  →  Send to Worker: email-inbound-handler
bugs@zyntapos.com     →  Send to Worker: email-inbound-handler
alerts@zyntapos.com   →  Send to Worker: email-inbound-handler
*@zyntapos.com        →  Send to: stalwart (custom destination — VPS port 25)
```

> **Custom destination for Stalwart:** In CF Email Routing → Destinations → Add custom destination:
> `smtp://mail.zyntapos.com:25` — This routes non-support email directly to Stalwart IMAP.

---

## 8. Admin Panel Changes (Day 7)

### 8.1 New Routes

```
admin-panel/src/routes/
├── email/
│   ├── index.tsx              # Redirect → /email/settings
│   ├── settings/
│   │   └── index.tsx          # SMTP config, template list, delivery log
│   └── templates/
│       └── $slug.tsx          # Template editor (subject + HTML + text preview)
└── support/
    └── inbox/
        └── index.tsx          # Support inbox (link/embed to Chatwoot)
```

### 8.2 New Components

```
admin-panel/src/components/
├── email/
│   ├── EmailSettingsForm.tsx       # SMTP host/port/credentials form (ADMIN only)
│   ├── EmailTestPanel.tsx          # "Send test email" → shows ✅/❌ result
│   ├── EmailDeliveryTable.tsx      # Recent delivery log with status badges
│   └── EmailTemplateEditor.tsx     # View template variables, edit subject/body
└── support/
    └── SupportInboxLink.tsx        # Button → opens support.zyntapos.com in new tab
```

### 8.3 Navigation Update

Add to nav items in `NavigationItems.tsx`:

```typescript
// In the HELPDESK-visible nav section (requires 'tickets:read' permission):
{
  title: "Support Inbox",
  url: "/support/inbox",
  icon: InboxIcon,
  requiredPermission: "tickets:read"  // HELPDESK can access
},

// In the settings section (requires 'email:settings' or 'email:logs'):
{
  title: "Email",
  url: "/email/settings",
  icon: MailIcon,
  requiredPermission: "email:logs"   // ADMIN + OPERATOR can see
},
```

### 8.4 New API Hook

```typescript
// admin-panel/src/api/email.ts

export const emailApi = {
  getSettings: () =>
    apiClient.get<EmailSettings>("/admin/email/settings"),

  updateSettings: (data: UpdateEmailSettingsRequest) =>
    apiClient.put("/admin/email/settings", data),

  sendTestEmail: (to: string) =>
    apiClient.post("/admin/email/test", { to }),

  getDeliveryLog: (params: { page?: number; status?: string }) =>
    apiClient.get<PaginatedResponse<EmailDeliveryLogEntry>>("/admin/email/delivery-log", { params }),

  getTemplates: () =>
    apiClient.get<EmailTemplate[]>("/admin/email/templates"),

  getTemplate: (slug: string) =>
    apiClient.get<EmailTemplate>(`/admin/email/templates/${slug}`),

  updateTemplate: (slug: string, data: UpdateTemplateRequest) =>
    apiClient.put(`/admin/email/templates/${slug}`, data),
}
```

---

## 9. New GitHub Secrets

| Secret | Purpose | Added to |
|--------|---------|----------|
| `STALWART_ADMIN_PASSWORD` | Stalwart admin console login | VPS `.env` |
| `STALWART_JMAP_TOKEN` | API bearer token for Ktor → Stalwart | VPS `.env` + GH Secrets |
| `CHATWOOT_DB_PASS` | PostgreSQL password for `zyntapos_chatwoot` | VPS `.env` |
| `CHATWOOT_SECRET` | Rails `SECRET_KEY_BASE` (min 64 chars) | VPS `.env` |
| `CHATWOOT_SMTP_PASS` | SMTP password for Chatwoot → Stalwart | VPS `.env` |
| `CHATWOOT_API_TOKEN` | Chatwoot REST API access token | GH Secrets (API service uses) |
| `CHATWOOT_ACCOUNT_ID` | Chatwoot account ID (obtained post-setup) | VPS `.env` |
| `INBOUND_EMAIL_HMAC_SECRET` | CF Worker → ZyntaPOS API HMAC auth | CF Worker Secrets + VPS `.env` |

---

## 10. local.properties.template Additions

```properties
# Email (production values in GitHub Secrets + VPS .env)
STALWART_JMAP_ENDPOINT=http://localhost:8080  # local dev: run stalwart via Docker
STALWART_JMAP_TOKEN=dev-token-placeholder
CHATWOOT_BASE_URL=http://localhost:3002
CHATWOOT_API_TOKEN=dev-token-placeholder
CHATWOOT_ACCOUNT_ID=1
INBOUND_EMAIL_HMAC_SECRET=dev-secret-placeholder
```

---

## 11. Rollout Sequence

| Day | Tasks |
|-----|-------|
| **D1** | DNS: MX records (CF Email Routing), SPF, DKIM, DMARC, PTR (Contabo panel). Deploy Stalwart container. Run setup wizard. Verify `telnet mail.zyntapos.com 25` works. |
| **D2** | Stalwart: provision staff mailboxes (name@zyntapos.com). Verify send/receive via Thunderbird. Configure DKIM signing. Run dmarcanalyzer.com check. |
| **D3** | Deploy Chatwoot (web + worker). Create Chatwoot inboxes: support, billing, bugs. Configure SMTP → Stalwart. Test Chatwoot → customer email delivery. |
| **D4** | Write + deploy CF Email Worker (`email-inbound-handler.ts`). Configure CF Email Routing rules. Test: send to support@zyntapos.com → verify Worker fires → logs in API. |
| **D5** | Backend: `StalwartEmailService`, `InboundEmailProcessor`, `ChatwootClient`, `EmailNotificationService`. Wire into existing `TicketService`, `LicenseScheduler`, `SyncEngine`. |
| **D6** | V7 DB migration (email_threads, email_delivery_log, email_templates + seed). Add 2 new permissions to `AdminPermissions.kt` + `permissions.ts`. |
| **D7** | Admin panel: Email Settings route + components. Support Inbox link for HELPDESK. Add to nav. Integration test full flow end-to-end. |

---

## 12. Validation Checklist

### Infrastructure
- [ ] MX records visible via `dig MX zyntapos.com` → `route1.mx.cloudflare.net`
- [ ] SPF check: `dig TXT zyntapos.com` → includes VPS IP
- [ ] DKIM: `dig TXT stalwart._domainkey.zyntapos.com` → returns DKIM public key
- [ ] DMARC: `dig TXT _dmarc.zyntapos.com` → returns DMARC policy
- [ ] PTR: `dig -x <VPS_IP>` → `mail.zyntapos.com`
- [ ] dmarcanalyzer.com full score ≥ 8/10
- [ ] Stalwart JMAP reachable from API container: `curl http://stalwart:8080/.well-known/jmap`
- [ ] Chatwoot reachable at `https://support.zyntapos.com`

### Inbound Email Flow
- [ ] Send to `support@zyntapos.com` from external (Gmail) → CF Worker fires
- [ ] API receives inbound payload with valid HMAC signature
- [ ] New `support_ticket` row created with status=OPEN, correct customer_email
- [ ] `email_threads` row created with direction=INBOUND, message_id populated
- [ ] Chatwoot conversation created and visible in support team inbox
- [ ] Customer receives auto-reply from `noreply@zyntapos.com` using `ticket_created` template
- [ ] Reply to same thread → existing ticket gets new comment (not new ticket)

### Outbound Transactional
- [ ] `onTicketAssigned` → assigned OPERATOR receives email at their @zyntapos.com address
- [ ] `onTicketResolved` → customer receives `ticket_resolved` email with resolution note
- [ ] `onLicenseExpiring` (30 days) → license contact receives `license_expiry_warning` email
- [ ] `onSyncFailure` → all OPERATOR+ADMIN users with `sync_failure:true` receive alert email
- [ ] `onAdminUserInvited` → invitee receives `admin_invite` email with accept link
- [ ] `onMfaBackupCodeRequested` → admin user receives OTP code email within 60 seconds
- [ ] All sent emails logged in `email_delivery_log` with status=SENT

### Admin Panel
- [ ] ADMIN: Email Settings page accessible at `/email/settings`
- [ ] ADMIN: Send test email → `email_delivery_log` entry created, email received
- [ ] ADMIN: Template editor visible, subject/body editable, preview renders
- [ ] OPERATOR: `/email/settings` shows delivery log only (read-only, no edit)
- [ ] FINANCE/AUDITOR: `/email/settings` → 403
- [ ] HELPDESK: Support Inbox link visible in nav, redirects to Chatwoot
- [ ] HELPDESK: No access to `/email/settings` → 403
- [ ] Non-ADMIN: `PUT /admin/email/templates/{slug}` → 403

### Staff Email
- [ ] `dilanka@zyntapos.com` mailbox exists in Stalwart
- [ ] Staff can connect Thunderbird/Apple Mail via IMAP: `mail.zyntapos.com:993` (TLS)
- [ ] Staff can send from Stalwart via SMTP: `mail.zyntapos.com:587` (STARTTLS)
- [ ] Email from `dilanka@zyntapos.com` passes SPF + DKIM checks (check via mail-tester.com)

---

## 13. What Is NOT in Scope

| Excluded | Reason |
|----------|--------|
| Newsletter / marketing email | Phase 3+ — different compliance requirements (CAN-SPAM, opt-out) |
| POS customer receipt emails | Already handled by `EmailPort` on-device — no server-side change needed |
| Email archiving / legal hold | Phase 3 enterprise compliance |
| GDPR email data retention | Phase 3 — delete email threads when ticket deleted |
| WhatsApp / SMS notifications | Phase 3 — Chatwoot supports these channels as future upgrade |
| Built-in email client in admin panel | Staff use standard IMAP clients (Thunderbird, Apple Mail, Outlook) |
| Anti-spam / filtering for inbound | Stalwart has built-in spam filtering; Cloudflare WAF handles abuse at edge |
| Email encryption (S/MIME, PGP) | Phase 3+ — not required for standard support workflows |

---

## 14. Future Upgrade Path (Phase 3)

- **Chatwoot → WhatsApp channel:** Chatwoot supports WhatsApp Business API natively. Add `WhatsApp` to support inbox mix.
- **Stalwart horizontal scaling:** Stalwart supports cluster mode — add second node behind load balancer when volume demands it.
- **Marketing email:** Add a separate service (Listmonk — MIT, self-hosted, Docker-ready) purely for newsletters. Never use transactional email infrastructure for bulk mail.
- **GDPR right to erasure:** When a customer requests data deletion, cascade-delete `email_threads` where `from_address = customer_email` + anonymize `ticket` records.
- **Email analytics:** Stalwart + webhook-based event tracking → open rates, click rates, bounce rates in admin dashboard.

---

## 15. Manual Steps Required (External Actions)

> **All code is committed.** Complete these steps in order to bring the system fully live.
> After each step, verify it before proceeding to the next.

---

### Step A — Deploy to VPS (prerequisite for all other steps)

First push the code changes to trigger the CI/CD pipeline and deploy to VPS.

```bash
# Trigger re-deploy after this branch merges to main — no action needed
# Pipeline: Branch Validate → Auto PR → CI Gate → Deploy → Smoke Test
```

Wait for `cd-deploy.yml` to complete successfully before proceeding.

---

### Step B — DNS Records (Cloudflare Dashboard)

**B1. Open Cloudflare Dashboard → DNS → Records for `zyntapos.com`**

Add these records (replace `<VPS_IP>` with your Contabo VPS IP address):

| Type | Name | Content | TTL | Proxy |
|------|------|---------|-----|-------|
| `A` | `mail` | `<VPS_IP>` | Auto | **DNS only** (grey cloud — NOT proxied) |
| `TXT` | `zyntapos.com` | `v=spf1 ip4:<VPS_IP> ~all` | Auto | — |
| `TXT` | `_dmarc` | `v=DMARC1; p=quarantine; rua=mailto:postmaster@zyntapos.com; ruf=mailto:postmaster@zyntapos.com; adkim=r; aspf=r` | Auto | — |

> **Note:** DKIM TXT record is added in Step D after Stalwart generates the key.

**B2. Enable Cloudflare Email Routing**

1. In Cloudflare Dashboard → **Email** → **Email Routing** → Enable
2. Cloudflare will add its own MX records automatically (`route1.mx.cloudflare.net`, `route2.mx.cloudflare.net`)
3. Verify with: `dig MX zyntapos.com` → should show CF MX records within 5 minutes

**B3. Set PTR Record (Reverse DNS) in Contabo**

1. Log in to [Contabo Customer Panel](https://new.contabo.com/)
2. Go to **Your Services** → **VPS** → your server → **Manage** → **Reverse DNS**
3. Set: `<VPS_IP>` → `mail.zyntapos.com`
4. Save. PTR records propagate within 24 hours.
5. Verify: `dig -x <VPS_IP>` → should eventually return `mail.zyntapos.com`

**Verification:**
```bash
dig MX zyntapos.com        # → route1.mx.cloudflare.net, route2.mx.cloudflare.net
dig TXT zyntapos.com       # → includes "v=spf1 ip4:<VPS_IP> ~all"
dig TXT _dmarc.zyntapos.com  # → v=DMARC1; p=quarantine; ...
```

---

### Step C — Stalwart First-Run Setup

**C1. Verify Stalwart is running:**

```bash
# From VPS (via GitHub Actions ad-hoc workflow or direct SSH)
docker compose ps stalwart   # should be "Up"
curl http://localhost:8080/.well-known/jmap   # should return JSON
```

**C2. Set Stalwart admin password via VPS:**

Create a one-off GitHub Actions workflow dispatch:
```bash
# Workflow dispatch (from your local machine):
curl -s -X POST -H "Authorization: token $PAT" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/workflows/vps-adhoc.yml/dispatches" \
  -d '{"ref":"main","inputs":{"command":"cd /opt/zyntapos && docker compose exec stalwart /usr/local/bin/stalwart-mail --config /opt/stalwart-mail/etc/config.toml account add --name admin --secret YOUR_ADMIN_PASSWORD --role admin"}}'
```

Alternatively, access Stalwart's web admin at `https://mail.zyntapos.com` and complete setup via wizard.

**C3. Generate DKIM key in Stalwart admin:**

1. Open `https://mail.zyntapos.com` → log in as admin
2. Go to **Email** → **DKIM** → **Generate new key**
3. Key type: `RSA-2048`, Selector: `stalwart`
4. Copy the **public key** (base64 string)

**C4. Add DKIM TXT record to Cloudflare:**

```
Type: TXT
Name: stalwart._domainkey
Content: v=DKIM1; k=rsa; p=<PASTE_PUBLIC_KEY_HERE>
TTL: Auto
```

**C5. Create staff mailboxes in Stalwart:**

1. Open `https://mail.zyntapos.com` → **Accounts** → **Add account**
2. Create: `dilanka@zyntapos.com` (and any other staff members)
3. Create system accounts: `noreply@zyntapos.com`, `panel@zyntapos.com`, `postmaster@zyntapos.com`, `chatwoot@zyntapos.com`

**C6. Create JMAP API token for Ktor:**

1. In Stalwart admin → **API Tokens** → **Create token**
2. Name: `zyntapos-api`, Permissions: `email:send`
3. Copy the token value
4. Add to VPS `.env`: `STALWART_JMAP_TOKEN=<token>`

**Verification:**
```bash
# DKIM check
dig TXT stalwart._domainkey.zyntapos.com  # → returns DKIM public key

# Send test email from Stalwart admin → verify delivery
# Check mail-tester.com score (should be 8+/10)
```

---

### Step D — Chatwoot First-Run Setup

**D1. Verify Chatwoot is running:**

```bash
# Check services
curl -s https://support.zyntapos.com  # should load Chatwoot login page
```

**D2. Create Chatwoot superadmin:**

1. Open `https://support.zyntapos.com/auth/sign_up`
2. Create the first account — this becomes the superadmin
3. Email: use your admin email address

**D3. Create the Support inbox in Chatwoot:**

1. Go to **Settings** → **Inboxes** → **Add Inbox**
2. Select **Email**
3. Channel name: `ZyntaPOS Support`
4. Email: `support@zyntapos.com`
5. SMTP settings:
   - Host: `stalwart` (internal container name)
   - Port: `587`
   - Username: `chatwoot@zyntapos.com`
   - Password: `<CHATWOOT_SMTP_PASS>` (set in `.env`)
   - Enable STARTTLS: Yes
6. Save and note the **Inbox ID** (shown in the URL: `/app/accounts/1/settings/inboxes/<INBOX_ID>/settings`)

**D4. Create API token in Chatwoot:**

1. Go to **Settings** → **Integrations** → **API Access Tokens**
2. Click **Generate Access Token**
3. Name: `zyntapos-api`
4. Copy the token

**D5. Update VPS `.env` with Chatwoot credentials:**

```bash
# Add to /opt/zyntapos/.env on VPS:
CHATWOOT_API_TOKEN=<token-from-step-D4>
CHATWOOT_ACCOUNT_ID=1
CHATWOOT_INBOX_ID=<inbox-id-from-step-D3>
```

Then restart the API service:
```bash
docker compose restart api
```

**Verification:**
- Open Chatwoot at `https://support.zyntapos.com`
- Send a test email to `support@zyntapos.com` from Gmail
- Verify a new conversation appears in Chatwoot inbox within 30 seconds

---

### Step E — CF Email Worker Deployment

**E1. Install Wrangler CLI** (if not already):

```bash
npm install -g wrangler
wrangler login  # opens browser OAuth with your Cloudflare account
```

**E2. Set Worker secrets:**

```bash
cd cf-workers/email-inbound-handler

# Required secrets (Wrangler prompts for the value interactively):
wrangler secret put INBOUND_HMAC_SECRET
# → Enter the same value as INBOUND_EMAIL_HMAC_SECRET in your VPS .env

wrangler secret put ZYNTA_API_ENDPOINT
# → Enter: https://api.zyntapos.com

wrangler secret put STALWART_SMTP_HOST
# → Enter: mail.zyntapos.com
```

**E3. Deploy the Worker:**

```bash
cd cf-workers/email-inbound-handler
npm install
wrangler deploy
# → Deploys to: email-inbound-handler.<your-cf-subdomain>.workers.dev
```

**E4. Configure CF Email Routing rules:**

1. In Cloudflare Dashboard → **Email** → **Email Routing** → **Routes**
2. Add these rules:

| Match | Action |
|-------|--------|
| `support@zyntapos.com` | Send to Worker → `email-inbound-handler` |
| `billing@zyntapos.com` | Send to Worker → `email-inbound-handler` |
| `bugs@zyntapos.com` | Send to Worker → `email-inbound-handler` |
| `alerts@zyntapos.com` | Send to Worker → `email-inbound-handler` |
| `*@zyntapos.com` (catch-all) | Forward to: `smtp://mail.zyntapos.com:25` |

**E5. Add INBOUND_EMAIL_HMAC_SECRET to GitHub Secrets:**

1. Open GitHub → `sendtodilanka/ZyntaPOS-KMM` → **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Name: `INBOUND_EMAIL_HMAC_SECRET`
4. Value: same random secret used in step E2
5. Save

Also add to VPS `.env`: `INBOUND_EMAIL_HMAC_SECRET=<same-value>`

**Verification:**
```bash
# Send test email to support@zyntapos.com from Gmail
# Check API logs:
docker compose logs api --tail 50 | grep "Inbound email"
# Should show: "Inbound email stored — threadId=... ticketId=... from=your@gmail.com"
```

---

### Step F — End-to-End Validation

Run through the validation checklist in Section 12. Key tests to confirm manually:

1. **Full inbound flow:**
   - Send email to `support@zyntapos.com` → ticket created in admin panel → auto-reply arrives in Gmail inbox

2. **Thread linking:**
   - Reply to the auto-reply email → ticket gets a new comment (not a new ticket)

3. **Chatwoot sync:**
   - Open `https://support.zyntapos.com` → verify conversation appeared with email body

4. **Deliverability score:**
   - Go to [mail-tester.com](https://www.mail-tester.com) → get a test address
   - Send email from `dilanka@zyntapos.com` (via Stalwart SMTP in Thunderbird) to the mail-tester address
   - Score should be 8+/10

5. **DMARC report (after 24h):**
   - Check `postmaster@zyntapos.com` inbox for first DMARC aggregate report
   - All messages should show `dkim=pass` and `spf=pass`
