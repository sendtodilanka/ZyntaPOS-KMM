# TODO-000 — ZyntaPOS Master Execution Plan

**Last updated:** 2026-03-01
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

**Status:** 🟡 IN PROGRESS
**CLAUDE.md scope:** Single-store POS, offline-first sync, core feature set
**Goal:** A production-ready single-store POS that can be handed to a real retailer

### TODO Summary

| # | Title | Status | Priority | Notes |
|---|-------|--------|----------|-------|
| [001](001-single-admin-account-management.md) | Single Admin Account Management | ✅ Done | — | ADR-005 enforced |
| [002](002-remove-signup-screen-and-account-flow.md) | Remove SignUp Screen | ✅ Done | — | ADR-005 enforced |
| [005](005-modern-dashboard-and-hierarchical-nav-drawer.md) | Modern Dashboard + Nav Drawer | ⬜ Pending | **P0** | Visual quality gate for MVP launch |
| [003](003-edition-management-wiring.md) | Edition Management Nav Wiring | 🟡 In Progress | **P1** | Last "Coming soon" placeholder in the app |
| [004](004-enterprise-audit-logging.md) | Enterprise Audit Logging (Part 1) | ⬜ Pending | **P1** | Core event types only; hash chain is Phase 3 |

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
- [ ] App runs on Android tablet with no placeholder screens
- [ ] All 29 dashboard checklist items pass
- [ ] All 12 edition management checklist items pass
- [ ] Audit log captures LOGIN, ORDER_CREATED, PAYMENT_PROCESSED, INVENTORY_ADJUSTED
- [ ] Detekt passes with zero violations
- [ ] Full CI pipeline green (`./gradlew clean test lint assembleDebug`)

---

## Phase 2 — Growth

**Status:** ⬜ PENDING (starts after Phase 1 exit criteria are met)
**CLAUDE.md scope:** Multi-store, CRM, promotions, CRDT sync
**Goal:** Backend infrastructure live, license system active, marketing website + Play Store listing launched

### TODO Summary

| # | Title | Status | Priority | Notes |
|---|-------|--------|----------|-------|
| [007](007-infrastructure-and-deployment.md) | Infrastructure & Deployment | ⬜ Pending | **P0** | Foundation for all other Phase 2 items |
| [009](009-ktor-security-hardening.md) | Ktor Backend Security Hardening | 🔒 Blocked on 007 Step 6 | **P0** | Must complete before backend goes live (concurrent with 007 Steps 7–10) |
| [008](008-seo-and-aso.md) | SEO & ASO — Website + Play Store | 🔒 Blocked on 007 | **P1** | Needs Astro site infrastructure from TODO-007 |
| [006](006-remote-diagnostic-access.md) | Remote Diagnostic Access | 🔒 Blocked on 007 + 004 | **P2** | Needs JWT auth (007) and audit log (004) |

### Execution Order (Phase 2)

```
TODO-007  ──────────────────────────────────────────────────┐
  │ (Step 7: Ktor server projects created)                   │
  ↓                                                          │
TODO-009 (Ktor Security Hardening) ──────────────────────► both must complete
  │ (Must finish before TODO-007 Step 10 sign-off)           │ before Phase 2 exit
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
- Must complete before TODO-007 Step 10 sign-off (sync engine cannot go live unhardenened)
- 8 hardening actions applied in priority order (see TODO-009 Implementation Order)
- ~1.5 days total effort; items 1–3 done in under 2 hours for immediate risk reduction

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
| 010 | Multi-Store KPI Dashboard & Inter-Store Transfers | `:composeApp:feature:multistore` |
| 011 | Staff, Shifts & Payroll | `:composeApp:feature:staff` |
| 012 | E-Invoice & IRD Submission Pipeline | `:composeApp:feature:accounting` |
| 013 | CRDT Conflict Resolution & Offline Sync V2 | `:shared:data` `ConflictResolver` |
| 014 | Coupon & Promotion Rule Engine | `:composeApp:feature:coupons` |
| 015 | Customer Loyalty & GDPR Export | `:composeApp:feature:customers` |

> **Note:** TODO-009 is now the Ktor Backend Security Hardening (Phase 2). Phase 3 identifiers
> start at 010.

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
Phase 2 ──── TODO-007 (Infrastructure & Backend) ─────────────────┐
         └── TODO-009 (Ktor Security Hardening) ← blocked on 007 S6 ┤ concurrent pair
         └── TODO-008 (SEO & ASO) ← depends on 007 infra ───────────┤
         └── TODO-006 (Remote Diagnostics) ← 007 + 004 ─────────────┘
                              │
                              ▼
Phase 3 ──── TODO-003 Phase 3 (Admin Console)
         └── TODO-004 Phase 2 (Hash chain)
         └── TODO-007 follow-up (CRDT resolver)
         └── TODO-010 through TODO-015 (new TODOs)
```

---

## Currently Working On

**→ Phase 1, Step 1: TODO-005 (Modern Dashboard + Hierarchical Nav Drawer)**

After TODO-005 is complete, move to TODO-003, then TODO-004 Part 1, then declare Phase 1 done and begin Phase 2 with TODO-007.

---

## Cross-References

| Document | Location |
|----------|----------|
| CLAUDE.md Development Phases | `/CLAUDE.md` — "Development Phases" section |
| License architecture research | `003-edition-management-wiring.md` — "Enterprise License Architecture" section |
| License server implementation spec | `007-infrastructure-and-deployment.md` — "License System Design" (lines 212–277) |
| SEO & ASO full specification | `008-seo-and-aso.md` |
| Remote diagnostic security design | `006-remote-diagnostic-access.md` |
| Audit log two-tier architecture | `004-enterprise-audit-logging.md` |
| Ktor backend security hardening spec | `009-ktor-security-hardening.md` |
