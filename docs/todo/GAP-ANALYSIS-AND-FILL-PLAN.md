# ZyntaPOS — Gap Analysis & Implementation Plan

**Created:** 2026-03-06
**Author:** Claude Code (automated gap analysis)
**Scope:** All TODO files (000–010) audited against actual codebase
**Purpose:** Identify what's done, what's remaining, and the exact implementation plan to fill every gap

---

## Executive Summary

| TODO | Title | Completion | Phase |
|------|-------|------------|-------|
| 000 | Master Execution Plan | Living doc | — |
| 001 | Single Admin Account | **100%** ✅ | Phase 1 |
| 002 | Remove SignUp Screen | **100%** ✅ | Phase 1 |
| 003 | Edition Management Wiring | **100%** ✅ | Phase 1 |
| 004 | Enterprise Audit Logging | **~85%** 🟡 | Phase 1 |
| 005 | Modern Dashboard + Nav | **100%** ✅ | Phase 1 |
| 006 | Remote Diagnostic Access | **0%** ⬜ | Phase 2 |
| 007 | Infrastructure & Deployment | **~65%** 🟡 | Phase 2 |
| 008 | SEO & ASO | **0%** ⬜ | Phase 2 |
| 009 | Ktor Security Hardening | **~75%** 🟡 | Phase 2 |
| 010 | Security Monitoring & Auto-Response | **~20%** 🟡 | Phase 2 |

**Bottom line:** Phase 1 is nearly complete (4 items remain in TODO-004). Phase 2 has substantial work remaining across 5 TODOs.

---

## Part 1: Detailed Gap Analysis

### TODO-004 — Enterprise Audit Logging (85% → 100%)

**What's done:**
- ✅ `AuditEventType` expanded to ~40 event types
- ✅ Enhanced `AuditEntry` model with `previousValue`, `newValue`, `deviceId`, `userName`, `userRole`, `ipAddress`
- ✅ SQLDelight schema updated with new columns and indexes
- ✅ `operational_logs` table created (Tier 2 logging)
- ✅ `SecurityAuditLogger` with 30+ fire-and-forget methods
- ✅ Brute-force detection wired to login flow
- ✅ Retention policy SQL defined
- ✅ Audit events wired in feature ViewModels

**Remaining gaps (4 items):**

| # | Gap | Effort | Priority |
|---|-----|--------|----------|
| 4a | SHA-256 hash chain **computation** — hash is a field in the model but `computeHash()` function that chains `previousHash + entry fields → SHA-256` is not implemented | 2 hrs | HIGH |
| 4b | `AuditIntegrityVerifier` — scheduled daily verification that walks the entire chain and reports `IntegrityReport` | 3 hrs | HIGH |
| 4c | Admin audit viewer UI — needs date range picker, event type multi-select, user/role filter, entity filter, success/failure toggle, pagination (50/page), CSV export button | 1 day | MEDIUM |
| 4d | Kermit-to-SQLite bridge — route operational logs from Kermit logger to `operational_logs` table instead of text files | 4 hrs | MEDIUM |

---

### TODO-009 — Ktor Security Hardening (75% → 100%)

**What's done:**
- ✅ `jdk.serialFilter=!*` in all 3 service `main()` + Dockerfiles
- ✅ CIO engine (Netty removed) in all 3 services
- ✅ Security headers plugin (CSP, X-Frame-Options, etc.) in all 3 services
- ✅ Dockerfiles: multi-stage build, non-root user, JVM security flags, exec-form ENTRYPOINT
- ✅ Docker Compose: `read_only: true`, `tmpfs`, `no-new-privileges:true`
- ✅ OWASP Dependency Check plugin in all 3 `build.gradle.kts` (with `owasp-suppressions.xml`)
- ✅ Dependabot `.github/dependabot.yml` configured
- ✅ `ForbiddenMethodCall` detekt rules (5 banned methods)
- ✅ Sensitive data zeroing patterns applied
- ✅ Error message sanitization (StatusPages — no `cause.message` to client)

**Remaining gaps (4 items):**

