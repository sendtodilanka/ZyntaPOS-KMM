# Stream 4: Backend Test Coverage — Item B4

**Master Plan:** `todo/missing-features-implementation-plan.md` (Section B4)
**Size:** XL (3-4 sessions) — this session targets gap analysis + expanding existing tests
**Conflict Risk:** LOW — modifies test files only, no production code changes
**Dependencies:** None — safe to start immediately (parallel with A1)

> **NOTE:** This stream maps to the plan's "Stream 3" (B1-B5). The prompt numbering
> differs from the plan's stream numbering for practical grouping reasons.

---

## Pre-Implementation (MANDATORY — do not skip)

1. Read `CLAUDE.md` fully (codebase context, module map, tech stack, conventions)
2. Read ALL files in `docs/adr/` (ADR-001 through ADR-008)
3. Read `docs/architecture/` (module dependency diagrams)
4. Read `todo/missing-features-implementation-plan.md` FULLY — especially:
   - Section B4 (Backend Test Coverage — your primary item)
   - Section A1 (Sync Engine — understand what EntityApplier does, since you'll test it)
   - BLOCKER 3 (why test coverage is a Phase 2 blocker)
   - IMPLEMENTATION COMPLIANCE RULES section
   - ERROR RECOVERY GUIDE section (test failure handling)
   - ITEM DEPENDENCY GRAPH (B4 has no blockers — safe to start immediately)
   - SESSION SCOPE GUIDANCE (B4 = XL = 3-4 sessions, plan accordingly)
5. Run `echo $PAT` to confirm GitHub token is available
6. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## ⚠️ CRITICAL: Existing Test Files — DO NOT OVERWRITE

**Test infrastructure and test files ALREADY EXIST in the codebase.** Your job is to
**EXTEND** them with additional test cases, NOT create from scratch.

### Existing Test Files (as of 2026-03-18)

| File | Lines | What It Covers |
|------|-------|----------------|
| `backend/api/src/test/.../sync/EntityApplierTest.kt` | 54 | PRODUCT type only (basic) |
| `backend/api/src/test/.../sync/SyncProcessorTest.kt` | 166 | Push processing |
| `backend/api/src/test/.../sync/DeltaEngineTest.kt` | 86 | Cursor-based pull |
| `backend/api/src/test/.../sync/ServerConflictResolverTest.kt` | 80 | LWW resolution |
| `backend/api/src/test/.../sync/SyncValidatorTest.kt` | 123 | Payload validation |
| `backend/api/src/test/.../service/AdminAuthServiceTest.kt` | 272 | Admin login + JWT |
| `backend/api/src/test/.../service/AdminAuthServiceExtendedTest.kt` | 519 | BCrypt, MFA, lockout |
| `backend/api/src/test/.../service/UserServiceTest.kt` | 206 | POS PIN auth |
| `backend/api/src/test/.../integration/SyncPushPullIntegrationTest.kt` | — | End-to-end sync |
| `backend/api/src/test/.../routes/AuthRoutesTest.kt` | — | Auth HTTP routes |
| `backend/api/src/test/.../routes/CsrfPluginTest.kt` | — | CSRF protection |

### Existing Test Dependencies (already in `backend/api/build.gradle.kts`)

```kotlin
testImplementation("org.testcontainers:testcontainers:1.20.4")
testImplementation("org.testcontainers:postgresql:1.20.4")
testImplementation("org.testcontainers:junit-jupiter:1.20.4")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

**DO NOT add these dependencies again — they are already present.**

---

## Codebase Exploration (BEFORE writing any test code)

```bash
# === STEP 1: Read ALL existing test files FIRST ===
# You MUST understand what's already tested before adding anything
find backend/ -name "*Test.kt" -o -name "*Spec.kt" | sort
for f in $(find backend/api/src/test -name "*Test.kt" | sort); do
  echo "=== $f ==="
  cat "$f"
  echo ""
done

# === STEP 2: Check Stream 1 progress (EntityApplier may have been extended) ===
# Stream 1 runs in parallel and extends EntityApplier with new entity types.
# Check if their PR has merged to main before writing tests.
git fetch origin main
git log origin/main --oneline -10  # Look for "feat(sync): extend EntityApplier" commits

# === STEP 3: Read the production code being tested ===
find backend/api/ -name "EntityApplier.kt" -exec cat {} \;
find backend/api/ -name "SyncProcessor.kt" -exec cat {} \;
find backend/api/ -name "DeltaEngine.kt" -exec cat {} \;
find backend/api/ -name "ServerConflictResolver.kt" -exec cat {} \;
find backend/api/ -name "AdminAuthService.kt" -exec cat {} \;
find backend/api/ -name "UserService.kt" -exec cat {} \;

# === STEP 4: Read existing test infrastructure ===
find backend/ -name "*Abstract*Test*" -o -name "*BaseTest*" -o -name "*TestHelper*" | sort
find backend/ -name "TestFixture*" -o -name "*TestFactory*" | sort
grep -r "testcontainers\|@Container\|PostgreSQLContainer" backend/ --include="*.kt" -l

# === STEP 5: Check test deps and build config ===
grep "testImplementation\|testRuntimeOnly" backend/api/build.gradle.kts

# === STEP 6: Understand DB schemas (for assertion queries) ===
ls backend/api/src/main/resources/db/migration/

# === STEP 7: Check existing Koin test module setup ===
grep -r "koin\|Koin\|startKoin\|loadModules" backend/api/src/test/ --include="*.kt" -l

# === STEP 8: Run existing tests to establish baseline ===
cd backend && ./gradlew :api:test --parallel --continue 2>&1 | tail -30
```

**Read EVERY existing test file before writing new code.** Understand what's covered
and what's missing. Your value is in filling GAPS, not duplicating.

---

## Implementation Plan (3 phases this session)

### Phase 1: Gap Analysis + Test Infrastructure Improvements

**Goal:** Identify exactly what's missing, improve shared test infra if needed.

1. **Run existing tests** and record pass/fail counts:
   ```bash
   cd backend && ./gradlew :api:test --info 2>&1 | grep -E "tests|PASSED|FAILED|SKIPPED"
   ```

2. **Catalog coverage gaps** by reading each test file:
   - Which test methods exist? Which edge cases are missing?
   - Are there `@Disabled` or TODO tests that need implementation?
   - Which production methods have ZERO test coverage?

3. **Improve test infrastructure (if gaps found):**
   - If no `AbstractIntegrationTest` base class exists → create one
   - If no `TestFixtures` factory object exists → create one
   - If existing test helpers are incomplete → extend them
   - **If infrastructure is already adequate → SKIP this step**

4. **Check if Stream 1 has extended EntityApplier:**
   ```bash
   git fetch origin main
   git log origin/main --oneline -10 | grep -i "entity\|sync\|applier"
   ```
   - If YES (new entity types added): write tests for the NEW types
   - If NO (still PRODUCT-only): write deeper PRODUCT tests + note handoff for new types

**Commit after Phase 1 (if infrastructure changes made):**
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(backend): improve test infrastructure and gap analysis [B4]

- [describe specific infrastructure improvements]
- Documented coverage gaps for targeted expansion

Plan file updated: B4 Phase 1 complete"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before Phase 2
```

---

### Phase 2: Expand Sync Logic Test Coverage (highest value)

**Goal:** Add missing test cases to EXISTING sync test files.

#### 2a. Extend `EntityApplierTest.kt` (currently 54 lines — thin coverage)

Read the existing tests first, then ADD these missing scenarios:
- Valid product with all optional fields populated → correct DB state
- Product with invalid category_id FK → graceful error handling
- Product with NULL optional fields → inserts correctly
- Product with max-length field values → no truncation errors
- **If Stream 1 has added new entity types:** Test each new type (ORDER, CUSTOMER, etc.)
- Concurrent apply of same entity → idempotent behavior

#### 2b. Extend `SyncProcessorTest.kt` (currently 166 lines)

Read existing tests, then ADD:
- Push with duplicate operation IDs → idempotent (no double-apply)
- Push with empty batch → no-op, no error
- Push with mixed valid/invalid operations → partial success handling
- Push with very large batch (100+ ops) → performance doesn't degrade
- Push with store_id mismatch vs JWT → rejection (if validation exists)

#### 2c. Extend `DeltaEngineTest.kt` (currently 86 lines)

Read existing tests, then ADD:
- Pull with store_id filter → only that store's data
- Pull with cursor at head (no new data) → empty delta
- Pull cursor monotonicity validation
- Pull with multiple entity types → correct ordering

#### 2d. Extend `ServerConflictResolverTest.kt` (currently 80 lines)

Read existing tests, then ADD:
- Field-level merge scenario (price changed on A, name changed on B → merge both)
- Identical timestamps → deterministic tiebreaker
- Conflict entry written to `sync_conflict_log`

**Running tests:**
```bash
# All sync tests
cd backend && ./gradlew :api:test --tests "*.sync.*" --info

# Specific class
cd backend && ./gradlew :api:test --tests "*.EntityApplierTest" --info
```

**Commit after Phase 2:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(sync): expand EntityApplier, SyncProcessor, DeltaEngine test coverage [B4]

- EntityApplier: added N test cases (FK validation, nulls, idempotency)
- SyncProcessor: added N test cases (empty batch, duplicates, large batch)
- DeltaEngine: added N test cases (store filter, empty delta, ordering)
- ConflictResolver: added N test cases (field merge, tiebreaker, logging)

Plan file updated: B4 sync test coverage expanded"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before Phase 3
```

---

### Phase 3: Repository Integration Tests (NEW test files)

**Goal:** Add repository-layer integration tests that currently don't exist.

These are genuinely NEW files — no existing tests for repositories:

1. **`ProductRepositoryTest.kt`** (NEW):
   - CRUD operations with real PostgreSQL via Testcontainers
   - FTS5 search (if backed by PostgreSQL full-text search)
   - Store-scoped queries (product belongs to store_id)
   - Pagination and sorting

2. **`PosUserRepositoryTest.kt`** (NEW):
   - User creation with store scoping
   - Username uniqueness per store
   - User lookup by various fields

3. **`AdminUserRepositoryTest.kt`** (NEW):
   - Admin CRUD operations
   - Role assignment and permission checks
   - Account lockout state persistence

```bash
# Verify these test files don't exist yet
find backend/ -name "ProductRepositoryTest.kt" -o -name "PosUserRepositoryTest.kt" -o -name "AdminUserRepositoryTest.kt"
```

**Commit after Phase 3:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(repository): add Product, PosUser, AdminUser repository integration tests [B4]

- ProductRepositoryTest: CRUD, search, store scoping, pagination
- PosUserRepositoryTest: creation, uniqueness, lookup
- AdminUserRepositoryTest: CRUD, roles, lockout persistence
- All tests use Testcontainers PostgreSQL with real Flyway migrations

Plan file updated: B4 repository tests added, coverage ~25% → ~50%"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green
```

---

### Phases 4-5 (NEXT SESSION — leave handoff note in plan file)

**Phase 4:** License service tests (`backend/license/src/test/`)
- Activation, heartbeat, expiry, device management

**Phase 5:** Coverage reporting in CI
- Add JaCoCo or Kover plugin
- Add coverage threshold check in `ci-gate.yml`
- Fail build if coverage drops below threshold

---

## Post-Implementation Updates (MANDATORY — per phase, in SAME commit)

### Update `todo/missing-features-implementation-plan.md`:

1. Mark completed checkboxes in B4 section:
```
- [x] Testcontainers setup (PostgreSQL + Redis) — ALREADY EXISTED (verified 2026-03-18)
- [x] `SyncProcessor`, `DeltaEngine`, `EntityApplier` tests — EXTENDED with N new cases
- [ ] `AdminAuthService`, `LicenseService` tests — AdminAuth exists, License next session
- [ ] Coverage reporting in CI pipeline — next session
```

2. Update B4 status with ACCURATE estimate based on gap analysis:
   - Run: `cd backend && ./gradlew :api:test --info | grep -c "PASSED\|FAILED"`
   - Count total test methods vs estimated needed
   - Update: `~25% vs 80% target` → actual estimate

3. Add HANDOFF note at top of B4 section:
```
> **HANDOFF (2026-03-18):** Test infra already existed (Testcontainers + 9 test files).
> This session expanded sync test coverage (N new test cases) and added
> repository integration tests (3 new files).
> Existing tests: EntityApplierTest, SyncProcessorTest, DeltaEngineTest,
> ServerConflictResolverTest, SyncValidatorTest, AdminAuthServiceTest (x2),
> UserServiceTest, SyncPushPullIntegrationTest, AuthRoutesTest, CsrfPluginTest.
> NEW tests: ProductRepositoryTest, PosUserRepositoryTest, AdminUserRepositoryTest.
> Run: `cd backend && ./gradlew :api:test --info` to verify.
> Next session: License service tests + coverage reporting CI step.
```

4. Update FEATURE COVERAGE MATRIX at bottom

### Update `CLAUDE.md`: **DO NOT update CLAUDE.md** — Stream 2 owns CLAUDE.md updates to avoid conflicts.

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
# (they modify different sections of the same file)
git add todo/missing-features-implementation-plan.md
git commit -m "merge: resolve plan file conflict with main"
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

**Do NOT start next phase until current phase's pipeline is green.**
**Do NOT end session without final push + pipeline green verification.**

---

## Important Notes

- **This stream modifies ONLY test files — NEVER modify production code.**
  If you find a bug while testing, document it as a `// BUG:` comment in the test
  but do NOT fix production code (that's Stream 1's responsibility).

- **Stream 1 runs in parallel extending EntityApplier.** Before writing sync tests,
  always check if Stream 1 has merged: `git log origin/main --oneline -5 | grep sync`.
  If new entity types are in main, test those too.

- **Backend tests run in CI Gate (Step[3+4])** — verify they pass in CI, not just locally.

- **Test file structure mirrors main source:**
  ```
  backend/api/src/main/kotlin/.../sync/EntityApplier.kt
  → backend/api/src/test/kotlin/.../sync/EntityApplierTest.kt
  ```

- **Test naming convention:** `methodName_scenario_expectedResult`
  ```kotlin
  @Test fun apply_validProduct_insertsToDatabase() { ... }
  @Test fun push_emptyBatch_noOpNoError() { ... }
  ```

- **Use JUnit 5 `@Nested`** classes to group related tests.

- **DO NOT add dependencies to `build.gradle.kts`** — Testcontainers, JUnit 5,
  and coroutines-test are already present. Only add if a genuinely new library is needed.

- **DO NOT update `CLAUDE.md`** — Stream 2 owns that file to prevent merge conflicts.
