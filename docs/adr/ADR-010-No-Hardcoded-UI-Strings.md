# ADR-010: No Hardcoded UI Strings

**Status:** ACCEPTED
**Date:** 2026-03-26
**Decision Makers:** Development Team

## Context

ZyntaPOS targets international markets (Sri Lanka, Southeast Asia, Middle East, East Asia). The codebase accumulated hundreds of hardcoded English strings in Compose screen files (`Text("Save")`, `label = "Email"`, etc.), making localization impossible without a full audit and rewrite.

An i18n infrastructure was built in `:shared:core`:
- `StringResource` enum (typed keys)
- `EnglishStrings` translation table
- `LocalizationManager` (locale switching, string resolution with positional args)
- `SupportedLocale` enum

A Compose-side bridge was added in `:composeApp:designsystem`:
- `LocalStrings` (`CompositionLocal` providing `StringResolver`)
- `StringResolver` (operator `[]` syntax for concise access)

All new and existing UI strings must use this infrastructure.

## Decision

**All user-visible strings in Compose screen composables MUST use `StringResource` keys resolved via `LocalStrings.current`.**

Hardcoded string literals in `Text()`, `label`, `placeholder`, `title`, `description`, `contentDescription`, error messages, and dialog text are **prohibited** in any feature module.

### Required Pattern

```kotlin
@Composable
fun MyScreen() {
    val s = LocalStrings.current
    Text(s[StringResource.COMMON_SAVE])
    OutlinedTextField(
        label = { Text(s[StringResource.AUTH_EMAIL_LABEL]) },
        placeholder = { Text(s[StringResource.AUTH_EMAIL_PLACEHOLDER]) },
    )
}
```

### What Requires a StringResource

| UI element | Must use StringResource? |
|------------|-------------------------|
| `Text("...")` content | Yes |
| `label = { Text("...") }` | Yes |
| `placeholder = { Text("...") }` | Yes |
| `title = "..."` in TopAppBar | Yes |
| `contentDescription = "..."` | Yes |
| Dialog title/body text | Yes |
| Snackbar messages | Yes |
| Error messages shown to user | Yes |
| Log messages (Kermit) | No (internal, not user-facing) |
| Test assertion strings | No |
| Format strings (currency, date) | Use CurrencyFormatter/DateTimeUtils |

### Adding New Strings

1. Add a new entry to `StringResource` enum in `:shared:core`
2. Add the English translation to `EnglishStrings.table` in `:shared:core`
3. Use `LocalStrings.current[StringResource.YOUR_KEY]` in the composable

### Code Review Rule

PRs introducing `Text("` with a hardcoded English string literal in any `composeApp/feature/*/` file MUST be rejected citing ADR-010.

## Consequences

### Positive
- Full localization support without code changes (add new locale translation table)
- Consistent string management across 16+ feature modules
- Type-safe string keys (compiler catches typos)
- Runtime locale switching without app restart

### Negative
- Slightly more verbose than inline strings
- All existing hardcoded strings must be migrated (one-time cost)
- `StringResource` enum grows large (mitigated by section comments)

## References

- `shared/core/src/commonMain/kotlin/.../i18n/StringResource.kt`
- `shared/core/src/commonMain/kotlin/.../i18n/EnglishStrings.kt`
- `shared/core/src/commonMain/kotlin/.../i18n/LocalizationManager.kt`
- `composeApp/designsystem/src/commonMain/kotlin/.../components/LocalStrings.kt`