| # | Gap | Effort | Priority |
|---|-----|--------|----------|
| 9a | `ValidationScope` class — reusable validation DSL for all POST/PUT routes | 3 hrs | HIGH |
| 9b | `RequestBodyLimit` plugin — body size limits (512KB default, 1MB sync, 4KB license) | 1 hr | HIGH |
| 9c | Seccomp profile `config/seccomp/ktor.json` + `cap_drop: ALL` / `cap_add: NET_BIND_SERVICE` in docker-compose.yml | 1 hr | MEDIUM |
| 9d | Lower `failBuildOnCVSS` from `9.0f` to `7.0f` in all 3 backend `build.gradle.kts` files (spec says 7.0) | 5 min | LOW |

---

### TODO-007 — Infrastructure & Deployment (65% → 100%)

**What's done:**
- ✅ Contabo VPS provisioned (separate from VPN VPS)
- ✅ Docker Compose running: Caddy, API, License, Sync, PostgreSQL, Redis, Canary
- ✅ Caddyfile: 6 subdomains with Cloudflare Origin TLS
- ✅ DNS delegated to Cloudflare, A records configured
- ✅ `api.zyntapos.com`, `license.zyntapos.com`, `sync.zyntapos.com` pointing to real services
- ✅ License server: activate, heartbeat, GET endpoints implemented
- ✅ JWT RS256 authentication on API + Sync services
- ✅ Database-per-service (ADR-007): `zyntapos_api`, `zyntapos_license`
- ✅ PostgreSQL tuning (`postgresql.conf`)
- ✅ `init-databases.sh` for per-service DB creation
- ✅ CI Gate workflow builds Docker images → GHCR
- ✅ Deploy workflow (SSH to VPS)
- ✅ Container hardening (read_only, tmpfs, mem_limit, no-new-privileges)

**Remaining gaps (8 items):**

| # | Gap | Effort | Priority | Blocker |
|---|-----|--------|----------|---------|
| 7a | React admin panel (`panel.zyntapos.com`) — license CRUD, deployment health, helpdesk | 2–3 weeks | HIGH | None |
| 7b | Astro marketing website (`www.zyntapos.com`) on Cloudflare Pages | 1 week | MEDIUM | None |
| 7c | Monitoring setup — Uptime Kuma or BetterStack for status page + alerts | 4 hrs | HIGH | None |
| 7d | Automated backup — `pg_dump` cron → Backblaze B2 via `rclone` | 2 hrs | HIGH | None |
| 7e | `docs.zyntapos.com` — API documentation site | 1 week | LOW | None |
| 7f | `status.zyntapos.com` — public uptime status page | 4 hrs | MEDIUM | 7c |
| 7g | Sync engine server-side — push/pull operations (beyond WebSocket relay) | 1 week | HIGH | None |
| 7h | Cloudflare WAF rules — custom rules (block non-POS clients, rate limit, geo-block) | 1 hr | MEDIUM | None |

---

### TODO-010 — Security Monitoring & Auto-Response (20% → 100%)

**What's done:**
- ✅ Canary tokens concept documented and response workflow planned
- ✅ `.github/workflows/canary-response.yml` exists or is planned

**Remaining gaps (7 items):**

| # | Gap | Effort | Priority | Blocker |
|---|-----|--------|----------|---------|
| 10a | Cloudflare Zero Trust — protect `panel.zyntapos.com` with CF Access | 1 hr | HIGH | None |
| 10b | Cloudflare Tunnel — `cloudflared` container in docker-compose | 1 hr | 7a (panel) |
| 10c | Bot Fight Mode — enable Super Bot Fight Mode in CF dashboard | 15 min | MEDIUM | None |
| 10d | Falco — install on VPS as systemd service | 1 hr | HIGH | VPS access |
| 10e | Custom Falco rules — `config/falco/zyntapos_rules.yaml` (4 JVM rules) | 1 hr | HIGH | 10d |
| 10f | Falcosidekick — Docker container + Slack webhook + response script | 1 hr | HIGH | 10d |
| 10g | Snyk Monitor — connect GitHub org, configure alerts | 15 min | MEDIUM | None |

