# ADR-001: ViewModel Base Class Policy

Date: 2026-02-21
Status: ACCEPTED

---

## Context

The ZyntaPOS project uses a canonical MVI base class:

```
composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt
```

This class provides:

- **AndroidX ViewModel lifecycle** — safe, scoped to the UI host
- **viewModelScope** — structured coroutine cancellation
- **Channel<E>(BUFFERED)** for one-shot side effects (navigation, toasts, etc.)
- **updateState{}** — atomic, thread-safe state mutation
- **abstract suspend fun handleIntent(I)** — the single entry point for all user intents (MVI contract)

A zombie duplicate of `BaseViewModel` existed in `:shared:core` (a module with no
AndroidX dependency) and was found during Sprint 4 audit. It was deleted because it
could never fulfil the AndroidX contract and caused confusion for new contributors.

---

## Decision

> **All ViewModels in feature modules MUST extend `ui.core.mvi.BaseViewModel`.**
>
> Direct extension of `androidx.lifecycle.ViewModel` is **PROHIBITED** in any
> feature module within the ZyntaPOS codebase.

---

## Rationale

| Reason | Detail |
|---|---|
| **Consistency** | Every feature screen shares the same MVI contract — same state update API, same effect channel, same intent handler signature. |
| **Safety** | `BaseViewModel` manages `viewModelScope` and effect channel lifecycle. Raw extension risks accidental scope leaks or missed cancellations. |
| **Auditability** | If the MVI pattern ever needs to evolve (e.g., migrate to a newer lifecycle API), there is exactly one class to update. |
| **Onboarding** | New contributors have a single, documented entry point rather than discovering multiple ViewModel superclasses. |

---

## Enforcement

### Immediate (Code Review Gate)
Pull Requests that contain a class directly extending `androidx.lifecycle.ViewModel`
inside any `:composeApp:feature:*` or `:shared:*` module **MUST be rejected** by
the reviewer. The correct superclass is:

```kotlin
import com.zynta.pos.ui.core.mvi.BaseViewModel

class MyFeatureViewModel(
    // dependencies
) : BaseViewModel<MyState, MyIntent, MyEffect>(MyState.Initial) {

    override suspend fun handleIntent(intent: MyIntent) {
        // ...
    }
}
```

### Future (Automated)
A custom **Android Lint rule** will be added to the `:lint` module to enforce this
at build time, producing a build error (not just a warning) for violations.

---

## Non-Compliant Code Found and Fixed (Sprint 4)

| Class | Location | Action Taken |
|---|---|---|
| `ReportsViewModel` | `:composeApp:feature:reports` | Migrated to extend `BaseViewModel` |
| `SettingsViewModel` | `:composeApp:feature:settings` | Migrated to extend `BaseViewModel` |
| Zombie `BaseViewModel` | `:shared:core` | **Deleted** — could not fulfil AndroidX contract |

---

## Consequences

- All new feature ViewModels have a clear, enforced template.
- The MVI contract is guaranteed to be uniform across the entire application.
- Minor: contributors must import `ui.core.mvi.BaseViewModel` explicitly; IDE
  templates / Live Templates should be configured to do this automatically.

---

*Approved by: Dilanka (Lead KMP Architect) — Sprint 4*
