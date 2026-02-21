# ADR-003: SecurePreferences Interface Consolidation

**Date:** 2026-02-21
**Status:** Accepted
**Deciders:** Senior KMP Architect (Dilanka)
**Referenced by:** `security/prefs/SecurePreferences.kt` (ZENTA-FINAL-AUDIT MERGED-F1 / MERGED-D3)

---

## Context

Two competing `SecurePreferences` abstractions existed simultaneously:

| Location | Type | Role |
|----------|------|------|
| `:shared:data` → `data.local.security.SecurePreferences` | `interface` | Defined the encrypted key-value contract for data layer consumers |
| `:shared:security` → `security.prefs.SecurePreferences` | `expect class` | Provided platform-actual implementations (Android + JVM) |

This created an import-ambiguity (both shared the same simple name), a structural violation of Clean Architecture (security contracts owned by `:shared:data` instead of `:shared:security`), and forced `:shared:data` consumers to depend on adapter shim classes (`AndroidEncryptedSecurePreferences`, `DesktopAesSecurePreferences`).

---

## Decision

**Canonical location:** `security.prefs.SecurePreferences` (`expect class`) in `:shared:security`.

1. The `data.local.security.SecurePreferences` **interface** was deleted from `:shared:data`.
2. The adapter shim classes (`AndroidEncryptedSecurePreferences`, `DesktopAesSecurePreferences`) were removed from `:shared:data`.
3. All `:shared:data` consumers were updated to import `security.prefs.SecurePreferences` directly.
4. `SecurePreferences` was extended to implement the `TokenStorage` interface (see `security.prefs.TokenStorage`) so `JwtManager` can accept it without a platform-specific import in `commonMain`.

---

## Consequences

- Single canonical source of truth for encrypted key-value storage.
- `:shared:data` no longer owns security primitives — all encryption and key management lives in `:shared:security`.
- `JwtManager` (commonMain) depends only on the `TokenStorage` interface, not on the `expect class`, preserving testability in `commonTest` via `FakeSecurePreferences`.