---

### TODO-008 — SEO & ASO (0% → 100%)

**Blocked on:** TODO-007 Step 15 (Astro site deployment)

| # | Gap | Effort | Blocker |
|---|-----|--------|---------|
| 8a | `robots.txt`, `sitemap.xml`, canonical tags | 2 hrs | 7b |
| 8b | Schema.org JSON-LD structured data | 4 hrs | 7b |
| 8c | Core Web Vitals optimization | 1 day | 7b |
| 8d | Google Search Console + GA4 + GTM setup | 2 hrs | 7b |
| 8e | Google Play Store ASO — metadata, screenshots, Data Safety | 1 day | App published |
| 8f | Lighthouse CI in GitHub Actions | 2 hrs | 7b |

---

### TODO-006 — Remote Diagnostic Access (0% → 100%)

**Blocked on:** TODO-007 (JWT auth) + TODO-004 (audit log)

| # | Gap | Effort | Blocker |
|---|-----|--------|---------|
| 6a | `DiagnosticSession` domain model in `:shared:domain` | 2 hrs | None |
| 6b | `DiagnosticTokenValidator` in `:shared:security` | 4 hrs | None |
| 6c | `:composeApp:feature:diagnostic` module (no business data deps) | 2 days | 6a, 6b |
| 6d | Customer consent flow (notification + ADMIN approval) | 1 day | 6c |
| 6e | WebSocket relay on panel for diagnostic commands | 1 day | 7a |
| 6f | Visual indicator for active Diagnostic Mode | 4 hrs | 6c |
| 6g | Site visit token support | 4 hrs | 6c |

---

## Part 2: Implementation Plan (Prioritized)

### Batch 1: Phase 1 Completion (TODO-004 + TODO-009 remaining)

**Goal:** Close all Phase 1 gaps + critical Phase 2 security gaps
**Total effort:** ~2 days
**No external blockers**

#### Step 1.1 — ValidationScope class (TODO-009, item 9a)

**Files to create:**
```
backend/common/src/main/kotlin/com/zyntasolutions/zyntapos/common/validation/ValidationScope.kt
```

**Implementation:**
```kotlin
class ValidationScope {
    private val errors = mutableListOf<String>()

    fun requireNotBlank(field: String, value: String?)
    fun requireLength(field: String, value: String?, min: Int, max: Int)
    fun requirePositive(field: String, value: Double)
    fun requireNonNegative(field: String, value: Int)
    fun requireUUID(field: String, value: String?)
    fun requireInRange(field: String, value: Double, min: Double, max: Double)
    fun requirePattern(field: String, value: String?, pattern: Regex, message: String)
    fun validate(): List<String>
}

// Extension for Ktor routes:
suspend fun ApplicationCall.validateOr422(block: ValidationScope.() -> Unit): Boolean
```

**Apply to routes:**
- `backend/api/.../routes/AuthRoutes.kt` — login request validation
- `backend/api/.../routes/ProductRoutes.kt` — product CRUD validation
- `backend/api/.../routes/SyncRoutes.kt` — sync payload validation
- `backend/license/.../routes/LicenseRoutes.kt` — replace inline `require()` with `ValidationScope`

#### Step 1.2 — Request body size limits (TODO-009, item 9b)

**Files to modify:**
- `backend/api/.../Application.kt` — install body limit plugin (512KB default, 1MB for `/api/v1/sync`)
- `backend/license/.../Application.kt` — 4KB limit for heartbeat/activate
- `backend/sync/.../Application.kt` — 1MB limit for sync payloads

**Ktor plugin:** Use `io.ktor.server.plugins.requestsize.RequestSizeLimit` or custom intercept on `receive()`.

#### Step 1.3 — Seccomp profile + capabilities (TODO-009, item 9c)

**Files to create:**
```
config/seccomp/ktor.json
```

Based on Docker default profile + additional denials: `ptrace`, `personality`, `keyctl`, `add_key`, `request_key`.

