# TASK 012 — Support Ticket System Enhancements

**Status:** Pending
**Priority:** HIGH
**Branch:** `claude/kmp-vps-qa-guide-isrFg`

---

## Context

The support ticket system backend and frontend are largely implemented. This task covers the **missing features** identified in the codebase audit:

### What Already Exists
- `AdminTicketRoutes.kt` — CRUD + assign/resolve/close + comments
- `AdminTicketService.kt` — SLA deadline logic (`slaDeadlineMs()`), `checkSlaBreaches()`
- `AdminTicketRepository.kt` / `AdminTicketRepositoryImpl.kt` — Exposed ORM, pagination
- `TicketCommentRepository.kt` / `TicketCommentRepositoryImpl.kt`
- `InboundEmailProcessor.kt` — HMAC-signed inbound email, dedup by messageId, thread linking
- `EmailService.kt` — Resend HTTP API, ticket_created/ticket_updated templates
- `ChatwootService.kt` — auto-creates Chatwoot conversations from inbound email
- `AlertGenerationJob.kt` + `AdminAlertsService.kt` — background job (60s interval), `checkSlaBreaches()` already exists
- DB migrations: V5 (tickets, comments, attachments), V18 (email_threads), V20 (email_delivery_log)
- Frontend: `TicketTable`, `TicketCreateModal`, `TicketAssignModal`, `TicketResolveModal`, `TicketCommentThread`, `TicketStatusBadge`
- API hooks: `useTickets`, `useTicket`, `useTicketComments`, all mutations

### What is MISSING (to implement in this task)

1. **TASK 1 — Email Thread Viewing + Reply-to-Reply Chain Tracking**
2. **TASK 2 — Bulk Ticket Operations (assign, resolve, export)**
3. **TASK 3 — SLA Breach Email Notifications**
4. **TASK 4 — Advanced Ticket Filtering (date range, full-text search)**
5. **TASK 5 — Ticket Metrics / Analytics Endpoint**
6. **TASK 6 — Agent Reply by Email (outbound email from ticket)**
7. **TASK 7 — Customer Portal (direct ticket status check)**
8. **BUG FIX — InboundEmailProcessor SLA Hardcode (MEDIUM 48h always)**

---

## TASK 1 — Email Thread Viewing + Reply-to-Reply Chain Tracking

### Goal
Show all inbound emails linked to a ticket on the ticket detail page. Email threads table (`email_threads`) already has `ticket_id` FK — just need an API + UI to surface it.

Additionally, track **reply-to-reply chains**: when a customer replies to an agent's email, link it to the parent message using `In-Reply-To` / `References` headers so the thread renders as a nested conversation, not a flat list.

### Backend Steps

**Step 1: Add `EmailThreadRepository` interface**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/repository/EmailThreadRepository.kt`

```kotlin
package com.zyntasolutions.zyntapos.api.repository

import java.util.UUID

data class EmailThreadRow(
    val id: UUID,
    val ticketId: UUID?,
    val messageId: String?,
    val inReplyTo: String?,
    val fromAddress: String,
    val fromName: String?,
    val toAddress: String,
    val subject: String,
    val bodyText: String?,
    val receivedAt: String,   // ISO-8601
    val createdAt: String,    // ISO-8601
)

interface EmailThreadRepository {
    suspend fun findByTicketId(ticketId: UUID): List<EmailThreadRow>
}
```

**Step 2: Add `EmailThreadRepositoryImpl`**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/repository/EmailThreadRepositoryImpl.kt`

- Use Exposed: query `email_threads` table WHERE `ticket_id = ticketId`
- Order by `received_at ASC`
- Map to `EmailThreadRow`
- Look at `AdminTicketRepositoryImpl.kt` for patterns (newSuspendedTransaction, ResultRow mapping)

