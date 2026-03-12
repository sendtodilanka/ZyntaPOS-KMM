# ZyntaPOS-KMM Gap Analysis — 2026-03-09

**Scope:** Actual codebase implementation vs. `docs/todo/` documentation (TODO-000 through TODO-011 + sub-tasks).
**Branch:** `claude/audit-todo-docs-gap-UnJU4`
**Analyst:** Claude Code (automated codebase audit)
**Date:** 2026-03-09

---

## 1. Executive Summary

| Phase | TODOs | Fully Complete | Partially Implemented | Not Started |
|-------|-------|----------------|-----------------------|-------------|
| Phase 1 (MVP) | 001–005 | 5 | 0 | 0 |
| Phase 2 — Infrastructure | 007–007g | 4 | 5 | 2 |
| Phase 2 — Security | 009–011 | 1 | 2 | 0 |
| Phase 2 — Remote Diagnostics | 006 | 0 | 0 | 1 |
| Phase 2 — Email / SEO | 008, 008a | 1\* | 0 | 1 |
| **Total** | **21** | **11** | **7** | **3** |

\* TODO-008 SEO is substantially done but blocked on external DNS cutover; counted as complete from a code standpoint.

**Critical path blockers (highest priority):**
1. TODO-007g — Sync engine server-side is a stub; blocks offline-first cloud sync
2. TODO-007e — API documentation site not started; blocks public developer adoption
3. TODO-006 — Remote diagnostic access not started; blocks enterprise support workflows
4. TODO-008a — Email system not started; blocks transactional email for all services
5. TODO-011 — Firebase Analytics not started; blocks product telemetry pipeline

---

## 2. Phase 1 — MVP (TODO-001 through TODO-005)

**Status: ALL COMPLETE ✅**

| TODO | Title | Status | Evidence |
|------|-------|--------|----------|
| 001 | Single Admin Account Management | ✅ Complete | `UserRepositoryImpl` guards, `isSystemAdmin` flag, role dropdown filtering |
| 002 | Remove Signup Screen & Account Flow | ✅ Complete | `ZyntaRoute.SignUp` removed, `SignUpViewModel` removed, nav graph clean |
| 003 | Edition Management Nav Graph Wiring | ✅ Complete | `EditionManagementScreen` wired in `MainNavGraph.kt`, all 11 checklist items done |
| 004 | Enterprise Audit Logging | ✅ Complete | ~40 `AuditEventType` values, SHA-256 hash chain, `AuditIntegrityVerifier`, `LogRetentionJob` |
| 005 | Modern Dashboard & Hierarchical Nav Drawer | ✅ Complete | 3-breakpoint adaptive nav, animated KPI cards, gradient hero, sparklines |

No Phase 1 gaps in the `docs/todo/` scope.

---

## 3. Phase 2 — Growth

### TODO-006: Remote Diagnostic Access

**Status: ⬜ 0% — NOT STARTED**
**Priority: HIGH** — Required for enterprise support workflows; depends on TODO-007a (admin panel WebSocket endpoint) which is now substantially implemented.

**All 11 checklist items are unchecked:**

| Gap | Description |
|-----|-------------|
| `DiagnosticSession` domain model | Missing from `shared/domain/src/commonMain/.../model/` |
| `DiagnosticTokenValidator` | Missing from `:shared:security` module |
| `:composeApp:feature:diagnostic` module | Does not exist — no module directory, no `build.gradle.kts`, not in `settings.gradle.kts` |
| Compile-time isolation from business data | Architecture constraint; cannot be implemented without the module existing |
| JIT technician token (15-min TTL) | No token generation or validation logic anywhere in codebase |
| Customer consent flow | No UI, no consent model, no consent recording |
| 3-layer data isolation | Layer 1 (read-only), Layer 2 (diagnostic ops), Layer 3 (no access to transactions) — none implemented |
| WebSocket relay via admin panel | `SyncWebSocketRoutes.kt` exists but only sends ACK; no diagnostic WebSocket endpoint |
| Session audit trail | Would feed into AuditLog (TODO-004 ✅), but diagnostic session events not defined |
| Remote session revocation | No revocation endpoint or token blacklist for diagnostic tokens |
| Feature flag gating | No `REMOTE_DIAGNOSTICS` feature flag in `ZyntaFeature` enum |

