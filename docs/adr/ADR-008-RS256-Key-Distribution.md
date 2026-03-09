# ADR-008: RS256 Public Key Distribution — Bundle Default + SecurePreferences Cache

**Status:** ACCEPTED
**Date:** 2026-03-09
**Deciders:** Security team

## Context

`JwtManager.verifyOfflineRole()` verifies the RS256 signature of a stored JWT before
trusting the `role` claim for offline RBAC route gating. It requires the RS256 public key
as a standard Base64-encoded DER (`SubjectPublicKeyInfo` format).

Two distribution approaches were considered:

**Option A — BuildConfig only:** Embed the public key in `BuildConfig.ZYNTA_RS256_PUBLIC_KEY`
(injected at build time from `local.properties`). Simple, always available offline.
Drawback: key rotation requires a new app build and store rollout, which can take weeks.

**Option B — Fetch-only:** Client fetches the key from a server endpoint on every session.
Enables instant key rotation. Drawback: breaks completely on first install or during offline-only
deployments before any network contact has occurred.

## Decision

Use a **hybrid "trust on first use" (TOFU) approach**:

```
Priority 1: Cached key in SecurePreferences  (fetched from server, refreshed on login)
Priority 2: Bundled key in BuildConfig       (embedded at build time, initial offline fallback)
```

### How it works

1. **First run / always offline:** `verifyOfflineRole(token, BuildConfig.ZYNTA_RS256_PUBLIC_KEY)`
   works immediately — no network dependency.

2. **On every successful online login or token refresh:** The client calls
   `GET /.well-known/public-key` and passes the response to `JwtManager.cachePublicKey()`,
   which persists it to `SecurePreferences` under `security.rs256_public_key`.

3. **Subsequent offline sessions:** Call sites prefer the cached key:
   ```kotlin
   val key = jwtManager.getCachedPublicKey() ?: BuildConfig.ZYNTA_RS256_PUBLIC_KEY
   val role = jwtManager.verifyOfflineRole(token, key) ?: Role.CASHIER
   ```

4. **Key rotation:** Update the RSA keypair on the server. The next time any device goes
   online and logs in, it fetches and caches the new key automatically. No app update required.

5. **Verification failure:** If both the cached key and the bundled key fail verification,
   `verifyOfflineRole` returns `null` and callers degrade to `Role.CASHIER` — the
   lowest-privilege role, never `Role.ADMIN`.

### Components implemented

| Component | File | Change |
|-----------|------|--------|
| Domain key constant | `shared/domain/.../port/SecureStorageKeys.kt` | Added `KEY_RS256_PUBLIC_KEY = "security.rs256_public_key"` |
| Security key delegation | `shared/security/.../prefs/SecurePreferencesKeys.kt` | Added delegation |
| JwtManager cache methods | `shared/security/.../auth/JwtManager.kt` | Added `cachePublicKey()` + `getCachedPublicKey()` |
| DTO | `shared/data/.../dto/WellKnownDto.kt` | New `PublicKeyResponseDto` |
| ApiService interface | `shared/data/.../api/ApiService.kt` | Added `fetchPublicKey()` |
| Ktor implementation | `shared/data/.../api/KtorApiService.kt` | Implemented `fetchPublicKey()` |
| Backend endpoint | `backend/api/.../routes/WellKnownRoutes.kt` | `GET /.well-known/public-key` |
| Routing registration | `backend/api/.../plugins/Routing.kt` | Registered under API rate limit |

### Call site wiring (Phase 2)

The fetch-and-cache hook belongs in `AuthRepositoryImpl.login()` on the online path
(Phase 2 implementation). When online login is implemented:

```kotlin
// After saving tokens:
runCatching { apiService.fetchPublicKey() }
    .onSuccess { jwtManager.cachePublicKey(it.publicKey) }
    // Failure is silently ignored — offline fallback is always available
```

The call is fire-and-forget with `runCatching` so a transient network failure on the
well-known endpoint never breaks the login flow.

## Consequences

**Positive:**
- Key rotation on the server propagates to all devices without an app update
- Offline-first behaviour preserved from day 1 (bundled fallback always present)
- No new required network dependency — `fetchPublicKey` failures are non-fatal

**Negative:**
- Devices that have never gone online use the BuildConfig-bundled key indefinitely
  (acceptable: these devices haven't received any server-signed JWTs either)
- The bundled key in the APK/JAR is extractable via `apktool` — this is not a security
  issue (RSA public keys are inherently public) but means the binary must be rebuilt
  if you want to remove the fallback after key rotation

## Rotation procedure

1. Generate new RSA-2048 keypair on VPS: `openssl genrsa -out new_private.pem 2048`
2. Update `RS256_PRIVATE_KEY` and `RS256_PUBLIC_KEY` environment variables in `.env`
3. Run `docker compose up -d api` — new JWT signing begins immediately
4. Update `ZYNTA_RS256_PUBLIC_KEY` in `local.properties` for new builds
5. All devices pick up the new public key on their next online login (no app update needed)
6. Old BuildConfig-bundled key in existing APKs will fail signature verification after
   rotation — force a token refresh or re-login to pick up the cached new key
