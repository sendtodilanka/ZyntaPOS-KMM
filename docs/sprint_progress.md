# ZyntaPOS — Module Implementation Progress

> **Purpose:** Single source of truth for module readiness across all phases.
> Update this file whenever a module transitions from SCAFFOLD → IN PROGRESS → IMPLEMENTED.
> A module is considered IMPLEMENTED only when it has: ViewModel, Screen composable(s),
> and a Koin DI binding — all wired into the app's navigation graph.
>
> **Last Updated:** 2026-02-22
> **Legend:** ✅ Done | 🔲 Not Started | 🔄 In Progress

---

## Shared Modules

| Module | Status | Phase | Notes |
|--------|--------|-------|-------|
| `:shared:core` | ✅ IMPLEMENTED | 1 | Utils, extensions, base models |
| `:shared:domain` | ✅ IMPLEMENTED | 1 | Use cases, repository interfaces, domain models |
| `:shared:data` | ✅ IMPLEMENTED | 1 | SQLDelight, Ktor, repository impls, mappers |
| `:shared:hal` | ✅ IMPLEMENTED | 1 | Printer/Scanner/Cash Drawer HAL interfaces + expect/actual |
| `:shared:security` | ✅ IMPLEMENTED | 1 | JWT, RBAC, AES-256, SQLCipher integration |

---

## Presentation / Infrastructure Modules

| Module | Status | Phase | Notes |
|--------|--------|-------|-------|
| `:composeApp:core` (M21) | ✅ IMPLEMENTED | 1 | `BaseViewModel`, MVI contracts, lifecycle-aware scopes |
| `:composeApp:designsystem` | ✅ IMPLEMENTED | 1 | Material 3 theme, tokens, Zynta component library |
| `:composeApp:navigation` | ✅ IMPLEMENTED | 1 | Type-safe nav graph skeleton (routes defined, graphs incomplete) |

---

## Feature Modules

| Module | Status | ViewModel | Screens | Koin Module | Phase | Sprint Target |
|--------|--------|-----------|---------|-------------|-------|---------------|
| `:feature:auth` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprints 12–13 |
| `:feature:pos` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprints 14–17 |
| `:feature:inventory` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprints 18–19 |
| `:feature:register` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprints 20–21 |
| `:feature:reports` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprint 22 (sales + stock only) |
| `:feature:settings` | ✅ IMPLEMENTED | ✅ | ✅ | ✅ | 1 | Sprint 23 (core settings) |
| `:feature:customers` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 2 | TBD |
| `:feature:coupons` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 2 | TBD |
| `:feature:multistore` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 2 | TBD |
| `:feature:expenses` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 2 | TBD |
| `:feature:staff` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 3 | TBD |
| `:feature:admin` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 3 | TBD |
| `:feature:media` | 🔲 SCAFFOLD — Not Started | ❌ | ❌ | ❌ | 3 | TBD |

---

## Scaffold Module Details

The 7 SCAFFOLD modules each contain only a single placeholder file (`*Module.kt`) with
an empty Koin module definition. They are registered in `settings.gradle.kts` but have
**no ViewModel**, **no Screen composables**, and **no navigation graph entry point**.

| Module | Placeholder File | Gap |
|--------|-----------------|-----|
| `:feature:admin` | `AdminModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:coupons` | `CouponsModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:customers` | `CustomersModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:expenses` | `ExpensesModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:media` | `MediaModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:multistore` | `MultistoreModule.kt` | No ViewModel, no Screens, not wired to nav |
| `:feature:staff` | `StaffModule.kt` | No ViewModel, no Screens, not wired to nav |

> ⚠️ **Risk Note (MERGED-G7):** The gap between module *registration* and module *implementation*
> is currently hidden because `App.kt` has no navigation graph. Once the nav graph is wired,
> these 7 modules will be unreachable routes until implemented. Sprint planning must assign
> explicit sprint targets before Phase 2 work begins.

---

## Phase Rollup

| Phase | Total Modules | Implemented | Scaffold | Completion |
|-------|--------------|-------------|----------|------------|
| Phase 1 | 11 | 11 | 0 | ✅ 100% |
| Phase 2 | 4 | 0 | 4 | 🔲 0% |
| Phase 3 | 3 | 0 | 3 | 🔲 0% |
| **Total** | **18** | **11** | **7** | **61%** |

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