**Unblocking requirement:** TODO-007a admin panel is substantially implemented — the WebSocket relay endpoint only needs to be added to the admin panel backend. The `:composeApp:feature:diagnostic` Compose module, `DiagnosticSession` domain model, and `DiagnosticTokenValidator` security service are the critical-path items to implement first.

---

### TODO-007: Infrastructure & Deployment

**Status: 🟡 ~75% — PARTIALLY COMPLETE**

Core VPS infrastructure (Docker Compose, Caddy, subdomain routing, API/License services) is deployed and operational. Sub-task status follows.

#### TODO-007a: React Admin Panel

**Status: ✅ ~98% Complete — only external/infrastructure actions remain**

Updated 2026-03-09: Full gap-fill implemented in `claude/audit-admin-panel-bir9J`.

**Confirmed implemented (post gap-fill):**
- Full tickets module: `V5__helpdesk_tickets.sql`, `AdminTicketService`, `AdminTicketRoutes`, Koin registration
- Frontend tickets: `types/ticket.ts`, `api/tickets.ts`, 6 components (TicketStatusBadge, TicketTable, TicketCreateModal, TicketAssignModal, TicketResolveModal, TicketCommentThread), 2 routes (`/tickets/`, `/tickets/$ticketId`)
- Sidebar RBAC: `hasPermission()` now correctly gates all nav items; permission strings fixed
- Auth audit logging: `ADMIN_LOGIN`, `ADMIN_LOGOUT`, `ADMIN_LOGIN_FAILED`, `ADMIN_MFA_ENABLED`, `ADMIN_MFA_DISABLED` events now recorded
- `AdminAuthorizationException` now handled with 403 (was falling through to 500)
- `use-keyboard.ts` hook: `/` and `Ctrl+K` focus search, `Escape` blurs; registered in AppShell
- Sync page: Conflicts tab + Dead Letters tab with retry/discard actions
- Tests: `TicketStatusBadge.test.tsx`, `ConfirmDialog.test.tsx` added
- Playwright E2E: `playwright.config.ts` + `e2e/smoke.spec.ts` scaffold

**Remaining (external/infrastructure):**
- VPS deployment (requires SSH access via GitHub Actions)
- CF Access bypass configuration (Cloudflare dashboard action)

#### TODO-007b: Astro Marketing Website

**Status: ✅ Code complete — DNS cutover is a user action**

All Astro components, pages, 20+ blog posts, and `_headers` exist in `website/`. The remaining step (Cloudflare Pages DNS cutover) requires manual action in the Cloudflare dashboard and is outside the repository scope.

**No code gaps.** Blocked on user action only.

#### TODO-007c: Monitoring Setup (Uptime Kuma)

**Status: 🟡 ~70% — Infrastructure exists, UI configuration not tracked in repo**

**Implemented in repo:**
- `uptime-kuma` service defined in `docker-compose.yml`
- `setup-uptime-kuma.sh` script exists

**Gaps (require manual configuration in Uptime Kuma UI — not trackable in repo):**
- Monitors for all 7 subdomains configured in Uptime Kuma
- Slack/email alert channels configured
- Status page branded and published
- Grafana dashboards for Docker metrics

These are operational/configuration gaps, not code gaps. The TODO-007c checklist cannot be fully satisfied by repository code alone.

#### TODO-007d: Automated Backup

**Status: ✅ Complete**

`backend/scripts/backup.sh` exists with full `pg_dump` → Backblaze B2 implementation including retention policy, compression, and restore procedure. All checklist items are satisfied.

#### TODO-007e: API Documentation Site

**Status: ⬜ 0% — NOT STARTED**