**Files to modify:**
- `docker-compose.yml` — add to all 3 Ktor services:
  ```yaml
  security_opt:
    - no-new-privileges:true
    - seccomp:./config/seccomp/ktor.json
  cap_drop:
    - ALL
  cap_add:
    - NET_BIND_SERVICE
  ```

#### Step 1.4 — Lower failBuildOnCVSS (TODO-009, item 9d)

**Files to modify:**
- `backend/api/build.gradle.kts` — change `failBuildOnCVSS = 9.0f` → `7.0f`
- `backend/license/build.gradle.kts` — same
- `backend/sync/build.gradle.kts` — same

#### Step 1.5 — SHA-256 hash chain computation (TODO-004, item 4a)

**Files to create/modify:**
```
shared/data/src/commonMain/.../audit/AuditHashComputer.kt
```

**Implementation:**
```kotlin
object AuditHashComputer {
    fun computeHash(entry: AuditEntry, previousHash: String): String {
        val input = buildString {
            append(entry.id)
            append(entry.eventType.name)
            append(entry.userId)
            append(entry.createdAt.toString())
            append(entry.entityType.orEmpty())
            append(entry.entityId.orEmpty())
            append(entry.payload)
            append(entry.success)
            append(previousHash)
        }
        return sha256Hex(input.encodeToByteArray())
    }
}
```

Wire into the audit repository's `insert()` method — compute hash before persisting.

#### Step 1.6 — AuditIntegrityVerifier (TODO-004, item 4b)

**Files to create:**
```
shared/data/src/commonMain/.../audit/AuditIntegrityVerifier.kt
```

**Implementation:** As specified in TODO-004 — walks entire chain, returns `IntegrityReport`.
Schedule via `WorkManager` (Android) / `ScheduledExecutorService` (JVM) — daily at 03:00 local time.

---

### Batch 2: Infrastructure Hardening (TODO-007 + TODO-010 remaining)

**Goal:** Complete monitoring, backup, and security detection layers
**Total effort:** ~1 week
**Requires:** VPS SSH access for some items

#### Step 2.1 — Automated backup (TODO-007, item 7d) — 2 hrs

**Files to create:**
```
backend/scripts/backup.sh
```

Set up `rclone` + Backblaze B2 on VPS. Cron job at 03:00 LKT daily.
Includes `pg_dump` of both `zyntapos_api` and `zyntapos_license` databases.

#### Step 2.2 — Monitoring setup (TODO-007, item 7c) — 4 hrs

Add Uptime Kuma to `docker-compose.yml` as a self-hosted monitoring service.
Configure health checks for all 6 subdomains.
Wire alerts to Slack `#alerts` channel.

#### Step 2.3 — Cloudflare Zero Trust (TODO-010, item 10a) — 1 hr

CF dashboard configuration:
- Create CF Access Application for `panel.zyntapos.com`
- Auth method: OTP to `@zyntasolutions.com`
- Policy: Allow `*@zyntasolutions.com` only

#### Step 2.4 — Falco + Falcosidekick (TODO-010, items 10d–10f) — 3 hrs

**Files to create:**
```
config/falco/zyntapos_rules.yaml
config/falco/falcosidekick.yaml
config/falco/response-handler.sh
```

Install Falco on VPS via apt. Add Falcosidekick to `docker-compose.yml`.

#### Step 2.5 — Snyk Monitor + Bot Fight Mode (TODO-010, items 10c, 10g) — 30 min

SaaS configuration only — no repo files needed.

#### Step 2.6 — Cloudflare WAF rules (TODO-007, item 7h) — 1 hr

CF dashboard configuration:
- Block non-POS clients on API
- Rate limit license heartbeat at edge
- Geo-challenge for panel access

---

### Batch 3: Frontend & Content (TODO-007 panel + site, TODO-008)

**Goal:** Admin panel, marketing site, SEO/ASO
**Total effort:** ~4 weeks
**Requires:** Design decisions (React for panel, Astro for site)

#### Step 3.1 — React Admin Panel (TODO-007, item 7a) — 2–3 weeks

