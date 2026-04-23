# Project Status Investigation — Final Merged Report

**Investigation Date:** 2026-04-23 05:22 UTC
**Last activity on `main`:** 2026-04-01 (22 days idle)
**Last code commit:** 2026-03-29 (`2cc6980 fix(api): Ktor 3.x compile errors`)
**User absence:** ~3 weeks (matches code-freeze window)

This report merges three parallel investigation streams:
- `project-status-investigation-2026-04-23-0522-part-docs.md` (docs/audit/ADR/sprint review)
- `project-status-investigation-2026-04-23-0522-part-git.md` (git history, branches, PRs, CI)
- `project-status-investigation-2026-04-23-0522-part-code.md` (TODO comments, recent files, stubs, test gaps)

---

## TL;DR — Where things stand

- **Phase 1, 2: ✅ 100% complete.** Phase 3: ✅ ~92% code-complete.
- **All 12 numbered TODO files (001–012):** ✅ done or 95%+ partial.
- **Pipeline / repo state is clean:** 0 open PRs, 0 open issues, 0 abandoned branches, no WIP fork to recover.
- **🚨 BLOCKER: Production is DOWN since 2026-04-21T20:57** (~50 hours). `api`, `web`, `sync` all return HTTP 503. `admin.zyntapos.com` DNS not resolving. Only `license.zyntapos.com` is healthy. The autonomous `Verify Endpoints` workflow has logged **20+ consecutive failures** and there is no auto-rollback for scheduled checks.
- **Newest unaddressed deliverable:** the 2026-03-30 admin-panel functional audit produced **51 findings** (2 CRITICAL, 33 HIGH, 9 MEDIUM, 4 LOW, 3 INFO) — none have follow-up commits.
- **CodeQL** — Kotlin/JVM scan failed once on 2026-04-22.

---

## Recommended Resume Order (Next TODOs)

### 🔴 P0 — Production Incident (do this first)

**Production has been silently down for ~50 hours. Address before any new feature work.**

1. **Diagnose VPS / Docker stack on Contabo VPS**
   - Trigger `cd-verify-endpoints.yml` manually with verbose output
   - Create a one-off `vps-adhoc.yml` workflow (per CLAUDE.md "VPS SSH Access" section) and run:
     - `cd /opt/zyntapos && docker compose ps`
     - `docker compose logs --tail=200 api sync caddy`
     - `df -h && free -m`
2. **Re-deploy current main SHA** if containers are simply stopped:
   ```bash
   curl -s -X POST -H "Authorization: token $PAT" \
     -H "Accept: application/vnd.github.v3+json" \
     "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/workflows/cd-deploy.yml/dispatches" \
     -d '{"ref":"main"}'
   ```
3. **Investigate `admin.zyntapos.com` DNS** — host doesn't resolve. Likely Cloudflare DNS record removal or Pages binding lost.
4. **Re-run smoke + verify chain** to confirm green status before proceeding.

### 🟠 P1 — Admin Panel Audit Top-3 Fixes (most actionable code work)

Source: `docs/audit/admin-panel-functional-audit-2026-03-30-0554.md`

These are the freshest unaddressed code findings. The audit even includes a "Top 3 Fixes" implementation playbook — scoped, no external blockers.

| # | Fix | Severity | Files |
|---|-----|----------|-------|
| 1 | Add `ConfirmDialog` to 3 destructive actions | **2 CRITICAL** | `admin-panel/src/routes/master-products/index.tsx` (Delete) and `admin-panel/src/routes/master-products/$masterProductId.tsx` (Remove store) |
| 2 | Add `isError` checks to 18 TanStack-query pages | HIGH (D-001..D-017) | broad — admin-panel pages |
| 3 | Add `onError` toast handlers to 7 fire-and-forget mutations | HIGH (I-001..I-007) | admin-panel mutations |

After Top-3, the remaining HIGH-severity backlog from the same audit:
- A-001 Forgot/Reset password flow (no UI)
- A-002 Tax rates CRUD (no UI)
- A-003 Ticket CSV export (no UI)
- A-004 Sync token revocation (no UI)
- B-002 `dangerouslySetInnerHTML` XSS risk in email template preview
- B-001 Render-body state init in templates

### 🟡 P2 — CI/CD & Code Hygiene

1. **Investigate CodeQL Kotlin/JVM scan failure** (2026-04-22T04:34) — all 4 sub-jobs failed (Backend Sync, Backend API, Backend License, KMP Modules). Single-occurrence event; re-trigger and read failure logs.
2. **Implement `EntityApplier` dead-letter queue** — `backend/api/.../sync/EntityApplier.kt:1369`. Sync push failures are currently dropped silently.
3. **Wire `SupplierDetailScreen` purchase history** — `composeApp/feature/inventory/.../SupplierDetailScreen.kt:247` (Phase 1 read-only stub).
4. **Audit `ReportRepositoryImpl`** — header doc claims "complex enterprise reports return empty/zero stubs". Verify current state (2026-03 batches may have closed this).
5. **Replace JVM `DatabaseKeyProvider` placeholder** with OS Credential Manager (`shared/data/.../jvmMain/.../DatabaseKeyProvider.kt:107`).
6. **Replace `LoginScreen` placeholder asset** with SVG vector (`composeApp/feature/auth/.../LoginScreen.kt:397`).