**File:** `docs/api/` — contains only `README.md` and `.gitkeep`

**All checklist items are incomplete:**
| Gap | Description |
|-----|-------------|
| OpenAPI/Swagger spec | No `.yaml` or `.json` OpenAPI spec anywhere in `backend/` |
| Redoc or Swagger UI hosting | No static site generator config, no `astro.config.mjs` equivalent |
| Hosting on CF Pages or VPS subdomain | No deployment workflow for `docs.zyntapos.com` |
| Authentication endpoints documented | No spec file |
| Sync engine endpoints documented | No spec file |
| License endpoints documented | No spec file |
| Code samples (Kotlin/TypeScript) | None |

**Impact:** Blocks external developer adoption and partner integrations.

#### TODO-007f: Admin Panel Cloudflare + Custom Auth

**Status: ✅ 100% COMPLETE — All 11 gaps resolved including G11 (verified 2026-03-09)**

**All code gaps resolved (updated 2026-03-09):**
- `AdminAuthService` JWT HMAC256 + brute-force lockout ✅
- `MfaService` TOTP ✅, `GoogleOAuthService` OAuth2 ✅
- G1: CSRF double-submit cookie — `plugins/Csrf.kt` + `CsrfPluginTest.kt` (6 tests) ✅
- G2: Password max-length 128 — `AdminAuthService.MAX_PASSWORD_LENGTH` enforced in login + validation ✅
- G3: Auth audit log — 9 event types wired in `AdminAuthRoutes.kt` + `AdminAuthService.kt` ✅
- G7: User list pagination — `page`/`size`/`search`/`role`/`isActive` params ✅
- G8: Backend tests — `AdminAuthServiceTest.kt` (11 tests), `CsrfPluginTest.kt` (6 tests), sync tests ✅
- G9: Google Cloud Console — OAuth Client ID created, vars set in VPS `.env` ✅ (user confirmed)
- G10: VPS env vars + deployment — `ADMIN_JWT_SECRET`, `GOOGLE_ALLOWED_DOMAIN`, `ADMIN_BOOTSTRAP_EMAIL` set; `cd-deploy.yml` ran successfully (2026-03-09) ✅

**G11 verified complete (2026-03-09):**
- `curl -I https://panel.zyntapos.com` → HTTP 200, no `cf-access-*` headers, no redirect to `cloudflareaccess.com`
- ZyntaPOS custom login (`<title>ZyntaPOS Admin</title>`) is served directly — CF Access is not intercepting
- CF network protections (WAF, DDoS, TLS via `server: cloudflare`) remain fully active

#### TODO-007g: Sync Engine Server-Side

**Status: ⬜ ~5% — STUB ONLY**

**File:** `backend/sync/routes/SyncWebSocketRoutes.kt`

The WebSocket handler connects and accepts frames but only responds with a static ACK. All sync logic is absent:

```kotlin
// From SyncWebSocketRoutes.kt (actual code):
// Echo back confirmation (real implementation would process sync ops)
outgoing.send(Frame.Text("""{"type":"ack","storeId":"$storeId"}"""))
```

**All checklist items from TODO-007g are incomplete:**
| Gap | Description |
|-----|-------------|
| CRDT merge handler | Server-side Last-Write-Wins or CRDT resolution not implemented |
| Pull endpoint (`GET /sync/pull`) | No REST endpoint to fetch pending server changes |
| Push endpoint (`POST /sync/push`) | No REST endpoint to receive client operations |
| WebSocket push notifications | Server cannot push change notifications to connected clients |
| Batch processing | No batching of sync operations |
| Conflict detection | No conflict detection or `conflict_log` writes from server |
| `version_vectors` management | `version_vectors` table exists in SQLDelight schema but server never reads/writes it |
| Store-scoped isolation | Multi-store data isolation not enforced server-side |
| Sync queue persistence | Server has no persistent sync queue |
| Authentication gating on sync WebSocket | No JWT validation on WebSocket upgrade |

