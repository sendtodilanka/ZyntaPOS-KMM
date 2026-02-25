# Security Model — ZyntaPOS

**Status:** Phase 1 implementation documented. Known gaps explicitly called out.
**Last updated:** 2026-02-25
**Sources:** `shared/security/src/commonMain/`, `androidMain/`, `jvmMain/`, `composeApp/feature/auth/session/`

---

## 1. Encryption at Rest

### Database: SQLCipher 4.5 (AES-256-CBC)

The entire SQLite database is encrypted at rest using SQLCipher 4.5 with a 256-bit AES key.
The key is never stored in plaintext on disk; it is managed by `DatabaseKeyManager` (see Key
Storage below). Each platform uses an envelope encryption scheme to protect the database key.

### Sensitive Preferences: AES-256-GCM

Individual key-value secrets (JWT tokens, session metadata) are stored in `SecurePreferences`
(see Section 3). Both platform implementations use AES-256-GCM authenticated encryption.

### General-Purpose Encryption: EncryptionManager

`EncryptionManager` (`shared/security/src/commonMain/.../crypto/EncryptionManager.kt`) is a
cross-platform facade for AES-256-GCM encryption:

- **Android:** Non-extractable key in Android Keystore; 12-byte random IV per call; 128-bit GCM tag.
- **Desktop (JVM):** Key loaded from PKCS12 KeyStore at `~/.zyntapos/.zyntapos.p12`; same IV/tag parameters.
- **Thread safety:** Not thread-safe; do not share instances across coroutines. Each call creates
  its own `Cipher` instance.

---

## 2. Key Storage

### Android: Envelope Encryption via Android Keystore

**File:** `shared/security/src/androidMain/.../crypto/DatabaseKeyManager.android.kt`

1. A 256-bit Data-Encryption Key (DEK) is generated with `java.security.SecureRandom` on first launch.
2. A Key-Encryption Key (KEK, alias `zyntapos_kek_v1`) is generated in the **Android Keystore**
   under `AES/GCM/NoPadding`. The KEK is non-extractable and may be hardware-backed (TEE/SE).
3. The DEK is wrapped (AES-256-GCM) by the KEK and stored in `SharedPreferences` as Base64.
4. The raw IV used for wrapping is also stored in `SharedPreferences`.
5. On subsequent launches: the wrapped DEK is unwrapped by the KEK and returned as raw bytes for
   SQLCipher PRAGMA key initialisation.

**Why this pattern?** Hardware-backed Keystore keys return `null` for `secretKey.encoded`, making
direct extraction impossible. Envelope encryption sidesteps this limitation while keeping the DEK
material hardware-protected at rest.

### Desktop (JVM): PKCS12 KeyStore

**File:** `shared/security/src/jvmMain/.../crypto/DatabaseKeyManager.jvm.kt`

1. A 256-bit AES key is generated with `java.security.SecureRandom` on first launch.
2. The key is stored as a `SecretKeyEntry` in a PKCS12 KeyStore at `~/.zyntapos/.db_keystore.p12`.
3. The KeyStore is password-protected with a machine fingerprint derived from
   `SHA-256(user.name | os.name | os.arch)`. This provides device-binding without a user-facing
   password prompt.
4. The key is directly extractable on the JVM (`secretKey.encoded` returns raw bytes).

**Security note:** The machine fingerprint password is not a secret — it is deterministic from
observable system properties. The PKCS12 file is protected by OS-level file permissions (user
home directory). This is acceptable for a desktop POS application where physical security of the
machine is assumed.

---

## 3. Secure Preferences (SecurePreferences)

**File:** `shared/security/src/commonMain/.../prefs/SecurePreferences.kt`

`SecurePreferences` is an `expect class` that implements both `TokenStorage` (for `JwtManager`)
and `SecureStoragePort` (domain port for `SyncEngine` / `ApiClient` in `:shared:data`).