**Step 3: Add `getEmailThreads()` to `AdminTicketService`**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/service/AdminTicketService.kt`

```kotlin
suspend fun getEmailThreads(ticketId: String): List<EmailThreadRow>? {
    val id = runCatching { UUID.fromString(ticketId) }.getOrNull() ?: return null
    return emailThreadRepo.findByTicketId(id)
}
```

Constructor injection: add `private val emailThreadRepo: EmailThreadRepository`

**Step 3b: Reply-to-Reply Chain Tracking in `EmailThreadRepositoryImpl`**

When `InboundEmailProcessor` stores a new email, resolve its parent in the chain:

```kotlin
// Find parent by matching inReplyTo → messageId
val parentId: UUID? = inReplyTo?.let {
    transaction {
        EmailThreads
            .select { EmailThreads.messageId eq it }
            .map { it[EmailThreads.id] }
            .firstOrNull()
    }
}
// Store parentId in email_threads.parent_thread_id (new nullable FK column)
```

**DB Migration: Add `parent_thread_id` column**

New migration file: `backend/api/src/main/resources/db/migration/V21__email_thread_chain.sql`

```sql
ALTER TABLE email_threads
    ADD COLUMN parent_thread_id UUID REFERENCES email_threads(id) ON DELETE SET NULL;

CREATE INDEX idx_email_threads_parent ON email_threads(parent_thread_id);
```

Update `EmailThreadRow` to include `parentThreadId: UUID?`.

Frontend renders chain as nested indented cards — child replies indented under their parent.

**Step 4: Add route `GET /admin/tickets/{id}/email-threads`**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/routes/AdminTicketRoutes.kt`

```kotlin
get("{id}/email-threads") {
    requirePermission(call, "tickets:read") ?: return@get
    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val threads = ticketService.getEmailThreads(id)
        ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respond(threads)
}
```

**Step 5: Register `EmailThreadRepository` in DI**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/di/AppModule.kt`

```kotlin
single<EmailThreadRepository> { EmailThreadRepositoryImpl() }
```

Update `AdminTicketService` binding to inject `EmailThreadRepository`.

### Frontend Steps

**Step 1: Add type to `admin-panel/src/types/ticket.ts`**

```typescript
export interface EmailThread {
  id: string
  ticketId: string | null
  messageId: string | null
  inReplyTo: string | null
  fromAddress: string
  fromName: string | null
  toAddress: string
  subject: string
  bodyText: string | null
  receivedAt: string   // ISO-8601
  createdAt: string    // ISO-8601
}
```

**Step 2: Add `useEmailThreads` hook to `admin-panel/src/api/tickets.ts`**

```typescript
export const emailThreadKeys = {
  forTicket: (id: string) => ['tickets', 'email-threads', id] as const,
}

