# Contributing to ZyntaPOS

Welcome to the ZyntaPOS codebase. This guide captures the architectural conventions
you **must** follow before submitting code. Read it once; enforce it always.

---

## 1. Architecture Overview

ZyntaPOS follows **Clean Architecture** with strict layer separation:

```
composeApp/          ← UI shells (Android APK + Desktop JVM)
  feature/           ← Feature modules (pos, inventory, auth, accounting, …)
shared/
  domain/            ← Pure business logic. No framework dependencies.
  data/              ← Repositories, SQLDelight, Ktor network adapters, IRD API client.
  core/              ← Cross-cutting utilities (logging, CurrencyUtils, extensions).
  hal/               ← Hardware Abstraction Layer (printer, scanner, image).
  security/          ← Encryption, Keystore, JWT, PIN hashing, RBAC engine.
  seed/              ← Debug-only. Seed data for UI/UX testing. debugImplementation only.
```

Dependencies flow **inward only**: `feature → domain ← data`.
The `domain` layer must never import from `data`, `composeApp`, or any platform SDK.

---

## 2. Naming Conventions

### 2.1 Domain Model Classes (`shared/domain/model/`)

> **Rule:** Use plain, ubiquitous-language names. No suffix.
> **Reference:** [ADR-002](docs/adr/ADR-002-DomainModelNaming.md)

```kotlin
// ✅ CORRECT — plain domain model name
data class Product(val id: String, val name: String, val price: Double)

// ❌ WRONG — *Entity suffix is banned in the domain layer
data class ProductEntity(val id: String, ...)
```

Domain models are pure Kotlin data classes. They carry business meaning and identity,
never persistence annotations or framework coupling.

### 2.2 Persistence / ORM Classes (`shared/data/`)

> **Rule:** DB mapping types use `*Entity`, `*Table`, or `*Row` suffix.

```kotlin
// ✅ CORRECT — persistence mapping in shared/data
data class ProductEntity(val id: String, val name: String)   // hand-written mapper
// SQLDelight generates: ProductsQueries, Products (table type)
```

This keeps the naming boundary unambiguous: if you see `Product` it's a domain object;
if you see `ProductEntity` it's a DB row.

### 2.3 Code Review Checklist Item

When reviewing any class in `shared/domain/model/`, ask:

> *"Does this class have an `*Entity` suffix?"*
> If **yes** → request rename to plain name and cite ADR-002.

---

## 3. State Management (MVI)

All UI state follows the **MVI pattern**:

- `UiState` — immutable data class representing screen state.
- `UiIntent` — sealed class for user actions / events.
- `ViewModel` — exposes `StateFlow<UiState>` and accepts `UiIntent` via `onIntent()`.

```kotlin
// Example pattern
data class ProductListState(val products: List<Product> = emptyList(), val isLoading: Boolean = false)
sealed class ProductListIntent { data object LoadProducts : ProductListIntent() }
```

---

## 4. Dependency Injection (Koin)

- Declare modules in the feature's `di/` package, named `<feature>Module`.
- Platform-specific bindings go in `androidMain` / `jvmMain` Koin modules.
- Never use `GlobalContext.get()` outside of DI bootstrap code.

---

## 5. Coroutines & Flow

- Use `StateFlow` for UI state, `SharedFlow` for one-shot events.
- Always bind coroutine scope to lifecycle (`viewModelScope`, `lifecycleScope`).
- Repository functions return `Flow<T>` or `Result<T>` — never raw values.

---

## 6. Testing Standards

| Layer | Coverage Target | Tool |
|-------|----------------|------|
| Use Cases | 95% | Kotlin Test + Mockative 3 |
| Repositories | 80% | Kotlin Test + Mockative 3 |
| ViewModels | 80% | Kotlin Test + Turbine (Flow assertions) |
| Compose UI | Critical flows | Compose UI Test (Phase 2) |

---

## 7. Architecture Decision Records (ADRs)

All significant architectural decisions are documented in `docs/adr/`.

| ADR | Topic | Status |
|-----|-------|--------|
| [ADR-001](docs/adr/ADR-001-ViewModelBaseClass.md) | ViewModel Base Class Policy | ACCEPTED |
| [ADR-002](docs/adr/ADR-002-DomainModelNaming.md) | Domain Model Naming Convention | ACCEPTED |
| [ADR-003](docs/adr/ADR-003-SecurePreferences-Consolidation.md) | SecurePreferences Interface Consolidation | ACCEPTED |
| [ADR-004](docs/adr/ADR-004-keystore-token-scaffold-removal.md) | Keystore/Token Scaffold Directory Removal | ACCEPTED |
| [ADR-005](docs/adr/ADR-005-single-admin-account-management.md) | Single Admin Account Management | ACCEPTED |
| [ADR-006](docs/adr/ADR-006-backend-docker-build-in-ci.md) | Backend Docker Images Built in CI — Not on VPS | ACCEPTED |
| [ADR-007](docs/adr/ADR-007-database-per-service.md) | Database-Per-Service for Backend Microservices | ACCEPTED |
| [ADR-008](docs/adr/ADR-008-RS256-Key-Distribution.md) | RS256 Public Key Distribution — Bundle Default + Cache | ACCEPTED |

Before making a structural change (new module, new convention, tech adoption),
create a new ADR in `docs/adr/ADR-NNN-ShortTitle.md` and get it reviewed.

---

## 8. Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(pos): add hold order use case
fix(inventory): correct stock adjustment calculation
docs(adr): accept ADR-002 domain model naming
refactor(data): rename ProductEntity mapper to align with ADR-002
```

---

*Last updated: 2026-03-18*
