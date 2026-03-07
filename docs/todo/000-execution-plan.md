# TODO-000 — ZyntaPOS Master Execution Plan

**Last updated:** 2026-03-06
**Status:** Living document — update whenever a TODO is added, completed, or re-phased

This document is the single source of truth for **what to work on, in what order, and why**. It maps all TODO files to the four development phases defined in `CLAUDE.md` and shows the dependency chain between them.

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Completed — validation checklist fully checked off |
| 🟡 | In Progress — partially done or actively being worked on |
| ⬜ | Pending — not started yet |
| 🔒 | Blocked — cannot start until a dependency is resolved |
| ➡ | "Must come before" (dependency arrow) |

---

## Phase 0 — Foundation

**Status:** ✅ COMPLETE
**CLAUDE.md scope:** Build system, module scaffold, secrets, CI skeleton

No TODO files. This phase is done. All 26 Kotlin modules are scaffolded, Gradle version catalog is locked, GitHub Actions CI/CD is wired, and the SQLDelight 36-table schema is in place.

---

## Phase 1 — MVP

**Status:** ✅ COMPLETE
**CLAUDE.md scope:** Single-store POS, offline-first sync, core feature set
**Goal:** A production-ready single-store POS that can be handed to a real retailer

### TODO Summary

| # | Title | Status | Priority | Notes |
|---|-------|--------|----------|-------|
| [001](001-single-admin-account-management.md) | Single Admin Account Management | ✅ Done | — | ADR-005 enforced |
| [002](002-remove-signup-screen-and-account-flow.md) | Remove SignUp Screen | ✅ Done | — | ADR-005 enforced |
| [005](005-modern-dashboard-and-hierarchical-nav-drawer.md) | Modern Dashboard + Nav Drawer | ✅ Done | — | 3-breakpoint nav, animated KPI cards |
| [003](003-edition-management-wiring.md) | Edition Management Nav Wiring | ✅ Done | — | No more placeholder screens in the app |
| [004](004-enterprise-audit-logging.md) | Enterprise Audit Logging | ✅ Done | — | ~40 event types, SHA-256 hash chain, integrity verifier, date range filter, CSV export, Kermit→SQLite bridge |

### Execution Order (Phase 1)

```
TODO-005  →  TODO-003  →  TODO-004 (Part 1)
```

**Step 1 — TODO-005: Modern Dashboard + Hierarchical Nav Drawer**
- Highest visual impact; sets the UX standard for all other screens
- Implement the 3-breakpoint nav drawer (Compact / Medium / Expanded) and animated dashboard cards
- ~29 validation checklist items
- No external dependencies

**Step 2 — TODO-003: Edition Management Nav Graph Wiring**
- Wire the existing `EditionManagementScreen` composable into the nav graph (replace placeholder)
- 12 checklist items; straightforward wiring task
- Unblocked now (screen already implemented; just nav plumbing needed)
- Phase 1 scope only: no license-server calls yet (those are Phase 2 / TODO-007)

**Step 3 — TODO-004: Enterprise Audit Logging (Part 1 only)**
- Expand `AuditEventType` to ~40 types
- Add before/after value fields to `AuditEntry`
- Update SQLDelight schema; wire audit calls into feature ViewModels
- Create `operational_logs` table (Tier 2 logging)
- **Stop here for Phase 1** — hash chain verification, brute-force detection, log export are Phase 3

### Phase 1 Exit Criteria
- [x] App runs on Android tablet with no placeholder screens
- [x] All 29 dashboard checklist items pass
- [x] All 12 edition management checklist items pass
- [x] Audit log captures LOGIN, ORDER_CREATED, PAYMENT_PROCESSED, INVENTORY_ADJUSTED (~40 event types)
- [x] SHA-256 hash chain computation + integrity verification implemented
- [x] Date range filter, CSV export, Kermit→SQLite bridge operational
- [x] Detekt passes with zero violations
- [x] Full CI pipeline green (`./gradlew clean test lint assembleDebug`)

---

## Phase 2 — Growth

**Status:** 🟡 IN PROGRESS
**CLAUDE.md scope:** Multi-store, CRM, promotions, CRDT sync
**Goal:** Backend infrastructure live, license system active, marketing website + Play Store listing launched