export function useEmailThreads(ticketId: string) {
  return useQuery({
    queryKey: emailThreadKeys.forTicket(ticketId),
    queryFn: () =>
      apiClient
        .get<EmailThread[]>(`/admin/tickets/${ticketId}/email-threads`)
        .then(r => r.data),
    enabled: !!ticketId,
  })
}
```

**Step 3: Create `TicketEmailThreadPanel.tsx`**

File: `admin-panel/src/components/tickets/TicketEmailThreadPanel.tsx`

- Call `useEmailThreads(ticketId)`
- Render each thread as a card: from address, subject, receivedAt (formatted), bodyText (collapsed with "Show more")
- Show empty state if no threads
- Do NOT render `bodyHtml` directly (XSS risk — use `bodyText` only)

**Step 4: Add panel to ticket detail page**

File: `admin-panel/src/routes/tickets/$ticketId.tsx`

- Import `TicketEmailThreadPanel`
- Add as a new tab or section below `TicketCommentThread`
- Label: "Email History" with count badge

---

## TASK 2 — Bulk Ticket Operations

### Goal
Allow selecting multiple tickets in `TicketTable` and performing bulk: assign, resolve, export (CSV).

### Backend Steps

**Add `POST /admin/tickets/bulk-assign`**

```
Body: { ticketIds: string[], assigneeId: string }
Permission: tickets:assign
```

Loop `assignTicket()` for each ID. Return `{ updated: number, failed: string[] }`.

**Add `POST /admin/tickets/bulk-resolve`**

```
Body: { ticketIds: string[], resolutionNote: string }
Permission: tickets:resolve
```

**Add `GET /admin/tickets/export`**

```
Query: same filters as list endpoint
Permission: tickets:read
Response: CSV (Content-Disposition: attachment; filename=tickets.csv)
```

CSV columns: ticket_number, status, priority, category, customer_email, title, created_at, resolved_at, time_spent_min, sla_breached

### Frontend Steps

- Add checkbox column to `TicketTable`
- Add bulk action toolbar (appears when 1+ selected): "Assign", "Resolve", "Export CSV"
- `BulkAssignModal.tsx` — operator dropdown
- `BulkResolveModal.tsx` — resolution note textarea
- Export: direct download via `window.location.href = /admin/tickets/export?...`

---

## TASK 3 — SLA Breach Email Notifications

### Goal
When `AlertGenerationJob` detects an SLA breach (via `checkSlaBreaches()`), send email to assigned operator.

### Backend Steps

**Extend `AdminTicketService.checkSlaBreaches()`**

After calling `ticketRepo.checkSlaBreaches(now)`, query for newly breached tickets:

```kotlin
val breachedTickets = ticketRepo.findRecentlyBreached(windowMs = 65_000L)
for (ticket in breachedTickets) {
    ticket.assignedToEmail?.let { email ->
        emailService.sendSlaBreachAlert(
            toEmail = email,
            ticketNumber = ticket.ticketNumber,
            title = ticket.title,
            priority = ticket.priority,
        )
    }
}
```

**Add `sendSlaBreachAlert()` to `EmailService`**

```kotlin
suspend fun sendSlaBreachAlert(toEmail: String, ticketNumber: String, title: String, priority: String) {
    val subject = "SLA Breach: $ticketNumber [$priority]"
    val body = """
        <h2>SLA Breach Alert</h2>
        <p>Ticket <strong>$ticketNumber</strong> has breached its SLA.</p>
        <p><strong>Title:</strong> ${htmlEscape(title)}</p>
        <p><strong>Priority:</strong> $priority</p>
        <p>Please take immediate action.</p>
    """.trimIndent()
    sendEmail(toEmail, subject, body, template = "sla_breach")
}
```

**Add `findRecentlyBreached()` to `AdminTicketRepository`**

Query: WHERE `sla_breached = true AND sla_due_at BETWEEN (now - windowMs) AND now`
Join with `admin_users` to get assignee email.

---

## TASK 4 — Advanced Ticket Filtering

### Goal
Add date range filter (createdAt) and full-text search on title + description.

### Backend Steps

**Extend `TicketFilter` data class:**

```kotlin
data class TicketFilter(
    val status: String? = null,
    val priority: String? = null,
    val category: String? = null,
    val assignedTo: String? = null,
    val storeId: String? = null,
    val search: String? = null,        // already exists — search title
    val searchBody: Boolean = false,   // NEW: also search description
    val createdAfter: Long? = null,    // NEW: epoch-ms
    val createdBefore: Long? = null,   // NEW: epoch-ms
)
```

**Extend `AdminTicketRepositoryImpl.list()`:**

```kotlin
if (filter.createdAfter != null) {
    andWhere { SupportTickets.createdAt greaterEq filter.createdAfter }
}
if (filter.createdBefore != null) {
    andWhere { SupportTickets.createdAt lessEq filter.createdBefore }
}
if (filter.search != null && filter.searchBody) {
    andWhere { (SupportTickets.title like "%${filter.search}%") or
               (SupportTickets.description like "%${filter.search}%") }
}
```

**Extend route query params:**

```kotlin
val createdAfter = call.request.queryParameters["createdAfter"]?.toLongOrNull()
val createdBefore = call.request.queryParameters["createdBefore"]?.toLongOrNull()
val searchBody = call.request.queryParameters["searchBody"] == "true"
```

### Frontend Steps

- Add date range picker to `TicketTable` filter bar (use existing date picker component if available)
- Add "Search body" toggle checkbox next to search input
- Pass new params in `useTickets(filter)` hook

---

## TASK 5 — Ticket Metrics Endpoint

### Goal
Single endpoint returning aggregate ticket metrics for dashboard widgets.

### Backend Steps

**Add `GET /admin/tickets/metrics`**

```
Permission: tickets:read
Response:
{
  "totalOpen": number,
  "totalAssigned": number,
  "totalResolved": number,
  "totalClosed": number,
  "slaBreached": number,
  "avgResolutionTimeMin": number,
  "openByPriority": { "CRITICAL": n, "HIGH": n, "MEDIUM": n, "LOW": n },
  "openByCategory": { "HARDWARE": n, ... }
}
```

Implement as a single SQL query with multiple CTEs or multiple COUNT queries in one transaction.

### Frontend Steps

- Add `useTicketMetrics()` hook
- Add metrics cards at top of `/tickets` route: Open, SLA Breached (red), Avg Resolution Time

---

## TASK 6 — Agent Reply by Email (Outbound from Ticket)

### Goal
Allow admin panel operators to reply to the customer's email directly from the ticket comment box. Sends email via `EmailService` and logs it in `email_delivery_log`.

### Backend Steps

**Extend `POST /admin/tickets/{id}/comments`**

Add optional field `replyToCustomer: Boolean = false` to `AddCommentRequest`.

If `replyToCustomer = true` AND ticket has `customerEmail`:

```kotlin
emailService.sendTicketReply(
    toEmail = ticket.customerEmail,
    customerName = ticket.customerName,
    ticketNumber = ticket.ticketNumber,
    agentName = authorName,
    messageBody = req.body,
)
```

**Add `sendTicketReply()` to `EmailService`**

Template: `ticket_reply`
Subject: `Re: [${ticketNumber}] ${ticket.title}`
Body: agent message + separator + previous thread context (optional)

### Frontend Steps

- Add "Reply to customer" toggle in `TicketCommentThread` comment form
- When toggled: show info banner "This message will be sent to customer@email.com"
- Pass `replyToCustomer: true` in add comment mutation

---

## TASK 7 — Customer Portal (Direct Ticket Status Check)

### Goal
Allow customers to check the status of their own support ticket without logging into the admin panel. Accessed via a unique token URL sent in the ticket confirmation email.

### Backend Steps

**Step 1: Add `customer_access_token` column to `support_tickets`**

New migration: `backend/api/src/main/resources/db/migration/V22__ticket_customer_token.sql`

```sql
ALTER TABLE support_tickets
    ADD COLUMN customer_access_token UUID DEFAULT gen_random_uuid() NOT NULL UNIQUE;

