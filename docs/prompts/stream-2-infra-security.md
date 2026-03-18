# Stream 2: Infra & Security — REMAINING ITEMS ONLY

**Master Plan:** `todo/missing-features-implementation-plan.md` (Sections A2, A5, A6, A7, B1, B2, B3)
**Size:** M (1-2 sessions) — all original A7/A6/A2 items are DONE; remaining items are from adjacent sections
**Conflict Risk:** LOW
**CLAUDE.md ownership:** This stream is the ONLY one that updates `CLAUDE.md`

> **STATUS (2026-03-18):** All 3 original items (A7, A6, A2) are VERIFIED COMPLETE.
> - A7: Admin JWT migrated to RS256. Zero HS256 remnants. License service validates with RS256 public key.
> - A6: OWASP in all 3 build.gradle.kts, Snyk in sec-backend-scan.yml, Falcosidekick→Slack configured.
> - A2: Email delivery log UI, useEmailDeliveryLogs() hook, WebhookRoutes.kt bounce handler — all done.
>
> This session focuses on remaining incomplete items from the broader infra/security scope.

---

## ✅ COMPLETED (do NOT re-implement)

### A7 — Admin JWT HS256→RS256 Migration — ✅ 100% DONE
- [x] `AdminAuthService.kt` line 270: `Algorithm.RSA256(publicKey, privateKey)`
- [x] `AdminJwtValidator.kt` (License service) line 22: RS256 public key verification
- [x] Zero HS256/HMAC256 references in entire backend
- [x] AppConfig.kt lines 70-71: Admin reuses POS RSA keypair
- [x] Token type claim `"type": "admin_access"` present

### A6 — Security Monitoring — ✅ 100% DONE
- [x] OWASP dependency-check v12.2.0 in api/license/sync build.gradle.kts (line 6 each)
- [x] Snyk container scanning in `sec-backend-scan.yml` lines 288-332
- [x] Trivy container scanning in `sec-backend-scan.yml` lines 227-286
- [x] Falcosidekick SLACK_WEBHOOKURL configured in docker-compose.yml line 403
- [x] Custom Falco rules in `config/falco/zyntapos_rules.yaml`

### A2 — Email Management System — ✅ 100% DONE
- [x] `admin-panel/src/routes/settings/email.tsx` — delivery log table with status/date filters + pagination
- [x] `admin-panel/src/api/email.ts` — `useEmailDeliveryLogs()` hook with query params
- [x] `AdminEmailRoutes.kt` — `GET /admin/email/delivery-logs` with RBAC gating
- [x] `WebhookRoutes.kt` — `POST /webhooks/resend` bounce/complaint handler
- [x] `EmailService.kt` — 6 email templates with HTML escaping

---

## What's STILL MISSING (implement these)

### 1. A5 — Firebase Analytics & Sentry KMP Integration (P1-HIGH)

**Status:** ~40% Complete — secrets configured, SDKs not wired

**What EXISTS:**
- `GOOGLE_SERVICES_JSON`, `GA4_MEASUREMENT_ID`, `SENTRY_DSN_*` secrets in GitHub
- Backend Sentry integration partial

**What's MISSING:**
- [ ] Firebase Android SDK dependency in `androidApp/build.gradle.kts`
- [ ] `google-services.json` decode step in CI (base64 → file)
- [ ] `FirebaseAnalytics` initialization in Android `ZyntaApplication.kt`
- [ ] `AnalyticsTracker` expect/actual interface in `:shared:core`
  - `expect`: `fun trackScreen(name: String)`, `fun trackEvent(name: String, params: Map<String, String>)`
  - `actual` Android: Firebase Analytics SDK
  - `actual` JVM: GA4 Measurement Protocol HTTP POST (or no-op stub)
- [ ] Screen view events in feature module ViewModels (POS, Auth, Dashboard, Inventory)
- [ ] Sentry initialization in all 3 backend services (api, license, sync)
- [ ] Sentry error boundary in admin panel (`ErrorBoundary` component)

**Key Files:**
- `androidApp/build.gradle.kts`
- `shared/core/src/commonMain/.../analytics/AnalyticsTracker.kt` (NEW)
- `shared/core/src/androidMain/.../analytics/AnalyticsTracker.kt` (NEW)
- `shared/core/src/jvmMain/.../analytics/AnalyticsTracker.kt` (NEW)

### 2. B1 — Admin Panel Remaining Items (~98% → 100%)

- [ ] Security dashboard page (threat overview, recent alerts, vuln scan results)
- [ ] OTA update management page (device firmware versions, push updates)
- [ ] Playwright E2E test scaffold (basic login + navigation smoke test)
- [ ] VPS deployment via GitHub Actions (Caddy static site config for panel)

### 3. B2 — Admin Panel Custom Auth Remaining (~75% → 100%)

- [ ] Session management UI (view/revoke active admin sessions)
- [ ] Security audit log page in admin panel
- [ ] IP allowlisting middleware (`X-Forwarded-For` check against allowed list)
- [ ] Login notification emails (email admin on new login from unknown IP)
- [ ] Forced password rotation policy (configurable days until password expires)

### 4. B3 — Monitoring Uptime Kuma Remaining (~70% → 100%)

- [ ] Add monitors for all 7 subdomains (api, license, sync, panel, docs, status, www)
- [ ] Slack/email alert channels configured in Uptime Kuma
- [ ] Status page branding (zyntapos.com logo, colors)
- [ ] Docker container health monitors + DB connection monitors

### 5. B5 — Mixed Timestamp Formats

- [ ] Standardize on `Instant` (kotlinx-datetime) across all backend services
- [ ] Add timestamp format validation in sync pipeline (reject non-ISO-8601)
- [ ] Document timestamp contract in API docs

### 6. E1 — CI Pipeline Enhancements (partial)

- [ ] Test coverage threshold in CI (fail if < 60%) — Kover installed but not enforced
- [ ] `google-services.json` decode step in CI workflows
- [ ] Playwright E2E tests for admin panel in CI

---

## Deferred Items (Phase 2 — do NOT implement now)

These were in the original plan but explicitly deferred:
- Email template editor in admin panel
- Email preference management UI for customers
- Email retry logic for QUEUED→SENDING failures (Resend handles retries)
- CF Zero Trust + WAF rules (Cloudflare dashboard actions, not code)

---

## Pre-Implementation (MANDATORY)

1. Read `CLAUDE.md` fully
2. Run `echo $PAT` to confirm GitHub token
3. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Commit + Push

```bash
git fetch origin main && git merge origin/main --no-edit
git add -A
git commit -m "feat(analytics): add Firebase Analytics + Sentry KMP integration [A5]

- AnalyticsTracker expect/actual interface in :shared:core
- Firebase SDK wiring in androidApp
- Screen view events in POS, Auth, Dashboard ViewModels
- Sentry initialization in backend services

Plan file updated: A5 status ~40% → ~80%"
git push -u origin $(git branch --show-current)
```
