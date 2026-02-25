# API Documentation — ZyntaPOS Network Layer

**Status:** Phase 1 endpoints documented. Known gaps explicitly called out.
**Last updated:** 2026-02-25
**Sources:** `shared/data/src/commonMain/kotlin/.../remote/api/ApiService.kt`, `ApiClient.kt`

---

## 1. Overview

ZyntaPOS communicates with a cloud backend via a Ktor HTTP client configured in
`shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/remote/api/ApiClient.kt`.

The client is built by the `buildApiClient()` factory function and injected as a Koin singleton.
All requests are authenticated with Bearer tokens; the base URL is injected from
`BuildConfig.ZYNTA_API_BASE_URL` (populated from `local.properties` via the Gradle Secrets Plugin).

---

## 2. Base URL

```
https://${BuildConfig.ZYNTA_API_BASE_URL}
```

The base URL is configured per environment in `local.properties`:

```properties
ZYNTA_API_BASE_URL=api.zyntapos.example.com   # without protocol prefix
```

All Phase 1 endpoints are prefixed with `/api/v1/`.

---

## 3. Authentication

All endpoints (except `/auth/login`) require a **Bearer token** in the `Authorization` header.

```
Authorization: Bearer <access_token>
```

Tokens are persisted in `SecurePreferences` (AES-256-GCM encrypted storage) under the keys:
- `KEY_ACCESS_TOKEN` — short-lived JWT (~15 min expiry)
- `KEY_REFRESH_TOKEN` — long-lived JWT

### Token Refresh

The Ktor `Auth` plugin handles token refresh automatically on 401 responses. The `refreshTokens`
callback in `buildApiClient()` surfaces the stored refresh token; `AuthRepositoryImpl` is
responsible for calling `POST /api/v1/auth/refresh` and persisting the new access token.

Client-side JWT expiry is checked proactively using `JwtManager.isTokenExpired()` with a 30-second
clock-skew buffer.

---

## 4. HTTP Client Configuration

**File:** `ApiClient.kt`

```kotlin
// JSON: ignoreUnknownKeys=true, isLenient=true, coerceInputValues=true, encodeDefaults=true
install(ContentNegotiation) { json(...) }

// Bearer auth: loads tokens from SecurePreferences, refreshes on 401
install(Auth) { bearer { ... } }

// Timeouts
install(HttpTimeout) {
    connectTimeoutMillis = 10_000L
    requestTimeoutMillis = 30_000L
    socketTimeoutMillis  = 30_000L
}

// Retry: up to 3 attempts, exponential backoff — 1 s, 2 s, 4 s (max 4 s)
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    retryOnException(maxRetries = 3, retryOnTimeout = true)
    exponentialDelay(base = 2.0, maxDelayMs = 4_000L)
}

// Logging (DEBUG builds only): HEADERS level via Kermit
if (AppConfig.IS_DEBUG) { install(Logging) { level = LogLevel.HEADERS } }
```

---

## 5. Endpoint Reference

All endpoints are defined in `ApiService` interface:
`shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/remote/api/ApiService.kt`

| Method | Path | Function | Request Type | Response Type |
|--------|------|----------|--------------|---------------|
| POST | `/api/v1/auth/login` | `login()` | `AuthRequestDto` | `AuthResponseDto` |
| POST | `/api/v1/auth/refresh` | `refreshToken()` | `refreshToken: String` | `AuthRefreshResponseDto` |
| GET | `/api/v1/products` | `getProducts()` | _(none)_ | `List<ProductDto>` |
| POST | `/api/v1/sync/push` | `pushOperations()` | `List<SyncOperationDto>` | `SyncResponseDto` |
| GET | `/api/v1/sync/pull` | `pullOperations()` | `lastSyncTimestamp: Long` | `SyncPullResponseDto` |

### POST `/api/v1/auth/login`

Authenticates with server credentials and returns JWT tokens.

**Request (`AuthRequestDto`):**
```json
{ "username": "cashier@store.com", "password": "..." }
```

**Response (`AuthResponseDto`):**
```json
{ "access_token": "eyJ...", "refresh_token": "eyJ...", "expires_in": 900 }
```

**Error:** Throws `AuthException` on 401 (invalid credentials).

### POST `/api/v1/auth/refresh`

Exchanges a refresh token for a new access token.

**Error:** Throws `AuthException` on 401 (expired refresh token — requires re-login).

### GET `/api/v1/products`

Retrieves the full product catalog. Typically called during initial seeding or full resync.

**Error:** Throws `NetworkException` on transport or server errors.

### POST `/api/v1/sync/push`

Pushes a batch of locally pending operations to the server.

**Request (`SyncOperationDto`):**
```json
{
  "id": "uuid",
  "entity_type": "PRODUCT",
  "entity_id": "uuid",
  "operation": "UPDATE",
  "payload": "{...}",
  "created_at": 1706000000000,
  "retry_count": 0
}
```

**Response (`SyncResponseDto`):**
```json
{
  "accepted": ["uuid1", "uuid2"],
  "rejected": ["uuid3"],
  "delta_operations": [...]
}
```

The `delta_operations` field contains server-authoritative conflict resolutions bundled with the
push acknowledgement. These are applied locally via `applyDeltaOperations()` in `SyncEngine`.

**Error:** Throws `SyncException` on 4xx/5xx server-side batch failure.

### GET `/api/v1/sync/pull`

Pulls server-side changes created after `lastSyncTimestamp`.

**Query params:** `?since=<epoch_ms>`

**Response (`SyncPullResponseDto`):**
```json
{
  "operations": [...],
  "server_timestamp": 1706000060000
}
```

**Error:** Throws `NetworkException` on transport or server errors.

---

## 6. Error Types

All HTTP errors are mapped to typed domain exceptions defined in `:shared:core`:

| Exception | HTTP Condition | Usage |
|-----------|---------------|-------|
| `AuthException` | 401 Unauthorized | Invalid credentials, expired tokens |
| `NetworkException` | Transport errors, timeouts, 5xx | General connectivity / server issues |
| `SyncException` | 4xx/5xx on sync endpoints | Sync-specific server rejections |

The Ktor retry plugin automatically retries on server errors (5xx) and timeouts, up to 3 times
with exponential backoff.

---

## 7. Known Gaps

### SyncRepositoryImpl — Network Stubs (Phase 1)

**File:** `SyncRepositoryImpl.kt`, lines 155–171

`SyncRepository.pushToServer()` and `pullFromServer()` are Phase 1 stubs that do NOT make HTTP
calls. They mark operations as locally SYNCED and return empty lists respectively.

The actual Ktor `ApiService` wiring is planned for Sprint 6, Step 3.4:

```kotlin
// TODO(Sprint6-Step3.4): wire Ktor ApiService here
override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> {
    return if (ops.isEmpty()) Result.Success(Unit)
    else markSynced(ops.map { it.id })  // stub: marks local only
}
```

Note: `SyncEngine.kt` calls `ApiService` directly (bypassing `SyncRepositoryImpl`), so push/pull
HTTP calls DO function through `SyncEngine`. The stub affects only the `SyncRepository` interface
contract layer.

### applyDeltaOperations — Sprint 7 TODO

Server delta operations are received by `SyncEngine` but not applied to the local database.
The entity-type router (`entityType → RepositoryImpl.upsertFromSync()`) is a Sprint 7 TODO.

### OpenAPI Specification

No OpenAPI / Swagger specification exists yet for the ZyntaPOS backend API. The endpoint table
above is derived from the Kotlin interface comments and DTO definitions.
