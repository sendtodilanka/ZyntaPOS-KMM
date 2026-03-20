# ZyntaPOS — Module Implementation Progress

> **Purpose:** Single source of truth for module readiness across all phases.
> A module is considered IMPLEMENTED only when it has: ViewModel, Screen composable(s),
> and a Koin DI binding — all wired into the app's navigation graph.
>
> **Last Updated:** 2026-03-20
> **Legend:** ✅ Done | 🔄 In Progress | 🔲 Not Started

---

## Shared Modules

| Module | Status | Phase | Notes |
|--------|--------|-------|-------|
| `:shared:core` | ✅ IMPLEMENTED | 1 | Utils, extensions, base models, SystemHealthTracker |
| `:shared:domain` | ✅ IMPLEMENTED | 1 | Use cases, repository interfaces, 77 domain models |
| `:shared:data` | ✅ IMPLEMENTED | 1 | SQLDelight (36 .sq files), Ktor, repository impls, sync engine |
| `:shared:hal` | ✅ IMPLEMENTED | 1 | Printer/Scanner/CashDrawer HAL interfaces + all platform drivers |
| `:shared:security` | ✅ IMPLEMENTED | 1 | JWT, RBAC, AES-256-GCM, SQLCipher, PinManager, SecurePreferences |
| `:shared:seed` | ✅ IMPLEMENTED | 1 | Debug-only SeedRunner (8 categories, 5 suppliers, 25 products, 15 customers) |

---

## Presentation / Infrastructure Modules

| Module | Status | Phase | Notes |
|--------|--------|-------|-------|
| `:composeApp:core` | ✅ IMPLEMENTED | 1 | `BaseViewModel<S,I,E>`, MVI contracts, lifecycle-aware scopes |
| `:composeApp:designsystem` | ✅ IMPLEMENTED | 1 | Material 3 theme, tokens, Zynta* component library |
| `:composeApp:navigation` | ✅ IMPLEMENTED | 1 | Type-safe nav graph, RBAC route gating, adaptive nav shell |

---

## Feature Modules

| Module | Status | ViewModels | Screens | Koin Module | Phase |
|--------|--------|-----------|---------|-------------|-------|
| `:feature:auth` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:pos` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:inventory` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:register` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:reports` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:settings` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 |
| `:feature:dashboard` | ✅ IMPLEMENTED | ✅ (2) | ✅ (2) | ✅ | 1 |
| `:feature:onboarding` | ✅ IMPLEMENTED | ✅ (2) | ✅ (1) | ✅ | 1 |
| `:feature:customers` | ✅ IMPLEMENTED | ✅ (2) | ✅ (4) | ✅ | 2 |
| `:feature:coupons` | ✅ IMPLEMENTED | ✅ (2) | ✅ (2) | ✅ | 2 |
| `:feature:multistore` | ✅ IMPLEMENTED | ✅ (2) | ✅ (6) | ✅ | 2 |
| `:feature:expenses` | ✅ IMPLEMENTED | ✅ (2) | ✅ (3) | ✅ | 2 |
| `:feature:staff` | ✅ IMPLEMENTED | ✅ (2) | ✅ (7) | ✅ | 3 |
| `:feature:admin` | ✅ IMPLEMENTED | ✅ (3) | ✅ (5) | ✅ | 3 |
| `:feature:media` | ✅ IMPLEMENTED | ✅ (2) | ✅ (1) | ✅ | 3 |
| `:feature:accounting` | ✅ IMPLEMENTED | ✅ (10) | ✅ (10) | ✅ | 3 |
| `:feature:diagnostic` | ✅ IMPLEMENTED | ✅ (1) | ✅ (1) | ✅ | 3 |

---

## Platform Apps / Tools

| Module | Status | Phase | Notes |
|--------|--------|-------|-------|
| `:androidApp` | ✅ IMPLEMENTED | 1 | Android application shell (AGP 9.0+ pattern) |
| `:tools:debug` | ✅ IMPLEMENTED | 1 | 6-tab Debug Console (Seeds/Database/Auth/Network/Diagnostics/UI) |

---

## Phase Rollup

| Phase | Status | Scope |
|-------|--------|-------|
| Phase 0 — Foundation | ✅ Complete | Build system, module scaffold, secrets, CI skeleton |
| Phase 1 — MVP | ✅ Complete | Single-store POS, offline sync, core features (auth, pos, inventory, register, reports, settings, dashboard, onboarding) |
| Phase 2 — Growth | ✅ ~98% implemented | Multi-store, CRM, promotions, expenses, CRDT sync (C6.1 complete 2026-03-19) |
| Phase 3 — Enterprise | 🔄 ~80% implemented | Staff/HR, admin, e-invoicing (IRD), accounting, diagnostic (IRD mTLS API, advanced charts, i18n pending) |

---

## Known Remaining Gaps (Phase 2–3 Backlog)

| Gap | Module | Severity |
|-----|--------|----------|
| C1.1 Global Product Catalog admin panel routes registered | `:admin-panel` | ✅ Done (2026-03-19) |
| ~~CRDT `ConflictResolver` not implemented~~ | `:shared:data` | ✅ DONE (C6.1, 2026-03-19) |
| Cash drawer open event not wired in POS payment flow | `:feature:pos` | Low |
| IRD e-invoice submission: format/sandbox validation pending | `:feature:accounting` | Medium |
| RS256 public key fetch not called after login (`AuthRepositoryImpl`) | `:shared:data` | Low |
| `VacuumDatabaseUseCase` not wired in AdminModule | `:feature:admin` | Low |
| Register session guard on login TODO (Sprint 20) | `:feature:auth` | Low |

---

## Transition Checklist (SCAFFOLD → IMPLEMENTED)

Before marking any module as IMPLEMENTED, verify **all** of the following:

- [ ] `*ViewModel.kt` extends `ui.core.mvi.BaseViewModel` (ADR-001)
- [ ] At least one `*Screen.kt` composable exists and compiles
- [ ] Koin `*Module.kt` binds the ViewModel and all use cases
- [ ] Module is included in `AppModule` / root DI graph
- [ ] At least one navigation route defined in `:composeApp:navigation`
- [ ] Unit tests exist for the ViewModel (≥ 80% coverage target)
- [ ] KDoc present on all public composables and the ViewModel
