# Stream 4: Backend Test Coverage — Item B4 — REMAINING ITEMS ONLY

**Master Plan:** `todo/missing-features-implementation-plan.md` (Section B4)
**Size:** L (2-3 sessions) — previous session massively expanded sync tests; remaining: repo tests + CI coverage
**Conflict Risk:** LOW — modifies test files only, no production code changes
**Dependencies:** None — safe to start immediately

> **STATUS (2026-03-18):** Sync test expansion is COMPLETE. 19 test files, 5,113 lines, 172+ test methods.
> EntityApplierTest expanded from 54→428 lines (45 tests).
> SyncProcessorTest expanded from 166→417 lines (21 tests).
> DeltaEngineTest expanded from 86→223 lines (18 tests).
> ServerConflictResolverTest expanded from 80→329 lines (26 tests).
> SyncValidatorTest: 611 lines, SyncMetricsTest: 143 lines (NEW).
> License service: 2 test files (374 lines). Sync service: 2 test files (387 lines).
>
> This session focuses on the 3 missing repository tests + CI coverage enforcement.

---

## ✅ COMPLETED (do NOT re-implement or expand)

### Existing Test Files (verified 2026-03-18)

| File | Lines | Tests | Status |
|------|-------|-------|--------|
| `sync/EntityApplierTest.kt` | 428 | 45 | ✅ All 17 entity types, payload parsing, missing fields, append-only audit |
| `sync/SyncProcessorTest.kt` | 417 | 21 | ✅ Push orchestration, conflict detection, dedup, dead letter, metrics |
| `sync/DeltaEngineTest.kt` | 223 | 18 | ✅ Cursor pagination, limit clamping, sequential pulls, boundary |
| `sync/ServerConflictResolverTest.kt` | 329 | 26 | ✅ LWW, device tiebreak, field merge (PRODUCT), conflict log |
| `sync/SyncValidatorTest.kt` | 611 | 17+ | ✅ Field validation all entity types, batch limits, timestamp |
| `sync/SyncMetricsTest.kt` | 143 | 7+ | ✅ Counters, P95, rolling window, conflict rate |
| `service/AdminAuthServiceTest.kt` | 296 | 12 | ✅ Admin login + JWT |
| `service/AdminAuthServiceExtendedTest.kt` | 523 | 21 | ✅ BCrypt, MFA, lockout |
| `service/UserServiceTest.kt` | 206 | 9+ | ✅ POS PIN auth |
| `integration/SyncPushPullIntegrationTest.kt` | 203 | 10+ | ✅ E2E sync |
| `routes/AuthRoutesTest.kt` | 148 | 8 | ✅ Auth HTTP routes |
| `routes/CsrfPluginTest.kt` | 133 | 7 | ✅ CSRF protection |
| **License:** `LicenseServiceTest.kt` | 235 | 11 | ✅ License CRUD |
| **License:** `AdminLicenseServiceTest.kt` | 139 | 6 | ✅ Admin license ops |
| **Sync:** `WebSocketHubTest.kt` | 216 | 10 | ✅ WS connections |
| **Sync:** `RedisPubSubListenerTest.kt` | 171 | 9 | ✅ Pub/sub relay |
| **Common:** `ValidationScopeTest.kt` | 440 | 28 | ✅ Validation DSL |
| **Common:** `JwtDefaultsTest.kt` | 127 | 6 | ✅ JWT defaults |
| **Common:** `JsonExtensionsTest.kt` | 125 | 6 | ✅ JSON utils |

### Test Dependencies (ALREADY in build.gradle.kts — do NOT add again)

```kotlin
testImplementation("org.testcontainers:testcontainers:1.20.4")
testImplementation("org.testcontainers:postgresql:1.20.4")
testImplementation("org.testcontainers:junit-jupiter:1.20.4")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
// MockK also present: io.mockk:mockk:1.13.16
```

---

## What's STILL MISSING (implement these)

### Phase 3: Repository Integration Tests (3 NEW files)

These files DO NOT EXIST — create them:

#### 1. `ProductRepositoryTest.kt` (NEW — HIGH)

**Location:** `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/repository/ProductRepositoryTest.kt`

**Test scenarios:**
- CRUD: Create product → read by ID → update → delete → verify deleted
- Search: Full-text search by name/SKU (PostgreSQL `tsvector` or `LIKE`)
- Store scoping: Products filtered by `store_id` — store A products not visible to store B
- Pagination: `LIMIT/OFFSET` with sorting by name, price, created_at
- Edge cases: Duplicate SKU in same store → error; same SKU in different stores → allowed
- FK validation: Product with non-existent category_id → error or null handling
- Soft delete: If implemented, verify deleted products excluded from search

**Use Testcontainers PostgreSQL** — run Flyway migrations for real schema:
```kotlin
@Testcontainers
class ProductRepositoryTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("zyntapos_api_test")
    }

    @BeforeEach
    fun setup() {
        // Run Flyway migrations
        // Create ProductRepository instance with test DataSource
    }
}
```

#### 2. `PosUserRepositoryTest.kt` (NEW — HIGH)

**Location:** `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/repository/PosUserRepositoryTest.kt`

**Test scenarios:**
- Create user with store scoping (`store_id` + `username` unique constraint)
- Lookup by username within store
- Username uniqueness per store (same username in different stores → OK)
- PIN hash storage and retrieval
- Role assignment (ADMIN, STORE_MANAGER, CASHIER, etc.)
- Brute-force lockout: increment failed attempts, verify lockout after threshold
- System admin flag (`isSystemAdmin`) — exactly one per system

