# Stream 4: Backend Test Coverage — Item B4

**Master Plan:** `todo/missing-features-implementation-plan.md` (Section B4)
**Size:** XL (3-4 sessions) — this session targets Phases 1-2 only
**Conflict Risk:** LOW — creates NEW test files only, no production code changes
**Dependencies:** None — safe to start immediately (parallel with A1)

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
   - SESSION SCOPE GUIDANCE (B4 = XL = 3-4 sessions, plan Phases 1-2 only)
5. Run `echo $PAT` to confirm GitHub token is available
6. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Codebase Exploration (BEFORE writing any test code)

```bash
# === Understand existing test landscape ===
find backend/ -name "*Test.kt" -o -name "*Spec.kt" | sort
find backend/ -name "*TestFactory*" -o -name "*TestFixture*" -o -name "*TestHelper*" | sort

# === Check existing test dependencies ===
grep -r "testcontainers\|junit\|mockk\|assertk\|kotest" backend/ --include="*.kts" --include="*.toml"
cat backend/api/build.gradle.kts
cat gradle/libs.versions.toml | grep -i "test\|junit\|mock\|assert"

# === Check existing test directory structure ===
ls -R backend/api/src/test/ 2>/dev/null || echo "No test dir"
ls -R backend/license/src/test/ 2>/dev/null || echo "No test dir"
ls -R backend/sync/src/test/ 2>/dev/null || echo "No test dir"

# === Read the source files you will be testing ===
# Sync engine (PRIMARY test targets)
find backend/api/ -name "EntityApplier.kt" -exec cat {} \;
find backend/api/ -name "SyncProcessor.kt" -exec cat {} \;
find backend/api/ -name "DeltaEngine.kt" -exec cat {} \;
find backend/api/ -name "ServerConflictResolver.kt" -exec cat {} \;

# Auth services (SECONDARY test targets)
find backend/api/ -name "AdminAuthService.kt" -exec cat {} \;
find backend/api/ -name "UserService.kt" -exec cat {} \;

# Koin DI module (need to override bindings in tests)
find backend/api/ -name "AppModule.kt" -exec cat {} \;

# DB migrations (understand table schemas for test assertions)
ls backend/api/src/main/resources/db/migration/
cat backend/api/src/main/resources/db/migration/V1__*.sql
cat backend/api/src/main/resources/db/migration/V4__*.sql

# Exposed table definitions (for test queries)
grep -r "object.*: Table\|: IntIdTable\|: LongIdTable\|: UUIDTable" backend/api/src/main/kotlin/ --include="*.kt"

# Repository implementations (reuse patterns in tests)
find backend/api/ -name "*Repository*.kt" | sort
```

Read EVERY source file THOROUGHLY before writing its test. Understand actual method
signatures, dependencies, error handling patterns, and edge cases.

---

## Implementation Plan (4 phases — target Phases 1-2 this session)

### Phase 1: Test Infrastructure Setup (do FIRST — everything depends on this)

#### 1. Add test dependencies to `backend/api/build.gradle.kts`

Use version catalog (`gradle/libs.versions.toml`) for ALL versions — NEVER hardcode.

