# Project Status Investigation — Code State (2026-04-23)

Scope: Code state only — TODO comments, recent modifications, stubs, test gaps.

---

## Section 1 — TODO/FIXME/HACK Comments in Source

Most occurrences are **task tracking references** (TODO-NNN format) pointing to docs, not unresolved work. Genuine code TODOs are minimal.

### Genuine code TODOs (action items)

| File | Line | Note |
|------|------|------|
| `shared/data/.../jvmMain/.../DatabaseKeyProvider.kt` | 107 | "Sprint 8: Replace with OS Credential Manager via DatabaseKeyManager" |
| `composeApp/feature/auth/.../LoginScreen.kt` | 397 | "Sprint 9 design-system polish — Replace with SVG vector asset" |
| `shared/data/.../repository/ReportRepositoryImpl.kt` | 60 | "empty/zero stubs with a TODO comment indicating what is needed" — REPORT STUB |
| `backend/api/.../sync/EntityApplier.kt` | 1369 | "TODO: route to dead-letter" — sync push failure handling |

### Task-reference TODOs (existing tracked work — these reference doc files in `docs/todo/`)

- **TODO-001** (Single admin guard): `UserRepositoryImpl.kt:85`, `SettingsViewModel.kt:608`, `SettingsViewModelTest.kt:537`
- **TODO-005** (Modern dashboard / nav): `ZyntaScaffold.kt:32`
- **TODO-006** (Remote Diagnostic Access — ENTERPRISE): 11 references across `composeApp/navigation/*`, `feature/diagnostic/*`, `backend/sync/hub/DiagnosticRelay.kt`, `backend/sync/routes/DiagnosticWebSocketRoutes.kt`, `admin-panel/src/types/diagnostic.ts`
- **TODO-007g** (Server-side sync engine): `KtorApiService.kt:96`, `SyncEngine.kt:305`, `SyncProcessor.kt:18`, `WebSocketMessages.kt:7`
- **TODO-008** (SEO/ASO + Play Integrity): `PosScreen.kt:76`, `PosViewModel.kt:169,690`, `IntegrityRoutes.kt`, `PlayIntegrityService.kt`
- **TODO-008a** (Email management — Resend, Chatwoot, CF Worker): heavy presence in `backend/api/service/InboundEmailProcessor.kt`, `ChatwootService.kt`, `AdminEmailRoutes.kt`, `InboundEmailRoutes.kt`, `Tables.kt:195,216`, `AdminPermissions.kt:64`, `AppConfig.kt:28-37`
- **TODO-009** (Ktor security hardening — deserialization block, body limits, rate limit, headers): `Application.kt:36,64`, `Csrf.kt:23`, `RateLimit.kt:12`, `Security.kt:9`, `ContentLengthLimit.kt:10`, `license/Application.kt:22`, `sync/Application.kt:28`
- **TODO-010** (Security monitoring + canary tokens): `backend/api/Application.kt:27`, `backend/sync/config/SyncConfig.kt:3`
- **TODO-011** (Analytics): `shared/core/.../analytics/AnalyticsTracker.kt:4`

### Admin panel (TypeScript)

Only one non-test reference: `admin-panel/src/types/diagnostic.ts:1` — header comment for TODO-006 types.

=== SECTION 1 COMPLETE ===

---

## Section 2 — Recently Modified Files (last 30 days)

Activity volume by top-level module (file count includes generated SqlDelight + every touched .kt/.ts):

| Module | Files touched | Notes |
|--------|---------------|-------|
| `shared/domain` | 559 | Heavy — domain models / use-cases churn |
| `composeApp/feature` | 337 | Feature module work continues |
| `shared/data` | 303 | Repos, sync engine, sqldelight |
| `admin-panel/src` | 210 | Active — types, api, hooks, components, charts, layout, e2e |
| `backend/api` | 173 | Routes, services, sync, email, plugins |
| `composeApp/designsystem` | 64 | Design tokens / components |
| `shared/hal` | 52 | HAL ports |
| `shared/security` | 45 | Token storage, RBAC, prefs |
| `shared/core` | 42 | i18n, analytics, MVI |
| `backend/license` | 33 | License + tests |
| `backend/sync` | 31 | Diagnostic relay, WS routes (TODO-006) |
| `tools/debug` | 28 | Debug console |
| `backend/common` | 13 | Shared validation DSL |
| `shared/seed` | 11 | Seed fixtures |
| `composeApp/navigation` | 10 | Nav graph (likely diagnostic route wiring) |
| `website/src` | 9 | Astro marketing site (data/seo) |
| `admin-panel/e2e` | 6 | Playwright auth/navigation/visual/accessibility/smoke |
| `androidApp/src` | 5 | Application bootstrap |
| `cf-workers/email-inbound-handler` | 1 | TODO-008a inbound email worker |