### TODO Summary

| # | Title | Status | Priority | Notes |
|---|-------|--------|----------|-------|
| [007](007-infrastructure-and-deployment.md) | Infrastructure & Deployment | 🟡 ~65% done | **P0** | VPS live, Docker Compose running, Caddy + API + License + Sync deployed, monitoring + backup done. Remaining: React panel (7a), Astro site (7b), docs site (7e), sync engine server-side (7g) |
| [007a](007a-react-admin-panel.md) | React Admin Panel | ⬜ Ready to implement | **P0** | Full 15-day (3-week) plan written. React 19 + TanStack + shadcn/ui. 10 feature areas, 40+ API endpoints. No blockers. |
| [007f](007f-admin-panel-cf-custom-auth.md) | Admin Panel: CF + Custom Auth | ⬜ Ready to implement | **P0** | 7-day plan. ZyntaPOS-branded login + backend JWT auth + MFA (TOTP) + Google SSO + brute-force protection. Replaces CF Access identity layer while keeping CF network security. Depends on 007a (panel exists). |
| [007b](007b-astro-marketing-website.md) | Astro Marketing Website | ⬜ Ready to implement | **P1** | Full 5-day plan written. Astro 5 + Tailwind on Cloudflare Pages. No blockers. |
| [009](009-ktor-security-hardening.md) | Ktor Backend Security Hardening | ✅ Done | **P0** | ValidationScope, body size limits, seccomp profile, CVSS threshold — all implemented |
| [010](010-security-monitoring-automated-response.md) | Security Monitoring & Automated Response | 🟡 ~70% done | **HIGH** | Falco rules, Falcosidekick, cloudflared tunnel done. Remaining: CF Zero Trust, Bot Fight Mode, Snyk Monitor (all CF/SaaS dashboard config) |
| [008](008-seo-and-aso.md) | SEO & ASO — Website + Play Store | 🔒 Blocked on 007b | **P1** | Items 8a/8b/8c/8f front-loaded into 007b plan. Remaining: GA4/GTM (8d), Play Store ASO (8e) |
| [006](006-remote-diagnostic-access.md) | Remote Diagnostic Access | 🔒 Blocked on 007a (panel) | **P2** | Needs WebSocket relay via admin panel |

### Execution Order (Phase 2)

```
TODO-010 (CF Zero Trust + Snyk + Canary Tokens) ← start Day 1, no blockers
  │
TODO-007  ──────────────────────────────────────────────────┐
  │ (Step 5: VPS provisioned)                                │
  │──→ TODO-010 (Falco + Falcosidekick)                      │
  │ (Step 6: Caddy running)                                  │
  │──→ TODO-010 (Cloudflare Tunnel for panel)                │ all 3 must complete
  │ (Step 7: Ktor server projects created)                   │ before Phase 2 exit
  │──→ TODO-009 (Ktor Security Hardening) ─────────────────►─┤
  │ (Step 10: sync engine sign-off)                          │
  └──────────────────────────────────────────────────────────┘
                              │
                              ▼
               TODO-008 (SEO & ASO)
                              │
                              ▼
               TODO-006 (Remote Diagnostics) ← 007 + 004
```

**Step 1 — TODO-007: Infrastructure & Deployment**
- Set up separate VPS (not the VPN VPS) for ZyntaPOS backend services
- Configure Caddy reverse proxy, PostgreSQL, Redis, Docker Compose
- Implement subdomain architecture: `api.`, `sync.`, `panel.`, `docs.`, `status.`, `www.`
- Build license server: RS256 JWT issuance, terminal activation flow, heartbeat endpoint
- Build sync engine (online pull/push; foundation for Phase 3 CRDT)
- Deploy Astro static site at `www.zyntapos.com` (basic version — content from Phase 1)
- 14-step rollout plan in the TODO; do not skip steps — order matters for security