Dependencies needed:
- `org.testcontainers:postgresql` — PostgreSQL container for integration tests
- `org.testcontainers:junit-jupiter` — JUnit 5 lifecycle integration
- `io.mockk:mockk` — Kotlin mocking (if not already present)
- `org.jetbrains.kotlin:kotlin-test-junit5` — JUnit 5 + Kotlin Test
- `org.assertj:assertj-core` or `io.kotest:kotest-assertions` — assertion library (check what's already used)

**Search first:**
```bash
# Check if any test deps already exist
grep -A 5 "testImplementation\|testRuntimeOnly" backend/api/build.gradle.kts
```

#### 2. Create `AbstractIntegrationTest.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/AbstractIntegrationTest.kt`

```kotlin
// Provides:
// - PostgreSQL Testcontainer (start before class, stop after)
// - Flyway auto-migration on test database (runs all V1-V14+ migrations)
// - Koin test module setup (overrides production DataSource with test DB)
// - Transaction rollback after each test (clean state)
// - Helper: fun getTestDataSource(): DataSource
```

Key considerations:
- Container reuse across test classes (singleton pattern for speed)
- Flyway should run ALL migrations in order — validates migration chain
- Koin: start test modules in `@BeforeAll`, stop in `@AfterAll`
- Transaction rollback: use `@Transactional` or manual `connection.rollback()`

#### 3. Create `TestFixtures.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/TestFixtures.kt`

Factory methods with sensible defaults + override params:
```kotlin
object TestFixtures {
    fun createProduct(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Product",
        price: Double = 100.0,
        storeId: String = "store-1",
        categoryId: String? = null,
        stockQty: Double = 50.0,
        // ... other fields with defaults
    ): ProductDto { ... }

    fun createOrder(...): OrderDto { ... }
    fun createCustomer(...): CustomerDto { ... }
    fun createCategory(...): CategoryDto { ... }
    fun createSupplier(...): SupplierDto { ... }
    fun createSyncOperation(
        entityType: String,
        payload: String, // JSON
        storeId: String = "store-1",
        clientId: String = "client-1",
        timestamp: Instant = Clock.System.now()
    ): SyncOperationDto { ... }

    // JWT token factories
    fun createPosToken(userId: String, storeId: String, role: String = "CASHIER"): String { ... }
    fun createAdminToken(userId: String, role: String = "ADMIN"): String { ... }
}
```

#### 4. Verify infrastructure works

```bash
cd backend && ./gradlew :api:test --info 2>&1 | tail -20
```

**Commit after Phase 1:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/api/build.gradle.kts gradle/libs.versions.toml
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(backend): add Testcontainers infrastructure and test fixtures [B4]

- AbstractIntegrationTest base class with PostgreSQL container + Flyway
- TestFixtures factory methods for all major entity types + JWT tokens
- Koin test module overrides for integration testing
- Verified: container starts, migrations run, Koin initializes

Plan file updated: B4 Phase 1 complete"
git push -u origin $(git branch --show-current)
# WAIT for pipeline green before proceeding to Phase 2
```

---

### Phase 2: Sync Logic Tests (highest business value — after Phase 1 pipeline green)

#### 5. `EntityApplierTest.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/sync/EntityApplierTest.kt`

```kotlin
class EntityApplierTest : AbstractIntegrationTest() {

    @Nested
    inner class ProductType {
        @Test fun apply_validProduct_insertsToDatabase() { ... }
        @Test fun apply_existingProduct_updatesExisting() { ... }
        @Test fun apply_missingRequiredFields_returnsError() { ... }
        @Test fun apply_invalidCategoryId_handlesGracefully() { ... }
        @Test fun apply_nullOptionalFields_insertsWithNull() { ... }
        @Test fun apply_maxLengthFields_handlesCorrectly() { ... }
    }

    // Add @Nested classes for each entity type as they exist in EntityApplier
    // If only PRODUCT exists now, test PRODUCT thoroughly
}
```

Test patterns:
- **Arrange:** Create test data via `TestFixtures`, insert prerequisite rows (store, category)
- **Act:** Call `entityApplier.apply(syncOperation)` with crafted payload
- **Assert:** Query database directly to verify correct state

#### 6. `SyncProcessorTest.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/sync/SyncProcessorTest.kt`

```kotlin
class SyncProcessorTest : AbstractIntegrationTest() {

    @Test fun push_validBatch_allOperationsApplied() { ... }
    @Test fun push_batchWithInvalidOp_partialSuccess() { ... }
    @Test fun push_conflictingTimestamps_lwwResolution() { ... }
    @Test fun push_emptyBatch_noOpNoError() { ... }
    @Test fun push_duplicateOperationIds_idempotent() { ... }
    @Test fun push_invalidJwt_returns401() { ... }
    @Test fun push_expiredJwt_returns401() { ... }
    @Test fun push_cursorAdvanced_afterSuccessfulPush() { ... }
}
```

#### 7. `DeltaEngineTest.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/sync/DeltaEngineTest.kt`

```kotlin
class DeltaEngineTest : AbstractIntegrationTest() {

    @Test fun pull_withCursor_returnsOnlyNewerOperations() { ... }
    @Test fun pull_zeroCursor_returnsFullSnapshot() { ... }
    @Test fun pull_withStoreIdFilter_returnsOnlyStoreData() { ... }
    @Test fun pull_noNewData_returnsEmptyDelta() { ... }
    @Test fun pull_cursorOrdering_monotonicallyIncreasing() { ... }
    @Test fun pull_multiplePagesOfData_paginatesCorrectly() { ... }
}
```

#### 8. `ServerConflictResolverTest.kt`

Location: `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/sync/ServerConflictResolverTest.kt`

```kotlin
class ServerConflictResolverTest {

    @Test fun resolve_clearTimestampWinner_newerVersionKept() { ... }
    @Test fun resolve_identicalTimestamps_deterministicTiebreaker() { ... }
    @Test fun resolve_fieldLevelMerge_bothFieldsKept() { ... }
    @Test fun resolve_conflictLogged_entryWrittenToLog() { ... }
}
```

**Running tests locally:**
```bash
# All sync tests
cd backend && ./gradlew :api:test --tests "*.sync.*" --info

# Specific test class
cd backend && ./gradlew :api:test --tests "*.EntityApplierTest" --info

# All backend tests
cd backend && ./gradlew test --parallel --continue
```

**Commit after Phase 2:**
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(sync): add EntityApplier, SyncProcessor, DeltaEngine, ConflictResolver tests [B4]

- EntityApplierTest: 6 tests covering PRODUCT type (insert, upsert, validation, error handling)
- SyncProcessorTest: 8 tests covering push batch processing, conflict, idempotency, auth
- DeltaEngineTest: 6 tests covering pull/delta, cursor, store filtering, pagination
- ServerConflictResolverTest: 4 tests covering LWW, tiebreaker, field merge, logging
- All tests use Testcontainers PostgreSQL with real Flyway migrations

Plan file updated: B4 Phase 2 complete, coverage estimate ~25% → ~45%"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green
```

---

### Phases 3-4 (NEXT SESSION — leave handoff note in plan file)

**Phase 3: Auth & Service Tests**
- `AdminAuthServiceTest.kt` — BCrypt login, RS256 JWT, MFA, lockout, failed attempts
- `UserServiceTest.kt` — PIN verification (SHA-256+salt), brute-force lockout, token refresh
- `LicenseServiceTest.kt` — activation, heartbeat, expiry, device management

**Phase 4: Repository Integration Tests**
- `ProductRepositoryTest.kt` — CRUD with real PostgreSQL
- `PosUserRepositoryTest.kt` — user creation, lookup, store scoping
- `AdminUserRepositoryTest.kt` — admin CRUD, role assignment
- Coverage reporting CI step

---

## Post-Implementation Updates (MANDATORY — per phase, in SAME commit)

### Update `todo/missing-features-implementation-plan.md`:

1. Mark completed checkboxes in B4 section:
```
- [x] Testcontainers setup (PostgreSQL + Redis)
- [x] `SyncProcessor`, `DeltaEngine`, `EntityApplier` tests
- [ ] `AdminAuthService`, `LicenseService` tests          <- next session
- [ ] Coverage reporting in CI pipeline                    <- next session
```

2. Update B4 status: `~25% vs 80% target` → `~45% vs 80% target` (or actual estimate)

3. Add HANDOFF note at top of B4 section:
```
> **HANDOFF (2026-03-18):** Testcontainers infra + sync test suite complete.
> 24 tests: EntityApplierTest(6), SyncProcessorTest(8), DeltaEngineTest(6),
> ServerConflictResolverTest(4).
> Run `cd backend && ./gradlew :api:test --tests "*.sync.*" --info` to verify.
> Next session: Phase 3 (auth/service tests) + Phase 4 (repository tests).
> Branch: claude/<branch-name>. PR #NNN.
```

4. Update FEATURE COVERAGE MATRIX at bottom:
```
Backend Test Coverage — ~25% → PARTIAL (~45%, Testcontainers infra + sync tests done)
```

### Update `CLAUDE.md` if:
- New test dependencies added to version catalog → update Technology Stack table
- New testing patterns established (Testcontainers base class) → note in Testing Standards

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

**Do NOT start Phase 2 until Phase 1 pipeline is green.**
**Do NOT end session without final push + pipeline green verification.**

---

## Important Notes

- **This stream creates ONLY test files — NEVER modify production code.**
  Production changes happen in Stream 1 (sync engine). If you find a bug while
  writing tests, document it as a comment in the test but do NOT fix production code.

- **Backend tests run in CI Gate (Step[3+4])** — verify they pass in CI, not just locally.
  CI runner may have different Docker/Java configuration.

- **If Testcontainers Docker not available in CI runner:**
  Check CI runner capabilities. Fallback options:
  - `zonky.test.db.postgres:embedded-postgres` (embedded, no Docker needed)
  - H2 in PostgreSQL compatibility mode (less accurate but no Docker)
  Add a CI-detection flag: `val isCI = System.getenv("CI") != null`

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

- **Use JUnit 5 `@Nested`** classes to group related tests within a test class.

- **B4 is a Phase 2 BLOCKER** (Blocker 3 in plan file). Higher test coverage
  protects against regressions when Stream 1 extends the sync engine.