CREATE INDEX idx_support_tickets_customer_token ON support_tickets(customer_access_token);
```

**Step 2: Add public (unauthenticated) route `GET /tickets/status/{token}`**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/routes/CustomerTicketRoutes.kt`

- No auth required — token IS the credential
- Returns limited public view: `ticketNumber`, `status`, `priority`, `title`, `createdAt`, `updatedAt`, `slaBreached`
- Does NOT return internal notes, assignee details, or customer PII beyond their own email

```kotlin
get("/tickets/status/{token}") {
    val token = call.parameters["token"]
        ?: return@get call.respond(HttpStatusCode.BadRequest)
    val ticket = ticketService.getByCustomerToken(token)
        ?: return@get call.respond(HttpStatusCode.NotFound)
    call.respond(ticket.toPublicView())
}
```

**Step 3: Include portal link in `ticket_created` email template**

In `EmailService.sendTicketCreatedEmail()`, append:

```
Track your ticket status: https://admin.zyntapos.com/ticket-status/{customerAccessToken}
```

**Step 4: Add `getByCustomerToken()` to `AdminTicketService` + repository**

```kotlin
suspend fun getByCustomerToken(token: String): SupportTicket? {
    val uuid = runCatching { UUID.fromString(token) }.getOrNull() ?: return null
    return ticketRepo.findByCustomerToken(uuid)
}
```