```
zyntapos-panel/
├── src/
│   ├── pages/
│   │   ├── Dashboard.tsx
│   │   ├── Licenses.tsx
│   │   ├── Deployments.tsx
│   │   ├── Helpdesk.tsx
│   │   └── Settings.tsx
│   ├── components/
│   ├── api/
│   └── App.tsx
├── package.json
└── Dockerfile
```

Features: License CRUD, deployment health monitoring, helpdesk ticket system, sync queue viewer.

#### Step 3.2 — Astro Marketing Site (TODO-007, item 7b) — 1 week

Deploy to Cloudflare Pages. Pages: Home, Features, Pricing, Contact, Blog.

#### Step 3.3 — SEO & ASO (TODO-008) — 1 week

After site is live:
- Technical SEO (robots.txt, sitemap, canonicals, Schema.org JSON-LD)
- Core Web Vitals optimization
- Google Search Console + GA4
- Play Store ASO (metadata, screenshots, Data Safety)
- Lighthouse CI in GitHub Actions

---

### Batch 4: Advanced Features (TODO-006, TODO-004 Phase 3)

**Goal:** Remote diagnostics, audit viewer enhancements
**Total effort:** ~2 weeks
**Requires:** Panel (3.1) + Audit infrastructure (1.5, 1.6)

#### Step 4.1 — Admin Audit Viewer Enhancements (TODO-004, items 4c, 4d) — 1.5 days

- Date range picker, event type filter, user/role filter, pagination, CSV export
- Kermit-to-SQLite bridge for operational logs

#### Step 4.2 — Remote Diagnostic Access (TODO-006) — 2 weeks

- Domain models (`DiagnosticSession`, `DiagnosticScope`)
- Token validator in `:shared:security`
- Diagnostic feature module (compile-time isolation from business data)
- Customer consent flow
- WebSocket relay via panel
- Visual indicator + session management

---

## Part 3: Priority Matrix

### Must Do Now (Phase 1 blockers)

| Item | Gap ID | Effort | Impact |
|------|--------|--------|--------|
| SHA-256 hash chain computation | 4a | 2 hrs | Legal compliance — audit trail tamper detection |
| AuditIntegrityVerifier | 4b | 3 hrs | Legal compliance — proves audit trail integrity |

### Should Do Now (Security — before backend goes live)

| Item | Gap ID | Effort | Impact |
|------|--------|--------|--------|
| ValidationScope class | 9a | 3 hrs | Closes injection attack surface on all endpoints |
| Request body size limits | 9b | 1 hr | Prevents memory exhaustion attacks |
| Seccomp profile + capabilities | 9c | 1 hr | Syscall-level container isolation |
| Lower failBuildOnCVSS to 7.0 | 9d | 5 min | Catches HIGH severity CVEs in CI |
| Automated backup | 7d | 2 hrs | Data protection — no backup = total risk |
| Monitoring | 7c | 4 hrs | Uptime SLA enforcement |

### Should Do Soon (Phase 2 — within 2 weeks)

| Item | Gap ID | Effort | Impact |
|------|--------|--------|--------|
| CF Zero Trust | 10a | 1 hr | Locks admin panel behind identity verification |
| Falco + Falcosidekick | 10d–f | 3 hrs | Runtime threat detection |
| Snyk Monitor | 10g | 15 min | Continuous CVE scanning between Dependabot runs |
| Cloudflare WAF rules | 7h | 1 hr | Edge-level attack filtering |

### Plan For Later (Phase 2–3 — within 1–2 months)

| Item | Gap ID | Effort | Impact |
|------|--------|--------|--------|
| React admin panel | 7a | 2–3 weeks | License management, helpdesk |
| Astro marketing site | 7b | 1 week | Customer acquisition |
| SEO/ASO | 8a–f | 1 week | Organic discovery |
| Remote diagnostics | 6a–g | 2 weeks | Customer support efficiency |
| Admin audit viewer | 4c, 4d | 1.5 days | Legal compliance UI |
| API docs site | 7e | 1 week | Developer experience |
| Status page | 7f | 4 hrs | Public uptime proof |