| Platform | Implementation |
|----------|----------------|
| Android | `EncryptedSharedPreferences` (Jetpack Security Crypto): AES-256-SIV key encryption, AES-256-GCM value encryption; master key in Android Keystore |
| Desktop (JVM) | AES-256-GCM encrypted Java Properties file; key from PKCS12 KeyStore |

**ADR-003 note:** The only canonical `SecurePreferences` is in `:shared:security`. A previously
existing interface in `:shared:data` was deleted. Do not recreate it there.

---

## 4. Authentication: JWT Flow

**File:** `shared/security/src/commonMain/.../auth/JwtManager.kt`

### Token Lifecycle

1. `POST /api/v1/auth/login` returns `access_token` (short-lived, ~15 min) and `refresh_token`
   (long-lived).
2. `JwtManager.saveTokens()` persists both tokens in `SecurePreferences` under
   `KEY_ACCESS_TOKEN` and `KEY_REFRESH_TOKEN`.
3. `ApiClient` (Ktor bearer auth plugin) loads tokens via `SecureStoragePort` on each request.
4. On 401: Ktor's `refreshTokens` callback surfaces the stored refresh token. The caller
   (`AuthRepositoryImpl`) is responsible for calling `POST /api/v1/auth/refresh` and persisting
   the new access token.
5. Logout: `JwtManager.clearTokens()` removes both keys.

### JWT Parsing

`JwtManager.parseJwt()` decodes the base64url payload and deserialises `JwtClaims`:

```kotlin
data class JwtClaims(val sub: String, val role: String, val storeId: String, val exp: Long, val iat: Long)
```

**Critical security note:** The client does **NOT** verify the JWT signature. Signature
verification is delegated to the server on every authenticated API call. The client only reads
claims for UX decisions (role gating, expiry detection).

### Token Expiry Check

```kotlin
fun isTokenExpired(token: String): Boolean {
    val claims = parseJwt(token)
    val nowSeconds = Clock.System.now().epochSeconds
    return claims.exp <= nowSeconds + 30  // 30-second clock-skew buffer
}
```

A 30-second buffer accommodates minor clock drift between the client device and the server.

---

## 5. Session Management

**File:** `composeApp/feature/auth/src/commonMain/.../session/SessionManager.kt`

**Important:** `SessionManager` is in `:composeApp:feature:auth`, NOT in `:shared:security`.
It is a UI-layer concern (emits Compose effects) and does not belong in the shared security module.

### Behaviour

`SessionManager` tracks operator idle time and emits `AuthEffect.ShowPinLock` when the
configurable timeout elapses:

| Role | Default Timeout |
|------|----------------|
| CASHIER | 10 minutes |
| STORE_MANAGER | 20 minutes |
| ADMIN | 30 minutes |
| Default | 15 minutes |

### Integration

Wire `SessionManager.onUserInteraction()` to every `Modifier.pointerInput` and `onKeyEvent` in
the authenticated scaffold. Observe `SessionManager.effects` in the root composable to push the
PIN lock screen.

- `start()` — call after successful login or PIN unlock
- `pause()` — call when PIN lock screen is already visible (prevents double-emit)
- `resume()` — call after PIN lock screen is dismissed
- `reset()` — call on explicit logout (cancels timer without emitting)

---

## 6. PIN Management

**File:** `shared/security/src/commonMain/.../auth/PinManager.kt`

`PinManager` is a stateless `object` that manages 4–6 digit cashier quick-switch PINs.

### Hashing Algorithm

```
stored_hash = "<base64url-salt-16bytes>:<hex-sha256-hash-32bytes>"
hash         = SHA-256(salt_bytes || pin_utf8_bytes)
```

**Why SHA-256 instead of bcrypt?** PINs are short (max 10^6 combinations) but are a low-latency
UX feature for cashier switching. BCrypt's ~300 ms latency degrades checkout flow. The salt
prevents rainbow-table attacks; application-level brute-force protection (5-attempt lockout) is
enforced in the auth feature. SHA-256 is not the sole authentication factor for high-privilege actions.