**Impact:** The offline-first sync pipeline — a core architectural requirement — cannot function end-to-end. Client stores operations locally and pushes to `sync_queue`, but the server does nothing with them.

---

### TODO-008: SEO & ASO

**Status: ✅ In-repo code complete — external steps pending (user actions)**

**Implemented in `website/`:**
- `robots.txt` in `website/public/`
- `sitemap.xml` generation configured
- `_headers` file with security headers
- JSON-LD structured data in page components
- `analytics.ts` with GA4 + GTM integration
- `BaseLayout.astro` with GA4/GTM scripts

**Pending (external/user actions — not trackable in repo):**
- Google Search Console (GSC) domain verification — requires DNS TXT record (Cloudflare dashboard)
- Play Store ASO: screenshots, feature graphic, short/long descriptions, localization — Google Play Console
- Lighthouse CI in pipeline (not added to `ci-website.yml`)

No code gaps. All remaining items are operational actions outside the repository.

---

### TODO-008a: Email Management System

**Status: ⬜ 0% — NOT STARTED**

The TODO-008a document describes a 7-day plan covering:
- Stalwart mail server deployment
- Chatwoot customer support integration
- Transactional email via Resend or Amazon SES
- Email template engine
- Unsubscribe flow
- SPF/DKIM/DMARC DNS configuration

**None of this exists in the codebase:**
| Gap | Evidence |
|-----|----------|
| Stalwart mail server | No service in `docker-compose.yml`, no config directory |
| Chatwoot | No service in `docker-compose.yml`, no config |
| Transactional email client | No Resend SDK, no SES client in `backend/` |
| Email template files | No `.html` or template files anywhere in `backend/` |
| Unsubscribe endpoint | No route in any backend service |
| SPF/DKIM/DMARC | DNS configuration — external; not trackable in repo |

**Impact:** No password reset emails, no welcome emails, no receipt delivery by email, no support ticketing.

---

### TODO-009: Ktor Security Hardening

**Status: ✅ 100% COMPLETE**

All items verified in codebase:
- `System.setProperty("jdk.serialFilter", "!*")` in `backend/api/Application.kt`
- CIO engine (not Netty) in `backend/api/build.gradle.kts`
- All 7 security headers in `backend/api/plugins/Security.kt`
- `ContentLengthLimit.kt` plugin active
- `ValidationScope.kt` in `backend/common/`
- OWASP dependency check with `failBuildOnCVSS=7.0f`
- `.github/dependabot.yml` for automated CVE updates
- Multi-stage Dockerfiles for all backend services
- `config/seccomp/ktor.json` seccomp profile
- `ForbiddenMethodCall` rules active in `config/detekt/detekt.yml`

No gaps.

---

### TODO-010: Security Monitoring & Automated Response

**Status: 🟡 ~85% in-repo — External configuration pending**

**Implemented in repo (✅):**
- `config/falco/zyntapos_rules.yaml` — custom Falco rules for container runtime monitoring
- `config/falco/falcosidekick.yaml` — Falcosidekick configuration
- `config/falco/response-handler.sh` — automated response script
- `.github/workflows/sec-canary-response.yml` — canary token breach workflow
- `config/cloudflare/tunnel-config.yml` — Cloudflare Tunnel config (template)
- `docker-compose.yml` includes `falcosidekick` and `cloudflared` services

**Gaps (external configuration or incomplete files):**
| Gap | Description | Location |
|-----|-------------|----------|
| CF Zero Trust access policies | Requires Cloudflare dashboard configuration — not in repo | External |
| Bot Fight Mode + WAF rules | Requires Cloudflare dashboard — not in repo | External |
| Snyk Monitor integration | Requires `snyk monitor` step in `ci-gate.yml` and snyk.io project setup | `.github/workflows/ci-gate.yml` |
| Canary tokens embedded in source | TODO-010 requires tokens in source files; none found | Source files |
| `config/cloudflare/tunnel-config.yml` | Has literal `<TUNNEL_ID>` placeholder — not yet a real tunnel | `config/cloudflare/tunnel-config.yml` |
| Falcosidekick → Slack wiring | `SLACK_WEBHOOK_URL` GitHub Secret exists, but `falcosidekick.yaml` has placeholder value | `config/falco/falcosidekick.yaml` |

