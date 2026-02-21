# Feature: POS

## Overview
The `pos` feature module owns the primary checkout screen — the highest-frequency
interaction surface in ZyntaPOS. Every millisecond of added latency and every
unnecessary recomposition has a direct impact on cashier throughput.

---

## Component Wrapper Pattern

### `PosSearchBar` → wraps `ZentaSearchBar`

`PosSearchBar` is a **thin, intentional wrapper** around the design-system primitive
`ZentaSearchBar`. It is **not** a re-implementation.

**File:** `src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/pos/PosSearchBar.kt`
**Wraps:** `composeApp/designsystem/.../components/ZentaSearchBar.kt`

#### Why the wrapper exists

| Responsibility | Detail |
|---|---|
| **MVI intent wiring** | Translates raw `String`/`Boolean` lambdas into typed `PosIntent` dispatches (`SearchQueryChanged`, `SearchFocusChanged`, `SetScannerActive`). The design system has no knowledge of `PosIntent`. |
| **Layout contract** | Applies `ZentaSpacing.md` horizontal and `ZentaSpacing.sm` vertical padding — a POS screen layout rule that must not leak into the generic design system. |
| **FocusRequester sharing** | Owns the `FocusRequester` instance that Desktop's `KeyboardShortcutHandler` (F2 key → focus search) needs to share. Hoisting it here keeps it out of `PosScreen` clutter. |
| **`onClear` normalisation** | Maps `onClear` to `onQueryChange("")` so all call sites share a consistent clear convention without each needing to know the design-system contract. |

#### Rules for maintaining this pattern

1. **Never duplicate UI logic.** If you need a new icon, colour, or keyboard behaviour,
   add a parameter to `ZentaSearchBar` and thread it through here — do not copy the
   `OutlinedTextField` block.
2. **Keep `PosSearchBar` stateless.** State (query text, scanner active flag) lives in
   `PosState` and is passed down. Debounce lives in `PosViewModel`. This composable
   is a pure render function.
3. **If the wrapper grows beyond ~60 lines**, treat it as a signal that either
   `ZentaSearchBar` needs a new parameter, or the POS screen needs a decomposition
   review.

---

## Debounce Strategy

Debounce is applied in **`PosViewModel`**, not in the composable. This keeps the UI
layer free of timing logic and makes debounce behaviour testable via unit tests on
the ViewModel without a Compose test harness.

```
Keystroke → PosSearchBar.onQueryChange
          → PosIntent.SearchQueryChanged(query)
          → PosViewModel (debounce 300 ms with Flow.debounce)
          → repository.searchProducts(query)
```

---

## Keyboard Shortcut Integration (Desktop / JVM)

`F2` → `focusRequester.requestFocus()` → search field gains focus.

The `FocusRequester` created in `PosSearchBar` must be hoisted to `PosScreen` and
passed to both `PosSearchBar` and `KeyboardShortcutHandler` (jvmMain source set).

```kotlin
// PosScreen.kt (jvmMain or commonMain with expect/actual guard)
val searchFocusRequester = remember { FocusRequester() }

PosSearchBar(
    ...
    focusRequester = searchFocusRequester,
)
KeyboardShortcutHandler(
    onF2 = { searchFocusRequester.requestFocus() }
)
```