---

## Part 4: Coding Standards for Gap Implementation

All gap-filling code must follow these industry-leading Kotlin backend practices:

### Clean Architecture (Backend)

```
Route Handler (thin) → Use Case / Service → Repository → Database
     ↑                                              ↓
  Validation                              Transaction boundary
```

- **Route handlers** do validation + serialization only — no business logic
- **Service layer** contains business rules, transaction orchestration
- **Repository** handles data access (Exposed ORM for License, raw SQL for API)

### Ktor-Specific Patterns

1. **Structured concurrency** — all coroutines scoped to request lifecycle
2. **Typed routing** — use Ktor's type-safe routing where possible
3. **Plugin composition** — security/validation/logging as composable plugins
4. **Error handling** — `StatusPages` catches all exceptions; never leak stack traces

### Validation Pattern (once ValidationScope is implemented)

```kotlin
// EVERY POST/PUT route MUST follow this pattern:
post("/resource") {
    val request = call.receive<CreateResourceRequest>()
    val errors = ValidationScope().apply {
        requireNotBlank("name", request.name)
        requireLength("name", request.name, 1, 100)
        requirePositive("price", request.price)
    }.validate()
    if (errors.isNotEmpty()) {
        call.respond(HttpStatusCode.UnprocessableEntity, mapOf("errors" to errors))
        return@post
    }
    // ... business logic
}
```

### Logging Pattern

```kotlin
// Server-side: SLF4J with structured fields
logger.info("License activated", "licenseKey" to maskKey(key), "deviceId" to deviceId)
logger.warn("Validation error", "route" to "/activate", "errors" to errors)
// NEVER log: full license keys, passwords, tokens, PII
```

### Testing Pattern

```kotlin
// Every new class gets a test file
// Service tests: mock repository, verify business rules
// Route tests: use Ktor testApplication { }
// Validation tests: verify all edge cases (blank, too long, invalid format)
```

---

## Appendix: File Inventory

### Files to Create

| File | Purpose | Batch |
|------|---------|-------|
| `backend/common/src/.../validation/ValidationScope.kt` | Reusable validation DSL | 1 |
| `config/seccomp/ktor.json` | Seccomp syscall allowlist | 1 |
| `shared/data/src/.../audit/AuditHashComputer.kt` | SHA-256 chain computation | 1 |
| `shared/data/src/.../audit/AuditIntegrityVerifier.kt` | Daily integrity verification | 1 |
| `backend/scripts/backup.sh` | pg_dump → Backblaze B2 | 2 |
| `config/falco/zyntapos_rules.yaml` | 4 JVM-specific Falco rules | 2 |
| `config/falco/falcosidekick.yaml` | Alert routing config | 2 |
| `config/falco/response-handler.sh` | Auto-response on CRITICAL alerts | 2 |
| `config/cloudflare/tunnel-config.yml` | CF Tunnel for panel | 2 |
| `zyntapos-panel/` (entire project) | React admin panel | 3 |

### Files to Modify

| File | Change | Batch |
|------|--------|-------|
| `backend/api/build.gradle.kts` | `failBuildOnCVSS = 7.0f` | 1 |
| `backend/license/build.gradle.kts` | `failBuildOnCVSS = 7.0f` | 1 |
| `backend/sync/build.gradle.kts` | `failBuildOnCVSS = 7.0f` | 1 |
| `backend/api/.../Application.kt` | Install body size limit | 1 |
| `backend/license/.../Application.kt` | Install body size limit | 1 |
| `backend/sync/.../Application.kt` | Install body size limit | 1 |
| `backend/api/.../routes/*.kt` | Apply ValidationScope | 1 |
| `backend/license/.../routes/LicenseRoutes.kt` | Apply ValidationScope | 1 |
| `docker-compose.yml` | seccomp, cap_drop/cap_add, monitoring | 1+2 |
| `shared/data/src/.../repository/*AuditRepository*` | Wire hash computation | 1 |
