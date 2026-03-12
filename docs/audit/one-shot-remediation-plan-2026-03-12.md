# One-Shot Remediation Plan — ZyntaPOS KMM

**Date:** 2026-03-12
**Author:** Claude (Senior KMP Architect)
**Scope:** All findings from `kmp-architecture-audit-2026-03-12.md` + `backend-modules-audit-2026-03-12.md` + Plan Agent deep analysis
**Strategy:** 4 sequential sprints, zero downtime, no component discards
**Estimated Total:** ~17 working days (single developer) or ~14 days (two developers)

---

## Architecture: Sprint Dependency Graph

```
Sprint 1 (Admin Panel)          Sprint 2 (Backend)
    |                               |
    | (independent — parallel OK)   |
    v                               v
         Sprint 3 (Tests + Performance)
                    |
                    v
         Sprint 4 (Docs + Compliance)
```

Sprints 1 and 2 are **independent** — they can execute in parallel if two developers are available.
Sprint 3 depends on Sprint 2 (table centralization must precede repository extraction).
Sprint 4 depends on Sprint 3 (documented API must reflect tested implementation).

---

## Sprint 1: Admin Panel Session Stability & Critical Bug Fixes

**Duration:** 2-3 days (~12 hours)
**Branch:** `claude/sprint1-admin-session-stability-<sessionId>`
**Goal:** Fix the most user-impacting bugs. After this sprint, admin panel sessions are stable and the React auth flow is correct.

### Critical Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S1-1 | **Implement token refresh interceptor** | `admin-panel/src/lib/api-client.ts` | 3h | Users hard-logged-out after 15 min; unsaved data lost. Implement 401 interceptor → call `POST /admin/auth/refresh` → replay failed requests. Must handle concurrent 401s with a request queue. |
| S1-2 | **Move setUser/clearUser out of queryFn into useEffect** | `admin-panel/src/api/auth.ts` | 2h | Side effects inside `queryFn` cause StrictMode double-execution and unpredictable retries. Extract to `useEffect` that watches query data. |

### High Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S1-3 | Remove redundant `navigate()` calls on login | `admin-panel/src/routes/login.tsx` | 1h | Triple navigation on login success — rely on `__root.tsx` guard only. |
| S1-4 | Wrap `changePassword.mutateAsync` in try/catch | `admin-panel/src/routes/settings/profile.tsx` | 0.5h | Unhandled promise rejection crashes error boundary. |
| S1-5 | Handle "Revoke All Sessions" for own user | `admin-panel/src/routes/settings/profile.tsx` | 1h | Revoking own session causes unexpected ejection. Should `clearUser()` + redirect to `/login`. |
| S1-6 | Unify auth source of truth (remove duplicate `useCurrentUser`) | `admin-panel/src/routes/settings/profile.tsx`, `mfa.tsx`, `auth-store.ts` | 2h | Two sources of truth (query cache vs Zustand store) cause stale MFA/role data across components. |

### Medium Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S1-7 | Remove `storeLoading` from root loading check | `admin-panel/src/routes/__root.tsx` | 0.5h | Triple loading state can cause infinite spinner if store never settles. |
| S1-8 | Show spinner on public pages while `statusLoading` | `admin-panel/src/routes/__root.tsx` | 0.5h | Login page flashes before bootstrap redirect on first run. |
| S1-9 | Add `.json()` to `useAdminMfaDisable` and `useRevokeSessions` | `admin-panel/src/api/auth.ts`, `users.ts` | 0.5h | Returns raw Response object instead of parsed JSON. |
| S1-11 | Enable `refetchOnWindowFocus` or reduce staleTime on `/me` query | `admin-panel/src/api/auth.ts` | 0.5h | 5-min stale time delays account deactivation propagation — security gap. |

### Low Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S1-10 | Narrow `setUser` to non-null type; remove duplication with `clearUser` | `admin-panel/src/stores/auth-store.ts` | 0.5h | Ambiguous API — `setUser(null)` duplicates `clearUser()`. |

### Execution Notes

