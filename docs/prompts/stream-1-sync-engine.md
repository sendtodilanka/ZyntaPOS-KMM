# Stream 1: Sync Engine (Critical Path) — Item A1

**Master Plan:** `todo/missing-features-implementation-plan.md` (Section A1)
**Size:** XL (4-5 sessions) — this session targets EntityApplier entity type expansion
**Conflict Risk:** LOW — touches `backend/api/src/.../sync/` only
**Dependencies:** None — start immediately

> **NOTE:** Stream 4 (Backend Tests) runs in parallel and tests `EntityApplier.kt`.
> Your changes land first via PR merge; Stream 4 will adapt its tests accordingly.

---

## Pre-Implementation (MANDATORY — do not skip)

1. Read `CLAUDE.md` fully (codebase context, module map, tech stack, conventions)
2. Read ALL files in `docs/adr/` (ADR-001 through ADR-008)
3. Read `docs/architecture/` (module dependency diagrams)
4. Read `todo/missing-features-implementation-plan.md` FULLY — especially:
   - Section A1 (Sync Engine Server-Side — your primary item)
   - BLOCKER 1 at the top (why this is the #1 critical path item)
   - IMPLEMENTATION COMPLIANCE RULES section
   - ERROR RECOVERY GUIDE section
   - ITEM DEPENDENCY GRAPH (A1 has no blockers, but C6.1, C6.2, C1.1 all DEPEND on A1)
   - SESSION SCOPE GUIDANCE (A1 = XL = 4-5 sessions total)
5. Run `echo $PAT` to confirm GitHub token is available
6. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Codebase Exploration (BEFORE writing any code)

Run these searches to understand the sync architecture:

```bash
# Read the existing EntityApplier (PRODUCT-only handler)
# This is the PRIMARY file you'll extend
find backend/ -name "EntityApplier.kt" -exec cat {} \;

# Read SyncProcessor (calls EntityApplier)
find backend/ -name "SyncProcessor.kt" -exec cat {} \;

# Read DeltaEngine (pull logic)
find backend/ -name "DeltaEngine.kt" -exec cat {} \;

# Read ServerConflictResolver (LWW resolution)
find backend/ -name "ServerConflictResolver.kt" -exec cat {} \;

# Understand ALL entity types that need handling
grep -r "entity_type\|EntityType\|entityType" backend/ --include="*.kt" -l

# Read DB migrations to understand table schemas for each entity
ls backend/api/src/main/resources/db/migration/

# Read KMM client sync schemas (what the client sends)
find shared/data/src/commonMain/sqldelight -name "sync*" -exec cat {} \;

# Check existing repository methods you can reuse
grep -r "Repository" backend/api/src/main/kotlin/ --include="*.kt" -l

# Check SyncRoutes for endpoint structure
find backend/ -name "SyncRoutes.kt" -exec cat {} \;
```

Read each file THOROUGHLY before writing code. Understand the PRODUCT handler
pattern in EntityApplier — you will replicate this exact pattern for all new types.

---

## What to Implement

The `EntityApplier` currently handles ONLY the PRODUCT entity type.
Extend it to handle ALL entity types that flow through the sync pipeline.

### This Session — implement these entity type handlers:

1. **ORDER + ORDER_ITEM** (with nested line items)
   - Apply order header to `orders` table
   - Apply each order item to `order_items` table
   - Handle status transitions (IN_PROGRESS → COMPLETED → VOIDED)
   - Validate: customer_id FK (if non-null), store_id matches JWT

2. **CUSTOMER** (with loyalty points)
   - Apply to `customers` table
   - Handle loyalty_points field (additive merge? or LWW?)
   - Validate: store_id scope (nullable = global customer)

3. **CATEGORY** (with parent-child hierarchy)
   - Apply to `categories` table
   - Handle `parent_id` self-referential FK
   - Validate: no circular parent references

4. **SUPPLIER**
   - Apply to `suppliers` table
   - Straightforward CRUD entity

5. **STOCK_ADJUSTMENT** (with stock quantity side-effect)
   - Apply to `stock_adjustments` table
   - Side-effect: Update `products.stock_qty` based on adjustment type
   - Types: INCREASE, DECREASE, TRANSFER
   - Validate: stock doesn't go negative (configurable?)

### If time permits, also implement:

6. **CASH_REGISTER + REGISTER_SESSION**
   - Apply register to `cash_registers` table
   - Apply session to `register_sessions` table (FK to register)

7. **AUDIT_ENTRY**
   - Apply to `audit_entries` table
   - Write-only (never updated via sync — append-only log)

8. **Add `store_id` validation middleware to sync routes**
   - Extract `store_id` from JWT claims
   - Validate every sync operation's `store_id` matches JWT
   - Reject operations targeting a different store

9. **Add field-level payload validation in `SyncProcessor`**
   - Validate required fields present per entity type
   - Validate field types (string, number, timestamp)
   - Return descriptive error for invalid payloads

### Key Files

| File | Purpose | Action |
|------|---------|--------|
| `backend/api/src/main/kotlin/.../sync/EntityApplier.kt` | JSONB → normalized tables | EXTEND (primary) |
| `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt` | Push batch processing | MODIFY (add validation) |
| `backend/api/src/main/kotlin/.../sync/DeltaEngine.kt` | Pull delta computation | READ ONLY (understand) |
| `backend/api/src/main/kotlin/.../sync/ServerConflictResolver.kt` | LWW resolution | READ ONLY (may extend for new types) |
| `backend/api/src/main/kotlin/.../routes/SyncRoutes.kt` | REST endpoints | MODIFY (add store_id middleware) |
| `backend/api/src/main/resources/db/migration/V*.sql` | Table schemas | READ ONLY (understand schema) |
| `shared/data/src/commonMain/sqldelight/.../sync_queue.sq` | Client outbox format | READ ONLY (understand payload format) |

---

## Search Before Coding (DRY Rule)

Before writing any new handler:

```bash
# Find existing repository methods for each entity type
grep -r "fun insert\|fun upsert\|fun update" backend/api/src/main/kotlin/.../repository/ --include="*.kt"

# Check if entity-specific validation already exists
grep -r "validate\|Validation" backend/api/src/main/kotlin/ --include="*.kt" -l

# Check existing Exposed table definitions
grep -r "object.*Table\|: Table()" backend/api/src/main/kotlin/ --include="*.kt" -l

# Reuse existing repository methods — do NOT duplicate DB logic
# EntityApplier should call existing repositories, not raw SQL
```

---

## Testing Requirements

Write tests for each new entity type handler:

- **File:** `backend/api/src/test/kotlin/.../sync/EntityApplierTest.kt`
- Test per entity type:
  - Valid payload → correct DB state (insert)
  - Duplicate ID → upsert (update existing)
  - Missing required fields → descriptive error
  - Invalid FK reference → error handling
- For STOCK_ADJUSTMENT: verify stock_qty side-effect
- For ORDER: verify nested ORDER_ITEM application
- For CATEGORY: verify parent-child FK integrity

```bash
# Run tests locally
cd backend && ./gradlew :api:test --tests "*.EntityApplierTest" --info
```

---

## Post-Implementation (MANDATORY — in SAME commit as code)

### 1. Update `todo/missing-features-implementation-plan.md`:

a. Mark completed checkboxes in A1 section:
```
- [x] EntityApplier — extend to handle ORDER + ORDER_ITEM
- [x] EntityApplier — extend to handle CUSTOMER
- [x] EntityApplier — extend to handle CATEGORY
- [x] EntityApplier — extend to handle SUPPLIER
- [x] EntityApplier — extend to handle STOCK_ADJUSTMENT
- [ ] Multi-store data isolation enforcement (store_id validation)  ← if not done
- [ ] WebSocket push notifications after sync                       ← next session
- [ ] JWT validation on WebSocket upgrade                           ← next session
```

b. Update A1 status line: `~60% Complete` → new percentage (e.g., `~80% Complete`)

c. Add HANDOFF note at top of A1 section:
```
> **HANDOFF (2026-03-18):** EntityApplier extended with ORDER, ORDER_ITEM,
> CUSTOMER, CATEGORY, SUPPLIER, STOCK_ADJUSTMENT handlers.
> Tests in EntityApplierTest.kt — run before modifying.
> Next session: store_id validation, WebSocket push, JWT on WS upgrade.
> Branch: claude/sync-engine-XXX. PR #NNN.
```

d. Update FEATURE COVERAGE MATRIX at bottom:
```
Multi-node Sync | C6.1 | PARTIAL (LWW only) → PARTIAL (LWW + 6 entity types)
Offline-First   | C6.2 | PARTIAL → PARTIAL (EntityApplier covers core types)
```

### 2. Update `CLAUDE.md`: **DO NOT update CLAUDE.md** — Stream 2 owns CLAUDE.md updates to avoid merge conflicts.

---

## ⚠️ Plan File Merge Conflict Warning

All 4 parallel streams update `todo/missing-features-implementation-plan.md`.
**Merge conflicts on this file are expected and normal.**

After EVERY push, check PR status:
```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "
import sys,json
prs=json.load(sys.stdin)
if not prs: print('No open PR yet')
for pr in prs:
  print(f'PR #{pr[\"number\"]}: mergeable={pr.get(\"mergeable\")} state={pr.get(\"mergeable_state\")}')
"
```

**If `mergeable=false` or `mergeable_state=dirty`:**
```bash
git fetch origin main
git merge origin/main --no-edit
# If plan file conflicts: keep BOTH your changes AND main's changes
git add todo/missing-features-implementation-plan.md
git commit -m "merge: resolve plan file conflict with main"
git push -u origin $(git branch --show-current)
```

---

## Pre-Commit Sync + Commit + Push

```bash
# 1. MANDATORY pre-commit sync
git fetch origin main && git merge origin/main --no-edit

# 2. Stage all changes (code + tests + docs)
git add backend/api/src/main/kotlin/.../sync/EntityApplier.kt
git add backend/api/src/main/kotlin/.../sync/SyncProcessor.kt
git add backend/api/src/main/kotlin/.../routes/SyncRoutes.kt
git add backend/api/src/test/kotlin/.../sync/EntityApplierTest.kt
git add todo/missing-features-implementation-plan.md

# 3. Commit
git commit -m "feat(sync): extend EntityApplier with ORDER, CUSTOMER, CATEGORY, SUPPLIER, STOCK_ADJUSTMENT types [A1]

- ORDER handler with nested ORDER_ITEM application
- CUSTOMER handler with loyalty points sync
- CATEGORY handler with parent-child hierarchy validation
- SUPPLIER handler (CRUD entity)
- STOCK_ADJUSTMENT handler with stock_qty side-effect
- Integration tests for all new entity type handlers

Plan file updated: A1 status ~60% → ~80%"

# 4. Push
git push -u origin $(git branch --show-current)
```

---

## Pipeline Monitoring (after EVERY push)

```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

# Step[1] — Watch Branch Validate
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('workflow_runs',[])]"

# Step[2] — Confirm PR auto-created (do NOT create manually)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "import sys,json; prs=json.load(sys.stdin); print('PR #'+str(prs[0]['number']) if prs else 'No PR yet')"

# Step[3+4] — CI Gate checks on PR
PR=<number from above>
SHA=$(curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls/$PR" | python3 -c "import sys,json; print(json.load(sys.stdin)['head']['sha'])")
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('check_runs',[])]"
```

**Do NOT end session without final push + pipeline green verification.**

---

## Important Notes

- This is the **#1 critical path blocker** — C6.1, C6.2, C1.1, C5.4 all depend on A1
- Follow the EXACT pattern of the existing PRODUCT handler — do not invent new patterns
- Reuse existing repository methods — EntityApplier should delegate to repositories
- NEVER modify production KMM client code in this stream (backend only)
- Read ALL migration files: `ls backend/api/src/main/resources/db/migration/` (V1-V22+, not just V14)
- If you need a new migration, use the next version number (check what V-number is latest)