**Actionable in-repo gaps (can be fixed without external dashboard access):**
1. Add `snyk monitor` step to `ci-gate.yml`
2. Embed canary tokens in designated source files
3. Replace `<TUNNEL_ID>` in `tunnel-config.yml` with actual tunnel ID once provisioned

---

### TODO-011: Firebase Analytics & Sentry Integration

**Status: 🟡 Partially complete — Sentry done, Firebase not started**

**Implemented (✅):**
- Sentry Android SDK: `SentryAndroid.init{}` in `androidApp/ZyntaApplication.kt`
- Sentry backend: `Sentry.init{}` in `backend/api/Application.kt`
- `SENTRY_DSN_API`, `SENTRY_DSN_LICENSE`, `SENTRY_DSN_SYNC` GitHub Secrets configured

**Not implemented (❌):**
| Gap | Description |
|-----|-------------|
| Firebase Android SDK | Not in `androidApp/build.gradle.kts` dependencies |
| `google-services.json` | Not in `androidApp/` (expected — git-ignored, but `GOOGLE_SERVICES_JSON` secret implies it should be injected in CI; no injection step found) |
| `FirebaseAnalytics` initialization | No Firebase import in `ZyntaApplication.kt` |
| Custom event tracking | No `logEvent()` calls anywhere in feature modules |
| Firebase Crashlytics | Not integrated (Sentry is used instead — intentional per architecture, but TODO-011 lists Crashlytics as a gap) |
| GA4 User Properties | No user property setup for role, edition, store size |
| Phase 2 analytics events | Screen views, funnel events, feature flag exposure — none instrumented |
| Phase 3 analytics | Retention, LTV, cohort tagging — none instrumented |

---

## 4. Phase 3 — Enterprise

**No TODO files exist in `docs/todo/` for Phase 3 features** beyond what is referenced in TODO-000's roadmap. Phase 3 is documented as "planned" in `TODO-000` with these categories:

- Multi-store KPI Dashboard & Inter-Store Transfers (referenced in TODO-003/007)
- Staff, Shifts & Payroll
- E-Invoice & IRD Submission (`:composeApp:feature:accounting` module exists as scaffold)
- CRDT Conflict Resolution & Offline Sync V2
- Coupon & Promotion Rule Engine (`:composeApp:feature:coupons` module exists as scaffold)
- Customer Loyalty & GDPR Export (`:composeApp:feature:customers` module exists as scaffold)

Since there are no dedicated Phase 3 TODO files in `docs/todo/`, this audit does not assess implementation completeness of Phase 3 features. The gap is: **Phase 3 TODO files have not been written yet**, which itself is a documentation gap.

---

## 5. Priority / Unblocking Order

```
CRITICAL (blocks multiple features / ongoing operations):
  1. TODO-007g — Implement sync engine server-side (WebSocket push/pull + CRDT merge)
     Blocking: Entire offline-first sync pipeline; all data eventually consistent

  2. TODO-006 — Implement remote diagnostic access
     Blocking: Enterprise support; admin panel WebSocket relay already exists

  3. TODO-008a — Deploy email infrastructure (Stalwart + transactional email)
     Blocking: Password reset, receipt delivery, support ticketing

HIGH (significant user/product impact):
  4. TODO-011 — Add Firebase Analytics SDK to Android
     Blocking: Product telemetry, funnel analysis, feature flag experiments

  5. TODO-007e — Create API documentation site
     Blocking: External developer adoption, partner integrations

  6. TODO-010 — Add Snyk Monitor step to CI + embed canary tokens + fix tunnel-config
     Non-blocking but completes the security posture

MEDIUM (completes in-progress work):
  7. TODO-007a — Complete admin panel Days 5/7 frontend work (security dashboard, OTA)
  8. TODO-007f — Complete frontend Days 3/5 (session management UI, security audit log)
  9. TODO-007c — Configure Uptime Kuma monitors and Grafana dashboards (operational)

LOW (external actions / user-owned):
  10. TODO-007b — DNS cutover to Cloudflare Pages (Cloudflare dashboard action)
  11. TODO-008 — GSC domain verification + Play Store ASO (external consoles)
  12. TODO-010 — CF Zero Trust + Bot Fight Mode (Cloudflare dashboard actions)

DOCUMENTATION (Phase 3 prep):
  13. Write Phase 3 TODO files (Multi-store, Staff/HR, E-Invoice, CRDT V2, Coupons, Loyalty)
```

