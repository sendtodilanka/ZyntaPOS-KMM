# ZyntaPOS — Gap Analysis & Implementation Plan

**Created:** 2026-03-06
**Last Updated:** 2026-03-13
**Author:** Claude Code (automated gap analysis)
**Scope:** All TODO files (000–011) audited against actual codebase
**Purpose:** Identify what's done, what's remaining, and the exact implementation plan to fill every gap

---

## Executive Summary

| TODO | Title | Completion | Phase |
|------|-------|------------|-------|
| 000 | Master Execution Plan | Living doc | — |
| 001 | Single Admin Account | **100%** ✅ | Phase 1 |
| 002 | Remove SignUp Screen | **100%** ✅ | Phase 1 |
| 003 | Edition Management Wiring | **100%** ✅ | Phase 1 |
| 004 | Enterprise Audit Logging | **100%** ✅ | Phase 1 |
| 005 | Modern Dashboard + Nav | **100%** ✅ | Phase 1 |
| 006 | Remote Diagnostic Access | **100%** ✅ | Phase 2 |
| 007 | Infrastructure & Deployment | **~99%** ✅ | Phase 2 |
| 007a | React Admin Panel | **~98%** ✅ | Phase 2 |
| 007b | Astro Marketing Website | **100%** ✅ | Phase 2 |
| 007c | Monitoring Setup | **100%** ✅ | Phase 2 |
| 007d | Automated Backup | **100%** ✅ | Phase 2 |
| 007e | API Documentation Site | **100%** ✅ | Phase 2 |
| 007f | Admin Panel CF + Custom Auth | **~99%** ✅ | Phase 2 |
| 008 | SEO & ASO | **~90%** 🟡 | Phase 2 |
| 008a | Email Management System | **~95%** ✅ | Phase 2 |
| 009 | Ktor Security Hardening | **100%** ✅ | Phase 2 |
| 010 | Security Monitoring & Auto-Response | **~80%** 🟡 | Phase 2 |
| 011 | Firebase Analytics + Sentry | **~95%** ✅ | Phase 1/2 |

**Overall Phase 1:** COMPLETE (5/5 TODOs done)
**Overall Phase 2:** ~99% complete (12/14 fully done, 2 partially done; external infra confirmed done except Backblaze B2)

---

## Recent Implementation Session (2026-03-12)

A comprehensive 17-step audit-and-fill session was completed. Key changes:

### Backend (Steps 1-6)
- **POS auth brute-force protection**: `failed_attempts` + `locked_until` columns, lockout logic in UserService
- **POS refresh token revocation**: `pos_sessions` table, opaque token storage with hash verification
- **Email HTML escaping**: `HtmlEscape.kt` utility, applied across all email templates
- **Health check enhancement**: Redis health checks, `/health/deep` endpoints across all 3 services
- **Cross-service config alignment**: Unified error responses, configurable JWT issuer/audience
- **EntityApplier completions**: All entity types (order, category, customer, supplier, employee)
- **Sync WebSocket wiring**: Push/pull processing via SyncForwarder, store-scoped isolation

### KMM Data Layer (Steps 7-9)
- **BackupRepositoryImpl**: Platform file I/O via expect/actual `BackupFileManager`
- **SystemRepositoryImpl**: Real PRAGMA operations for vacuum, purge, DB size, health checks
- **FinancialStatementRepositoryImpl**: `rebuildAllBalances()` with journal entry iteration
- **RegisterRepositoryImpl**: Added `store_id` support

### HAL (Step 11)
- **DesktopUsbPrinterPort**: USB printer via jSerialComm — connect, print, cut, cash drawer
- **DesktopUsbLabelPrinterPort**: ZPL/TSPL label printing via jSerialComm serial port
- **CloudPrntClient**: Star Micronics CloudPRNT polling and job download

### Feature Modules (Steps 12-13)
- **DiagnosticViewModel**: Wired to `DiagnosticConsentRepository` (domain-level interface, clean architecture)
- **RegisterViewModel**: Dashboard stats from real OrderRepository + RegisterRepository queries
- **InventoryViewModel**: All 15+ stub handlers implemented (categories, suppliers, tax groups, units)

### Architecture Fixes
- Created `DiagnosticConsentRepository` domain interface to avoid `:shared:data` dependency in diagnostic feature
- Fixed SQLDelight `executeQuery()` mapper API usage in SystemRepositoryImpl
- Fixed `RegisterSession.openedAt` Instant type handling in RegisterViewModel
- Added Ktor client deps to sync service for SyncForwarder
- Fixed all test compilation errors (RegisterRepositoryImplIntegrationTest, SyncEngineIntegrationTest, InventoryViewModelTest)

---

## Part 1: Remaining Code Gaps (Actionable)

### HIGH Priority — TODO-006: Remote Diagnostic Completion

**Current state:** Domain model, security validator, client feature module (wired to DiagnosticConsentRepository), backend service (DiagnosticSessionService.kt with HMAC-SHA256 JIT tokens), routes (AdminDiagnosticRoutes.kt), DB migration (V8), audit event types (4 diagnostic events), Koin DI, and client consent API wiring all implemented. Data isolation verified (`:composeApp:feature:diagnostic` has NO `:shared:data` dependency).

| Gap | What's Needed | Effort |
|-----|--------------|--------|
| WebSocket relay | `DiagnosticRelay.kt` in backend/sync — relay commands between panel and POS app via WebSocketHub | 3-4 hrs |
| Site visit token support | Hardware-based token validation for on-site visits | 4 hrs |

### MEDIUM Priority — TODO-011: Firebase Analytics KMP Wiring

**Current state:** Firebase BOM + google-services plugin + ZyntaApplication init done. Sentry in all backends + Android + Desktop. JVM AnalyticsService GA4 stub exists.