### Constant-Time Comparison

`PinManager.verifyPin()` uses constant-time string comparison to prevent timing side-channel
attacks:

```kotlin
private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}
```

---

## 7. Role-Based Access Control (RBAC)

**File:** `shared/security/src/commonMain/.../rbac/RbacEngine.kt`

`RbacEngine` is a **stateless, pure-computation** component — no IO, no side effects. All
permission decisions are derived from `Permission.rolePermissions` (the RBAC matrix defined in
`:shared:domain`).

### Roles

```
ADMIN > MANAGER > CASHIER | CUSTOMER_SERVICE | REPORTER
```

- `ADMIN`: Full system access
- `MANAGER`: Store operations, reports, user management (no system config)
- `CASHIER`: POS, register session, stock lookups
- `CUSTOMER_SERVICE`: Customer directory, order lookup, refunds
- `REPORTER`: Read-only reports

### Usage

```kotlin
val rbac = RbacEngine()           // injected as Koin singleton
rbac.hasPermission(user, Permission.VOID_ORDER)  // true/false
rbac.getPermissions(role)          // Set<Permission>
rbac.getDeniedPermissions(role)    // Set<Permission> (for error messages)
```

### Navigation Gating

RBAC filtering for navigation items is applied within `ZyntaNavGraph.kt` and `NavigationItems.kt`
in `:composeApp:navigation`. There is no separate `RbacNavFilter` class — the filtering logic
is inlined using `RbacEngine`.

---

## 8. Secrets Injection

Sensitive configuration is injected via the Gradle Secrets Plugin at build time:

| Key | Destination |
|-----|-------------|
| `ZYNTA_API_BASE_URL` | `BuildConfig.ZYNTA_API_BASE_URL` |
| `ZYNTA_API_CLIENT_ID` | `BuildConfig.ZYNTA_API_CLIENT_ID` |
| `ZYNTA_DB_PASSPHRASE` | `BuildConfig.ZYNTA_DB_PASSPHRASE` (SQLCipher initial passphrase) |
| `ZYNTA_IRD_CLIENT_CERTIFICATE_PATH` | `BuildConfig.ZYNTA_IRD_CLIENT_CERTIFICATE_PATH` |
| `ZYNTA_IRD_CERTIFICATE_PASSWORD` | `BuildConfig.ZYNTA_IRD_CERTIFICATE_PASSWORD` |

`local.properties` is **git-ignored**. Copy `local.properties.template` before building.

---

## 9. Security Audit Logger

**File:** `shared/security/src/commonMain/.../audit/SecurityAuditLogger.kt`

`SecurityAuditLogger` records security-relevant events (`LOGIN_ATTEMPT`, `LOGOUT`,
`PERMISSION_DENIED`, `ORDER_VOID`, `STOCK_ADJUSTMENT`, etc.) as `AuditEntry` domain objects.

**Known gap:** `AuditRepositoryImpl` has 3 `TODO` stubs — `insert()`, `observeAll()`, and
`observeByUserId()` — all blocked on the `audit_log` SQLDelight schema generation (tracked as
MERGED-D2). Audit entries are currently lost on app restart and are not persisted to the database.

---

## 10. Known Security Gaps

| Gap | Severity | Status |
|-----|----------|--------|
| `AuditRepositoryImpl.insert()` is a `TODO` — audit entries not persisted | High | MERGED-D2 pending |
| `AuditRepositoryImpl.observeAll()` is a `TODO` — audit log viewer returns no data | High | MERGED-D2 pending |
| `CashDrawerController` HAL interface not implemented — no drawer open audit events | Medium | Phase 2 backlog |
| JWT signature not verified client-side | By design | Server validates on every call |
| Desktop PKCS12 password is machine-fingerprint (deterministic) | Accepted risk | Desktop deployment assumes physical security |
| PIN is SHA-256, not bcrypt | Accepted risk | Application-level lockout (5 attempts) mitigates |