### 🟢 P3 — Phase 3 Sprint 23 → 24 (planned milestones)

Source: `docs/plans/phase/p3/Phase3_Sprint{23,24}.md`

- **Sprint 23**
  - Custom RBAC Role Editor
  - Full Sinhala / Tamil i18n migration (follow-through on ADR-010 — no hardcoded UI strings)
  - Advanced Settings screens: Security Policy, Data Retention, Audit Policy
- **Sprint 24**
  - Integration QA pass
  - Version bump → `v2.0.0`
  - Tagged release (Android + Desktop installers via existing `release.yml`)

### 🔵 P4 — Test Coverage Gaps

Highest-leverage modules to bring under test (sources : tests):

| Module | Gap | Why it matters |
|--------|-----|----------------|
| `composeApp/feature/pos` | 41 : 2 | Core revenue feature |
| `composeApp/feature/reports` | 18 : 1 | Financial output |
| `composeApp/designsystem` | 62 : 2 | UI regression risk across app |
| `feature/multistore`, `feature/admin`, `feature/staff` | 15-24 : 2 | Enterprise paths |
| `feature/coupons`, `customers`, `expenses`, `media`, `register` | each ≤ 1 test | Single-test breadth |

Backend coverage is healthy (43–100% file ratios). Shared modules OK except `shared/hal` (43 src / 9 test).

### ⚪ P5 — External / Infrastructure (user action — not Claude-actionable)

These were already deferred in `missing_implementation_plan.md` and require account-owner intervention:

- Backblaze B2 bucket setup (TODO-007d follow-up)
- Cloudflare Pages DNS cutover for marketing site (TODO-007b)
- VPS deployment finalization for admin panel (TODO-007a)
- Cloudflare Zero Trust for `panel.zyntapos.com`
- IRD sandbox access (deferred to Phase 4)
- Play Store listing / GSC verification (TODO-008)
- CF Bot Fight Mode

---

## Context — How we got here

### Last 2 weeks of activity (Mar 22 → Apr 1)

The pre-pause sprint focused almost entirely on **adversarial security audit + remediation** (~25 PRs):

- **Backend security:** 13 findings remediated (PR #607); 8+ rounds of CI/CD pipeline adversarial audit (PRs #594–#601)
- **Firebase removal (ADR-012)** — completed end-to-end including data layer
- **TLS pinning (ADR-011)** — Signed Pin List rotation strategy
- **CVE patches** + GitHub Security tab cleanup
- **Test coverage push** — register, multistore, settings, core, security
- **Docker hardening** — Redis UID 999, no-new-privileges fixes
- **Admin panel functional audit** (2026-03-30) — 1185 lines, 51 findings — the deliverable that closed the active session

### What is NOT a problem

- No abandoned WIP branches (squash-merge + delete-branch policy worked correctly)
- No open PRs awaiting merge / CI conflict
- No critical compilation issues (last fix was Ktor 3.x — already merged)
- No new architectural drift (12 ADRs all ACCEPTED; no fresh proposals pending)

### Recent ADRs to be aware of (likely still rolling out)

- **ADR-010** (2026-03-26) — No Hardcoded UI Strings. Migration of legacy strings to `LocalStrings.current[StringResource.KEY]` likely partial — relevant to Sprint 23 i18n.
- **ADR-011** (2026-03-28) — TLS SPKI dual-pin (leaf + intermediate); Signed Pin List as future evolution.
- **ADR-012** (2026-03-28) — Firebase removal complete. Sentry + Kermit + `FeatureRegistryRepository` are now canonical.

---

## Suggested First Session Plan

A focused 1-day resumption could look like:

1. **Morning:** Triage P0 production outage. Restore service. Confirm Verify Endpoints flips green.
2. **Midday:** Tackle the 2 CRITICAL admin-panel findings (master-products ConfirmDialog) — small, high-impact, no infra risk.
3. **Afternoon:** Either (a) batch through HIGH severity admin-panel `isError`/`onError` work, OR (b) start Sprint 23 i18n migration depending on appetite.

Total scope is small for restart; the project itself is in **good shape** — Phase 3 is ~92% code-complete and there is no architectural debt to unwind. The largest "real" risk is the **silent production outage**, which is operational, not engineering.

---

## File Locations Reference

| Need | Path |
|------|------|
| 51 audit findings (P1 source) | `docs/audit/admin-panel-functional-audit-2026-03-30-0554.md` |
| Phase 3 forward sprints | `docs/plans/phase/p3/Phase3_Sprint{1..24}.md` |
| Last "where to pick up" snapshot (predates pause) | `docs/ai_workflows/CONTINUATION-PROMPT.md` |
| Granular task tracker | `docs/ai_workflows/execution_log.md` |
| Master gap analysis | `docs/todo/missing_implementation_plan.md` |
| ADR index | `docs/adr/ADR-NNN-*.md` |
| Partial investigation reports | `docs/todo/project-status-investigation-2026-04-23-0522-part-{docs,git,code}.md` |
