# ADR-004: keystore/ and token/ Scaffold Directory Removal (MERGED-F2)

**Date:** 2026-02-22
**Status:** Accepted
**Deciders:** Senior KMP Architect (Dilanka)
**Fixes:** MERGED-F2 audit finding

---

## Context

Four directories inside `:shared:security` contained only `.gitkeep` files with no
code and no prior architectural decision:

| Directory | Source Set |
|-----------|-----------|
| `security/keystore/` | commonMain |
| `security/keystore/` | androidMain |
| `security/keystore/` | jvmMain |
| `security/token/`    | commonMain |

These were ambiguous scaffold placeholders. An explicit decision was required:
either implement the planned abstractions or remove the scaffolding as redundant.

---

## Investigation

A full read of all existing `.kt` files in `:shared:security` was performed before
making this decision. The findings were:

### keystore/ — Fully superseded by `crypto/`

The Master Plan (Sprint 8, Steps 5.1.1–5.1.2) originally planned a `KeystoreProvider`
expect/actual to abstract Android Keystore / JCE PKCS12 KeyStore access. During
implementation, this abstraction was absorbed directly into the `crypto/` classes:

- **`EncryptionManager.android.kt`** — Calls `KeyStore.getInstance("AndroidKeyStore")`
  directly; creates/retrieves AES-256 key under a named alias. Fully implemented.
- **`EncryptionManager.jvm.kt`** — Creates/loads a PKCS12 KeyStore at
  `~/.zyntapos/.zyntapos.p12` with a machine-derived password. Fully implemented.
- **`DatabaseKeyManager.android.kt`** — Envelope encryption: KEK in Android Keystore
  wraps a random DEK stored in SharedPreferences. Fully implemented.
- **`DatabaseKeyManager.jvm.kt`** — 32-byte AES DEK stored in PKCS12 KeyStore at
  `~/.zyntapos/.db_keystore.p12`. Fully implemented.

A separate `KeystoreProvider` would add a layer of indirection with no benefit —
each `EncryptionManager` / `DatabaseKeyManager` actual already contains the minimum
keystore interaction needed for its specific operation.

### token/ — Fully superseded by `prefs/` + `auth/`

The original plan placed `TokenStorage` in `security/token/`. During implementation
the responsibility was split more cleanly:

- **`security/prefs/TokenStorage.kt`** — The `interface` contract (put/get/remove).
- **`security/prefs/SecurePreferences.kt`** — The `expect class` that implements
  `TokenStorage` on Android and Desktop.
- **`security/auth/JwtManager.kt`** — Owns all token lifecycle operations
  (`saveTokens`, `getAccessToken`, `getRefreshToken`, `clearTokens`, `isTokenExpired`,
  `extractUserId`, `extractRole`). Accepts `TokenStorage` via constructor injection,
  enabling `commonTest` testing via `FakeSecurePreferences`.

No further class is needed in `security/token/`.

---

## Decision

**Remove all four `.gitkeep` scaffold files. No new source files are created.**

The `keystore/` and `token/` package directories are left absent from the source tree.
This is valid in Kotlin — packages do not require physical directory presence if no
source files use them.

A comprehensive decision rationale comment has been added to `SecurityModule.kt` (Koin)
above the `securityModule` declaration, documenting:
- Which existing classes fulfil the keystore abstraction role
- Which existing classes fulfil the token storage role
- Under what future conditions a `KeystoreProvider` expect/actual should be created

---

## Future Guidance

If future requirements demand a **standalone `KeystoreProvider` abstraction** (e.g.,
for key rotation scheduling, cross-module key sharing, or a dedicated hardware-security
module adapter), create:

```
shared/security/src/commonMain/.../security/keystore/KeystoreProvider.kt
  → expect class KeystoreProvider

shared/security/src/androidMain/.../security/keystore/KeystoreProvider.kt
  → actual class KeystoreProvider (Android Keystore)

shared/security/src/jvmMain/.../security/keystore/KeystoreProvider.kt
  → actual class KeystoreProvider (JCE PKCS12)
```

Follow the `SecurePreferences` expect/actual pattern in `security/prefs/`.

---

## Files Modified

| File | Action |
|------|--------|
| `shared/security/src/commonMain/.../security/keystore/.gitkeep` | DELETED |
| `shared/security/src/androidMain/.../security/keystore/.gitkeep` | DELETED |
| `shared/security/src/jvmMain/.../security/keystore/.gitkeep` | DELETED |
| `shared/security/src/commonMain/.../security/token/.gitkeep` | DELETED |
| `shared/security/src/commonMain/.../security/di/SecurityModule.kt` | EDITED — ADR-004 rationale comment added above `securityModule` |
| `docs/adr/ADR-003-SecurePreferences-Consolidation.md` | CREATED — fills existing code reference in SecurePreferences.kt |
| `docs/adr/ADR-004-keystore-token-scaffold-removal.md` | CREATED — this document |