### Active hot-spots (admin-panel routes/components recently touched)

- `admin-panel/src/api/*` — full set rewritten/touched (auth, users, licenses, tickets, audit, alerts, customers, metrics, inventory, email, stores, sync, exchange-rates, master-products, config, health, diagnostic)
- `admin-panel/src/components/{shared,charts,layout,licenses,users,stores,sync,audit,config}` — broad UI build-out
- `admin-panel/src/types/*` — full type model refresh (license, ticket, health, diagnostic, sync, store, audit, alert, user, master-product, metrics, config, api)
- `admin-panel/e2e/*.spec.ts` — Playwright suite added

### Backend hot-spots

- `backend/api` (173 files): broad — routing, sync (push/pull), admin (inventory/transfer/replenishment), email (inbound/outbound/Chatwoot), Play Integrity, CSRF/RateLimit/Security plugins
- `backend/sync` (31 files): DiagnosticRelay + DiagnosticWebSocketRoutes (TODO-006) recently touched
- `cf-workers/email-inbound-handler/src/index.ts` — TODO-008a worker recently modified

=== SECTION 2 COMPLETE ===

---

## Section 3 — Empty / Stub Implementations

### Production-code stubs

- **No `TODO()` (Kotlin builtin) anywhere in production code.** Clean.
- **No `NotImplementedError` in production.** All occurrences are intentional test fakes inside `*Test.kt` files (e.g., `GetMultiStoreKPIsUseCaseTest.kt` test fake).
- `shared/data/.../ReportRepositoryImpl.kt:60` — header doc says "complex enterprise reports... return sensible empty/zero stubs with a TODO comment indicating what is needed". File-level scan found no inline `TODO` markers, suggesting either: (a) stubs were removed since the doc was written, or (b) the stubs are pure no-op returns without inline TODOs. **Worth a closer audit.**
- `composeApp/feature/inventory/.../SupplierDetailScreen.kt:247` — "Section 2: Purchase History (read-only, Phase 1 stub)" — purchase history view is not yet wired.
- `backend/api/.../sync/EntityApplier.kt:1369` — "TODO: route to dead-letter" — sync push failures are silently dropped instead of going to a dead-letter queue.

### Safe stubs by design (HAL — not action items)

- `shared/hal/.../NullPrinterPort.kt` — intentional null-object pattern; replaced when operator configures hardware in Settings.
- `HalModule.android.kt` — receipt + label printer ports start as `NullPrinterPort` until configured.

=== SECTION 3 COMPLETE ===

---

## Section 4 — Test Coverage Gaps

(File counts only — not line coverage. Ratios are a proxy.)

### Shared modules — healthy

| Module | src | test | Ratio | Notes |
|--------|-----|------|-------|-------|
| `shared/core` | 29 | 13 | 0.45 | Decent |
| `shared/domain` | 427 | 132 | 0.31 | Many models = low ratio is normal |
| `shared/data` | 138 | 93 | 0.67 | Good |
| `shared/security` | 33 | 12 | 0.36 | OK |
| `shared/hal` | 43 | 9 | 0.21 | LOW — could use more |
| `shared/seed` | 9 | 2 | 0.22 | Acceptable (debug-only) |

### UI infrastructure — designsystem under-tested

| Module | src | test | Notes |
|--------|-----|------|-------|
| `composeApp/core` | 1 | 1 | OK |
| `composeApp/designsystem` | 62 | **2** | **GAP — only 2 tests for 62 source files** |
| `composeApp/navigation` | 8 | 2 | Acceptable |

### Feature modules — broad coverage gaps

Modules with only **1 test file** despite many sources:

| Feature | src | test | Status |
|---------|-----|------|--------|
| `feature/coupons` | 7 | 1 | GAP |
| `feature/customers` | 9 | 1 | GAP |
| `feature/diagnostic` | 6 | 1 | OK (new TODO-006 work) |
| `feature/expenses` | 8 | 1 | GAP |
| `feature/media` | 12 | 1 | GAP |
| `feature/onboarding` | 6 | 1 | OK |
| `feature/register` | 13 | 1 | GAP |
| `feature/reports` | 18 | 1 | **GAP — reports critical** |
| `feature/admin` | 15 | 2 | GAP |
| `feature/multistore` | 24 | 2 | GAP |
| `feature/pos` | 41 | **2** | **MAJOR GAP — POS is core feature** |
| `feature/staff` | 18 | 2 | GAP |
| `feature/inventory` | 49 | 6 | OK-ish but room |
| `feature/settings` | 33 | 5 | OK-ish |

### Backend — well-covered

| Module | src | test | Ratio |
|--------|-----|------|-------|
| `backend/api` | 121 | 52 | 0.43 |
| `backend/license` | 21 | 12 | 0.57 |
| `backend/sync` | 20 | 11 | 0.55 |
| `backend/common` | 6 | 7 | 1.17 (excellent) |

### Highest-priority test gaps

1. **`composeApp/feature/pos`** — only 2 tests for 41 files; this is the most critical user feature.
2. **`composeApp/feature/reports`** — 1 test for 18 files; financial output, high risk.
3. **`composeApp/designsystem`** — 2 tests for 62 files; UI regressions likely.
4. **`feature/customers`, `feature/coupons`, `feature/expenses`, `feature/register`, `feature/media`, `feature/staff`, `feature/admin`, `feature/multistore`** — all under-tested.

=== SECTION 4 COMPLETE ===

---

## Section 5 — Build & Module Health

- **29 Gradle modules** declared in `settings.gradle.kts` (matches CLAUDE.md target).
- **17 feature modules** (auth, pos, inventory, register, reports, settings, customers, coupons, expenses, staff, multistore, admin, diagnostic, media, dashboard, accounting, onboarding) — all present.

### Commit cadence

- **Since 2026-03-01:** active commits across all top-level modules.
- **Since 2026-04-01:** only 2 commits — both docs (audit reports + investigation WIP). **Code freeze for ~3 weeks.**
- Most-recent code commit: `2cc6980 fix(api): resolve Ktor 3.x compile errors in ContentLengthLimit and ExportRoutes` — late March.
- The user's "2 weeks away" matches the code-freeze gap exactly.

### Modules with high March activity (commits touched files)

1. `shared/domain` (576 files touched)
2. `composeApp/feature/*` (362)
3. `shared/data` (344)
4. `backend/api` (220)
5. `admin-panel/src` (212) + `admin-panel/e2e` (73)
6. `composeApp/designsystem` (67)
7. `shared/hal` (64)
8. `website/src` (62)
9. `shared/security` (54)
10. `shared/core` (52)

### Recently active (March commits)

- `cf-workers/email-inbound-handler` (4) — TODO-008a CF Worker
- `backend/postgres`, `backend/email-relay`, `backend/caddy` — infra hardening
- `androidApp/src` (23) — bootstrap changes (likely diagnostic module wiring)

### Recent commit themes (last 10)

- 3a3ca38 docs(todo): WIP project status investigation
- 9407834 docs(audit): admin panel functional audit (1185 lines, 51 findings)
- 84ac8bf docs(audit): frontend map + routes audit (partial)
- edb1db2 / e65c9e8 docs(claude): Long-Running Analysis Protocol + parallel agent splitting
- 89f8607 / 99aeed9 fix(docker): Redis user/permissions
- 2cc6980 fix(api): Ktor 3.x compile errors in ContentLengthLimit + ExportRoutes
- f20a9d1 docs(deployment): security audit env vars

=== SECTION 5 COMPLETE ===

---

## Section 6 — Synthesis (Code-Only View)

### 1. Where the most pending TODO references live

The `TODO-NNN` doc-tracked work clusters in:

- **TODO-008a (Email management)** — biggest spread: backend/api (Tables.kt, AppConfig.kt, AdminEmailRoutes.kt, InboundEmailRoutes.kt, ChatwootService.kt, InboundEmailProcessor.kt, AdminPermissions.kt, AppModule.kt) + cf-workers/email-inbound-handler. Multiple integration touchpoints suggests wiring is mostly done; verify end-to-end against `docs/todo/008a-email-management-system.md`.
- **TODO-009 (Ktor security hardening)** — broad presence across all 3 backend services (api, license, sync) — Csrf, RateLimit, ContentLengthLimit, Security headers, Application bootstrap. Recent commit `2cc6980` fixed Ktor 3.x compile errors here.
- **TODO-006 (Remote Diagnostic Access — ENTERPRISE)** — present in feature/diagnostic, navigation graph wiring, backend/sync DiagnosticRelay + DiagnosticWebSocketRoutes, admin-panel diagnostic types/api/routes. Likely active or near-complete.
- **TODO-007g (Server-side sync engine)** — sync push/pull, WebSocketMessages, KtorApiService, SyncEngine pull cursor.
- **TODO-008 (ASO/Play Integrity)** — PosViewModel review counter, Play Integrity backend service.
- **TODO-011 (Analytics)** — only one anchor (`AnalyticsTracker.kt`) — likely partial.
- **TODO-005, TODO-001, TODO-010** — narrow remaining touch points.

### 2. Modules actively worked on in Mar/Apr 2026

Code freeze since 2026-04-01 (only docs). High-volume March work:

- `shared/domain`, `shared/data` — large domain/data churn
- `backend/api` — sync, email, security plugins
- `admin-panel/src` + `admin-panel/e2e` — full UI build-out + Playwright e2e
- `composeApp/feature/*` — broad feature work
- `cf-workers/email-inbound-handler` — TODO-008a CF Worker

### 3. Stub / incomplete modules

- **`composeApp/feature/inventory/SupplierDetailScreen.kt:247`** — Purchase History pane is a Phase 1 read-only stub.
- **`shared/data/.../ReportRepositoryImpl.kt`** — class doc claims "complex enterprise reports return empty/zero stubs" — needs audit to confirm current state.
- **`backend/api/.../EntityApplier.kt:1369`** — sync push errors are dropped; dead-letter queue not yet implemented.
- **`shared/data/.../jvmMain/.../DatabaseKeyProvider.kt:107`** — desktop key storage uses placeholder; Sprint 8 plan was OS Credential Manager.
- **`composeApp/feature/auth/.../LoginScreen.kt:397`** — placeholder logo asset; SVG vector replacement deferred to Sprint 9 polish.

### 4. Test coverage gaps to address

Most urgent (critical features with ≤2 test files):

1. **`composeApp/feature/pos`** — 41 src / 2 test
2. **`composeApp/feature/reports`** — 18 src / 1 test (financial output, high risk)
3. **`composeApp/designsystem`** — 62 src / 2 test (UI regression risk)
4. **`composeApp/feature/multistore`, `feature/admin`, `feature/staff`** — 15-24 src / 2 test
5. **Single-test features:** `coupons`, `customers`, `expenses`, `media`, `register`

Backend test coverage is healthy (43-100% file ratios). Shared modules are mostly OK except `shared/hal` (43 src / 9 test).

### Recommended next actions (code-only perspective)

1. **Resume from where you left off** — last code commit was 2026-03-25 (`fix(api): Ktor 3.x compile errors`); since then only docs/audits.
2. **Verify TODO-008a end-to-end** (Email management) — heaviest TODO surface area; CF Worker, backend services, and routes are wired.
3. **Wire TODO-006 last-mile** (Remote Diagnostic) — types + relay + admin viewer all exist; verify the technician-side admin panel UI and consent flow integration tests.
4. **Address Phase 1 stub** in `SupplierDetailScreen` purchase history view (or backlog explicitly).
5. **Implement EntityApplier dead-letter queue** for sync push failures.
6. **Add tests** for `feature/pos`, `feature/reports`, `designsystem` — these are the largest production code paths with the smallest test footprint.
7. **Read parallel partial reports** in `docs/todo/` to cross-reference docs/git findings before deciding priorities.

=== ALL CODE SECTIONS COMPLETE ===