1. **S1-1 is highest risk.** Implement with request queue pattern:
   - On first 401, pause all outgoing requests
   - Call refresh endpoint
   - If refresh succeeds, replay queued requests with new token
   - If refresh fails, redirect to `/login`
   - Test with shortened token TTL (30 seconds) in dev
2. S1-2 through S1-11 are independent fixes — commit together after S1-1
3. Manual test checklist: login → wait >15 min → verify refresh works; MFA flow; revoke-all behavior

---

## Sprint 2: Backend Cross-Module Alignment & Security Hardening

**Duration:** 5-6 days (~41 hours)
**Branch:** `claude/sprint2-backend-alignment-<sessionId>`
**Goal:** Eliminate architectural inconsistencies across the 3 backend services and close remaining security gaps.

### Critical Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S2-5 | **Centralize API Exposed table objects into `db/Tables.kt`** | `AdminAuthService.kt`, `UserService.kt`, `ProductService.kt` → new `db/Tables.kt` | 4h | Tables scattered inline in service files cause circular imports, untestable code, impossible schema overview. License service already does this correctly — follow that pattern. |
| S2-6 | **Dual-hash password migration (SHA-256 → bcrypt)** | `UserService.kt`, new V13 migration | 6h | POS passwords use SHA-256 (GPU-vulnerable). Implement rolling migration: try bcrypt first → fall back to SHA-256 → re-hash to bcrypt on success. No forced password resets, no downtime. |

### High Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S2-1 | Centralize JWT config constants in `backend/common` | `backend/common/` (new), all 3 service configs | 3h | JWT issuer/audience rely on matching hardcoded defaults — no shared validation. Misconfigured env var silently breaks cross-service auth. |
| S2-2 | Centralize error response models | `backend/common/`, all StatusPages plugins | 3h | API uses `{code, message}`, validation uses `{errors:[]}` — clients handle two shapes. |
| S2-3 | Standardize timestamps to `Instant.now()` UTC | `AdminAuthService`, `UserService`, WS messages | 3h | Mixed `System.currentTimeMillis()` vs `Instant.now()` vs epoch-ms causes TTL drift. |
| S2-7 | Add sync payload field-level validation | New `SyncValidator.kt` | 4h | Sync payloads validated for schema but not content — no field length checks, stored XSS risk. |
| S2-8 | Add heartbeat replay protection | `LicenseService.kt` | 3h | No timestamp freshness check, nonce, or request signing — captured heartbeats replayable indefinitely. |
| S2-9 | Add POS token revocation check on JWT validation | `Authentication.kt` | 3h | Deactivated users keep access until token expiry because no revocation list is checked. |
| S2-10 | Validate `storeId` claim against DB on sync operations | `SyncRoutes.kt` | 2h | Manipulated JWT could push data to any store — no store-level authorization check. |
| S2-11 | Validate `ADMIN_PANEL_URL` at startup | `AppConfig.kt` | 1h | Misconfigured env var could leak password reset tokens to malicious domain. |
| S2-13 | Add health check dependency indicators | `HealthRoutes.kt`, `AdminHealthRoutes.kt` | 2h | `/health` only checks own DB — Redis or downstream service failures invisible. |
| S2-14 | Make blocking Redis retry async in Sync service | `RedisPubSubListener.kt` | 2h | Blocking retry up to 2.5 min at startup causes Docker health check failure. |

### Medium Priority

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S2-4 | Pin kotlinx-datetime 0.7.1 in backend builds | `backend/license/build.gradle.kts` | 0.5h | License uses 0.6.1 vs KMM 0.7.1 — serialization incompatibility risk. |
| S2-12 | Add license validation to API auth flow | `UserService.kt` or `AuthRoutes.kt` | 4h | Revoked licenses still grant API access — API never validates status with License service. |
| S2-15 | Configure Sentry sampling and PII scrubbing | All 3 `Application.kt` files | 1h | 100% error sampling exceeds quotas, sends PII. |

### Execution Notes

1. **S2-5 first** (table centralization) — all subsequent API work depends on clean table definitions
2. **S2-6 (dual-hash)** requires V13 Flyway migration. Transition period: both hash formats coexist. `verifyPasswordHash()` tries bcrypt first → fallback SHA-256 → re-hash on success
3. Run `./gradlew :backend-api:flywayMigrate` locally before each push
4. Test JWT cross-service auth manually after S2-1