**Step 1b — TODO-009: Ktor Backend Security Hardening** *(concurrent with TODO-007 Steps 7–10)*
- Starts when TODO-007 Step 6 is done (Docker Compose running with Caddy + PostgreSQL + Redis)
- Must complete before TODO-007 Step 10 sign-off (sync engine cannot go live unhardened)
- 8 hardening actions applied in priority order (see TODO-009 Implementation Order)
- ~1.5 days total effort; items 1–3 done in under 2 hours for immediate risk reduction

**Step 1c — TODO-010: Security Monitoring & Automated Response** *(partially unblocked from Day 1)*
- **CF Zero Trust, Snyk Monitor, Canary Tokens:** start on Day 1 of Phase 2 — no infrastructure dependencies
- **Falco + Falcosidekick:** starts after TODO-007 Step 5 (VPS provisioned with Docker + ufw)
- **Cloudflare Tunnel for panel subdomain:** starts after TODO-007 Step 6 (Caddy running)
- ~7 hrs total effort spread across Phase 2; items 1–3 take under 2 hours with zero blockers
- Adds active detection for the 4 accepted JVM risks documented in TODO-009

**Step 2 — TODO-008: SEO & ASO (www.zyntapos.com + Google Play Store)**
- Depends on: Astro site deployed (TODO-007 Step 12)
- Add all Schema.org JSON-LD to the Astro site
- Configure robots.txt, sitemap.xml, canonical tags, hreflang
- Set up Google Search Console, GA4, GTM
- Optimize Google Play listing (metadata, screenshots, Data Safety section)
- Wire Lighthouse CI into GitHub Actions for automated CWV regression detection
- See TODO-008 for the full specification

**Step 3 — TODO-006: Remote Diagnostic Access**
- Depends on: JWT RS256 auth from TODO-007, audit log from TODO-004
- Design and implement the "Lockbox Pattern" diagnostic mode
- Zero standing privileges, customer consent per session, 2-hour session limit
- Architectural firewall between diagnostic layer and business data layer

### Phase 2 Exit Criteria
- [ ] `api.zyntapos.com` serving authenticated requests
- [ ] License server issuing and validating RS256 terminal tokens
- [ ] All 25 TODO-009 validation checklist items passing
- [ ] OWASP Dependency Check passing with zero HIGH/CRITICAL CVEs
- [ ] Dependabot enabled and first batch of PRs reviewed
- [ ] All 14 TODO-010 validation checklist items passing
- [ ] `panel.zyntapos.com` protected by CF Zero Trust (verified via `curl -I` returning 403 without auth)
- [ ] Falco running on VPS with all 4 custom ZyntaPOS rules active
- [ ] Snyk Monitor connected to ZyntaPOS-KMM repo with alerts configured
- [ ] Canary tokens embedded in source and verified to fire on access (test each manually)
- [ ] `www.zyntapos.com` live with Lighthouse score ≥90 on all 4 axes
- [ ] Google Search Console verified, sitemap indexed
- [ ] Play Store listing published (or ready for submission)
- [ ] Remote diagnostic mode live and passing security audit

---

## Phase 3 — Enterprise

**Status:** ⬜ PLANNED (starts after Phase 2 exit criteria are met)
**CLAUDE.md scope:** Staff/HR, admin, e-invoicing (IRD), analytics
**Goal:** Full enterprise feature set for chains, franchises, and regulated industries

### TODO Extensions (continuing existing TODOs)

| Source | Item | Notes |
|--------|------|-------|
| TODO-003 (Phase 3) | Corporate Admin Console (web) | License transfer between outlets, remote deactivation, usage analytics |
| TODO-004 (Phase 2) | Audit log hash chain + brute-force detection | Legal-grade tamper-evident log |
| TODO-007 follow-up | CRDT conflict resolver | `ConflictResolver` currently not implemented (noted in CLAUDE.md) |
| TODO-007 follow-up | Admin panel at `panel.zyntapos.com` | Internal dashboard for Zynta staff |

### New TODOs to Create in Phase 3

These do not have TODO files yet. Create them when Phase 2 is complete:

| # | Suggested Title | CLAUDE.md Reference |
|---|-----------------|---------------------|
| 011 | Multi-Store KPI Dashboard & Inter-Store Transfers | `:composeApp:feature:multistore` |
| 012 | Staff, Shifts & Payroll | `:composeApp:feature:staff` |
| 013 | E-Invoice & IRD Submission Pipeline | `:composeApp:feature:accounting` |
| 014 | CRDT Conflict Resolution & Offline Sync V2 | `:shared:data` `ConflictResolver` |
| 015 | Coupon & Promotion Rule Engine | `:composeApp:feature:coupons` |
| 016 | Customer Loyalty & GDPR Export | `:composeApp:feature:customers` |

> **Note:** TODO-009 is the Ktor Backend Security Hardening (Phase 2). TODO-010 is the Security
> Monitoring & Automated Response layer (Phase 2). Phase 3 new TODO identifiers start at 011.

### Phase 3 Exit Criteria
- [ ] All 16 feature modules fully implemented (no placeholders)
- [ ] IRD e-invoicing integration live and certified
- [ ] CRDT sync merge logic implemented and tested
- [ ] Multi-store corporate admin console live
- [ ] 95%+ use-case test coverage across `:shared:domain`

---

## Dependency Graph

```
Phase 0 (Done)
     │
     ▼
Phase 1 ──── TODO-005 (Dashboard + Nav) ─────────┐
         └── TODO-003 (Edition Mgmt Wiring) ──────┤
         └── TODO-004 Part 1 (Audit Logging core) ┘
                              │
                              ▼
Phase 2 ──── TODO-007 (Infrastructure & Backend) ─────────────────────┐
         └── TODO-009 (Ktor Security Hardening) ← blocked on 007 S6 ──┤ all 3 concurrent
         └── TODO-010 (Security Monitoring) ← partially unblocked ─────┤ must complete before
         └── TODO-008 (SEO & ASO) ← depends on 007 infra ──────────────┤ Phase 2 exit
         └── TODO-006 (Remote Diagnostics) ← 007 + 004 ────────────────┘
                              │
                              ▼
Phase 3 ──── TODO-003 Phase 3 (Admin Console)
         └── TODO-004 Phase 2 (Hash chain)
         └── TODO-007 follow-up (CRDT resolver)
         └── TODO-011 through TODO-016 (new TODOs)
```

---

## Currently Working On

**→ Phase 2: Infrastructure & Growth**

Phase 1 is **complete** (all 5 TODOs done). Phase 2 is in progress:
- ✅ TODO-007 backend infra (VPS, Docker, Caddy, API, License, Sync, monitoring, backup)
- ✅ TODO-009 Ktor security hardening (all 4 items done)
- ✅ TODO-010 in-repo items (Falco rules, Falcosidekick, cloudflared)
- **→ Next: TODO-007b — Astro marketing website** (5-day plan ready at `docs/todo/007b-astro-marketing-website.md`)
- Then: TODO-008 SEO/ASO (partially front-loaded into 007b)
- Then: TODO-010 dashboard config items (CF Zero Trust, Snyk Monitor)
- Later: TODO-007a React admin panel → **TODO-007f CF + Custom Auth** (auth hardening after panel exists), TODO-006 remote diagnostics

---

## Cross-References

| Document | Location |
|----------|----------|
| CLAUDE.md Development Phases | `/CLAUDE.md` — "Development Phases" section |
| License architecture research | `003-edition-management-wiring.md` — "Enterprise License Architecture" section |
| License server implementation spec | `007-infrastructure-and-deployment.md` — "License System Design" (lines 212–277) |
| **Astro marketing website plan** | **`007b-astro-marketing-website.md`** — 5-day implementation plan |
| **Admin Panel CF + Custom Auth plan** | **`007f-admin-panel-cf-custom-auth.md`** — 7-day implementation plan |
| Gap analysis & fill plan | `GAP-ANALYSIS-AND-FILL-PLAN.md` — Tracks all gaps across TODOs |
| SEO & ASO full specification | `008-seo-and-aso.md` |
| Remote diagnostic security design | `006-remote-diagnostic-access.md` |
| Audit log two-tier architecture | `004-enterprise-audit-logging.md` |
| Ktor backend security hardening spec | `009-ktor-security-hardening.md` |
| Security monitoring & automated response | `010-security-monitoring-automated-response.md` |
