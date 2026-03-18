# ZyntaPOS-KMM — Plan Creation Guideline

**Created:** 2026-03-18
**Purpose:** Standardize how implementation plans are created for this codebase.
**Audience:** Claude Code sessions, human developers, AI agents creating task plans.

> **This is the MASTER TEMPLATE.** Every new plan document MUST follow this structure.
> Before creating a plan, read this file end-to-end. No exceptions.

---

## Table of Contents

1. [Why This Guideline Exists](#why-this-guideline-exists)
2. [Plan File Location & Naming](#plan-file-location--naming)
3. [Document Structure — Required Sections](#document-structure--required-sections)
4. [Section Templates with Examples](#section-templates-with-examples)
5. [Item Writing Rules](#item-writing-rules)
6. [Dependency Graph Rules](#dependency-graph-rules)
7. [Session Scope & Size Estimation](#session-scope--size-estimation)
8. [Error Recovery Section Rules](#error-recovery-section-rules)
9. [Compliance & Cross-Reference Rules](#compliance--cross-reference-rules)
10. [Checklist Template](#checklist-template)
11. [Anti-Patterns — What NOT to Do](#anti-patterns--what-not-to-do)
12. [Plan Lifecycle — Create → Execute → Archive](#plan-lifecycle--create--execute--archive)
13. [Quick-Start Skeleton](#quick-start-skeleton)

---

## Why This Guideline Exists

Plan documents in this repo are consumed by **autonomous Claude Code sessions** that:

1. Cannot ask clarifying questions mid-execution
2. Have limited context windows (must find info quickly)
3. Run in parallel — multiple sessions read the same plan simultaneously
4. Must produce code that passes a 7-step CI/CD pipeline on first push

A poorly structured plan causes:
- Sessions implementing the wrong thing or re-implementing completed items
- Missing dependencies → broken builds
- No handoff notes → next session starts from scratch
- No error recovery → sessions waste time debugging known issues

This guideline ensures every plan is **self-contained, unambiguous, and executable**.

---

## Plan File Location & Naming

### Location

```
todo/                    → Active plans (being executed now)
docs/plans/              → Reference plans, completed plans, architecture plans
docs/plans/phase/        → Phase-level roadmaps
```

**Rule:** Implementation plans that Claude sessions actively execute go in `todo/`.
Architectural plans, audits, and completed plans go in `docs/plans/`.

### Naming Convention

```
# Active implementation plans (in todo/)
todo/<topic>-implementation-plan.md

# Examples:
todo/missing-features-implementation-plan.md
todo/sync-engine-implementation-plan.md
todo/multi-store-migration-plan.md

# Architectural/reference plans (in docs/plans/)
docs/plans/PLAN_<TOPIC>_v<version>.md

# Examples:
docs/plans/PLAN_PHASE2.md
docs/plans/PLAN_DEBUG_MODULE_v1.0.md
```

### Header Block (REQUIRED — every plan starts with this)

```markdown
# ZyntaPOS-KMM — <Plan Title>

**Created:** YYYY-MM-DD
**Last Updated:** YYYY-MM-DD
**Status:** Draft | In Progress | Completed | Archived
**Author:** <session-id or human name>
**Scope:** <one-line description of what this plan covers>
```

---

## Document Structure — Required Sections

Every implementation plan MUST have these sections **in this exact order**.
Optional sections are marked. Do not reorder — sessions scan by position.

```
1.  HEADER BLOCK                              (REQUIRED)
2.  Overview                                   (REQUIRED)
3.  Priority Legend                             (REQUIRED)
4.  Top Blockers / Prerequisites               (REQUIRED if items have blockers)
5.  Item Sections (grouped by priority/theme)   (REQUIRED — the core of the plan)
6.  Implementation Timeline                    (REQUIRED)
7.  Item Dependency Graph                      (REQUIRED if >5 items)
8.  Session Scope Guidance                     (REQUIRED if any item is L/XL size)
9.  Error Recovery Guide                       (REQUIRED)
10. Feature Coverage Matrix                    (OPTIONAL — for large feature plans)
11. How to Use This Document                   (REQUIRED)
12. Pre-Implementation Reading Order           (REQUIRED)
13. Implementation Compliance Rules            (REQUIRED)
14. Multi-Session Awareness                    (REQUIRED)
15. Implementation Session Checklist           (REQUIRED)
16. Stale Data Warning                         (REQUIRED)
17. Completed Section                          (REQUIRED — starts empty)
```

### Why This Order?

Sessions read top-to-bottom. The order is designed so that:
- **Sections 1-4:** Context loading (what, why, what's blocked)
- **Sections 5-6:** The actual work items + timeline
- **Sections 7-9:** Execution safety nets (dependencies, scope, errors)
- **Sections 10-16:** Operational rules and checklists
- **Section 17:** Archive area for done items

---

## Section Templates with Examples

### Section 1-2: Header + Overview

```markdown
# ZyntaPOS-KMM — <Title>

**Created:** 2026-MM-DD
**Last Updated:** 2026-MM-DD
**Status:** Draft — Awaiting Approval

---

## Overview

<2-4 sentences in Sinhala or English explaining:>
- What this plan covers
- How many items are in it
- What phase/sprint it belongs to
- What the end-state looks like when all items are complete
```

### Section 3: Priority Legend

Always use this exact legend for consistency across all plans:

```markdown
## PRIORITY LEGEND

| Priority | Meaning |
|----------|---------|
| P0-CRITICAL | Blocks MVP launch — must fix immediately |
| P1-HIGH | Feature-blocking — required for production readiness |
| P2-MEDIUM | Completeness — needed for full feature parity |
| P3-LOW | Enhancement — nice to have, can defer |
| PHASE-2 | Planned for Phase 2 (Growth) |
| PHASE-3 | Planned for Phase 3 (Enterprise) |
```

### Section 4: Top Blockers

```markdown
## 🔴 TOP BLOCKERS

> <Explain in 1 line why these block everything else.>

### Blocker 1: <Name> (<Item ID>) — <% Complete> | <Priority>

<3-5 bullet points explaining:>
- What exactly is blocked
- What must be done to unblock
- Impact if not resolved

### Blocker 2: ...
```

**Rules:**
- Maximum 3-5 blockers
- Each must reference an item ID from Section 5
- Include percentage completion
- Include concrete impact statement

### Section 5: Item Sections (THE CORE)

This is the most important section. Every item MUST follow this exact template:

```markdown
## SECTION <LETTER>: <THEME> (<Priority Range>)

---

### <ID>. <Title> (<TODO-ref if exists>) — <% Complete>

**Priority:** <P0-CRITICAL | P1-HIGH | P2-MEDIUM | P3-LOW>
**Impact:** <One sentence — what breaks or is missing without this>
**Modules:** <Colon-separated module paths affected>
**Size:** <S | M | L | XL>
**Est. Sessions:** <1 | 2-3 | 3-4 | 4-5>

**What EXISTS:**
- <Bullet list of what's already built — be specific with file paths>
- <Include table names, class names, route names>

**What's MISSING:**
- [ ] <Checkbox item 1 — specific, actionable, one unit of work>
- [ ] <Checkbox item 2>
- [ ] <Checkbox item 3>

**Key Files:**
```
<path/to/file1.kt>     → <what this file does>
<path/to/file2.kt>     → <what this file does>
<path/to/file3.sq>     → <what this schema does>
```

**Implementation Steps:**
1. <Step 1 — concrete action>
2. <Step 2 — concrete action>
3. <Step 3 — concrete action>
4. Write tests for <specific classes>
5. Run `<specific gradle command>` to verify
```

#### Example: Good Item

```markdown
### A1. Sync Engine Server-Side (TODO-007g) — ~60% Complete

**Priority:** P0-CRITICAL
**Impact:** Offline-first sync pipeline non-functional; client data sits in `sync_queue` unprocessed
**Modules:** `:shared:data`, `backend/api`, `backend/sync`
**Size:** XL
**Est. Sessions:** 4-5

**What EXISTS:**
- `sync_operations`, `sync_cursors`, `entity_snapshots` tables (V4 migration)
- `SyncProcessor.kt` — push processing with batch validation
- `EntityApplier.kt` — JSONB → normalized tables (ONLY handles PRODUCT type)
- KMM client: `sync_queue.sq` (outbox), `sync_state.sq` (cursor)

**What's MISSING:**
- [ ] `EntityApplier` — extend to handle ORDER type
- [ ] `EntityApplier` — extend to handle CUSTOMER type
- [ ] `EntityApplier` — extend to handle CATEGORY, SUPPLIER types
- [ ] Multi-store data isolation (validate `store_id` matches JWT claims)
- [ ] WebSocket push notifications after sync
- [ ] JWT validation on WebSocket upgrade

**Key Files:**
```
backend/api/src/main/kotlin/.../sync/EntityApplier.kt → JSONB to table mapper
backend/api/src/main/kotlin/.../sync/SyncProcessor.kt → Push endpoint logic
backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt  → WS connection manager
shared/data/src/commonMain/sqldelight/sync_queue.sq    → Client outbox
```

**Implementation Steps:**
1. Read existing `EntityApplier.kt` — understand PRODUCT handling pattern
2. Add ORDER type handler following same pattern
3. Add CUSTOMER type handler
4. Add remaining entity types (CATEGORY, SUPPLIER, STOCK_ADJUSTMENT, etc.)
5. Add `store_id` validation in `SyncProcessor.processOperations()`
6. Write tests: `EntityApplierTest.kt` for each new entity type
7. Run `./gradlew :backend:api:test` to verify
```

#### Example: Bad Item (DO NOT write like this)

```markdown
### Fix sync

Need to fix the sync engine. It doesn't work for all types.
Look at EntityApplier and add the missing stuff.
```

**Why it's bad:**
- No ID, no priority, no percentage, no size
- No "What EXISTS" — session doesn't know starting point
- No checkboxes — can't track partial progress
- No key files — session has to search entire codebase
- No implementation steps — session has to guess the approach
- "missing stuff" is not actionable

### Section 6: Implementation Timeline

```markdown
## IMPLEMENTATION TIMELINE (Suggested)

| Sprint | Focus | Items | Duration |
|--------|-------|-------|----------|
| Sprint 1 | <Theme> | A1, A2 | 2 weeks |
| Sprint 2 | <Theme> | B1, B2, B3 | 1 week |
| Sprint 3 | <Theme> | C1.1, C1.2 | 3 weeks |
```

**Rules:**
- Every item ID from Section 5 must appear exactly once
- Group by theme, not by item number
- Include realistic duration estimates
- Critical path items (from Dependency Graph) should come first

### Section 7: Item Dependency Graph

See [Dependency Graph Rules](#dependency-graph-rules) below.

### Section 8: Session Scope Guidance

See [Session Scope & Size Estimation](#session-scope--size-estimation) below.

### Section 9: Error Recovery Guide

See [Error Recovery Section Rules](#error-recovery-section-rules) below.

---

## Item Writing Rules

### The 7 Mandatory Fields Per Item

Every item MUST have these fields. Missing any field = incomplete item.

| # | Field | Purpose | Example |
|---|-------|---------|---------|
| 1 | **ID + Title** | Unique reference for commits/handoffs | `A1. Sync Engine` |
| 2 | **Priority** | Execution ordering | `P0-CRITICAL` |
| 3 | **Impact** | Why this matters | `Offline sync non-functional` |
| 4 | **Modules** | What code areas are affected | `:shared:data`, `backend/api` |
| 5 | **What EXISTS** | Starting point — prevents re-implementation | Bullet list with file paths |
| 6 | **What's MISSING** | Checkboxes — trackable work units | `- [ ] Add ORDER handler` |
| 7 | **Key Files** | Where to look — prevents codebase-wide searches | Path → purpose table |

### Optional But Recommended Fields

| Field | When to Include |
|-------|-----------------|
| **Size + Est. Sessions** | Always (helps session planning) |
| **Implementation Steps** | For M/L/XL items |
| **Handoff Note** | After partial completion by a session |
| **Completion Date** | When moving to COMPLETED section |

### Item ID Convention

```
Section A items: A1, A2, A3, ...
Section B items: B1, B2, B3, ...
Section C items: C1.1, C1.2, C2.1, ...  (category.item within section)
Section D items: D1, D2, D3, ...
Section E items: E1, E2, E3, ...
Section G items: G1, G2, ... G21  (UI/UX gap items)
Sub-items:       INV-1, INV-2, MS-1, MS-2  (module-specific gap IDs)
```

**Rules:**
- IDs are permanent — never reassign an ID to a different item
- Reference IDs in commit messages: `feat(sync): implement ORDER handler [A1]`
- Reference IDs in handoff notes: `Next session should continue A1 sub-items`

### Checkbox Rules

```markdown
- [ ] Unchecked — not started
- [x] Checked — completed (include date)
- [~] Partial — started but not finished (add note)
```

**Each checkbox = ONE unit of work that can be:**
- Completed in a single coding session
- Tested independently
- Committed as a standalone change

**DO NOT write vague checkboxes:**
```markdown
# ❌ BAD — too vague, not testable
- [ ] Fix the sync engine
- [ ] Make it work better
- [ ] Handle edge cases

# ✅ GOOD — specific, testable, one unit each
- [ ] EntityApplier — add ORDER type handler (follow PRODUCT pattern)
- [ ] EntityApplier — add CUSTOMER type handler
- [ ] SyncProcessor — validate store_id matches JWT claims
- [ ] WebSocketHub — add JWT validation on upgrade handshake
```

---

## Dependency Graph Rules

### When Required

- Plans with **>5 items** MUST have a dependency graph
- Plans with **<5 independent items** can skip it

### Required Sub-Sections

```markdown
## 🔴 ITEM DEPENDENCY GRAPH (MUST FOLLOW ORDER)

> Items have dependencies — implementing them out of order produces broken code.
> **ALWAYS check this graph before picking the next item.**

### Critical Path (must follow this order)

<ASCII diagram showing the longest dependency chain>

### Dependency Table (all items)

| Item | Depends On | Can Start When |
|------|-----------|----------------|
| A1   | _(none)_  | Immediately    |
| C1.1 | **A1**    | After A1 ≥80%  |

### Parallel Work Streams (safe to run in separate sessions simultaneously)

<List independent streams that can run concurrently>
```

### ASCII Diagram Rules

```
# Use these symbols:
──→    dependency arrow (A must finish before B)
│      vertical connector
├──→   branch (multiple items depend on same parent)
└──→   last branch

# Example:
A1 (Sync Engine)
  │
  ├──→ C6.1 (Multi-Node Sync) — BLOCKED until A1 ≥90%
  ├──→ C6.2 (Offline-First) — BLOCKED until A1 entity types
  └──→ C1.1 (Global Catalog) — needs sync for replication
         │
         ├──→ C1.2 (Store Inventory) — needs catalog base
         └──→ C2.1 (Pricing) — needs global product
```

### Dependency Table Rules

| Column | Content |
|--------|---------|
| Item | Bold ID + short name |
| Depends On | Bold dependency ID or `_(none)_` if independent |
| Can Start When | "Immediately" or "After X" with specific condition |

### Parallel Streams

Identify which items can safely run in **separate Claude sessions simultaneously**:

```
Stream 1: A1 → C6.1 → C6.2           (Sync — critical path)
Stream 2: A2, A3, A4, A5              (Infra — all independent)
Stream 3: G1-G21                      (UI fixes — all independent)
```

**Rule:** Items in the same stream are sequential. Items in different streams are parallel-safe.

---

## Session Scope & Size Estimation

### When Required

- REQUIRED if any item is **L or XL** size
- Can skip for plans where all items are S or M

### Size Definitions

| Size | Files Changed | Sessions | Typical Examples |
|------|--------------|----------|------------------|
| **S (Small)** | < 5 files | 1 session | UI fix, config change, single bug fix |
| **M (Medium)** | 5-15 files | 1 session | New feature endpoint, new screen, new use case |
| **L (Large)** | 15-30 files | 2-3 sessions | New subsystem, major refactor, multi-module feature |
| **XL (Extra Large)** | 30+ files | 3-5+ sessions | New engine, new service, cross-cutting architecture change |

### Per-Item Size Table (REQUIRED)

```markdown
| Item | Size | Est. Sessions |
|------|------|---------------|
| A1   | XL   | 4-5           |
| A2   | S    | 1             |
| B4   | XL   | 3-4           |
```

### Partial Progress Protocol (REQUIRED for L/XL items)

Include this exact template in the plan:

```markdown
### Handling Partial Progress (for L/XL items)

When a session cannot finish an item, it MUST:

1. **Mark partial progress in the item's checkbox list:**
   ```
   - [x] Sub-task A (done in session YYYY-MM-DD)
   - [x] Sub-task B (done in session YYYY-MM-DD)
   - [ ] Sub-task C
   - [ ] Sub-task D
   ```

2. **Update the item's status line:**
   ```
   ### A1. Title — ~60% Complete  →  ~75% Complete
   ```

3. **Add a handoff note at the TOP of the item section:**
   ```
   > **HANDOFF (YYYY-MM-DD):** <What was done>.
   > Next session should continue with <what's left>.
   > Tests added in `<TestFile>.kt` — run before modifying.
   > PR #NNN merged. Branch clean.
   ```

4. **Commit and push all progress** — even partial work must be on remote.
```

### Session Planning Rule (REQUIRED)

```markdown
### Session Planning Rule

At session start, after reading this file:
1. Check your assigned item's SIZE estimate
2. If XL: plan to implement 2-3 sub-items only
3. If L: plan to implement the core + tests, defer edge cases
4. If S/M: plan to complete fully in this session
5. ALWAYS leave 15 min at session end for docs + push + pipeline monitoring
```

---

## Error Recovery Section Rules

### When Required

ALWAYS required. Every plan must have an error recovery section.

### Required Sub-Sections

```markdown
## 🔴 ERROR RECOVERY GUIDE

### Build Failures

| Error | Cause | Fix |
|-------|-------|-----|
| <exact error message or pattern> | <root cause> | <specific fix command or action> |

### Test Failures

| Error | Cause | Fix |
|-------|-------|-----|
| <exact error message or pattern> | <root cause> | <specific fix command or action> |

### Pipeline Failures

| Step | Common Failure | Fix |
|------|---------------|-----|
| Step[1] Branch Validate | <failure type> | <fix> |
| Step[3+4] CI Gate | <failure type> | <fix> |

### Recovery Workflow

<bash commands for diagnosing and fixing pipeline failures>

### Known Pitfalls

<Numbered list of project-specific gotchas relevant to this plan>
```

### What to Include in Error Tables

Include errors that are **specific to the items in this plan**. Don't just copy generic errors.

**DO include:**
- Errors you've actually encountered while building similar features
- Errors caused by dependencies between items in this plan
- ZyntaPOS-specific pitfalls (datetime pin, Koin order, ADR rules, etc.)

**DO NOT include:**
- Generic Kotlin compilation errors
- OS-level issues
- Network connectivity problems

### Always Include These ZyntaPOS-Specific Pitfalls

These apply to ALL plans in this codebase:

```markdown
### Known Pitfalls (ZyntaPOS-Specific — always applies)

1. `kotlinx-datetime` MUST be `0.7.1` — never downgrade
2. `loadKoinModules(global=true)` is banned — use `koin.loadModules()`
3. `*Entity` suffix banned in `:shared:domain` — use plain names (ADR-002)
4. `:shared:domain` can ONLY depend on `:shared:core`
5. `gradle` bare command is wrong — always `./gradlew`
6. `git rebase` is banned for conflict resolution — use `git merge`
7. Never manually create PRs — Step[2] auto-creates them
8. Never skip pre-commit sync — main moves fast with multiple sessions
```

---

## Compliance & Cross-Reference Rules

### Pre-Implementation Reading Order (REQUIRED)

Every plan must tell the session what to read BEFORE implementing:

```markdown
## 🔴 MANDATORY: PRE-IMPLEMENTATION READING ORDER

### Step 1: Architecture Foundation (~10 min)
docs/adr/ADR-001 through ADR-008

### Step 2: Architecture Diagrams (~5 min)
docs/architecture/
CLAUDE.md
CONTRIBUTING.md

### Step 3: Existing Audits (~5 min)
docs/audit/

### Step 4: This Plan File
Only after Steps 1-3, begin implementing.
```

### Implementation Compliance Rules (REQUIRED)

Every plan MUST include or reference these rule categories:

1. **Architecture Compliance** — Clean Architecture layering, module dependencies
2. **MVI Pattern** — BaseViewModel, State/Intent/Effect, updateState/sendEffect
3. **DRY Principle** — Search-before-coding process, codebase exploration commands
4. **Naming Conventions** — ADR-002, Zynta* prefix, UseCase suffix, etc.
5. **Security Requirements** — SQLDelight only, Keystore, PinManager, RBAC
6. **Performance Requirements** — debounce, flatMapLatest, LazyColumn
7. **Kotlin/KMP Specific** — 100% Kotlin, version catalog, datetime pin
8. **Testing Requirements** — coverage targets, Mockative 3, Turbine
9. **CI Pipeline Compliance** — 7-step chain, monitor after every push

**For shorter plans:** You can write `See CLAUDE.md → "Common Pitfalls to Avoid" section`
instead of repeating all rules, BUT you MUST at minimum include rules specific to
the items in your plan.

### Multi-Session Awareness (REQUIRED)

```markdown
## 🔴 CRITICAL: MULTI-SESSION AWARENESS

- Multiple Claude sessions run in parallel on this repo
- `main` moves fast — ALWAYS sync before every commit
- NEVER force-push — other sessions reference your commits
- NEVER manually create PRs — Step[2] handles this
- If PR shows `mergeable=false` — sync main immediately
```

---

## Checklist Template

Every plan MUST end with a session checklist. Use this template:

```markdown
## IMPLEMENTATION SESSION CHECKLIST

> Copy-paste this checklist at the start of every implementation session.
> Steps marked ♻️ are repeated for EVERY item in the session.

═══ SESSION START (once) ═════════════════════════════════════
□ 1.  Read CLAUDE.md (full codebase context)
□ 2.  Read all ADRs in docs/adr/
□ 3.  Read docs/architecture/
□ 4.  Read this plan file
□ 5.  Run `echo $PAT` to confirm GitHub token available
□ 6.  Sync: `git fetch origin main && git merge origin/main --no-edit`

═══ PER-ITEM LOOP (repeat for each item) ♻️ ══════════════════
□ 7.  Pick highest priority unchecked item
□ 8.  Search codebase FIRST (DRY rule)
□ 9.  Implement following compliance rules
□ 10. Write tests
□ 11. Run local tests

── POST-IMPLEMENTATION: UPDATE ALL DOCS (MANDATORY) ─────────
□ 12. Update THIS plan file:
      a. Mark checkboxes [x]
      b. Update % completion
      c. Update FEATURE COVERAGE MATRIX (if exists)
      d. Move completed sections to COMPLETED
□ 13. Update CLAUDE.md if counts/routes/modules changed
□ 14. Update docs/adr/ if new architectural pattern
□ 15. Update docs/architecture/ if module deps changed

── PRE-COMMIT SYNC + COMMIT + PUSH ──────────────────────────
□ 16. Sync: `git fetch origin main && git merge origin/main --no-edit`
□ 17. Stage ALL changed files (code + docs + plan)
□ 18. Commit with conventional format + plan item ID
□ 19. Push: `git push -u origin $(git branch --show-current)`

── POST-PUSH: MONITOR PIPELINE ──────────────────────────────
□ 20. Monitor Step[1] → Step[2] until green
□ 21. Check PR for conflicts (fix if mergeable=false)
□ 22. Monitor Step[3+4] CI Gate until green
□ 23. ♻️ Go to step 7 for next item

═══ SESSION END (once) ═══════════════════════════════════════
□ 24. Final sync + push
□ 25. Verify PR is green
□ 26. Update `Last Updated` date
```

---

## Anti-Patterns — What NOT to Do

### Structure Anti-Patterns

| Anti-Pattern | Problem | Fix |
|-------------|---------|-----|
| No item IDs | Can't reference in commits or handoffs | Assign A1, B2, C3.1 IDs |
| No "What EXISTS" section | Session re-implements existing code | Always list what's already built |
| Vague checkboxes | Can't track partial progress | One checkbox = one testable unit of work |
| No key files | Session searches entire codebase | List 3-8 critical file paths |
| No dependency graph | Items implemented out of order | Add graph for >5 items |
| No size estimates | Session takes on XL item and can't finish | Tag every item S/M/L/XL |
| No error recovery | Session wastes time on known issues | Add build/test/pipeline failure tables |

### Content Anti-Patterns

| Anti-Pattern | Example | Fix |
|-------------|---------|-----|
| Describing instead of specifying | "improve the sync" | "Add ORDER type handler to EntityApplier.kt" |
| Missing percentages | "partially done" | "~60% Complete" |
| No module paths | "fix the data layer" | "`:shared:data`, `backend/api`" |
| Mixing priorities | All items are P1-HIGH | Use full P0-P3 range honestly |
| No impact statement | Item exists with no "why" | "Impact: Offline sync non-functional" |
| Steps without test step | Implementation steps end at code | Always include "Write tests for X" step |
| English-only key terms | Sinhala-only descriptions | Bilingual: Sinhala description + English technical terms |

### Process Anti-Patterns

| Anti-Pattern | Problem | Fix |
|-------------|---------|-----|
| No pre-commit sync | PR conflicts block pipeline | Include sync in checklist |
| No pipeline monitoring | Broken builds go unnoticed | Monitor after every push |
| No doc updates after impl | Next session re-implements | Steps 12-15 in checklist |
| No handoff notes for L/XL | Next session starts from scratch | Handoff protocol mandatory |
| Manual PR creation | Breaks 7-step dispatch chain | Let Step[2] auto-create |

---

## Plan Lifecycle — Create → Execute → Archive

### Phase 1: Create

1. **Audit the codebase** — search for existing implementations before listing items
2. **Group items** by priority (A=Critical, B=Medium, C=Theme, D=Future, G=UI/UX)
3. **Write each item** using the 7 mandatory fields
4. **Build dependency graph** — identify critical path and parallel streams
5. **Estimate sizes** — tag every item S/M/L/XL
6. **Add error recovery** — common failures for the items in this plan
7. **Add compliance rules** — at minimum reference CLAUDE.md, include plan-specific rules
8. **Add checklist** — copy from template, customize if needed
9. **Set status to "Draft"** — commit and push for review

### Phase 2: Execute

1. Session reads plan top-to-bottom
2. Session picks highest priority item with no unresolved dependencies
3. Session implements, tests, updates docs
4. Session marks checkboxes `[x]`, updates percentages
5. Session adds handoff notes if item not completed
6. Session commits code + doc updates in same commit
7. Repeat until all items done or session ends

### Phase 3: Archive

When ALL items in a plan are complete:

1. Update status to "Completed"
2. Add final completion date
3. Move file from `todo/` to `docs/plans/`
4. Update any references in CLAUDE.md

---

## Quick-Start Skeleton

Copy this skeleton to start a new plan. Fill in every `<placeholder>`.

```markdown
# ZyntaPOS-KMM — <Plan Title>

**Created:** <YYYY-MM-DD>
**Last Updated:** <YYYY-MM-DD>
**Status:** Draft — Awaiting Approval

---

## Overview

<2-4 sentences: what this plan covers, item count, end-state>

---

## PRIORITY LEGEND

| Priority | Meaning |
|----------|---------|
| P0-CRITICAL | Blocks MVP launch — must fix immediately |
| P1-HIGH | Feature-blocking — required for production readiness |
| P2-MEDIUM | Completeness — needed for full feature parity |
| P3-LOW | Enhancement — nice to have, can defer |

---

## 🔴 TOP BLOCKERS

### Blocker 1: <Name> (<ID>) — <% Complete> | <Priority>

<Impact description>

---

## SECTION A: <THEME> (<Priority>)

---

### A1. <Title> — <% Complete>

**Priority:** <P0|P1|P2|P3>
**Impact:** <one sentence>
**Modules:** <module paths>
**Size:** <S|M|L|XL>
**Est. Sessions:** <number>

**What EXISTS:**
- <existing code/files>

**What's MISSING:**
- [ ] <checkbox item 1>
- [ ] <checkbox item 2>

**Key Files:**
```
<path> → <purpose>
```

**Implementation Steps:**
1. <step>
2. <step>
3. Write tests for <classes>
4. Run `./gradlew <test command>`

---

## IMPLEMENTATION TIMELINE (Suggested)

| Sprint | Focus | Items | Duration |
|--------|-------|-------|----------|
| Sprint 1 | <theme> | A1, A2 | <duration> |

---

## 🔴 ITEM DEPENDENCY GRAPH (MUST FOLLOW ORDER)

### Critical Path

```
<ASCII diagram>
```

### Dependency Table

| Item | Depends On | Can Start When |
|------|-----------|----------------|
| A1   | _(none)_  | Immediately    |

### Parallel Work Streams

```
Stream 1: <items>
Stream 2: <items>
```

---

## 🔴 SESSION SCOPE GUIDANCE

### Per-Item Size Tags

| Item | Size | Est. Sessions |
|------|------|---------------|
| A1   | <size> | <sessions>  |

### Handling Partial Progress (for L/XL items)

<Include the partial progress protocol from this guideline>

### Session Planning Rule

<Include the session planning rule from this guideline>

---

## 🔴 ERROR RECOVERY GUIDE

### Build Failures

| Error | Cause | Fix |
|-------|-------|-----|
| <error> | <cause> | <fix> |

### Test Failures

| Error | Cause | Fix |
|-------|-------|-----|
| <error> | <cause> | <fix> |

### Pipeline Failures

| Step | Common Failure | Fix |
|------|---------------|-----|
| Step[1] | <failure> | <fix> |

### Known Pitfalls (ZyntaPOS-Specific)

1. `kotlinx-datetime` MUST be `0.7.1`
2. `*Entity` suffix banned in `:shared:domain`
3. `:shared:domain` can ONLY depend on `:shared:core`
4. Always `./gradlew` — never bare `gradle`
5. `git rebase` banned — use `git merge`
6. Never manually create PRs
7. Pre-commit sync is mandatory

---

## HOW TO USE THIS DOCUMENT

1. Pick items from highest priority section first
2. Mark checkboxes `[x]` when complete
3. Update `Last Updated` date after changes
4. Reference item IDs in commits: `feat(module): description [A1]`
5. Move completed sections to COMPLETED at bottom

---

## 🔴 MANDATORY: PRE-IMPLEMENTATION READING ORDER

### Step 1: Architecture Foundation (~10 min)
docs/adr/ADR-001 through ADR-008

### Step 2: Architecture Diagrams (~5 min)
docs/architecture/ + CLAUDE.md + CONTRIBUTING.md

### Step 3: Existing Audits (~5 min)
docs/audit/

### Step 4: This Plan File

---

## 🔴 MANDATORY: IMPLEMENTATION COMPLIANCE RULES

<Include or reference from CLAUDE.md. At minimum include plan-specific rules.>

---

## 🔴 CRITICAL: MULTI-SESSION AWARENESS

- Multiple sessions run in parallel
- Sync before every commit
- Never force-push, never manually create PRs
- Fix mergeable=false immediately

---

## IMPLEMENTATION SESSION CHECKLIST

<Copy the checklist template from this guideline>

---

## 🔴 WARNING: STALE DATA CAUSES RE-IMPLEMENTATION

> If you implement a feature but do NOT update this file + CLAUDE.md,
> the next session will re-implement the same feature from scratch.

---

## COMPLETED

> Move fully completed sections here.
> Format: `### [DONE YYYY-MM-DD PR #NNN] <original header>`

_(No completed items yet)_

---

*End of document*
```

---

## Validation Checklist — Use Before Finalizing Any Plan

Before marking a plan as ready for execution, verify:

```
□ Every item has all 7 mandatory fields (ID, Priority, Impact, Modules, EXISTS, MISSING, Key Files)
□ Every checkbox is specific, testable, and represents one unit of work
□ Dependency graph covers all items with >0 dependencies
□ Every item has a Size tag (S/M/L/XL)
□ Error recovery section includes plan-specific failures (not just generic)
□ Pre-implementation reading order is included
□ Session checklist is included
□ COMPLETED section exists (even if empty)
□ Stale data warning is included
□ Multi-session awareness section is included
□ All file paths in "Key Files" are accurate (verified via grep/glob)
□ "What EXISTS" sections are accurate (verified by reading the actual code)
□ Percentage completions reflect current state (not aspirational)
□ Timeline includes all items exactly once
□ Parallel streams in dependency graph don't contain hidden dependencies
```

---

*End of guideline*