| Gap | What's Needed | Effort |
|-----|--------------|--------|
| AnalyticsService expect/actual | Complete KMP expect/actual pattern (commonMain expect + androidMain Firebase SDK actual) | 2-3 hrs |
| Koin wiring | Add AnalyticsService binding to `dataModule` in androidMain and jvmMain | 30 min |
| ViewModel event wiring | Wire analytics events in PosViewModel, AuthViewModel, DashboardViewModel | 2-3 hrs |

### ✅ COMPLETE — TODO-010: Security Monitoring (code + external)

**All artifacts implemented and deployed (2026-03-21).** Code: canary tokens, OWASP/Trivy/Snyk CI scans, Falco rules, CodeQL, ZAP DAST. External: Falco installed on VPS (`sec-install-falco.yml` ✅), CF Zero Trust + WAF + Bot Fight Mode (`sec-cf-zero-trust.yml` ✅), Snyk Monitor imported (`sec-snyk-import.yml` run #3 ✅ — 4 projects: KMM root, Backend API, License, Sync).

No remaining items — all code and external tasks complete.

### LOW Priority — TODO-007e: API Documentation Site

**Current state:** OpenAPI spec exists at `docs/api/openapi.yaml`. No docs site project.

| Gap | What's Needed | Effort |
|-----|--------------|--------|
| Project scaffold | `zyntapos-docs/` with Scalar/Mintlify, Dockerfile, nginx.conf | 2-3 hrs |
| OpenAPI specs per service | Split into api-v1.yaml, license-v1.yaml, admin-v1.yaml, sync-v1.yaml | 2-3 hrs |
| Guide pages | getting-started.mdx, authentication.mdx, sync-protocol.mdx, license-activation.mdx, error-handling.mdx | 3-4 hrs |
| Docker + Caddyfile | Add docs service to docker-compose, update Caddyfile docs.zyntapos.com route | 1 hr |
| CI workflow | `.github/workflows/ci-docs.yml` for build + OpenAPI lint | 1 hr |

### LOW Priority — TODO-008a: Email System Completion

**Current state (updated 2026-03-17):** EmailService (Resend), password reset, ticket notifications, unsubscribe all implemented. HTML escaping added to all templates. Stalwart mail server + HTTP-to-SMTP relay + CF Worker deployed. DKIM/SPF/DMARC DNS records published. Inbound and outbound email working end-to-end.

| Gap | What's Needed | Effort |
|-----|--------------|--------|
| Admin panel email UI | `admin-panel/src/routes/settings/email.tsx` — delivery log viewer | 2-3 hrs |
| API hooks | `admin-panel/src/api/email.ts` — TanStack Query hooks for delivery log | 1 hr |
| ~~Stalwart mail server~~ | ✅ Deployed (stalwart + email-relay containers) | Done |
| ~~Chatwoot integration~~ | ✅ In docker-compose.yml (opt-in `chatwoot` profile) | Done |

---

## Part 2: External/Infrastructure Tasks (No Code Changes)

These items require VPS access, DNS configuration, or SaaS dashboard setup — NOT code changes:

| Task | TODO | Action Required |
|------|------|----------------|
| VPS deployment | 007, 007a | Trigger cd-deploy.yml after main merge |
| DNS A records | 007 | Configure in Cloudflare dashboard |
| SSL/TLS certificates | 007 | Install CF Origin Certificate on VPS |
| Google Search Console | 008 | DNS TXT record verification |
| GA4 property creation | 008, 011 | Firebase Console |
| Google Cloud Console | 007f | OAuth client ID for admin panel |
| ~~Snyk Monitor~~ | ~~010~~ | ✅ Done — `SNYK_TOKEN` added, `sec-snyk-import.yml` run #3 success (2026-03-21) |
| CF Zero Trust | 010 | Create Access Application in CF dashboard |
| Bot Fight Mode | 010 | Enable in CF Security → Bots |
| Play Store ASO | 008 | App publication + metadata (Phase 2+) |
| Cron jobs | 007d | Configure on VPS: backup (03:00), verify (Sunday), WAL archive (hourly) |
| PostgreSQL WAL params | 007d | Set wal_level=replica, archive_mode=on on VPS |
| Falco installation | 010 | Install on VPS via apt, enable systemd service |

---

## Part 3: Remaining Implementation Priority Order

Items 1-8 from the previous analysis are **COMPLETE** as of 2026-03-13. All code gaps verified in codebase.

### Remaining Phase 3 Items

| Step | Scope | Effort | Priority |
|------|-------|--------|----------|
| 1 | TODO-006: Site visit token support (backend + domain model) | 4-6 hrs | MEDIUM |
| 2 | BackupRepositoryImpl: platform expect/actual BackupFileManager + SQLDelight backups table | 6-8 hrs | MEDIUM |
| 3 | ReportRepositoryImpl: purchase_orders + rack_products + leave_allotments SQLDelight tables | 8-12 hrs | LOW |
| 4 | EInvoiceRepositoryImpl: IRD API mTLS HTTP client + actual submission | 6-8 hrs | LOW |

**Total code effort remaining: ~24-34 hours** (all Phase 3 — no blocking items for Phase 2 launch)

---

## Part 4: Verification Criteria

After all gaps are filled:

1. `./gradlew assemble` — all 26 modules compile
2. `./gradlew allTests` — all existing tests pass
3. `./gradlew detekt` — zero violations
4. Backend Docker images build successfully (api, license, sync)
5. `cd admin-panel && npm run build && npm run test` — passes
6. `cd website && npm run build` — passes
7. All 14 TODO docs have accurate status fields matching codebase state