### Frontend Steps (Admin Panel or separate public page)

**Option A — Admin Panel public route (recommended)**

Add unauthenticated route: `admin-panel/src/routes/ticket-status/$token.tsx`

- Calls `GET /tickets/status/{token}`
- Shows: status badge, priority, title, creation date, last updated, SLA status
- No login required (public route, excluded from auth guard)
- Simple read-only card layout

---

## BUG FIX — InboundEmailProcessor SLA Hardcode

### Bug

`InboundEmailProcessor.kt` creates tickets with hardcoded `priority = "MEDIUM"` and `slaDeadlineMs = 48h` always — regardless of email content or subject keywords.

When an admin later updates the ticket priority to `HIGH` via the PATCH endpoint, `slaDeadlineMs()` correctly recalculates. But the **initial creation is always wrong** because the SLA is set before priority is determined.

Additionally, `InboundEmailProcessor` duplicates ticket creation logic that already exists in `AdminTicketService.createTicket()` — two code paths for the same operation.

### Fix

**Step 1: Inject `AdminTicketService` into `InboundEmailProcessor`**

Current: `InboundEmailProcessor` directly inserts into the DB / calls repository.
Fix: Replace direct DB calls with `adminTicketService.createTicket(request)`.

```kotlin
// BEFORE (wrong — duplicate logic, hardcoded SLA)
val ticket = SupportTickets.insert {
    it[priority] = "MEDIUM"
    it[slaDeadlineAt] = System.currentTimeMillis() + 48 * 60 * 60 * 1000L
    ...
}

// AFTER (correct — uses AdminTicketService which calls slaDeadlineMs())
val createRequest = CreateTicketRequest(
    title = subject,
    description = bodyText,
    customerEmail = fromAddress,
    customerName = fromName,
    priority = inferPriorityFromEmail(subject, bodyText),  // see Step 2
    category = "GENERAL",
    source = "EMAIL",
)
adminTicketService.createTicket(createRequest)
```

**Step 2: Add `inferPriorityFromEmail()` helper**

Simple keyword-based priority inference (can be improved later):

```kotlin
private fun inferPriorityFromEmail(subject: String, body: String?): String {
    val text = "${subject} ${body ?: ""}".lowercase()
    return when {
        text.containsAny("urgent", "critical", "down", "broken", "emergency") -> "HIGH"
        text.containsAny("billing", "payment", "invoice") -> "HIGH"
        text.containsAny("feature", "suggestion", "how to") -> "LOW"
        else -> "MEDIUM"
    }
}

private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
```

**Step 3: Remove duplicate SLA/insert logic from `InboundEmailProcessor`**

After Step 1, delete the direct `SupportTickets.insert {}` block and any duplicate `slaDeadlineMs` calculation from `InboundEmailProcessor`. Single source of truth = `AdminTicketService.createTicket()`.

**Step 4: Update DI — inject `AdminTicketService` into `InboundEmailProcessor`**

File: `backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/di/AppModule.kt`

```kotlin
single { InboundEmailProcessor(get(), get(), get()) }
// Add AdminTicketService to constructor params
```

### Verification

```bash
# Send test inbound email with "URGENT" in subject
# Verify ticket created with priority=HIGH and correct SLA (4h, not 48h)
curl -X POST http://localhost:8081/inbound/email \
  -H "X-HMAC-Signature: ..." \
  -d '{"subject":"URGENT: System down","from":"customer@test.com","bodyText":"Help!"}'

# Check created ticket
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/admin/tickets?search=URGENT | python3 -m json.tool
```