#### 3. `AdminUserRepositoryTest.kt` (NEW — MEDIUM)

**Location:** `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/repository/AdminUserRepositoryTest.kt`

**Test scenarios:**
- Admin CRUD (create, read, update, delete)
- Email uniqueness constraint
- Role assignment (ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK)
- BCrypt password hash storage
- Account lockout state persistence (failed_attempts, locked_until)
- MFA secret storage and retrieval
- Last login timestamp update

### Phase 4: Test Infrastructure Improvements

#### 4. `AbstractIntegrationTest` Base Class (MEDIUM)

**Problem:** Each test file reimplements its own mock setup. No shared base class.

**Create:** `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/test/AbstractIntegrationTest.kt`

```kotlin
@Testcontainers
abstract class AbstractIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("zyntapos_api_test")

        @Container
        val redis = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    protected lateinit var dataSource: DataSource
    protected lateinit var database: Database

    @BeforeEach
    fun baseSetup() {
        // Run Flyway, create DataSource, initialize Exposed
    }

    @AfterEach
    fun baseTeardown() {
        // Clean up test data
    }
}
```

#### 5. `TestFixtures` Factory Object (MEDIUM)

**Create:** `backend/api/src/test/kotlin/com/zyntasolutions/zyntapos/api/test/TestFixtures.kt`

```kotlin
object TestFixtures {
    fun product(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Product",
        price: Double = 9.99,
        storeId: String = "store-1"
    ): Product = Product(id, name, price, storeId, ...)

    fun syncOperation(
        entityType: String = "PRODUCT",
        operationType: String = "INSERT",
        payload: String = validProductJson()
    ): SyncOperation = ...

    fun validProductJson(name: String = "Test"): String = ...
    fun validOrderJson(): String = ...
    fun validCustomerJson(): String = ...
}
```

### Phase 5: CI Coverage Enforcement

#### 6. Kover Coverage Reporting in CI (HIGH)

**Problem:** Kover plugin v0.9.1 is installed in all 3 backend services but:
- No `koverVerify {}` block with minimum thresholds
- No `koverReport` task in CI
- No coverage reports uploaded as artifacts

**Implementation:**

a. Add Kover verification to each `build.gradle.kts`:
```kotlin
kover {
    reports {
        verify {
            rule {
                minBound(60) // fail if line coverage < 60%
            }
        }
    }
}
```

b. Add to `.github/workflows/ci-gate.yml` after test step:
```yaml
- name: Generate coverage report — ${{ matrix.service }}
  run: ./gradlew :${{ matrix.service }}:koverXmlReport --no-daemon

- name: Upload coverage report
  uses: actions/upload-artifact@v4
  with:
    name: coverage-${{ matrix.service }}
    path: backend/${{ matrix.service }}/build/reports/kover/xml/report.xml
```

c. Optional: Add coverage comment on PR using a GitHub Action

---

## Missing Test Scenarios in Existing Files (NICE-TO-HAVE)

If time permits after the main items above:

### EntityApplierTest — Missing edge cases:
- [ ] FK validation tests (product with non-existent category_id)
- [ ] NULL optional fields in actual DB operations
- [ ] Max-length field values (no truncation)
- [ ] Concurrent apply of same entity (idempotent behavior)

### DeltaEngineTest — Missing scenarios:
- [ ] Multiple entity types in same pull response
- [ ] Store_id isolation (store A data never in store B pull)

### SyncProcessorTest — Missing scenarios:
- [ ] Multi-store isolation test (operations from store A rejected if JWT is store B)

---

## Pre-Implementation (MANDATORY)

1. Read `CLAUDE.md` fully
2. Run existing tests to establish baseline:
   ```bash
   cd backend && ./gradlew :api:test --parallel --continue 2>&1 | tail -20
   ```
3. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Commit + Push (per phase)

```bash
# Phase 3 — Repository tests
git fetch origin main && git merge origin/main --no-edit
git add backend/api/src/test/
git add todo/missing-features-implementation-plan.md
git commit -m "test(repository): add Product, PosUser, AdminUser integration tests [B4]

- ProductRepositoryTest: CRUD, search, store scoping, pagination
- PosUserRepositoryTest: creation, uniqueness, lockout
- AdminUserRepositoryTest: CRUD, roles, BCrypt, MFA
- All use Testcontainers PostgreSQL with Flyway migrations

Plan file updated: B4 repository tests added"
git push -u origin $(git branch --show-current)

# Phase 5 — Kover CI
git fetch origin main && git merge origin/main --no-edit
git add backend/ .github/workflows/ci-gate.yml
git add todo/missing-features-implementation-plan.md
git commit -m "build(ci): enforce Kover coverage threshold in CI pipeline [B4]

- koverVerify with 60% minimum line coverage
- koverXmlReport uploaded as CI artifact
- Coverage report for api, license, sync services

Plan file updated: B4 coverage enforcement added"
git push -u origin $(git branch --show-current)
```

---

## Important Notes

- **This stream modifies ONLY test files + CI config — NEVER modify production code.**
- If you find a bug while testing, document it as `// BUG:` comment in the test.
- **DO NOT update `CLAUDE.md`** — Stream 2 owns that file.
- Test naming: `methodName_scenario_expectedResult`
- Use JUnit 5 `@Nested` classes to group related tests.