---

## Sprint 3: Test Coverage & Performance

**Duration:** 8-10 days (~64 hours)
**Branch:** `claude/sprint3-test-coverage-<sessionId>`
**Goal:** Bring backend test coverage to acceptable levels. Close performance gaps.
**Depends on:** Sprint 2 (table centralization and repository extraction must be complete)

### Test Infrastructure (Do First)

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S3-1 | Add Testcontainers (PostgreSQL + Redis) | All 3 `build.gradle.kts`, test config | 3h | Integration test prerequisite — in-memory H2 is insufficient. |
| S3-2 | Add MockK to test dependencies | All 3 `build.gradle.kts` | 1h | Mocking prerequisite for unit tests. |
| S3-15 | **Extract repository layer from API service classes** | New repository files for Admin/User/Product | 8h | Services mix business logic and data access (violates Clean Architecture). Extract to enable unit testing. |

### Test Coverage (Current: API ~25%, License <1%, Sync ~15%, Common 0%)

| # | Task | File(s) | Effort | Target |
|---|------|---------|--------|--------|
| S3-3 | License service tests (activate, heartbeat, status, grace period) | `LicenseServiceTest.kt` (rewrite) | 8h | >60% coverage |
| S3-4 | API POS auth tests (login, refresh, lockout, revocation) | New `UserServiceTest.kt`, `AuthRoutesTest.kt` | 6h | Zero → >40% |
| S3-5 | API sync integration tests (push + pull with DB) | Expand `SyncPushPullIntegrationTest.kt` | 8h | End-to-end sync flow |
| S3-6 | Admin auth tests (MFA, password reset, sessions) | Expand `AdminAuthServiceTest.kt` | 6h | MFA flow coverage |
| S3-7 | Sync WebSocket integration tests | Expand `WebSocketHubTest.kt` | 6h | Connection lifecycle |
| S3-8 | License admin CRUD tests | New test files | 4h | Admin operations |
| S3-9 | Common module validation tests | New `ValidationScopeTest.kt` | 2h | 0% → >80% |
| S3-10 | API route-level auth enforcement tests | New route test files | 4h | 401/403 enforcement |

### Performance Fixes

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S3-11 | Add composite index on `sync_operations(store_id, entity_type, entity_id)` | V14 migration | 1h | Full table scan on conflict detection. |
| S3-12 | Batch sync push transactions | `SyncProcessor.kt` | 4h | Sequential transaction per operation — 50x fewer transactions with batching. |
| S3-13 | Add Redis connection pooling to API | `AppModule.kt`, `SyncProcessor.kt` | 2h | Single connection bottleneck under concurrency. |
| S3-14 | Make License HikariCP pool configurable | `DatabaseFactory.kt` in License | 1h | Pool size = 5 (too low for production with many devices). |

### Coverage Targets After Sprint 3

| Service | Before | After |
|---------|--------|-------|
| API | ~25% | >40% |
| License | <1% | >60% |
| Sync | ~15% | >30% |
| Common | 0% | >80% |

---

## Sprint 4: Documentation, Observability & Compliance

**Duration:** 4-5 days (~40 hours)
**Branch:** `claude/sprint4-docs-compliance-<sessionId>`
**Goal:** Close documentation gaps, add production observability, address compliance requirements.
**Depends on:** Sprint 3 (documented API must reflect tested implementation)

### Observability Stack

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S4-1 | Add Prometheus/Micrometer metrics to all services | All 3 services, new `/metrics` routes | 8h | No production metrics — blind to performance degradation. |
| S4-2 | Add request correlation ID middleware | All 3 services, Redis message format | 3h | Cross-service debugging impossible without correlation IDs. |
| S4-3 | Add structured logging (MDC) | All service log calls | 3h | String interpolation logging — machine-parseable logs impossible. |