---

## File Reference

| File | Action |
|------|--------|
| `backend/api/.../repository/EmailThreadRepository.kt` | CREATE |
| `backend/api/.../repository/EmailThreadRepositoryImpl.kt` | CREATE |
| `backend/api/.../service/AdminTicketService.kt` | MODIFY (add email threads, SLA notifications) |
| `backend/api/.../service/EmailService.kt` | MODIFY (add slaBreachAlert, ticketReply) |
| `backend/api/.../routes/AdminTicketRoutes.kt` | MODIFY (add email-threads, bulk, metrics, export routes) |
| `backend/api/.../repository/AdminTicketRepository.kt` | MODIFY (add findRecentlyBreached, metrics query) |
| `backend/api/.../repository/AdminTicketRepositoryImpl.kt` | MODIFY |
| `backend/api/.../di/AppModule.kt` | MODIFY (register EmailThreadRepository) |
| `admin-panel/src/types/ticket.ts` | MODIFY (add EmailThread type) |
| `admin-panel/src/api/tickets.ts` | MODIFY (add useEmailThreads, useTicketMetrics, bulk hooks) |
| `admin-panel/src/components/tickets/TicketEmailThreadPanel.tsx` | CREATE |
| `admin-panel/src/components/tickets/BulkAssignModal.tsx` | CREATE |
| `admin-panel/src/components/tickets/BulkResolveModal.tsx` | CREATE |
| `admin-panel/src/components/tickets/TicketTable.tsx` | MODIFY (add checkboxes, bulk toolbar) |
| `admin-panel/src/routes/tickets/$ticketId.tsx` | MODIFY (add email thread panel) |
| `admin-panel/src/routes/tickets/index.tsx` | MODIFY (add metrics cards, date filter) |
| `backend/api/.../routes/CustomerTicketRoutes.kt` | CREATE |
| `backend/api/src/main/resources/db/migration/V21__email_thread_chain.sql` | CREATE |
| `backend/api/src/main/resources/db/migration/V22__ticket_customer_token.sql` | CREATE |
| `backend/api/.../service/InboundEmailProcessor.kt` | MODIFY (inject AdminTicketService, remove duplicate SLA logic) |
| `admin-panel/src/routes/ticket-status/$token.tsx` | CREATE |

---

## Implementation Order

0. **BUG FIX** (InboundEmailProcessor SLA hardcode) — fix first, unblocks correct SLA on all new tickets
1. **TASK 1** (Email Thread View + Chain) — V21 migration + query `email_threads` + chain tracking
2. **TASK 3** (SLA Breach Alerts) — extend existing `checkSlaBreaches()` + `AlertGenerationJob`
3. **TASK 4** (Advanced Filtering) — extend existing filter params
4. **TASK 5** (Metrics Endpoint) — read-only aggregate query, no schema change
5. **TASK 2** (Bulk Operations) — new endpoints + frontend selection UI
6. **TASK 6** (Agent Email Reply) — extend comment endpoint + new email template
7. **TASK 7** (Customer Portal) — V22 migration + public route + public frontend page

---

## Verification

After each task, verify:

```bash
# Backend compiles
./gradlew :backend:api:compileKotlin

# Frontend builds
cd admin-panel && npm run build

# Test ticket endpoints
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/admin/tickets/{id}/email-threads"
```

---

## Notes

- `AlertGenerationJob` runs every 60s — TASK 3 hooks into this, no new job needed
- `bodyHtml` must NOT be rendered as innerHTML — use `bodyText` only (XSS risk)
- Bulk export CSV: stream response, do not buffer entire result set in memory
- All new routes must check permissions via `requirePermission()` (same pattern as existing routes)
- HMAC for inbound emails is already implemented — do not change `InboundEmailRoutes.kt`