---

## 6. Summary Table

| TODO | Title | Status | Gap Type |
|------|-------|--------|----------|
| 001 | Single Admin Account | ✅ Complete | — |
| 002 | Remove Signup | ✅ Complete | — |
| 003 | Edition Management Wiring | ✅ Complete | — |
| 004 | Enterprise Audit Logging | ✅ Complete | — |
| 005 | Modern Dashboard & Nav | ✅ Complete | — |
| 006 | Remote Diagnostic Access | ⬜ Not started | All 11 items; module, model, validator, WebSocket, consent |
| 007 | Infrastructure & Deployment | 🟡 ~75% | Sub-tasks 007e and 007g are the critical gaps |
| 007a | React Admin Panel | 🟡 ~85% | Days 5/7 frontend work; E2E tests |
| 007b | Astro Marketing Website | ✅ Code complete | DNS cutover (user action) |
| 007c | Monitoring Setup | 🟡 ~70% | Uptime Kuma UI config, Grafana dashboards (operational) |
| 007d | Automated Backup | ✅ Complete | — |
| 007e | API Documentation Site | ⬜ Not started | No OpenAPI spec, no Redoc/Swagger hosting |
| 007f | Admin Panel CF + Custom Auth | 🟡 ~75% | Frontend Days 3/5/6/7; CF dashboard action |
| 007g | Sync Engine Server-Side | ⬜ ~5% (stub) | All sync logic; only ACK stub exists |
| 008 | SEO & ASO | ✅ Code complete | GSC + Play Store (external); Lighthouse CI |
| 008a | Email Management System | ⬜ Not started | Stalwart, Chatwoot, transactional email, templates |
| 009 | Ktor Security Hardening | ✅ Complete | — |
| 010 | Security Monitoring | 🟡 ~85% | Snyk CI, canary tokens, tunnel-config placeholder, CF dashboard |
| 011 | Firebase Analytics & Sentry | 🟡 ~40% | Firebase SDK not integrated; Sentry done |
| Phase 3 | (no TODO files yet) | ⬜ Planned | TODO files not written; Phase 3 feature scaffolds exist |

---

## Update — 2026-03-11: Gap Fill Applied

All gaps addressed by branch `claude/audit-api-documentation-ileya`. See plan file at `/root/.claude/plans/vast-coalescing-karp.md` for full implementation details.

**Changes applied:**