### API Documentation

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S4-4 | Generate OpenAPI spec for all services | New plugin config, route annotations | 8h | No API documentation exists — KMM client built against implicit contracts. |
| S4-5 | Update CLAUDE.md backend architecture section | `CLAUDE.md` | 2h | Backend section incomplete. |
| S4-6 | Update gap analysis (sync engine at 60%, not 5%) | `docs/audit/gap_analysis_2026-03-09.md` | 1h | Outdated percentage — misleads planning. |
| S4-7 | Document backend database schemas | New doc file | 2h | No schema reference for 12 API + 4 License migrations. |

### Admin Panel Infrastructure Hardening

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S4-8 | Add HSTS + Permissions-Policy headers | `admin-panel/nginx.conf` | 0.5h | Missing security headers. |
| S4-9 | Replace CSP `unsafe-inline` with nonce-based scripts | `nginx.conf`, `vite.config.ts` | 2h | XSS mitigation weakened by inline scripts. |

### Compliance (Critical for Launch)

| # | Task | File(s) | Effort | Issue |
|---|------|---------|--------|-------|
| S4-10 | **Implement GDPR customer data export use case** | `shared/domain/.../usecase/`, `shared/data/` | 6h | GDPR right-to-access not functional. `ExportCustomerDataUseCase` missing. |
| S4-11 | **Implement audit trail sync to server** | `shared/data/.../repository/AuditRepositoryImpl.kt`, API sync routes | 4h | PCI-DSS R10 violation — audit entries generated by `SecurityAuditLogger` are discarded in memory, never written to DB. |

---

## Component Salvage/Discard Summary

| Component | Decision | Rationale |
|-----------|----------|-----------|
| `ForceSyncSubscriber` | **Already Discarded (A6)** | Dead code; `RedisPubSubListener` handles both channels |
| `SyncSessionManager` | **Already Discarded (A6)** | Superseded by `WebSocketHub` |
| Admin panel `auth-store.ts` | **Salvage + modify** | Narrow `setUser` type, remove `isLoading` |
| Admin panel `api-client.ts` | **Salvage + modify** | Add token refresh interceptor; keep CSRF and retry |
| Admin panel `useCurrentUser()` queryFn | **Salvage + refactor** | Move side effects to `useEffect` |
| Backend `AdminAuthService` inline tables | **Salvage + relocate** | Move table objects to `db/Tables.kt` |
| Backend `LicenseServiceTest.kt` | **Discard + rewrite** | All 6 tests check trivial assertions (`isNotBlank()`); zero logic tested |
| `AuditRepositoryImpl` (KMM) | **Salvage + extend** | Fully implemented for SQLite; add server sync pipeline |
| `backend/common/ValidationScope.kt` | **Salvage + extend** | Working DSL; add shared error models, JWT config |
| **Everything else** | **Salvage as-is** | No architectural violations found |

---

## Full Findings Tracker (94 Findings)

### Status Summary

| Category | Total | Resolved (Phase A) | Open | Severity |
|----------|-------|--------------------|----|----------|
| Backend Cross-Module | 11 | 2 | 9 | 3C, 5H, 3M |
| Backend Security | 18 | 6 | 12 | 0C (was 4C), 6H, 6M |
| Backend Code Quality | 21 | 4 | 17 | 1C (was 2C), 7H, 9M |
| Backend Test Coverage | 14 | 0 | 14 | 3C, 5H, 6M |
| Backend Documentation | 11 | 1 | 10 | 2C, 3H, 5M |
| Backend Performance | 4 | 0 | 4 | 0C, 2H, 2M |
| Admin Panel Bugs | 16 | 0 | 16 | 2C, 5H, 5M, 4L |
| Admin Panel Infra | 5 | 0 | 5 | 0C, 0H, 2M, 3L |
| KMM Compliance | 4 | 0 | 4 | 1C, 2H, 1M |
| **Total** | **94** (deduplicated) | **13** | **81** | |

### Sprint Assignment Map

