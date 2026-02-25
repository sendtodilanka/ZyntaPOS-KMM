# Module Dependency Graph — ZyntaPOS KMM

**Status:** Derived from actual `build.gradle.kts` files as of 2026-02-25.
**Module count:** 28 registered in `settings.gradle.kts` (26 production + 2 platform app/tools)

---

## 1. ASCII Dependency Diagram

```
                        ┌───────────────┐
                        │  :androidApp  │  (com.android.application shell)
                        └───────┬───────┘
                                │ implementation(project(...))
                                ▼
                    ┌───────────────────────┐
                    │      :composeApp      │  (KMP root — App() composable)
                    └───────────┬───────────┘
                                │
           ┌────────────────────┼────────────────────────┐
           ▼                    ▼                         ▼
  :composeApp:core    :composeApp:navigation      :composeApp:feature:*
  (BaseViewModel)     (ZyntaNavGraph, routes)     (16 feature modules)
           │                    │                         │
           │            ┌───────┘                         │
           │            ▼                                 │
           │   :composeApp:designsystem                   │
           │   (ZyntaTheme, Zynta* components)            │
           │            │                                 │
           └────────────┴──────────────┬──────────────────┘
                                       │
                              :shared:domain
                              (models, use cases, repo interfaces)
                                       │
                                       │ api(project(":shared:core"))
                                       ▼
                                :shared:core
                                (utilities, Result<T>, logging)

     :shared:data ──────────────────► :shared:domain
     :shared:security ──────────────► :shared:domain + :shared:core
     :shared:hal ────────────────────► :shared:domain + :shared:core
     :shared:seed ───────────────────► :shared:domain + :shared:core

     :tools:debug ──────────────────► :composeApp:core + :composeApp:designsystem
                                    + :shared:core + :shared:domain
                                    + :shared:seed + :shared:data
```

---

## 2. Module Dependency Table

Each row shows which modules a given module directly depends on (from `build.gradle.kts`).

### Shared Modules

| Module | Direct Dependencies |
|--------|---------------------|
| `:shared:core` | _(none — pure Kotlin, no project deps)_ |
| `:shared:domain` | `:shared:core` (api) |
| `:shared:data` | `:shared:domain` (api) |
| `:shared:security` | `:shared:core` (api), `:shared:domain` (api) |
| `:shared:hal` | `:shared:core` (api), `:shared:domain` (api) |
| `:shared:seed` | `:shared:core` (impl), `:shared:domain` (impl) |

### UI Infrastructure

| Module | Direct Dependencies |
|--------|---------------------|
| `:composeApp:core` | _(none — pure KMP BaseViewModel, no project deps)_ |
| `:composeApp:designsystem` | `:shared:core` (api) |
| `:composeApp:navigation` | `:composeApp:designsystem` (api), `:shared:domain` (api), `:shared:security` (api) |
| `:composeApp` | `:shared:core`, `:shared:domain`, `:shared:data`, `:shared:security`, `:shared:hal`, `:shared:seed` (debug), `:composeApp:core`, `:composeApp:navigation`, all 16 feature modules, `:tools:debug` |

### Feature Modules

All feature modules share a common base dependency set. Modules that also use hardware (HAL) or
the `auth` session are noted separately.

| Module | Project Dependencies |
|--------|----------------------|
| `:composeApp:feature:auth` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:dashboard` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:onboarding` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:pos` | `:composeApp:core`, `:composeApp:designsystem`, `:composeApp:feature:auth`, `:shared:core`, `:shared:domain`, `:shared:hal` |
| `:composeApp:feature:inventory` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:register` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain`, `:shared:hal` |
| `:composeApp:feature:reports` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain`, `:shared:hal` |
| `:composeApp:feature:settings` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain`, `:shared:hal` |
| `:composeApp:feature:customers` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:coupons` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:expenses` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:staff` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:multistore` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:admin` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:media` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |
| `:composeApp:feature:accounting` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` |

### Platform Apps / Tools

| Module | Direct Dependencies |
|--------|---------------------|
| `:androidApp` | `:composeApp`, `:shared:core`, `:shared:domain`, `:shared:data`, `:shared:security`, `:shared:hal`, `:shared:seed`, `:composeApp:navigation`, all 16 feature modules, `:tools:debug` |
| `:tools:debug` | `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain`, `:shared:seed`, `:shared:data` |

---

## 3. Allowed vs Forbidden Dependencies

### Allowed

```
:shared:core         → (nothing)
:shared:domain       → :shared:core only
:shared:data         → :shared:domain (and transitively :shared:core)
:shared:security     → :shared:core, :shared:domain
:shared:hal          → :shared:core, :shared:domain
:shared:seed         → :shared:core, :shared:domain
:composeApp:core     → (nothing — platform VM lifecycle only)
:composeApp:designsystem  → :shared:core
:composeApp:navigation    → :composeApp:designsystem, :shared:domain, :shared:security
:composeApp:feature:*     → :composeApp:core, :composeApp:designsystem, :shared:core, :shared:domain
:composeApp:feature:pos   → additionally :composeApp:feature:auth, :shared:hal
:composeApp:feature:register, :reports, :settings → additionally :shared:hal
```

### Forbidden (Architecture Guardrails)

| Forbidden Direction | Rule | ADR |
|--------------------|------|-----|
| `:shared:domain` importing from `:shared:data` | Domain must not depend on its own implementation | ADR-002 |
| `:shared:domain` importing from `:shared:security` | Domain is security-framework-free | ADR-003 |
| `:shared:domain` importing from `:shared:hal` | Domain must not import hardware drivers | Architecture |
| `:shared:domain` importing from any `:composeApp:feature:*` | Domain is UI-framework-free | Architecture |
| `:shared:data` importing from `:shared:security` | Data module uses `SecureStoragePort` (domain interface) instead | ADR-003 (MERGED-F3) |
| `:composeApp:feature:*` importing from `:shared:data` directly | Features use domain use cases, not data implementations | Architecture |
| `:shared:seed` as `implementation` (non-debug) | Seed data must not ship in production | CLAUDE.md pitfall #9 |

---

## 4. Dependency Violation Audit Result

A manual review of all `build.gradle.kts` files against the guardrails above found:

**0 architecture violations** in the current codebase.

Notable clean boundaries confirmed:
- `:shared:domain` only depends on `:shared:core` (via `api`). No imports from data/security/hal.
- `:shared:data` imports `:shared:domain` but not `:shared:security` directly. It uses `SecureStoragePort` (domain interface) after the MERGED-F3 refactor (2026-02-22).
- All 16 feature modules depend only on `:composeApp:core`, `:composeApp:designsystem`, `:shared:core`, `:shared:domain` (and optionally `:shared:hal` or `:composeApp:feature:auth`). None import `:shared:data` or `:shared:security` directly.
- `:composeApp:navigation` imports `:shared:security` for the `RbacEngine` Koin binding but this is a presentation-layer module, not a domain module — this is permitted.

---

## 5. Transitive Dependency Paths

Key transitive chains to be aware of:

```
:androidApp
    → :composeApp
        → :composeApp:navigation
            → :shared:security
                → :shared:domain
                    → :shared:core

:composeApp:feature:pos
    → :composeApp:feature:auth  (for PIN session context)
    → :shared:hal               (for BarcodeScanner events)
    → :shared:domain            (use cases, models)
```

Because `:composeApp` (the root KMP module) aggregates all features and shared modules, it
transitively pulls in the entire dependency graph. This is intentional for the KMP library root
and does not represent a violation.