| Gap | Resolution |
|-----|-----------|
| GAP-1 (`SyncEngine.applyDeltaOperations()`) | Fully implemented — routes 7 entity types to correct `RepositoryImpl.upsertFromSync()`. 5 new `upsertFromSync()` methods added. Koin DI updated with 6 concrete repo params. |
| GAP-2/GAP-3 (API docs out of date) | `docs/api/README.md` corrected (8 contract fixes). `docs/api/openapi.yaml` created (full OpenAPI 3.0.3 spec for all 3 services). |
| GAP-4 (No OpenAPI spec) | `docs/api/openapi.yaml` — 60+ endpoints across api/license/sync services. |
| GAP-5 (Remote Diagnostics TODO-006) | `DiagnosticSession` domain model, `DiagnosticTokenValidator` (shared/security), V8 migration, `DiagnosticSessionService` + `AdminDiagnosticRoutes` (backend), `:composeApp:feature:diagnostic` module, `ZyntaRoute.DiagnosticConsent`. |
| GAP-6 (Email System TODO-008a) | `EmailService` (Ktor HTTP to Resend API), V7 migration (email_preferences + password_reset_tokens), forgot-password/reset-password endpoints, welcome/ticket email wiring, `UnsubscribeRoutes`. `AppConfig` email env vars added. |
| GAP-7 (Firebase TODO-011) | Firebase BOM + Analytics + Crashlytics deps in `androidApp`, google-services plugins in root + androidApp, `google-services.json` placeholder, Firebase init in `ZyntaApplication`. |
| GAP-8 (Security Monitoring) | Snyk advisory job added to `ci-gate.yml`. `<TUNNEL_ID>` replaced with `${CF_TUNNEL_ID}` in tunnel-config.yml. Canary tokens embedded in 3 source files. |

---

## Update — 2026-03-12: One-Shot Remediation (Sprint 1+2)

Applied from branch `claude/kmp-architecture-audit-66Ypu`. Implements 24 of 26 Sprint 1+2 tasks from the remediation plan (`docs/audit/one-shot-remediation-plan-2026-03-12.md`).

### Admin Panel — Session Stability (Sprint 1: 11/11 tasks)

| Task | Resolution |
|------|-----------|
| S1-1 | Token refresh interceptor with request queue for concurrent 401s |
| S1-2 | Moved `setUser`/`clearUser` out of `queryFn` into `useEffect` |
| S1-3 | Removed redundant `navigate()` calls on login |
| S1-4 | Wrapped `changePassword` in try/catch |
| S1-5 | "Revoke All Sessions" now redirects own user to login |
| S1-6 | TanStack Query is auth source of truth; Zustand synced via useEffect |
| S1-7 | Removed `storeLoading` from root loading check |
| S1-8 | Spinner shown on all pages while `statusLoading` |
| S1-9 | Added `.json()` to `useAdminMfaDisable` + `useRevokeSessions` |
| S1-10 | Narrowed `setUser` to non-null type |
| S1-11 | Reduced `staleTime` to 1min, enabled `refetchOnWindowFocus` |

### Backend — Cross-Module Alignment & Security (Sprint 2: 13/15 tasks)

| Task | Resolution |
|------|-----------|
| S2-1 | Centralized JWT config (`JwtDefaults`) in `backend/common` |
| S2-2 | Centralized `ErrorResponse` model in `backend/common` |
| S2-4 | Pinned `kotlinx-datetime` to 0.7.1 in license build |
| S2-5 | Centralized Exposed table objects into `api/db/Tables.kt` |
| S2-6 | Dual-hash password migration (SHA-256 → bcrypt rolling upgrade) |
| S2-7 | Sync payload field-level validation for PRODUCT/CUSTOMER/etc |
| S2-8 | Heartbeat replay protection in `LicenseService` |
| S2-9 | POS token revocation check on JWT validation |
| S2-10 | `storeId` claim validated against DB on sync operations |
| S2-11 | `ADMIN_PANEL_URL` validated at startup |
| S2-13 | Service/version indicators added to all health endpoints |
| S2-14 | Redis retry made async (suspend fun + coroutine launch) |
| S2-15 | Sentry: 10% trace sampling + PII scrubbing (all 3 services) |

### Deferred to next session

| Task | Reason |
|------|--------|
| S2-3 | Timestamp standardization — low risk, would touch many files |
| S2-12 | License validation in API auth — requires cross-service HTTP client |

### Infra (Sprint 4 early)

| Task | Resolution |
|------|-----------|
| S4-8 | HSTS + Permissions-Policy headers added to `admin-panel/nginx.conf` |
| S3-14 | HikariCP pool size made configurable via environment variables |

**Sync engine status:** Now ~60% complete (was ~5%). Full push/pull REST endpoints, LWW conflict resolution, Redis pub/sub fan-out, and field-level payload validation are all implemented.