| Sprint | Findings Resolved | Remaining After |
|--------|------------------|-----------------|
| Phase A (PR #285) ✅ | 13 | 81 |
| Sprint 1 (Admin Panel) | 16 | 65 |
| Sprint 2 (Backend) | 26 | 39 |
| Sprint 3 (Tests + Perf) | 22 | 17 |
| Sprint 4 (Docs + Compliance) | 17 | **0** |

---

## Risk Mitigation

### 1. Branch Divergence
Each sprint is a single feature branch. Sync with `origin/main` before every commit (per CLAUDE.md mandatory protocol). Keep PRs focused — never bundle unrelated sprints.

### 2. Database Migrations
Sprints 2 and 3 introduce V13 (bcrypt migration) and V14 (sync index). Both are **additive** — no destructive DDL. Always test locally:
```bash
./gradlew :backend-api:flywayMigrate
```

### 3. Token Refresh (S1-1 — Highest Risk)
Implement with request queue pattern:
1. On first 401, pause all requests
2. Call `POST /admin/auth/refresh`
3. If success → replay queued requests with new cookie
4. If failure → redirect to `/login`
5. Test with 30-second token TTL in dev

### 4. Dual-Hash Password Migration (S2-6)
Rolling migration — zero downtime, no forced password resets:
```kotlin
fun verifyPassword(input: String, storedHash: String): Boolean {
    // Try bcrypt first (new format)
    if (storedHash.startsWith("$2")) return BCrypt.verify(input, storedHash)
    // Fallback to SHA-256 (old format: base64url-salt:hex-hash)
    val sha256Match = verifySha256(input, storedHash)
    if (sha256Match) {
        // Re-hash to bcrypt on successful login (rolling upgrade)
        upgradeHashToBcrypt(userId, input)
    }
    return sha256Match
}
```

### 5. Zero Downtime Guarantee
All changes are backward-compatible. No destructive database operations. Migrations are additive. Docker services restart independently. Rolling deploys via `docker compose up -d --no-deps <service>`.

---

## Verification Checklist (Per Sprint)

### After Sprint 1
- [ ] Login → wait >15 min → session refreshes automatically
- [ ] MFA enable → disable → re-enable works without stale state
- [ ] "Revoke All Sessions" redirects to login gracefully
- [ ] No unhandled promise rejections in browser console
- [ ] `npm run build && npm run lint` — zero errors

### After Sprint 2
- [ ] `./gradlew clean test --parallel --continue` — all tests pass
- [ ] JWT cross-service auth verified (API ↔ License ↔ Sync)
- [ ] V13 migration: existing SHA-256 passwords still work; new logins upgrade to bcrypt
- [ ] Sync push with oversized fields rejected (field validation)
- [ ] `/health` reports Redis and downstream dependencies

### After Sprint 3
- [ ] API coverage >40%, License >60%, Sync >30%, Common >80%
- [ ] Sync push with 1000 operations completes in <5s (batched)
- [ ] `EXPLAIN ANALYZE` on conflict detection uses composite index

### After Sprint 4
- [ ] OpenAPI spec serves at `/docs` or `/swagger`
- [ ] `AuditRepositoryImpl` has zero TODO stubs
- [ ] Customer data export generates JSON/CSV with all PII fields
- [ ] nginx returns HSTS, Permissions-Policy, CSP-with-nonce headers
- [ ] Prometheus metrics available at `/metrics`

---

## Timeline

| Sprint | Days | Parallel? | Prerequisites |
|--------|------|-----------|---------------|
| Sprint 1 | 2-3 | Yes (with Sprint 2) | None |
| Sprint 2 | 5-6 | Yes (with Sprint 1) | None |
| Sprint 3 | 8-10 | No | Sprint 2 complete |
| Sprint 4 | 4-5 | No | Sprint 3 complete |
| **Total** | **17 days** (sequential) / **14 days** (S1 ‖ S2) | | |

---

## Decision Points for Stakeholders

1. **Sprint 1 vs Sprint 2 priority?** If only one developer: Sprint 1 first (user-facing bugs) or Sprint 2 first (security hardening)?
2. **GDPR compliance (S4-10, S4-11):** Required for Phase 1 MVP if targeting EU/UK market. Can defer if Sri Lanka only initially.
3. **POS password migration to bcrypt (S2-6):** Rolling migration is safe but adds complexity. Alternative: force password reset for all POS users on next login.
4. **Admin JWT HS256 → RS256 migration (Finding 1.2):** Not in this plan — would require coordinated API + License restart. Deferred to Phase 2. Acceptable risk if HS256 secret is properly rotated.
